package org.socialsignin.spring.data.dynamodb.domain.sample;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

/**
 * Invalid test entity with duplicate GSI sort keys.
 * This should trigger validation error during table creation.
 */
@DynamoDbBean
public class InvalidEntityDuplicateGsiSortKey {

    private String id;
    private String memberId;
    private String createdAt;
    private String updatedAt;

    public InvalidEntityDuplicateGsiSortKey() {
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "memberIndex")
    @DynamoDbAttribute("memberId")
    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    // INVALID: Two different attributes trying to be sort key for same index
    @DynamoDbSecondarySortKey(indexNames = "memberIndex")
    @DynamoDbAttribute("createdAt")
    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    // INVALID: This is the second sort key for "memberIndex"
    @DynamoDbSecondarySortKey(indexNames = "memberIndex")
    @DynamoDbAttribute("updatedAt")
    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
