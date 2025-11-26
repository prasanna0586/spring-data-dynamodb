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
package org.socialsignin.spring.data.dynamodb.repository.cdi;

import jakarta.enterprise.context.spi.CreationalContext;
import jakarta.enterprise.inject.spi.Bean;
import jakarta.enterprise.inject.spi.BeanManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;
import org.socialsignin.spring.data.dynamodb.domain.sample.User;
import org.springframework.data.repository.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DynamoDBRepositoryBeanTest {
    interface SampleRepository extends Repository<User, String> {
    }

    @Mock
    private CreationalContext<DynamoDbClient> creationalContext;
    @Mock
    private CreationalContext<SampleRepository> repoCreationalContext;
    @Mock
    private BeanManager beanManager;
    @Mock
    private Bean<DynamoDbClient> dynamoDbClientBean;
    @Mock
    private DynamoDbClient dynamoDbClient;
    @Mock
    private Bean<DynamoDbEnhancedClient> enhancedClientBean;
    @Mock
    private DynamoDbEnhancedClient enhancedClient;
    @Mock
    private Bean<DynamoDBOperations> dynamoDBOperationsBean;

    private Set<Annotation> qualifiers = Collections.emptySet();
    private Class<SampleRepository> repositoryType = SampleRepository.class;

    @BeforeEach
    public void setUp() {
    }

    private void setupDynamoDBBeanStubs() {
        when(beanManager.createCreationalContext(dynamoDbClientBean)).thenReturn(creationalContext);
        when(beanManager.getReference(eq(dynamoDbClientBean), eq(DynamoDbClient.class), any()))
                .thenReturn(dynamoDbClient);
        when(beanManager.getReference(eq(enhancedClientBean), eq(DynamoDbEnhancedClient.class), any()))
                .thenReturn(enhancedClient);
    }

    @Test
    public void testNullOperationsOk() {
        DynamoDBRepositoryBean<SampleRepository> underTest = new DynamoDBRepositoryBean<>(beanManager,
                dynamoDbClientBean, enhancedClientBean, null, qualifiers, repositoryType);

        assertNotNull(underTest);
    }

    @Test
    public void testNullOperationFail() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new DynamoDBRepositoryBean<>(beanManager, null, enhancedClientBean, null, qualifiers, repositoryType);
        });

        assertTrue(exception.getMessage().contains("dynamoDbClientBean must not be null!"));
    }

    @Test
    public void testSetOperationOk1() {
        DynamoDBRepositoryBean<SampleRepository> underTest = new DynamoDBRepositoryBean<>(beanManager, null, null,
                dynamoDBOperationsBean, qualifiers, repositoryType);

        assertNotNull(underTest);
    }

    @Test
    public void testSetOperationFail1() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new DynamoDBRepositoryBean<>(beanManager, null, enhancedClientBean, dynamoDBOperationsBean,
                    qualifiers, repositoryType);
        });

        assertTrue(exception.getMessage().contains(
                "Cannot specify both enhancedClient bean and dynamoDBOperationsBean in repository configuration"));
    }

    @Test
    public void testSetOperationFail2() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            new DynamoDBRepositoryBean<>(beanManager, dynamoDbClientBean, null, dynamoDBOperationsBean,
                    qualifiers, repositoryType);
        });

        assertTrue(exception.getMessage().contains(
                "Cannot specify both dynamoDbClient bean and dynamoDBOperationsBean in repository configuration"));
    }

    @Test
    public void testCreateRepostiory() {
        setupDynamoDBBeanStubs();

        DynamoDBRepositoryBean<SampleRepository> underTest = new DynamoDBRepositoryBean<>(beanManager,
                dynamoDbClientBean, enhancedClientBean, null, qualifiers, repositoryType);

        SampleRepository actual = underTest.create(repoCreationalContext, SampleRepository.class);
        assertNotNull(actual);
    }
}
