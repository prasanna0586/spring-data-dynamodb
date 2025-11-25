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

import org.springframework.data.repository.core.EntityInformation;
import org.springframework.lang.Nullable;

import java.util.Optional;

/**
 * Encapsulates minimal information needed to load DynamoDB entities. As a minimum, provides access to hash-key related
 * metadata. Implementing classes can elect to be either range-key aware or not.
 * <p>
 * If a subclass is not range-key aware it should return null from getRangeKey(ID id) method, and return false from
 * isRangeKeyAware and isCompositeHashAndRangeKeyProperty methods.
 * @param <T> the entity type
 * @param <ID> the ID type
 * @author Prasanna Kumar Ramachandran
 */
public interface DynamoDBEntityInformation<T, ID>
        extends EntityInformation<T, ID>, DynamoDBHashKeyExtractingEntityMetadata<T> {

    /**
     * Checks if this entity uses a range key in addition to a hash key.
     *
     * @return true if the entity has a range key, false otherwise
     */
    default boolean isRangeKeyAware() {
        return false;
    }

    /**
     * Checks if the given property is a composite hash and range key property.
     *
     * @param propertyName the property name to check
     * @return true if the property is a composite hash and range key, false otherwise
     */
    boolean isCompositeHashAndRangeKeyProperty(String propertyName);

    /**
     * Extracts the hash key value from the given ID.
     *
     * @param id the entity ID
     * @return the hash key value, or null if not available
     */
    @Nullable
    Object getHashKey(ID id);

    /**
     * Extracts the range key value from the given ID.
     *
     * @param id the entity ID
     * @return the range key value, or null if not available or not a range key aware entity
     */
    @Nullable
    default Object getRangeKey(ID id) {
        return null;
    }

    /**
     * Gets the projection expression for queries.
     *
     * @return an Optional containing the projection expression, or empty if no projection is defined
     */
    Optional<String> getProjection();

    /**
     * Gets the limit for queries.
     *
     * @return an Optional containing the query limit, or empty if no limit is defined
     */
    Optional<Integer> getLimit();
}
