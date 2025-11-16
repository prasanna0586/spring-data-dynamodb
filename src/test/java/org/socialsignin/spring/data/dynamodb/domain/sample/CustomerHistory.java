/**
 * Copyright © 2018 spring-data-dynamodb (https://github.com/prasanna0586/spring-data-dynamodb)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.springframework.data.annotation.Id;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * Entity demonstrating composite primary key (hash + range) with a Global Secondary Index.
 *
 * Primary Key: customerId (partition key) + createDt (sort key)
 * GSI: idx_global_tag with tag as partition key
 */
@DynamoDbBean
public class CustomerHistory {
    @Id
    private CustomerHistoryId id;

    private String tag;

    /**
     * GSI partition key for idx_global_tag index.
     * SDK v2 uses @DynamoDbSecondaryPartitionKey to define GSI partition keys.
     */
    @DynamoDbSecondaryPartitionKey(indexNames = "idx_global_tag")
    @DynamoDbAttribute("tag")
    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    /**
     * Partition key (hash key) for the primary key.
     * Returns the customerId from the composite key object.
     */
    @DynamoDbPartitionKey
    @DynamoDbAttribute("customerId")
    public String getId() {
        return id != null ? id.getCustomerId() : null;
    }

    public void setId(String customerId) {
        if (this.id == null) {
            this.id = new CustomerHistoryId();
        }
        this.id.setCustomerId(customerId);
    }

    /**
     * Sort key (range key) for the primary key.
     * Returns the createDt from the composite key object.
     */
    @DynamoDbSortKey
    @DynamoDbAttribute("createDt")
    public String getCreateDt() {
        return id != null ? id.getCreateDt() : null;
    }

    public void setCreateDt(String createDt) {
        if (this.id == null) {
            this.id = new CustomerHistoryId();
        }

        this.id.setCreateDt(createDt);
    }
}
