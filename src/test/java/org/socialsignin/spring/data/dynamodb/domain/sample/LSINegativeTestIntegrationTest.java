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
 * NEGATIVE TESTING for Local Secondary Index (LSI) queries.
 *
 * This test suite validates that INVALID LSI operations are properly rejected.
 * These tests help identify bugs where:
 * 1. LSI index names are not being set correctly
 * 2. Sort validation is not enforcing LSI constraints
 * 3. Queries fall back to inefficient table scans instead of failing fast
 *
 * DynamoDB LSI Rules:
 * - When querying with an LSI condition, you MUST use that LSI's index
 * - You can ONLY sort by the LSI's range key (not main table range key)
 * - You CANNOT use multiple LSIs in a single query
 *
 * Expected Outcomes:
 * - If production code is correct: Tests throw UnsupportedOperationException
 * - If Bug #1 exists (no LSI index detection): Queries succeed but use table scan with filters
 * - If Bug #2 exists (incorrect sort validation): Queries succeed with wrong sort key
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, LSINegativeTestIntegrationTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("LSI Negative Testing - Invalid Operations Should Fail")
public class LSINegativeTestIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(LSINegativeTestIntegrationTest.class);

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    public static class TestAppConfig {
    }

    @Autowired
    private OrderRepository orderRepository;

    private static final String CUSTOMER_1 = "customer-001";
    private Instant now;
    private Instant yesterday;
    private Instant twoDaysAgo;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();

        // Setup test data
        now = Instant.now();
        yesterday = now.minus(1, ChronoUnit.DAYS);
        twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-001", twoDaysAgo,
                "PENDING", 100.0, "Product A", 2));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-002", yesterday,
                "SHIPPED", 200.0, "Product B", 1));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-003", now,
                "COMPLETED", 300.0, "Product C", 3));
    }

    @Test
    @Order(1)
    @DisplayName("NEGATIVE: Query by LSI (orderDate) but sort by main table range key (orderId) - Should fail")
    void testInvalidSort_LSICondition_MainTableRangeKeySort() {
        logger.info("\n=== TEST 1: Query by orderDate LSI, Sort by orderId (main range key) ===");
        logger.info("Expected: UnsupportedOperationException");

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class, () -> {
            // This query should fail because:
            // - Condition uses orderDate (LSI range key)
            // - Sort uses orderId (main table range key)
            // When using orderDate LSI, can ONLY sort by orderDate, not orderId
            orderRepository.findByCustomerIdAndOrderDateAfterOrderByOrderIdAsc(CUSTOMER_1, twoDaysAgo);
        });

        logger.info("CORRECTLY THREW UnsupportedOperationException");
        logger.info("   Message: {}", exception.getMessage());
        logger.info("   Production code correctly validates LSI sort constraints");
    }

    @Test
    @Order(2)
    @DisplayName("NEGATIVE: Query by LSI (status) but sort by main table range key (orderId) - Should fail")
    void testInvalidSort_StatusLSI_MainTableRangeKeySort() {
        logger.info("\n=== TEST 2: Query by status LSI, Sort by orderId (main range key) ===");
        logger.info("Expected: UnsupportedOperationException");

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class, () -> {
            orderRepository.findByCustomerIdAndStatusOrderByOrderIdDesc(CUSTOMER_1, "SHIPPED");
        });

        logger.info("CORRECTLY THREW UnsupportedOperationException: {}", exception.getMessage());
    }

    @Test
    @Order(3)
    @DisplayName("NEGATIVE: Query by one LSI (orderDate) but sort by different LSI (status) - Should fail")
    void testInvalidSort_OneLSI_DifferentLSISort() {
        logger.info("\n=== TEST 3: Query by orderDate LSI, Sort by status (different LSI) ===");
        logger.info("Expected: UnsupportedOperationException");
        logger.info("Reason: Cannot use two different LSIs in one query");

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class, () -> {
            orderRepository.findByCustomerIdAndOrderDateAfterOrderByStatusAsc(CUSTOMER_1, twoDaysAgo);
        });

        logger.info("CORRECTLY THREW UnsupportedOperationException: {}", exception.getMessage());
    }

    @Test
    @Order(4)
    @DisplayName("NEGATIVE: Query by one LSI (status) but sort by different LSI (totalAmount) - Should fail")
    void testInvalidSort_StatusLSI_TotalAmountLSISort() {
        logger.info("\n=== TEST 4: Query by status LSI, Sort by totalAmount (different LSI) ===");
        logger.info("Expected: UnsupportedOperationException");

        UnsupportedOperationException exception = assertThrows(UnsupportedOperationException.class, () -> {
            orderRepository.findByCustomerIdAndStatusOrderByTotalAmountDesc(CUSTOMER_1, "PENDING");
        });

        logger.info("CORRECTLY THREW UnsupportedOperationException: {}", exception.getMessage());
    }

    @Test
    @Order(5)
    @DisplayName("CONTROL: Valid query - LSI condition with same LSI sort - Should succeed")
    void testValidQuery_LSICondition_SameLSISort() {
        logger.info("\n=== CONTROL TEST: Valid query - orderDate LSI with orderDate sort ===");
        logger.info("Expected: SUCCESS (this is a valid operation)");

        // This should work - querying and sorting by the SAME LSI
        List<ProductOrder> orders = orderRepository.findByCustomerIdAndOrderDateAfterOrderByOrderDateAsc(
                CUSTOMER_1, twoDaysAgo);

        logger.info("QUERY SUCCEEDED (as expected for valid operation)");
        logger.info("   Returned {} orders, sorted by orderDate", orders.size());

        assertThat(orders).hasSize(2);
        assertThat(orders.get(0).getOrderDate()).isEqualTo(yesterday);
        assertThat(orders.get(1).getOrderDate()).isEqualTo(now);

        logger.info("   Results correctly sorted by orderDate: {}",
                orders.stream().map(o -> o.getOrderDate().toString()).toList());
        logger.info("   TEST COMPLETED: Valid query works correctly\n");
    }

    @AfterAll
    static void printSummary() {
        logger.info("\n" + "=".repeat(80));
        logger.info("TEST SUITE SUMMARY");
        logger.info("=".repeat(80));
        logger.info("This test suite validates LSI sort constraint enforcement.");
        logger.info("");
        logger.info("If all tests PASS (throw UnsupportedOperationException):");
        logger.info("  Production code correctly validates LSI constraints");
        logger.info("  No bugs exist");
        logger.info("");
        logger.info("If tests FAIL (queries succeed instead of throwing exceptions):");
        logger.info("  BUG #1: LSI index names not being detected/set");
        logger.info("  BUG #2: Sort validation not enforcing LSI constraints");
        logger.info("  Queries fall back to inefficient table scans with filters");
        logger.info("=".repeat(80) + "\n");
    }
}
