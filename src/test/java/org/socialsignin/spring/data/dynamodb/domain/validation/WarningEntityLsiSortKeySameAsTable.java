package org.socialsignin.spring.data.dynamodb.domain.validation;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

/**
 * Valid but questionable test entity - EC-3.3: LSI sort key same as table sort key.
 * DynamoDB permits this, but it's redundant (LSI would be identical to table).
 * Should log a WARNING but not throw an exception.
 */
@DynamoDbBean
public class WarningEntityLsiSortKeySameAsTable {

    private String customerId;
    private String orderId;

    public WarningEntityLsiSortKeySameAsTable() {
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

    // QUESTIONABLE but VALID: LSI uses same sort key as table (redundant)
    @DynamoDbSecondarySortKey(indexNames = "redundantIndex")
    @DynamoDbAttribute("orderId")  // Same as table sort key
    public String getOrderIdForIndex() {
        return orderId;
    }

    public void setOrderIdForIndex(String orderId) {
        this.orderId = orderId;
    }
}
