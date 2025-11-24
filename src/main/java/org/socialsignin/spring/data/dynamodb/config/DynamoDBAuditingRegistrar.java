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
package org.socialsignin.spring.data.dynamodb.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socialsignin.spring.data.dynamodb.mapping.DynamoDBMappingContext;
import org.socialsignin.spring.data.dynamodb.mapping.event.AuditingEntityCallback;
import org.socialsignin.spring.data.dynamodb.mapping.event.AuditingEventListener;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.data.auditing.IsNewAwareAuditingHandler;
import org.springframework.data.auditing.config.AuditingBeanDefinitionRegistrarSupport;
import org.springframework.data.auditing.config.AuditingConfiguration;
import org.springframework.data.config.ParsingUtils;
import org.springframework.data.repository.config.PersistentEntitiesFactoryBean;
import org.springframework.util.Assert;

import java.lang.annotation.Annotation;

import static org.socialsignin.spring.data.dynamodb.config.BeanNames.MAPPING_CONTEXT_BEAN_NAME;
import static org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE;
import static org.springframework.beans.factory.support.BeanDefinitionBuilder.rootBeanDefinition;

/**
 * {@link org.springframework.context.annotation.ImportBeanDefinitionRegistrar} to enable {@link EnableDynamoDBAuditing}
 * annotation.
 * @author Prasanna Kumar Ramachandran
 */
class DynamoDBAuditingRegistrar extends AuditingBeanDefinitionRegistrarSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamoDBAuditingRegistrar.class);

    /*
     * (non-Javadoc)
     * @see org.springframework.data.auditing.config. AuditingBeanDefinitionRegistrarSupport#getAnnotation()
     */
    @Override
    protected Class<? extends Annotation> getAnnotation() {
        return EnableDynamoDBAuditing.class;
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.auditing.config.
     * AuditingBeanDefinitionRegistrarSupport#getAuditingHandlerBeanName()
     */
    @Override
    protected String getAuditingHandlerBeanName() {
        return "dynamoDBAuditingHandler";
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.auditing.config.
     * AuditingBeanDefinitionRegistrarSupport#registerBeanDefinitions(org. springframework.core.type.AnnotationMetadata,
     * org.springframework.beans.factory.support.BeanDefinitionRegistry)
     */
    @Override
    public void registerBeanDefinitions(AnnotationMetadata annotationMetadata, BeanDefinitionRegistry registry) {
        LOGGER.trace("registerBeanDefinitions");
        Assert.notNull(annotationMetadata, "AnnotationMetadata must not be null!");
        Assert.notNull(registry, "BeanDefinitionRegistry must not be null!");

        defaultDependenciesIfNecessary(registry, annotationMetadata);
        super.registerBeanDefinitions(annotationMetadata, registry);
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.auditing.config.
     * AuditingBeanDefinitionRegistrarSupport#getAuditHandlerBeanDefinitionBuilder(
     * org.springframework.data.auditing.config.AuditingConfiguration)
     */
    @Override
    protected BeanDefinitionBuilder getAuditHandlerBeanDefinitionBuilder(AuditingConfiguration configuration) {
        LOGGER.trace("getAuditHandlerBeanDefinitionBuilder");
        Assert.notNull(configuration, "AuditingConfiguration must not be null!");

        BeanDefinitionBuilder persistentEntities = rootBeanDefinition(PersistentEntitiesFactoryBean.class);
        persistentEntities.addConstructorArgReference(MAPPING_CONTEXT_BEAN_NAME);

        BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(IsNewAwareAuditingHandler.class);
        builder.addConstructorArgValue(persistentEntities.getBeanDefinition());
        return configureDefaultAuditHandlerAttributes(configuration, builder);
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.auditing.config. AuditingBeanDefinitionRegistrarSupport#registerAuditListener(org.
     * springframework.beans.factory.config.BeanDefinition,
     * org.springframework.beans.factory.support.BeanDefinitionRegistry)
     */
    @Override
    protected void registerAuditListenerBeanDefinition(BeanDefinition auditingHandlerDefinition,
            BeanDefinitionRegistry registry) {
        LOGGER.trace("registerAuditListenerBeanDefinition");
        Assert.notNull(auditingHandlerDefinition, "BeanDefinition must not be null!");
        Assert.notNull(registry, "BeanDefinitionRegistry must not be null!");

        // Register the modern callback-based auditing (for entity modification)
        BeanDefinitionBuilder callbackBeanDefinitionBuilder = BeanDefinitionBuilder
                .rootBeanDefinition(AuditingEntityCallback.class);
        callbackBeanDefinitionBuilder.addConstructorArgValue(
                ParsingUtils.getObjectFactoryBeanDefinition(getAuditingHandlerBeanName(), registry));

        registerInfrastructureBeanWithId(callbackBeanDefinitionBuilder.getBeanDefinition(),
                AuditingEntityCallback.class.getName(), registry);

        // Also register the legacy event listener for backward compatibility
        BeanDefinitionBuilder listenerBeanDefinitionBuilder = BeanDefinitionBuilder
                .rootBeanDefinition(AuditingEventListener.class);
        listenerBeanDefinitionBuilder.addConstructorArgValue(
                ParsingUtils.getObjectFactoryBeanDefinition(getAuditingHandlerBeanName(), registry));

        registerInfrastructureBeanWithId(listenerBeanDefinitionBuilder.getBeanDefinition(),
                AuditingEventListener.class.getName(), registry);
    }

    /**
     * This method no longer creates a default {@link DynamoDBMappingContext} bean.
     * The mapping context should be provided by:
     * 1. @EnableDynamoDBRepositories (which creates it with the appropriate marshalling mode), or
     * 2. A user-defined @Bean
     *
     * This matches the pattern used by Spring Data MongoDB's MongoAuditingRegistrar, which
     * relies on Spring's dependency injection to fail with a clear error if the required
     * mapping context bean is not available.
     *
     * @param registry
     *            the {@link BeanDefinitionRegistry} (unused, kept for method signature compatibility)
     * @param source
     *            the source (unused, kept for method signature compatibility)
     */
    private void defaultDependenciesIfNecessary(BeanDefinitionRegistry registry, Object source) {
        // No longer create a default mapping context bean.
        // The bean reference in getAuditHandlerBeanDefinitionBuilder (line 97) will cause Spring
        // to fail with a clear dependency injection error if no DynamoDBMappingContext bean exists.
        LOGGER.trace("DynamoDBAuditingRegistrar expects DynamoDBMappingContext bean '{}' to be provided by @EnableDynamoDBRepositories or user configuration",
                MAPPING_CONTEXT_BEAN_NAME);
    }
}
