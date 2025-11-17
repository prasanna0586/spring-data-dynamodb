package org.socialsignin.spring.data.dynamodb.domain.sample;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

/**
 * Invalid test entity - EC-2.3: GSI with only sort key (no partition key).
 * This should trigger validation error.
 */
@DynamoDbBean
public class InvalidEntityGsiWithoutPartitionKey {

    private String id;
    private String status;

    public InvalidEntityGsiWithoutPartitionKey() {
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    // INVALID: GSI with only sort key, no partition key
    @DynamoDbSecondarySortKey(indexNames = "statusIndex")
    @DynamoDbAttribute("status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
