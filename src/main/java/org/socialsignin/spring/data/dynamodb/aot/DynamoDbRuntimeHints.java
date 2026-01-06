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
package org.socialsignin.spring.data.dynamodb.aot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

import java.util.HashSet;
import java.util.Set;

/**
 * {@link RuntimeHintsRegistrar} for DynamoDB entities to enable GraalVM native image support.
 *
 * <p>This registrar provides reflection hints for {@code @DynamoDbBean} and
 * {@code @DynamoDbImmutable} annotated classes, ensuring they can be accessed
 * via reflection in native images.
 *
 * <p><b>Usage:</b></p>
 * <p>This registrar is automatically discovered via Spring Boot's auto-configuration.
 * You can also manually import it using:
 * <pre>
 * {@code @ImportRuntimeHints(DynamoDbRuntimeHints.class)}
 * public class MyConfiguration {
 *     // ...
 * }
 * </pre>
 *
 * <p><b>Manual Registration:</b></p>
 * <p>For cases where entity classes are not automatically discovered, you can
 * manually register them:
 * <pre>
 * DynamoDbRuntimeHints.registerEntityClass(MyEntity.class);
 * </pre>
 *
 * @author Prasanna Kumar Ramachandran
 * @since 7.0.0
 * @see org.springframework.aot.hint.RuntimeHintsRegistrar
 */
public class DynamoDbRuntimeHints implements RuntimeHintsRegistrar {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamoDbRuntimeHints.class);

    /**
     * Set of entity classes to register hints for.
     * Classes can be added programmatically before AOT processing.
     */
    private static final Set<Class<?>> ENTITY_CLASSES = new HashSet<>();

    /**
     * Registers an entity class for runtime hints.
     *
     * <p>Call this method during application initialization (before AOT processing)
     * to ensure the class is included in native image reflection configuration.
     *
     * @param entityClass the entity class to register
     */
    public static void registerEntityClass(@NonNull Class<?> entityClass) {
        ENTITY_CLASSES.add(entityClass);
        LOGGER.debug("Registered entity class for runtime hints: {}", entityClass.getName());
    }

    /**
     * Registers multiple entity classes for runtime hints.
     *
     * @param entityClasses the entity classes to register
     */
    public static void registerEntityClasses(@NonNull Iterable<Class<?>> entityClasses) {
        for (Class<?> entityClass : entityClasses) {
            registerEntityClass(entityClass);
        }
    }

    @Override
    public void registerHints(@NonNull RuntimeHints hints, @Nullable ClassLoader classLoader) {
        LOGGER.info("Registering DynamoDB runtime hints for {} entity classes", ENTITY_CLASSES.size());

        // Register hints for all known entity classes
        for (Class<?> entityClass : ENTITY_CLASSES) {
            registerEntityHints(hints, entityClass);
        }

        // Register hints for common DynamoDB SDK classes
        registerSdkHints(hints);

        // Register hints for Spring Data DynamoDB internal classes
        registerInternalHints(hints);
    }

    /**
     * Registers runtime hints for an entity class.
     *
     * @param hints       the runtime hints
     * @param entityClass the entity class
     */
    public static void registerEntityHints(@NonNull RuntimeHints hints, @NonNull Class<?> entityClass) {
        LOGGER.debug("Registering runtime hints for entity: {}", entityClass.getName());

        // Register the entity class with full reflection access
        hints.reflection().registerType(entityClass,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.ACCESS_DECLARED_FIELDS,
                MemberCategory.ACCESS_PUBLIC_FIELDS);

        // If it's a nested class, register the enclosing class too
        Class<?> enclosingClass = entityClass.getEnclosingClass();
        if (enclosingClass != null) {
            hints.reflection().registerType(enclosingClass,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
        }
    }

    /**
     * Registers hints for AWS SDK DynamoDB Enhanced Client classes.
     */
    private void registerSdkHints(@NonNull RuntimeHints hints) {
        // Register common AWS SDK classes that may be needed at runtime
        String[] sdkClasses = {
                "software.amazon.awssdk.enhanced.dynamodb.mapper.BeanTableSchema",
                "software.amazon.awssdk.enhanced.dynamodb.mapper.StaticTableSchema",
                "software.amazon.awssdk.enhanced.dynamodb.mapper.ImmutableTableSchema",
                "software.amazon.awssdk.enhanced.dynamodb.DefaultAttributeConverterProvider",
                "software.amazon.awssdk.enhanced.dynamodb.internal.mapper.BeanTableSchemaAttributeTags"
        };

        for (String className : sdkClasses) {
            try {
                hints.reflection().registerType(TypeReference.of(className),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS);
            } catch (Exception e) {
                LOGGER.trace("Could not register hints for SDK class: {}", className);
            }
        }
    }

    /**
     * Registers hints for Spring Data DynamoDB internal classes.
     */
    private void registerInternalHints(@NonNull RuntimeHints hints) {
        // Register marshallers
        String[] marshallerClasses = {
                "org.socialsignin.spring.data.dynamodb.marshaller.Date2IsoAttributeConverter",
                "org.socialsignin.spring.data.dynamodb.marshaller.Date2EpocheAttributeConverter",
                "org.socialsignin.spring.data.dynamodb.marshaller.Instant2IsoAttributeConverter",
                "org.socialsignin.spring.data.dynamodb.marshaller.Instant2EpocheAttributeConverter",
                "org.socialsignin.spring.data.dynamodb.marshaller.BooleanNumberAttributeConverter"
        };

        for (String className : marshallerClasses) {
            try {
                hints.reflection().registerType(TypeReference.of(className),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
            } catch (Exception e) {
                LOGGER.trace("Could not register hints for marshaller class: {}", className);
            }
        }

        // Register repository implementation classes for proper fragment routing in native image
        registerRepositoryImplementationHints(hints);

        // Register entity callback hints for Spring Data callbacks
        registerEntityCallbackHints(hints);
    }

    /**
     * Registers hints for Spring Data entity callbacks.
     * <p>
     * This is critical for GraalVM native image support. Spring Data's EntityCallbackDiscoverer
     * uses reflection to find callback methods on callback interfaces. Without proper reflection
     * hints, the callback method lookup fails with:
     * "BeforeConvertCallback does not define a callback method accepting EntityType and N additional arguments"
     */
    private void registerEntityCallbackHints(@NonNull RuntimeHints hints) {
        // Entity callback interfaces and implementations
        String[] callbackClasses = {
                // Custom callback interfaces
                "org.socialsignin.spring.data.dynamodb.mapping.event.BeforeConvertCallback",
                "org.socialsignin.spring.data.dynamodb.mapping.event.AuditingEntityCallback",

                // Event classes
                "org.socialsignin.spring.data.dynamodb.mapping.event.DynamoDBMappingEvent",
                "org.socialsignin.spring.data.dynamodb.mapping.event.BeforeSaveEvent",
                "org.socialsignin.spring.data.dynamodb.mapping.event.AfterSaveEvent",
                "org.socialsignin.spring.data.dynamodb.mapping.event.BeforeDeleteEvent",
                "org.socialsignin.spring.data.dynamodb.mapping.event.AfterDeleteEvent",
                "org.socialsignin.spring.data.dynamodb.mapping.event.AfterLoadEvent",
                "org.socialsignin.spring.data.dynamodb.mapping.event.AfterQueryEvent",
                "org.socialsignin.spring.data.dynamodb.mapping.event.AfterScanEvent",

                // Event listeners
                "org.socialsignin.spring.data.dynamodb.mapping.event.AbstractDynamoDBEventListener",
                "org.socialsignin.spring.data.dynamodb.mapping.event.LoggingEventListener",
                "org.socialsignin.spring.data.dynamodb.mapping.event.ValidatingDynamoDBEventListener",
                "org.socialsignin.spring.data.dynamodb.mapping.event.AuditingEventListener",

                // Spring Data callback infrastructure
                "org.springframework.data.mapping.callback.EntityCallback",
                "org.springframework.data.mapping.callback.EntityCallbacks",
                "org.springframework.data.mapping.callback.EntityCallbackDiscoverer",
                "org.springframework.data.mapping.callback.DefaultEntityCallbacks",

                // Auditing support
                "org.springframework.data.auditing.IsNewAwareAuditingHandler",
                "org.springframework.data.auditing.AuditingHandler"
        };

        for (String className : callbackClasses) {
            try {
                hints.reflection().registerType(TypeReference.of(className),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.INVOKE_PUBLIC_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS,
                        MemberCategory.ACCESS_PUBLIC_FIELDS);
                LOGGER.debug("Registered reflection hints for callback class: {}", className);
            } catch (Exception e) {
                LOGGER.trace("Could not register hints for callback class: {}", className);
            }
        }
    }

    /**
     * Registers hints for repository implementation classes.
     * <p>
     * This is critical for GraalVM native image support. Spring Data uses dynamic proxies
     * to route method calls to repository fragment implementations. Without proper reflection
     * hints, the proxy cannot find and invoke the implementation methods, causing CRUD
     * operations like save, delete, etc. to fail.
     */
    private void registerRepositoryImplementationHints(@NonNull RuntimeHints hints) {
        // Repository implementation classes
        String[] repositoryImplClasses = {
                // Core repository implementations
                "org.socialsignin.spring.data.dynamodb.repository.support.SimpleDynamoDBCrudRepository",
                "org.socialsignin.spring.data.dynamodb.repository.support.SimpleDynamoDBPagingAndSortingRepository",

                // Repository support classes
                "org.socialsignin.spring.data.dynamodb.repository.support.DynamoDBRepositoryFactory",
                "org.socialsignin.spring.data.dynamodb.repository.support.DynamoDBRepositoryFactoryBean",
                "org.socialsignin.spring.data.dynamodb.repository.support.DynamoDBEntityInformation",
                "org.socialsignin.spring.data.dynamodb.repository.support.DynamoDBEntityMetadataSupport",
                "org.socialsignin.spring.data.dynamodb.repository.support.DynamoDBHashKeyExtractingEntityMetadata",
                "org.socialsignin.spring.data.dynamodb.repository.support.DynamoDBHashAndRangeKeyExtractingEntityMetadataImpl",
                "org.socialsignin.spring.data.dynamodb.repository.support.DynamoDBIdIsHashKeyEntityInformationImpl",
                "org.socialsignin.spring.data.dynamodb.repository.support.DynamoDBIdIsHashAndRangeKeyEntityInformationImpl",
                "org.socialsignin.spring.data.dynamodb.repository.support.EnableScanAnnotationPermissions",
                "org.socialsignin.spring.data.dynamodb.repository.support.FieldAndGetterReflectionEntityInformation",
                "org.socialsignin.spring.data.dynamodb.repository.support.HashKeyIsIdHashKeyExtractor",
                "org.socialsignin.spring.data.dynamodb.repository.support.CompositeIdHashAndRangeKeyExtractor",

                // Query classes
                "org.socialsignin.spring.data.dynamodb.repository.query.DynamoDBQueryLookupStrategy",
                "org.socialsignin.spring.data.dynamodb.repository.query.DynamoDBQueryMethod",
                "org.socialsignin.spring.data.dynamodb.repository.query.PartTreeDynamoDBQuery",
                "org.socialsignin.spring.data.dynamodb.repository.query.AbstractDynamoDBQuery",

                // Core classes
                "org.socialsignin.spring.data.dynamodb.core.DynamoDBTemplate",
                "org.socialsignin.spring.data.dynamodb.core.DynamoDbTableSchemaRegistry",
                "org.socialsignin.spring.data.dynamodb.core.StaticTableSchemaGenerator",
                "org.socialsignin.spring.data.dynamodb.core.TableSchemaFactory"
        };

        for (String className : repositoryImplClasses) {
            try {
                hints.reflection().registerType(TypeReference.of(className),
                        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.INVOKE_PUBLIC_METHODS,
                        MemberCategory.ACCESS_DECLARED_FIELDS,
                        MemberCategory.ACCESS_PUBLIC_FIELDS);
                LOGGER.debug("Registered reflection hints for: {}", className);
            } catch (Exception e) {
                LOGGER.trace("Could not register hints for class: {}", className);
            }
        }

        // Register repository interfaces
        String[] repositoryInterfaces = {
                "org.socialsignin.spring.data.dynamodb.repository.DynamoDBCrudRepository",
                "org.socialsignin.spring.data.dynamodb.repository.DynamoDBPagingAndSortingRepository",
                "org.springframework.data.repository.CrudRepository",
                "org.springframework.data.repository.PagingAndSortingRepository",
                "org.springframework.data.repository.Repository"
        };

        for (String interfaceName : repositoryInterfaces) {
            try {
                hints.reflection().registerType(TypeReference.of(interfaceName),
                        MemberCategory.INVOKE_DECLARED_METHODS,
                        MemberCategory.INVOKE_PUBLIC_METHODS);
                LOGGER.debug("Registered reflection hints for interface: {}", interfaceName);
            } catch (Exception e) {
                LOGGER.trace("Could not register hints for interface: {}", interfaceName);
            }
        }

        // Register JDK dynamic proxy hints for repository interfaces
        hints.proxies().registerJdkProxy(
                TypeReference.of("org.socialsignin.spring.data.dynamodb.repository.DynamoDBCrudRepository"),
                TypeReference.of("org.springframework.aop.SpringProxy"),
                TypeReference.of("org.springframework.aop.framework.Advised"),
                TypeReference.of("org.springframework.core.DecoratingProxy")
        );

        hints.proxies().registerJdkProxy(
                TypeReference.of("org.socialsignin.spring.data.dynamodb.repository.DynamoDBPagingAndSortingRepository"),
                TypeReference.of("org.springframework.aop.SpringProxy"),
                TypeReference.of("org.springframework.aop.framework.Advised"),
                TypeReference.of("org.springframework.core.DecoratingProxy")
        );
    }

    /**
     * Checks if a class is a DynamoDB entity (annotated with @DynamoDbBean or @DynamoDbImmutable).
     *
     * @param clazz the class to check
     * @return true if it's a DynamoDB entity
     */
    public static boolean isDynamoDbEntity(@NonNull Class<?> clazz) {
        return clazz.isAnnotationPresent(DynamoDbBean.class) ||
                clazz.isAnnotationPresent(DynamoDbImmutable.class);
    }
}
