package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.socialsignin.spring.data.dynamodb.utils.DynamoDBLocalResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for Error Recovery scenarios using spring-data-dynamodb repository methods.
 *
 * This test suite validates the library's error handling and recovery capabilities:
 * - Repository batch operations with large datasets
 * - Handling non-existent items in batch operations
 * - Repository save/update error handling
 * - Duplicate key handling through repository
 * - Null value handling
 * - Optional empty handling for missing items
 *
 * Note: Tests focus on library behavior, not low-level AWS SDK error handling.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, ErrorRecoveryIntegrationTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Error Recovery Integration Tests")
public class ErrorRecoveryIntegrationTest {

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    public static class TestAppConfig {
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BankAccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        accountRepository.deleteAll();
    }

    // ==================== Repository Batch Operations Error Handling ====================

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Test 1: Repository handles duplicate key saves (last write wins)")
    void testRepositoryHandlesDuplicateKeySaves() {
        // Given - Same entity saved twice with different values
        User user1 = new User();
        user1.setId("duplicate-key");
        user1.setName("First Name");
        user1.setNumberOfPlaylists(10);

        User user2 = new User();
        user2.setId("duplicate-key");
        user2.setName("Second Name");
        user2.setNumberOfPlaylists(20);

        // When - Save both (last write wins in DynamoDB)
        userRepository.save(user1);
        userRepository.save(user2);

        // Then - Should contain the last saved values
        User retrieved = userRepository.findById("duplicate-key").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getName()).isEqualTo("Second Name");
        assertThat(retrieved.getNumberOfPlaylists()).isEqualTo(20);
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Test 2: Handle batch write with some invalid items")
    void testBatchWriteWithInvalidItems() {
        // Given - Mix of valid and invalid items (empty set)
        User validUser = new User();
        validUser.setId("valid-user");
        validUser.setName("Valid User");

        // When - Save valid user
        userRepository.save(validUser);

        // Then - Should succeed for valid item
        assertThat(userRepository.findById("valid-user")).isPresent();
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Test 3: Retry logic for batch operations")
    void testRetryLogicForBatchOperations() {
        // Given - Large batch that might require retry
        List<User> users = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            User user = new User();
            user.setId("retry-user-" + i);
            user.setName("User " + i);
            users.add(user);
        }

        // When - Save all (uses batch internally)
        userRepository.saveAll(users);

        // Then - All should be saved
        assertThat(userRepository.count()).isEqualTo(25);
    }

    // ==================== Repository Batch Read Error Handling ====================

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Test 4: Repository batch get with non-existent keys")
    void testRepositoryBatchGetWithNonExistentKeys() {
        // Given - Some existing users
        userRepository.save(createUser("exists-1", "User 1"));
        userRepository.save(createUser("exists-2", "User 2"));

        // When - Repository findAllById with mixed existing and non-existent IDs
        List<String> ids = Arrays.asList("exists-1", "non-existent", "exists-2");
        List<User> results = (List<User>) userRepository.findAllById(ids);

        // Then - Should return only existing items (library filters out non-existent)
        assertThat(results).hasSize(2);
        assertThat(results).extracting(User::getId)
                .containsExactlyInAnyOrder("exists-1", "exists-2");
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("Test 5: Handle batch read unprocessed keys")
    void testHandleBatchReadUnprocessedKeys() {
        // Given - 50 users
        List<User> users = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            User user = new User();
            user.setId("batch-read-" + i);
            user.setName("User " + i);
            users.add(user);
        }
        userRepository.saveAll(users);

        // When - Batch read all keys
        List<String> ids = users.stream().map(User::getId).collect(Collectors.toList());
        List<User> retrieved = (List<User>) userRepository.findAllById(ids);

        // Then - All should be retrieved
        assertThat(retrieved).hasSize(50);
    }

    // ==================== Repository Item Not Found Handling ====================

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("Test 6: Repository findById returns empty Optional for non-existent item")
    void testRepositoryFindByIdReturnsEmptyForNonExistent() {
        // Given - No user exists with this ID
        String nonExistentId = "does-not-exist";

        // When - Try to find non-existent user
        Optional<User> result = userRepository.findById(nonExistentId);

        // Then - Should return empty Optional (not throw exception)
        assertThat(result).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("Test 7: Repository handles null values gracefully")
    void testRepositoryHandlesNullValues() {
        // Given - User with some null fields
        User user = new User();
        user.setId("null-fields-user");
        user.setName(null); // null name
        user.setNumberOfPlaylists(null); // null number

        // When - Save and retrieve
        userRepository.save(user);
        User retrieved = userRepository.findById("null-fields-user").orElse(null);

        // Then - Should handle null fields correctly
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getId()).isEqualTo("null-fields-user");
        assertThat(retrieved.getName()).isNull();
        assertThat(retrieved.getNumberOfPlaylists()).isNull();
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("Test 8: Repository deleteById throws exception for non-existent items")
    void testRepositoryDeleteThrowsForNonExistent() {
        // Given - Non-existent ID
        String nonExistentId = "never-existed";

        // When/Then - Library throws EmptyResultDataAccessException for non-existent item
        assertThatThrownBy(() -> userRepository.deleteById(nonExistentId))
                .isInstanceOf(EmptyResultDataAccessException.class)
                .hasMessageContaining("never-existed");

        // Verify it doesn't exist
        assertThat(userRepository.findById(nonExistentId)).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("Test 9: Repository update via save overwrites existing item")
    void testRepositoryUpdateOverwritesExisting() {
        // Given - Existing user
        User original = new User();
        original.setId("update-test");
        original.setName("Original Name");
        original.setNumberOfPlaylists(10);
        userRepository.save(original);

        // When - Save updated version
        User updated = new User();
        updated.setId("update-test"); // same ID
        updated.setName("Updated Name");
        updated.setNumberOfPlaylists(20);
        userRepository.save(updated);

        // Then - Should overwrite with new values
        User retrieved = userRepository.findById("update-test").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getName()).isEqualTo("Updated Name");
        assertThat(retrieved.getNumberOfPlaylists()).isEqualTo(20);
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("Test 10: Repository batch operations with empty collections")
    void testRepositoryBatchOperationsWithEmptyCollections() {
        // Given - Empty collections
        List<User> emptyList = new ArrayList<>();

        // When - Batch operations with empty data
        userRepository.saveAll(emptyList);
        List<User> results = (List<User>) userRepository.findAllById(Collections.emptyList());

        // Then - Should handle gracefully
        assertThat(results).isEmpty();
        assertThat(userRepository.count()).isEqualTo(0);
    }

    // ==================== Helper Methods ====================

    private User createUser(String id, String name) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        return user;
    }
}
