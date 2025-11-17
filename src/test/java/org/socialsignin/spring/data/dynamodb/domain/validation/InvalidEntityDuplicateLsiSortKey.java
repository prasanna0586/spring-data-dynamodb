package org.socialsignin.spring.data.dynamodb.domain.validation;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

/**
 * Invalid test entity with duplicate LSI sort keys.
 * This should trigger validation error during table creation.
 */
@DynamoDbBean
public class InvalidEntityDuplicateLsiSortKey {

    private String customerId;
    private String orderId;
    private String orderDate;
    private String createdDate;

    public InvalidEntityDuplicateLsiSortKey() {
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("customerId")
    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("orderId")
    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    // INVALID: Two different attributes trying to be sort key for same LSI
    @DynamoDbSecondarySortKey(indexNames = "customerId-date-index")
    @DynamoDbAttribute("orderDate")
    public String getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(String orderDate) {
        this.orderDate = orderDate;
    }

    // INVALID: This is the second sort key for "customerId-date-index" LSI
    @DynamoDbSecondarySortKey(indexNames = "customerId-date-index")
    @DynamoDbAttribute("createdDate")
    public String getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
    }
}
