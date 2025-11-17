package org.socialsignin.spring.data.dynamodb.domain.validation;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

/**
 * Invalid test entity - EC-1.3: Multiple table sort keys.
 * This should trigger validation error.
 */
@DynamoDbBean
public class InvalidEntityMultipleSortKeys {

    private String id;
    private String sortKey1;
    private String sortKey2;

    public InvalidEntityMultipleSortKeys() {
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    // INVALID: First sort key
    @DynamoDbSortKey
    @DynamoDbAttribute("sortKey1")
    public String getSortKey1() {
        return sortKey1;
    }

    public void setSortKey1(String sortKey1) {
        this.sortKey1 = sortKey1;
    }

    // INVALID: Second sort key - can only have one
    @DynamoDbSortKey
    @DynamoDbAttribute("sortKey2")
    public String getSortKey2() {
        return sortKey2;
    }

    public void setSortKey2(String sortKey2) {
        this.sortKey2 = sortKey2;
    }
}
