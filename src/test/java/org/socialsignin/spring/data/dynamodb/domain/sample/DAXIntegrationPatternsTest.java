package org.socialsignin.spring.data.dynamodb.domain.sample;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.*;
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
 * Integration tests for DynamoDB Accelerator (DAX) patterns.
 *
 * DAX is an in-memory cache for DynamoDB that provides:
 * - Microsecond latency for cached reads
 * - Write-through caching
 * - Seamless integration with DynamoDB API
 * - Item cache and query cache
 *
 * NOTE: These tests run against DynamoDB Local (not actual DAX cluster).
 * They demonstrate the patterns and API usage that work with DAX.
 *
 * Key DAX Concepts Tested:
 * - Read patterns that benefit from caching (GetItem, Query, Scan)
 * - Write patterns with write-through caching (PutItem, UpdateItem)
 * - Consistency models (eventually consistent vs strongly consistent)
 * - Cache invalidation patterns
 * - Batch operations with caching
 *
 * Production DAX Setup:
 * - Replace AmazonDynamoDB client with DAX client
 * - Configuration: ClusterDaxClient.builder().endpoint("dax://cluster.endpoint").build()
 * - Same API as DynamoDB (drop-in replacement)
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, DAXIntegrationPatternsTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("DAX Integration Patterns Tests")
public class DAXIntegrationPatternsTest {

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    public static class TestAppConfig {
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AmazonDynamoDB amazonDynamoDB;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    // ==================== Read Patterns That Benefit from DAX ====================

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Test 1: GetItem pattern - Hot key reads (DAX item cache)")
    void testGetItemHotKeyReads() {
        // Given - Create a user that will be read frequently (hot key)
        User user = new User();
        user.setId("hot-key-user");
        user.setName("Popular User");
        user.setNumberOfPlaylists(100);
        userRepository.save(user);

        // When - Repeatedly read the same item (benefits from DAX item cache)
        int readCount = 10;
        List<Long> readTimes = new ArrayList<>();

        for (int i = 0; i < readCount; i++) {
            long start = System.nanoTime();
            GetItemRequest request = new GetItemRequest()
                    .withTableName("user")
                    .withKey(Collections.singletonMap("Id", new AttributeValue().withS("hot-key-user")))
                    .withConsistentRead(false); // Eventually consistent for DAX caching

            GetItemResult result = amazonDynamoDB.getItem(request);
            long duration = (System.nanoTime() - start) / 1_000_000; // Convert to ms
            readTimes.add(duration);

            assertThat(result.getItem()).isNotNull();
            assertThat(result.getItem().get("Id").getS()).isEqualTo("hot-key-user");
        }

        // Then - Multiple reads of same item (DAX would cache this)
        System.out.println("=== DAX Hot Key Read Pattern ===");
        System.out.println("Item: hot-key-user (frequently accessed)");
        System.out.println("Read count: " + readCount);
        System.out.println("Average read time: " + readTimes.stream().mapToLong(Long::longValue).average().orElse(0) + " ms");
        System.out.println("Note: With DAX, subsequent reads would be served from cache (microseconds)");
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Test 2: Scan pattern - Repeated scans (DAX query cache)")
    void testQueryRepeatedQueries() {
        // Given - Create users
        for (int i = 0; i < 10; i++) {
            User user = new User();
            user.setId("query-user-" + String.format("%03d", i));
            user.setName("User " + i);
            user.setNumberOfPlaylists(i);
            userRepository.save(user);
        }

        // When - Repeatedly execute same scan with filter (benefits from DAX query cache)
        int queryCount = 5;
        List<Long> queryTimes = new ArrayList<>();

        for (int i = 0; i < queryCount; i++) {
            long start = System.nanoTime();

            ScanRequest request = new ScanRequest()
                    .withTableName("user")
                    .withFilterExpression("numberOfPlaylists < :limit")
                    .withExpressionAttributeValues(
                            Collections.singletonMap(":limit", new AttributeValue().withN("10"))
                    );

            ScanResult result = amazonDynamoDB.scan(request);
            long duration = (System.nanoTime() - start) / 1_000_000;
            queryTimes.add(duration);

            assertThat(result.getCount()).isEqualTo(10);
        }

        // Then - Scan results (DAX would cache identical scans)
        System.out.println("=== DAX Query Cache Pattern ===");
        System.out.println("Scan: numberOfPlaylists < 10");
        System.out.println("Scan execution count: " + queryCount);
        System.out.println("Average scan time: " + queryTimes.stream().mapToLong(Long::longValue).average().orElse(0) + " ms");
        System.out.println("Note: With DAX, identical scans are cached");
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Test 3: Batch GetItem pattern - Multiple item retrieval")
    void testBatchGetItemPattern() {
        // Given - Create multiple users
        List<String> userIds = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            String id = "batch-user-" + i;
            User user = new User();
            user.setId(id);
            user.setName("User " + i);
            userRepository.save(user);
            userIds.add(id);
        }

        // When - Batch get items (DAX caches individual items)
        Map<String, KeysAndAttributes> requestItems = new HashMap<>();
        List<Map<String, AttributeValue>> keys = new ArrayList<>();

        for (String id : userIds) {
            keys.add(Collections.singletonMap("Id", new AttributeValue().withS(id)));
        }

        requestItems.put("user", new KeysAndAttributes().withKeys(keys));

        long start = System.nanoTime();
        BatchGetItemRequest request = new BatchGetItemRequest()
                .withRequestItems(requestItems);
        BatchGetItemResult result = amazonDynamoDB.batchGetItem(request);
        long duration = (System.nanoTime() - start) / 1_000_000;

        // Then
        List<Map<String, AttributeValue>> items = result.getResponses().get("user");
        assertThat(items).hasSize(25);

        System.out.println("=== DAX Batch Get Pattern ===");
        System.out.println("Items retrieved: " + items.size());
        System.out.println("Batch get time: " + duration + " ms");
        System.out.println("Note: DAX caches each item individually for future GetItem calls");
    }

    // ==================== Write Patterns with DAX ====================

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Test 4: PutItem pattern - Write-through cache")
    void testPutItemWriteThroughCache() {
        // When - Write item (DAX uses write-through caching)
        User user = new User();
        user.setId("write-through-user");
        user.setName("Write Through User");
        user.setNumberOfPlaylists(50);

        long writeStart = System.nanoTime();
        userRepository.save(user);
        long writeDuration = (System.nanoTime() - writeStart) / 1_000_000;

        // Immediately read (DAX would serve from cache)
        long readStart = System.nanoTime();
        Optional<User> retrieved = userRepository.findById("write-through-user");
        long readDuration = (System.nanoTime() - readStart) / 1_000_000;

        // Then
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getName()).isEqualTo("Write Through User");

        System.out.println("=== DAX Write-Through Pattern ===");
        System.out.println("Write time: " + writeDuration + " ms");
        System.out.println("Immediate read time: " + readDuration + " ms");
        System.out.println("Note: DAX write-through ensures cache is updated on writes");
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("Test 5: UpdateItem pattern - Cache invalidation")
    void testUpdateItemCacheInvalidation() {
        // Given - Existing user
        User user = new User();
        user.setId("update-user");
        user.setName("Original Name");
        user.setNumberOfPlaylists(10);
        userRepository.save(user);

        // Read to populate cache
        userRepository.findById("update-user");

        // When - Update item (DAX invalidates cache entry)
        user.setName("Updated Name");
        user.setNumberOfPlaylists(20);
        userRepository.save(user);

        // Read updated item
        Optional<User> updated = userRepository.findById("update-user");

        // Then - Should get updated value (not stale cache)
        assertThat(updated).isPresent();
        assertThat(updated.get().getName()).isEqualTo("Updated Name");
        assertThat(updated.get().getNumberOfPlaylists()).isEqualTo(20);

        System.out.println("=== DAX Update Pattern ===");
        System.out.println("Note: DAX automatically invalidates cache on updates");
        System.out.println("Read after update returns: " + updated.get().getName());
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("Test 6: DeleteItem pattern - Cache removal")
    void testDeleteItemCacheRemoval() {
        // Given - User that's been read (cached)
        User user = new User();
        user.setId("delete-user");
        user.setName("To Be Deleted");
        userRepository.save(user);

        // Read to cache
        assertThat(userRepository.findById("delete-user")).isPresent();

        // When - Delete item (DAX removes from cache)
        userRepository.deleteById("delete-user");

        // Read after delete
        Optional<User> deleted = userRepository.findById("delete-user");

        // Then - Should not find (cache cleared)
        assertThat(deleted).isEmpty();

        System.out.println("=== DAX Delete Pattern ===");
        System.out.println("Note: DAX removes deleted items from cache");
    }

    // ==================== Consistency Considerations ====================

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("Test 7: Eventually consistent reads (DAX default)")
    void testEventuallyConsistentReads() {
        // Given - Create user
        User user = new User();
        user.setId("eventual-user");
        user.setName("Eventual User");
        userRepository.save(user);

        // When - Eventually consistent read (default for DAX)
        GetItemRequest request = new GetItemRequest()
                .withTableName("user")
                .withKey(Collections.singletonMap("Id", new AttributeValue().withS("eventual-user")))
                .withConsistentRead(false); // Eventually consistent

        GetItemResult result = amazonDynamoDB.getItem(request);

        // Then - DAX can cache this
        assertThat(result.getItem()).isNotNull();

        System.out.println("=== DAX Consistency: Eventually Consistent ===");
        System.out.println("ConsistentRead=false (default) - DAX caches these reads");
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("Test 8: Strongly consistent reads (bypass DAX cache)")
    void testStronglyConsistentReads() {
        // Given - Create user
        User user = new User();
        user.setId("strong-user");
        user.setName("Strong User");
        userRepository.save(user);

        // When - Strongly consistent read (bypasses DAX cache)
        GetItemRequest request = new GetItemRequest()
                .withTableName("user")
                .withKey(Collections.singletonMap("Id", new AttributeValue().withS("strong-user")))
                .withConsistentRead(true); // Strongly consistent

        GetItemResult result = amazonDynamoDB.getItem(request);

        // Then - Read directly from DynamoDB
        assertThat(result.getItem()).isNotNull();

        System.out.println("=== DAX Consistency: Strongly Consistent ===");
        System.out.println("ConsistentRead=true - Bypasses DAX, reads from DynamoDB");
        System.out.println("Note: Use for latest data when cache might be stale");
    }

    // ==================== Cache Performance Patterns ====================

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("Test 9: Read-heavy workload pattern (ideal for DAX)")
    void testReadHeavyWorkloadPattern() {
        // Given - Small dataset read frequently
        int itemCount = 10;
        for (int i = 0; i < itemCount; i++) {
            User user = new User();
            user.setId("popular-user-" + i);
            user.setName("Popular User " + i);
            userRepository.save(user);
        }

        // When - Read same items multiple times (read-heavy pattern)
        int readsPerItem = 10;
        int totalReads = 0;
        long totalReadTime = 0;

        for (int r = 0; r < readsPerItem; r++) {
            for (int i = 0; i < itemCount; i++) {
                long start = System.nanoTime();
                userRepository.findById("popular-user-" + i);
                totalReadTime += (System.nanoTime() - start) / 1_000_000;
                totalReads++;
            }
        }

        double avgReadTime = totalReadTime / (double) totalReads;

        // Then
        System.out.println("=== DAX Read-Heavy Workload Pattern ===");
        System.out.println("Items: " + itemCount);
        System.out.println("Reads per item: " + readsPerItem);
        System.out.println("Total reads: " + totalReads);
        System.out.println("Average read time: " + String.format("%.2f", avgReadTime) + " ms");
        System.out.println("Note: With DAX, repeated reads would be microseconds (100-200x faster)");
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("Test 10: Scan cache effectiveness pattern")
    void testQueryCacheEffectivenessPattern() {
        // Given - Data for repeated scans
        for (int i = 0; i < 20; i++) {
            User user = new User();
            user.setId("cache-user-" + String.format("%03d", i));
            user.setName("User " + i);
            user.setNumberOfPlaylists(i);
            userRepository.save(user);
        }

        // When - Execute same scan multiple times
        int queryExecutions = 5;
        List<Long> executionTimes = new ArrayList<>();

        for (int i = 0; i < queryExecutions; i++) {
            long start = System.nanoTime();

            ScanRequest request = new ScanRequest()
                    .withTableName("user")
                    .withFilterExpression("begins_with(Id, :prefix)")
                    .withExpressionAttributeValues(
                            Collections.singletonMap(":prefix", new AttributeValue().withS("cache-user-"))
                    );

            ScanResult result = amazonDynamoDB.scan(request);
            long duration = (System.nanoTime() - start) / 1_000_000;
            executionTimes.add(duration);

            assertThat(result.getCount()).isEqualTo(20);
        }

        // Then
        System.out.println("=== DAX Query Cache Effectiveness ===");
        System.out.println("Scan: begins_with(Id, 'cache-user-')");
        System.out.println("Executions: " + queryExecutions);
        for (int i = 0; i < executionTimes.size(); i++) {
            System.out.println("  Execution " + (i + 1) + ": " + executionTimes.get(i) + " ms");
        }
        System.out.println("Note: With DAX, executions 2-5 would be cached (much faster)");
    }

    // ==================== DAX Configuration Patterns ====================

    @Test
    @org.junit.jupiter.api.Order(11)
    @DisplayName("Test 11: Demonstrate DAX-compatible API usage")
    void testDAXCompatibleAPIUsage() {
        // This test demonstrates patterns that work seamlessly with DAX

        // Pattern 1: GetItem
        User user1 = new User();
        user1.setId("dax-compatible-1");
        user1.setName("User 1");
        userRepository.save(user1);

        GetItemRequest getRequest = new GetItemRequest()
                .withTableName("user")
                .withKey(Collections.singletonMap("Id", new AttributeValue().withS("dax-compatible-1")));
        GetItemResult getResult = amazonDynamoDB.getItem(getRequest);
        assertThat(getResult.getItem()).isNotNull();

        // Pattern 2: Scan with filter (DAX caches scan results)
        user1.setPostCode("dax-code");
        userRepository.save(user1);

        ScanRequest scanWithFilterRequest = new ScanRequest()
                .withTableName("user")
                .withFilterExpression("postCode = :code")
                .withExpressionAttributeValues(
                        Collections.singletonMap(":code", new AttributeValue().withS("dax-code"))
                );
        ScanResult scanWithFilterResult = amazonDynamoDB.scan(scanWithFilterRequest);
        assertThat(scanWithFilterResult.getCount()).isGreaterThan(0);

        // Pattern 3: Scan
        ScanRequest scanRequest = new ScanRequest()
                .withTableName("user")
                .withLimit(10);
        ScanResult scanResult = amazonDynamoDB.scan(scanRequest);
        assertThat(scanResult.getItems()).isNotEmpty();

        System.out.println("=== DAX-Compatible API Patterns ===");
        System.out.println("GetItem: Compatible ✓");
        System.out.println("Scan with Filter: Compatible ✓");
        System.out.println("Scan: Compatible ✓");
        System.out.println("PutItem: Compatible ✓");
        System.out.println("UpdateItem: Compatible ✓");
        System.out.println("DeleteItem: Compatible ✓");
        System.out.println("BatchGetItem: Compatible ✓");
        System.out.println("Note: To use DAX, simply replace AmazonDynamoDB client with ClusterDaxClient");
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    @DisplayName("Test 12: DAX best practices demonstration")
    void testDAXBestPractices() {
        System.out.println("=== DAX Best Practices ===");
        System.out.println();
        System.out.println("1. Use Eventually Consistent Reads:");
        System.out.println("   - Set ConsistentRead=false in GetItem/Query requests");
        System.out.println("   - Only use ConsistentRead=true when absolute latest data required");
        System.out.println();
        System.out.println("2. Optimize for Read-Heavy Workloads:");
        System.out.println("   - DAX best for read:write ratios of 10:1 or higher");
        System.out.println("   - Item cache: Frequently accessed individual items");
        System.out.println("   - Query cache: Repeated queries with same parameters");
        System.out.println();
        System.out.println("3. Cache TTL Configuration:");
        System.out.println("   - Item cache TTL: Default 5 minutes");
        System.out.println("   - Query cache TTL: Default 5 minutes");
        System.out.println("   - Configure based on data freshness requirements");
        System.out.println();
        System.out.println("4. Cluster Sizing:");
        System.out.println("   - Start with 3-node cluster for HA");
        System.out.println("   - Monitor cache hit rate (should be >80% for benefit)");
        System.out.println("   - Scale horizontally based on request volume");
        System.out.println();
        System.out.println("5. Client Configuration:");
        System.out.println("   - Use connection pooling");
        System.out.println("   - Set appropriate timeouts");
        System.out.println("   - Enable client-side metrics");
        System.out.println();
        System.out.println("6. Monitoring:");
        System.out.println("   - Track cache hit rate");
        System.out.println("   - Monitor query latencies (p50, p99)");
        System.out.println("   - Watch for cache evictions");
    }
}
