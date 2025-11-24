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
package org.socialsignin.spring.data.dynamodb.repository.config;

import org.socialsignin.spring.data.dynamodb.core.TableNameResolver;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.lang.Nullable;

/**
 * Factory for creating TableNameResolver instances for SDK v2.
 * <p>
 * Replaces the SDK v1 DynamoDBMapperConfigFactory. In SDK v2, table name resolution
 * is simplified and no longer requires the complex configuration that DynamoDBMapperConfig provided.
 * </p>
 *
 * @deprecated This factory is provided for backward compatibility. Consider injecting
 *             TableNameResolver directly or using a custom configuration class.
 */
@Deprecated
public class DynamoDBMapperConfigFactory implements FactoryBean<TableNameResolver> {

    /**
     * Default TableNameResolver that returns the base table name unchanged.
     */
    private static final TableNameResolver DEFAULT = new TableNameResolver() {
        @Override
        public <T> String resolveTableName(Class<T> domainClass, String baseTableName) {
            return baseTableName;
        }
    };

    @Override
    @Nullable
    public TableNameResolver getObject() {
        return DEFAULT;
    }

    @Override
    public Class<?> getObjectType() {
        return TableNameResolver.class;
    }

}
