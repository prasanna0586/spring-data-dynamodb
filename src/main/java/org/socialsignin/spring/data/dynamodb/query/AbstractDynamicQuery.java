/*
 * Copyright © 2018 spring-data-dynamodb (https://github.com/prasanna0586/spring-data-dynamodb)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.socialsignin.spring.data.dynamodb.query;

import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;

/**
 * Abstract base class for dynamic query implementations.
 *
 * This class provides a common foundation for query operations that dynamically
 * construct and execute queries against DynamoDB. Subclasses should implement
 * specific query execution logic.
 * @param <T> the domain class type for this query
 */
public abstract class AbstractDynamicQuery<T> extends AbstractQuery<T> {

    /**
     * The DynamoDB operations instance used to perform database operations.
     */
    protected final DynamoDBOperations dynamoDBOperations;

    /**
     * The domain class type for entities returned by this query.
     */
    protected final Class<T> clazz;

    /**
     * Constructs an AbstractDynamicQuery with the specified DynamoDB operations and domain class.
     * @param dynamoDBOperations the DynamoDB operations instance
     * @param clazz the domain class type
     */
    public AbstractDynamicQuery(DynamoDBOperations dynamoDBOperations, Class<T> clazz) {
        this.dynamoDBOperations = dynamoDBOperations;
        this.clazz = clazz;
    }
}
