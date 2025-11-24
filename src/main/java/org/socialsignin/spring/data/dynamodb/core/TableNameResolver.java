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
package org.socialsignin.spring.data.dynamodb.core;

/**
 * Strategy interface for resolving DynamoDB table names.
 * Replaces SDK v1 DynamoDBMapperConfig.TableNameOverride functionality.
 * @author Prasanna Kumar Ramachandran
 * @since 7.0.0
 */
public interface TableNameResolver {
    /**
     * Resolves the table name for a given domain class.
     *
     * @param <T>           The domain class type
     * @param domainClass   The domain class
     * @param baseTableName The base table name from annotations
     * @return The resolved table name (may include prefix/override)
     */
    <T> String resolveTableName(Class<T> domainClass, String baseTableName);
}
