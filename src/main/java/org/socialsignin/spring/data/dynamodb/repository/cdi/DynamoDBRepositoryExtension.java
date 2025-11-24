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
package org.socialsignin.spring.data.dynamodb.repository.cdi;

import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.UnsatisfiedResolutionException;
import jakarta.enterprise.inject.spi.AfterBeanDiscovery;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import jakarta.enterprise.inject.spi.ProcessBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;
import org.springframework.data.repository.cdi.CdiRepositoryExtensionSupport;
import org.springframework.lang.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * A portable CDI extension which registers beans for Spring Data DynamoDB repositories.
 * @author Prasanna Kumar Ramachandran
 */
public class DynamoDBRepositoryExtension extends CdiRepositoryExtensionSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamoDBRepositoryExtension.class);

    private final Map<Set<Annotation>, Bean<DynamoDbClient>> amazonDynamoDBs = new HashMap<>();

    private final Map<Set<Annotation>, Bean<DynamoDBOperations>> dynamoDBOperations = new HashMap<>();

    private final Map<Set<Annotation>, Bean<DynamoDbEnhancedClient>> enhancedClients = new HashMap<>();

    public DynamoDBRepositoryExtension() {
        LOGGER.info("Activating CDI extension for Spring Data DynamoDB repositories.");
    }

    /**
     * Implementation of a observer which checks for DynamoDbClient beans and stores them in
     * {@link #amazonDynamoDBs} for later association with corresponding repository beans.
     * @param <X>
     *            The type.
     * @param processBean
     *            The process bean event as defined by CDI.
     */
    @SuppressWarnings("unchecked")
    <X> void processBean(@NonNull @Observes ProcessBean<X> processBean) {
        Bean<X> bean = processBean.getBean();
        for (Type type : bean.getTypes()) {
            // Check if the bean is a DynamoDbClient
            if (type instanceof Class<?> && DynamoDbClient.class.isAssignableFrom((Class<?>) type)) {
                Set<Annotation> qualifiers = new HashSet<>(bean.getQualifiers());
                if (bean.isAlternative() || !amazonDynamoDBs.containsKey(qualifiers)) {
                    logDiscoveredBean(DynamoDbClient.class, qualifiers);
                    amazonDynamoDBs.put(qualifiers, (Bean<DynamoDbClient>) bean);
                }
            }
            // Check if the bean is a DynamoDbEnhancedClient
            if (type instanceof Class<?> && DynamoDbEnhancedClient.class.isAssignableFrom((Class<?>) type)) {
                Set<Annotation> qualifiers = new HashSet<>(bean.getQualifiers());
                if (bean.isAlternative() || !enhancedClients.containsKey(qualifiers)) {
                    logDiscoveredBean(DynamoDbEnhancedClient.class, qualifiers);
                    enhancedClients.put(qualifiers, (Bean<DynamoDbEnhancedClient>) bean);
                }
            }
            // Check if the bean is a DynamoDBOperations
            if (type instanceof Class<?> && DynamoDBOperations.class.isAssignableFrom((Class<?>) type)) {
                Set<Annotation> qualifiers = new HashSet<>(bean.getQualifiers());
                if (bean.isAlternative() || !dynamoDBOperations.containsKey(qualifiers)) {
                    logDiscoveredBean(DynamoDBOperations.class, qualifiers);
                    dynamoDBOperations.put(qualifiers, (Bean<DynamoDBOperations>) bean);
                }
            }
        }
    }

    private void logDiscoveredBean(@NonNull Class<?> beanClass, Set<Annotation> qualifiers) {
        LOGGER.debug("Discovered '{}' with qualifiers {}.", beanClass.getName(), qualifiers);
    }

    /**
     * Implementation of a observer which registers beans to the CDI container for the detected Spring Data
     * repositories.
     * <p>
     * The repository beans are associated to the EntityManagers using their qualifiers.
     * @param beanManager
     *            The BeanManager instance.
     */
    void afterBeanDiscovery(@NonNull @Observes AfterBeanDiscovery afterBeanDiscovery, @NonNull BeanManager beanManager) {

        for (Entry<Class<?>, Set<Annotation>> entry : getRepositoryTypes()) {

            Class<?> repositoryType = entry.getKey();
            Set<Annotation> qualifiers = entry.getValue();
            // Create the bean representing the repository.
            Bean<?> repositoryBean = createRepositoryBean(repositoryType, qualifiers, beanManager);
            LOGGER.info("Registering bean for '{}' with qualifiers {}.", repositoryType.getName(), qualifiers);
            // Register the bean to the container.
            afterBeanDiscovery.addBean(repositoryBean);
        }
    }

    /**
     * Creates a {@link Bean}.
     * @param <T>
     *            The type of the repository.
     * @param repositoryType
     *            The class representing the repository.
     * @param beanManager
     *            The BeanManager instance.
     * @return The bean.
     */
    @NonNull
    private <T> Bean<T> createRepositoryBean(@NonNull Class<T> repositoryType, @NonNull Set<Annotation> qualifiers,
                                             @NonNull BeanManager beanManager) {

        // Determine the amazondbclient bean which matches the qualifiers of the
        // repository.
        Bean<DynamoDbClient> amazonDynamoDBBean = amazonDynamoDBs.get(qualifiers);

        Bean<DynamoDbEnhancedClient> enhancedClientBean = enhancedClients.get(qualifiers);

        Bean<DynamoDBOperations> dynamoDBOperationsBean = dynamoDBOperations.get(qualifiers);
        if (amazonDynamoDBBean == null) {
            throw new UnsatisfiedResolutionException(
                    String.format("Unable to resolve a bean for '%s' with qualifiers %s.",
                            DynamoDbClient.class.getName(), qualifiers));
        }

        // Construct and return the repository bean.
        return new DynamoDBRepositoryBean<>(beanManager, amazonDynamoDBBean, enhancedClientBean,
                dynamoDBOperationsBean, qualifiers, repositoryType);
    }
}
