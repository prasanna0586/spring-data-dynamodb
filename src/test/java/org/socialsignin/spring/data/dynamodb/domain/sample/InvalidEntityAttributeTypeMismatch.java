package org.socialsignin.spring.data.dynamodb.domain.sample;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

/**
 * Invalid test entity - EC-5.3: Attribute type mismatch between method and field.
 * The same attribute name "createdAt" is defined with different types on two different methods.
 * This should throw an IllegalStateException during validation.
 */
@DynamoDbBean
public class InvalidEntityAttributeTypeMismatch {

    private String customerId;
    private String createdAt;
    private String status;

    public InvalidEntityAttributeTypeMismatch() {
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("customerId")
    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    // Define createdAt as String (S type) for table sort key
    @DynamoDbSortKey
    @DynamoDbAttribute("createdAt")
    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    @DynamoDbAttribute("status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    // INVALID: Try to use "createdAt" again but with a different type (Number/Long)
    // This creates a GSI partition key using the same attribute name with a different type
    @DynamoDbSecondaryPartitionKey(indexNames = "statusIndex")
    @DynamoDbAttribute("createdAt")  // Same attribute name but will be detected as different type
    public Long getCreatedAtAsNumber() {
        return createdAt != null ? Long.parseLong(createdAt) : null;
    }

    public void setCreatedAtAsNumber(Long value) {
        this.createdAt = value != null ? String.valueOf(value) : null;
    }
}
