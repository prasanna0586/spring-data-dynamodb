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

import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;
import org.socialsignin.spring.data.dynamodb.core.DynamoDBTemplate;
import org.socialsignin.spring.data.dynamodb.repository.support.DynamoDBRepositoryFactory;
import org.springframework.data.repository.cdi.CdiRepositoryBean;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.lang.annotation.Annotation;
import java.util.Set;

/**
 * A bean which represents a DynamoDB repository.
 *
 * <p>Migrated to AWS SDK v2. Uses DynamoDbClient and DynamoDbEnhancedClient
 * instead of SDK v1's AmazonDynamoDB and DynamoDBMapper.
 * @author Prasanna Kumar Ramachandran
 * @param <T>
 *            The type of the repository.
 * @since 7.0.0
 */
class DynamoDBRepositoryBean<T> extends CdiRepositoryBean<T> {
    @Nullable
    private final Bean<DynamoDbClient> dynamoDbClientBean;

    @Nullable
    private final Bean<DynamoDbEnhancedClient> enhancedClientBean;

    @Nullable
    private final Bean<DynamoDBOperations> dynamoDBOperationsBean;

    /**
     * Constructs a {@link DynamoDBRepositoryBean}.
     * @param beanManager
     *            must not be {@literal null}.
     * @param dynamoDbClientBean
     *            must not be {@literal null} if dynamoDBOperationsBean is null.
     * @param enhancedClientBean
     *            must not be {@literal null} if dynamoDBOperationsBean is null.
     * @param dynamoDBOperationsBean
     *            can be {@literal null}.
     * @param qualifiers
     *            must not be {@literal null}.
     * @param repositoryType
     *            must not be {@literal null}.
     */
    DynamoDBRepositoryBean(@NonNull BeanManager beanManager, @Nullable Bean<DynamoDbClient> dynamoDbClientBean,
                           @Nullable Bean<DynamoDbEnhancedClient> enhancedClientBean, @Nullable Bean<DynamoDBOperations> dynamoDBOperationsBean,
                           @NonNull Set<Annotation> qualifiers, @NonNull Class<T> repositoryType) {

        super(qualifiers, repositoryType, beanManager);
        if (dynamoDBOperationsBean == null) {
            Assert.notNull(dynamoDbClientBean, "dynamoDbClientBean must not be null!");
            Assert.notNull(enhancedClientBean, "enhancedClientBean must not be null!");
        } else {
            Assert.isNull(dynamoDbClientBean,
                    "Cannot specify both dynamoDbClient bean and dynamoDBOperationsBean in repository configuration");
            Assert.isNull(enhancedClientBean,
                    "Cannot specify both enhancedClient bean and dynamoDBOperationsBean in repository configuration");
        }
        this.dynamoDbClientBean = dynamoDbClientBean;
        this.enhancedClientBean = enhancedClientBean;
        this.dynamoDBOperationsBean = dynamoDBOperationsBean;
    }

    /*
     * (non-Javadoc)
     * @see jakarta.enterprise.context.spi.Contextual#create(jakarta.enterprise.context.spi.CreationalContext, Class, java.util.Optional)
     */
    @NonNull
    @Override
    protected T create(@NonNull CreationalContext<T> creationalContext, @NonNull Class<T> repositoryType) {
        // Get DynamoDBOperations if provided directly
        DynamoDBOperations dynamoDBOperations = dynamoDBOperationsBean == null ? null
                : getDependencyInstance(dynamoDBOperationsBean, DynamoDBOperations.class);

        // If DynamoDBOperations is not provided, create it from DynamoDbClient and DynamoDbEnhancedClient
        if (dynamoDBOperations == null) {
            // Constructor guarantees these beans are non-null if dynamoDBOperationsBean is null
            Assert.notNull(dynamoDbClientBean, "dynamoDbClientBean must not be null");
            Assert.notNull(enhancedClientBean, "enhancedClientBean must not be null");

            DynamoDbClient dynamoDbClient = getDependencyInstance(dynamoDbClientBean, DynamoDbClient.class);
            DynamoDbEnhancedClient enhancedClient = getDependencyInstance(enhancedClientBean, DynamoDbEnhancedClient.class);

            // Create DynamoDBTemplate with SDK v2 clients
            dynamoDBOperations = new DynamoDBTemplate(dynamoDbClient, enhancedClient, null, null);
        }

        // Create repository factory and get the repository
        DynamoDBRepositoryFactory factory = new DynamoDBRepositoryFactory(dynamoDBOperations);
        return factory.getRepository(repositoryType);
    }
}
