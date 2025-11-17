package org.socialsignin.spring.data.dynamodb.domain.sample;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

/**
 * Invalid test entity with duplicate GSI partition keys.
 * This should trigger validation error during table creation.
 */
@DynamoDbBean
public class InvalidEntityDuplicateGsiPartitionKey {

    private String id;
    private String status;
    private String category;

    public InvalidEntityDuplicateGsiPartitionKey() {
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    // INVALID: Two different attributes trying to be partition key for same index
    @DynamoDbSecondaryPartitionKey(indexNames = "duplicateIndex")
    @DynamoDbAttribute("status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    // INVALID: This is the second partition key for "duplicateIndex"
    @DynamoDbSecondaryPartitionKey(indexNames = "duplicateIndex")
    @DynamoDbAttribute("category")
    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
