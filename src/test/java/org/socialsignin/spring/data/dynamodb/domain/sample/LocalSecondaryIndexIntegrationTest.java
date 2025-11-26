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
 * Comprehensive integration tests for Local Secondary Indexes (LSI).
 *
 * LSI characteristics tested:
 * - Must share same hash key as base table
 * - Can have different range key
 * - Queried using customerId (hash key) + LSI range key
 * - Support different data types as range keys (Instant, String, Double)
 * - Support range queries (after, before, between, greater than, less than)
 *
 * Coverage:
 * - LSI with Instant range key (date queries)
 * - LSI with String range key (status queries)
 * - LSI with Double range key (amount queries)
 * - Range queries on LSI
 * - Multiple LSI on same table
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, LocalSecondaryIndexIntegrationTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Local Secondary Index (LSI) Integration Tests")
public class LocalSecondaryIndexIntegrationTest {

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    public static class TestAppConfig {
    }

    @Autowired
    private OrderRepository orderRepository;

    private static final String CUSTOMER_1 = "customer-001";
    private static final String CUSTOMER_2 = "customer-002";

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Test 1: Create and retrieve orders - basic hash + range key query")
    void testBasicHashRangeKeyQuery() {
        // Given
        ProductOrder order = new ProductOrder(CUSTOMER_1, "order-001", Instant.now(),
                "PENDING", 100.0, "Product A", 2);
        orderRepository.save(order);

        // When
        List<ProductOrder> orders = orderRepository.findByCustomerIdAndOrderId(CUSTOMER_1, "order-001");

        // Then
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getCustomerId()).isEqualTo(CUSTOMER_1);
        assertThat(orders.get(0).getOrderId()).isEqualTo("order-001");
        assertThat(orders.get(0).getProductName()).isEqualTo("Product A");
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Test 2: LSI with Instant range key - Query orders by date (ascending)")
    void testLSI_QueryOrdersByDate() {
        // Given - Create orders with different dates
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-001", twoDaysAgo,
                "COMPLETED", 100.0, "Product A", 2));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-002", yesterday,
                "PENDING", 200.0, "Product B", 1));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-003", now,
                "SHIPPED", 300.0, "Product C", 3));

        // When - Query using LSI: customerId-orderDate-index
        // Note: Spring Data DynamoDB requires a condition on the LSI range key to use the index
        // Using a very old date to effectively get all records, sorted by the LSI
        Instant veryOldDate = Instant.EPOCH;
        List<ProductOrder> orders = orderRepository.findByCustomerIdAndOrderDateAfter(CUSTOMER_1, veryOldDate);

        // Then - Should return in chronological order
        assertThat(orders).hasSize(3);
        assertThat(orders.get(0).getOrderId()).isEqualTo("order-001");
        assertThat(orders.get(1).getOrderId()).isEqualTo("order-002");
        assertThat(orders.get(2).getOrderId()).isEqualTo("order-003");
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Test 3: LSI date range - Query orders after specific date")
    void testLSI_QueryOrdersAfterDate() {
        // Given
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-001", twoDaysAgo,
                "COMPLETED", 100.0, "Product A", 2));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-002", yesterday,
                "PENDING", 200.0, "Product B", 1));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-003", now,
                "SHIPPED", 300.0, "Product C", 3));

        // When - Query orders after yesterday
        List<ProductOrder> orders = orderRepository.findByCustomerIdAndOrderDateAfter(CUSTOMER_1, yesterday);

        // Then - Should return only today's order
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getOrderId()).isEqualTo("order-003");
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Test 4: LSI date range - Query orders before specific date")
    void testLSI_QueryOrdersBeforeDate() {
        // Given
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-001", twoDaysAgo,
                "COMPLETED", 100.0, "Product A", 2));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-002", yesterday,
                "PENDING", 200.0, "Product B", 1));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-003", now,
                "SHIPPED", 300.0, "Product C", 3));

        // When - Query orders before yesterday
        List<ProductOrder> orders = orderRepository.findByCustomerIdAndOrderDateBefore(CUSTOMER_1, yesterday);

        // Then - Should return only two days ago order
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getOrderId()).isEqualTo("order-001");
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("Test 5: LSI date range - Query orders between dates")
    void testLSI_QueryOrdersBetweenDates() {
        // Given
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);
        Instant threeDaysAgo = now.minus(3, ChronoUnit.DAYS);

        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-001", threeDaysAgo,
                "COMPLETED", 100.0, "Product A", 2));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-002", twoDaysAgo,
                "PENDING", 200.0, "Product B", 1));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-003", yesterday,
                "SHIPPED", 300.0, "Product C", 3));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-004", now,
                "DELIVERED", 400.0, "Product D", 4));

        // When - Query orders between three days ago and yesterday
        List<ProductOrder> orders = orderRepository.findByCustomerIdAndOrderDateBetween(
                CUSTOMER_1, threeDaysAgo, yesterday);

        // Then - Should return orders from three days ago, two days ago, and yesterday
        assertThat(orders).hasSize(3);
        assertThat(orders).extracting(ProductOrder::getOrderId)
                .containsExactlyInAnyOrder("order-001", "order-002", "order-003");
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("Test 6: LSI with String range key - Query orders by status")
    void testLSI_QueryOrdersByStatus() {
        // Given - Create orders with different statuses
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-001", Instant.now(),
                "PENDING", 100.0, "Product A", 2));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-002", Instant.now(),
                "PENDING", 200.0, "Product B", 1));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-003", Instant.now(),
                "SHIPPED", 300.0, "Product C", 3));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-004", Instant.now(),
                "COMPLETED", 400.0, "Product D", 4));

        // When - Query using LSI: customerId-status-index
        List<ProductOrder> pendingOrders = orderRepository.findByCustomerIdAndStatus(CUSTOMER_1, "PENDING");
        List<ProductOrder> shippedOrders = orderRepository.findByCustomerIdAndStatus(CUSTOMER_1, "SHIPPED");

        // Then
        assertThat(pendingOrders).hasSize(2);
        assertThat(pendingOrders).extracting(ProductOrder::getOrderId)
                .containsExactlyInAnyOrder("order-001", "order-002");

        assertThat(shippedOrders).hasSize(1);
        assertThat(shippedOrders.get(0).getOrderId()).isEqualTo("order-003");
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("Test 7: LSI with Double range key - Query orders by total amount (greater than)")
    void testLSI_QueryOrdersByAmountGreaterThan() {
        // Given - Create orders with different amounts
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-001", Instant.now(),
                "COMPLETED", 100.0, "Product A", 2));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-002", Instant.now(),
                "COMPLETED", 250.0, "Product B", 1));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-003", Instant.now(),
                "COMPLETED", 500.0, "Product C", 3));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-004", Instant.now(),
                "COMPLETED", 1000.0, "Product D", 4));

        // When - Query using LSI: customerId-totalAmount-index (amount > 300)
        List<ProductOrder> orders = orderRepository.findByCustomerIdAndTotalAmountGreaterThan(CUSTOMER_1, 300.0);

        // Then - Should return orders with amount > 300
        assertThat(orders).hasSize(2);
        assertThat(orders).extracting(ProductOrder::getOrderId)
                .containsExactlyInAnyOrder("order-003", "order-004");
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("Test 8: LSI with Double range key - Query orders by total amount (less than)")
    void testLSI_QueryOrdersByAmountLessThan() {
        // Given
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-001", Instant.now(),
                "COMPLETED", 100.0, "Product A", 2));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-002", Instant.now(),
                "COMPLETED", 250.0, "Product B", 1));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-003", Instant.now(),
                "COMPLETED", 500.0, "Product C", 3));

        // When - Query orders with amount < 300
        List<ProductOrder> orders = orderRepository.findByCustomerIdAndTotalAmountLessThan(CUSTOMER_1, 300.0);

        // Then
        assertThat(orders).hasSize(2);
        assertThat(orders).extracting(ProductOrder::getOrderId)
                .containsExactlyInAnyOrder("order-001", "order-002");
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("Test 9: LSI with Double range key - Query orders by amount range")
    void testLSI_QueryOrdersByAmountBetween() {
        // Given
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-001", Instant.now(),
                "COMPLETED", 100.0, "Product A", 2));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-002", Instant.now(),
                "COMPLETED", 250.0, "Product B", 1));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-003", Instant.now(),
                "COMPLETED", 500.0, "Product C", 3));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-004", Instant.now(),
                "COMPLETED", 1000.0, "Product D", 4));

        // When - Query orders with amount between 200 and 600
        List<ProductOrder> orders = orderRepository.findByCustomerIdAndTotalAmountBetween(CUSTOMER_1, 200.0, 600.0);

        // Then
        assertThat(orders).hasSize(2);
        assertThat(orders).extracting(ProductOrder::getOrderId)
                .containsExactlyInAnyOrder("order-002", "order-003");
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("Test 10: LSI isolation - Different customers have separate order sets")
    void testLSI_CustomerIsolation() {
        // Given - Create orders for two different customers
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-001", Instant.now(),
                "PENDING", 100.0, "Product A", 2));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-002", Instant.now(),
                "PENDING", 200.0, "Product B", 1));
        orderRepository.save(new ProductOrder(CUSTOMER_2, "order-003", Instant.now(),
                "PENDING", 300.0, "Product C", 3));

        // When - Query orders for each customer
        List<ProductOrder> customer1Orders = orderRepository.findByCustomerIdAndStatus(CUSTOMER_1, "PENDING");
        List<ProductOrder> customer2Orders = orderRepository.findByCustomerIdAndStatus(CUSTOMER_2, "PENDING");

        // Then - Each customer should see only their own orders
        assertThat(customer1Orders).hasSize(2);
        assertThat(customer1Orders).extracting(ProductOrder::getOrderId)
                .containsExactlyInAnyOrder("order-001", "order-002");

        assertThat(customer2Orders).hasSize(1);
        assertThat(customer2Orders.get(0).getOrderId()).isEqualTo("order-003");
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    @DisplayName("Test 11: Multiple LSI on same table - Query using different indexes")
    void testMultipleLSI_DifferentIndexes() {
        // Given - Create a single order with multiple attributes
        Instant orderDate = Instant.now().minus(1, ChronoUnit.DAYS);
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-001", orderDate,
                "SHIPPED", 500.0, "Product A", 2));

        // When - Query using three different LSI
        List<ProductOrder> byDate = orderRepository.findByCustomerIdAndOrderDateAfter(
                CUSTOMER_1, orderDate.minus(1, ChronoUnit.HOURS));
        List<ProductOrder> byStatus = orderRepository.findByCustomerIdAndStatus(CUSTOMER_1, "SHIPPED");
        List<ProductOrder> byAmount = orderRepository.findByCustomerIdAndTotalAmountGreaterThan(CUSTOMER_1, 400.0);

        // Then - All three queries should return the same order
        assertThat(byDate).hasSize(1);
        assertThat(byStatus).hasSize(1);
        assertThat(byAmount).hasSize(1);

        assertThat(byDate.get(0).getOrderId()).isEqualTo("order-001");
        assertThat(byStatus.get(0).getOrderId()).isEqualTo("order-001");
        assertThat(byAmount.get(0).getOrderId()).isEqualTo("order-001");
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    @DisplayName("Test 12: LSI query returns no results when criteria not met")
    void testLSI_NoResultsWhenCriteriaNotMet() {
        // Given
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-001", Instant.now(),
                "PENDING", 100.0, "Product A", 2));

        // When - Query with non-existent values
        List<ProductOrder> byStatus = orderRepository.findByCustomerIdAndStatus(CUSTOMER_1, "NON_EXISTENT_STATUS");
        List<ProductOrder> byAmount = orderRepository.findByCustomerIdAndTotalAmountGreaterThan(CUSTOMER_1, 1000.0);

        // Then - Should return empty lists
        assertThat(byStatus).isEmpty();
        assertThat(byAmount).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.Order(13)
    @DisplayName("Test 13: LSI with Instant range key - Query orders by exact date (equality)")
    void testLSI_QueryOrdersByExactDate() {
        // Given
        Instant targetDate = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant otherDate = targetDate.plus(1, ChronoUnit.DAYS);

        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-001", targetDate,
                "COMPLETED", 100.0, "Product A", 2));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-002", otherDate,
                "COMPLETED", 200.0, "Product B", 1));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-003", targetDate,
                "PENDING", 300.0, "Product C", 3));

        // When - Query orders with exact date using equality
        List<ProductOrder> orders = orderRepository.findByCustomerIdAndOrderDate(CUSTOMER_1, targetDate);

        // Then - Should return only orders with exact matching date
        assertThat(orders).hasSize(2);
        assertThat(orders).extracting(ProductOrder::getOrderId)
                .containsExactlyInAnyOrder("order-001", "order-003");
    }

    @Test
    @org.junit.jupiter.api.Order(14)
    @DisplayName("Test 14: LSI date range - Query orders on or after specific date (GTE)")
    void testLSI_QueryOrdersOnOrAfterDate() {
        // Given
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-001", twoDaysAgo,
                "COMPLETED", 100.0, "Product A", 2));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-002", yesterday,
                "PENDING", 200.0, "Product B", 1));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-003", now,
                "SHIPPED", 300.0, "Product C", 3));

        // When - Query orders on or after yesterday (GTE)
        List<ProductOrder> orders = orderRepository.findByCustomerIdAndOrderDateGreaterThanEqual(CUSTOMER_1, yesterday);

        // Then - Should return yesterday's and today's orders (inclusive)
        assertThat(orders).hasSize(2);
        assertThat(orders).extracting(ProductOrder::getOrderId)
                .containsExactlyInAnyOrder("order-002", "order-003");
    }

    @Test
    @org.junit.jupiter.api.Order(15)
    @DisplayName("Test 15: LSI date range - Query orders on or before specific date (LTE)")
    void testLSI_QueryOrdersOnOrBeforeDate() {
        // Given
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-001", twoDaysAgo,
                "COMPLETED", 100.0, "Product A", 2));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-002", yesterday,
                "PENDING", 200.0, "Product B", 1));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-003", now,
                "SHIPPED", 300.0, "Product C", 3));

        // When - Query orders on or before yesterday (LTE)
        List<ProductOrder> orders = orderRepository.findByCustomerIdAndOrderDateLessThanEqual(CUSTOMER_1, yesterday);

        // Then - Should return two days ago and yesterday's orders (inclusive)
        assertThat(orders).hasSize(2);
        assertThat(orders).extracting(ProductOrder::getOrderId)
                .containsExactlyInAnyOrder("order-001", "order-002");
    }

    @Test
    @org.junit.jupiter.api.Order(16)
    @DisplayName("Test 16: LSI with String range key - Query orders by status prefix (StartingWith)")
    void testLSI_QueryOrdersByStatusPrefix() {
        // Given - Create orders with different statuses
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-001", Instant.now(),
                "PENDING", 100.0, "Product A", 2));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-002", Instant.now(),
                "PENDING_REVIEW", 200.0, "Product B", 1));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-003", Instant.now(),
                "SHIPPED", 300.0, "Product C", 3));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-004", Instant.now(),
                "COMPLETED", 400.0, "Product D", 4));

        // When - Query orders with status starting with "PEN" (begins_with operator)
        List<ProductOrder> orders = orderRepository.findByCustomerIdAndStatusStartingWith(CUSTOMER_1, "PEN");

        // Then - Should return orders with status PENDING and PENDING_REVIEW
        assertThat(orders).hasSize(2);
        assertThat(orders).extracting(ProductOrder::getOrderId)
                .containsExactlyInAnyOrder("order-001", "order-002");
    }

    @Test
    @org.junit.jupiter.api.Order(17)
    @DisplayName("Test 17: LSI with Double range key - Query orders by exact amount (equality)")
    void testLSI_QueryOrdersByExactAmount() {
        // Given
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-001", Instant.now(),
                "COMPLETED", 250.0, "Product A", 2));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-002", Instant.now(),
                "COMPLETED", 500.0, "Product B", 1));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-003", Instant.now(),
                "COMPLETED", 250.0, "Product C", 3));

        // When - Query orders with exact amount using equality
        List<ProductOrder> orders = orderRepository.findByCustomerIdAndTotalAmount(CUSTOMER_1, 250.0);

        // Then - Should return only orders with exact matching amount
        assertThat(orders).hasSize(2);
        assertThat(orders).extracting(ProductOrder::getOrderId)
                .containsExactlyInAnyOrder("order-001", "order-003");
    }

    @Test
    @org.junit.jupiter.api.Order(18)
    @DisplayName("Test 18: LSI with Double range key - Query orders with amount >= threshold (GTE)")
    void testLSI_QueryOrdersByAmountGreaterThanEqual() {
        // Given
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-001", Instant.now(),
                "COMPLETED", 100.0, "Product A", 2));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-002", Instant.now(),
                "COMPLETED", 300.0, "Product B", 1));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-003", Instant.now(),
                "COMPLETED", 500.0, "Product C", 3));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-004", Instant.now(),
                "COMPLETED", 300.0, "Product D", 4));

        // When - Query orders with amount >= 300 (GTE)
        List<ProductOrder> orders = orderRepository.findByCustomerIdAndTotalAmountGreaterThanEqual(CUSTOMER_1, 300.0);

        // Then - Should return orders with amount >= 300 (inclusive)
        assertThat(orders).hasSize(3);
        assertThat(orders).extracting(ProductOrder::getOrderId)
                .containsExactlyInAnyOrder("order-002", "order-003", "order-004");
    }

    @Test
    @org.junit.jupiter.api.Order(19)
    @DisplayName("Test 19: LSI with Double range key - Query orders with amount <= threshold (LTE)")
    void testLSI_QueryOrdersByAmountLessThanEqual() {
        // Given
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-001", Instant.now(),
                "COMPLETED", 100.0, "Product A", 2));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-002", Instant.now(),
                "COMPLETED", 250.0, "Product B", 1));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-003", Instant.now(),
                "COMPLETED", 500.0, "Product C", 3));

        // When - Query orders with amount <= 250 (LTE)
        List<ProductOrder> orders = orderRepository.findByCustomerIdAndTotalAmountLessThanEqual(CUSTOMER_1, 250.0);

        // Then - Should return orders with amount <= 250 (inclusive)
        assertThat(orders).hasSize(2);
        assertThat(orders).extracting(ProductOrder::getOrderId)
                .containsExactlyInAnyOrder("order-001", "order-002");
    }

    @Test
    @org.junit.jupiter.api.Order(20)
    @DisplayName("Test 20: LSI ordering - Orders returned in ascending order by date")
    void testLSI_OrderByDateAscending() {
        // Given - Create orders with different dates
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-002", yesterday,
                "PENDING", 200.0, "Product B", 1));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-003", now,
                "SHIPPED", 300.0, "Product C", 3));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-001", twoDaysAgo,
                "COMPLETED", 100.0, "Product A", 2));

        // When - Query with OrderBy ascending (tests hash-only query with sort)
        List<ProductOrder> orders = orderRepository.findByCustomerIdOrderByOrderDateAsc(CUSTOMER_1);

        // Then - Should return in chronological order (oldest to newest)
        assertThat(orders).hasSize(3);
        assertThat(orders.get(0).getOrderDate()).isEqualTo(twoDaysAgo);
        assertThat(orders.get(1).getOrderDate()).isEqualTo(yesterday);
        assertThat(orders.get(2).getOrderDate()).isEqualTo(now);
    }

    @Test
    @org.junit.jupiter.api.Order(21)
    @DisplayName("Test 21: LSI ordering - Orders returned in descending order by date")
    void testLSI_OrderByDateDescending() {
        // Given - Create orders with different dates
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);

        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-002", yesterday,
                "PENDING", 200.0, "Product B", 1));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-003", now,
                "SHIPPED", 300.0, "Product C", 3));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-001", twoDaysAgo,
                "COMPLETED", 100.0, "Product A", 2));

        // When - Query with OrderBy descending (tests hash-only query with reverse sort)
        List<ProductOrder> orders = orderRepository.findByCustomerIdOrderByOrderDateDesc(CUSTOMER_1);

        // Then - Should return in reverse chronological order (newest to oldest)
        assertThat(orders).hasSize(3);
        assertThat(orders.get(0).getOrderDate()).isEqualTo(now);
        assertThat(orders.get(1).getOrderDate()).isEqualTo(yesterday);
        assertThat(orders.get(2).getOrderDate()).isEqualTo(twoDaysAgo);
    }

    @Test
    @org.junit.jupiter.api.Order(22)
    @DisplayName("Test 22: LSI range query with ordering - Orders after date in ascending order")
    void testLSI_RangeQueryWithOrdering() {
        // Given
        Instant now = Instant.now();
        Instant yesterday = now.minus(1, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);
        Instant threeDaysAgo = now.minus(3, ChronoUnit.DAYS);

        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-001", threeDaysAgo,
                "COMPLETED", 100.0, "Product A", 2));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-002", twoDaysAgo,
                "PENDING", 200.0, "Product B", 1));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-003", yesterday,
                "SHIPPED", 300.0, "Product C", 3));
        orderRepository.save(new ProductOrder(CUSTOMER_1, "order-004", now,
                "DELIVERED", 400.0, "Product D", 4));

        // When - Query orders after two days ago with OrderBy (combined condition + sort)
        List<ProductOrder> orders = orderRepository.findByCustomerIdAndOrderDateAfterOrderByOrderDateAsc(
                CUSTOMER_1, twoDaysAgo);

        // Then - Should return orders after two days ago, in ascending order
        assertThat(orders).hasSize(2);
        assertThat(orders.get(0).getOrderDate()).isEqualTo(yesterday);
        assertThat(orders.get(1).getOrderDate()).isEqualTo(now);
        assertThat(orders).extracting(ProductOrder::getOrderId)
                .containsExactly("order-003", "order-004");
    }
}
