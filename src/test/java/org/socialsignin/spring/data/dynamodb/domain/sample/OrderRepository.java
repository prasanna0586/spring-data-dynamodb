package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.socialsignin.spring.data.dynamodb.repository.EnableScan;
import org.springframework.data.repository.CrudRepository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for testing Local Secondary Indexes (LSI) queries.
 */
@EnableScan
public interface OrderRepository extends CrudRepository<ProductOrder, ProductOrderId> {

    /**
     * Query using base table - by customerId and orderId (hash + range key).
     */
    List<ProductOrder> findByCustomerIdAndOrderId(String customerId, String orderId);

    /**
     * Query using LSI 1: customerId-orderDate-index
     * Finds orders for a customer after a specific date.
     * Note: To get all orders sorted by date, use Instant.EPOCH as the date parameter.
     */
    List<ProductOrder> findByCustomerIdAndOrderDateAfter(String customerId, Instant orderDate);

    /**
     * Query using LSI 1: customerId-orderDate-index
     * Finds orders for a customer before a specific date.
     */
    List<ProductOrder> findByCustomerIdAndOrderDateBefore(String customerId, Instant orderDate);

    /**
     * Query using LSI 1: customerId-orderDate-index
     * Finds orders for a customer within a date range.
     */
    List<ProductOrder> findByCustomerIdAndOrderDateBetween(String customerId, Instant startDate, Instant endDate);

    /**
     * Query using LSI 2: customerId-status-index
     * Finds all orders for a customer with specific status.
     */
    List<ProductOrder> findByCustomerIdAndStatus(String customerId, String status);

    /**
     * Query using LSI 3: customerId-totalAmount-index
     * Finds orders for a customer with amount greater than specified.
     */
    List<ProductOrder> findByCustomerIdAndTotalAmountGreaterThan(String customerId, Double totalAmount);

    /**
     * Query using LSI 3: customerId-totalAmount-index
     * Finds orders for a customer with amount less than specified.
     */
    List<ProductOrder> findByCustomerIdAndTotalAmountLessThan(String customerId, Double totalAmount);

    /**
     * Query using LSI 3: customerId-totalAmount-index
     * Finds orders for a customer within an amount range.
     */
    List<ProductOrder> findByCustomerIdAndTotalAmountBetween(String customerId, Double minAmount, Double maxAmount);
}
