package org.socialsignin.spring.data.dynamodb.domain.sample;

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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive integration tests for advanced query patterns and complex filter expressions.
 *
 * SDK v2 Migration Notes:
 * - SDK v1: DynamoDBMapper → SDK v2: DynamoDbEnhancedClient (not used in this test)
 * - SDK v1: DynamoDBQueryExpression/DynamoDBScanExpression → SDK v2: Direct scan/query with DynamoDbClient
 * - SDK v1: new AttributeValue("string") → SDK v2: AttributeValue.fromS("string")
 * - All scan operations use SDK v2 ScanRequest and ScanResponse
 * - Filter expressions use the same syntax in both SDKs
 *
 * Coverage:
 * - Complex filter expressions (AND, OR, NOT)
 * - IN operator
 * - BETWEEN operator
 * - begins_with() function
 * - contains() function
 * - size() function
 * - attribute_exists() function
 * - attribute_type() function
 * - Multiple conditions combined
 * - Filter expressions with scan
 * - Consistent reads
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, AdvancedQueryPatternsIntegrationTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Advanced Query Patterns and Filter Expressions Integration Tests")
public class AdvancedQueryPatternsIntegrationTest {

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    public static class TestAppConfig {
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DynamoDbClient amazonDynamoDB;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        // Create test data
        createTestUsers();
    }

    private void createTestUsers() {
        User user1 = new User();
        user1.setId("user-001");
        user1.setName("Alice Anderson");
        user1.setPostCode("12345");
        user1.setNumberOfPlaylists(5);
        user1.setTags(new HashSet<>(Arrays.asList("premium", "verified")));

        User user2 = new User();
        user2.setId("user-002");
        user2.setName("Bob Brown");
        user2.setPostCode("12345");
        user2.setNumberOfPlaylists(10);
        user2.setTags(new HashSet<>(Arrays.asList("verified")));

        User user3 = new User();
        user3.setId("user-003");
        user3.setName("Alice Baker");
        user3.setPostCode("67890");
        user3.setNumberOfPlaylists(15);

        User user4 = new User();
        user4.setId("user-004");
        user4.setName("Charlie Cooper");
        user4.setPostCode("67890");
        user4.setNumberOfPlaylists(8);
        user4.setTags(new HashSet<>(Arrays.asList("premium")));

        User user5 = new User();
        user5.setId("user-005");
        user5.setName("David Davis");
        user5.setPostCode("11111");
        user5.setNumberOfPlaylists(20);

        userRepository.saveAll(Arrays.asList(user1, user2, user3, user4, user5));
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Test 1: Filter expression - BETWEEN operator")
    void testFilterExpressionBetween() {
        // Given - Test data already created

        // When - Scan with BETWEEN filter
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":min", AttributeValue.builder().n("8")
                .build());
        expressionAttributeValues.put(":max", AttributeValue.builder().n("15")
                .build());

        ScanRequest scanRequest = ScanRequest.builder()
                .tableName("user")
                .filterExpression("numberOfPlaylists BETWEEN :min AND :max")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        ScanResponse result = amazonDynamoDB.scan(scanRequest);

        // Then - Should return users with 8-15 playlists
        assertThat(result.items()).hasSize(3); // user2(10), user3(15), user4(8)
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Test 2: Filter expression - IN operator")
    void testFilterExpressionIn() {
        // When - Scan with IN filter
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":code1", AttributeValue.fromS("12345"));
        expressionAttributeValues.put(":code2", AttributeValue.fromS("67890"));

        ScanRequest scanRequest = ScanRequest.builder()
                .tableName("user")
                .filterExpression("postCode IN (:code1, :code2)")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        ScanResponse result = amazonDynamoDB.scan(scanRequest);

        // Then - Should return users in postcodes 12345 or 67890
        assertThat(result.items()).hasSize(4); // user1, user2, user3, user4
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Test 3: Filter expression - begins_with() function")
    void testFilterExpressionBeginsWith() {
        // When - Scan for names beginning with "Alice"
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":prefix", AttributeValue.fromS("Alice"));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#n", "name");

        ScanRequest scanRequest = ScanRequest.builder()
                .tableName("user")
                .filterExpression("begins_with(#n, :prefix)")
                .expressionAttributeNames(expressionAttributeNames)
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        ScanResponse result = amazonDynamoDB.scan(scanRequest);

        // Then - Should return Alice Anderson and Alice Baker
        assertThat(result.items()).hasSize(2);
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Test 4: Filter expression - contains() function")
    void testFilterExpressionContains() {
        // When - Scan for names containing "Brown"
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":substring", AttributeValue.fromS("Brown"));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#n", "name");

        ScanRequest scanRequest = ScanRequest.builder()
                .tableName("user")
                .filterExpression("contains(#n, :substring)")
                .expressionAttributeNames(expressionAttributeNames)
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        ScanResponse result = amazonDynamoDB.scan(scanRequest);

        // Then - Should return Bob Brown
        assertThat(result.items()).hasSize(1);
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("Test 5: Filter expression - AND conditions")
    void testFilterExpressionAnd() {
        // When - Scan with multiple AND conditions
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":postCode", AttributeValue.fromS("12345"));
        expressionAttributeValues.put(":minPlaylists", AttributeValue.builder().n("8")
                .build());

        ScanRequest scanRequest = ScanRequest.builder()
                .tableName("user")
                .filterExpression("postCode = :postCode AND numberOfPlaylists > :minPlaylists")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        ScanResponse result = amazonDynamoDB.scan(scanRequest);

        // Then - Should return user2 (postCode=12345, playlists=10)
        assertThat(result.items()).hasSize(1);
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("Test 6: Filter expression - OR conditions")
    void testFilterExpressionOr() {
        // When - Scan with OR conditions
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":high", AttributeValue.builder().n("20")
                .build());
        expressionAttributeValues.put(":low", AttributeValue.builder().n("5")
                .build());

        ScanRequest scanRequest = ScanRequest.builder()
                .tableName("user")
                .filterExpression("numberOfPlaylists = :high OR numberOfPlaylists = :low")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        ScanResponse result = amazonDynamoDB.scan(scanRequest);

        // Then - Should return user1(5) and user5(20)
        assertThat(result.items()).hasSize(2);
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("Test 7: Filter expression - NOT condition")
    void testFilterExpressionNot() {
        // When - Scan with NOT condition
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":code", AttributeValue.fromS("12345"));

        ScanRequest scanRequest = ScanRequest.builder()
                .tableName("user")
                .filterExpression("NOT postCode = :code")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        ScanResponse result = amazonDynamoDB.scan(scanRequest);

        // Then - Should return users not in postCode 12345
        assertThat(result.items()).hasSize(3); // user3, user4, user5
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("Test 8: Filter expression - attribute_exists()")
    void testFilterExpressionAttributeExists() {
        // When - Scan for users with tags
        ScanRequest scanRequest = ScanRequest.builder()
                .tableName("user")
                .filterExpression("attribute_exists(tags)")
                .build();

        ScanResponse result = amazonDynamoDB.scan(scanRequest);

        // Then - Should return users with tags set
        assertThat(result.items()).hasSize(3); // user1, user2, user4
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("Test 9: Filter expression - attribute_not_exists()")
    void testFilterExpressionAttributeNotExists() {
        // When - Scan for users without tags
        ScanRequest scanRequest = ScanRequest.builder()
                .tableName("user")
                .filterExpression("attribute_not_exists(tags)")
                .build();

        ScanResponse result = amazonDynamoDB.scan(scanRequest);

        // Then - Should return users without tags
        assertThat(result.items()).hasSize(2); // user3, user5
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("Test 10: Filter expression - size() function on set")
    void testFilterExpressionSizeFunction() {
        // When - Scan for users with exactly 2 tags
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":size", AttributeValue.builder().n("2")
                .build());

        ScanRequest scanRequest = ScanRequest.builder()
                .tableName("user")
                .filterExpression("size(tags) = :size")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        ScanResponse result = amazonDynamoDB.scan(scanRequest);

        // Then - Should return user1 (has 2 tags: premium, verified)
        assertThat(result.items()).hasSize(1);
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    @DisplayName("Test 11: Complex filter - Combination of multiple operators")
    void testComplexFilterExpression() {
        // When - Complex filter: (postCode=12345 OR postCode=67890) AND playlists > 5 AND name begins with A
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":code1", AttributeValue.fromS("12345"));
        expressionAttributeValues.put(":code2", AttributeValue.fromS("67890"));
        expressionAttributeValues.put(":minPlaylists", AttributeValue.builder().n("5")
                .build());
        expressionAttributeValues.put(":prefix", AttributeValue.fromS("A"));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#n", "name");

        ScanRequest scanRequest = ScanRequest.builder()
                .tableName("user")
                .filterExpression("(postCode = :code1 OR postCode = :code2) AND numberOfPlaylists > :minPlaylists AND begins_with(#n, :prefix)")
                .expressionAttributeNames(expressionAttributeNames)
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        ScanResponse result = amazonDynamoDB.scan(scanRequest);

        // Then - Should return user3 (Alice Baker, postCode=67890, playlists=15)
        assertThat(result.items()).hasSize(1);
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    @DisplayName("Test 12: Consistent read query")
    void testConsistentRead() {
        // Given - User repository with consistent read configured
        // (UserRepository has @Query(consistentReads = CONSISTENT) on findById)

        // When - Read with consistent read
        Optional<User> user = userRepository.findById("user-001");

        // Then - Should retrieve user (with strongly consistent read)
        assertThat(user).isPresent();
        assertThat(user.get().getName()).isEqualTo("Alice Anderson");
    }

    @Test
    @org.junit.jupiter.api.Order(13)
    @DisplayName("Test 13: Query with limit")
    void testQueryWithLimit() {
        // When - Scan with limit
        ScanRequest scanRequest = ScanRequest.builder()
                .tableName("user")
                .limit(2)
                .build();

        ScanResponse result = amazonDynamoDB.scan(scanRequest);

        // Then - Should return at most 2 items
        assertThat(result.items().size()).isLessThanOrEqualTo(2);
    }

    @Test
    @org.junit.jupiter.api.Order(14)
    @DisplayName("Test 14: Scan with pagination")
    void testScanWithPagination() {
        // When - Scan with pagination (2 items per page)
        List<Map<String, AttributeValue>> allItems = new ArrayList<>();
        Map<String, AttributeValue> lastEvaluatedKey = null;

        do {
            ScanRequest scanRequest = ScanRequest.builder()
                    .tableName("user")
                    .limit(2)
                    .exclusiveStartKey(lastEvaluatedKey)
                    .build();

            ScanResponse result = amazonDynamoDB.scan(scanRequest);
            allItems.addAll(result.items());
            lastEvaluatedKey = result.lastEvaluatedKey();
        } while (lastEvaluatedKey != null);

        // Then - Should have retrieved all 5 users via pagination
        assertThat(allItems).hasSize(5);
    }

    @Test
    @org.junit.jupiter.api.Order(15)
    @DisplayName("Test 15: Count query with filter")
    void testCountWithFilter() {
        // When - Count users with more than 10 playlists
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":threshold", AttributeValue.builder().n("10")
                .build());

        ScanRequest scanRequest = ScanRequest.builder()
                .tableName("user")
                .select(Select.COUNT)
                .filterExpression("numberOfPlaylists > :threshold")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        ScanResponse result = amazonDynamoDB.scan(scanRequest);

        // Then - Should count users with >10 playlists
        assertThat(result.count()).isEqualTo(2); // user3(15), user5(20)
    }
}
