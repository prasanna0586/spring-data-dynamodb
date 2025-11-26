package org.socialsignin.spring.data.dynamodb.domain.validation;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

/**
 * Invalid test entity - EC-3.2: LSI on table without sort key.
 * This should trigger validation error.
 */
@DynamoDbBean
public class InvalidEntityLsiWithoutTableSortKey {

    private String id;
    private String createdAt;

    public InvalidEntityLsiWithoutTableSortKey() {
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    // INVALID: LSI defined but table has no sort key (only partition key)
    @DynamoDbSecondarySortKey(indexNames = "createdAtIndex")
    @DynamoDbAttribute("createdAt")
    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
