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

import org.springframework.data.repository.core.EntityMetadata;
import org.springframework.lang.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;

import java.util.Map;
import java.util.Optional;

/**
 * Obtains basic hash key-related metadata about a DynamoDBEntity, such as whether properties have overridden attribute
 * names or have custom attribute converters assigned, whether a property is a hash key property or a composite id
 * property, and generates a hash key prototype entity given a hash key.
 * @param <T> the entity type
 * @author Prasanna Kumar Ramachandran
 */
public interface DynamoDBHashKeyExtractingEntityMetadata<T> extends EntityMetadata<T> {

    Optional<String> getOverriddenAttributeName(String propertyName);

    @Nullable
    AttributeConverter<?> getAttributeConverterForProperty(String propertyName);

    boolean isHashKeyProperty(String propertyName);

    @Nullable
    String getHashKeyPropertyName();

    String getDynamoDBTableName();

    Map<String, String[]> getGlobalSecondaryIndexNamesByPropertyName();

    boolean isGlobalIndexHashKeyProperty(String propertyName);

    boolean isGlobalIndexRangeKeyProperty(String propertyName);

}
