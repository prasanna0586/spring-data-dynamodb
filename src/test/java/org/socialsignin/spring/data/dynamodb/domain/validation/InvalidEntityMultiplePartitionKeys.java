package org.socialsignin.spring.data.dynamodb.domain.validation;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * Invalid test entity - EC-1.2: Multiple table partition keys.
 * This should trigger validation error.
 */
@DynamoDbBean
public class InvalidEntityMultiplePartitionKeys {

    private String id;
    private String alternateId;

    public InvalidEntityMultiplePartitionKeys() {
    }

    // INVALID: First partition key
    @DynamoDbPartitionKey
    @DynamoDbAttribute("id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    // INVALID: Second partition key - can only have one
    @DynamoDbPartitionKey
    @DynamoDbAttribute("alternateId")
    public String getAlternateId() {
        return alternateId;
    }

    public void setAlternateId(String alternateId) {
        this.alternateId = alternateId;
    }
}
