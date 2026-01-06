package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.socialsignin.spring.data.dynamodb.utils.DynamoDBLocalResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance benchmark tests to establish baseline metrics before SDK v1 to v2 migration.
 *
 * These tests can be run after migration to ensure no performance regression.
 * Metrics collected:
 * - Single vs Batch operation performance
 * - Read vs Write latency
 * - Query vs Scan performance
 * - Throughput measurements
 * - Concurrent operation performance
 *
 * NOTE: DynamoDB Local performance != AWS performance, but these tests
 * establish relative baselines for comparison.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, PerformanceBenchmarkIntegrationTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Performance Benchmark Integration Tests")
public class PerformanceBenchmarkIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceBenchmarkIntegrationTest.class);

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    public static class TestAppConfig {
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        orderRepository.deleteAll();
    }

    // ==================== Write Performance ====================

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Test 1: Benchmark - Single save operations (100 items)")
    void benchmarkSingleSaveOperations() {
        // Given
        int itemCount = 100;
        List<User> users = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            User user = new User();
            user.setId("bench-single-" + i);
            user.setName("User " + i);
            user.setNumberOfPlaylists(i);
            users.add(user);
        }

        // When - Save one at a time
        long startTime = System.nanoTime();
        for (User user : users) {
            userRepository.save(user);
        }
        long endTime = System.nanoTime();

        long durationMs = (endTime - startTime) / 1_000_000;
        double avgLatencyMs = durationMs / (double) itemCount;

        // Then
        assertThat(userRepository.count()).isEqualTo(itemCount);

        // Performance metrics
        logger.info("=== Single Save Performance ===");
        logger.info("Total items: " + itemCount);
        logger.info("Total time: " + durationMs + " ms");
        logger.info("Average latency: " + String.format("%.2f", avgLatencyMs) + " ms/item");
        logger.info("Throughput: " + String.format("%.2f", itemCount * 1000.0 / durationMs) + " items/sec");
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Test 2: Benchmark - Batch save operations (100 items)")
    void benchmarkBatchSaveOperations() {
        // Given
        int itemCount = 100;
        List<User> users = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            User user = new User();
            user.setId("bench-batch-" + i);
            user.setName("User " + i);
            user.setNumberOfPlaylists(i);
            users.add(user);
        }

        // When - Batch save
        long startTime = System.nanoTime();
        userRepository.saveAll(users);
        long endTime = System.nanoTime();

        long durationMs = (endTime - startTime) / 1_000_000;
        double avgLatencyMs = durationMs / (double) itemCount;

        // Then
        assertThat(userRepository.count()).isEqualTo(itemCount);

        // Performance metrics
        logger.info("=== Batch Save Performance ===");
        logger.info("Total items: " + itemCount);
        logger.info("Total time: " + durationMs + " ms");
        logger.info("Average latency: " + String.format("%.2f", avgLatencyMs) + " ms/item");
        logger.info("Throughput: " + String.format("%.2f", itemCount * 1000.0 / durationMs) + " items/sec");
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Test 3: Benchmark - Compare single vs batch write performance")
    void benchmarkSingleVsBatchWrite() {
        // Test single writes
        int itemCount = 50;

        // Single write benchmark
        List<User> singleUsers = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            User user = new User();
            user.setId("compare-single-" + i);
            user.setName("User " + i);
            singleUsers.add(user);
        }

        long singleStart = System.nanoTime();
        for (User user : singleUsers) {
            userRepository.save(user);
        }
        long singleDuration = (System.nanoTime() - singleStart) / 1_000_000;

        // Batch write benchmark
        userRepository.deleteAll();
        List<User> batchUsers = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            User user = new User();
            user.setId("compare-batch-" + i);
            user.setName("User " + i);
            batchUsers.add(user);
        }

        long batchStart = System.nanoTime();
        userRepository.saveAll(batchUsers);
        long batchDuration = (System.nanoTime() - batchStart) / 1_000_000;

        // Performance comparison
        double speedup = singleDuration / (double) batchDuration;

        logger.info("=== Write Performance Comparison ===");
        logger.info("Single writes: " + singleDuration + " ms (" + String.format("%.2f", singleDuration / (double) itemCount) + " ms/item)");
        logger.info("Batch writes: " + batchDuration + " ms (" + String.format("%.2f", batchDuration / (double) itemCount) + " ms/item)");
        logger.info("Batch speedup: " + String.format("%.2fx", speedup) + " faster");

        assertThat(speedup).isGreaterThan(1.0); // Batch should be faster
    }

    // ==================== Read Performance ====================

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Test 4: Benchmark - Single read operations (100 items)")
    void benchmarkSingleReadOperations() {
        // Given - Pre-populate data
        int itemCount = 100;
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            String id = "read-single-" + i;
            User user = new User();
            user.setId(id);
            user.setName("User " + i);
            userRepository.save(user);
            ids.add(id);
        }

        // When - Read one at a time
        long startTime = System.nanoTime();
        for (String id : ids) {
            userRepository.findById(id);
        }
        long endTime = System.nanoTime();

        long durationMs = (endTime - startTime) / 1_000_000;
        double avgLatencyMs = durationMs / (double) itemCount;

        // Performance metrics
        logger.info("=== Single Read Performance ===");
        logger.info("Total items: " + itemCount);
        logger.info("Total time: " + durationMs + " ms");
        logger.info("Average latency: " + String.format("%.2f", avgLatencyMs) + " ms/item");
        logger.info("Throughput: " + String.format("%.2f", itemCount * 1000.0 / durationMs) + " items/sec");
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("Test 5: Benchmark - Batch read operations (100 items)")
    void benchmarkBatchReadOperations() {
        // Given - Pre-populate data
        int itemCount = 100;
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            String id = "read-batch-" + i;
            User user = new User();
            user.setId(id);
            user.setName("User " + i);
            userRepository.save(user);
            ids.add(id);
        }

        // When - Batch read
        long startTime = System.nanoTime();
        List<User> users = (List<User>) userRepository.findAllById(ids);
        long endTime = System.nanoTime();

        long durationMs = (endTime - startTime) / 1_000_000;
        double avgLatencyMs = durationMs / (double) itemCount;

        // Performance metrics
        logger.info("=== Batch Read Performance ===");
        logger.info("Total items: " + itemCount);
        logger.info("Total time: " + durationMs + " ms");
        logger.info("Average latency: " + String.format("%.2f", avgLatencyMs) + " ms/item");
        logger.info("Throughput: " + String.format("%.2f", itemCount * 1000.0 / durationMs) + " items/sec");

        assertThat(users).hasSize(itemCount);
    }

    // ==================== Query vs Scan Performance ====================

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("Test 6: Benchmark - Scan with filter performance")
    void benchmarkQueryPerformance() {
        // Given - Create 100 users
        for (int i = 0; i < 100; i++) {
            User user = new User();
            user.setId("query-bench-" + String.format("%03d", i));
            user.setName("User " + i);
            user.setNumberOfPlaylists(i);
            userRepository.save(user);
        }

        // When - Scan with filter
        long startTime = System.nanoTime();
        List<User> results = (List<User>) userRepository.findAll();
        // Filter in application
        results = results.stream()
                .filter(u -> u.getId() != null && u.getId().startsWith("query-bench-"))
                .toList();
        long endTime = System.nanoTime();

        long durationMs = (endTime - startTime) / 1_000_000;

        // Performance metrics
        logger.info("=== Scan Performance ===");
        logger.info("Items found: " + results.size());
        logger.info("Scan time: " + durationMs + " ms");

        assertThat(results).hasSize(100);
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("Test 7: Benchmark - Scan performance")
    void benchmarkScanPerformance() {
        // Given - Create 100 users
        for (int i = 0; i < 100; i++) {
            User user = new User();
            user.setId("scan-bench-" + i);
            user.setName("User " + i);
            user.setNumberOfPlaylists(i);
            userRepository.save(user);
        }

        // When - Full table scan
        long startTime = System.nanoTime();
        List<User> results = (List<User>) userRepository.findAll();
        long endTime = System.nanoTime();

        long durationMs = (endTime - startTime) / 1_000_000;

        // Performance metrics
        logger.info("=== Scan Performance ===");
        logger.info("Items found: " + results.size());
        logger.info("Scan time: " + durationMs + " ms");

        assertThat(results.size()).isGreaterThanOrEqualTo(100);
    }

    // ==================== Concurrent Operation Performance ====================

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("Test 8: Benchmark - Concurrent write operations")
    void benchmarkConcurrentWrites() throws InterruptedException {
        // Given
        int threadCount = 10;
        int itemsPerThread = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // When - Concurrent writes
        long startTime = System.nanoTime();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < itemsPerThread; i++) {
                        User user = new User();
                        user.setId("concurrent-t" + threadId + "-i" + i);
                        user.setName("Thread " + threadId + " Item " + i);
                        userRepository.save(user);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        long endTime = System.nanoTime();

        executor.shutdown();

        long durationMs = (endTime - startTime) / 1_000_000;
        int totalItems = threadCount * itemsPerThread;

        // Performance metrics
        logger.info("=== Concurrent Write Performance ===");
        logger.info("Threads: " + threadCount);
        logger.info("Items per thread: " + itemsPerThread);
        logger.info("Total items: " + totalItems);
        logger.info("Total time: " + durationMs + " ms");
        logger.info("Throughput: " + String.format("%.2f", totalItems * 1000.0 / durationMs) + " items/sec");

        assertThat(userRepository.count()).isEqualTo(totalItems);
    }

    // ==================== Complex Operation Performance ====================

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("Test 9: Benchmark - LSI query with range conditions")
    void benchmarkLSIQueryWithRange() {
        // Given - Create orders with different dates
        String customerId = "benchmark-customer";
        Instant now = Instant.now();

        for (int i = 0; i < 100; i++) {
            Instant orderDate = now.minusSeconds(i * 3600); // One hour apart
            ProductOrder order = new ProductOrder(
                    customerId,
                    "order-" + i,
                    orderDate,
                    "COMPLETED",
                    100.0 + i,
                    "Product " + i,
                    1
            );
            orderRepository.save(order);
        }

        // When - Query with date range (last 50 hours)
        Instant cutoffDate = now.minusSeconds(50 * 3600);

        long startTime = System.nanoTime();
        List<ProductOrder> results = orderRepository.findByCustomerIdAndOrderDateAfter(customerId, cutoffDate);
        long endTime = System.nanoTime();

        long durationMs = (endTime - startTime) / 1_000_000;

        // Performance metrics
        logger.info("=== LSI Query Performance ===");
        logger.info("Items found: " + results.size());
        logger.info("Query time: " + durationMs + " ms");

        assertThat(results.size()).isLessThanOrEqualTo(50);
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("Test 10: Benchmark - Update operations")
    void benchmarkUpdateOperations() {
        // Given - Pre-populate users
        int itemCount = 50;
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            String id = "update-bench-" + i;
            User user = new User();
            user.setId(id);
            user.setName("User " + i);
            user.setNumberOfPlaylists(0);
            userRepository.save(user);
            ids.add(id);
        }

        // When - Update all users
        long startTime = System.nanoTime();
        for (String id : ids) {
            Optional<User> userOpt = userRepository.findById(id);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                user.setNumberOfPlaylists(10);
                userRepository.save(user);
            }
        }
        long endTime = System.nanoTime();

        long durationMs = (endTime - startTime) / 1_000_000;
        double avgLatencyMs = durationMs / (double) itemCount;

        // Performance metrics
        logger.info("=== Update Operations Performance ===");
        logger.info("Total items: " + itemCount);
        logger.info("Total time: " + durationMs + " ms");
        logger.info("Average latency: " + String.format("%.2f", avgLatencyMs) + " ms/item");
        logger.info("Throughput: " + String.format("%.2f", itemCount * 1000.0 / durationMs) + " items/sec");
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    @DisplayName("Test 11: Benchmark - Delete operations")
    void benchmarkDeleteOperations() {
        // Given - Pre-populate users
        int itemCount = 50;
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            String id = "delete-bench-" + i;
            User user = new User();
            user.setId(id);
            user.setName("User " + i);
            userRepository.save(user);
            ids.add(id);
        }

        // When - Delete all users one by one
        long startTime = System.nanoTime();
        for (String id : ids) {
            userRepository.deleteById(id);
        }
        long endTime = System.nanoTime();

        long durationMs = (endTime - startTime) / 1_000_000;
        double avgLatencyMs = durationMs / (double) itemCount;

        // Performance metrics
        logger.info("=== Delete Operations Performance ===");
        logger.info("Total items: " + itemCount);
        logger.info("Total time: " + durationMs + " ms");
        logger.info("Average latency: " + String.format("%.2f", avgLatencyMs) + " ms/item");
        logger.info("Throughput: " + String.format("%.2f", itemCount * 1000.0 / durationMs) + " items/sec");

        assertThat(userRepository.count()).isEqualTo(0);
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    @DisplayName("Test 12: Benchmark - Memory overhead comparison")
    void benchmarkMemoryOverhead() {
        // Force garbage collection
        System.gc();
        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        // Create and save 1000 users
        int itemCount = 1000;
        for (int i = 0; i < itemCount; i++) {
            User user = new User();
            user.setId("memory-bench-" + i);
            user.setName("User with a reasonably long name " + i);
            user.setNumberOfPlaylists(i);
            userRepository.save(user);
        }

        // Force garbage collection
        System.gc();
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();

        long memoryUsedMB = (memoryAfter - memoryBefore) / (1024 * 1024);
        double memoryPerItemKB = (memoryAfter - memoryBefore) / (double) itemCount / 1024;

        logger.info("=== Memory Overhead ===");
        logger.info("Total items: " + itemCount);
        logger.info("Memory before: " + memoryBefore / (1024 * 1024) + " MB");
        logger.info("Memory after: " + memoryAfter / (1024 * 1024) + " MB");
        logger.info("Memory used: " + memoryUsedMB + " MB");
        logger.info("Memory per item: " + String.format("%.2f", memoryPerItemKB) + " KB");
    }
}
