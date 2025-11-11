package org.socialsignin.spring.data.dynamodb.domain.sample;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive integration tests for Complex Conditional Expressions in DynamoDB.
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
    private AmazonDynamoDB amazonDynamoDB;

    @Autowired
    private DynamoDBMapper dynamoDBMapper;

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
        key.put("Id", new AttributeValue("complex-1"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":inc", new AttributeValue().withN("10"));
        expressionAttributeValues.put(":threshold", new AttributeValue().withN("3"));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#name", "name");

        UpdateItemRequest request = new UpdateItemRequest()
                .withTableName("user")
                .withKey(key)
                .withUpdateExpression("SET numberOfPlaylists = numberOfPlaylists + :inc")
                .withConditionExpression("attribute_exists(#name) AND numberOfPlaylists > :threshold")
                .withExpressionAttributeNames(expressionAttributeNames)
                .withExpressionAttributeValues(expressionAttributeValues);

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
        key.put("Id", new AttributeValue("complex-2"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":inc", new AttributeValue().withN("5"));
        expressionAttributeValues.put(":threshold", new AttributeValue().withN("5"));

        UpdateItemRequest request = new UpdateItemRequest()
                .withTableName("user")
                .withKey(key)
                .withUpdateExpression("SET numberOfPlaylists = numberOfPlaylists + :inc")
                .withConditionExpression("attribute_exists(postCode) AND numberOfPlaylists > :threshold")
                .withExpressionAttributeValues(expressionAttributeValues);

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
        key.put("Id", new AttributeValue("complex-3"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newName", new AttributeValue("Charlie Updated"));
        expressionAttributeValues.put(":code", new AttributeValue("12345"));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#name", "name");

        UpdateItemRequest request = new UpdateItemRequest()
                .withTableName("user")
                .withKey(key)
                .withUpdateExpression("SET #name = :newName")
                .withConditionExpression("attribute_not_exists(postCode) OR postCode = :code")
                .withExpressionAttributeNames(expressionAttributeNames)
                .withExpressionAttributeValues(expressionAttributeValues);

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
        key.put("Id", new AttributeValue("complex-4"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newName", new AttributeValue("David Updated"));
        expressionAttributeValues.put(":code1", new AttributeValue("12345"));
        expressionAttributeValues.put(":code2", new AttributeValue("67890"));
        expressionAttributeValues.put(":maxPlaylists", new AttributeValue().withN("20"));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#name", "name");

        UpdateItemRequest request = new UpdateItemRequest()
                .withTableName("user")
                .withKey(key)
                .withUpdateExpression("SET #name = :newName")
                .withConditionExpression("(postCode = :code1 OR postCode = :code2) AND numberOfPlaylists < :maxPlaylists")
                .withExpressionAttributeNames(expressionAttributeNames)
                .withExpressionAttributeValues(expressionAttributeValues);

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
        key.put("Id", new AttributeValue("complex-5"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newName", new AttributeValue("Eve Updated"));
        expressionAttributeValues.put(":code1", new AttributeValue("12345"));
        expressionAttributeValues.put(":code2", new AttributeValue("67890"));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#name", "name");

        UpdateItemRequest request = new UpdateItemRequest()
                .withTableName("user")
                .withKey(key)
                .withUpdateExpression("SET #name = :newName")
                .withConditionExpression("NOT (postCode = :code1 OR postCode = :code2)")
                .withExpressionAttributeNames(expressionAttributeNames)
                .withExpressionAttributeValues(expressionAttributeValues);

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
        key.put("Id", new AttributeValue("complex-6"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newName", new AttributeValue("Frank Updated"));
        expressionAttributeValues.put(":code1", new AttributeValue("12345"));
        expressionAttributeValues.put(":code2", new AttributeValue("67890"));
        expressionAttributeValues.put(":min", new AttributeValue().withN("10"));
        expressionAttributeValues.put(":max", new AttributeValue().withN("20"));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#name", "name");

        UpdateItemRequest request = new UpdateItemRequest()
                .withTableName("user")
                .withKey(key)
                .withUpdateExpression("SET #name = :newName")
                .withConditionExpression("(postCode = :code1 OR postCode = :code2) AND (numberOfPlaylists BETWEEN :min AND :max)")
                .withExpressionAttributeNames(expressionAttributeNames)
                .withExpressionAttributeValues(expressionAttributeValues);

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
        key.put("Id", new AttributeValue("complex-7"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":inc", new AttributeValue().withN("10"));
        expressionAttributeValues.put(":minTags", new AttributeValue().withN("3"));
        expressionAttributeValues.put(":minPlaylists", new AttributeValue().withN("3"));

        UpdateItemRequest request = new UpdateItemRequest()
                .withTableName("user")
                .withKey(key)
                .withUpdateExpression("SET numberOfPlaylists = numberOfPlaylists + :inc")
                .withConditionExpression("size(tags) >= :minTags AND numberOfPlaylists > :minPlaylists")
                .withExpressionAttributeValues(expressionAttributeValues);

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
        key.put("Id", new AttributeValue("complex-8"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newName", new AttributeValue("Henry Updated"));
        expressionAttributeValues.put(":minTags", new AttributeValue().withN("3"));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#name", "name");

        UpdateItemRequest request = new UpdateItemRequest()
                .withTableName("user")
                .withKey(key)
                .withUpdateExpression("SET #name = :newName")
                .withConditionExpression("size(tags) >= :minTags")
                .withExpressionAttributeNames(expressionAttributeNames)
                .withExpressionAttributeValues(expressionAttributeValues);

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
        key.put("Id", new AttributeValue("complex-9"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newName", new AttributeValue("Ian Updated"));
        expressionAttributeValues.put(":prefix", new AttributeValue("123"));
        expressionAttributeValues.put(":minPlaylists", new AttributeValue().withN("15"));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#name", "name");

        UpdateItemRequest request = new UpdateItemRequest()
                .withTableName("user")
                .withKey(key)
                .withUpdateExpression("SET #name = :newName")
                .withConditionExpression("begins_with(postCode, :prefix) AND numberOfPlaylists >= :minPlaylists")
                .withExpressionAttributeNames(expressionAttributeNames)
                .withExpressionAttributeValues(expressionAttributeValues);

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
        key.put("Id", new AttributeValue("complex-10"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":inc", new AttributeValue().withN("5"));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#name", "name");

        UpdateItemRequest request = new UpdateItemRequest()
                .withTableName("user")
                .withKey(key)
                .withUpdateExpression("SET numberOfPlaylists = numberOfPlaylists + :inc")
                .withConditionExpression("attribute_exists(#name) AND attribute_exists(postCode) AND attribute_exists(numberOfPlaylists)")
                .withExpressionAttributeNames(expressionAttributeNames)
                .withExpressionAttributeValues(expressionAttributeValues);

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
        key.put("Id", new AttributeValue("complex-11"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newName", new AttributeValue("Kevin Updated"));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#name", "name");

        UpdateItemRequest request = new UpdateItemRequest()
                .withTableName("user")
                .withKey(key)
                .withUpdateExpression("SET #name = :newName")
                .withConditionExpression("attribute_exists(postCode) OR attribute_not_exists(postCode)")
                .withExpressionAttributeNames(expressionAttributeNames)
                .withExpressionAttributeValues(expressionAttributeValues);

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
        key.put("Id", new AttributeValue("complex-12"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":zero", new AttributeValue().withN("0"));

        DeleteItemRequest deleteRequest = new DeleteItemRequest()
                .withTableName("user")
                .withKey(key)
                .withConditionExpression("numberOfPlaylists = :zero AND attribute_exists(postCode)")
                .withExpressionAttributeValues(expressionAttributeValues);

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
        key.put("Id", new AttributeValue("complex-13"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":zero", new AttributeValue().withN("0"));

        DeleteItemRequest deleteRequest = new DeleteItemRequest()
                .withTableName("user")
                .withKey(key)
                .withConditionExpression("numberOfPlaylists = :zero")
                .withExpressionAttributeValues(expressionAttributeValues);

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
        key.put("Id", new AttributeValue("complex-14"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newName", new AttributeValue("Nancy Updated"));
        expressionAttributeValues.put(":min", new AttributeValue().withN("10"));
        expressionAttributeValues.put(":max", new AttributeValue().withN("20"));

        Map<String, String> expressionAttributeNames = new HashMap<>();
        expressionAttributeNames.put("#name", "name");

        UpdateItemRequest request = new UpdateItemRequest()
                .withTableName("user")
                .withKey(key)
                .withUpdateExpression("SET #name = :newName")
                .withConditionExpression("numberOfPlaylists BETWEEN :min AND :max AND attribute_exists(postCode)")
                .withExpressionAttributeNames(expressionAttributeNames)
                .withExpressionAttributeValues(expressionAttributeValues);

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
        key1.put("Id", new AttributeValue("complex-txn-1"));
        Map<String, AttributeValue> values1 = new HashMap<>();
        values1.put(":inc", new AttributeValue().withN("10"));
        values1.put(":threshold", new AttributeValue().withN("5"));

        actions.add(new TransactWriteItem().withUpdate(
                new Update()
                        .withTableName("user")
                        .withKey(key1)
                        .withUpdateExpression("SET numberOfPlaylists = numberOfPlaylists + :inc")
                        .withConditionExpression("numberOfPlaylists > :threshold AND attribute_exists(postCode)")
                        .withExpressionAttributeValues(values1)
        ));

        // Update user2
        Map<String, AttributeValue> key2 = new HashMap<>();
        key2.put("Id", new AttributeValue("complex-txn-2"));
        Map<String, AttributeValue> values2 = new HashMap<>();
        values2.put(":inc", new AttributeValue().withN("5"));

        actions.add(new TransactWriteItem().withUpdate(
                new Update()
                        .withTableName("user")
                        .withKey(key2)
                        .withUpdateExpression("SET numberOfPlaylists = numberOfPlaylists + :inc")
                        .withExpressionAttributeValues(values2)
        ));

        TransactWriteItemsRequest request = new TransactWriteItemsRequest()
                .withTransactItems(actions);
        amazonDynamoDB.transactWriteItems(request);

        // Then - Both updates should succeed
        User updated1 = userRepository.findById("complex-txn-1").get();
        User updated2 = userRepository.findById("complex-txn-2").get();
        assertThat(updated1.getNumberOfPlaylists()).isEqualTo(20);
        assertThat(updated2.getNumberOfPlaylists()).isEqualTo(10);
    }
}
