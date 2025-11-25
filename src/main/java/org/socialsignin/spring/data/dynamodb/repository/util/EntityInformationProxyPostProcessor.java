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
package org.socialsignin.spring.data.dynamodb.repository.util;

import org.socialsignin.spring.data.dynamodb.repository.support.DynamoDBEntityInformation;
import org.socialsignin.spring.data.dynamodb.repository.support.SimpleDynamoDBCrudRepository;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.support.RepositoryProxyPostProcessor;
import org.springframework.lang.NonNull;

/**
 * Base class for repository proxy post processors that need access to entity information.
 * @param <T> the entity type
 * @param <ID> the entity ID type
 */
public abstract class EntityInformationProxyPostProcessor<T, ID> implements RepositoryProxyPostProcessor {

    /**
     * Default constructor for subclasses.
     */
    protected EntityInformationProxyPostProcessor() {
    }

    /**
     * Callback method invoked when an entity is registered.
     *
     * @param entityInformation the entity information for the registered entity
     */
    protected abstract void registeredEntity(DynamoDBEntityInformation<T, ID> entityInformation);

    @Override
    public final void postProcess(@NonNull ProxyFactory factory, @NonNull RepositoryInformation repositoryInformation) {
        try {
            TargetSource targetSource = factory.getTargetSource();

            @SuppressWarnings("unchecked")
            SimpleDynamoDBCrudRepository<T, ID> target = (SimpleDynamoDBCrudRepository<T, ID>) targetSource.getTarget();

            assert target != null;
            DynamoDBEntityInformation<T, ID> entityInformation = target.getEntityInformation();
            registeredEntity(entityInformation);

        } catch (Exception e) {
            throw new RuntimeException("Could not extract SimpleDynamoDBCrudRepository from " + factory, e);
        }
    }

}
