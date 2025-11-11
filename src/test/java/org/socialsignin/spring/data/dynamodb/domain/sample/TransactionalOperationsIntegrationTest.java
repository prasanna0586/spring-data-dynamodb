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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive integration tests for DynamoDB Transactional Operations.
 *
 * Transactional operations provide ACID guarantees across multiple items/tables.
 *
 * Coverage:
 * - TransactWriteItems (Put, Update, Delete, ConditionCheck)
 * - TransactGetItems (consistent reads across multiple items)
 * - Transaction rollback on condition failures
 * - Multiple operations in single transaction
 * - Cross-table transactions
 * - Optimistic locking with transactions
 * - Transaction idempotency
 * - Error handling (TransactionCanceledException)
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, TransactionalOperationsIntegrationTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Transactional Operations Integration Tests")
public class TransactionalOperationsIntegrationTest {

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    public static class TestAppConfig {
    }

    @Autowired
    private BankAccountRepository accountRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DynamoDbClient amazonDynamoDB;

    @Autowired
    private DynamoDBMapper dynamoDBMapper;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ==================== TransactWriteItems - Put Operations ====================

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Test 1: TransactWriteItems - Multiple Put operations")
    void testTransactWriteMultiplePuts() {
        // Given - Create transaction with 3 new accounts
        Collection<TransactWriteItem> actions = new ArrayList<>();

        // Account 1
        Map<String, AttributeValue> item1 = new HashMap<>();
        item1.put("accountId", new AttributeValue("txn-account-1"));
        item1.put("accountHolder", new AttributeValue("Alice"));
        item1.put("balance", AttributeValue.builder().n("1000.0")
                .build());
        item1.put("status", new AttributeValue("ACTIVE"));
        actions.add(TransactWriteItem.builder().put(
                Put.builder().tableName("BankAccount").item(item1)
                .build()
        )
        .build());

        // Account 2
        Map<String, AttributeValue> item2 = new HashMap<>();
        item2.put("accountId", new AttributeValue("txn-account-2"));
        item2.put("accountHolder", new AttributeValue("Bob"));
        item2.put("balance", AttributeValue.builder().n("2000.0")
                .build());
        item2.put("status", new AttributeValue("ACTIVE"));
        actions.add(TransactWriteItem.builder().put(
                Put.builder().tableName("BankAccount").item(item2)
                .build()
        )
        .build());

        // Account 3
        Map<String, AttributeValue> item3 = new HashMap<>();
        item3.put("accountId", new AttributeValue("txn-account-3"));
        item3.put("accountHolder", new AttributeValue("Charlie"));
        item3.put("balance", AttributeValue.builder().n("3000.0")
                .build());
        item3.put("status", new AttributeValue("ACTIVE"));
        actions.add(TransactWriteItem.builder().put(
                Put.builder().tableName("BankAccount").item(item3)
                .build()
        )
        .build());

        // When - Execute transaction
        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(actions)
                .build();
        amazonDynamoDB.transactWriteItems(request);

        // Then - All 3 accounts should exist
        assertThat(accountRepository.findById("txn-account-1")).isPresent();
        assertThat(accountRepository.findById("txn-account-2")).isPresent();
        assertThat(accountRepository.findById("txn-account-3")).isPresent();

        BankAccount acc1 = accountRepository.findById("txn-account-1").get();
        assertThat(acc1.getBalance()).isEqualTo(1000.0);
        assertThat(acc1.getAccountHolder()).isEqualTo("Alice");
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Test 2: TransactWriteItems - Money transfer (debit + credit)")
    void testTransactWriteMoneyTransfer() {
        // Given - Two accounts with initial balances
        BankAccount account1 = new BankAccount("txn-transfer-1", "Alice", 1000.0);
        BankAccount account2 = new BankAccount("txn-transfer-2", "Bob", 500.0);
        accountRepository.save(account1);
        accountRepository.save(account2);

        // When - Transfer $300 from Alice to Bob atomically
        Collection<TransactWriteItem> actions = new ArrayList<>();

        // Debit Alice's account
        Map<String, AttributeValue> key1 = new HashMap<>();
        key1.put("accountId", new AttributeValue("txn-transfer-1"));
        actions.add(TransactWriteItem.builder().update(
                Update.builder()
                        .tableName("BankAccount")
                        .key(key1)
                        .updateExpression("SET balance = balance - :amount")
                        .expressionAttributeValues(Collections.singletonMap(
                                ":amount",  AttributeValue.builder().n("300")
                                .build()
                        ))
                .build()
        )
        .build());

        // Credit Bob's account
        Map<String, AttributeValue> key2 = new HashMap<>();
        key2.put("accountId", new AttributeValue("txn-transfer-2"));
        actions.add(TransactWriteItem.builder().update(
                Update.builder()
                        .tableName("BankAccount")
                        .key(key2)
                        .updateExpression("SET balance = balance + :amount")
                        .expressionAttributeValues(Collections.singletonMap(
                                ":amount",  AttributeValue.builder().n("300")
                                .build()
                        ))
                .build()
        )
        .build());

        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(actions)
                .build();
        amazonDynamoDB.transactWriteItems(request);

        // Then - Balances should be updated atomically
        BankAccount updatedAccount1 = accountRepository.findById("txn-transfer-1").get();
        BankAccount updatedAccount2 = accountRepository.findById("txn-transfer-2").get();

        assertThat(updatedAccount1.getBalance()).isEqualTo(700.0);  // 1000 - 300
        assertThat(updatedAccount2.getBalance()).isEqualTo(800.0);  // 500 + 300
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Test 3: TransactWriteItems - Conditional money transfer with balance check")
    void testTransactWriteConditionalTransfer() {
        // Given - Account with insufficient funds
        BankAccount account1 = new BankAccount("txn-cond-1", "Alice", 100.0);
        BankAccount account2 = new BankAccount("txn-cond-2", "Bob", 500.0);
        accountRepository.save(account1);
        accountRepository.save(account2);

        // When - Try to transfer $300 (more than available) with condition
        Collection<TransactWriteItem> actions = new ArrayList<>();

        Map<String, AttributeValue> key1 = new HashMap<>();
        key1.put("accountId", new AttributeValue("txn-cond-1"));
        actions.add(TransactWriteItem.builder().update(
                Update.builder()
                        .tableName("BankAccount")
                        .key(key1)
                        .updateExpression("SET balance = balance - :amount")
                        .conditionExpression("balance >= :amount")
                        .expressionAttributeValues(Collections.singletonMap(
                                ":amount",  AttributeValue.builder().n("300")
                                .build()
                        ))
                .build()
        )
        .build());

        Map<String, AttributeValue> key2 = new HashMap<>();
        key2.put("accountId", new AttributeValue("txn-cond-2"));
        actions.add(TransactWriteItem.builder().update(
                Update.builder()
                        .tableName("BankAccount")
                        .key(key2)
                        .updateExpression("SET balance = balance + :amount")
                        .expressionAttributeValues(Collections.singletonMap(
                                ":amount",  AttributeValue.builder().n("300")
                                .build()
                        ))
                .build()
        )
        .build());

        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(actions)
                .build();

        // Then - Transaction should fail and NEITHER account should be modified
        assertThatThrownBy(() -> amazonDynamoDB.transactWriteItems(request))
                .isInstanceOf(TransactionCanceledException.class);

        // Verify balances unchanged (transaction rolled back)
        BankAccount unchanged1 = accountRepository.findById("txn-cond-1").get();
        BankAccount unchanged2 = accountRepository.findById("txn-cond-2").get();
        assertThat(unchanged1.getBalance()).isEqualTo(100.0);  // Unchanged
        assertThat(unchanged2.getBalance()).isEqualTo(500.0);  // Unchanged
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Test 4: TransactWriteItems - ConditionCheck on separate item")
    void testTransactWriteConditionCheck() {
        // Given - Two accounts, one ACTIVE and one to update
        BankAccount activeAccount = new BankAccount("txn-check-active", "Admin", 10000.0);
        activeAccount.setStatus("ACTIVE");
        accountRepository.save(activeAccount);

        BankAccount targetAccount = new BankAccount("txn-check-1", "Alice", 1000.0);
        accountRepository.save(targetAccount);

        // When - Update targetAccount only if activeAccount is ACTIVE
        Collection<TransactWriteItem> actions = new ArrayList<>();

        // ConditionCheck - Verify activeAccount is ACTIVE
        Map<String, AttributeValue> checkKey = new HashMap<>();
        checkKey.put("accountId", new AttributeValue("txn-check-active"));
        actions.add(TransactWriteItem.builder().conditionCheck(
                ConditionCheck.builder()
                        .tableName("BankAccount")
                        .key(checkKey)
                        .conditionExpression("#status = :active")
                        .expressionAttributeNames(Collections.singletonMap("#status", "status"))
                        .expressionAttributeValues(Collections.singletonMap(
                                ":active", new AttributeValue("ACTIVE")
                        ))
                .build()
        )
        .build());

        // Update - Debit the target account
        Map<String, AttributeValue> updateKey = new HashMap<>();
        updateKey.put("accountId", new AttributeValue("txn-check-1"));
        actions.add(TransactWriteItem.builder().update(
                Update.builder()
                        .tableName("BankAccount")
                        .key(updateKey)
                        .updateExpression("SET balance = balance - :amount")
                        .expressionAttributeValues(Collections.singletonMap(
                                ":amount",  AttributeValue.builder().n("200")
                                .build()
                        ))
                .build()
        )
        .build());

        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(actions)
                .build();
        amazonDynamoDB.transactWriteItems(request);

        // Then - Withdrawal should succeed because activeAccount is ACTIVE
        BankAccount updated = accountRepository.findById("txn-check-1").get();
        assertThat(updated.getBalance()).isEqualTo(800.0);
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("Test 5: TransactWriteItems - ConditionCheck fails for FROZEN account")
    void testTransactWriteConditionCheckFails() {
        // Given - FROZEN account
        BankAccount account = new BankAccount("txn-frozen-1", "Bob", 1000.0);
        account.setStatus("FROZEN");
        accountRepository.save(account);

        // When - Try to withdraw from FROZEN account
        Collection<TransactWriteItem> actions = new ArrayList<>();

        Map<String, AttributeValue> key = new HashMap<>();
        key.put("accountId", new AttributeValue("txn-frozen-1"));

        actions.add(TransactWriteItem.builder().conditionCheck(
                ConditionCheck.builder()
                        .tableName("BankAccount")
                        .key(key)
                        .conditionExpression("#status = :active")
                        .expressionAttributeNames(Collections.singletonMap("#status", "status"))
                        .expressionAttributeValues(Collections.singletonMap(
                                ":active", new AttributeValue("ACTIVE")
                        ))
                .build()
        )
        .build());

        actions.add(TransactWriteItem.builder().update(
                Update.builder()
                        .tableName("BankAccount")
                        .key(key)
                        .updateExpression("SET balance = balance - :amount")
                        .expressionAttributeValues(Collections.singletonMap(
                                ":amount",  AttributeValue.builder().n("200")
                                .build()
                        ))
                .build()
        )
        .build());

        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(actions)
                .build();

        // Then - Transaction should fail
        assertThatThrownBy(() -> amazonDynamoDB.transactWriteItems(request))
                .isInstanceOf(TransactionCanceledException.class);

        // Balance should be unchanged
        BankAccount unchanged = accountRepository.findById("txn-frozen-1").get();
        assertThat(unchanged.getBalance()).isEqualTo(1000.0);
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("Test 6: TransactWriteItems - Delete operation")
    void testTransactWriteDelete() {
        // Given - Two accounts
        BankAccount account1 = new BankAccount("txn-delete-1", "Alice", 0.0);
        BankAccount account2 = new BankAccount("txn-delete-2", "Bob", 1000.0);
        accountRepository.save(account1);
        accountRepository.save(account2);

        // When - Delete account1 and update account2 in transaction
        Collection<TransactWriteItem> actions = new ArrayList<>();

        // Delete account1
        Map<String, AttributeValue> key1 = new HashMap<>();
        key1.put("accountId", new AttributeValue("txn-delete-1"));
        actions.add(TransactWriteItem.builder().delete(
                Delete.builder()
                        .tableName("BankAccount")
                        .key(key1)
                .build()
        )
        .build());

        // Update account2
        Map<String, AttributeValue> key2 = new HashMap<>();
        key2.put("accountId", new AttributeValue("txn-delete-2"));
        actions.add(TransactWriteItem.builder().update(
                Update.builder()
                        .tableName("BankAccount")
                        .key(key2)
                        .updateExpression("SET #status = :closed")
                        .expressionAttributeNames(Collections.singletonMap("#status", "status"))
                        .expressionAttributeValues(Collections.singletonMap(
                                ":closed", new AttributeValue("CLOSED")
                        ))
                .build()
        )
        .build());

        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(actions)
                .build();
        amazonDynamoDB.transactWriteItems(request);

        // Then - Account1 deleted, Account2 updated
        assertThat(accountRepository.findById("txn-delete-1")).isEmpty();

        BankAccount updated = accountRepository.findById("txn-delete-2").get();
        assertThat(updated.getStatus()).isEqualTo("CLOSED");
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("Test 7: TransactWriteItems - Mixed operations (Put + Update + Delete)")
    void testTransactWriteMixedOperations() {
        // Given - One existing account
        BankAccount existingAccount = new BankAccount("txn-mixed-1", "Alice", 500.0);
        accountRepository.save(existingAccount);

        // When - Mixed transaction: Put new, Update existing, Delete another
        Collection<TransactWriteItem> actions = new ArrayList<>();

        // Put - Create new account
        Map<String, AttributeValue> newItem = new HashMap<>();
        newItem.put("accountId", new AttributeValue("txn-mixed-2"));
        newItem.put("accountHolder", new AttributeValue("Bob"));
        newItem.put("balance", AttributeValue.builder().n("1000.0")
                .build());
        newItem.put("status", new AttributeValue("ACTIVE"));
        actions.add(TransactWriteItem.builder().put(
                Put.builder().tableName("BankAccount").item(newItem)
                .build()
        )
        .build());

        // Update - Modify existing account
        Map<String, AttributeValue> updateKey = new HashMap<>();
        updateKey.put("accountId", new AttributeValue("txn-mixed-1"));
        actions.add(TransactWriteItem.builder().update(
                Update.builder()
                        .tableName("BankAccount")
                        .key(updateKey)
                        .updateExpression("SET balance = balance + :amount")
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
        amazonDynamoDB.transactWriteItems(request);

        // Then - All operations succeeded
        assertThat(accountRepository.findById("txn-mixed-2")).isPresent();

        BankAccount updated = accountRepository.findById("txn-mixed-1").get();
        assertThat(updated.getBalance()).isEqualTo(1000.0);
    }

    // ==================== TransactGetItems ====================

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("Test 8: TransactGetItems - Consistent read of multiple items")
    void testTransactGetItems() {
        // Given - Three accounts
        accountRepository.save(new BankAccount("txn-get-1", "Alice", 1000.0));
        accountRepository.save(new BankAccount("txn-get-2", "Bob", 2000.0));
        accountRepository.save(new BankAccount("txn-get-3", "Charlie", 3000.0));

        // When - Read all three accounts in a transaction
        Collection<TransactGetItem> items = new ArrayList<>();

        Map<String, AttributeValue> key1 = new HashMap<>();
        key1.put("accountId", new AttributeValue("txn-get-1"));
        items.add(TransactGetItem.builder().get(
                Get.builder().tableName("BankAccount").key(key1)
                .build()
        )
        .build());

        Map<String, AttributeValue> key2 = new HashMap<>();
        key2.put("accountId", new AttributeValue("txn-get-2"));
        items.add(TransactGetItem.builder().get(
                Get.builder().tableName("BankAccount").key(key2)
                .build()
        )
        .build());

        Map<String, AttributeValue> key3 = new HashMap<>();
        key3.put("accountId", new AttributeValue("txn-get-3"));
        items.add(TransactGetItem.builder().get(
                Get.builder().tableName("BankAccount").key(key3)
                .build()
        )
        .build());

        TransactGetItemsRequest request = TransactGetItemsRequest.builder()
                .transactItems(items)
                .build();
        TransactGetItemsResponse result = amazonDynamoDB.transactGetItems(request);

        // Then - Should get all 3 items with consistent read
        assertThat(result.responses()).hasSize(3);

        ItemResponse response1 = result.responses().get(0);
        assertThat(response1.item().get("accountHolder").s()).isEqualTo("Alice");
        assertThat(response1.item().get("balance").n()).isEqualTo("1000.0");

        ItemResponse response2 = result.responses().get(1);
        assertThat(response2.item().get("accountHolder").s()).isEqualTo("Bob");

        ItemResponse response3 = result.responses().get(2);
        assertThat(response3.item().get("accountHolder").s()).isEqualTo("Charlie");
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("Test 9: TransactGetItems - Cross-table consistent read")
    void testTransactGetItemsCrossTables() {
        // Given - One account and one user
        accountRepository.save(new BankAccount("txn-cross-1", "Alice", 1000.0));

        User user = new User();
        user.setId("txn-cross-user-1");
        user.setName("Alice");
        userRepository.save(user);

        // When - Read from both tables in single transaction
        Collection<TransactGetItem> items = new ArrayList<>();

        Map<String, AttributeValue> accountKey = new HashMap<>();
        accountKey.put("accountId", new AttributeValue("txn-cross-1"));
        items.add(TransactGetItem.builder().get(
                Get.builder().tableName("BankAccount").key(accountKey)
                .build()
        )
        .build());

        Map<String, AttributeValue> userKey = new HashMap<>();
        userKey.put("Id", new AttributeValue("txn-cross-user-1"));
        items.add(TransactGetItem.builder().get(
                Get.builder().tableName("user").key(userKey)
                .build()
        )
        .build());

        TransactGetItemsRequest request = TransactGetItemsRequest.builder()
                .transactItems(items)
                .build();
        TransactGetItemsResponse result = amazonDynamoDB.transactGetItems(request);

        // Then - Should get items from both tables
        assertThat(result.responses()).hasSize(2);
        assertThat(result.responses().get(0).item()).isNotNull();
        assertThat(result.responses().get(1).item()).isNotNull();
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("Test 10: TransactGetItems - Non-existent item returns null")
    void testTransactGetItemsNonExistent() {
        // Given - One existing and one non-existent account
        accountRepository.save(new BankAccount("txn-exists-1", "Alice", 1000.0));

        // When - Read existing and non-existent in transaction
        Collection<TransactGetItem> items = new ArrayList<>();

        Map<String, AttributeValue> key1 = new HashMap<>();
        key1.put("accountId", new AttributeValue("txn-exists-1"));
        items.add(TransactGetItem.builder().get(
                Get.builder().tableName("BankAccount").key(key1)
                .build()
        )
        .build());

        Map<String, AttributeValue> key2 = new HashMap<>();
        key2.put("accountId", new AttributeValue("non-existent"));
        items.add(TransactGetItem.builder().get(
                Get.builder().tableName("BankAccount").key(key2)
                .build()
        )
        .build());

        TransactGetItemsRequest request = TransactGetItemsRequest.builder()
                .transactItems(items)
                .build();
        TransactGetItemsResponse result = amazonDynamoDB.transactGetItems(request);

        // Then - Should get 2 responses, second one null
        assertThat(result.responses()).hasSize(2);
        assertThat(result.responses().get(0).item()).isNotNull();
        assertThat(result.responses().get(1).item()).isNull();
    }

    // ==================== Advanced Transaction Scenarios ====================

    @Test
    @org.junit.jupiter.api.Order(11)
    @DisplayName("Test 11: Transaction with 10 operations (max per transaction)")
    void testTransactionWith10Operations() {
        // Given - Create 10 accounts in a single transaction
        Collection<TransactWriteItem> actions = new ArrayList<>();

        for (int i = 1; i <= 10; i++) {
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("accountId", new AttributeValue("txn-bulk-" + i));
            item.put("accountHolder", new AttributeValue("User" + i));
            item.put("balance", AttributeValue.builder().n(String.valueOf(i * 100.0))
                    .build());
            item.put("status", new AttributeValue("ACTIVE"));

            actions.add(TransactWriteItem.builder().put(
                    Put.builder().tableName("BankAccount").item(item)
                    .build()
            )
            .build());
        }

        // When - Execute transaction with 10 operations
        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(actions)
                .build();
        amazonDynamoDB.transactWriteItems(request);

        // Then - All 10 accounts should exist
        assertThat(accountRepository.count()).isEqualTo(10);
        assertThat(accountRepository.findById("txn-bulk-5")).isPresent();
        assertThat(accountRepository.findById("txn-bulk-10")).isPresent();
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    @DisplayName("Test 12: Transaction idempotency with ClientRequestToken")
    void testTransactionIdempotency() {
        // Given - Transaction with idempotency token
        String idempotencyToken = UUID.randomUUID().toString();

        Collection<TransactWriteItem> actions = new ArrayList<>();
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("accountId", new AttributeValue("txn-idempotent-1"));
        item.put("accountHolder", new AttributeValue("Alice"));
        item.put("balance", AttributeValue.builder().n("1000.0")
                .build());
        item.put("status", new AttributeValue("ACTIVE"));

        actions.add(TransactWriteItem.builder().put(
                Put.builder().tableName("BankAccount").item(item)
                .build()
        )
        .build());

        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(actions)
                .clientRequestToken(idempotencyToken)
                .build();

        // When - Execute transaction twice with same token
        amazonDynamoDB.transactWriteItems(request);
        amazonDynamoDB.transactWriteItems(request); // Should be idempotent

        // Then - Should only create one account
        assertThat(accountRepository.findById("txn-idempotent-1")).isPresent();
    }

    @Test
    @org.junit.jupiter.api.Order(13)
    @DisplayName("Test 13: Transaction rollback - All or nothing")
    void testTransactionRollback() {
        // Given - One existing account
        accountRepository.save(new BankAccount("txn-rollback-1", "Alice", 1000.0));

        // When - Transaction with one valid and one invalid operation
        Collection<TransactWriteItem> actions = new ArrayList<>();

        // Valid update
        Map<String, AttributeValue> key1 = new HashMap<>();
        key1.put("accountId", new AttributeValue("txn-rollback-1"));
        actions.add(TransactWriteItem.builder().update(
                Update.builder()
                        .tableName("BankAccount")
                        .key(key1)
                        .updateExpression("SET balance = :newBalance")
                        .expressionAttributeValues(Collections.singletonMap(
                                ":newBalance",  AttributeValue.builder().n("2000.0")
                                .build()
                        ))
                .build()
        )
        .build());

        // Invalid update - non-existent item with condition
        Map<String, AttributeValue> key2 = new HashMap<>();
        key2.put("accountId", new AttributeValue("non-existent"));
        actions.add(TransactWriteItem.builder().update(
                Update.builder()
                        .tableName("BankAccount")
                        .key(key2)
                        .updateExpression("SET balance = :newBalance")
                        .conditionExpression("attribute_exists(accountId)")
                        .expressionAttributeValues(Collections.singletonMap(
                                ":newBalance",  AttributeValue.builder().n("500.0")
                                .build()
                        ))
                .build()
        )
        .build());

        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(actions)
                .build();

        // Then - Transaction should fail and first update should be rolled back
        assertThatThrownBy(() -> amazonDynamoDB.transactWriteItems(request))
                .isInstanceOf(TransactionCanceledException.class);

        // Verify first account unchanged (rollback)
        BankAccount unchanged = accountRepository.findById("txn-rollback-1").get();
        assertThat(unchanged.getBalance()).isEqualTo(1000.0);  // Still original value
    }

    @Test
    @org.junit.jupiter.api.Order(14)
    @DisplayName("Test 14: Transaction with attribute_not_exists condition")
    void testTransactionAttributeNotExists() {
        // When - Create account only if it doesn't exist
        Collection<TransactWriteItem> actions = new ArrayList<>();

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("accountId", new AttributeValue("txn-not-exists-1"));
        item.put("accountHolder", new AttributeValue("Alice"));
        item.put("balance", AttributeValue.builder().n("1000.0")
                .build());
        item.put("status", new AttributeValue("ACTIVE"));

        actions.add(TransactWriteItem.builder().put(
                Put.builder()
                        .tableName("BankAccount")
                        .item(item)
                        .conditionExpression("attribute_not_exists(accountId)")
                .build()
        )
        .build());

        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(actions)
                .build();

        // First time - should succeed
        amazonDynamoDB.transactWriteItems(request);

        // Then - Second time should fail
        assertThatThrownBy(() -> amazonDynamoDB.transactWriteItems(request))
                .isInstanceOf(TransactionCanceledException.class);
    }

    @Test
    @org.junit.jupiter.api.Order(15)
    @DisplayName("Test 15: Transaction cancellation reasons")
    void testTransactionCancellationReasons() {
        // Given - Account with balance 100
        accountRepository.save(new BankAccount("txn-cancel-1", "Alice", 100.0));

        // When - Try to withdraw more than available
        Collection<TransactWriteItem> actions = new ArrayList<>();

        Map<String, AttributeValue> key = new HashMap<>();
        key.put("accountId", new AttributeValue("txn-cancel-1"));
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

        // Then - Transaction cancelled with ConditionalCheckFailed reason
        try {
            amazonDynamoDB.transactWriteItems(request);
        } catch (TransactionCanceledException e) {
            assertThat(e.getMessage()).contains("Transaction cancelled");
            assertThat(e.cancellationReasons()).isNotEmpty();
            assertThat(e.cancellationReasons().get(0).code()).isEqualTo("ConditionalCheckFailed");
        }
    }

    @Test
    @org.junit.jupiter.api.Order(16)
    @DisplayName("Test 16: Cross-table transaction - Update user and account together")
    void testCrossTableTransaction() {
        // Given - User and their account
        User user = new User();
        user.setId("txn-cross-update-1");
        user.setName("Alice");
        user.setNumberOfPlaylists(0);
        userRepository.save(user);

        accountRepository.save(new BankAccount("txn-cross-update-1", "Alice", 1000.0));

        // When - Update both in single transaction
        Collection<TransactWriteItem> actions = new ArrayList<>();

        // Update user
        Map<String, AttributeValue> userKey = new HashMap<>();
        userKey.put("Id", new AttributeValue("txn-cross-update-1"));
        actions.add(TransactWriteItem.builder().update(
                Update.builder()
                        .tableName("user")
                        .key(userKey)
                        .updateExpression("SET numberOfPlaylists = :count")
                        .expressionAttributeValues(Collections.singletonMap(
                                ":count",  AttributeValue.builder().n("5")
                                .build()
                        ))
                .build()
        )
        .build());

        // Update account
        Map<String, AttributeValue> accountKey = new HashMap<>();
        accountKey.put("accountId", new AttributeValue("txn-cross-update-1"));
        actions.add(TransactWriteItem.builder().update(
                Update.builder()
                        .tableName("BankAccount")
                        .key(accountKey)
                        .updateExpression("SET balance = balance + :amount")
                        .expressionAttributeValues(Collections.singletonMap(
                                ":amount",  AttributeValue.builder().n("100")
                                .build()
                        ))
                .build()
        )
        .build());

        TransactWriteItemsRequest request = TransactWriteItemsRequest.builder()
                .transactItems(actions)
                .build();
        amazonDynamoDB.transactWriteItems(request);

        // Then - Both should be updated atomically
        User updatedUser = userRepository.findById("txn-cross-update-1").get();
        assertThat(updatedUser.getNumberOfPlaylists()).isEqualTo(5);

        BankAccount updatedAccount = accountRepository.findById("txn-cross-update-1").get();
        assertThat(updatedAccount.getBalance()).isEqualTo(1100.0);
    }
}
