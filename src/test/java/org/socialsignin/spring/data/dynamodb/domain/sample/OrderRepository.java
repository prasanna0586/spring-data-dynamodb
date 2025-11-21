package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.socialsignin.spring.data.dynamodb.repository.EnableScan;
import org.socialsignin.spring.data.dynamodb.repository.EnableScanCount;
import org.springframework.data.repository.CrudRepository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for testing Local Secondary Indexes (LSI) queries.
 *
 * IMPORTANT: @EnableScan is ONLY added to deleteAll() and findAll() for test cleanup.
 * Query methods do NOT have @EnableScan, ensuring they must use LSI correctly.
 * If a query method can't use LSI properly, it will fail rather than falling back
 * to a table scan, giving us confidence in our LSI implementation.
 */
public interface OrderRepository extends CrudRepository<ProductOrder, ProductOrderId> {

    // Override deleteAll() to enable scan for test cleanup
    @Override
    @EnableScan
    void deleteAll();

    // Override findAll() to enable scan (used internally by deleteAll())
    @Override
    @EnableScan
    Iterable<ProductOrder> findAll();

    // Override count() to enable scan count
    @Override
    @EnableScan
    long count();

    // ========== LSI QUERIES - customerId-orderDate-index (table hash: customerId, LSI range: orderDate) ==========

    // Test 13: Table hash + LSI range equality
    List<ProductOrder> findByCustomerIdAndOrderDate(String customerId, Instant orderDate);

    // Test 14: Table hash + LSI range >
    List<ProductOrder> findByCustomerIdAndOrderDateAfter(String customerId, Instant orderDate);

    // Test 15: Table hash + LSI range <
    List<ProductOrder> findByCustomerIdAndOrderDateBefore(String customerId, Instant orderDate);

    // Test 16: Table hash + LSI range Between
    List<ProductOrder> findByCustomerIdAndOrderDateBetween(String customerId, Instant startDate, Instant endDate);

    // Test 17: Table hash + OrderBy LSI range Asc
    List<ProductOrder> findByCustomerIdOrderByOrderDateAsc(String customerId);

    // Test 18: Table hash + OrderBy LSI range Desc
    List<ProductOrder> findByCustomerIdOrderByOrderDateDesc(String customerId);

    // Test 19: Table hash + LSI range + OrderBy
    List<ProductOrder> findByCustomerIdAndOrderDateAfterOrderByOrderDateAsc(String customerId, Instant orderDate);

    // Test 20: Table hash + LSI range >= (GreaterThanEqual)
    List<ProductOrder> findByCustomerIdAndOrderDateGreaterThanEqual(String customerId, Instant orderDate);

    // Test 21: Table hash + LSI range <= (LessThanEqual)
    List<ProductOrder> findByCustomerIdAndOrderDateLessThanEqual(String customerId, Instant orderDate);

    // ========== Additional LSI queries for status and totalAmount indexes ==========

    // LSI 2: customerId-status-index
    List<ProductOrder> findByCustomerIdAndStatus(String customerId, String status);

    // LSI 2: customerId-status-index with StartingWith (begins_with)
    List<ProductOrder> findByCustomerIdAndStatusStartingWith(String customerId, String statusPrefix);

    // LSI 3: customerId-totalAmount-index
    List<ProductOrder> findByCustomerIdAndTotalAmountGreaterThan(String customerId, Double totalAmount);
    List<ProductOrder> findByCustomerIdAndTotalAmountLessThan(String customerId, Double totalAmount);
    List<ProductOrder> findByCustomerIdAndTotalAmountBetween(String customerId, Double minAmount, Double maxAmount);

    // LSI 3: Additional operators for comprehensive coverage
    List<ProductOrder> findByCustomerIdAndTotalAmount(String customerId, Double totalAmount);
    List<ProductOrder> findByCustomerIdAndTotalAmountGreaterThanEqual(String customerId, Double totalAmount);
    List<ProductOrder> findByCustomerIdAndTotalAmountLessThanEqual(String customerId, Double totalAmount);

    // ========== Main table query for comparison ==========

    // Base table query (customerId = hash, orderId = range)
    List<ProductOrder> findByCustomerIdAndOrderId(String customerId, String orderId);

    // ========== NEGATIVE TEST METHODS - These should fail or be invalid ==========
    // These methods test scenarios that SHOULD throw exceptions or behave incorrectly
    // if there are bugs in LSI handling

    // Test: Query by LSI range key (orderDate) but sort by MAIN table range key (orderId)
    // Expected: Should throw UnsupportedOperationException (can't sort by orderId when using orderDate LSI)
    // Bug behavior: Might allow it by querying base table with orderDate as filter
    List<ProductOrder> findByCustomerIdAndOrderDateAfterOrderByOrderIdAsc(String customerId, Instant orderDate);

    // Test: Query by LSI range key (status) but sort by MAIN table range key (orderId)
    // Expected: Should throw UnsupportedOperationException (can't sort by orderId when using status LSI)
    // Bug behavior: Might allow it by querying base table with status as filter
    List<ProductOrder> findByCustomerIdAndStatusOrderByOrderIdDesc(String customerId, String status);

    // Test: Query by one LSI (orderDate) but sort by DIFFERENT LSI range key (status)
    // Expected: Should throw UnsupportedOperationException (can't use two different LSIs)
    // Bug behavior: Might allow it incorrectly
    List<ProductOrder> findByCustomerIdAndOrderDateAfterOrderByStatusAsc(String customerId, Instant orderDate);

    // Test: Query by one LSI (status) but sort by DIFFERENT LSI range key (totalAmount)
    // Expected: Should throw UnsupportedOperationException (can't use two different LSIs)
    // Bug behavior: Might allow it incorrectly
    List<ProductOrder> findByCustomerIdAndStatusOrderByTotalAmountDesc(String customerId, String status);
}
