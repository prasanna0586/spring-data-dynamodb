package org.socialsignin.spring.data.dynamodb.domain.sample;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite ID class for ProductOrder entity (customerId + orderId).
 */
public class ProductOrderId implements Serializable {
    private static final long serialVersionUID = 1L;

    private String customerId;
    private String orderId;

    public ProductOrderId() {
    }

    public ProductOrderId(String customerId, String orderId) {
        this.customerId = customerId;
        this.orderId = orderId;
    }

    @DynamoDBHashKey
    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    @DynamoDBRangeKey
    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductOrderId that = (ProductOrderId) o;
        return Objects.equals(customerId, that.customerId) &&
                Objects.equals(orderId, that.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customerId, orderId);
    }

    @Override
    public String toString() {
        return "ProductOrderId{" +
                "customerId='" + customerId + '\'' +
                ", orderId='" + orderId + '\'' +
                '}';
    }
}
