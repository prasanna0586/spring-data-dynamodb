/*
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
package org.socialsignin.spring.data.dynamodb.repository.support;

import java.util.Set;

/**
 * Obtains basic hash-and-range-key-related metadata about a DynamoDB entity.
 * @param <T> the entity type
 * @param <ID> the ID type
 * @author Prasanna Kumar Ramachandran
 */
public interface DynamoDBHashAndRangeKeyExtractingEntityMetadata<T, ID>
        extends DynamoDBHashKeyExtractingEntityMetadata<T> {

    /**
     * Gets a hash and range key extractor for the specified ID class.
     *
     * @param <H> the hash key type
     * @param idClass the ID class
     * @return a hash and range key extractor
     */
    <H> HashAndRangeKeyExtractor<ID, H> getHashAndRangeKeyExtractor(Class<ID> idClass);

    /**
     * Gets the name of the range key property.
     *
     * @return the range key property name
     */
    String getRangeKeyPropertyName();

    /**
     * Gets the names of all index range key properties (local secondary index range keys).
     *
     * @return a set of index range key property names
     */
    Set<String> getIndexRangeKeyPropertyNames();

    /**
     * Checks if the given property is a composite hash and range key property.
     *
     * @param propertyName the property name to check
     * @return true if the property is a composite hash and range key, false otherwise
     */
    boolean isCompositeHashAndRangeKeyProperty(String propertyName);

    /**
     * Creates a prototype entity with only the hash key set.
     *
     * @param <H> the hash key type
     * @param hashKey the hash key value
     * @return a prototype entity with the hash key set
     */
    <H> T getHashKeyPropotypeEntityForHashKey(H hashKey);

}
