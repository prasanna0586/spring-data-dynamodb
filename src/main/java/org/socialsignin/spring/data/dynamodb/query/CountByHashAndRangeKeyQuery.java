/**
 * Copyright © 2018 spring-data-dynamodb (https://github.com/prasanna0586/spring-data-dynamodb)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.socialsignin.spring.data.dynamodb.query;

import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;
import org.springframework.lang.NonNull;

/**
 * Counts entities in DynamoDB matching hash and range key query criteria.
 * @param <T> the entity type
 * @author Prasanna Kumar Ramachandran
 */
public class CountByHashAndRangeKeyQuery<T> extends AbstractSingleEntityQuery<Long> implements Query<Long> {

    private final Object hashKey;
    private final Object rangeKey;
    private final Class<T> entityClass;

    /**
     * Constructs a new CountByHashAndRangeKeyQuery.
     * @param dynamoDBOperations the DynamoDB operations instance
     * @param clazz the entity class type to query
     * @param hashKey the hash key value to match
     * @param rangeKey the range key value to match
     */
    public CountByHashAndRangeKeyQuery(DynamoDBOperations dynamoDBOperations, Class<T> clazz, Object hashKey,
            Object rangeKey) {
        super(dynamoDBOperations, Long.class);
        this.hashKey = hashKey;
        this.rangeKey = rangeKey;
        this.entityClass = clazz;
    }

    @NonNull
    @Override
    public Long getSingleResult() {
        return dynamoDBOperations.load(entityClass, hashKey, rangeKey) == null ? 0L : 1L;
    }

}
