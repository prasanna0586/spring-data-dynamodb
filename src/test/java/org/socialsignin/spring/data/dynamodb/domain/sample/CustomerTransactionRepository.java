package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.socialsignin.spring.data.dynamodb.repository.EnableScan;
import org.springframework.data.repository.CrudRepository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for testing Global Secondary Index (GSI) queries.
 *
 * IMPORTANT: @EnableScan is ONLY added to deleteAll() and findAll() for test cleanup.
 * Query methods do NOT have @EnableScan, ensuring they must use GSI correctly.
 * If a query method can't use GSI properly, it will fail rather than falling back
 * to a table scan, giving us confidence in our GSI implementation.
 */
public interface CustomerTransactionRepository extends CrudRepository<CustomerTransaction, CustomerTransactionId> {

    // Override deleteAll() to enable scan for test cleanup
    @Override
    @EnableScan
    void deleteAll();

    // Override findAll() to enable scan (used internally by deleteAll())
    @Override
    @EnableScan
    Iterable<CustomerTransaction> findAll();

    // ========== MAIN TABLE QUERIES (customerId = hash, transactionId = range) ==========

    // Test 1: Hash only
    List<CustomerTransaction> findByCustomerId(String customerId);

    // Test 2: Hash + Range equality
    List<CustomerTransaction> findByCustomerIdAndTransactionId(String customerId, String transactionId);

    // Test 3: Hash + Range >
    List<CustomerTransaction> findByCustomerIdAndTransactionIdGreaterThan(String customerId, String transactionId);

    // Test 4: Hash + Range >=
    List<CustomerTransaction> findByCustomerIdAndTransactionIdGreaterThanEqual(String customerId, String transactionId);

    // Test 5: Hash + Range <
    List<CustomerTransaction> findByCustomerIdAndTransactionIdLessThan(String customerId, String transactionId);

    // Test 6: Hash + Range <=
    List<CustomerTransaction> findByCustomerIdAndTransactionIdLessThanEqual(String customerId, String transactionId);

    // Test 7: Hash + Range Between
    List<CustomerTransaction> findByCustomerIdAndTransactionIdBetween(String customerId, String startId, String endId);

    // Test 8: Hash + Range BeginsWith
    List<CustomerTransaction> findByCustomerIdAndTransactionIdStartingWith(String customerId, String prefix);

    // Test 9: Hash + OrderBy Range Asc
    List<CustomerTransaction> findByCustomerIdOrderByTransactionIdAsc(String customerId);

    // Test 10: Hash + OrderBy Range Desc
    List<CustomerTransaction> findByCustomerIdOrderByTransactionIdDesc(String customerId);

    // Test 11: Hash + Range + OrderBy Range
    List<CustomerTransaction> findByCustomerIdAndTransactionIdGreaterThanOrderByTransactionIdAsc(String customerId, String transactionId);

    // ========== GSI QUERIES - merchantId-transactionDate-index (hash: merchantId, range: transactionDate) ==========

    // Test 21: GSI hash + range equality
    List<CustomerTransaction> findByMerchantIdAndTransactionDate(String merchantId, Instant transactionDate);

    // Test 22: GSI hash + range >
    List<CustomerTransaction> findByMerchantIdAndTransactionDateAfter(String merchantId, Instant transactionDate);

    // Test 23: GSI hash + range <
    List<CustomerTransaction> findByMerchantIdAndTransactionDateBefore(String merchantId, Instant transactionDate);

    // Test 24: GSI hash + range Between
    List<CustomerTransaction> findByMerchantIdAndTransactionDateBetween(String merchantId, Instant startDate, Instant endDate);

    // Test 25: GSI hash + range >= (GreaterThanEqual)
    List<CustomerTransaction> findByMerchantIdAndTransactionDateGreaterThanEqual(String merchantId, Instant transactionDate);

    // Test 26: GSI hash + range <= (LessThanEqual)
    List<CustomerTransaction> findByMerchantIdAndTransactionDateLessThanEqual(String merchantId, Instant transactionDate);

    // Test 27: GSI hash + OrderBy range Asc
    List<CustomerTransaction> findByMerchantIdOrderByTransactionDateAsc(String merchantId);

    // Test 28: GSI hash + OrderBy range Desc
    List<CustomerTransaction> findByMerchantIdOrderByTransactionDateDesc(String merchantId);

    // Test 29: GSI hash + range + OrderBy
    List<CustomerTransaction> findByMerchantIdAndTransactionDateAfterOrderByTransactionDateAsc(String merchantId, Instant transactionDate);

    // ========== GSI QUERIES - merchantId-index (hash only: merchantId) ==========

    // Test 20: GSI hash only
    List<CustomerTransaction> findByMerchantId(String merchantId);

    // ========== GSI QUERIES - category-amount-index (hash: category, range: amount) ==========

    // Test 28: GSI hash only
    List<CustomerTransaction> findByCategory(String category);

    // Test 29: GSI hash + range >
    List<CustomerTransaction> findByCategoryAndAmountGreaterThan(String category, Double amount);

    // Test 30: GSI hash + range <
    List<CustomerTransaction> findByCategoryAndAmountLessThan(String category, Double amount);

    // Test 31: GSI hash + range Between
    List<CustomerTransaction> findByCategoryAndAmountBetween(String category, Double minAmount, Double maxAmount);

    // Test 32: GSI hash + OrderBy range Asc
    List<CustomerTransaction> findByCategoryOrderByAmountAsc(String category);

    // Test 33: GSI hash + OrderBy range Desc
    List<CustomerTransaction> findByCategoryOrderByAmountDesc(String category);

    // ========== GSI QUERIES - category-status-index (hash: category, range: status) ==========

    // Test for BEGINS_WITH on String GSI range key
    List<CustomerTransaction> findByCategoryAndStatusStartingWith(String category, String statusPrefix);
}
