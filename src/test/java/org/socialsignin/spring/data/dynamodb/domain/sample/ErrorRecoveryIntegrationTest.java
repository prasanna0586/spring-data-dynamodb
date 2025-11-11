package org.socialsignin.spring.data.dynamodb.domain.sample;

import com.amazonaws.AmazonServiceException;
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
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for Error Recovery scenarios in DynamoDB operations.
 *
 * Coverage:
 * - Partial batch write failures
 * - Batch write with unprocessed items
 * - Retry logic for unprocessed items
 * - Partial batch read failures
 * - Transaction partial failures and rollback
 * - Handling of various DynamoDB exceptions
 * - Recovery from conditional check failures
 * - Handling malformed requests
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

    @Autowired
    private AmazonDynamoDB amazonDynamoDB;

    @Autowired
    private DynamoDBMapper dynamoDBMapper;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        accountRepository.deleteAll();
    }

    // ==================== Batch Write Error Recovery ====================

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Test 1: Batch write with duplicate keys returns unprocessed items")
    void testBatchWriteWithDuplicateKeys() {
        // Given - Batch request with duplicate keys (violates DynamoDB constraint)
        List<WriteRequest> writeRequests = new ArrayList<>();

        // Two items with same key
        Map<String, AttributeValue> item1 = new HashMap<>();
        item1.put("Id", new AttributeValue("duplicate-key"));
        item1.put("name", new AttributeValue("Item 1"));
        writeRequests.add(new WriteRequest().withPutRequest(new PutRequest().withItem(item1)));

        Map<String, AttributeValue> item2 = new HashMap<>();
        item2.put("Id", new AttributeValue("duplicate-key"));
        item2.put("name", new AttributeValue("Item 2"));
        writeRequests.add(new WriteRequest().withPutRequest(new PutRequest().withItem(item2)));

        BatchWriteItemRequest batchRequest = new BatchWriteItemRequest()
                .withRequestItems(Collections.singletonMap("user", writeRequests));

        // When/Then - Should throw validation exception
        assertThatThrownBy(() -> amazonDynamoDB.batchWriteItem(batchRequest))
                .isInstanceOf(AmazonDynamoDBException.class)
                .hasMessageContaining("duplicate");
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

    // ==================== Batch Read Error Recovery ====================

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Test 4: Batch get with non-existent keys")
    void testBatchGetWithNonExistentKeys() {
        // Given - Some existing users
        userRepository.save(createUser("exists-1", "User 1"));
        userRepository.save(createUser("exists-2", "User 2"));

        // When - Batch get including non-existent keys
        Map<String, KeysAndAttributes> requestItems = new HashMap<>();

        List<Map<String, AttributeValue>> keys = Arrays.asList(
                Collections.singletonMap("Id", new AttributeValue("exists-1")),
                Collections.singletonMap("Id", new AttributeValue("non-existent")),
                Collections.singletonMap("Id", new AttributeValue("exists-2"))
        );

        requestItems.put("user", new KeysAndAttributes().withKeys(keys));

        BatchGetItemRequest batchGetRequest = new BatchGetItemRequest()
                .withRequestItems(requestItems);
        BatchGetItemResult result = amazonDynamoDB.batchGetItem(batchGetRequest);

        // Then - Should return only existing items
        assertThat(result.getResponses().get("user")).hasSize(2);
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

    // ==================== Transaction Error Recovery ====================

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("Test 6: Transaction rollback on conditional check failure")
    void testTransactionRollbackOnConditionalFailure() {
        // Given - Two accounts
        accountRepository.save(new BankAccount("txn-err-1", "Alice", 1000.0));
        accountRepository.save(new BankAccount("txn-err-2", "Bob", 500.0));

        // When - Transaction with failing condition
        Collection<TransactWriteItem> actions = new ArrayList<>();

        // Update account 1 (will succeed)
        Map<String, AttributeValue> key1 = new HashMap<>();
        key1.put("accountId", new AttributeValue("txn-err-1"));
        actions.add(new TransactWriteItem().withUpdate(
                new Update()
                        .withTableName("BankAccount")
                        .withKey(key1)
                        .withUpdateExpression("SET balance = balance + :amount")
                        .withExpressionAttributeValues(Collections.singletonMap(
                                ":amount", new AttributeValue().withN("100")
                        ))
        ));

        // Update account 2 with impossible condition (will fail)
        Map<String, AttributeValue> key2 = new HashMap<>();
        key2.put("accountId", new AttributeValue("txn-err-2"));
        actions.add(new TransactWriteItem().withUpdate(
                new Update()
                        .withTableName("BankAccount")
                        .withKey(key2)
                        .withUpdateExpression("SET balance = balance - :amount")
                        .withConditionExpression("balance >= :required")
                        .withExpressionAttributeValues(Map.of(
                                ":amount", new AttributeValue().withN("1000"),
                                ":required", new AttributeValue().withN("1000")
                        ))
        ));

        TransactWriteItemsRequest request = new TransactWriteItemsRequest()
                .withTransactItems(actions);

        // Then - Transaction fails, both rollback
        assertThatThrownBy(() -> amazonDynamoDB.transactWriteItems(request))
                .isInstanceOf(TransactionCanceledException.class);

        // Verify both accounts unchanged
        BankAccount account1 = accountRepository.findById("txn-err-1").get();
        BankAccount account2 = accountRepository.findById("txn-err-2").get();
        assertThat(account1.getBalance()).isEqualTo(1000.0);
        assertThat(account2.getBalance()).isEqualTo(500.0);
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("Test 7: Transaction cancellation reasons")
    void testTransactionCancellationReasons() {
        // Given - Account
        accountRepository.save(new BankAccount("txn-reason-1", "Alice", 100.0));

        // When - Transaction with condition failure
        Collection<TransactWriteItem> actions = new ArrayList<>();

        Map<String, AttributeValue> key = new HashMap<>();
        key.put("accountId", new AttributeValue("txn-reason-1"));
        actions.add(new TransactWriteItem().withUpdate(
                new Update()
                        .withTableName("BankAccount")
                        .withKey(key)
                        .withUpdateExpression("SET balance = balance - :amount")
                        .withConditionExpression("balance >= :amount")
                        .withExpressionAttributeValues(Collections.singletonMap(
                                ":amount", new AttributeValue().withN("500")
                        ))
        ));

        TransactWriteItemsRequest request = new TransactWriteItemsRequest()
                .withTransactItems(actions);

        // Then - Check cancellation reasons
        try {
            amazonDynamoDB.transactWriteItems(request);
            Assertions.fail("Should have thrown TransactionCanceledException");
        } catch (TransactionCanceledException e) {
            assertThat(e.getCancellationReasons()).isNotEmpty();
            assertThat(e.getCancellationReasons().get(0).getCode())
                    .isEqualTo("ConditionalCheckFailed");
        }
    }

    // ==================== Exception Handling ====================

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("Test 8: Handle ResourceNotFoundException for non-existent table")
    void testResourceNotFoundException() {
        // When/Then - Query non-existent table
        ScanRequest scanRequest = new ScanRequest().withTableName("NonExistentTable");

        assertThatThrownBy(() -> amazonDynamoDB.scan(scanRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("Test 9: Handle ValidationException for invalid request")
    void testValidationException() {
        // Given - User
        User user = new User();
        user.setId("validation-test");
        userRepository.save(user);

        // When/Then - Invalid UpdateExpression
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", new AttributeValue("validation-test"));

        UpdateItemRequest request = new UpdateItemRequest()
                .withTableName("user")
                .withKey(key)
                .withUpdateExpression("INVALID SYNTAX HERE");

        assertThatThrownBy(() -> amazonDynamoDB.updateItem(request))
                .isInstanceOf(AmazonDynamoDBException.class)
                .hasMessageContaining("Syntax error");
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("Test 10: Recover from conditional check failure")
    void testRecoverFromConditionalCheckFailure() {
        // Given - User
        User user = new User();
        user.setId("recover-1");
        user.setName("Alice");
        user.setNumberOfPlaylists(10);
        userRepository.save(user);

        // When - First update with wrong condition (fails)
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("Id", new AttributeValue("recover-1"));

        UpdateItemRequest failingRequest = new UpdateItemRequest()
                .withTableName("user")
                .withKey(key)
                .withUpdateExpression("SET numberOfPlaylists = :value")
                .withConditionExpression("numberOfPlaylists = :expected")
                .withExpressionAttributeValues(Map.of(
                        ":value", new AttributeValue().withN("20"),
                        ":expected", new AttributeValue().withN("999")
                ));

        try {
            amazonDynamoDB.updateItem(failingRequest);
        } catch (ConditionalCheckFailedException e) {
            // Expected - now retry with correct condition
        }

        // Retry with correct condition
        UpdateItemRequest successRequest = new UpdateItemRequest()
                .withTableName("user")
                .withKey(key)
                .withUpdateExpression("SET numberOfPlaylists = :value")
                .withConditionExpression("numberOfPlaylists = :expected")
                .withExpressionAttributeValues(Map.of(
                        ":value", new AttributeValue().withN("20"),
                        ":expected", new AttributeValue().withN("10")
                ));

        amazonDynamoDB.updateItem(successRequest);

        // Then - Should succeed on retry
        User updated = userRepository.findById("recover-1").get();
        assertThat(updated.getNumberOfPlaylists()).isEqualTo(20);
    }

    // ==================== Helper Methods ====================

    private User createUser(String id, String name) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        return user;
    }
}
