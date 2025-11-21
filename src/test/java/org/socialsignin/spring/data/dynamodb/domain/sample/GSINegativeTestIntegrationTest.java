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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * NEGATIVE TESTING for Global Secondary Index (GSI) queries.
 *
 * This test suite validates that INVALID GSI operations are properly rejected by the library.
 * These tests verify that library-level validation catches scenarios that DynamoDB doesn't validate.
 *
 * Based on DirectGSISDKTest findings:
 * - DynamoDB DOES NOT validate sort attributes (it always sorts by index range key)
 * - Library MUST validate to prevent unexpected behavior
 *
 * DynamoDB GSI Rules:
 * - When querying a GSI, you can ONLY sort by that GSI's range key
 * - You CANNOT sort by the main table range key when querying a GSI
 * - You CANNOT sort by a different GSI's range key
 *
 * Expected Outcomes:
 * - If production code is correct: Tests throw UnsupportedOperationException
 * - If validation is missing: Queries succeed but produce unexpected sort behavior
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, GSINegativeTestIntegrationTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("GSI Negative Testing - Invalid Operations Should Fail")
public class GSINegativeTestIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(GSINegativeTestIntegrationTest.class);

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    public static class TestAppConfig {
    }

    @Autowired
    private TransactionRepository transactionRepository;

    private static final String CUSTOMER_1 = "customer-001";
    private static final String MERCHANT_1 = "merchant-001";
    private static final String CATEGORY_1 = "electronics";

    private Instant now;
    private Instant yesterday;
    private Instant twoDaysAgo;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();

        // Setup test data
        now = Instant.now();
        yesterday = now.minus(1, ChronoUnit.DAYS);
        twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-001", twoDaysAgo,
                MERCHANT_1, CATEGORY_1, 100.0, "PENDING"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-002", yesterday,
                MERCHANT_1, CATEGORY_1, 200.0, "COMPLETED"));
        transactionRepository.save(new CustomerTransaction(CUSTOMER_1, "txn-003", now,
                MERCHANT_1, CATEGORY_1, 300.0, "PENDING"));
    }

    // ============================================================================
    // NEGATIVE TESTS - What library MUST reject
    // ============================================================================

    @Test
    @Order(1)
    @DisplayName("NEGATIVE: Query by GSI (merchantId) but sort by main table range key (transactionId) - Should fail")
    void testInvalidSort_GSIQuery_MainTableRangeKeySort() {
        logger.info("\n=== TEST 1: Query by merchantId (GSI), sort by transactionId (main range key) ===");
        logger.info("Expected: UnsupportedOperationException");
        logger.info("Reason: Cannot sort by main table range key when querying a GSI");

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class, () -> {
            // This query should fail because:
            // - Condition uses merchantId (GSI 1 partition key)
            // - Sort uses transactionId (main table range key)
            // When using GSI, can ONLY sort by that GSI's range key, not main table range key
            transactionRepository.findByMerchantIdOrderByTransactionIdAsc(MERCHANT_1);
        });

        logger.info("✅ CORRECTLY THREW UnsupportedOperationException");
        logger.info("   Message: {}", exception.getMessage());
        logger.info("   Production code correctly validates GSI sort constraints");
    }

    @Test
    @Order(2)
    @DisplayName("NEGATIVE: Query by one GSI (merchantId) but sort by different GSI range key (amount) - Should fail")
    void testInvalidSort_OneGSI_DifferentGSISort() {
        logger.info("\n=== TEST 2: Query by merchantId (GSI 1), sort by amount (GSI 2 range key) ===");
        logger.info("Expected: UnsupportedOperationException");
        logger.info("Reason: Cannot sort by a different GSI's range key");

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class, () -> {
            // This query should fail because:
            // - Condition uses merchantId (GSI 1 partition key: merchantId-transactionDate-index)
            // - Sort uses amount (GSI 2 range key: category-amount-index)
            // Cannot use range key from a different GSI
            transactionRepository.findByMerchantIdOrderByAmountAsc(MERCHANT_1);
        });

        logger.info("✅ CORRECTLY THREW UnsupportedOperationException: {}", exception.getMessage());
    }

    @Test
    @Order(3)
    @DisplayName("NEGATIVE: Query by GSI (category) but sort by main table range key (transactionId) - Should fail")
    void testInvalidSort_CategoryGSI_MainTableRangeKeySort() {
        logger.info("\n=== TEST 3: Query by category (GSI), sort by transactionId (main range key) ===");
        logger.info("Expected: UnsupportedOperationException");

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class, () -> {
            transactionRepository.findByCategoryOrderByTransactionIdDesc(CATEGORY_1);
        });

        logger.info("✅ CORRECTLY THREW UnsupportedOperationException: {}", exception.getMessage());
    }

    @Test
    @Order(4)
    @DisplayName("NEGATIVE: Query by one GSI (category) but sort by different GSI range key (transactionDate) - Should fail")
    void testInvalidSort_CategoryGSI_DifferentGSISort() {
        logger.info("\n=== TEST 4: Query by category (GSI 2), sort by transactionDate (GSI 1 range key) ===");
        logger.info("Expected: UnsupportedOperationException");

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class, () -> {
            transactionRepository.findByCategoryOrderByTransactionDateAsc(CATEGORY_1);
        });

        logger.info("✅ CORRECTLY THREW UnsupportedOperationException: {}", exception.getMessage());
    }

    // ============================================================================
    // POSITIVE TESTS - Valid GSI operations that should succeed
    // ============================================================================

    @Test
    @Order(10)
    @DisplayName("POSITIVE: Query by GSI (merchantId), sort by same GSI range key (transactionDate) - Should succeed")
    void testValidSort_GSIQuery_SameGSIRangeKeySort() {
        logger.info("\n=== TEST 10: Query by merchantId (GSI), sort by transactionDate (same GSI range key) ===");
        logger.info("Expected: SUCCESS");

        List<CustomerTransaction> transactions = transactionRepository
                .findByMerchantIdOrderByTransactionDateAsc(MERCHANT_1);

        logger.info("✅ QUERY SUCCEEDED (as expected for valid operation)");
        logger.info("   Returned {} transactions, sorted by transactionDate", transactions.size());

        assertThat(transactions).hasSize(3);
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(twoDaysAgo);
        assertThat(transactions.get(1).getTransactionDate()).isEqualTo(yesterday);
        assertThat(transactions.get(2).getTransactionDate()).isEqualTo(now);

        logger.info("   Results correctly sorted by transactionDate (GSI range key)");
    }

    @Test
    @Order(11)
    @DisplayName("POSITIVE: Query by GSI with range condition, sort by same GSI range key - Should succeed")
    void testValidSort_GSIQueryWithRange_SameGSIRangeKeySort() {
        logger.info("\n=== TEST 11: Query by merchantId + transactionDate > (GSI), sort by transactionDate ===");
        logger.info("Expected: SUCCESS");

        List<CustomerTransaction> transactions = transactionRepository
                .findByMerchantIdAndTransactionDateAfterOrderByTransactionDateAsc(MERCHANT_1, twoDaysAgo);

        logger.info("✅ QUERY SUCCEEDED (as expected for valid operation)");
        logger.info("   Returned {} transactions", transactions.size());

        assertThat(transactions).hasSize(2); // yesterday and now
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(yesterday);
        assertThat(transactions.get(1).getTransactionDate()).isEqualTo(now);
    }

    @Test
    @Order(12)
    @DisplayName("POSITIVE: Query by GSI (category), sort by same GSI range key (amount) - Should succeed")
    void testValidSort_CategoryGSI_SameGSIRangeKeySort() {
        logger.info("\n=== TEST 12: Query by category (GSI), sort by amount (same GSI range key) ===");
        logger.info("Expected: SUCCESS");

        List<CustomerTransaction> transactions = transactionRepository
                .findByCategoryOrderByAmountAsc(CATEGORY_1);

        logger.info("✅ QUERY SUCCEEDED (as expected for valid operation)");
        logger.info("   Returned {} transactions, sorted by amount", transactions.size());

        assertThat(transactions).hasSize(3);
        assertThat(transactions.get(0).getAmount()).isEqualTo(100.0);
        assertThat(transactions.get(1).getAmount()).isEqualTo(200.0);
        assertThat(transactions.get(2).getAmount()).isEqualTo(300.0);
    }

    @Test
    @Order(13)
    @DisplayName("POSITIVE: Query by GSI with range condition, sort DESC - Should succeed")
    void testValidSort_GSIQueryWithRange_SortDesc() {
        logger.info("\n=== TEST 13: Query by category + amount > (GSI), sort by amount DESC ===");
        logger.info("Expected: SUCCESS");

        List<CustomerTransaction> transactions = transactionRepository
                .findByCategoryAndAmountGreaterThanOrderByAmountDesc(CATEGORY_1, 100.0);

        logger.info("✅ QUERY SUCCEEDED (as expected for valid operation)");
        logger.info("   Returned {} transactions", transactions.size());

        assertThat(transactions).hasSize(2); // 200.0 and 300.0
        assertThat(transactions.get(0).getAmount()).isEqualTo(300.0);
        assertThat(transactions.get(1).getAmount()).isEqualTo(200.0);
    }

    @Test
    @Order(14)
    @DisplayName("POSITIVE: Query by GSI hash-only index, no sort - Should succeed")
    void testValidQuery_HashOnlyGSI_NoSort() {
        logger.info("\n=== TEST 14: Query by merchantId (hash-only GSI), no sort ===");
        logger.info("Expected: SUCCESS");

        List<CustomerTransaction> transactions = transactionRepository.findByMerchantId(MERCHANT_1);

        logger.info("✅ QUERY SUCCEEDED (as expected for valid operation)");
        logger.info("   Returned {} transactions", transactions.size());

        assertThat(transactions).hasSize(3);
    }

    @AfterAll
    static void printSummary() {
        logger.info("\n" + "=".repeat(80));
        logger.info("TEST SUITE SUMMARY");
        logger.info("=".repeat(80));
        logger.info("This test suite validates GSI sort constraint enforcement.");
        logger.info("");
        logger.info("If all tests PASS:");
        logger.info("  ✅ Library correctly validates GSI sort constraints");
        logger.info("  ✅ Invalid operations are rejected before reaching DynamoDB");
        logger.info("  ✅ Users get clear error messages instead of unexpected behavior");
        logger.info("");
        logger.info("If NEGATIVE tests FAIL (queries succeed instead of throwing exceptions):");
        logger.info("  ❌ BUG: GSI sort validation not enforcing constraints");
        logger.info("  ❌ Queries would produce unexpected sort behavior");
        logger.info("  ❌ Users would get confusing results (sorted by GSI range, not requested field)");
        logger.info("");
        logger.info("Key findings from DirectSDK tests:");
        logger.info("  - DynamoDB DOES NOT validate sort attributes");
        logger.info("  - DynamoDB always sorts by the index's range key");
        logger.info("  - Library MUST validate to prevent user confusion");
        logger.info("=".repeat(80) + "\n");
    }
}
