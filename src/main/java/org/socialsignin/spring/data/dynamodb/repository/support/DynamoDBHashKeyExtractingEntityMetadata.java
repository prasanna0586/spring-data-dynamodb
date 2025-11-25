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

    /**
     * Gets the overridden DynamoDB attribute name for a property, if one exists.
     *
     * @param propertyName the property name
     * @return an Optional containing the overridden attribute name, or empty if not overridden
     */
    Optional<String> getOverriddenAttributeName(String propertyName);

    /**
     * Gets the attribute converter for a property, if one is configured.
     *
     * @param propertyName the property name
     * @return the attribute converter, or null if none is configured
     */
    @Nullable
    AttributeConverter<?> getAttributeConverterForProperty(String propertyName);

    /**
     * Checks if the given property is the hash key property.
     *
     * @param propertyName the property name to check
     * @return true if the property is the hash key, false otherwise
     */
    boolean isHashKeyProperty(String propertyName);

    /**
     * Gets the name of the hash key property.
     *
     * @return the hash key property name, or null if not available
     */
    @Nullable
    String getHashKeyPropertyName();

    /**
     * Gets the DynamoDB table name for this entity.
     *
     * @return the DynamoDB table name
     */
    String getDynamoDBTableName();

    /**
     * Gets the global secondary index names mapped by property name.
     *
     * @return a map of property names to their associated global secondary index names
     */
    Map<String, String[]> getGlobalSecondaryIndexNamesByPropertyName();

    /**
     * Checks if the given property is a global secondary index hash key.
     *
     * @param propertyName the property name to check
     * @return true if the property is a GSI hash key, false otherwise
     */
    boolean isGlobalIndexHashKeyProperty(String propertyName);

    /**
     * Checks if the given property is a global secondary index range key.
     *
     * @param propertyName the property name to check
     * @return true if the property is a GSI range key, false otherwise
     */
    boolean isGlobalIndexRangeKeyProperty(String propertyName);

}
