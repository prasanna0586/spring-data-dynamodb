package org.socialsignin.spring.data.dynamodb.domain.sample;

import com.amazonaws.services.dynamodbv2.datamodeling.*;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Domain model demonstrating nested documents in DynamoDB.
 * Contains nested Address and List<OrderItem>.
 */
@DynamoDBTable(tableName = "CustomerOrder")
public class CustomerOrder {

    private String orderId;
    private String customerId;
    private Instant orderDate;
    private Address shippingAddress;
    private Address billingAddress;
    private List<OrderItem> items;
    private Double totalAmount;
    private String status;

    public CustomerOrder() {
    }

    @DynamoDBHashKey(attributeName = "orderId")
    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    @DynamoDBAttribute(attributeName = "customerId")
    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    @DynamoDBAttribute(attributeName = "orderDate")
    @DynamoDBTypeConverted(converter = InstantConverter.class)
    public Instant getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(Instant orderDate) {
        this.orderDate = orderDate;
    }

    @DynamoDBAttribute(attributeName = "shippingAddress")
    public Address getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(Address shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    @DynamoDBAttribute(attributeName = "billingAddress")
    public Address getBillingAddress() {
        return billingAddress;
    }

    public void setBillingAddress(Address billingAddress) {
        this.billingAddress = billingAddress;
    }

    @DynamoDBAttribute(attributeName = "items")
    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    @DynamoDBAttribute(attributeName = "totalAmount")
    public Double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Double totalAmount) {
        this.totalAmount = totalAmount;
    }

    @DynamoDBAttribute(attributeName = "status")
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
        CustomerOrder that = (CustomerOrder) o;
        return Objects.equals(orderId, that.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId);
    }

    @Override
    public String toString() {
        return "CustomerOrder{" +
                "orderId='" + orderId + '\'' +
                ", customerId='" + customerId + '\'' +
                ", orderDate=" + orderDate +
                ", shippingAddress=" + shippingAddress +
                ", billingAddress=" + billingAddress +
                ", items=" + items +
                ", totalAmount=" + totalAmount +
                ", status='" + status + '\'' +
                '}';
    }
}
