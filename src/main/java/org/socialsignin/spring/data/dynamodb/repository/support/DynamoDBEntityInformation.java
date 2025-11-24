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

    default boolean isRangeKeyAware() {
        return false;
    }

    boolean isCompositeHashAndRangeKeyProperty(String propertyName);

    @Nullable
    Object getHashKey(ID id);

    @Nullable
    default Object getRangeKey(ID id) {
        return null;
    }

    Optional<String> getProjection();

    Optional<Integer> getLimit();
}
