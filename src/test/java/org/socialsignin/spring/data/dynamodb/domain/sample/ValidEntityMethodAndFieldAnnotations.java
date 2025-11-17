package org.socialsignin.spring.data.dynamodb.domain.sample;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

/**
 * Valid test entity with same attribute annotated on both method and field.
 * This is a valid configuration and should NOT trigger validation errors.
 */
@DynamoDbBean
public class ValidEntityMethodAndFieldAnnotations {

    private String status;  // No field annotation - annotations only on methods

    private String id;

    public ValidEntityMethodAndFieldAnnotations() {
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    // Same attribute with same annotation on BOTH field and method - should be OK
    @DynamoDbSecondaryPartitionKey(indexNames = "statusIndex")
    @DynamoDbAttribute("status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
