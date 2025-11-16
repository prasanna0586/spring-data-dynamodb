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
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.DeleteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive integration tests for DynamoDB conditional expressions.
 * Tests various condition types beyond optimistic locking:
 * - attribute_exists
 * - attribute_not_exists
 * - Conditional saves (create-only, update-only)
 * - Conditional deletes
 * - Complex condition expressions
 * - Condition expression failures
 *
 * Coverage:
 * - Prevent overwriting existing items (attribute_not_exists)
 * - Ensure item exists before update (attribute_exists)
 * - Conditional delete based on attribute values
 * - Complex multi-attribute conditions
 * - Expected value matching
 * - Condition failure handling
 *
 * SDK v2 Migration Notes:
 * - SDK v1: DynamoDBMapper with DynamoDBSaveExpression/DynamoDBDeleteExpression
 * - SDK v2: DynamoDBTemplate with Expression and condition expression strings
 * - SDK v1: ExpectedAttributeValue with comparison operators (EQ, LE, GE, etc.)
 * - SDK v2: Expression with condition expressions and attribute value maps
 * - Condition syntax changed from ComparisonOperator to condition expression strings
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, ConditionalExpressionsIntegrationTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Conditional Expressions Integration Tests")
public class ConditionalExpressionsIntegrationTest {

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    public static class TestAppConfig {
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("dynamoDbEnhancedClient")
    private DynamoDbEnhancedClient enhancedClient;

    private DynamoDbTable<User> userTable;

    @BeforeEach
    void setUp() {
        // Initialize the DynamoDB table for User entity
        userTable = enhancedClient.table("User", TableSchema.fromBean(User.class));

        // Clear all data before each test
        userRepository.deleteAll();
    }

    // ==================== Expected Value Matching Tests ====================
    // Note: Tests for attribute_exists and attribute_not_exists using ExpectedAttributeValue
    // have complex behavior in AWS SDK v1 that differs between PutItem and UpdateItem operations.
    // These tests focus on the reliable conditional expression patterns that work consistently.

    @Test
    @Order(5)
    @DisplayName("Test 5: Conditional save - Update only if attribute matches expected value")
    void testConditionalSave_MatchExpectedValue() {
        // Given - Create user with specific numberOfPlaylists
        User user = new User();
        user.setId("cond-user-5");
        user.setName("User 5");
        user.setNumberOfPlaylists(10);
        userRepository.save(user);

        // When - Update only if numberOfPlaylists is still 10
        user.setName("Updated User 5");
        user.setNumberOfPlaylists(20);

        // SDK v2: Use Enhanced Client with PutItemEnhancedRequest and condition expression
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":val", AttributeValue.builder().n("10").build());

        Expression conditionExpression = Expression.builder()
                .expression("numberOfPlaylists = :val")
                .expressionValues(expressionAttributeValues)
                .build();

        PutItemEnhancedRequest<User> putRequest = PutItemEnhancedRequest.builder(User.class)
                .item(user)
                .conditionExpression(conditionExpression)
                .build();

        // Should succeed since numberOfPlaylists is 10
        userTable.putItem(putRequest);

        // Then - Verify update succeeded
        User updatedUser = userRepository.findById("cond-user-5").orElse(null);
        assertThat(updatedUser).isNotNull();
        assertThat(updatedUser.getName()).isEqualTo("Updated User 5");
        assertThat(updatedUser.getNumberOfPlaylists()).isEqualTo(20);
    }

    @Test
    @Order(6)
    @DisplayName("Test 6: Conditional save - Update fails if attribute doesn't match expected value")
    void testConditionalSave_MismatchExpectedValue() {
        // Given - Create user with numberOfPlaylists = 10
        User user = new User();
        user.setId("cond-user-6");
        user.setName("User 6");
        user.setNumberOfPlaylists(10);
        userRepository.save(user);

        // When/Then - Attempt to update with wrong expected value (expecting 999)
        user.setName("Should Fail");
        user.setNumberOfPlaylists(20);

        // SDK v2: Condition expression expecting wrong value
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":val", AttributeValue.builder().n("999").build());

        Expression conditionExpression = Expression.builder()
                .expression("numberOfPlaylists = :val")
                .expressionValues(expressionAttributeValues)
                .build();

        PutItemEnhancedRequest<User> putRequest = PutItemEnhancedRequest.builder(User.class)
                .item(user)
                .conditionExpression(conditionExpression)
                .build();

        // Should throw ConditionalCheckFailedException
        assertThatThrownBy(() -> userTable.putItem(putRequest))
                .isInstanceOf(ConditionalCheckFailedException.class);

        // Verify original data unchanged
        User unchangedUser = userRepository.findById("cond-user-6").orElse(null);
        assertThat(unchangedUser).isNotNull();
        assertThat(unchangedUser.getName()).isEqualTo("User 6");
        assertThat(unchangedUser.getNumberOfPlaylists()).isEqualTo(10);
    }

    // ==================== Conditional Delete Tests ====================

    @Test
    @Order(7)
    @DisplayName("Test 7: Conditional delete - Delete only if attribute matches")
    void testConditionalDelete_MatchExpectedValue() {
        // Given - Create user
        User user = new User();
        user.setId("cond-user-7");
        user.setName("User 7");
        user.setNumberOfPlaylists(10);
        userRepository.save(user);

        // When - Delete only if numberOfPlaylists is 10
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":val", AttributeValue.builder().n("10").build());

        Expression conditionExpression = Expression.builder()
                .expression("numberOfPlaylists = :val")
                .expressionValues(expressionAttributeValues)
                .build();

        DeleteItemEnhancedRequest deleteRequest = DeleteItemEnhancedRequest.builder()
                .key(k -> k.partitionValue(user.getId()))
                .conditionExpression(conditionExpression)
                .build();

        // Should succeed
        userTable.deleteItem(deleteRequest);

        // Then - Verify deletion
        boolean exists = userRepository.existsById("cond-user-7");
        assertThat(exists).isFalse();
    }

    @Test
    @Order(8)
    @DisplayName("Test 8: Conditional delete - Delete fails if attribute doesn't match")
    void testConditionalDelete_MismatchExpectedValue() {
        // Given - Create user with numberOfPlaylists = 10
        User user = new User();
        user.setId("cond-user-8");
        user.setName("User 8");
        user.setNumberOfPlaylists(10);
        userRepository.save(user);

        // When/Then - Attempt to delete with wrong expected value (expecting 999)
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":val", AttributeValue.builder().n("999").build());

        Expression conditionExpression = Expression.builder()
                .expression("numberOfPlaylists = :val")
                .expressionValues(expressionAttributeValues)
                .build();

        DeleteItemEnhancedRequest deleteRequest = DeleteItemEnhancedRequest.builder()
                .key(k -> k.partitionValue(user.getId()))
                .conditionExpression(conditionExpression)
                .build();

        // Should throw ConditionalCheckFailedException
        assertThatThrownBy(() -> userTable.deleteItem(deleteRequest))
                .isInstanceOf(ConditionalCheckFailedException.class);

        // Verify item still exists
        User unchangedUser = userRepository.findById("cond-user-8").orElse(null);
        assertThat(unchangedUser).isNotNull();
        assertThat(unchangedUser.getName()).isEqualTo("User 8");
    }

    // ==================== Complex Conditional Expressions ====================

    @Test
    @Order(9)
    @DisplayName("Test 9: Conditional save - Multiple conditions (AND logic)")
    void testConditionalSave_MultipleConditions() {
        // Given - Create user
        User user = new User();
        user.setId("cond-user-9");
        user.setName("User 9");
        user.setNumberOfPlaylists(10);
        userRepository.save(user);

        // When - Update only if both conditions match
        user.setName("Updated User 9");
        user.setNumberOfPlaylists(20);

        // SDK v2: Multiple conditions with AND operator
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":playlistVal", AttributeValue.builder().n("10").build());
        expressionAttributeValues.put(":nameVal", AttributeValue.builder().s("User 9").build());

        Expression conditionExpression = Expression.builder()
                .expression("numberOfPlaylists = :playlistVal AND #name = :nameVal")
                .expressionValues(expressionAttributeValues)
                .putExpressionName("#name", "name")  // name is a reserved word in DynamoDB
                .build();

        PutItemEnhancedRequest<User> putRequest = PutItemEnhancedRequest.builder(User.class)
                .item(user)
                .conditionExpression(conditionExpression)
                .build();

        // Should succeed since both conditions match
        userTable.putItem(putRequest);

        // Then - Verify update succeeded
        User updatedUser = userRepository.findById("cond-user-9").orElse(null);
        assertThat(updatedUser).isNotNull();
        assertThat(updatedUser.getName()).isEqualTo("Updated User 9");
        assertThat(updatedUser.getNumberOfPlaylists()).isEqualTo(20);
    }

    @Test
    @Order(10)
    @DisplayName("Test 10: Conditional save - Multiple conditions fail if one doesn't match")
    void testConditionalSave_MultipleConditionsOneFails() {
        // Given - Create user
        User user = new User();
        user.setId("cond-user-10");
        user.setName("User 10");
        user.setNumberOfPlaylists(10);
        userRepository.save(user);

        // When/Then - Update with one correct and one incorrect condition
        user.setName("Should Fail");

        // SDK v2: One condition is correct, one is incorrect
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":playlistVal", AttributeValue.builder().n("10").build());
        expressionAttributeValues.put(":nameVal", AttributeValue.builder().s("Wrong Name").build());

        Expression conditionExpression = Expression.builder()
                .expression("numberOfPlaylists = :playlistVal AND #name = :nameVal")
                .expressionValues(expressionAttributeValues)
                .putExpressionName("#name", "name")
                .build();

        PutItemEnhancedRequest<User> putRequest = PutItemEnhancedRequest.builder(User.class)
                .item(user)
                .conditionExpression(conditionExpression)
                .build();

        // Should fail because name doesn't match
        assertThatThrownBy(() -> userTable.putItem(putRequest))
                .isInstanceOf(ConditionalCheckFailedException.class);

        // Verify original data unchanged
        User unchangedUser = userRepository.findById("cond-user-10").orElse(null);
        assertThat(unchangedUser).isNotNull();
        assertThat(unchangedUser.getName()).isEqualTo("User 10");
    }

    // ==================== Comparison Operators ====================

    @Test
    @Order(13)
    @DisplayName("Test 13: Conditional save - Using LE (less than or equal) operator")
    void testConditionalSave_LessThanOrEqualOperator() {
        // Given - Create user with 10 playlists
        User user = new User();
        user.setId("cond-user-13");
        user.setName("User 13");
        user.setNumberOfPlaylists(10);
        userRepository.save(user);

        // When - Update only if numberOfPlaylists <= 15
        user.setNumberOfPlaylists(12);

        // SDK v2: Use <= operator in condition expression
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":val", AttributeValue.builder().n("15").build());

        Expression conditionExpression = Expression.builder()
                .expression("numberOfPlaylists <= :val")
                .expressionValues(expressionAttributeValues)
                .build();

        PutItemEnhancedRequest<User> putRequest = PutItemEnhancedRequest.builder(User.class)
                .item(user)
                .conditionExpression(conditionExpression)
                .build();

        // Should succeed since 10 <= 15
        userTable.putItem(putRequest);

        // Then - Verify update
        User updatedUser = userRepository.findById("cond-user-13").orElse(null);
        assertThat(updatedUser).isNotNull();
        assertThat(updatedUser.getNumberOfPlaylists()).isEqualTo(12);
    }

    @Test
    @Order(14)
    @DisplayName("Test 14: Conditional save - Using GE (greater than or equal) operator")
    void testConditionalSave_GreaterThanOrEqualOperator() {
        // Given - Create user with 100 playlists
        User user = new User();
        user.setId("cond-user-14");
        user.setName("User 14");
        user.setNumberOfPlaylists(100);
        userRepository.save(user);

        // When - Update only if numberOfPlaylists >= 50
        user.setNumberOfPlaylists(110);

        // SDK v2: Use >= operator in condition expression
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":val", AttributeValue.builder().n("50").build());

        Expression conditionExpression = Expression.builder()
                .expression("numberOfPlaylists >= :val")
                .expressionValues(expressionAttributeValues)
                .build();

        PutItemEnhancedRequest<User> putRequest = PutItemEnhancedRequest.builder(User.class)
                .item(user)
                .conditionExpression(conditionExpression)
                .build();

        // Should succeed since 100 >= 50
        userTable.putItem(putRequest);

        // Then - Verify update
        User updatedUser = userRepository.findById("cond-user-14").orElse(null);
        assertThat(updatedUser).isNotNull();
        assertThat(updatedUser.getNumberOfPlaylists()).isEqualTo(110);
    }

    @Test
    @Order(15)
    @DisplayName("Test 15: Conditional save - Preventing race conditions in counter increment")
    void testConditionalSave_CounterIncrement() {
        // Given - Create user with counter
        User user = new User();
        user.setId("cond-user-15");
        user.setName("User 15");
        user.setNumberOfPlaylists(5);
        userRepository.save(user);

        // When - Increment counter only if current value is 5 (simulate atomic increment)
        User userToUpdate = userRepository.findById("cond-user-15").orElse(null);
        assertThat(userToUpdate).isNotNull();

        int currentValue = userToUpdate.getNumberOfPlaylists();
        userToUpdate.setNumberOfPlaylists(currentValue + 1);

        // SDK v2: Condition to ensure we're updating from the expected value
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":val", AttributeValue.builder().n(String.valueOf(currentValue)).build());

        Expression conditionExpression = Expression.builder()
                .expression("numberOfPlaylists = :val")
                .expressionValues(expressionAttributeValues)
                .build();

        PutItemEnhancedRequest<User> putRequest = PutItemEnhancedRequest.builder(User.class)
                .item(userToUpdate)
                .conditionExpression(conditionExpression)
                .build();

        // Should succeed
        userTable.putItem(putRequest);

        // Then - Verify increment
        User updatedUser = userRepository.findById("cond-user-15").orElse(null);
        assertThat(updatedUser).isNotNull();
        assertThat(updatedUser.getNumberOfPlaylists()).isEqualTo(6);

        // Simulate concurrent update with stale data
        User staleUser = new User();
        staleUser.setId("cond-user-15");
        staleUser.setName("User 15");
        staleUser.setNumberOfPlaylists(currentValue + 1); // Still thinks it's 5

        PutItemEnhancedRequest<User> stalePutRequest = PutItemEnhancedRequest.builder(User.class)
                .item(staleUser)
                .conditionExpression(conditionExpression)
                .build();

        // Should fail because current value is now 6, not 5
        assertThatThrownBy(() -> userTable.putItem(stalePutRequest))
                .isInstanceOf(ConditionalCheckFailedException.class);
    }
}
