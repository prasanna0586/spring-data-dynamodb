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
package org.socialsignin.spring.data.dynamodb.repository.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socialsignin.spring.data.dynamodb.core.TableNameResolver;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * Factory for creating TableNameResolver instances for SDK v2.
 * <p>
 * Replaces the SDK v1 DynamoDBMapperConfigFactory. In SDK v2, table name resolution
 * is simplified and no longer requires the complex configuration that DynamoDBMapperConfig provided.
 * <p>
 * If a user-defined {@link TableNameResolver} bean exists in the application context,
 * it will be used. Otherwise, a default resolver that returns the base table name unchanged
 * will be provided.
 *
 * @deprecated This factory is provided for backward compatibility. Consider injecting
 *             TableNameResolver directly or using a custom configuration class.
 */
@Deprecated
public class DynamoDBMapperConfigFactory implements FactoryBean<TableNameResolver>, BeanFactoryAware, BeanNameAware {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamoDBMapperConfigFactory.class);

    /**
     * User-defined TableNameResolver bean, if available.
     */
    @Nullable
    private TableNameResolver tableNameResolver;

    @Nullable
    private ConfigurableListableBeanFactory beanFactory;

    @Nullable
    private String ownBeanName;

    /**
     * Default constructor for DynamoDBMapperConfigFactory.
     */
    public DynamoDBMapperConfigFactory() {
    }

    @Override
    public void setBeanName(@NonNull String name) {
        this.ownBeanName = name;
    }

    @Override
    public void setBeanFactory(@NonNull BeanFactory beanFactory) throws BeansException {
        if (beanFactory instanceof ConfigurableListableBeanFactory) {
            this.beanFactory = (ConfigurableListableBeanFactory) beanFactory;
        }
    }

    /**
     * Sets the TableNameResolver to use. Called programmatically for testing.
     *
     * @param tableNameResolver the user-defined TableNameResolver, or null if none exists
     */
    public void setTableNameResolver(@Nullable TableNameResolver tableNameResolver) {
        this.tableNameResolver = tableNameResolver;
        if (tableNameResolver != null) {
            LOGGER.debug("Using user-defined TableNameResolver: {}", tableNameResolver.getClass().getName());
        }
    }

    /**
     * Default TableNameResolver that returns the base table name unchanged.
     */
    private static final TableNameResolver DEFAULT = new TableNameResolver() {
        @Override
        public <T> String resolveTableName(Class<T> domainClass, String baseTableName) {
            return baseTableName;
        }
    };

    /**
     * Looks up user-defined TableNameResolver beans from the application context.
     * This method avoids circular reference issues by scanning bean definitions
     * instead of instantiating beans.
     */
    @Nullable
    private TableNameResolver lookupUserDefinedResolver() {
        if (beanFactory == null) {
            return null;
        }

        // Scan bean definitions to find user-defined TableNameResolver beans
        // without triggering bean instantiation that would cause circular references
        String[] beanNames = beanFactory.getBeanNamesForType(TableNameResolver.class, false, false);

        for (String beanName : beanNames) {
            // Skip our own bean (the FactoryBean creates a bean with the same name)
            if (beanName.equals(ownBeanName)) {
                continue;
            }

            // Check if this is a FactoryBean-produced bean by checking for the & prefix
            // FactoryBeans register themselves with & prefix for the factory, and without for the product
            if (beanFactory.containsBean("&" + beanName)) {
                // This is a FactoryBean-produced bean, check if it's our FactoryBean type
                Object factoryBean = beanFactory.getBean("&" + beanName);
                if (factoryBean instanceof DynamoDBMapperConfigFactory) {
                    // Skip beans produced by our own factory type
                    continue;
                }
            }

            // Check if this is a real bean definition (not a FactoryBean product)
            BeanDefinition beanDef = beanFactory.containsBeanDefinition(beanName)
                    ? beanFactory.getBeanDefinition(beanName)
                    : null;

            if (beanDef != null) {
                String beanClassName = beanDef.getBeanClassName();
                // Skip if this is a DynamoDBMapperConfigFactory
                if (beanClassName != null && beanClassName.equals(DynamoDBMapperConfigFactory.class.getName())) {
                    continue;
                }
            }

            // This looks like a user-defined TableNameResolver, try to get it
            try {
                TableNameResolver resolver = beanFactory.getBean(beanName, TableNameResolver.class);
                LOGGER.debug("Found user-defined TableNameResolver bean '{}': {}", beanName, resolver.getClass().getName());
                return resolver;
            } catch (Exception e) {
                LOGGER.trace("Could not get TableNameResolver bean '{}': {}", beanName, e.getMessage());
            }
        }

        return null;
    }

    @Override
    @Nullable
    public TableNameResolver getObject() {
        // First check if explicitly set (for testing)
        if (tableNameResolver != null) {
            return tableNameResolver;
        }

        // Look up from application context
        TableNameResolver resolved = lookupUserDefinedResolver();
        if (resolved != null) {
            return resolved;
        }

        LOGGER.debug("No user-defined TableNameResolver found, using default (no-op) resolver");
        return DEFAULT;
    }

    @Override
    public Class<?> getObjectType() {
        return TableNameResolver.class;
    }

}
