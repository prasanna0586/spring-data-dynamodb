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
 * Counts entities in DynamoDB matching a hash key query.
 * @param <T> the entity type
 * @author Prasanna Kumar Ramachandran
 */
public class CountByHashKeyQuery<T> extends AbstractSingleEntityQuery<Long> implements Query<Long> {

    private final Object hashKey;
    private final Class<T> entityClass;

    /**
     * Constructs a new CountByHashKeyQuery.
     * @param dynamoDBOperations the DynamoDB operations instance
     * @param clazz the entity class type to query
     * @param hashKey the hash key value to match
     */
    public CountByHashKeyQuery(DynamoDBOperations dynamoDBOperations, Class<T> clazz, Object hashKey) {
        super(dynamoDBOperations, Long.class);
        this.hashKey = hashKey;
        this.entityClass = clazz;
    }

    @NonNull
    @Override
    public Long getSingleResult() {
        return dynamoDBOperations.load(entityClass, hashKey) == null ? 0L : 1L;
    }

}
