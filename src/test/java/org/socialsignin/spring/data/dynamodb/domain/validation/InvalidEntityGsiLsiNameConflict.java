package org.socialsignin.spring.data.dynamodb.domain.validation;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

/**
 * Invalid test entity - EC-4.1: GSI and LSI with same name.
 * This should trigger validation error because we have:
 * - A GSI named "conflictIndex"
 * - An LSI also named "conflictIndex" (different index, same name)
 */
@DynamoDbBean
public class InvalidEntityGsiLsiNameConflict {

    private String id;
    private String sortKey;
    private String gsiAttribute;
    private String lsiAttribute;

    public InvalidEntityGsiLsiNameConflict() {
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("sortKey")
    public String getSortKey() {
        return sortKey;
    }

    public void setSortKey(String sortKey) {
        this.sortKey = sortKey;
    }

    // GSI named "conflictIndex" - has partition key = gsiAttribute
    @DynamoDbSecondaryPartitionKey(indexNames = "conflictIndex")
    @DynamoDbAttribute("gsiAttribute")
    public String getGsiAttribute() {
        return gsiAttribute;
    }

    public void setGsiAttribute(String gsiAttribute) {
        this.gsiAttribute = gsiAttribute;
    }

    // INVALID: LSI also named "conflictIndex" but with different attribute
    // This creates a separate LOCAL secondary index with the same name as the GSI above
    // LSI will use table's partition key (id) + lsiAttribute as sort key
    @DynamoDbSecondarySortKey(indexNames = "conflictIndex")
    @DynamoDbAttribute("lsiAttribute")
    public String getLsiAttribute() {
        return lsiAttribute;
    }

    public void setLsiAttribute(String lsiAttribute) {
        this.lsiAttribute = lsiAttribute;
    }
}
