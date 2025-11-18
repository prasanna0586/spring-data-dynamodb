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
        // Mock createTable response
        CreateTableResponse createResponse = CreateTableResponse.builder().build();
        when(amazonDynamoDB.createTable(any(CreateTableRequest.class))).thenReturn(createResponse);

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

        // When - Start context (should drop if exists, then create)
        runContextStart();

        // Then - Verify table creation on startup
        verify(amazonDynamoDB).createTable(any(CreateTableRequest.class));
        // Note: First deleteTable on startup doesn't actually call amazonDynamoDB.deleteTable()
        // because the table doesn't exist and ResourceNotFoundException is caught

        // When - Stop context (should delete table)
        runContextStop();

        // Then - Verify table deletion on shutdown
        verify(amazonDynamoDB).deleteTable(any(DeleteTableRequest.class));
        // CREATE mode should NOT recreate on shutdown
        verify(amazonDynamoDB, times(1)).createTable(any(CreateTableRequest.class));
    }

    @Test
    public void testDrop() {
        setupCreateTableMocks();
        setupDeleteTableMocks();
        setUp(Entity2DDL.DROP);

        // When - Start context (DROP mode does nothing on start)
        runContextStart();

        // Then - Verify no operations on startup
        verify(amazonDynamoDB, never()).createTable(any(CreateTableRequest.class));
        verify(amazonDynamoDB, never()).deleteTable(any(DeleteTableRequest.class));

        // When - Stop context (DROP mode drops and recreates on shutdown)
        runContextStop();

        // Then - Verify drop and recreate on shutdown
        verify(amazonDynamoDB).deleteTable(any(DeleteTableRequest.class));
        verify(amazonDynamoDB).createTable(any(CreateTableRequest.class));
    }

    @Test
    public void testCreateOnly() {
        setupCreateTableMocks();
        setUp(Entity2DDL.CREATE_ONLY);

        // When - Start context (CREATE_ONLY creates table)
        runContextStart();

        // Then - Verify table creation on startup
        verify(amazonDynamoDB).createTable(any(CreateTableRequest.class));

        // When - Stop context (CREATE_ONLY should NOT delete on shutdown)
        runContextStop();

        // Then - CREATE_ONLY mode should not delete on shutdown
        verify(amazonDynamoDB, never()).deleteTable(any(DeleteTableRequest.class));
    }

    @Test
    public void testCreateDrop() {
        setupCreateTableMocks();
        setupDeleteTableMocks();
        setUp(Entity2DDL.CREATE_DROP);

        // When - Start context (CREATE_DROP drops if exists, then creates)
        runContextStart();

        // Then - Verify table creation on startup
        verify(amazonDynamoDB).createTable(any(CreateTableRequest.class));
        // Note: First deleteTable on startup throws ResourceNotFoundException (caught), then succeeds on shutdown

        // When - Stop context (CREATE_DROP drops and recreates on shutdown)
        runContextStop();

        // Then - Verify drop and recreate on shutdown
        // deleteTable is called twice: once on startup (throws exception, caught), once on shutdown (succeeds)
        verify(amazonDynamoDB, times(2)).deleteTable(any(DeleteTableRequest.class));
        // Verify createTable was called twice: once on startup, once on shutdown
        verify(amazonDynamoDB, times(2)).createTable(any(CreateTableRequest.class));
    }

    @Test
    public void testValidate() {
        setUp(Entity2DDL.VALIDATE);

        // Mock describeTable to return a matching table description
        TableDescription tableDescription = TableDescription.builder()
                .tableName("tableName")
                .tableStatus(TableStatus.ACTIVE)
                .keySchema(
                        KeySchemaElement.builder()
                                .attributeName("id")
                                .keyType(KeyType.HASH)
                                .build()
                )
                .attributeDefinitions(
                        AttributeDefinition.builder()
                                .attributeName("id")
                                .attributeType(ScalarAttributeType.S)
                                .build()
                )
                .build();

        DescribeTableResponse describeResponse = DescribeTableResponse.builder()
                .table(tableDescription)
                .build();

        when(amazonDynamoDB.describeTable(any(DescribeTableRequest.class))).thenReturn(describeResponse);

        // When - Start context (VALIDATE mode validates table exists and matches schema)
        runContextStart();

        // Then - Verify describeTable was called to validate
        verify(amazonDynamoDB).describeTable(any(DescribeTableRequest.class));
        // VALIDATE mode should not create or delete tables
        verify(amazonDynamoDB, never()).createTable(any(CreateTableRequest.class));
        verify(amazonDynamoDB, never()).deleteTable(any(DeleteTableRequest.class));

        // When - Stop context (VALIDATE mode does nothing on stop)
        runContextStop();

        // Then - Verify no operations on shutdown (only the one describeTable from startup)
        verify(amazonDynamoDB, times(1)).describeTable(any(DescribeTableRequest.class));
    }
}
