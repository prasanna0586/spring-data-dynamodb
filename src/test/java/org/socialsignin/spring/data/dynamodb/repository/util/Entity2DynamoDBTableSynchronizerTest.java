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
package org.socialsignin.spring.data.dynamodb.repository.util;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.socialsignin.spring.data.dynamodb.mapping.DynamoDBMappingContext;
import org.socialsignin.spring.data.dynamodb.repository.support.DynamoDBEntityInformation;
import org.socialsignin.spring.data.dynamodb.repository.support.SimpleDynamoDBCrudRepository;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.ContextStoppedEvent;
import org.springframework.data.repository.core.RepositoryInformation;
import org.socialsignin.spring.data.dynamodb.domain.sample.SimpleTestEntity;

import org.mockito.MockedStatic;

import static org.mockito.Mockito.*;
import static org.socialsignin.spring.data.dynamodb.core.MarshallingMode.SDK_V2_NATIVE;

@ExtendWith(MockitoExtension.class)
public class Entity2DynamoDBTableSynchronizerTest<T, ID> {

    private Entity2DynamoDBTableSynchronizer<T, ID> underTest;
    private MockedStatic<software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter> waiterMock;
    private software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable<?> mockTable;
    @Mock
    private DynamoDbClient amazonDynamoDB;
    @Mock
    private DynamoDbEnhancedClient enhancedClient;
    @Mock
    private DynamoDBMappingContext mappingContext;
    @Mock
    private software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter waiter;
    @Mock
    private ProxyFactory factory;
    @Mock
    private RepositoryInformation repositoryInformation;
    @Mock
    private ApplicationContext applicationContext;
    @Mock
    private SimpleDynamoDBCrudRepository<T, ID> repository;
    @Mock
    private DynamoDBEntityInformation<T, ID> entityInformation;

    @BeforeEach
    public void setUp() throws Exception {
        TargetSource targetSource = mock(TargetSource.class);
        when(targetSource.getTarget()).thenReturn(repository);
        when(factory.getTargetSource()).thenReturn(targetSource);

        when(repository.getEntityInformation()).thenReturn(entityInformation);

        when(entityInformation.getDynamoDBTableName()).thenReturn("tableName");
        // Use lenient() since not all tests trigger table creation
        lenient().when(entityInformation.getJavaType()).thenReturn((Class) SimpleTestEntity.class);

        // Mock marshalling mode - use lenient() since not all tests trigger table creation
        lenient().when(mappingContext.getMarshallingMode()).thenReturn(SDK_V2_NATIVE);
    }

    @AfterEach
    public void tearDown() {
        if (waiterMock != null) {
            waiterMock.close();
        }
    }

    private void setupCreateTableMocks() {
        // Mock Enhanced Client table operations
        mockTable = mock(software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable.class);
        when(enhancedClient.table(anyString(), any(software.amazon.awssdk.enhanced.dynamodb.TableSchema.class))).thenReturn(mockTable);

        // Mock the waiter static builder
        software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter mockWaiter = mock(software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter.class);
        software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter.Builder mockWaiterBuilder = mock(software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter.Builder.class);

        waiterMock = mockStatic(software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter.class);
        waiterMock.when(software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter::builder).thenReturn(mockWaiterBuilder);

        when(mockWaiterBuilder.client(any(DynamoDbClient.class))).thenReturn(mockWaiterBuilder);
        when(mockWaiterBuilder.build()).thenReturn(mockWaiter);

        // The waiter is auto-closeable, so it calls close()
        lenient().doNothing().when(mockWaiter).close();

        // Mock the wait response (waiter.waitUntilTableExists)
        software.amazon.awssdk.core.waiters.WaiterResponse mockWaiterResponse = mock(software.amazon.awssdk.core.waiters.WaiterResponse.class);
        when(mockWaiter.waitUntilTableExists(any(DescribeTableRequest.class))).thenReturn(mockWaiterResponse);
    }

    private void setupDeleteTableMocks() {
        // First delete (on start) throws ResourceNotFoundException because table doesn't exist
        // Second delete (on shutdown) succeeds
        when(amazonDynamoDB.deleteTable(any(DeleteTableRequest.class)))
                .thenThrow(ResourceNotFoundException.builder().message("Table not found").build())
                .thenReturn(DeleteTableResponse.builder().build());
    }

    public void setUp(Entity2DDL mode) {
        underTest = new Entity2DynamoDBTableSynchronizer<>(amazonDynamoDB, enhancedClient, mappingContext, mode);
        underTest.postProcess(factory, repositoryInformation);
    }

    public void runContextStart() {
        underTest.onApplicationEvent(new ContextRefreshedEvent(applicationContext));
    }

    public void runContextStop() {
        underTest.onApplicationEvent(new ContextStoppedEvent(applicationContext));
    }

    @Test
    public void testUnmatchedEvent() {
        setUp(Entity2DDL.NONE);

        underTest.onApplicationEvent(new ContextClosedEvent(applicationContext));

        verify(factory).getTargetSource();
        verifyNoMoreInteractions(amazonDynamoDB, factory, repositoryInformation, applicationContext);
    }

    @Test
    public void testNone() {
        setUp(Entity2DDL.NONE);

        runContextStart();

        runContextStop();

        verify(factory).getTargetSource();
        verifyNoMoreInteractions(amazonDynamoDB, factory, repositoryInformation, applicationContext);

    }

    @Test
    public void testCreate() {
        setupCreateTableMocks();
        setupDeleteTableMocks();
        setUp(Entity2DDL.CREATE);

        runContextStart();
        // With Enhanced Client migration, verify Enhanced Client interactions
        verify(enhancedClient).table(eq("tableName"), any(software.amazon.awssdk.enhanced.dynamodb.TableSchema.class));
        verify(mockTable).createTable(any(java.util.function.Consumer.class));
        // Note: First deleteTable on startup doesn't actually call amazonDynamoDB.deleteTable()
        // because the table doesn't exist and ResourceNotFoundException is caught

        runContextStop();
        // Verify deleteTable was called on shutdown
        verify(amazonDynamoDB).deleteTable(any(DeleteTableRequest.class));
    }

    @Test
    public void testDrop() {
        setupCreateTableMocks();
        setupDeleteTableMocks();
        setUp(Entity2DDL.DROP);

        runContextStart();

        runContextStop();
    }

    @Test
    public void testCreateOnly() {
        setupCreateTableMocks();
        setUp(Entity2DDL.CREATE_ONLY);

        runContextStart();
        // With Enhanced Client migration, verify Enhanced Client interactions
        verify(enhancedClient).table(eq("tableName"), any(software.amazon.awssdk.enhanced.dynamodb.TableSchema.class));
        verify(mockTable).createTable(any(java.util.function.Consumer.class));

        runContextStop();
        // CREATE_ONLY mode should not delete on shutdown
        verify(amazonDynamoDB, never()).deleteTable(any(DeleteTableRequest.class));
    }

    @Test
    public void testCreateDrop() {
        setupCreateTableMocks();
        setupDeleteTableMocks();
        setUp(Entity2DDL.CREATE_DROP);

        runContextStart();

        runContextStop();
    }
}
