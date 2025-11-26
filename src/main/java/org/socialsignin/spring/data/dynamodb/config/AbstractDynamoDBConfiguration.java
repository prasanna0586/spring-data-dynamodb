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
package org.socialsignin.spring.data.dynamodb.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socialsignin.spring.data.dynamodb.mapping.DynamoDBMappingContext;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.lang.NonNull;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.util.HashSet;
import java.util.Set;

/**
 * Abstract configuration class for setting up Spring Data DynamoDB using JavaConfig.
 *
 * This class provides a base implementation for DynamoDB configuration, handling
 * the scanning and initialization of DynamoDB entities annotated with {@link DynamoDbBean}.
 * Subclasses must provide a concrete implementation of the {@link #amazonDynamoDB()} method
 * to supply the DynamoDB client bean.
 *
 * The configuration automatically scans the base packages for DynamoDB mapped entities
 * and creates a {@link DynamoDBMappingContext} to manage the mapping of these entities.
 * By default, the base package to scan is determined from the concrete configuration class
 * that extends this class.
 * @author Prasanna Kumar Ramachandran
 */
@Configuration
public abstract class AbstractDynamoDBConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDynamoDBConfiguration.class);

    /**
     * Default constructor for the abstract DynamoDB configuration class.
     * <p>
     * This constructor is used by Spring to instantiate the configuration class
     * when it is being processed as a Spring bean.
     */
    public AbstractDynamoDBConfiguration() {
    }

    /**
     * Returns the DynamoDB client bean for use throughout the application.
     * <p>
     * Subclasses must implement this method to provide a concrete {@link DynamoDbClient} instance.
     * This client is used for all DynamoDB operations in the Spring Data DynamoDB framework.
     * @return the DynamoDB client bean configured for this application
     */
    public abstract DynamoDbClient amazonDynamoDB();

    /**
     * Return the base packages to scan for mapped {@link DynamoDbBean}s. Will return the package name of the
     * configuration class' (the concrete class, not this one here) by default. So if you have a
     * {@code com.acme.AppConfig} extending {@link AbstractDynamoDBConfiguration} the base package will be considered
     * {@code com.acme} unless the method is overriden to implement alternate behaviour.
     * @return the base package to scan for mapped {@link DynamoDbBean} classes or {@literal null} to not enable
     *         scanning for entities.
     */
    @NonNull
    protected String[] getMappingBasePackages() {

        Package mappingBasePackage = getClass().getPackage();
        String basePackage = mappingBasePackage == null ? null : mappingBasePackage.getName();

        return new String[] { basePackage };
    }

    /**
     * Creates a {@link DynamoDBMappingContext} equipped with entity classes scanned from the mapping base package.
     * @see #getMappingBasePackages()
     * @return A newly created {@link DynamoDBMappingContext}
     * @throws ClassNotFoundException
     *             if the class with {@link DynamoDbBean} annotation can't be loaded
     */
    @NonNull
    @Bean
    public DynamoDBMappingContext dynamoDBMappingContext() throws ClassNotFoundException {

        DynamoDBMappingContext mappingContext = new DynamoDBMappingContext();
        mappingContext.setInitialEntitySet(getInitialEntitySet());

        return mappingContext;
    }

    /**
     * Scans the mapping base package for classes annotated with {@link DynamoDbBean}.
     * @see #getMappingBasePackages()
     * @return All classes with {@link DynamoDbBean} annotation
     * @throws ClassNotFoundException
     *             if the class with {@link DynamoDbBean} annotation can't be loaded
     */
    @NonNull
    protected Set<Class<?>> getInitialEntitySet() throws ClassNotFoundException {

        Set<Class<?>> initialEntitySet = new HashSet<>();

        String[] basePackages = getMappingBasePackages();

        for (String basePackage : basePackages) {
            LOGGER.trace("getInitialEntitySet. basePackage: {}", basePackage);

            if (StringUtils.hasText(basePackage)) {
                ClassPathScanningCandidateComponentProvider componentProvider = new ClassPathScanningCandidateComponentProvider(
                        false);
                componentProvider.addIncludeFilter(new AnnotationTypeFilter(DynamoDbBean.class));

                for (BeanDefinition candidate : componentProvider.findCandidateComponents(basePackage)) {
                    String candidateClass = candidate.getBeanClassName();
                    if (candidateClass != null) {
                        LOGGER.trace("getInitialEntitySet. candidate: {}", candidateClass);
                        initialEntitySet.add(ClassUtils.forName(candidateClass,
                                AbstractDynamoDBConfiguration.class.getClassLoader()));
                    } else {
                        LOGGER.warn("getInitialEntitySet. candidate: {} did not provide a class", candidate);
                    }
                }
            }
        }

        return initialEntitySet;
    }

}
