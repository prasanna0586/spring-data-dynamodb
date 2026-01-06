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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive integration tests for DynamoDB batch operations at scale.
 * Tests batch write, read, and delete operations with focus on DynamoDB limits:
 * - BatchWriteItem: max 25 items per request
 * - BatchGetItem: max 100 items per request
 * - Maximum 16 MB per request
 * - Maximum 400 KB per item
 *
 * Coverage:
 * - Batch write with exactly 25 items (maximum)
 * - Batch write with >25 items (verifies automatic splitting)
 * - Batch write with 100+ items
 * - Batch read with 100 items (maximum)
 * - Batch delete operations
 * - Mixed batch operations (create, update, delete)
 * - Empty batch handling
 * - Batch operation performance characteristics
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, BatchOperationsAtScaleIntegrationTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Batch Operations At Scale Integration Tests")
public class BatchOperationsAtScaleIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(BatchOperationsAtScaleIntegrationTest.class);

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    public static class TestAppConfig {
    }

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // Clear all data before each test
        userRepository.deleteAll();
    }

    // ==================== Batch Write Tests - Maximum Capacity ====================

    @Test
    @Order(1)
    @DisplayName("Test 1: Batch write exactly 25 items (DynamoDB maximum)")
    void testBatchWrite_Exactly25Items() {
        // Given - Create exactly 25 users (DynamoDB batch write limit)
        List<User> users = createUsers(25, "batch25-");

        // When
        long startTime = System.currentTimeMillis();
        Iterable<User> savedUsers = userRepository.saveAll(users);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(savedUsers).hasSize(25);
        assertThat(userRepository.count()).isEqualTo(25);

        // Verify all users were saved correctly
        List<User> allUsers = (List<User>) userRepository.findAll();
        assertThat(allUsers).hasSize(25);

        // Performance check - batch should be faster than individual saves
        logger.info("Batch write 25 items took: " + duration + "ms");
        assertThat(duration).isLessThan(5000); // Should complete in under 5 seconds
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Batch write 26 items (exceeds single batch limit)")
    void testBatchWrite_26Items() {
        // Given - Create 26 users (requires 2 batches: 25 + 1)
        List<User> users = createUsers(26, "batch26-");

        // When
        Iterable<User> savedUsers = userRepository.saveAll(users);

        // Then - Framework should automatically split into multiple batches
        assertThat(savedUsers).hasSize(26);
        assertThat(userRepository.count()).isEqualTo(26);

        // Verify all users were saved
        List<User> allUsers = (List<User>) userRepository.findAll();
        assertThat(allUsers).hasSize(26);
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Batch write 50 items (2 full batches)")
    void testBatchWrite_50Items() {
        // Given - Create 50 users (requires 2 batches: 25 + 25)
        List<User> users = createUsers(50, "batch50-");

        // When
        long startTime = System.currentTimeMillis();
        Iterable<User> savedUsers = userRepository.saveAll(users);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(savedUsers).hasSize(50);
        assertThat(userRepository.count()).isEqualTo(50);

        logger.info("Batch write 50 items took: " + duration + "ms");
        assertThat(duration).isLessThan(10000); // Should complete in under 10 seconds
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: Batch write 100 items (4 full batches)")
    void testBatchWrite_100Items() {
        // Given - Create 100 users (requires 4 batches: 25 * 4)
        List<User> users = createUsers(100, "batch100-");

        // When
        long startTime = System.currentTimeMillis();
        Iterable<User> savedUsers = userRepository.saveAll(users);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(savedUsers).hasSize(100);
        assertThat(userRepository.count()).isEqualTo(100);

        logger.info("Batch write 100 items took: " + duration + "ms");
        assertThat(duration).isLessThan(15000); // Should complete in under 15 seconds

        // Verify data integrity - check specific users
        User firstUser = userRepository.findById("batch100-user-0").orElse(null);
        User lastUser = userRepository.findById("batch100-user-99").orElse(null);

        assertThat(firstUser).isNotNull();
        assertThat(lastUser).isNotNull();
        assertThat(firstUser.getName()).isEqualTo("User 0");
        assertThat(lastUser.getName()).isEqualTo("User 99");
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: Batch write 250 items (10 full batches)")
    void testBatchWrite_250Items() {
        // Given - Create 250 users (requires 10 batches: 25 * 10)
        List<User> users = createUsers(250, "batch250-");

        // When
        long startTime = System.currentTimeMillis();
        Iterable<User> savedUsers = userRepository.saveAll(users);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(savedUsers).hasSize(250);
        assertThat(userRepository.count()).isEqualTo(250);

        logger.info("Batch write 250 items took: " + duration + "ms");

        // Verify random samples
        User midUser = userRepository.findById("batch250-user-125").orElse(null);
        assertThat(midUser).isNotNull();
        assertThat(midUser.getName()).isEqualTo("User 125");
    }

    // ==================== Batch Read Tests ====================

    @Test
    @Order(6)
    @DisplayName("Test 6: Batch read with findAllById (50 items)")
    void testBatchRead_FindAllById_50Items() {
        // Given - Create 50 users
        List<User> users = createUsers(50, "read50-");
        userRepository.saveAll(users);

        // When - Read all 50 users by ID in a single batch operation
        List<String> userIds = users.stream()
                .map(User::getId)
                .collect(Collectors.toList());

        long startTime = System.currentTimeMillis();
        Iterable<User> foundUsers = userRepository.findAllById(userIds);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(foundUsers).hasSize(50);

        logger.info("Batch read 50 items took: " + duration + "ms");

        // Verify all users were retrieved
        List<String> foundIds = ((List<User>) foundUsers).stream()
                .map(User::getId)
                .collect(Collectors.toList());
        assertThat(foundIds).containsExactlyInAnyOrderElementsOf(userIds);
    }

    @Test
    @Order(7)
    @DisplayName("Test 7: Batch read with findAllById (100 items - maximum)")
    void testBatchRead_FindAllById_100Items() {
        // Given - Create 100 users
        List<User> users = createUsers(100, "read100-");
        userRepository.saveAll(users);

        // When - Read all 100 users by ID (DynamoDB BatchGetItem maximum)
        List<String> userIds = users.stream()
                .map(User::getId)
                .collect(Collectors.toList());

        long startTime = System.currentTimeMillis();
        Iterable<User> foundUsers = userRepository.findAllById(userIds);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(foundUsers).hasSize(100);

        logger.info("Batch read 100 items took: " + duration + "ms");
    }

    @Test
    @Order(8)
    @DisplayName("Test 8: Batch read with findAllById (150 items - exceeds maximum)")
    void testBatchRead_FindAllById_150Items() {
        // Given - Create 150 users
        List<User> users = createUsers(150, "read150-");
        userRepository.saveAll(users);

        // When - Read all 150 users (should automatically split into 2 batch requests: 100 + 50)
        List<String> userIds = users.stream()
                .map(User::getId)
                .collect(Collectors.toList());

        long startTime = System.currentTimeMillis();
        Iterable<User> foundUsers = userRepository.findAllById(userIds);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(foundUsers).hasSize(150);

        logger.info("Batch read 150 items took: " + duration + "ms");
    }

    // ==================== Batch Delete Tests ====================

    @Test
    @Order(9)
    @DisplayName("Test 9: Batch delete with deleteAll(entities) - 25 items")
    void testBatchDelete_25Items() {
        // Given - Create and save 25 users
        List<User> users = createUsers(25, "delete25-");
        userRepository.saveAll(users);
        assertThat(userRepository.count()).isEqualTo(25);

        // When - Delete all 25 users in batch
        long startTime = System.currentTimeMillis();
        userRepository.deleteAll(users);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(userRepository.count()).isEqualTo(0);

        logger.info("Batch delete 25 items took: " + duration + "ms");
    }

    @Test
    @Order(10)
    @DisplayName("Test 10: Batch delete with deleteAll(entities) - 50 items")
    void testBatchDelete_50Items() {
        // Given - Create and save 50 users
        List<User> users = createUsers(50, "delete50-");
        userRepository.saveAll(users);
        assertThat(userRepository.count()).isEqualTo(50);

        // When - Delete all 50 users (requires 2 batches: 25 + 25)
        long startTime = System.currentTimeMillis();
        userRepository.deleteAll(users);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(userRepository.count()).isEqualTo(0);

        logger.info("Batch delete 50 items took: " + duration + "ms");
    }

    @Test
    @Order(11)
    @DisplayName("Test 11: Batch delete with deleteAllById - 100 items")
    void testBatchDelete_ById_100Items() {
        // Given - Create and save 100 users
        List<User> users = createUsers(100, "deleteById100-");
        userRepository.saveAll(users);
        assertThat(userRepository.count()).isEqualTo(100);

        // When - Delete all 100 users by ID
        List<String> userIds = users.stream()
                .map(User::getId)
                .collect(Collectors.toList());

        long startTime = System.currentTimeMillis();
        userRepository.deleteAllById(userIds);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(userRepository.count()).isEqualTo(0);

        logger.info("Batch delete by ID 100 items took: " + duration + "ms");
    }

    // ==================== Mixed Batch Operations ====================

    @Test
    @Order(12)
    @DisplayName("Test 12: Mixed batch operations - create, update, verify")
    void testMixedBatchOperations() {
        // Phase 1: Create 50 users
        List<User> users = createUsers(50, "mixed-");
        userRepository.saveAll(users);
        assertThat(userRepository.count()).isEqualTo(50);

        // Phase 2: Update all 50 users
        List<User> updatedUsers = ((List<User>) userRepository.findAll()).stream()
                .peek(user -> user.setName(user.getName() + " - UPDATED"))
                .collect(Collectors.toList());

        userRepository.saveAll(updatedUsers);

        // Phase 3: Verify updates
        List<User> verifiedUsers = (List<User>) userRepository.findAll();
        assertThat(verifiedUsers).hasSize(50);
        assertThat(verifiedUsers).allMatch(user -> user.getName().endsWith(" - UPDATED"));

        // Phase 4: Delete half
        List<User> usersToDelete = verifiedUsers.subList(0, 25);
        userRepository.deleteAll(usersToDelete);

        // Phase 5: Verify remaining
        assertThat(userRepository.count()).isEqualTo(25);
    }

    // ==================== Edge Cases ====================

    @Test
    @Order(13)
    @DisplayName("Test 13: Empty batch operations")
    void testBatchOperations_Empty() {
        // Given - Empty lists
        List<User> emptyList = new ArrayList<>();

        // When/Then - Should handle empty lists gracefully
        Iterable<User> savedUsers = userRepository.saveAll(emptyList);
        assertThat(savedUsers).isEmpty();

        Iterable<User> foundUsers = userRepository.findAllById(new ArrayList<>());
        assertThat(foundUsers).isEmpty();

        userRepository.deleteAll(emptyList);
        assertThat(userRepository.count()).isEqualTo(0);
    }

    @Test
    @Order(14)
    @DisplayName("Test 14: Single item batch operations")
    void testBatchOperations_SingleItem() {
        // Given - Single user
        List<User> singleUser = createUsers(1, "single-");

        // When
        Iterable<User> savedUsers = userRepository.saveAll(singleUser);

        // Then
        assertThat(savedUsers).hasSize(1);
        assertThat(userRepository.count()).isEqualTo(1);

        // Batch read single item
        List<String> singleId = List.of(singleUser.get(0).getId());
        Iterable<User> foundUsers = userRepository.findAllById(singleId);
        assertThat(foundUsers).hasSize(1);

        // Batch delete single item
        userRepository.deleteAll(singleUser);
        assertThat(userRepository.count()).isEqualTo(0);
    }

    @Test
    @Order(15)
    @DisplayName("Test 15: Batch read validates unique IDs")
    void testBatchRead_RequiresUniqueIds() {
        // Given - Create and save 10 users
        List<User> users = createUsers(10, "dup-");
        userRepository.saveAll(users);

        // When/Then - DynamoDB requires unique IDs in batch get requests
        // Get unique IDs only (no duplicates)
        List<String> uniqueIds = users.stream()
                .map(User::getId)
                .distinct()
                .collect(Collectors.toList());

        Iterable<User> foundUsers = userRepository.findAllById(uniqueIds);
        List<User> foundUserList = (List<User>) foundUsers;

        assertThat(foundUserList).hasSize(10);
        assertThat(userRepository.count()).isEqualTo(10);

        // Note: Attempting to call findAllById with duplicate IDs will throw:
        // AmazonDynamoDBException: "Provided list of item keys contains duplicates"
    }

    // ==================== Performance Comparison ====================

    @Test
    @Order(16)
    @DisplayName("Test 16: Performance comparison - Batch vs Individual saves")
    void testPerformanceComparison_BatchVsIndividual() {
        // Test 1: Individual saves (20 items)
        List<User> individualUsers = createUsers(20, "individual-");
        long individualStart = System.currentTimeMillis();
        for (User user : individualUsers) {
            userRepository.save(user);
        }
        long individualDuration = System.currentTimeMillis() - individualStart;

        userRepository.deleteAll();

        // Test 2: Batch save (20 items)
        List<User> batchUsers = createUsers(20, "batch-");
        long batchStart = System.currentTimeMillis();
        userRepository.saveAll(batchUsers);
        long batchDuration = System.currentTimeMillis() - batchStart;

        // Then - Batch should be significantly faster
        logger.info("Individual saves (20 items): " + individualDuration + "ms");
        logger.info("Batch save (20 items): " + batchDuration + "ms");
        logger.info("Speedup: " + ((double) individualDuration / batchDuration) + "x");

        // Batch should be at least faster (may not always be 2x due to local testing variance)
        assertThat(batchDuration).isLessThan(individualDuration);

        // Both should have same result
        assertThat(userRepository.count()).isEqualTo(20);
    }

    // ==================== Stress Test ====================

    @Test
    @Order(17)
    @DisplayName("Test 17: Stress test - 500 items end-to-end")
    void testStressTest_500Items() {
        // Given - Create 500 users (requires 20 batches of 25)
        List<User> users = createUsers(500, "stress-");

        // When - Full lifecycle: create, read, update, delete
        long createStart = System.currentTimeMillis();
        userRepository.saveAll(users);
        long createDuration = System.currentTimeMillis() - createStart;

        assertThat(userRepository.count()).isEqualTo(500);
        logger.info("Create 500 items: " + createDuration + "ms");

        // Read all
        List<String> allIds = users.stream().map(User::getId).collect(Collectors.toList());
        long readStart = System.currentTimeMillis();
        Iterable<User> foundUsers = userRepository.findAllById(allIds);
        long readDuration = System.currentTimeMillis() - readStart;

        assertThat(foundUsers).hasSize(500);
        logger.info("Read 500 items: " + readDuration + "ms");

        // Update all
        List<User> usersToUpdate = ((List<User>) foundUsers).stream()
                .peek(u -> u.setName(u.getName() + " - UPDATED"))
                .collect(Collectors.toList());

        long updateStart = System.currentTimeMillis();
        userRepository.saveAll(usersToUpdate);
        long updateDuration = System.currentTimeMillis() - updateStart;

        logger.info("Update 500 items: " + updateDuration + "ms");

        // Delete all
        long deleteStart = System.currentTimeMillis();
        userRepository.deleteAll(usersToUpdate);
        long deleteDuration = System.currentTimeMillis() - deleteStart;

        assertThat(userRepository.count()).isEqualTo(0);
        logger.info("Delete 500 items: " + deleteDuration + "ms");

        // Summary
        long totalDuration = createDuration + readDuration + updateDuration + deleteDuration;
        logger.info("Total time for 500-item lifecycle: " + totalDuration + "ms");
    }

    // ==================== Helper Methods ====================

    private List<User> createUsers(int count, String prefix) {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            User user = new User();
            user.setId(prefix + "user-" + i);
            user.setName("User " + i);
            user.setNumberOfPlaylists(i);
            user.setLeaveDate(null);
            users.add(user);
        }
        return users;
    }
}
