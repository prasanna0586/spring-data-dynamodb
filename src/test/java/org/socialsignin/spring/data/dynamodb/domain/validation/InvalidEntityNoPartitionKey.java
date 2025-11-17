package org.socialsignin.spring.data.dynamodb.domain.validation;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

/**
 * Invalid test entity - EC-1.1: Missing table partition key.
 * This should trigger validation error.
 */
@DynamoDbBean
public class InvalidEntityNoPartitionKey {

    private String id;

    public InvalidEntityNoPartitionKey() {
    }

    // INVALID: No @DynamoDbPartitionKey annotation
    @DynamoDbAttribute("id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
