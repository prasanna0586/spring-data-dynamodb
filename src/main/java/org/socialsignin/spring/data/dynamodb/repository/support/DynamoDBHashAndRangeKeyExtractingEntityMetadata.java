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

import java.util.Set;

/**
 * Obtains basic hash-and-range-key-related metadata about a DynamoDB entity.
 * @param <T> the entity type
 * @param <ID> the ID type
 * @author Prasanna Kumar Ramachandran
 */
public interface DynamoDBHashAndRangeKeyExtractingEntityMetadata<T, ID>
        extends DynamoDBHashKeyExtractingEntityMetadata<T> {

    <H> HashAndRangeKeyExtractor<ID, H> getHashAndRangeKeyExtractor(Class<ID> idClass);

    String getRangeKeyPropertyName();

    Set<String> getIndexRangeKeyPropertyNames();

    boolean isCompositeHashAndRangeKeyProperty(String propertyName);

    <H> T getHashKeyPropotypeEntityForHashKey(H hashKey);

}
