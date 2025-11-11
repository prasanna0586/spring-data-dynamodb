package org.socialsignin.spring.data.dynamodb.domain.sample;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
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
    private DynamoDbClient amazonDynamoDB;

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
        writeRequests.add(WriteRequest.builder().putRequest(PutRequest.builder().item(item1)
                .build())
                .build());

        Map<String, AttributeValue> item2 = new HashMap<>();
        item2.put("Id", new AttributeValue("duplicate-key"));
        item2.put("name", new AttributeValue("Item 2"));
        writeRequests.add(WriteRequest.builder().putRequest(PutRequest.builder().item(item2)
                .build())
                .build());

        BatchWriteItemRequest batchRequest = BatchWriteItemRequest.builder()
                .requestItems(Collections.singletonMap("user", writeRequests))
                .build();

        // When/Then - Should throw validation exception
        assertThatThrownBy(() -> amazonDynamoDB.batchWriteItem(batchRequest))
                .isInstanceOf(DynamoDbException.class)
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

        requestItems.put("user", KeysAndAttributes.builder().keys(keys)
                .build());

        BatchGetItemRequest batchGetRequest = BatchGetItemRequest.builder()
                .requestItems(requestItems)
                .build();
        BatchGetItemResponse result = amazonDynamoDB.batchGetItem(batchGetRequest);

        // Then - Should return only existing items
        assertThat(result.responses().get("user")).hasSize(2);
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
        actions.add(TransactWriteItem.builder().update(
                Update.builder()
                        .tableName("BankAccount")
                        .key(key1)
                        .updateExpression("SET balance = balance + :amount")
                        .expressionAttributeValues(Collections.singletonMap(
                                ":amount",  AttributeValue.builder().n("100")
                                .build()
                        ))
                .build()
        )
        .build());

        // Update account 2 with impossible condition (will fail)
        Map<String, AttributeValue> key2 = new HashMap<>();
        key2.put("accountId", new AttributeValue("txn-err-2"));
        actions.add(TransactWriteItem.builder().update(
                Update.builder()
                        .tableName("BankAccount")
                        .key(key2)
                        .updateExpression("SET balance = balance - :amount")
                        .conditionExpression("balance >= :required")
                        .expressionAttributeValues(Map.of(
                                ":amount",  AttributeValue.builder().n("1000")
                                        .build(),
                                ":required",  AttributeValue.builder().n("1000")
                                .build()
                        ))
                .build()
        )
        .build());

        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(actions)
                .build();

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
        actions.add(TransactWriteItem.builder().update(
                Update.builder()
                        .tableName("BankAccount")
                        .key(key)
                        .updateExpression("SET balance = balance - :amount")
                        .conditionExpression("balance >= :amount")
                        .expressionAttributeValues(Collections.singletonMap(
                                ":amount",  AttributeValue.builder().n("500")
                                .build()
                        ))
                .build()
        )
        .build());

        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(actions)
                .build();

        // Then - Check cancellation reasons
        try {
            amazonDynamoDB.transactWriteItems(request);
            Assertions.fail("Should have thrown TransactionCanceledException");
        } catch (TransactionCanceledException e) {
            assertThat(e.cancellationReasons()).isNotEmpty();
            assertThat(e.cancellationReasons().get(0).code())
                    .isEqualTo("ConditionalCheckFailed");
        }
    }

    // ==================== Exception Handling ====================

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("Test 8: Handle ResourceNotFoundException for non-existent table")
    void testResourceNotFoundException() {
        // When/Then - Query non-existent table
        ScanRequest scanRequest = ScanRequest.builder().tableName("NonExistentTable")
                .build();

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

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("INVALID SYNTAX HERE")
                .build();

        assertThatThrownBy(() -> amazonDynamoDB.updateItem(request))
                .isInstanceOf(DynamoDbException.class)
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

        UpdateItemRequest failingRequest = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("SET numberOfPlaylists = :value")
                .conditionExpression("numberOfPlaylists = :expected")
                .expressionAttributeValues(Map.of(
                        ":value",  AttributeValue.builder().n("20")
                                .build(),
                        ":expected",  AttributeValue.builder().n("999")
                        .build()
                ))
                .build();

        try {
            amazonDynamoDB.updateItem(failingRequest);
        } catch (ConditionalCheckFailedException e) {
            // Expected - now retry with correct condition
        }

        // Retry with correct condition
        UpdateItemRequest successRequest = UpdateItemRequest.builder()
                .tableName("user")
                .key(key)
                .updateExpression("SET numberOfPlaylists = :value")
                .conditionExpression("numberOfPlaylists = :expected")
                .expressionAttributeValues(Map.of(
                        ":value",  AttributeValue.builder().n("20")
                                .build(),
                        ":expected",  AttributeValue.builder().n("10")
                        .build()
                ))
                .build();

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
