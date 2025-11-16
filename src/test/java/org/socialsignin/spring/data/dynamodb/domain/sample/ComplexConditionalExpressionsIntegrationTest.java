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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive integration tests for Complex Conditional Expressions in DynamoDB.
 *
 * SDK v2 Migration Notes:
 * - SDK v1: DynamoDBMapper → SDK v2: Not used in this test (uses DynamoDbClient directly)
 * - SDK v1: new AttributeValue("string") → SDK v2: AttributeValue.fromS("string")
 * - All conditional expressions use SDK v2 UpdateItemRequest, DeleteItemRequest, TransactWriteItemsRequest
 * - Conditional expression syntax remains the same between SDKs
 *
 * Tests advanced conditional expression scenarios that combine multiple operators
 * and functions in complex boolean logic.
 *
 * Coverage:
 * - attribute_exists AND other conditions
 * - attribute_not_exists with complex logic
 * - Nested AND/OR/NOT combinations
 * - Multiple attribute checks in single condition
 * - size() function with conditions
 * - begins_with combined with other conditions
 * - BETWEEN with additional conditions
 * - Complex conditions in transactions
 * - Conditional deletes with multiple checks
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, ComplexConditionalExpressionsIntegrationTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Complex Conditional Expressions Integration Tests")
public class ComplexConditionalExpressionsIntegrationTest {

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

    // ==================== attribute_exists with AND/OR ====================

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Test 1: attribute_exists AND value comparison")
    void testAttributeExistsAndValueComparison() {
        // Given
        User user = new User();
        user.setId("complex-1");
        user.setName("Alice");
        user.setNumberOfPlaylists(5);
        userRepository.save(user);

        // When - Update only if name exists AND playlists > 3
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.fromS("complex-1"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":inc", AttributeValue.builder().n("10")
                .build());
        expressionAttributeValues.put(":threshold", AttributeValue.builder().n("3")
                .build());

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#name", "name");

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("SET numberOfPlaylists = numberOfPlaylists + :inc")
                .conditionExpression("attribute_exists(#name) AND numberOfPlaylists > :threshold")
                .expressionAttributeNames(expressionAttributeNames)
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        amazonDynamoDB.updateItem(request);

        // Then
        User updated = userRepository.findById("complex-1").get();
        assertThat(updated.getNumberOfPlaylists()).isEqualTo(15);
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Test 2: attribute_exists AND fails when attribute missing")
    void testAttributeExistsAndFailsWhenMissing() {
        // Given - User without postCode
        User user = new User();
        user.setId("complex-2");
        user.setName("Bob");
        user.setNumberOfPlaylists(10);
        // postCode is null
        userRepository.save(user);

        // When/Then - Try to update with condition requiring postCode exists
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.fromS("complex-2"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":inc", AttributeValue.builder().n("5")
                .build());
        expressionAttributeValues.put(":threshold", AttributeValue.builder().n("5")
                .build());

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("SET numberOfPlaylists = numberOfPlaylists + :inc")
                .conditionExpression("attribute_exists(postCode) AND numberOfPlaylists > :threshold")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        assertThatThrownBy(() -> amazonDynamoDB.updateItem(request))
                .isInstanceOf(ConditionalCheckFailedException.class);
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Test 3: attribute_not_exists OR value match")
    void testAttributeNotExistsOrValueMatch() {
        // Given
        User user = new User();
        user.setId("complex-3");
        user.setName("Charlie");
        user.setPostCode("12345");
        userRepository.save(user);

        // When - Update if postCode doesn't exist OR postCode = "12345"
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.fromS("complex-3"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newName", AttributeValue.fromS("Charlie Updated"));
        expressionAttributeValues.put(":code", AttributeValue.fromS("12345"));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#name", "name");

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("SET #name = :newName")
                .conditionExpression("attribute_not_exists(postCode) OR postCode = :code")
                .expressionAttributeNames(expressionAttributeNames)
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        amazonDynamoDB.updateItem(request);

        // Then - Should succeed (postCode = "12345")
        User updated = userRepository.findById("complex-3").get();
        assertThat(updated.getName()).isEqualTo("Charlie Updated");
    }

    // ==================== Nested AND/OR/NOT Combinations ====================

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Test 4: (condition1 OR condition2) AND condition3")
    void testNestedOrAndConditions() {
        // Given
        User user = new User();
        user.setId("complex-4");
        user.setName("David");
        user.setPostCode("12345");
        user.setNumberOfPlaylists(10);
        userRepository.save(user);

        // When - Update if (postCode = "12345" OR postCode = "67890") AND playlists < 20
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.fromS("complex-4"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newName", AttributeValue.fromS("David Updated"));
        expressionAttributeValues.put(":code1", AttributeValue.fromS("12345"));
        expressionAttributeValues.put(":code2", AttributeValue.fromS("67890"));
        expressionAttributeValues.put(":maxPlaylists", AttributeValue.builder().n("20")
                .build());

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#name", "name");

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("SET #name = :newName")
                .conditionExpression("(postCode = :code1 OR postCode = :code2) AND numberOfPlaylists < :maxPlaylists")
                .expressionAttributeNames(expressionAttributeNames)
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        amazonDynamoDB.updateItem(request);

        // Then
        User updated = userRepository.findById("complex-4").get();
        assertThat(updated.getName()).isEqualTo("David Updated");
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("Test 5: NOT (condition1 OR condition2)")
    void testNotOrCondition() {
        // Given
        User user = new User();
        user.setId("complex-5");
        user.setName("Eve");
        user.setPostCode("11111");
        userRepository.save(user);

        // When - Update if NOT (postCode = "12345" OR postCode = "67890")
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.fromS("complex-5"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newName", AttributeValue.fromS("Eve Updated"));
        expressionAttributeValues.put(":code1", AttributeValue.fromS("12345"));
        expressionAttributeValues.put(":code2", AttributeValue.fromS("67890"));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#name", "name");

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("SET #name = :newName")
                .conditionExpression("NOT (postCode = :code1 OR postCode = :code2)")
                .expressionAttributeNames(expressionAttributeNames)
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        amazonDynamoDB.updateItem(request);

        // Then - Should succeed (postCode is "11111")
        User updated = userRepository.findById("complex-5").get();
        assertThat(updated.getName()).isEqualTo("Eve Updated");
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("Test 6: Complex triple condition - (A OR B) AND (C OR D)")
    void testComplexTripleCondition() {
        // Given
        User user = new User();
        user.setId("complex-6");
        user.setName("Frank");
        user.setPostCode("12345");
        user.setNumberOfPlaylists(15);
        userRepository.save(user);

        // When - (postCode in [12345, 67890]) AND (playlists between 10-20)
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.fromS("complex-6"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newName", AttributeValue.fromS("Frank Updated"));
        expressionAttributeValues.put(":code1", AttributeValue.fromS("12345"));
        expressionAttributeValues.put(":code2", AttributeValue.fromS("67890"));
        expressionAttributeValues.put(":min", AttributeValue.builder().n("10")
                .build());
        expressionAttributeValues.put(":max", AttributeValue.builder().n("20")
                .build());

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#name", "name");

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("SET #name = :newName")
                .conditionExpression("(postCode = :code1 OR postCode = :code2) AND (numberOfPlaylists BETWEEN :min AND :max)")
                .expressionAttributeNames(expressionAttributeNames)
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        amazonDynamoDB.updateItem(request);

        // Then
        User updated = userRepository.findById("complex-6").get();
        assertThat(updated.getName()).isEqualTo("Frank Updated");
    }

    // ==================== size() Function with Conditions ====================

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("Test 7: size() function with AND condition")
    void testSizeFunctionWithAndCondition() {
        // Given - User with tags
        User user = new User();
        user.setId("complex-7");
        user.setName("Grace");
        user.setTags(new HashSet<>(Arrays.asList("premium", "verified", "vip")));
        user.setNumberOfPlaylists(5);
        userRepository.save(user);

        // When - Update if size(tags) >= 3 AND playlists > 3
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.fromS("complex-7"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":inc", AttributeValue.builder().n("10")
                .build());
        expressionAttributeValues.put(":minTags", AttributeValue.builder().n("3")
                .build());
        expressionAttributeValues.put(":minPlaylists", AttributeValue.builder().n("3")
                .build());

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("SET numberOfPlaylists = numberOfPlaylists + :inc")
                .conditionExpression("size(tags) >= :minTags AND numberOfPlaylists > :minPlaylists")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        amazonDynamoDB.updateItem(request);

        // Then
        User updated = userRepository.findById("complex-7").get();
        assertThat(updated.getNumberOfPlaylists()).isEqualTo(15);
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("Test 8: size() function fails when condition not met")
    void testSizeFunctionFailsWhenNotMet() {
        // Given - User with only 1 tag
        User user = new User();
        user.setId("complex-8");
        user.setName("Henry");
        user.setTags(new HashSet<>(Collections.singletonList("basic")));
        userRepository.save(user);

        // When/Then - Update if size(tags) >= 3 (should fail)
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.fromS("complex-8"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newName", AttributeValue.fromS("Henry Updated"));
        expressionAttributeValues.put(":minTags", AttributeValue.builder().n("3")
                .build());

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#name", "name");

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("SET #name = :newName")
                .conditionExpression("size(tags) >= :minTags")
                .expressionAttributeNames(expressionAttributeNames)
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        assertThatThrownBy(() -> amazonDynamoDB.updateItem(request))
                .isInstanceOf(ConditionalCheckFailedException.class);
    }

    // ==================== begins_with Combined with Other Conditions ====================

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("Test 9: begins_with AND numeric comparison")
    void testBeginsWithAndNumericComparison() {
        // Given
        User user = new User();
        user.setId("complex-9");
        user.setName("Ian");
        user.setPostCode("12345");
        user.setNumberOfPlaylists(20);
        userRepository.save(user);

        // When - Update if postCode begins with "123" AND playlists >= 15
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.fromS("complex-9"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newName", AttributeValue.fromS("Ian Updated"));
        expressionAttributeValues.put(":prefix", AttributeValue.fromS("123"));
        expressionAttributeValues.put(":minPlaylists", AttributeValue.builder().n("15")
                .build());

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#name", "name");

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("SET #name = :newName")
                .conditionExpression("begins_with(postCode, :prefix) AND numberOfPlaylists >= :minPlaylists")
                .expressionAttributeNames(expressionAttributeNames)
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        amazonDynamoDB.updateItem(request);

        // Then
        User updated = userRepository.findById("complex-9").get();
        assertThat(updated.getName()).isEqualTo("Ian Updated");
    }

    // ==================== Multiple Attribute Checks ====================

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("Test 10: Multiple attribute_exists checks")
    void testMultipleAttributeExistsChecks() {
        // Given - User with all attributes
        User user = new User();
        user.setId("complex-10");
        user.setName("Jane");
        user.setPostCode("12345");
        user.setNumberOfPlaylists(10);
        userRepository.save(user);

        // When - Update if name, postCode, and numberOfPlaylists all exist
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.fromS("complex-10"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":inc", AttributeValue.builder().n("5")
                .build());

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#name", "name");

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("SET numberOfPlaylists = numberOfPlaylists + :inc")
                .conditionExpression("attribute_exists(#name) AND attribute_exists(postCode) AND attribute_exists(numberOfPlaylists)")
                .expressionAttributeNames(expressionAttributeNames)
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        amazonDynamoDB.updateItem(request);

        // Then
        User updated = userRepository.findById("complex-10").get();
        assertThat(updated.getNumberOfPlaylists()).isEqualTo(15);
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    @DisplayName("Test 11: attribute_exists OR attribute_not_exists")
    void testAttributeExistsOrNotExists() {
        // Given - User without postCode
        User user = new User();
        user.setId("complex-11");
        user.setName("Kevin");
        // postCode is null
        userRepository.save(user);

        // When - Update if postCode exists OR doesn't exist (always true)
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.fromS("complex-11"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newName", AttributeValue.fromS("Kevin Updated"));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#name", "name");

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("SET #name = :newName")
                .conditionExpression("attribute_exists(postCode) OR attribute_not_exists(postCode)")
                .expressionAttributeNames(expressionAttributeNames)
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        amazonDynamoDB.updateItem(request);

        // Then - Always succeeds
        User updated = userRepository.findById("complex-11").get();
        assertThat(updated.getName()).isEqualTo("Kevin Updated");
    }

    // ==================== Conditional Deletes ====================

    @Test
    @org.junit.jupiter.api.Order(12)
    @DisplayName("Test 12: Conditional delete with complex expression")
    void testConditionalDeleteWithComplexExpression() {
        // Given
        User user = new User();
        user.setId("complex-12");
        user.setName("Laura");
        user.setNumberOfPlaylists(0);
        user.setPostCode("12345");
        userRepository.save(user);

        // When - Delete only if playlists = 0 AND postCode exists
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.fromS("complex-12"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":zero", AttributeValue.builder().n("0")
                .build());

        DeleteItemRequest deleteRequest = DeleteItemRequest.builder()
                .tableName("user")
                .key(key)
                .conditionExpression("numberOfPlaylists = :zero AND attribute_exists(postCode)")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        amazonDynamoDB.deleteItem(deleteRequest);

        // Then - User should be deleted
        assertThat(userRepository.findById("complex-12")).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.Order(13)
    @DisplayName("Test 13: Conditional delete fails when condition not met")
    void testConditionalDeleteFailsWhenNotMet() {
        // Given
        User user = new User();
        user.setId("complex-13");
        user.setName("Mike");
        user.setNumberOfPlaylists(10);
        userRepository.save(user);

        // When/Then - Try to delete only if playlists = 0 (should fail)
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.fromS("complex-13"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":zero", AttributeValue.builder().n("0")
                .build());

        DeleteItemRequest deleteRequest = DeleteItemRequest.builder()
                .tableName("user")
                .key(key)
                .conditionExpression("numberOfPlaylists = :zero")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        assertThatThrownBy(() -> amazonDynamoDB.deleteItem(deleteRequest))
                .isInstanceOf(ConditionalCheckFailedException.class);

        // User should still exist
        assertThat(userRepository.findById("complex-13")).isPresent();
    }

    // ==================== BETWEEN with Additional Conditions ====================

    @Test
    @org.junit.jupiter.api.Order(14)
    @DisplayName("Test 14: BETWEEN AND attribute_exists")
    void testBetweenAndAttributeExists() {
        // Given
        User user = new User();
        user.setId("complex-14");
        user.setName("Nancy");
        user.setNumberOfPlaylists(15);
        user.setPostCode("12345");
        userRepository.save(user);

        // When - Update if playlists between 10-20 AND postCode exists
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", AttributeValue.fromS("complex-14"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newName", AttributeValue.fromS("Nancy Updated"));
        expressionAttributeValues.put(":min", AttributeValue.builder().n("10")
                .build());
        expressionAttributeValues.put(":max", AttributeValue.builder().n("20")
                .build());

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#name", "name");

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("SET #name = :newName")
                .conditionExpression("numberOfPlaylists BETWEEN :min AND :max AND attribute_exists(postCode)")
                .expressionAttributeNames(expressionAttributeNames)
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        amazonDynamoDB.updateItem(request);

        // Then
        User updated = userRepository.findById("complex-14").get();
        assertThat(updated.getName()).isEqualTo("Nancy Updated");
    }

    // ==================== Complex Conditions in Transactions ====================

    @Test
    @org.junit.jupiter.api.Order(15)
    @DisplayName("Test 15: Transaction with complex conditional update")
    void testTransactionWithComplexCondition() {
        // Given - Two users
        User user1 = new User();
        user1.setId("complex-txn-1");
        user1.setName("Oscar");
        user1.setNumberOfPlaylists(10);
        user1.setPostCode("12345");
        userRepository.save(user1);

        User user2 = new User();
        user2.setId("complex-txn-2");
        user2.setName("Paula");
        user2.setNumberOfPlaylists(5);
        userRepository.save(user2);

        // When - Transaction with complex condition
        Collection<TransactWriteItem> actions = new ArrayList<>();

        // Update user1 if playlists > 5 AND postCode exists
        Map<String, AttributeValue> key1 = new HashMap<>();
        key1.put("Id", AttributeValue.fromS("complex-txn-1"));
        Map<String, AttributeValue> values1 = new HashMap<>();
        values1.put(":inc", AttributeValue.builder().n("10")
                .build());
        values1.put(":threshold", AttributeValue.builder().n("5")
                .build());

        actions.add(TransactWriteItem.builder().update(
                Update.builder()
                        .tableName("user")
                        .key(key1)
                        .updateExpression("SET numberOfPlaylists = numberOfPlaylists + :inc")
                        .conditionExpression("numberOfPlaylists > :threshold AND attribute_exists(postCode)")
                        .expressionAttributeValues(values1)
                .build()
        )
        .build());

        // Update user2
        Map<String, AttributeValue> key2 = new HashMap<>();
        key2.put("Id", AttributeValue.fromS("complex-txn-2"));
        Map<String, AttributeValue> values2 = new HashMap<>();
        values2.put(":inc", AttributeValue.builder().n("5")
                .build());

        actions.add(TransactWriteItem.builder().update(
                Update.builder()
                        .tableName("user")
                        .key(key2)
                        .updateExpression("SET numberOfPlaylists = numberOfPlaylists + :inc")
                        .expressionAttributeValues(values2)
                .build()
        )
        .build());

        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(actions)
                .build();
        amazonDynamoDB.transactWriteItems(request);

        // Then - Both updates should succeed
        User updated1 = userRepository.findById("complex-txn-1").get();
        User updated2 = userRepository.findById("complex-txn-2").get();
        assertThat(updated1.getNumberOfPlaylists()).isEqualTo(20);
        assertThat(updated2.getNumberOfPlaylists()).isEqualTo(10);
    }
}
