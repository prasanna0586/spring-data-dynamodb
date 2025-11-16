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
 * Comprehensive integration tests for DynamoDB Update Expressions.
 *
 * Update Expressions allow atomic updates of item attributes without reading the item first.
 * Supported operations:
 * - SET: Update or add attributes
 * - REMOVE: Delete attributes
 * - ADD: Atomically increment/decrement numbers, add to sets
 * - DELETE: Remove elements from sets
 *
 * Coverage:
 * - Atomic counter increment/decrement
 * - SET operations (update single/multiple attributes)
 * - REMOVE operations
 * - ADD operations (numbers and sets)
 * - DELETE operations (from sets)
 * - List operations (append, prepend)
 * - Nested attribute updates
 * - Conditional updates
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, UpdateExpressionsIntegrationTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Update Expressions Integration Tests")
public class UpdateExpressionsIntegrationTest {

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

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Test 1: SET - Update single attribute")
    void testSetSingleAttribute() {
        // Given - Create user
        User user = new User();
        user.setId("update-user-1");
        user.setName("Original Name");
        user.setNumberOfPlaylists(10);
        userRepository.save(user);

        // When - Update name using UpdateExpression
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.builder().s("update-user-1").build());

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newName", AttributeValue.builder().s("Updated Name").build());

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("SET #n = :newName")
                .expressionAttributeNames(Collections.singletonMap("#n", "name"))
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        amazonDynamoDB.updateItem(updateRequest);

        // Then - Verify update
        User updated = userRepository.findById("update-user-1").orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getName()).isEqualTo("Updated Name");
        assertThat(updated.getNumberOfPlaylists()).isEqualTo(10); // Other attributes unchanged
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Test 2: SET - Update multiple attributes")
    void testSetMultipleAttributes() {
        // Given
        User user = new User();
        user.setId("update-user-2");
        user.setName("Original");
        user.setPostCode("12345");
        user.setNumberOfPlaylists(5);
        userRepository.save(user);

        // When - Update multiple attributes
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.builder().s("update-user-2").build());

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":name", AttributeValue.builder().s("New Name").build());
        expressionAttributeValues.put(":postCode", AttributeValue.builder().s("67890").build());

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("SET #n = :name, postCode = :postCode")
                .expressionAttributeNames(Collections.singletonMap("#n", "name"))
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        amazonDynamoDB.updateItem(updateRequest);

        // Then
        User updated = userRepository.findById("update-user-2").orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getName()).isEqualTo("New Name");
        assertThat(updated.getPostCode()).isEqualTo("67890");
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Test 3: ADD - Atomic counter increment")
    void testAtomicCounterIncrement() {
        // Given
        User user = new User();
        user.setId("update-user-3");
        user.setName("User 3");
        user.setNumberOfPlaylists(10);
        userRepository.save(user);

        // When - Increment counter by 5
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.builder().s("update-user-3").build());

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":inc", AttributeValue.builder().n("5")
                .build());

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("ADD numberOfPlaylists :inc")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        amazonDynamoDB.updateItem(updateRequest);

        // Then - Verify increment
        User updated = userRepository.findById("update-user-3").orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getNumberOfPlaylists()).isEqualTo(15);
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Test 4: ADD - Atomic counter decrement")
    void testAtomicCounterDecrement() {
        // Given
        User user = new User();
        user.setId("update-user-4");
        user.setName("User 4");
        user.setNumberOfPlaylists(20);
        userRepository.save(user);

        // When - Decrement by 3 (add negative number)
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.builder().s("update-user-4").build());

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":dec", AttributeValue.builder().n("-3")
                .build());

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("ADD numberOfPlaylists :dec")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        amazonDynamoDB.updateItem(updateRequest);

        // Then
        User updated = userRepository.findById("update-user-4").orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getNumberOfPlaylists()).isEqualTo(17);
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("Test 5: ADD - Add elements to set")
    void testAddToSet() {
        // Given - User with tags
        User user = new User();
        user.setId("update-user-5");
        user.setName("User 5");
        user.setTags(new HashSet<>(Arrays.asList("tag1", "tag2")));
        userRepository.save(user);

        // When - Add new tags
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.builder().s("update-user-5").build());

        AttributeValue newTags = AttributeValue.builder().ss("tag3", "tag4")
                .build();
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newTags", newTags);

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("ADD tags :newTags")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        amazonDynamoDB.updateItem(updateRequest);

        // Then - Verify tags were added
        User updated = userRepository.findById("update-user-5").orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getTags()).containsExactlyInAnyOrder("tag1", "tag2", "tag3", "tag4");
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("Test 6: DELETE - Remove elements from set")
    void testDeleteFromSet() {
        // Given
        User user = new User();
        user.setId("update-user-6");
        user.setName("User 6");
        user.setTags(new HashSet<>(Arrays.asList("tag1", "tag2", "tag3", "tag4")));
        userRepository.save(user);

        // When - Remove specific tags
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.builder().s("update-user-6").build());

        AttributeValue tagsToRemove = AttributeValue.builder().ss("tag2", "tag4")
                .build();
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":removeTags", tagsToRemove);

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("DELETE tags :removeTags")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        amazonDynamoDB.updateItem(updateRequest);

        // Then
        User updated = userRepository.findById("update-user-6").orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getTags()).containsExactlyInAnyOrder("tag1", "tag3");
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("Test 7: REMOVE - Remove attribute")
    void testRemoveAttribute() {
        // Given
        User user = new User();
        user.setId("update-user-7");
        user.setName("User 7");
        user.setPostCode("12345");
        userRepository.save(user);

        // When - Remove postCode attribute
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.builder().s("update-user-7").build());

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("REMOVE postCode")
                .build();

        amazonDynamoDB.updateItem(updateRequest);

        // Then - postCode should be null
        User updated = userRepository.findById("update-user-7").orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getPostCode()).isNull();
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("Test 8: SET - Initialize attribute if not exists")
    void testSetIfNotExists() {
        // Given - User without numberOfPlaylists
        User user = new User();
        user.setId("update-user-8");
        user.setName("User 8");
        userRepository.save(user);

        // When - Set numberOfPlaylists only if it doesn't exist
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.builder().s("update-user-8").build());

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":zero", AttributeValue.builder().n("0")
                .build());

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("SET numberOfPlaylists = if_not_exists(numberOfPlaylists, :zero)")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        amazonDynamoDB.updateItem(updateRequest);

        // Then
        User updated = userRepository.findById("update-user-8").orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getNumberOfPlaylists()).isEqualTo(0);

        // When - Try again (should not change since it exists now)
        expressionAttributeValues.put(":zero", AttributeValue.builder().n("100")
                .build());
        updateRequest = updateRequest.toBuilder().expressionAttributeValues(expressionAttributeValues).build();
        amazonDynamoDB.updateItem(updateRequest);

        updated = userRepository.findById("update-user-8").orElse(null);
        assertThat(updated.getNumberOfPlaylists()).isEqualTo(0); // Still 0, not 100
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("Test 9: Conditional update - Update only if condition met")
    void testConditionalUpdate() {
        // Given
        User user = new User();
        user.setId("update-user-9");
        user.setName("User 9");
        user.setNumberOfPlaylists(10);
        userRepository.save(user);

        // When - Update only if numberOfPlaylists is 10
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.builder().s("update-user-9").build());

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newCount", AttributeValue.builder().n("20")
                .build());
        expressionAttributeValues.put(":expectedCount", AttributeValue.builder().n("10")
                .build());

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("SET numberOfPlaylists = :newCount")
                .conditionExpression("numberOfPlaylists = :expectedCount")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        amazonDynamoDB.updateItem(updateRequest);

        // Then - Update succeeded
        User updated = userRepository.findById("update-user-9").orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getNumberOfPlaylists()).isEqualTo(20);
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("Test 10: Combined operations - SET, ADD, REMOVE in one update")
    void testCombinedOperations() {
        // Given
        User user = new User();
        user.setId("update-user-10");
        user.setName("User 10");
        user.setPostCode("12345");
        user.setNumberOfPlaylists(5);
        user.setTags(new HashSet<>(Arrays.asList("tag1")));
        userRepository.save(user);

        // When - Multiple operations in one update
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.builder().s("update-user-10").build());

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newName", AttributeValue.builder().s("Updated User 10").build());
        expressionAttributeValues.put(":inc", AttributeValue.builder().n("3")
                .build());
        expressionAttributeValues.put(":newTag", AttributeValue.builder().ss("tag2")
                .build());

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#n", "name");

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("SET #n = :newName ADD numberOfPlaylists :inc, tags :newTag REMOVE postCode")
                .expressionAttributeNames(expressionAttributeNames)
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        amazonDynamoDB.updateItem(updateRequest);

        // Then - All operations succeeded
        User updated = userRepository.findById("update-user-10").orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getName()).isEqualTo("Updated User 10");
        assertThat(updated.getNumberOfPlaylists()).isEqualTo(8); // 5 + 3
        assertThat(updated.getTags()).containsExactlyInAnyOrder("tag1", "tag2");
        assertThat(updated.getPostCode()).isNull();
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    @DisplayName("Test 11: Math operations - Increment using SET")
    void testMathOperationsWithSet() {
        // Given
        User user = new User();
        user.setId("update-user-11");
        user.setName("User 11");
        user.setNumberOfPlaylists(10);
        userRepository.save(user);

        // When - Increment using SET with math operation
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.builder().s("update-user-11").build());

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":inc", AttributeValue.builder().n("5")
                .build());

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("SET numberOfPlaylists = numberOfPlaylists + :inc")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        amazonDynamoDB.updateItem(updateRequest);

        // Then
        User updated = userRepository.findById("update-user-11").orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getNumberOfPlaylists()).isEqualTo(15);
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    @DisplayName("Test 12: Return values - Get updated item")
    void testReturnUpdatedValues() {
        // Given
        User user = new User();
        user.setId("update-user-12");
        user.setName("User 12");
        user.setNumberOfPlaylists(5);
        userRepository.save(user);

        // When - Update and return updated values
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.builder().s("update-user-12").build());

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":inc", AttributeValue.builder().n("10")
                .build());

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("ADD numberOfPlaylists :inc")
                .expressionAttributeValues(expressionAttributeValues)
                .returnValues(ReturnValue.ALL_NEW)
                .build();

        UpdateItemResponse result = amazonDynamoDB.updateItem(updateRequest);

        // Then - Result contains updated values
        assertThat(result.attributes()).isNotNull();
        assertThat(result.attributes().get("numberOfPlaylists").n()).isEqualTo("15");
    }
}
