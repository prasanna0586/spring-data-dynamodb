package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.springframework.data.annotation.Id;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain model for testing Global Secondary Indexes (GSI).
 * GSI can have different hash/range keys from the base table.
 *
 * Table structure:
 * - Hash Key: customerId
 * - Range Key: transactionId
 * - GSI 1: merchantId (hash) + transactionDate (range) - Find all transactions for a merchant by date
 * - GSI 2: category (hash) + amount (range) - Find all transactions in category by amount
 * - GSI 3: merchantId (hash only) - Find all transactions for a merchant
 */
@DynamoDbBean
public class CustomerTransaction {

    @Id
    private CustomerTransactionId customerTransactionId;
    private Instant transactionDate;
    private String merchantId;
    private String category;
    private Double amount;
    private String status;

    public CustomerTransaction() {
    }

    public CustomerTransaction(String customerId, String transactionId, Instant transactionDate,
                               String merchantId, String category, Double amount, String status) {
        this.customerTransactionId = new CustomerTransactionId(customerId, transactionId);
        this.transactionDate = transactionDate;
        this.merchantId = merchantId;
        this.category = category;
        this.amount = amount;
        this.status = status;
    }

    @DynamoDbIgnore
    public CustomerTransactionId getCustomerTransactionId() {
        return customerTransactionId;
    }

    public void setCustomerTransactionId(CustomerTransactionId customerTransactionId) {
        this.customerTransactionId = customerTransactionId;
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("customerId")
    public String getCustomerId() {
        return customerTransactionId != null ? customerTransactionId.getCustomerId() : null;
    }

    public void setCustomerId(String customerId) {
        if (customerTransactionId == null) {
            customerTransactionId = new CustomerTransactionId();
        }
        customerTransactionId.setCustomerId(customerId);
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("transactionId")
    public String getTransactionId() {
        return customerTransactionId != null ? customerTransactionId.getTransactionId() : null;
    }

    public void setTransactionId(String transactionId) {
        if (customerTransactionId == null) {
            customerTransactionId = new CustomerTransactionId();
        }
        customerTransactionId.setTransactionId(transactionId);
    }

    /**
     * GSI 1 & 3: merchantId is the hash key for two GSI
     */
    @DynamoDbSecondaryPartitionKey(indexNames = {"merchantId-transactionDate-index", "merchantId-index"})
    @DynamoDbAttribute("merchantId")
    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    /**
     * GSI 1: transactionDate is the range key for merchantId-transactionDate-index
     */
    @DynamoDbSecondarySortKey(indexNames = "merchantId-transactionDate-index")
    @DynamoDbAttribute("transactionDate")
    @DynamoDbConvertedBy(InstantConverter.class)
    public Instant getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(Instant transactionDate) {
        this.transactionDate = transactionDate;
    }

    /**
     * GSI 2 & 4: category is the hash key for two GSI
     */
    @DynamoDbSecondaryPartitionKey(indexNames = {"category-amount-index", "category-status-index"})
    @DynamoDbAttribute("category")
    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    /**
     * GSI 2: amount is the range key for category-amount-index
     */
    @DynamoDbSecondarySortKey(indexNames = "category-amount-index")
    @DynamoDbAttribute("amount")
    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    /**
     * GSI 4: status is the range key for category-status-index (for testing BEGINS_WITH on String range key)
     */
    @DynamoDbSecondarySortKey(indexNames = "category-status-index")
    @DynamoDbAttribute("status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomerTransaction that = (CustomerTransaction) o;
        return Objects.equals(customerTransactionId, that.customerTransactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customerTransactionId);
    }
}
