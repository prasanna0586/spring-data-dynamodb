package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.socialsignin.spring.data.dynamodb.utils.DynamoDBLocalResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests demonstrating async operation patterns for SDK v2 migration.
 *
 * SDK v2 provides native CompletableFuture support for async operations.
 * These tests demonstrate async patterns that can be used after migration:
 * - Parallel independent operations
 * - Async batch operations
 * - Non-blocking I/O patterns
 * - Error handling in async context
 * - Combining multiple async operations
 *
 * NOTE: Current implementation uses sync SDK v1 with ExecutorService to simulate
 * async patterns. After SDK v2 migration, these can use native async client.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, AsyncOperationPatternsIntegrationTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Async Operation Patterns Integration Tests")
public class AsyncOperationPatternsIntegrationTest {

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    public static class TestAppConfig {
    }

    @Autowired
    private UserRepository userRepository;

    private ExecutorService executorService;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        executorService = Executors.newFixedThreadPool(10);
    }

    @AfterEach
    void tearDown() {
        executorService.shutdown();
    }

    // ==================== Parallel Independent Operations ====================

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Test 1: Async pattern - Parallel independent writes")
    void testAsyncParallelIndependentWrites() throws Exception {
        // Given - Multiple independent write operations
        int operationCount = 10;
        List<CompletableFuture<User>> futures = new ArrayList<>();

        // When - Execute writes in parallel
        for (int i = 0; i < operationCount; i++) {
            final int index = i;
            CompletableFuture<User> future = CompletableFuture.supplyAsync(() -> {
                User user = new User();
                user.setId("async-write-" + index);
                user.setName("User " + index);
                user.setNumberOfPlaylists(index);
                return userRepository.save(user);
            }, executorService);

            futures.add(future);
        }

        // Wait for all to complete
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );
        allOf.get(10, TimeUnit.SECONDS);

        // Then - All writes should succeed
        assertThat(userRepository.count()).isEqualTo(operationCount);

        for (CompletableFuture<User> future : futures) {
            assertThat(future).isCompleted();
            assertThat(future.get()).isNotNull();
        }

        System.out.println("Completed " + operationCount + " async write operations");
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Test 2: Async pattern - Parallel independent reads")
    void testAsyncParallelIndependentReads() throws Exception {
        // Given - Pre-populate data
        int itemCount = 10;
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < itemCount; i++) {
            String id = "async-read-" + i;
            User user = new User();
            user.setId(id);
            user.setName("User " + i);
            userRepository.save(user);
            ids.add(id);
        }

        // When - Execute reads in parallel
        List<CompletableFuture<Optional<User>>> futures = new ArrayList<>();

        for (String id : ids) {
            CompletableFuture<Optional<User>> future = CompletableFuture.supplyAsync(() ->
                    userRepository.findById(id), executorService
            );
            futures.add(future);
        }

        // Wait for all to complete
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );
        allOf.get(10, TimeUnit.SECONDS);

        // Then - All reads should succeed
        for (CompletableFuture<Optional<User>> future : futures) {
            assertThat(future).isCompleted();
            assertThat(future.get()).isPresent();
        }

        System.out.println("Completed " + itemCount + " async read operations");
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Test 3: Async pattern - Mix read and write operations")
    void testAsyncMixedReadWriteOperations() throws Exception {
        // Given - Some existing data
        for (int i = 0; i < 5; i++) {
            User user = new User();
            user.setId("existing-" + i);
            user.setName("Existing User " + i);
            userRepository.save(user);
        }

        // When - Mix of reads and writes
        List<CompletableFuture<?>> futures = new ArrayList<>();

        // Async reads
        for (int i = 0; i < 5; i++) {
            String id = "existing-" + i;
            CompletableFuture<Optional<User>> future = CompletableFuture.supplyAsync(() ->
                    userRepository.findById(id), executorService
            );
            futures.add(future);
        }

        // Async writes
        for (int i = 5; i < 10; i++) {
            final int index = i;
            CompletableFuture<User> future = CompletableFuture.supplyAsync(() -> {
                User user = new User();
                user.setId("new-" + index);
                user.setName("New User " + index);
                return userRepository.save(user);
            }, executorService);
            futures.add(future);
        }

        // Wait for all
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );
        allOf.get(10, TimeUnit.SECONDS);

        // Then
        assertThat(userRepository.count()).isEqualTo(10);
        System.out.println("Completed mixed async operations: " + futures.size());
    }

    // ==================== Async Composition ====================

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Test 4: Async pattern - Sequential composition (read then update)")
    void testAsyncSequentialComposition() throws Exception {
        // Given - Initial user
        User initialUser = new User();
        initialUser.setId("compose-user");
        initialUser.setName("Initial Name");
        initialUser.setNumberOfPlaylists(5);
        userRepository.save(initialUser);

        // When - Read, then update (chained async operations)
        CompletableFuture<User> readFuture = CompletableFuture.supplyAsync(() ->
                userRepository.findById("compose-user").orElseThrow(), executorService
        );

        CompletableFuture<User> updateFuture = readFuture.thenApplyAsync(user -> {
            user.setName("Updated Name");
            user.setNumberOfPlaylists(10);
            return userRepository.save(user);
        }, executorService);

        User result = updateFuture.get(10, TimeUnit.SECONDS);

        // Then
        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getNumberOfPlaylists()).isEqualTo(10);

        User persisted = userRepository.findById("compose-user").orElseThrow();
        assertThat(persisted.getName()).isEqualTo("Updated Name");

        System.out.println("Async composition completed: read -> update");
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("Test 5: Async pattern - Combine multiple async results")
    void testAsyncCombineMultipleResults() throws Exception {
        // Given - Create multiple users
        for (int i = 0; i < 5; i++) {
            User user = new User();
            user.setId("combine-user-" + i);
            user.setName("User " + i);
            user.setNumberOfPlaylists(i * 10);
            userRepository.save(user);
        }

        // When - Fetch multiple users and combine results
        CompletableFuture<Optional<User>> future1 = CompletableFuture.supplyAsync(() ->
                userRepository.findById("combine-user-0"), executorService
        );

        CompletableFuture<Optional<User>> future2 = CompletableFuture.supplyAsync(() ->
                userRepository.findById("combine-user-1"), executorService
        );

        CompletableFuture<Optional<User>> future3 = CompletableFuture.supplyAsync(() ->
                userRepository.findById("combine-user-2"), executorService
        );

        // Combine results - calculate total playlists
        CompletableFuture<Integer> combinedFuture = CompletableFuture.allOf(future1, future2, future3)
                .thenApply(v -> {
                    int total = 0;
                    if (future1.join().isPresent()) total += future1.join().get().getNumberOfPlaylists();
                    if (future2.join().isPresent()) total += future2.join().get().getNumberOfPlaylists();
                    if (future3.join().isPresent()) total += future3.join().get().getNumberOfPlaylists();
                    return total;
                });

        Integer totalPlaylists = combinedFuture.get(10, TimeUnit.SECONDS);

        // Then - Total should be 0 + 10 + 20 = 30
        assertThat(totalPlaylists).isEqualTo(30);
        System.out.println("Combined async results: total playlists = " + totalPlaylists);
    }

    // ==================== Error Handling in Async Context ====================

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("Test 6: Async pattern - Error handling with exceptionally")
    void testAsyncErrorHandlingExceptionally() throws Exception {
        // When - Attempt to read non-existent item with error handling
        CompletableFuture<User> future = CompletableFuture.supplyAsync(() -> {
            Optional<User> userOpt = userRepository.findById("non-existent");
            if (!userOpt.isPresent()) {
                throw new RuntimeException("User not found");
            }
            return userOpt.get();
        }, executorService).exceptionally(throwable -> {
            System.out.println("Handled exception: " + throwable.getMessage());
            // Return default user
            User defaultUser = new User();
            defaultUser.setId("default-user");
            defaultUser.setName("Default User");
            return defaultUser;
        });

        User result = future.get(10, TimeUnit.SECONDS);

        // Then - Should get default user
        assertThat(result.getId()).isEqualTo("default-user");
        assertThat(result.getName()).isEqualTo("Default User");
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("Test 7: Async pattern - Error handling with handle")
    void testAsyncErrorHandlingWithHandle() throws Exception {
        // When - Operation that may fail
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            Optional<User> userOpt = userRepository.findById("non-existent");
            return userOpt.map(User::getName).orElseThrow(() ->
                    new RuntimeException("User not found")
            );
        }, executorService).handle((result, throwable) -> {
            if (throwable != null) {
                return "Error: " + throwable.getMessage();
            }
            return "Success: " + result;
        });

        String result = future.get(10, TimeUnit.SECONDS);

        // Then
        assertThat(result).contains("Error");
        System.out.println("Handled result: " + result);
    }

    // ==================== Timeout Handling ====================

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("Test 8: Async pattern - Timeout handling")
    void testAsyncTimeoutHandling() {
        // When - Operation with timeout
        CompletableFuture<User> future = CompletableFuture.supplyAsync(() -> {
            try {
                // Simulate slow operation
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            User user = new User();
            user.setId("timeout-user");
            user.setName("User");
            return userRepository.save(user);
        }, executorService);

        // Then - Should complete within timeout
        assertThat(future).succeedsWithin(5, TimeUnit.SECONDS);
        System.out.println("Async operation completed within timeout");
    }

    // ==================== Async Batch Operations ====================

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("Test 9: Async pattern - Batch write with async")
    void testAsyncBatchWrite() throws Exception {
        // Given - Large batch of users
        List<User> users = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            User user = new User();
            user.setId("async-batch-" + i);
            user.setName("User " + i);
            users.add(user);
        }

        // When - Async batch save
        CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
                userRepository.saveAll(users), executorService
        );

        future.get(30, TimeUnit.SECONDS);

        // Then
        assertThat(userRepository.count()).isEqualTo(100);
        System.out.println("Async batch write completed: 100 items");
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("Test 10: Async pattern - Parallel batch operations")
    void testAsyncParallelBatchOperations() throws Exception {
        // Given - Multiple batches
        int batchCount = 5;
        int itemsPerBatch = 20;
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // When - Process batches in parallel
        for (int b = 0; b < batchCount; b++) {
            final int batchIndex = b;
            List<User> batch = new ArrayList<>();

            for (int i = 0; i < itemsPerBatch; i++) {
                User user = new User();
                user.setId("parallel-batch-" + batchIndex + "-" + i);
                user.setName("Batch " + batchIndex + " User " + i);
                batch.add(user);
            }

            CompletableFuture<Void> future = CompletableFuture.runAsync(() ->
                    userRepository.saveAll(batch), executorService
            );

            futures.add(future);
        }

        CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );
        allOf.get(30, TimeUnit.SECONDS);

        // Then
        assertThat(userRepository.count()).isEqualTo(batchCount * itemsPerBatch);
        System.out.println("Parallel batch operations completed: " + batchCount + " batches, " +
                (batchCount * itemsPerBatch) + " total items");
    }

    // ==================== Async Query Operations ====================

    @Test
    @org.junit.jupiter.api.Order(11)
    @DisplayName("Test 11: Async pattern - Parallel data retrieval operations")
    void testAsyncParallelQueries() throws Exception {
        // Given - Users with various attributes
        for (int i = 0; i < 20; i++) {
            User user = new User();
            user.setId("query-user-" + i);
            user.setName("User " + i);
            user.setPostCode(i % 2 == 0 ? "12345" : "67890");
            user.setNumberOfPlaylists(i);
            userRepository.save(user);
        }

        // When - Execute multiple data retrieval operations in parallel
        CompletableFuture<List<User>> query1 = CompletableFuture.supplyAsync(() -> {
            // Scan and filter for postCode = "12345"
            List<User> allUsers = (List<User>) userRepository.findAll();
            return allUsers.stream()
                    .filter(u -> "12345".equals(u.getPostCode()))
                    .toList();
        }, executorService);

        CompletableFuture<List<User>> query2 = CompletableFuture.supplyAsync(() -> {
            // Scan and filter for postCode = "67890"
            List<User> allUsers = (List<User>) userRepository.findAll();
            return allUsers.stream()
                    .filter(u -> "67890".equals(u.getPostCode()))
                    .toList();
        }, executorService);

        CompletableFuture<Void> allOf = CompletableFuture.allOf(query1, query2);
        allOf.get(10, TimeUnit.SECONDS);

        // Then
        List<User> result1 = query1.get();
        List<User> result2 = query2.get();

        assertThat(result1).hasSize(10);
        assertThat(result2).hasSize(10);

        System.out.println("Parallel queries completed: Query1=" + result1.size() +
                " items, Query2=" + result2.size() + " items");
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    @DisplayName("Test 12: Async pattern - Fire and forget operations")
    void testAsyncFireAndForget() throws Exception {
        // When - Fire and forget writes (don't wait for completion)
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            final int index = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                User user = new User();
                user.setId("fire-forget-" + index);
                user.setName("User " + index);
                userRepository.save(user);
            }, executorService);

            futures.add(future);
        }

        // Eventually wait to verify in test
        Thread.sleep(2000);

        // Then - Should eventually be saved
        assertThat(userRepository.count()).isGreaterThanOrEqualTo(10);
        System.out.println("Fire and forget operations completed");
    }
}
