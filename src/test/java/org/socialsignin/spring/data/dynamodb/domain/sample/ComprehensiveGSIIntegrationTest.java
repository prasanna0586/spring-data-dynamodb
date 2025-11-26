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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive integration tests for Global Secondary Indexes (GSI).
 *
 * GSI characteristics tested:
 * - Can have different hash key from base table
 * - Can have different range key from base table
 * - Queried using GSI hash key + optional GSI range key
 * - Support different data types as keys (String, Integer, Instant, Double)
 * - Support range queries (after, before, between, greater than, less than)
 * - Multiple GSI on same table
 *
 * Coverage:
 * - GSI with String hash key + Integer range key
 * - GSI with String hash key + Instant range key (date queries)
 * - GSI with String hash key + Double range key (amount queries)
 * - Range queries on GSI
 * - Multiple GSI on same table
 * - GSI data isolation
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, ComprehensiveGSIIntegrationTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Comprehensive Global Secondary Index (GSI) Integration Tests")
public class ComprehensiveGSIIntegrationTest {

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    public static class TestAppConfig {
    }

    @Autowired
    private CustomerTransactionRepository transactionRepository;

    private static final String CUSTOMER_1 = "customer-001";
    private static final String CUSTOMER_2 = "customer-002";
    private static final String MERCHANT_A = "MERCHANT_A";
    private static final String MERCHANT_B = "MERCHANT_B";
    private static final String CATEGORY_FOOD = "FOOD";
    private static final String CATEGORY_TRAVEL = "TRAVEL";

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Test 1: Create and retrieve transactions - basic hash + range key query")
    void testBasicHashRangeKeyQuery() {
        // Given
        CustomerTransaction txn = new CustomerTransaction(CUSTOMER_1, "txn-001", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 50.0, "Pending");
        transactionRepository.save(txn);

        // When
        List<CustomerTransaction> transactions = transactionRepository.findByCustomerIdAndTransactionId(CUSTOMER_1, "txn-001");

        // Then
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getCustomerId()).isEqualTo(CUSTOMER_1);
        assertThat(transactions.get(0).getTransactionId()).isEqualTo("txn-001");
        assertThat(transactions.get(0).getMerchantId()).isEqualTo(MERCHANT_A);
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Test 2: GSI Query by hash key only - Find transactions by merchant")
    void testGSI_QueryByMerchantId() {
        // Given - Create transactions for different merchants
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-001", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 50.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-002", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 75.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_2, "txn-003", Instant.now(),
                MERCHANT_B, CATEGORY_TRAVEL, 200.0, "Completed"));

        // When - Query using GSI: merchantId-index
        List<CustomerTransaction> merchantATxns = transactionRepository.findByMerchantId(MERCHANT_A);

        // Then
        assertThat(merchantATxns).hasSize(2);
        assertThat(merchantATxns).allMatch(txn -> txn.getMerchantId().equals(MERCHANT_A));
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Test 3: GSI Query by hash + range key - Find transactions by merchant and date")
    void testGSI_QueryByMerchantIdAndDate() {
        // Given
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-001", twoDaysAgo,
                MERCHANT_A, CATEGORY_FOOD, 50.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-002", yesterday,
                MERCHANT_A, CATEGORY_FOOD, 75.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-003", now,
                MERCHANT_A, CATEGORY_FOOD, 100.0, "Completed"));

        // When - Query using GSI: merchantId-transactionDate-index
        List<CustomerTransaction> recentTxns = transactionRepository.findByMerchantIdAndTransactionDateAfter(
                MERCHANT_A, yesterday.minus(1, ChronoUnit.HOURS));

        // Then
        assertThat(recentTxns).hasSize(2);
        assertThat(recentTxns).allMatch(txn -> txn.getMerchantId().equals(MERCHANT_A));
        assertThat(recentTxns).allMatch(txn -> txn.getTransactionDate().isAfter(yesterday.minus(1, ChronoUnit.HOURS)));
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Test 4: GSI Range Query - Transactions after specific date")
    void testGSI_QueryByMerchantIdAndDateAfter() {
        // Given
        Instant now = Instant.now();
        Instant oneDayAgo = now.minus(1, ChronoUnit.DAYS);
        Instant threeDaysAgo = now.minus(3, ChronoUnit.DAYS);

        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-001", threeDaysAgo,
                MERCHANT_A, CATEGORY_FOOD, 50.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-002", oneDayAgo,
                MERCHANT_A, CATEGORY_FOOD, 75.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-003", now,
                MERCHANT_A, CATEGORY_FOOD, 100.0, "Completed"));

        // When
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);
        List<CustomerTransaction> recentTxns = transactionRepository.findByMerchantIdAndTransactionDateAfter(
                MERCHANT_A, twoDaysAgo);

        // Then
        assertThat(recentTxns).hasSize(2);
        assertThat(recentTxns).allMatch(txn -> txn.getTransactionDate().isAfter(twoDaysAgo));
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("Test 5: GSI Range Query - Transactions before specific date")
    void testGSI_QueryByMerchantIdAndDateBefore() {
        // Given
        Instant now = Instant.now();
        Instant oneDayAgo = now.minus(1, ChronoUnit.DAYS);
        Instant threeDaysAgo = now.minus(3, ChronoUnit.DAYS);

        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-001", threeDaysAgo,
                MERCHANT_A, CATEGORY_FOOD, 50.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-002", oneDayAgo,
                MERCHANT_A, CATEGORY_FOOD, 75.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-003", now,
                MERCHANT_A, CATEGORY_FOOD, 100.0, "Completed"));

        // When
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);
        List<CustomerTransaction> oldTxns = transactionRepository.findByMerchantIdAndTransactionDateBefore(
                MERCHANT_A, twoDaysAgo);

        // Then
        assertThat(oldTxns).hasSize(1);
        assertThat(oldTxns.get(0).getTransactionDate()).isBefore(twoDaysAgo);
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("Test 6: GSI Range Query - Transactions between dates")
    void testGSI_QueryByMerchantIdAndDateBetween() {
        // Given
        Instant now = Instant.now();
        Instant oneDayAgo = now.minus(1, ChronoUnit.DAYS);
        Instant threeDaysAgo = now.minus(3, ChronoUnit.DAYS);
        Instant fiveDaysAgo = now.minus(5, ChronoUnit.DAYS);

        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-001", fiveDaysAgo,
                MERCHANT_A, CATEGORY_FOOD, 50.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-002", threeDaysAgo,
                MERCHANT_A, CATEGORY_FOOD, 75.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-003", oneDayAgo,
                MERCHANT_A, CATEGORY_FOOD, 100.0, "Completed"));

        // When
        Instant fourDaysAgo = now.minus(4, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);
        List<CustomerTransaction> midRangeTxns = transactionRepository.findByMerchantIdAndTransactionDateBetween(
                MERCHANT_A, fourDaysAgo, twoDaysAgo);

        // Then
        assertThat(midRangeTxns).hasSize(1);
        assertThat(midRangeTxns.get(0).getTransactionDate()).isBetween(fourDaysAgo, twoDaysAgo);
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("Test 7: GSI with Double range key - Query transactions by category and amount (greater than)")
    void testGSI_QueryByCategoryAndAmountGreaterThan() {
        // Given
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-001", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 25.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-002", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 75.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-003", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 150.0, "Completed"));

        // When - Query using GSI: category-amount-index
        List<CustomerTransaction> highValueTxns = transactionRepository.findByCategoryAndAmountGreaterThan(
                CATEGORY_FOOD, 50.0);

        // Then
        assertThat(highValueTxns).hasSize(2);
        assertThat(highValueTxns).allMatch(txn -> txn.getAmount() > 50.0);
        assertThat(highValueTxns).allMatch(txn -> txn.getCategory().equals(CATEGORY_FOOD));
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("Test 8: GSI with Double range key - Query transactions by category and amount (less than)")
    void testGSI_QueryByCategoryAndAmountLessThan() {
        // Given
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-001", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 25.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-002", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 75.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-003", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 150.0, "Completed"));

        // When
        List<CustomerTransaction> lowValueTxns = transactionRepository.findByCategoryAndAmountLessThan(
                CATEGORY_FOOD, 50.0);

        // Then
        assertThat(lowValueTxns).hasSize(1);
        assertThat(lowValueTxns.get(0).getAmount()).isLessThan(50.0);
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("Test 9: GSI with Double range key - Query transactions by category and amount range")
    void testGSI_QueryByCategoryAndAmountBetween() {
        // Given
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-001", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 25.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-002", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 75.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-003", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 150.0, "Completed"));

        // When
        List<CustomerTransaction> midValueTxns = transactionRepository.findByCategoryAndAmountBetween(
                CATEGORY_FOOD, 50.0, 100.0);

        // Then
        assertThat(midValueTxns).hasSize(1);
        assertThat(midValueTxns.get(0).getAmount()).isBetween(50.0, 100.0);
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("Test 10: GSI data isolation - Different merchants have separate transaction sets")
    void testGSI_DataIsolation() {
        // Given
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-001", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 50.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-002", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 75.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_2, "txn-003", Instant.now(),
                MERCHANT_B, CATEGORY_TRAVEL, 200.0, "Completed"));

        // When
        List<CustomerTransaction> merchantATxns = transactionRepository.findByMerchantId(MERCHANT_A);
        List<CustomerTransaction> merchantBTxns = transactionRepository.findByMerchantId(MERCHANT_B);

        // Then
        assertThat(merchantATxns).hasSize(2);
        assertThat(merchantBTxns).hasSize(1);
        assertThat(merchantATxns).allMatch(txn -> txn.getMerchantId().equals(MERCHANT_A));
        assertThat(merchantBTxns).allMatch(txn -> txn.getMerchantId().equals(MERCHANT_B));
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    @DisplayName("Test 11: Multiple GSI on same table - Query using different indexes")
    void testGSI_MultipleIndexesOnSameTable() {
        // Given
        Instant now = Instant.now();
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-001", now,
                MERCHANT_A, CATEGORY_FOOD, 50.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-002", now,
                MERCHANT_A, CATEGORY_TRAVEL, 200.0, "Completed"));

        // When - Query using different GSI
        List<CustomerTransaction> byMerchant = transactionRepository.findByMerchantId(MERCHANT_A);
        List<CustomerTransaction> byCategory = transactionRepository.findByCategory(CATEGORY_FOOD);

        // Then
        assertThat(byMerchant).hasSize(2);
        assertThat(byCategory).hasSize(1);
        assertThat(byCategory.get(0).getCategory()).isEqualTo(CATEGORY_FOOD);
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    @DisplayName("Test 12: GSI query returns no results when criteria not met")
    void testGSI_NoResultsWhenCriteriaNotMet() {
        // Given
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-001", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 50.0, "Completed"));

        // When
        List<CustomerTransaction> noResults = transactionRepository.findByMerchantId("NON_EXISTENT_MERCHANT");

        // Then
        assertThat(noResults).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.Order(13)
    @DisplayName("Test 13: GSI ordering - Transactions returned in sort key order")
    void testGSI_Ordering() {
        // Given
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-003", now,
                MERCHANT_A, CATEGORY_FOOD, 100.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-001", twoDaysAgo,
                MERCHANT_A, CATEGORY_FOOD, 50.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-002", yesterday,
                MERCHANT_A, CATEGORY_FOOD, 75.0, "Completed"));

        // When - Query should return in date order (ascending by default)
        List<CustomerTransaction> txns = transactionRepository.findByMerchantIdOrderByTransactionDateAsc(MERCHANT_A);

        // Then
        assertThat(txns).hasSize(3);
        assertThat(txns.get(0).getTransactionDate()).isEqualTo(twoDaysAgo);
        assertThat(txns.get(1).getTransactionDate()).isEqualTo(yesterday);
        assertThat(txns.get(2).getTransactionDate()).isEqualTo(now);
    }

    @Test
    @org.junit.jupiter.api.Order(14)
    @DisplayName("Test 14: GSI descending order")
    void testGSI_DescendingOrder() {
        // Given
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-001", twoDaysAgo,
                MERCHANT_A, CATEGORY_FOOD, 50.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-002", yesterday,
                MERCHANT_A, CATEGORY_FOOD, 75.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-003", now,
                MERCHANT_A, CATEGORY_FOOD, 100.0, "Completed"));

        // When
        List<CustomerTransaction> txns = transactionRepository.findByMerchantIdOrderByTransactionDateDesc(MERCHANT_A);

        // Then
        assertThat(txns).hasSize(3);
        assertThat(txns.get(0).getTransactionDate()).isEqualTo(now);
        assertThat(txns.get(1).getTransactionDate()).isEqualTo(yesterday);
        assertThat(txns.get(2).getTransactionDate()).isEqualTo(twoDaysAgo);
    }

    @Test
    @org.junit.jupiter.api.Order(15)
    @DisplayName("Test 15: GSI Range GreaterThanEqual (>=) - Transactions on or after specific date")
    void testGSI_QueryByMerchantIdAndDateGreaterThanEqual() {
        // Given
        Instant now = Instant.now();
        Instant oneDayAgo = now.minus(1, ChronoUnit.DAYS);
        Instant threeDaysAgo = now.minus(3, ChronoUnit.DAYS);

        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-001", threeDaysAgo,
                MERCHANT_A, CATEGORY_FOOD, 50.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-002", oneDayAgo,
                MERCHANT_A, CATEGORY_FOOD, 75.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-003", now,
                MERCHANT_A, CATEGORY_FOOD, 100.0, "Completed"));

        // When - Query using GSI: merchantId-transactionDate-index with >=
        List<CustomerTransaction> recentTxns = transactionRepository.findByMerchantIdAndTransactionDateGreaterThanEqual(
                MERCHANT_A, oneDayAgo);

        // Then - Should include transaction at exactly oneDayAgo and after
        assertThat(recentTxns).hasSize(2);
        assertThat(recentTxns).allMatch(txn ->
            txn.getTransactionDate().equals(oneDayAgo) || txn.getTransactionDate().isAfter(oneDayAgo));
        assertThat(recentTxns).anyMatch(txn -> txn.getTransactionDate().equals(oneDayAgo));
    }

    @Test
    @org.junit.jupiter.api.Order(16)
    @DisplayName("Test 16: GSI Range LessThanEqual (<=) - Transactions on or before specific date")
    void testGSI_QueryByMerchantIdAndDateLessThanEqual() {
        // Given
        Instant now = Instant.now();
        Instant oneDayAgo = now.minus(1, ChronoUnit.DAYS);
        Instant threeDaysAgo = now.minus(3, ChronoUnit.DAYS);

        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-001", threeDaysAgo,
                MERCHANT_A, CATEGORY_FOOD, 50.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-002", oneDayAgo,
                MERCHANT_A, CATEGORY_FOOD, 75.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-003", now,
                MERCHANT_A, CATEGORY_FOOD, 100.0, "Completed"));

        // When - Query using GSI: merchantId-transactionDate-index with <=
        List<CustomerTransaction> olderTxns = transactionRepository.findByMerchantIdAndTransactionDateLessThanEqual(
                MERCHANT_A, oneDayAgo);

        // Then - Should include transaction at exactly oneDayAgo and before
        assertThat(olderTxns).hasSize(2);
        assertThat(olderTxns).allMatch(txn ->
            txn.getTransactionDate().equals(oneDayAgo) || txn.getTransactionDate().isBefore(oneDayAgo));
        assertThat(olderTxns).anyMatch(txn -> txn.getTransactionDate().equals(oneDayAgo));
    }

    @Test
    @org.junit.jupiter.api.Order(17)
    @DisplayName("Test 17: GSI Range BEGINS_WITH on String - Transactions with status starting with prefix")
    void testGSI_QueryByCategoryAndStatusBeginsWith() {
        // Given - Create transactions with various statuses
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-001", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 50.0, "PENDING"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-002", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 75.0, "PENDING_APPROVAL"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-003", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 100.0, "COMPLETED"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-004", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 125.0, "PENDING_REVIEW"));

        // When - Query using GSI: category-status-index with begins_with
        List<CustomerTransaction> pendingTxns = transactionRepository.findByCategoryAndStatusStartingWith(
                CATEGORY_FOOD, "PENDING");

        // Then - Should return all transactions with status starting with "PENDING"
        assertThat(pendingTxns).hasSize(3);
        assertThat(pendingTxns).allMatch(txn -> txn.getStatus().startsWith("PENDING"));
        assertThat(pendingTxns).allMatch(txn -> txn.getCategory().equals(CATEGORY_FOOD));
    }

    @Test
    @org.junit.jupiter.api.Order(18)
    @DisplayName("Test 18: GSI Hash + Range + OrderBy - Transactions filtered and ordered")
    void testGSI_QueryByMerchantIdAndDateAfterWithOrderBy() {
        // Given
        Instant now = Instant.now();
        Instant oneDayAgo = now.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);
        Instant threeDaysAgo = now.minus(3, ChronoUnit.DAYS);

        // Insert in random order to test ordering
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-003", now,
                MERCHANT_A, CATEGORY_FOOD, 100.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-001", threeDaysAgo,
                MERCHANT_A, CATEGORY_FOOD, 50.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-004", oneDayAgo,
                MERCHANT_A, CATEGORY_FOOD, 125.0, "Completed"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-002", twoDaysAgo,
                MERCHANT_A, CATEGORY_FOOD, 75.0, "Completed"));

        // When - Query using GSI with range condition AND OrderBy
        List<CustomerTransaction> filteredAndOrderedTxns =
            transactionRepository.findByMerchantIdAndTransactionDateAfterOrderByTransactionDateAsc(
                MERCHANT_A, twoDaysAgo.plus(1, ChronoUnit.HOURS));

        // Then - Should return only transactions after twoDaysAgo, ordered by date ascending
        assertThat(filteredAndOrderedTxns).hasSize(2);
        assertThat(filteredAndOrderedTxns.get(0).getTransactionDate()).isEqualTo(oneDayAgo);
        assertThat(filteredAndOrderedTxns.get(1).getTransactionDate()).isEqualTo(now);
        assertThat(filteredAndOrderedTxns).allMatch(txn ->
            txn.getTransactionDate().isAfter(twoDaysAgo.plus(1, ChronoUnit.HOURS)));
    }

    @Test
    @org.junit.jupiter.api.Order(19)
    @DisplayName("Edge Case Test 1: Sparse GSI - Query works when some items lack GSI attributes")
    void testGSI_SparseIndex() {
        // Given - Create items where some have merchantId (GSI attribute) and some don't
        // This simulates a sparse GSI where not all table items are indexed
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-001", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 50.0, "Completed"));  // Has merchantId
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-002", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 75.0, "Completed"));  // Has merchantId
        transactionRepository.save(new CustomerTransaction(CUSTOMER_2, "txn-003", Instant.now(),
                null, CATEGORY_FOOD, 100.0, "Completed"));  // No merchantId (sparse)
        transactionRepository.save(new CustomerTransaction(CUSTOMER_2, "txn-004", Instant.now(),
                null, CATEGORY_TRAVEL, 200.0, "Completed"));  // No merchantId (sparse)

        // When - Query using GSI on merchantId (merchantId-index)
        // The library must correctly identify the GSI and generate a valid query
        List<CustomerTransaction> merchantATxns = transactionRepository.findByMerchantId(MERCHANT_A);
        List<CustomerTransaction> merchantBTxns = transactionRepository.findByMerchantId(MERCHANT_B);

        // Then - Should only return items that have the GSI attribute with the specified value
        assertThat(merchantATxns).hasSize(2);
        assertThat(merchantATxns).allMatch(txn -> MERCHANT_A.equals(txn.getMerchantId()));

        assertThat(merchantBTxns).isEmpty();  // No items with MERCHANT_B

        // Verify sparse items are NOT included in the GSI query results
        // (items with null merchantId should not appear in any merchantId query)
        assertThat(merchantATxns).noneMatch(txn -> "txn-003".equals(txn.getTransactionId()));
        assertThat(merchantATxns).noneMatch(txn -> "txn-004".equals(txn.getTransactionId()));
    }
}
