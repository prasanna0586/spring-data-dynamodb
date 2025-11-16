package org.socialsignin.spring.data.dynamodb.domain.sample;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

/**
 * Simple test entity for unit testing table synchronization.
 * Minimal structure with just an ID field to avoid Date converter dependencies.
 */
@DynamoDbBean
public class SimpleTestEntity {
    private String id;

    public SimpleTestEntity() {
    }

    public SimpleTestEntity(String id) {
        this.id = id;
    }

    @DynamoDbPartitionKey
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
