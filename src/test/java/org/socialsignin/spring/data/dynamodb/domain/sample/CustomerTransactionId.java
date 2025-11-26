package org.socialsignin.spring.data.dynamodb.domain.sample;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.io.Serializable;
import java.util.Objects;

public class CustomerTransactionId implements Serializable {

    private static final long serialVersionUID = 1L;

    private String customerId;
    private String transactionId;

    public CustomerTransactionId() {
    }

    public CustomerTransactionId(String customerId, String transactionId) {
        this.customerId = customerId;
        this.transactionId = transactionId;
    }

    @DynamoDbPartitionKey
    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    @DynamoDbSortKey
    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomerTransactionId that = (CustomerTransactionId) o;
        return Objects.equals(customerId, that.customerId) &&
                Objects.equals(transactionId, that.transactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customerId, transactionId);
    }
}
