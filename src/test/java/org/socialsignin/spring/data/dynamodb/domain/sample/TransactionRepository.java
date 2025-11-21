package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.socialsignin.spring.data.dynamodb.repository.EnableScan;
import org.springframework.data.repository.CrudRepository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for testing Global Secondary Index (GSI) queries.
 */
public interface TransactionRepository extends CrudRepository<CustomerTransaction, CustomerTransactionId> {

    // ===========================================================================
    // NEGATIVE TEST METHODS - Should throw UnsupportedOperationException
    // ===========================================================================

    /**
     * NEGATIVE: Query by merchantId (GSI hash key), sort by transactionId (main table range key).
     * Expected: UnsupportedOperationException
     * Reason: Cannot sort by main table range key when querying a GSI
     */
    List<CustomerTransaction> findByMerchantIdOrderByTransactionIdAsc(String merchantId);

    /**
     * NEGATIVE: Query by merchantId (GSI 1 hash), sort by amount (GSI 2 range key).
     * Expected: UnsupportedOperationException
     * Reason: Cannot sort by a different GSI's range key
     */
    List<CustomerTransaction> findByMerchantIdOrderByAmountAsc(String merchantId);

    /**
     * NEGATIVE: Query by category (GSI 2 hash), sort by transactionId (main table range key).
     * Expected: UnsupportedOperationException
     * Reason: Cannot sort by main table range key when querying a GSI
     */
    List<CustomerTransaction> findByCategoryOrderByTransactionIdDesc(String category);

    /**
     * NEGATIVE: Query by category (GSI 2 hash), sort by transactionDate (GSI 1 range key).
     * Expected: UnsupportedOperationException
     * Reason: Cannot sort by a different GSI's range key
     */
    List<CustomerTransaction> findByCategoryOrderByTransactionDateAsc(String category);

    // ===========================================================================
    // POSITIVE TEST METHODS - Should succeed
    // ===========================================================================

    /**
     * POSITIVE: Query by merchantId (GSI 1 hash), sort by transactionDate (GSI 1 range key).
     * Expected: SUCCESS
     * Reason: Sorting by the same GSI's range key
     */
    List<CustomerTransaction> findByMerchantIdOrderByTransactionDateAsc(String merchantId);

    /**
     * POSITIVE: Query by merchantId (GSI 1 hash) with range condition, sort by transactionDate.
     * Expected: SUCCESS
     * Reason: Querying and sorting by the same GSI
     */
    List<CustomerTransaction> findByMerchantIdAndTransactionDateAfterOrderByTransactionDateAsc(
            String merchantId, Instant transactionDate);

    /**
     * POSITIVE: Query by category (GSI 2 hash), sort by amount (GSI 2 range key).
     * Expected: SUCCESS
     * Reason: Sorting by the same GSI's range key
     */
    List<CustomerTransaction> findByCategoryOrderByAmountAsc(String category);

    /**
     * POSITIVE: Query by category (GSI 2 hash) with range condition, sort by amount.
     * Expected: SUCCESS
     * Reason: Querying and sorting by the same GSI
     */
    List<CustomerTransaction> findByCategoryAndAmountGreaterThanOrderByAmountDesc(
            String category, Double amount);

    /**
     * POSITIVE: Query by merchantId (GSI 3 - hash only index), no sort.
     * Expected: SUCCESS
     * Reason: Valid GSI query without sorting
     */
    List<CustomerTransaction> findByMerchantId(String merchantId);

    // ===========================================================================
    // Additional methods for data setup
    // ===========================================================================

    @Override
    @EnableScan
    void deleteAll();

    @Override
    @EnableScan
    Iterable<CustomerTransaction> findAll();
}
