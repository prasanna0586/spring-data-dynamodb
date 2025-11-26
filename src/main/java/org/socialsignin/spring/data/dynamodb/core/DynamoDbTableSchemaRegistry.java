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
package org.socialsignin.spring.data.dynamodb.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for DynamoDB TableSchema instances.
 *
 * <p>This registry provides a central location for managing TableSchema instances,
 * supporting both pre-registered schemas (for GraalVM native image compatibility)
 * and dynamically generated schemas.
 *
 * <p><b>GraalVM Native Image Support:</b></p>
 * <p>For GraalVM native image builds, schemas should be pre-registered during
 * application startup or AOT processing. This avoids the use of
 * {@code TableSchema.fromBean()} which relies on {@code LambdaMetafactory}
 * and is incompatible with native images.
 *
 * <p><b>Usage:</b></p>
 * <pre>
 * // Register a pre-built StaticTableSchema
 * DynamoDbTableSchemaRegistry.getInstance().register(MyEntity.class, myStaticSchema);
 *
 * // Or use the supplier-based registration for lazy initialization
 * DynamoDbTableSchemaRegistry.getInstance().register(MyEntity.class, () -> buildSchema());
 *
 * // Retrieve a schema (will use registered or generate dynamically)
 * TableSchema&lt;MyEntity&gt; schema = DynamoDbTableSchemaRegistry.getInstance().getTableSchema(MyEntity.class);
 * </pre>
 *
 * @author Prasanna Kumar Ramachandran
 * @since 7.0.0
 * @see StaticTableSchemaGenerator
 * @see software.amazon.awssdk.enhanced.dynamodb.TableSchema
 */
public class DynamoDbTableSchemaRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamoDbTableSchemaRegistry.class);

    private static final DynamoDbTableSchemaRegistry INSTANCE = new DynamoDbTableSchemaRegistry();

    private final Map<Class<?>, TableSchema<?>> schemaCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, TableSchemaSupplier<?>> schemaSuppliers = new ConcurrentHashMap<>();

    private volatile boolean useStaticSchemaGenerator = true;

    /**
     * Functional interface for supplying TableSchema instances lazily.
     *
     * @param <T> the entity type
     */
    @FunctionalInterface
    public interface TableSchemaSupplier<T> {
        /**
         * Supplies a TableSchema instance.
         *
         * @return the TableSchema
         */
        TableSchema<T> get();
    }

    /**
     * Private constructor for singleton pattern.
     */
    private DynamoDbTableSchemaRegistry() {
    }

    /**
     * Returns the singleton instance of the registry.
     *
     * @return the registry instance
     */
    @NonNull
    public static DynamoDbTableSchemaRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Registers a TableSchema for a domain class.
     *
     * <p>This method should be used to pre-register StaticTableSchema instances
     * for GraalVM native image compatibility.
     *
     * @param <T>         the entity type
     * @param domainClass the domain class
     * @param schema      the TableSchema to register
     */
    public <T> void register(@NonNull Class<T> domainClass, @NonNull TableSchema<T> schema) {
        LOGGER.debug("Registering TableSchema for class: {}", domainClass.getName());
        schemaCache.put(domainClass, schema);
    }

    /**
     * Registers a TableSchema supplier for lazy initialization.
     *
     * <p>The supplier will be called once when the schema is first requested,
     * and the result will be cached.
     *
     * @param <T>         the entity type
     * @param domainClass the domain class
     * @param supplier    the supplier that creates the TableSchema
     */
    public <T> void register(@NonNull Class<T> domainClass, @NonNull TableSchemaSupplier<T> supplier) {
        LOGGER.debug("Registering TableSchema supplier for class: {}", domainClass.getName());
        schemaSuppliers.put(domainClass, supplier);
    }

    /**
     * Retrieves the TableSchema for a domain class.
     *
     * <p>This method will:
     * <ol>
     *   <li>Return a pre-registered schema if available</li>
     *   <li>Call a registered supplier if available (and cache the result)</li>
     *   <li>Generate a schema using {@link StaticTableSchemaGenerator} if enabled</li>
     *   <li>Fall back to {@code TableSchema.fromBean()} as a last resort (JVM only)</li>
     * </ol>
     *
     * @param <T>         the entity type
     * @param domainClass the domain class
     * @return the TableSchema for the domain class
     * @throws IllegalStateException if no schema can be created
     */
    @NonNull
    @SuppressWarnings("unchecked")
    public <T> TableSchema<T> getTableSchema(@NonNull Class<T> domainClass) {
        // Check cache first
        TableSchema<T> cached = (TableSchema<T>) schemaCache.get(domainClass);
        if (cached != null) {
            LOGGER.trace("Returning cached TableSchema for class: {}", domainClass.getName());
            return cached;
        }

        // Check for supplier
        TableSchemaSupplier<T> supplier = (TableSchemaSupplier<T>) schemaSuppliers.get(domainClass);
        if (supplier != null) {
            LOGGER.debug("Creating TableSchema from supplier for class: {}", domainClass.getName());
            TableSchema<T> schema = supplier.get();
            schemaCache.put(domainClass, schema);
            return schema;
        }

        // Generate using StaticTableSchemaGenerator
        if (useStaticSchemaGenerator) {
            LOGGER.debug("Generating StaticTableSchema for class: {}", domainClass.getName());
            try {
                TableSchema<T> schema = StaticTableSchemaGenerator.generateSchema(domainClass);
                schemaCache.put(domainClass, schema);
                return schema;
            } catch (Exception e) {
                LOGGER.warn("Failed to generate StaticTableSchema for {}, falling back to fromBean(): {}",
                        domainClass.getName(), e.getMessage());
            }
        }

        // Fallback to fromBean() - will fail in native image
        LOGGER.debug("Falling back to TableSchema.fromBean() for class: {}", domainClass.getName());
        TableSchema<T> schema = TableSchema.fromBean(domainClass);
        schemaCache.put(domainClass, schema);
        return schema;
    }

    /**
     * Checks if a schema is registered for the given domain class.
     *
     * @param domainClass the domain class
     * @return true if a schema or supplier is registered
     */
    public boolean isRegistered(@NonNull Class<?> domainClass) {
        return schemaCache.containsKey(domainClass) || schemaSuppliers.containsKey(domainClass);
    }

    /**
     * Clears all registered schemas and suppliers.
     *
     * <p>This method is primarily intended for testing purposes.
     */
    public void clear() {
        LOGGER.debug("Clearing all registered TableSchemas");
        schemaCache.clear();
        schemaSuppliers.clear();
    }

    /**
     * Sets whether to use the StaticTableSchemaGenerator for unregistered classes.
     *
     * <p>When enabled (default), the registry will attempt to generate a
     * StaticTableSchema using reflection and MethodHandles. When disabled,
     * it will fall back directly to {@code TableSchema.fromBean()}.
     *
     * @param enabled true to enable StaticTableSchemaGenerator
     */
    public void setUseStaticSchemaGenerator(boolean enabled) {
        this.useStaticSchemaGenerator = enabled;
    }

    /**
     * Returns whether the StaticTableSchemaGenerator is enabled.
     *
     * @return true if enabled
     */
    public boolean isUseStaticSchemaGenerator() {
        return useStaticSchemaGenerator;
    }
}
