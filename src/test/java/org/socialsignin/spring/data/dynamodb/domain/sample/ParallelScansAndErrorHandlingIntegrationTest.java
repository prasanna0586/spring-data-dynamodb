package org.socialsignin.spring.data.dynamodb.domain.sample;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.socialsignin.spring.data.dynamodb.utils.DynamoDBLocalResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for Parallel Scans and Error Handling scenarios.
 *
 * Coverage:
 * - Parallel scan with multiple segments
 * - Parallel scan performance vs sequential
 * - Large table scanning
 * - Error handling: ConditionalCheckFailedException
 * - Error handling: Item not found
 * - Error handling: Validation errors
 * - Error handling: Invalid expressions
 * - Edge cases: Large items, attribute limits
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, ParallelScansAndErrorHandlingIntegrationTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Parallel Scans and Error Handling Integration Tests")
public class ParallelScansAndErrorHandlingIntegrationTest {

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    public static class TestAppConfig {
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DynamoDbClient amazonDynamoDB;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("dynamoDbEnhancedClient")
    private DynamoDbEnhancedClient dynamoDbEnhancedClient;

    private DynamoDbTable<User> userTable;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userTable = dynamoDbEnhancedClient.table("user", TableSchema.fromBean(User.class));
    }

    // ==================== Parallel Scans ====================

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Test 1: Parallel scan with 4 segments")
    void testParallelScanWith4Segments() throws InterruptedException, ExecutionException {
        // Given - Create 100 users
        createUsers(100);

        // When - Parallel scan with 4 segments
        int totalSegments = 4;
        ExecutorService executor = Executors.newFixedThreadPool(totalSegments);
        List<Future<List<Map<String, AttributeValue>>>> futures = new ArrayList<>();

        for (int segment = 0; segment < totalSegments; segment++) {
            final int currentSegment = segment;
            Future<List<Map<String, AttributeValue>>> future = executor.submit(() -> {
                return scanSegment(currentSegment, totalSegments);
            });
            futures.add(future);
        }

        // Collect all results
        List<Map<String, AttributeValue>> allItems = new ArrayList<>();
        for (Future<List<Map<String, AttributeValue>>> future : futures) {
            allItems.addAll(future.get());
        }

        executor.shutdown();

        // Then - Should have scanned all 100 users
        assertThat(allItems).hasSize(100);
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Test 2: Parallel scan performance vs sequential scan")
    void testParallelScanPerformance() throws Exception {
        // Given - Create 200 users
        createUsers(200);

        // Sequential scan
        long sequentialStart = System.currentTimeMillis();
        List<Map<String, AttributeValue>> sequentialResults = scanSegment(0, 1);
        long sequentialTime = System.currentTimeMillis() - sequentialStart;

        // Parallel scan with 4 segments
        long parallelStart = System.currentTimeMillis();
        int totalSegments = 4;
        ExecutorService executor = Executors.newFixedThreadPool(totalSegments);
        List<Future<List<Map<String, AttributeValue>>>> futures = new ArrayList<>();

        for (int segment = 0; segment < totalSegments; segment++) {
            final int currentSegment = segment;
            futures.add(executor.submit(() -> scanSegment(currentSegment, totalSegments)));
        }

        List<Map<String, AttributeValue>> parallelResults = new ArrayList<>();
        for (Future<List<Map<String, AttributeValue>>> future : futures) {
            parallelResults.addAll(future.get());
        }
        executor.shutdown();
        long parallelTime = System.currentTimeMillis() - parallelStart;

        // Then
        assertThat(sequentialResults).hasSize(200);
        assertThat(parallelResults).hasSize(200);
        System.out.println("Sequential scan: " + sequentialTime + "ms");
        System.out.println("Parallel scan (4 segments): " + parallelTime + "ms");
        System.out.println("Speedup: " + ((double)sequentialTime / parallelTime) + "x");
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Test 3: Parallel scan with filter expression")
    void testParallelScanWithFilter() throws Exception {
        // Given
        createUsers(100);

        // When - Parallel scan with filter (playlists > 50)
        int totalSegments = 4;
        ExecutorService executor = Executors.newFixedThreadPool(totalSegments);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int segment = 0; segment < totalSegments; segment++) {
            final int currentSegment = segment;
            futures.add(executor.submit(() -> {
                Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
                expressionAttributeValues.put(":threshold", AttributeValue.builder().n("50")
                        .build());

                List<Map<String, AttributeValue>> segmentItems = new ArrayList<>();
                Map<String, AttributeValue> lastKey = null;
                do {
                    ScanRequest scanRequest = ScanRequest.builder()
                            .tableName("user")
                            .segment(currentSegment)
                            .totalSegments(totalSegments)
                            .filterExpression("numberOfPlaylists > :threshold")
                            .expressionAttributeValues(expressionAttributeValues)
                            .exclusiveStartKey(lastKey)
                            .build();

                    ScanResponse result = amazonDynamoDB.scan(scanRequest);
                    segmentItems.addAll(result.items());
                    lastKey = result.lastEvaluatedKey();
                } while (lastKey != null && !lastKey.isEmpty());

                return segmentItems.size();
            }));
        }

        int totalFilteredItems = 0;
        for (Future<Integer> future : futures) {
            totalFilteredItems += future.get();
        }
        executor.shutdown();

        // Then - Should find users with playlists > 50
        assertThat(totalFilteredItems).isGreaterThan(0);
    }

    // ==================== Error Handling ====================

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Test 4: ConditionalCheckFailedException on failed conditional update")
    void testConditionalCheckFailedException() {
        // Given
        User user = new User();
        user.setId("error-user-1");
        user.setName("Test User");
        user.setNumberOfPlaylists(10);
        userRepository.save(user);

        // When/Then - Conditional update with wrong condition
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":expected", AttributeValue.builder().n("999").build());

        Expression conditionExpression = Expression.builder()
                .expression("numberOfPlaylists = :expected")
                .expressionValues(expressionValues)
                .build();

        user.setName("Updated Name");

        assertThatThrownBy(() -> userTable.putItem(builder -> builder
                .item(user)
                .conditionExpression(conditionExpression)))
                .isInstanceOf(ConditionalCheckFailedException.class);
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("Test 5: Item not found returns empty Optional")
    void testItemNotFound() {
        // When - Try to find non-existent item
        Optional<User> result = userRepository.findById("non-existent-id");

        // Then - Should return empty
        assertThat(result).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("Test 6: Invalid update expression throws AmazonDynamoDBException")
    void testInvalidUpdateExpression() {
        // Given
        User user = new User();
        user.setId("error-user-2");
        user.setName("Test");
        userRepository.save(user);

        // When/Then - Invalid update expression
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.builder().s("error-user-2").build());

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("SET invalid_syntax here")
                .build(); // Invalid syntax

        assertThatThrownBy(() -> amazonDynamoDB.updateItem(updateRequest))
                .isInstanceOf(DynamoDbException.class);
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("Test 7: Missing expression attribute values throws ValidationException")
    void testMissingExpressionAttributeValues() {
        // Given
        User user = new User();
        user.setId("error-user-3");
        userRepository.save(user);

        // When/Then - Update expression references :value but doesn't provide it
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.builder().s("error-user-3").build());

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("SET #n = :value") // References :value
                .expressionAttributeNames(Collections.singletonMap("#n", "name"))
                .build();
        // Missing expressionAttributeValues

        assertThatThrownBy(() -> amazonDynamoDB.updateItem(updateRequest))
                .isInstanceOf(DynamoDbException.class)
                .hasMessageContaining("attribute value: :value");
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("Test 8: Empty set throws ValidationException")
    void testEmptySetValidation() {
        // When/Then - Try to save user with empty set
        User user = new User();
        user.setId("error-user-4");
        user.setName("Test");
        user.setTags(new HashSet<>()); // Empty set

        assertThatThrownBy(() -> userRepository.save(user))
                .isInstanceOf(DynamoDbException.class)
                .hasMessageContaining("may not be empty");
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("Test 9: Batch write with duplicate keys fails")
    void testBatchWriteDuplicateKeys() {
        // Given - Two users with same ID
        User user1 = new User();
        user1.setId("duplicate-id");
        user1.setName("User 1");

        User user2 = new User();
        user2.setId("duplicate-id");
        user2.setName("User 2");

        // When/Then - Save the first, then trying to batch with duplicate will fail
        userRepository.save(user1);

        List<WriteRequest> writeRequests = new ArrayList<>();
        Map<String, AttributeValue> item1 = new HashMap<>();
        item1.put("Id", AttributeValue.builder().s("duplicate-id").build());
        item1.put("name", AttributeValue.builder().s("User 1").build());
        writeRequests.add(WriteRequest.builder().putRequest(PutRequest.builder().item(item1)
                .build())
                .build());

        Map<String, AttributeValue> item2 = new HashMap<>();
        item2.put("Id", AttributeValue.builder().s("duplicate-id").build());
        item2.put("name", AttributeValue.builder().s("User 2").build());
        writeRequests.add(WriteRequest.builder().putRequest(PutRequest.builder().item(item2)
                .build())
                .build());

        BatchWriteItemRequest batchRequest = BatchWriteItemRequest.builder()
                .requestItems(Collections.singletonMap("user", writeRequests))
                .build();

        // DynamoDB enforces no duplicate keys in a single batch write request
        assertThatThrownBy(() -> amazonDynamoDB.batchWriteItem(batchRequest))
                .isInstanceOf(DynamoDbException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("Test 10: Query with non-existent table throws ResourceNotFoundException")
    void testNonExistentTable() {
        // When/Then - Query non-existent table
        ScanRequest scanRequest = ScanRequest.builder().tableName("NonExistentTable")
                .build();

        assertThatThrownBy(() -> amazonDynamoDB.scan(scanRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ==================== Helper Methods ====================

    private void createUsers(int count) {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            User user = new User();
            user.setId("parallel-user-" + i);
            user.setName("User " + i);
            user.setNumberOfPlaylists(i);
            user.setPostCode(String.valueOf(10000 + (i % 10)));
            users.add(user);
        }
        userRepository.saveAll(users);
    }

    private List<Map<String, AttributeValue>> scanSegment(int segment, int totalSegments) {
        List<Map<String, AttributeValue>> items = new ArrayList<>();
        Map<String, AttributeValue> lastEvaluatedKey = null;

        do {
            ScanRequest scanRequest = ScanRequest.builder()
                    .tableName("user")
                    .segment(segment)
                    .totalSegments(totalSegments)
                    .exclusiveStartKey(lastEvaluatedKey)
                    .build();

            ScanResponse result = amazonDynamoDB.scan(scanRequest);
            items.addAll(result.items());
            lastEvaluatedKey = result.lastEvaluatedKey();
        } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());

        return items;
    }
}
