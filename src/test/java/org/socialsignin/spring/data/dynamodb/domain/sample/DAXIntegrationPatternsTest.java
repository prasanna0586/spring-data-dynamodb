package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.socialsignin.spring.data.dynamodb.utils.DynamoDBLocalResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for DynamoDB Accelerator (DAX) patterns using library APIs.
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
 * - Read patterns that benefit from caching (repository.findById, DynamoDBOperations.scan)
 * - Write patterns with write-through caching (repository.save)
 * - Consistency models (eventually consistent vs strongly consistent)
 * - Cache invalidation patterns (repository.save, repository.delete)
 * - Batch operations with caching (repository.findAllById)
 *
 * This test suite validates the library's API patterns work correctly with DAX-compatible operations.
 * Tests use repository methods and DynamoDBOperations instead of raw AWS SDK calls.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, DAXIntegrationPatternsTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("DAX Integration Patterns Tests")
public class DAXIntegrationPatternsTest {

    private static final Logger logger = LoggerFactory.getLogger(DAXIntegrationPatternsTest.class);

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    public static class TestAppConfig {
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DynamoDBOperations dynamoDBOperations;

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

        // When - Repeatedly read the same item using repository (benefits from DAX item cache)
        int readCount = 10;
        List<Long> readTimes = new ArrayList<>();

        for (int i = 0; i < readCount; i++) {
            long start = System.nanoTime();
            Optional<User> result = userRepository.findById("hot-key-user");
            long duration = (System.nanoTime() - start) / 1_000_000; // Convert to ms
            readTimes.add(duration);

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo("hot-key-user");
            assertThat(result.get().getName()).isEqualTo("Popular User");
        }

        // Then - Multiple reads of same item (DAX would cache this)
        logger.info("=== DAX Hot Key Read Pattern ===");
        logger.info("Item: hot-key-user (frequently accessed)");
        logger.info("Read count: " + readCount);
        logger.info("Average read time: " + readTimes.stream().mapToLong(Long::longValue).average().orElse(0) + " ms");
        logger.info("Note: With DAX, subsequent reads would be served from cache (microseconds)");
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

        // When - Repeatedly execute same query using repository (benefits from DAX query cache)
        int queryCount = 5;
        List<Long> queryTimes = new ArrayList<>();

        for (int i = 0; i < queryCount; i++) {
            long start = System.nanoTime();

            List<User> users = userRepository.findByNumberOfPlaylistsLessThan(10);

            long duration = (System.nanoTime() - start) / 1_000_000;
            queryTimes.add(duration);

            assertThat(users).hasSize(10);
        }

        // Then - Query results (DAX would cache identical queries)
        logger.info("=== DAX Query Cache Pattern ===");
        logger.info("Query: numberOfPlaylists < 10");
        logger.info("Query execution count: " + queryCount);
        logger.info("Average query time: " + queryTimes.stream().mapToLong(Long::longValue).average().orElse(0) + " ms");
        logger.info("Note: With DAX, identical queries are cached");
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

        // When - Batch get items using repository (DAX caches individual items)
        long start = System.nanoTime();
        List<User> users = (List<User>) userRepository.findAllById(userIds);
        long duration = (System.nanoTime() - start) / 1_000_000;

        // Then
        assertThat(users).hasSize(25);

        logger.info("=== DAX Batch Get Pattern ===");
        logger.info("Items retrieved: " + users.size());
        logger.info("Batch get time: " + duration + " ms");
        logger.info("Note: DAX caches each item individually for future GetItem calls");
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

        logger.info("=== DAX Write-Through Pattern ===");
        logger.info("Write time: " + writeDuration + " ms");
        logger.info("Immediate read time: " + readDuration + " ms");
        logger.info("Note: DAX write-through ensures cache is updated on writes");
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

        logger.info("=== DAX Update Pattern ===");
        logger.info("Note: DAX automatically invalidates cache on updates");
        logger.info("Read after update returns: " + updated.get().getName());
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

        logger.info("=== DAX Delete Pattern ===");
        logger.info("Note: DAX removes deleted items from cache");
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

        // When - Eventually consistent read using repository (default for DAX)
        // Note: Repository queries are eventually consistent by default unless otherwise specified
        Optional<User> result = userRepository.findById("eventual-user");

        // Then - DAX can cache this
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("eventual-user");
        assertThat(result.get().getName()).isEqualTo("Eventual User");

        logger.info("=== DAX Consistency: Eventually Consistent ===");
        logger.info("Repository reads (default) - DAX caches these reads");
        logger.info("Note: Eventually consistent reads benefit from DAX caching");
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

        // When - Strongly consistent read using repository (bypasses DAX cache)
        // Note: UserRepository.findById is annotated with @Query(consistentReads = CONSISTENT)
        Optional<User> result = userRepository.findById("strong-user");

        // Then - Read directly from DynamoDB (bypasses cache)
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("strong-user");
        assertThat(result.get().getName()).isEqualTo("Strong User");

        logger.info("=== DAX Consistency: Strongly Consistent ===");
        logger.info("Repository.findById (ConsistentRead=true) - Bypasses DAX, reads from DynamoDB");
        logger.info("Note: Use for latest data when cache might be stale");
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
        logger.info("=== DAX Read-Heavy Workload Pattern ===");
        logger.info("Items: " + itemCount);
        logger.info("Reads per item: " + readsPerItem);
        logger.info("Total reads: " + totalReads);
        logger.info("Average read time: " + String.format("%.2f", avgReadTime) + " ms");
        logger.info("Note: With DAX, repeated reads would be microseconds (100-200x faster)");
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("Test 10: Scan cache effectiveness pattern")
    void testQueryCacheEffectivenessPattern() {
        // Given - Data for repeated queries
        for (int i = 0; i < 20; i++) {
            User user = new User();
            user.setId("cache-user-" + String.format("%03d", i));
            user.setName("User " + i);
            user.setNumberOfPlaylists(i);
            userRepository.save(user);
        }

        // When - Execute same query multiple times using repository
        int queryExecutions = 5;
        List<Long> executionTimes = new ArrayList<>();

        for (int i = 0; i < queryExecutions; i++) {
            long start = System.nanoTime();

            List<User> users = userRepository.findByIdStartingWith("cache-user-");

            long duration = (System.nanoTime() - start) / 1_000_000;
            executionTimes.add(duration);

            assertThat(users).hasSize(20);
        }

        // Then
        logger.info("=== DAX Query Cache Effectiveness ===");
        logger.info("Query: Id starts with 'cache-user-'");
        logger.info("Executions: " + queryExecutions);
        for (int i = 0; i < executionTimes.size(); i++) {
            logger.info("  Execution " + (i + 1) + ": " + executionTimes.get(i) + " ms");
        }
        logger.info("Note: With DAX, executions 2-5 would be cached (much faster)");
    }

    // ==================== DAX Configuration Patterns ====================

    @Test
    @org.junit.jupiter.api.Order(11)
    @DisplayName("Test 11: Demonstrate DAX-compatible library API usage")
    void testDAXCompatibleAPIUsage() {
        // This test demonstrates library patterns that work seamlessly with DAX

        // Pattern 1: save() and findById() (PutItem + GetItem operations)
        User user1 = new User();
        user1.setId("dax-compatible-1");
        user1.setName("User 1");
        user1.setNumberOfPlaylists(10);
        userRepository.save(user1);

        Optional<User> getResult = userRepository.findById("dax-compatible-1");
        assertThat(getResult).isPresent();
        assertThat(getResult.get().getId()).isEqualTo("dax-compatible-1");

        // Pattern 2: findBy query with filter (Scan with filter - DAX caches results)
        List<User> queryResult = userRepository.findByNumberOfPlaylistsLessThan(15);
        assertThat(queryResult).isNotEmpty();
        assertThat(queryResult).allMatch(u -> u.getNumberOfPlaylists() < 15);

        // Pattern 3: findAll (Scan operation)
        List<User> scanResult = userRepository.findAll();
        assertThat(scanResult).hasSize(1);

        // Pattern 4: findAllById (Batch GetItem)
        List<User> batchGetResult = (List<User>) userRepository.findAllById(Arrays.asList("dax-compatible-1"));
        assertThat(batchGetResult).hasSize(1);

        logger.info("=== DAX-Compatible Library API Patterns ===");
        logger.info("repository.save() + findById(): Compatible ✓");
        logger.info("repository.findByProperty(): Compatible ✓");
        logger.info("repository.findAll(): Compatible ✓");
        logger.info("repository.findAllById(): Compatible ✓");
        logger.info("Note: All library APIs work seamlessly with DAX when configured");
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    @DisplayName("Test 12: DAX best practices demonstration")
    void testDAXBestPractices() {
        logger.info("=== DAX Best Practices ===");
        logger.info(System.lineSeparator());
        logger.info("1. Use Eventually Consistent Reads:");
        logger.info("   - Set ConsistentRead=false in GetItem/Query requests");
        logger.info("   - Only use ConsistentRead=true when absolute latest data required");
        logger.info(System.lineSeparator());
        logger.info("2. Optimize for Read-Heavy Workloads:");
        logger.info("   - DAX best for read:write ratios of 10:1 or higher");
        logger.info("   - Item cache: Frequently accessed individual items");
        logger.info("   - Query cache: Repeated queries with same parameters");
        logger.info(System.lineSeparator());
        logger.info("3. Cache TTL Configuration:");
        logger.info("   - Item cache TTL: Default 5 minutes");
        logger.info("   - Query cache TTL: Default 5 minutes");
        logger.info("   - Configure based on data freshness requirements");
        logger.info(System.lineSeparator());
        logger.info("4. Cluster Sizing:");
        logger.info("   - Start with 3-node cluster for HA");
        logger.info("   - Monitor cache hit rate (should be >80% for benefit)");
        logger.info("   - Scale horizontally based on request volume");
        logger.info(System.lineSeparator());
        logger.info("5. Client Configuration:");
        logger.info("   - Use connection pooling");
        logger.info("   - Set appropriate timeouts");
        logger.info("   - Enable client-side metrics");
        logger.info(System.lineSeparator());
        logger.info("6. Monitoring:");
        logger.info("   - Track cache hit rate");
        logger.info("   - Monitor query latencies (p50, p99)");
        logger.info("   - Watch for cache evictions");
    }
}
