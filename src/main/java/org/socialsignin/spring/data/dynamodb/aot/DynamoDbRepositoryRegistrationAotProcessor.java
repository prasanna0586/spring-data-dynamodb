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
import org.socialsignin.spring.data.dynamodb.core.StaticTableSchemaGenerator;
import org.socialsignin.spring.data.dynamodb.repository.DynamoDBCrudRepository;
import org.socialsignin.spring.data.dynamodb.repository.DynamoDBPagingAndSortingRepository;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.data.repository.Repository;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

import java.util.HashSet;
import java.util.Set;

/**
 * AOT processor for DynamoDB repositories that registers runtime hints and
 * pre-generates TableSchema instances for GraalVM native image support.
 *
 * <p>This processor runs during the AOT (Ahead-of-Time) compilation phase and:
 * <ul>
 *   <li>Discovers all {@code @DynamoDbBean} and {@code @DynamoDbImmutable} annotated classes</li>
 *   <li>Registers reflection hints for these classes</li>
 *   <li>Pre-generates StaticTableSchema instances to avoid runtime LambdaMetafactory usage</li>
 * </ul>
 *
 * <p><b>How It Works:</b></p>
 * <p>During AOT processing (maven/gradle build with AOT enabled), this processor:
 * <ol>
 *   <li>Scans the BeanFactory for repository beans</li>
 *   <li>Extracts the entity types from repository interfaces</li>
 *   <li>Generates runtime hints for reflection access</li>
 *   <li>Pre-registers TableSchema suppliers in the registry</li>
 * </ol>
 *
 * <p><b>Registration:</b></p>
 * <p>This processor is automatically registered via {@code META-INF/spring/aot.factories}:
 * <pre>
 * org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor=\
 *   org.socialsignin.spring.data.dynamodb.aot.DynamoDbRepositoryRegistrationAotProcessor
 * </pre>
 *
 * @author Prasanna Kumar Ramachandran
 * @since 7.0.0
 * @see DynamoDbRuntimeHints
 * @see StaticTableSchemaGenerator
 */
public class DynamoDbRepositoryRegistrationAotProcessor implements BeanFactoryInitializationAotProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamoDbRepositoryRegistrationAotProcessor.class);

    @Override
    @Nullable
    public BeanFactoryInitializationAotContribution processAheadOfTime(
            @NonNull ConfigurableListableBeanFactory beanFactory) {

        LOGGER.info("Processing DynamoDB repositories for AOT compilation");

        Set<Class<?>> entityClasses = discoverEntityClasses(beanFactory);
        Set<Class<?>> repositoryInterfaces = discoverRepositoryInterfaces(beanFactory);

        if (entityClasses.isEmpty() && repositoryInterfaces.isEmpty()) {
            LOGGER.debug("No DynamoDB entity classes or repository interfaces found");
            return null;
        }

        LOGGER.info("Discovered {} DynamoDB entity classes and {} repository interfaces for AOT processing",
                entityClasses.size(), repositoryInterfaces.size());

        return (generationContext, beanFactoryInitializationCode) -> {
            RuntimeHints hints = generationContext.getRuntimeHints();

            for (Class<?> entityClass : entityClasses) {
                // Register runtime hints for reflection
                registerEntityHints(hints, entityClass);

                // Register the entity class for schema generation
                DynamoDbRuntimeHints.registerEntityClass(entityClass);
            }

            // Register repository interface hints including proxy configuration
            for (Class<?> repositoryInterface : repositoryInterfaces) {
                registerRepositoryHints(hints, repositoryInterface);
            }

            LOGGER.info("Registered AOT hints for {} DynamoDB entity classes and {} repository interfaces",
                    entityClasses.size(), repositoryInterfaces.size());
        };
    }

    /**
     * Discovers all DynamoDB entity classes from the bean factory.
     */
    @NonNull
    private Set<Class<?>> discoverEntityClasses(@NonNull ConfigurableListableBeanFactory beanFactory) {
        Set<Class<?>> entityClasses = new HashSet<>();

        // Scan all bean definitions for @DynamoDbBean annotated classes
        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            try {
                Class<?> beanClass = beanFactory.getType(beanName);
                if (beanClass != null) {
                    // Check if this bean is a DynamoDB entity
                    if (isDynamoDbEntity(beanClass)) {
                        entityClasses.add(beanClass);
                        LOGGER.debug("Discovered DynamoDB entity: {}", beanClass.getName());
                    }

                    // Check if this is a repository and extract its entity type
                    extractEntityFromRepository(beanClass, entityClasses);
                }
            } catch (Exception e) {
                LOGGER.trace("Could not process bean '{}': {}", beanName, e.getMessage());
            }
        }

        return entityClasses;
    }

    /**
     * Extracts entity type from a repository interface.
     */
    private void extractEntityFromRepository(@NonNull Class<?> repositoryClass, @NonNull Set<Class<?>> entityClasses) {
        // Check generic interfaces for Repository<T, ID> pattern
        for (java.lang.reflect.Type genericInterface : repositoryClass.getGenericInterfaces()) {
            if (genericInterface instanceof java.lang.reflect.ParameterizedType parameterizedType) {
                java.lang.reflect.Type[] typeArguments = parameterizedType.getActualTypeArguments();
                if (typeArguments.length > 0 && typeArguments[0] instanceof Class<?> entityType) {
                    if (isDynamoDbEntity(entityType)) {
                        entityClasses.add(entityType);
                        LOGGER.debug("Discovered entity from repository: {}", entityType.getName());
                    }
                }
            }
        }
    }

    /**
     * Checks if a class is a DynamoDB entity.
     */
    private boolean isDynamoDbEntity(@NonNull Class<?> clazz) {
        return clazz.isAnnotationPresent(DynamoDbBean.class) ||
                clazz.isAnnotationPresent(DynamoDbImmutable.class);
    }

    /**
     * Discovers all DynamoDB repository interfaces from the bean factory.
     */
    @NonNull
    private Set<Class<?>> discoverRepositoryInterfaces(@NonNull ConfigurableListableBeanFactory beanFactory) {
        Set<Class<?>> repositoryInterfaces = new HashSet<>();

        for (String beanName : beanFactory.getBeanDefinitionNames()) {
            try {
                Class<?> beanClass = beanFactory.getType(beanName);
                if (beanClass != null && isDynamoDbRepository(beanClass)) {
                    repositoryInterfaces.add(beanClass);
                    LOGGER.debug("Discovered DynamoDB repository: {}", beanClass.getName());
                }
            } catch (Exception e) {
                LOGGER.trace("Could not process bean '{}' for repository discovery: {}", beanName, e.getMessage());
            }
        }

        return repositoryInterfaces;
    }

    /**
     * Checks if a class is a DynamoDB repository interface.
     */
    private boolean isDynamoDbRepository(@NonNull Class<?> clazz) {
        if (!clazz.isInterface()) {
            return false;
        }
        return DynamoDBCrudRepository.class.isAssignableFrom(clazz) ||
                DynamoDBPagingAndSortingRepository.class.isAssignableFrom(clazz) ||
                (Repository.class.isAssignableFrom(clazz) && hasDynamoDbEntity(clazz));
    }

    /**
     * Checks if a repository interface has a DynamoDB entity as its domain type.
     */
    private boolean hasDynamoDbEntity(@NonNull Class<?> repositoryClass) {
        for (java.lang.reflect.Type genericInterface : repositoryClass.getGenericInterfaces()) {
            if (genericInterface instanceof java.lang.reflect.ParameterizedType parameterizedType) {
                java.lang.reflect.Type[] typeArguments = parameterizedType.getActualTypeArguments();
                if (typeArguments.length > 0 && typeArguments[0] instanceof Class<?> entityType) {
                    if (isDynamoDbEntity(entityType)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Registers runtime hints for an entity class.
     */
    private void registerEntityHints(@NonNull RuntimeHints hints, @NonNull Class<?> entityClass) {
        LOGGER.debug("Registering AOT hints for entity: {}", entityClass.getName());

        hints.reflection().registerType(entityClass,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS);

        // Register any nested classes (for composite keys, etc.)
        for (Class<?> nestedClass : entityClass.getDeclaredClasses()) {
            hints.reflection().registerType(nestedClass,
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.DECLARED_FIELDS);
        }
    }

    /**
     * Registers runtime hints for a repository interface.
     * <p>
     * This includes reflection hints for the interface itself and JDK proxy hints
     * for Spring's AOP proxy support. These hints are essential for proper
     * repository method routing in GraalVM native images.
     */
    private void registerRepositoryHints(@NonNull RuntimeHints hints, @NonNull Class<?> repositoryInterface) {
        LOGGER.debug("Registering AOT hints for repository: {}", repositoryInterface.getName());

        // Register reflection hints for the repository interface
        hints.reflection().registerType(repositoryInterface,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS);

        // Register JDK proxy hints for the repository interface
        // Spring Data creates JDK proxies for repository interfaces
        hints.proxies().registerJdkProxy(
                TypeReference.of(repositoryInterface),
                TypeReference.of("org.springframework.aop.SpringProxy"),
                TypeReference.of("org.springframework.aop.framework.Advised"),
                TypeReference.of("org.springframework.core.DecoratingProxy")
        );

        LOGGER.debug("Registered JDK proxy hints for repository: {}", repositoryInterface.getName());
    }
}
