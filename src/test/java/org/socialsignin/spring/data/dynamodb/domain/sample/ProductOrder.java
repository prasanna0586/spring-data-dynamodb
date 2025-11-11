package org.socialsignin.spring.data.dynamodb.domain.sample;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import org.springframework.data.annotation.Id;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain model for testing Local Secondary Indexes (LSI).
 * LSI must have the same hash key as the base table but different range keys.
 *
 * Table structure:
 * - Hash Key: customerId
 * - Range Key: orderId
 * - LSI 1: customerId + orderDate (range key)
 * - LSI 2: customerId + status (range key)
 * - LSI 3: customerId + totalAmount (range key)
 */
@DynamoDBTable(tableName = "ProductOrder")
public class ProductOrder {

    @Id
    @DynamoDBIgnore
    private ProductOrderId productOrderId;
    private Instant orderDate;
    private String status;
    private Double totalAmount;
    private String productName;
    private Integer quantity;

    public ProductOrder() {
    }

    public ProductOrder(String customerId, String orderId, Instant orderDate, String status,
                        Double totalAmount, String productName, Integer quantity) {
        this.productOrderId = new ProductOrderId(customerId, orderId);
        this.orderDate = orderDate;
        this.status = status;
        this.totalAmount = totalAmount;
        this.productName = productName;
        this.quantity = quantity;
    }

    public ProductOrderId getProductOrderId() {
        return productOrderId;
    }

    public void setProductOrderId(ProductOrderId productOrderId) {
        this.productOrderId = productOrderId;
    }

    @DynamoDBHashKey(attributeName = "customerId")
    public String getCustomerId() {
        return productOrderId != null ? productOrderId.getCustomerId() : null;
    }

    public void setCustomerId(String customerId) {
        if (productOrderId == null) {
            productOrderId = new ProductOrderId();
        }
        productOrderId.setCustomerId(customerId);
    }

    @DynamoDBRangeKey(attributeName = "orderId")
    public String getOrderId() {
        return productOrderId != null ? productOrderId.getOrderId() : null;
    }

    public void setOrderId(String orderId) {
        if (productOrderId == null) {
            productOrderId = new ProductOrderId();
        }
        productOrderId.setOrderId(orderId);
    }

    /**
     * LSI 1: Query orders by date for a customer
     */
    @DynamoDBIndexRangeKey(localSecondaryIndexName = "customerId-orderDate-index", attributeName = "orderDate")
    @DynamoDBTypeConverted(converter = InstantConverter.class)
    public Instant getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(Instant orderDate) {
        this.orderDate = orderDate;
    }

    /**
     * LSI 2: Query orders by status for a customer
     */
    @DynamoDBIndexRangeKey(localSecondaryIndexName = "customerId-status-index", attributeName = "status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * LSI 3: Query orders by total amount for a customer
     */
    @DynamoDBIndexRangeKey(localSecondaryIndexName = "customerId-totalAmount-index", attributeName = "totalAmount")
    public Double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Double totalAmount) {
        this.totalAmount = totalAmount;
    }

    @DynamoDBAttribute(attributeName = "productName")
    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    @DynamoDBAttribute(attributeName = "quantity")
    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductOrder order = (ProductOrder) o;
        return Objects.equals(productOrderId, order.productOrderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productOrderId);
    }

    @Override
    public String toString() {
        return "ProductOrder{" +
                "customerId='" + getCustomerId() + '\'' +
                ", orderId='" + getOrderId() + '\'' +
                ", orderDate=" + orderDate +
                ", status='" + status + '\'' +
                ", totalAmount=" + totalAmount +
                ", productName='" + productName + '\'' +
                ", quantity=" + quantity +
                '}';
    }
}
