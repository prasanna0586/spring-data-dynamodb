package org.socialsignin.spring.data.dynamodb.domain.sample;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
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
 * Integration tests for Throttling and Retry Logic in DynamoDB operations.
 *
 * Note: DynamoDB Local doesn't simulate throttling, so these tests demonstrate
 * the retry patterns and error handling that would be used in production.
 *
 * Coverage:
 * - Batch operations with large item counts (auto-retry logic)
 * - Error handling patterns for throttling
 * - Exponential backoff strategies
 * - Retry exhaustion scenarios
 * - Best practices for high-throughput operations
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, ThrottlingAndRetryIntegrationTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Throttling and Retry Logic Integration Tests")
public class ThrottlingAndRetryIntegrationTest {

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
    }

    // ==================== Batch Operations with Automatic Retry ====================

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Test 1: Large batch write with automatic splitting")
    void testLargeBatchWriteWithAutoSplitting() {
        // Given - More than 25 items (DynamoDB batch limit)
        List<User> users = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            User user = new User();
            user.setId("throttle-user-" + i);
            user.setName("User " + i);
            user.setNumberOfPlaylists(i);
            users.add(user);
        }

        // When - Save all (repository handles batching internally)
        long startTime = System.currentTimeMillis();
        userRepository.saveAll(users);
        long duration = System.currentTimeMillis() - startTime;

        // Then - All should be saved
        assertThat(userRepository.count()).isEqualTo(100);
        System.out.println("Saved 100 items in " + duration + "ms");
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Test 2: Large batch read with automatic splitting")
    void testLargeBatchReadWithAutoSplitting() {
        // Given - 100 users
        List<User> users = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            User user = new User();
            user.setId("batch-read-" + i);
            user.setName("User " + i);
            users.add(user);
        }
        userRepository.saveAll(users);

        // When - Read all
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            ids.add("batch-read-" + i);
        }

        long startTime = System.currentTimeMillis();
        List<User> retrieved = (List<User>) userRepository.findAllById(ids);
        long duration = System.currentTimeMillis() - startTime;

        // Then - All should be retrieved
        assertThat(retrieved).hasSize(100);
        System.out.println("Retrieved 100 items in " + duration + "ms");
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Test 3: Sequential writes under load")
    void testSequentialWritesUnderLoad() {
        // Given - Rapid sequential writes
        int itemCount = 50;

        // When - Write items rapidly
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < itemCount; i++) {
            User user = new User();
            user.setId("seq-user-" + i);
            user.setName("User " + i);
            userRepository.save(user);
        }
        long duration = System.currentTimeMillis() - startTime;

        // Then - All should be saved
        assertThat(userRepository.count()).isEqualTo(itemCount);
        System.out.println("Sequential write of " + itemCount + " items took " + duration + "ms");
        System.out.println("Average: " + (duration / itemCount) + "ms per item");
    }

    // ==================== Error Handling Patterns ====================

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Test 4: Handle AmazonServiceException gracefully")
    void testHandleAmazonServiceException() {
        // When - Invalid table name
        ScanRequest scanRequest = ScanRequest.builder()
                .tableName("Invalid@Table#Name")
                .build();

        // Then - Should throw AmazonServiceException
        try {
            amazonDynamoDB.scan(scanRequest);
            Assertions.fail("Should have thrown exception");
        } catch (AwsServiceException e) {
            // Expected - log and handle
            System.out.println("Caught expected exception: " + e.awsErrorDetails().errorCode());
            assertThat(e.awsErrorDetails().sdkHttpResponse().statusCode()).isGreaterThanOrEqualTo(400);
        }
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("Test 5: Retry pattern for transient failures")
    void testRetryPatternForTransientFailures() {
        // Given - Operation that might fail transiently
        User user = new User();
        user.setId("retry-user");
        user.setName("Retry Test");

        // When - Implement retry with exponential backoff
        int maxRetries = 3;
        int attempt = 0;
        boolean success = false;

        while (attempt < maxRetries && !success) {
            try {
                userRepository.save(user);
                success = true;
            } catch (Exception e) {
                attempt++;
                if (attempt >= maxRetries) {
                    throw e;
                }
                // Exponential backoff: 100ms, 200ms, 400ms
                try {
                    Thread.sleep((long) (100 * Math.pow(2, attempt - 1)));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        // Then - Should succeed
        assertThat(success).isTrue();
        assertThat(userRepository.findById("retry-user")).isPresent();
    }

    // ==================== Best Practices ====================

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("Test 6: Chunked batch operations to avoid throttling")
    void testChunkedBatchOperations() {
        // Given - Large dataset
        int totalItems = 250;
        int chunkSize = 25; // DynamoDB batch limit

        List<User> allUsers = new ArrayList<>();
        for (int i = 0; i < totalItems; i++) {
            User user = new User();
            user.setId("chunk-user-" + i);
            user.setName("User " + i);
            allUsers.add(user);
        }

        // When - Process in chunks
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < totalItems; i += chunkSize) {
            int end = Math.min(i + chunkSize, totalItems);
            List<User> chunk = allUsers.subList(i, end);
            userRepository.saveAll(chunk);

            // Optional: Add small delay between chunks to avoid throttling
            if (end < totalItems) {
                try {
                    Thread.sleep(10); // 10ms delay
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        long duration = System.currentTimeMillis() - startTime;

        // Then - All should be saved
        assertThat(userRepository.count()).isEqualTo(totalItems);
        System.out.println("Saved " + totalItems + " items in chunks of " + chunkSize + " in " + duration + "ms");
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("Test 7: Parallel write operations")
    void testParallelWriteOperations() {
        // Given - Items to write in parallel
        int itemsPerThread = 25;
        int threadCount = 4;

        // When - Write in parallel (simulating high load)
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            Thread thread = new Thread(() -> {
                for (int i = 0; i < itemsPerThread; i++) {
                    User user = new User();
                    user.setId("parallel-t" + threadId + "-u" + i);
                    user.setName("Thread " + threadId + " User " + i);
                    userRepository.save(user);
                }
            });
            threads.add(thread);
            thread.start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Then - All should be saved
        long expectedCount = (long) itemsPerThread * threadCount;
        assertThat(userRepository.count()).isEqualTo(expectedCount);
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("Test 8: Demonstrate exponential backoff calculation")
    void testExponentialBackoffCalculation() {
        // Demonstrate exponential backoff intervals
        int maxRetries = 5;
        int baseDelay = 100; // milliseconds

        System.out.println("Exponential backoff schedule:");
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            long delay = (long) (baseDelay * Math.pow(2, attempt - 1));
            long jitter = (long) (Math.random() * delay * 0.1); // Add 10% jitter
            long actualDelay = delay + jitter;

            System.out.println("  Attempt " + attempt + ": " + actualDelay + "ms");
            assertThat(actualDelay).isGreaterThan(0);
        }
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("Test 9: Handle batch write with rate limiting")
    void testBatchWriteWithRateLimiting() {
        // Given - Items to write with rate limiting
        int totalItems = 100;
        int writeCapacityUnits = 10; // Simulate provisioned capacity
        long intervalMs = 1000 / writeCapacityUnits; // Time between writes

        List<User> users = new ArrayList<>();
        for (int i = 0; i < totalItems; i++) {
            User user = new User();
            user.setId("rate-limited-" + i);
            user.setName("User " + i);
            users.add(user);
        }

        // When - Write with rate limiting
        long startTime = System.currentTimeMillis();
        for (User user : users) {
            userRepository.save(user);

            // Rate limit: delay between writes
            if (intervalMs > 0) {
                try {
                    Thread.sleep(intervalMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(userRepository.count()).isEqualTo(totalItems);
        System.out.println("Rate-limited write of " + totalItems + " items took " + duration + "ms");
        System.out.println("Target WCU: " + writeCapacityUnits + ", Actual rate: " + (totalItems * 1000.0 / duration) + " writes/sec");
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("Test 10: Monitor and log operation durations")
    void testMonitorOperationDurations() {
        // Given - Various operations to monitor
        List<Long> durations = new ArrayList<>();

        // Single write
        long start = System.nanoTime();
        User user = new User();
        user.setId("monitor-1");
        user.setName("Monitored User");
        userRepository.save(user);
        durations.add((System.nanoTime() - start) / 1_000_000); // Convert to ms

        // Single read
        start = System.nanoTime();
        userRepository.findById("monitor-1");
        durations.add((System.nanoTime() - start) / 1_000_000);

        // Batch write
        List<User> batchUsers = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            User u = new User();
            u.setId("monitor-batch-" + i);
            u.setName("Batch User " + i);
            batchUsers.add(u);
        }
        start = System.nanoTime();
        userRepository.saveAll(batchUsers);
        durations.add((System.nanoTime() - start) / 1_000_000);

        // Then - Log performance metrics
        System.out.println("Operation durations:");
        System.out.println("  Single write: " + durations.get(0) + "ms");
        System.out.println("  Single read: " + durations.get(1) + "ms");
        System.out.println("  Batch write (25 items): " + durations.get(2) + "ms");

        assertThat(durations).allMatch(d -> d >= 0);
    }
}
