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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive test suite for all DynamoDB query patterns.
 * Tests Main Table, LSI, and GSI queries with various operations.
 *
 * Purpose: Identify ALL scenarios where index selection might fail,
 * not just patch OrderBy issues.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, ComprehensiveQueryPatternTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Comprehensive Query Pattern Tests - Main Table, LSI, and GSI")
public class ComprehensiveQueryPatternTest {

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    public static class TestAppConfig {
    }

    @Autowired
    private CustomerTransactionRepository transactionRepo;

    @Autowired
    private OrderRepository orderRepo;

    // Test data constants
    private static final String CUSTOMER_1 = "customer-001";
    private static final String CUSTOMER_2 = "customer-002";
    private static final String MERCHANT_A = "merchant-A";
    private static final String MERCHANT_B = "merchant-B";
    private static final String CATEGORY_FOOD = "FOOD";
    private static final String CATEGORY_TRAVEL = "TRAVEL";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_COMPLETED = "COMPLETED";

    @BeforeEach
    void setUp() {
        transactionRepo.deleteAll();
        orderRepo.deleteAll();
    }

    // ========================================
    // MAIN TABLE QUERIES (customerId = hash, transactionId = range)
    // ========================================

    @Test
    @Order(1)
    @DisplayName("Test 1: Main Table - Hash only")
    void testMainTable_HashOnly() {
        // Given
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-001", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 50.0, STATUS_COMPLETED));
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-002", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 75.0, STATUS_COMPLETED));
        transactionRepo.save(new CustomerTransaction(CUSTOMER_2, "txn-003", Instant.now(),
                MERCHANT_B, CATEGORY_TRAVEL, 200.0, STATUS_COMPLETED));

        // When
        List<CustomerTransaction> results = transactionRepo.findByCustomerId(CUSTOMER_1);

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(t -> t.getCustomerId().equals(CUSTOMER_1));
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Main Table - Hash + Range equality")
    void testMainTable_HashAndRangeEquality() {
        // Given
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-001", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 50.0, STATUS_COMPLETED));
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-002", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 75.0, STATUS_COMPLETED));

        // When
        List<CustomerTransaction> results = transactionRepo.findByCustomerIdAndTransactionId(CUSTOMER_1, "txn-001");

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTransactionId()).isEqualTo("txn-001");
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Main Table - Hash + Range GreaterThan")
    void testMainTable_HashAndRangeGreaterThan() {
        // Given
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-001", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 50.0, STATUS_COMPLETED));
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-002", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 75.0, STATUS_COMPLETED));
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-003", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 100.0, STATUS_COMPLETED));

        // When
        List<CustomerTransaction> results = transactionRepo.findByCustomerIdAndTransactionIdGreaterThan(CUSTOMER_1, "txn-001");

        // Then
        assertThat(results).hasSizeGreaterThanOrEqualTo(2);
        assertThat(results).allMatch(t -> t.getTransactionId().compareTo("txn-001") > 0);
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: Main Table - Hash + Range GreaterThanEqual")
    void testMainTable_HashAndRangeGreaterThanEqual() {
        // Given
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-001", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 50.0, STATUS_COMPLETED));
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-002", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 75.0, STATUS_COMPLETED));

        // When
        List<CustomerTransaction> results = transactionRepo.findByCustomerIdAndTransactionIdGreaterThanEqual(CUSTOMER_1, "txn-001");

        // Then
        assertThat(results).hasSizeGreaterThanOrEqualTo(2);
        assertThat(results).allMatch(t -> t.getTransactionId().compareTo("txn-001") >= 0);
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: Main Table - Hash + Range LessThan")
    void testMainTable_HashAndRangeLessThan() {
        // Given
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-001", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 50.0, STATUS_COMPLETED));
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-002", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 75.0, STATUS_COMPLETED));
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-003", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 100.0, STATUS_COMPLETED));

        // When
        List<CustomerTransaction> results = transactionRepo.findByCustomerIdAndTransactionIdLessThan(CUSTOMER_1, "txn-003");

        // Then
        assertThat(results).hasSizeGreaterThanOrEqualTo(2);
        assertThat(results).allMatch(t -> t.getTransactionId().compareTo("txn-003") < 0);
    }

    @Test
    @Order(6)
    @DisplayName("Test 6: Main Table - Hash + Range LessThanEqual")
    void testMainTable_HashAndRangeLessThanEqual() {
        // Given
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-001", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 50.0, STATUS_COMPLETED));
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-002", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 75.0, STATUS_COMPLETED));

        // When
        List<CustomerTransaction> results = transactionRepo.findByCustomerIdAndTransactionIdLessThanEqual(CUSTOMER_1, "txn-002");

        // Then
        assertThat(results).hasSizeGreaterThanOrEqualTo(2);
        assertThat(results).allMatch(t -> t.getTransactionId().compareTo("txn-002") <= 0);
    }

    @Test
    @Order(7)
    @DisplayName("Test 7: Main Table - Hash + Range Between")
    void testMainTable_HashAndRangeBetween() {
        // Given
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-001", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 50.0, STATUS_COMPLETED));
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-002", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 75.0, STATUS_COMPLETED));
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-003", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 100.0, STATUS_COMPLETED));

        // When
        List<CustomerTransaction> results = transactionRepo.findByCustomerIdAndTransactionIdBetween(CUSTOMER_1, "txn-001", "txn-002");

        // Then
        assertThat(results).hasSizeGreaterThanOrEqualTo(2);
        assertThat(results).allMatch(t ->
            t.getTransactionId().compareTo("txn-001") >= 0 &&
            t.getTransactionId().compareTo("txn-002") <= 0
        );
    }

    @Test
    @Order(8)
    @DisplayName("Test 8: Main Table - Hash + Range StartingWith")
    void testMainTable_HashAndRangeStartingWith() {
        // Given
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-001", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 50.0, STATUS_COMPLETED));
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-002", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 75.0, STATUS_COMPLETED));
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "payment-001", Instant.now(),
                MERCHANT_A, CATEGORY_FOOD, 100.0, STATUS_COMPLETED));

        // When
        List<CustomerTransaction> results = transactionRepo.findByCustomerIdAndTransactionIdStartingWith(CUSTOMER_1, "txn");

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(t -> t.getTransactionId().startsWith("txn"));
    }

    @Test
    @Order(9)
    @DisplayName("Test 9: Main Table - Hash + OrderBy Range Asc")
    void testMainTable_HashOrderByRangeAsc() {
        // Given
        Instant now = Instant.now();
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-003", now,
                MERCHANT_A, CATEGORY_FOOD, 100.0, STATUS_COMPLETED));
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-001", now,
                MERCHANT_A, CATEGORY_FOOD, 50.0, STATUS_COMPLETED));
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-002", now,
                MERCHANT_A, CATEGORY_FOOD, 75.0, STATUS_COMPLETED));

        // When
        List<CustomerTransaction> results = transactionRepo.findByCustomerIdOrderByTransactionIdAsc(CUSTOMER_1);

        // Then
        assertThat(results).hasSize(3);
        assertThat(results.get(0).getTransactionId()).isEqualTo("txn-001");
        assertThat(results.get(1).getTransactionId()).isEqualTo("txn-002");
        assertThat(results.get(2).getTransactionId()).isEqualTo("txn-003");
    }

    @Test
    @Order(10)
    @DisplayName("Test 10: Main Table - Hash + OrderBy Range Desc")
    void testMainTable_HashOrderByRangeDesc() {
        // Given
        Instant now = Instant.now();
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-001", now,
                MERCHANT_A, CATEGORY_FOOD, 50.0, STATUS_COMPLETED));
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-002", now,
                MERCHANT_A, CATEGORY_FOOD, 75.0, STATUS_COMPLETED));
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-003", now,
                MERCHANT_A, CATEGORY_FOOD, 100.0, STATUS_COMPLETED));

        // When
        List<CustomerTransaction> results = transactionRepo.findByCustomerIdOrderByTransactionIdDesc(CUSTOMER_1);

        // Then
        assertThat(results).hasSize(3);
        assertThat(results.get(0).getTransactionId()).isEqualTo("txn-003");
        assertThat(results.get(1).getTransactionId()).isEqualTo("txn-002");
        assertThat(results.get(2).getTransactionId()).isEqualTo("txn-001");
    }

    @Test
    @Order(11)
    @DisplayName("Test 12: Main Table - Hash + Range + OrderBy Range")
    void testMainTable_HashRangeOrderByRange() {
        // Given
        Instant now = Instant.now();
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-001", now,
                MERCHANT_A, CATEGORY_FOOD, 50.0, STATUS_COMPLETED));
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-002", now,
                MERCHANT_A, CATEGORY_FOOD, 75.0, STATUS_COMPLETED));
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-003", now,
                MERCHANT_A, CATEGORY_FOOD, 100.0, STATUS_COMPLETED));
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-004", now,
                MERCHANT_A, CATEGORY_FOOD, 125.0, STATUS_COMPLETED));

        // When
        List<CustomerTransaction> results = transactionRepo.findByCustomerIdAndTransactionIdGreaterThanOrderByTransactionIdAsc(CUSTOMER_1, "txn-001");

        // Then
        assertThat(results).hasSize(3);
        assertThat(results).allMatch(t -> t.getTransactionId().compareTo("txn-001") > 0);
        assertThat(results.get(0).getTransactionId()).isEqualTo("txn-002");
        assertThat(results.get(1).getTransactionId()).isEqualTo("txn-003");
        assertThat(results.get(2).getTransactionId()).isEqualTo("txn-004");
    }

    @Test
    @Order(13)
    @DisplayName("Edge Case Test 2: Main Table - BEGINS_WITH with empty string throws DynamoDB exception")
    void testMainTable_BeginsWithEmptyString() {
        // Given - Create transactions with different ID prefixes
        Instant now = Instant.now();
        transactionRepo.save(new CustomerTransaction(CUSTOMER_1, "txn-001", now,
                MERCHANT_A, CATEGORY_FOOD, 50.0, STATUS_COMPLETED));

        // When/Then - Query with empty string prefix (edge case)
        // DynamoDB does NOT allow empty strings in key attribute values or conditions
        // This is a DynamoDB constraint, not a library issue
        // The library correctly passes the empty string to the SDK, which properly rejects it
        assertThatThrownBy(() ->
            transactionRepo.findByCustomerIdAndTransactionIdStartingWith(CUSTOMER_1, ""))
            .isInstanceOf(software.amazon.awssdk.services.dynamodb.model.DynamoDbException.class)
            .hasMessageContaining("The AttributeValue for a key attribute cannot contain an empty string value");
    }

    // Due to file size, I'll create this in a modular approach.
    // Let me continue in the next message with LSI and GSI tests
}
