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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socialsignin.spring.data.dynamodb.core.TableSchemaFactory;
import org.socialsignin.spring.data.dynamodb.mapping.DynamoDBMappingContext;
import org.socialsignin.spring.data.dynamodb.repository.support.DynamoDBEntityInformation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationContextEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.ContextStoppedEvent;
import org.springframework.data.repository.core.support.RepositoryProxyPostProcessor;
import org.springframework.util.ReflectionUtils;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This is the base class for all classes performing the validation or auto-creation of tables based on the entity
 * classes. //TODO: It would be nice if the checks would run in parallel via a TaskScheduler (if available)
 *
 * <p>This implementation uses SDK v2 APIs to introspect entity classes and generate CreateTableRequest objects
 * based on the @DynamoDbPartitionKey, @DynamoDbSortKey, @DynamoDbSecondaryPartitionKey annotations.</p>
 *
 * @see Entity2DDL
 */
public class Entity2DynamoDBTableSynchronizer<T, ID> extends EntityInformationProxyPostProcessor<T, ID>
        implements RepositoryProxyPostProcessor, ApplicationListener<ApplicationContextEvent> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Entity2DynamoDBTableSynchronizer.class);

    private static final String CONFIGURATION_KEY_entity2ddl_auto = "${spring.data.dynamodb.entity2ddl.auto:none}";
    private static final String CONFIGURATION_KEY_entity2ddl_gsiProjectionType = "${spring.data.dynamodb.entity2ddl.gsiProjectionType:ALL}";
    private static final String CONFIGURATION_KEY_entity2ddl_lsiProjectionType = "${spring.data.dynamodb.entity2ddl.lsiProjectionType:ALL}";
    private static final String CONFIGURATION_KEY_entity2ddl_readCapacity = "${spring.data.dynamodb.entity2ddl.readCapacity:10}";
    private static final String CONFIGURATION_KEY_entity2ddl_writeCapacity = "${spring.data.dynamodb.entity2ddl.writeCapacity:1}";

    private final DynamoDbClient amazonDynamoDB;
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDBMappingContext mappingContext;

    private final Entity2DDL mode;
    private final ProjectionType gsiProjectionType;
    private final ProjectionType lsiProjectionType;
    private final long readCapacity;
    private final long writeCapacity;

    private final Collection<DynamoDBEntityInformation<T, ID>> registeredEntities = new ArrayList<>();

    public Entity2DynamoDBTableSynchronizer(DynamoDbClient amazonDynamoDB,
            DynamoDbEnhancedClient enhancedClient,
            DynamoDBMappingContext mappingContext,
            Entity2DDL mode) {
        this(amazonDynamoDB, enhancedClient, mappingContext, mode.getConfigurationValue(),
                ProjectionType.ALL.name(), ProjectionType.ALL.name(), 10L, 10L);
    }

    @Autowired
    public Entity2DynamoDBTableSynchronizer(DynamoDbClient amazonDynamoDB,
            @Qualifier("dynamoDbEnhancedClient") DynamoDbEnhancedClient enhancedClient,
            @Qualifier("dynamoDBMappingContext") DynamoDBMappingContext mappingContext,
            @Value(CONFIGURATION_KEY_entity2ddl_auto) String mode,
            @Value(CONFIGURATION_KEY_entity2ddl_gsiProjectionType) String gsiProjectionType,
            @Value(CONFIGURATION_KEY_entity2ddl_lsiProjectionType) String lsiProjectionType,
            @Value(CONFIGURATION_KEY_entity2ddl_readCapacity) long readCapacity,
            @Value(CONFIGURATION_KEY_entity2ddl_writeCapacity) long writeCapacity) {
        this.amazonDynamoDB = amazonDynamoDB;
        this.enhancedClient = enhancedClient;
        this.mappingContext = mappingContext;

        this.mode = Entity2DDL.fromValue(mode);
        this.gsiProjectionType = ProjectionType.fromValue(gsiProjectionType);
        this.lsiProjectionType = ProjectionType.fromValue(lsiProjectionType);
        this.readCapacity = readCapacity;
        this.writeCapacity = writeCapacity;
    }

    @Override
    protected void registeredEntity(DynamoDBEntityInformation<T, ID> entityInformation) {
        this.registeredEntities.add(entityInformation);
    }

    @Override
    public void onApplicationEvent(ApplicationContextEvent event) {
        LOGGER.info("Checking repository classes with DynamoDB tables {} for {}",
                registeredEntities.stream().map(e -> e.getDynamoDBTableName()).collect(Collectors.joining(", ")),
                event.getClass().getSimpleName());

        for (DynamoDBEntityInformation<T, ID> entityInformation : registeredEntities) {

            try {
                synchronize(entityInformation, event);
            } catch (InterruptedException | UnsupportedOperationException e) {
                throw new RuntimeException("Could not perform Entity2DDL operation " + mode + " on "
                        + entityInformation.getDynamoDBTableName(), e);
            }
        }
    }

    protected void synchronize(DynamoDBEntityInformation<T, ID> entityInformation, ApplicationContextEvent event)
            throws InterruptedException {

        if (event instanceof ContextRefreshedEvent) {
            switch (mode) {
                case CREATE_DROP:
                case CREATE:
                    performDrop(entityInformation);
                    // TODO implement wait for deletion
                case CREATE_ONLY:
                    performCreate(entityInformation);
                    break;
                case VALIDATE:
                    performValidate(entityInformation);
                    break;
                case DROP:
                case NONE:
                default:
                    LOGGER.debug("No auto table DDL performed on start");
                    break;
            }
        } else if (event instanceof ContextStoppedEvent) {
            switch (mode) {
                case CREATE_DROP:
                case DROP:
                    performDrop(entityInformation);
                    performCreate(entityInformation);
                    break;

                case CREATE:
                case VALIDATE:
                case NONE:
                default:
                    LOGGER.debug("No auto table DDL performed on stop");
                    break;
            }
        } else {
            LOGGER.trace("Ignored ApplicationContextEvent: {}", event);
        }

    }

    /**
     * Creates a DynamoDB table using the Enhanced Client.
     *
     * <p>Uses AWS SDK v2 Enhanced Client's automatic schema generation from annotations.
     * This approach automatically handles:
     * <ul>
     *   <li>Key schema creation (partition and sort keys)</li>
     *   <li>Attribute definitions</li>
     *   <li>Local Secondary Index (LSI) creation</li>
     *   <li>Global Secondary Index (GSI) creation</li>
     *   <li>Correct key ordering</li>
     * </ul>
     *
     * @param entityInformation Entity metadata
     * @return true if table was created, false if already exists
     * @throws InterruptedException if waiting for table creation is interrupted
     */
    private boolean performCreate(DynamoDBEntityInformation<T, ID> entityInformation)
            throws InterruptedException {
        Class<T> domainType = entityInformation.getJavaType();
        String tableName = entityInformation.getDynamoDBTableName();

        LOGGER.info("Creating table {} for entity {} using Enhanced Client",
                tableName, domainType.getSimpleName());

        try {
            // Create table schema using factory (respects marshalling mode)
            TableSchema<T> schema = TableSchemaFactory.createTableSchema(
                    domainType,
                    mappingContext.getMarshallingMode());

            // Get table reference from Enhanced Client
            DynamoDbTable<T> table = enhancedClient.table(tableName, schema);

            // Create table with configuration for provisioned throughput
            // Note: Since SDK v2.20.86+, indexes are automatically created from annotations
            // with ALL projection type and default throughput (can be customized if needed)
            table.createTable(builder -> builder
                    .provisionedThroughput(b -> b
                            .readCapacityUnits(readCapacity)
                            .writeCapacityUnits(writeCapacity)));

            LOGGER.info("Table {} created successfully for entity {}",
                    tableName, domainType.getSimpleName());

            // Wait for table to become active
            try (DynamoDbWaiter waiter = DynamoDbWaiter.builder().client(amazonDynamoDB).build()) {
                DescribeTableRequest describeRequest = DescribeTableRequest.builder()
                        .tableName(tableName)
                        .build();
                waiter.waitUntilTableExists(describeRequest);
                LOGGER.debug("Table {} is now active", tableName);
            }

            return true;

        } catch (ResourceInUseException e) {
            LOGGER.debug("Table {} already exists", tableName);
            return false;
        } catch (DynamoDbException e) {
            LOGGER.error("Failed to create table {} for entity {}. Error: {}",
                    tableName, domainType.getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }

    private boolean performDrop(DynamoDBEntityInformation<T, ID> entityInformation) {
        Class<T> domainType = entityInformation.getJavaType();
        String tableName = entityInformation.getDynamoDBTableName();

        LOGGER.trace("Dropping table {} for entity {}", tableName, domainType);

        try {
            DeleteTableRequest dtr = DeleteTableRequest.builder()
                    .tableName(tableName)
                    .build();
            amazonDynamoDB.deleteTable(dtr);
            LOGGER.debug("Deleted table {} for entity {}", tableName, domainType);
            return true;
        } catch (ResourceNotFoundException e) {
            LOGGER.debug("Table {} does not exist", tableName);
            return false;
        }
    }

    /**
     * @param entityInformation
     *            The entity to check for it's table
     *
     * @throws IllegalStateException
     *             is thrown if the existing table doesn't match the entity's annotation
     */
    private DescribeTableResponse performValidate(DynamoDBEntityInformation<T, ID> entityInformation)
            throws IllegalStateException {
        Class<T> domainType = entityInformation.getJavaType();

        CreateTableRequest expected = generateCreateTableRequest(domainType);
        DescribeTableResponse result = amazonDynamoDB.describeTable(DescribeTableRequest.builder()
                .tableName(expected.tableName())
                .build());
        TableDescription actual = result.table();

        if (!expected.keySchema().equals(actual.keySchema())) {
            throw new IllegalStateException("KeySchema is not as expected. Expected: <" + expected.keySchema()
                    + "> but found <" + actual.keySchema() + ">");
        }
        LOGGER.debug("KeySchema is valid");

        if (expected.globalSecondaryIndexes() != null) {
            if (!compareGSI(expected.globalSecondaryIndexes(), actual.globalSecondaryIndexes())) {
                throw new IllegalStateException("Global Secondary Indexes are not as expected. Expected: <"
                        + expected.globalSecondaryIndexes() + "> but found <" + actual.globalSecondaryIndexes()
                        + ">");
            }
        }
        LOGGER.debug("Global Secondary Indexes are valid");

        LOGGER.info("Validated table {} for entity {}", expected.tableName(), domainType);
        return result;
    }

    private boolean compareGSI(List<GlobalSecondaryIndex> expected, List<GlobalSecondaryIndexDescription> actual) {
        if (expected.size() != actual.size()) {
            return false;
        }

        Map<String, GlobalSecondaryIndex> expectedMap = expected.stream()
                .collect(Collectors.toMap(GlobalSecondaryIndex::indexName, gsi -> gsi));
        Map<String, GlobalSecondaryIndexDescription> actualMap = actual.stream()
                .collect(Collectors.toMap(GlobalSecondaryIndexDescription::indexName, gsi -> gsi));

        for (String indexName : expectedMap.keySet()) {
            if (!actualMap.containsKey(indexName)) {
                return false;
            }
            GlobalSecondaryIndex exp = expectedMap.get(indexName);
            GlobalSecondaryIndexDescription act = actualMap.get(indexName);
            if (!exp.keySchema().equals(act.keySchema())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Generates a CreateTableRequest by introspecting the entity class annotations.
     * This replicates the functionality of SDK v1's DynamoDBMapper.generateCreateTableRequest().
     */
    private CreateTableRequest generateCreateTableRequest(Class<T> domainType) {
        String tableName = getTableName(domainType);

        // Collect all attribute definitions and key schema
        List<AttributeDefinition> attributeDefinitions = new ArrayList<>();
        List<KeySchemaElement> keySchema = new ArrayList<>();
        Map<String, ScalarAttributeType> attributeTypes = new HashMap<>();

        // Find partition key and sort key
        findPartitionKey(domainType, keySchema, attributeTypes);
        findSortKey(domainType, keySchema, attributeTypes);

        // SDK v2 REQUIRES Hash Key BEFORE Range Key in keySchema list
        sortKeySchemaElements(keySchema);

        // Find GSIs
        List<GlobalSecondaryIndex> globalSecondaryIndexes = findGlobalSecondaryIndexes(domainType, attributeTypes);

        // Find LSIs
        List<LocalSecondaryIndex> localSecondaryIndexes = findLocalSecondaryIndexes(domainType, attributeTypes);

        // Build attribute definitions from collected types
        attributeTypes.forEach((name, type) ->
            attributeDefinitions.add(AttributeDefinition.builder()
                    .attributeName(name)
                    .attributeType(type)
                    .build())
        );

        CreateTableRequest.Builder builder = CreateTableRequest.builder()
                .tableName(tableName)
                .keySchema(keySchema)
                .attributeDefinitions(attributeDefinitions)
                .provisionedThroughput(ProvisionedThroughput.builder()
                        .readCapacityUnits(readCapacity)
                        .writeCapacityUnits(writeCapacity)
                        .build());

        if (!globalSecondaryIndexes.isEmpty()) {
            builder.globalSecondaryIndexes(globalSecondaryIndexes);
        }

        if (!localSecondaryIndexes.isEmpty()) {
            builder.localSecondaryIndexes(localSecondaryIndexes);
        }

        return builder.build();
    }

    private String getTableName(Class<T> domainType) {
        // SDK v2's @DynamoDbBean doesn't have a tableName property
        // Table name is determined by the class name (simple name)
        // For custom table names, users should use TableNameResolver
        return domainType.getSimpleName();
    }

    // Note: The following introspection methods are kept for performValidate() but are deprecated.
    // TODO: Consider using Enhanced Client's TableSchema for validation in a future update.

    private void findPartitionKey(Class<T> domainType, List<KeySchemaElement> keySchema,
                                   Map<String, ScalarAttributeType> attributeTypes) {
        ReflectionUtils.doWithMethods(domainType, method -> {
            if (method.isAnnotationPresent(DynamoDbPartitionKey.class)) {
                String attributeName = getAttributeName(method);
                keySchema.add(KeySchemaElement.builder()
                        .attributeName(attributeName)
                        .keyType(KeyType.HASH)
                        .build());
                attributeTypes.put(attributeName, getScalarType(method.getReturnType()));
            }
        });

        ReflectionUtils.doWithFields(domainType, field -> {
            if (field.isAnnotationPresent(DynamoDbPartitionKey.class)) {
                String attributeName = getAttributeName(field);
                keySchema.add(KeySchemaElement.builder()
                        .attributeName(attributeName)
                        .keyType(KeyType.HASH)
                        .build());
                attributeTypes.put(attributeName, getScalarType(field.getType()));
            }
        });
    }

    private void findSortKey(Class<T> domainType, List<KeySchemaElement> keySchema,
                             Map<String, ScalarAttributeType> attributeTypes) {
        ReflectionUtils.doWithMethods(domainType, method -> {
            if (method.isAnnotationPresent(DynamoDbSortKey.class)) {
                String attributeName = getAttributeName(method);
                keySchema.add(KeySchemaElement.builder()
                        .attributeName(attributeName)
                        .keyType(KeyType.RANGE)
                        .build());
                attributeTypes.put(attributeName, getScalarType(method.getReturnType()));
            }
        });

        ReflectionUtils.doWithFields(domainType, field -> {
            if (field.isAnnotationPresent(DynamoDbSortKey.class)) {
                String attributeName = getAttributeName(field);
                keySchema.add(KeySchemaElement.builder()
                        .attributeName(attributeName)
                        .keyType(KeyType.RANGE)
                        .build());
                attributeTypes.put(attributeName, getScalarType(field.getType()));
            }
        });
    }

    private List<GlobalSecondaryIndex> findGlobalSecondaryIndexes(Class<T> domainType,
                                                                    Map<String, ScalarAttributeType> attributeTypes) {
        Map<String, GlobalSecondaryIndex.Builder> gsiBuilders = new HashMap<>();

        // Find all GSI partition keys and sort keys
        ReflectionUtils.doWithMethods(domainType, method -> {
            if (method.isAnnotationPresent(DynamoDbSecondaryPartitionKey.class)) {
                for (DynamoDbSecondaryPartitionKey annotation : method.getAnnotationsByType(DynamoDbSecondaryPartitionKey.class)) {
                    for (String indexName : annotation.indexNames()) {
                        String attributeName = getAttributeName(method);
                        attributeTypes.put(attributeName, getScalarType(method.getReturnType()));

                        GlobalSecondaryIndex.Builder gsiBuilder = gsiBuilders.computeIfAbsent(indexName,
                                k -> GlobalSecondaryIndex.builder()
                                        .indexName(indexName)
                                        .projection(Projection.builder().projectionType(gsiProjectionType).build())
                                        .provisionedThroughput(ProvisionedThroughput.builder()
                                                .readCapacityUnits(readCapacity)
                                                .writeCapacityUnits(writeCapacity)
                                                .build()));

                        List<KeySchemaElement> keySchema = new ArrayList<>(gsiBuilder.build().keySchema());
                        keySchema.add(KeySchemaElement.builder()
                                .attributeName(attributeName)
                                .keyType(KeyType.HASH)
                                .build());
                        gsiBuilder.keySchema(keySchema);
                        gsiBuilders.put(indexName, gsiBuilder);
                    }
                }
            }

            if (method.isAnnotationPresent(DynamoDbSecondarySortKey.class)) {
                for (DynamoDbSecondarySortKey annotation : method.getAnnotationsByType(DynamoDbSecondarySortKey.class)) {
                    for (String indexName : annotation.indexNames()) {
                        String attributeName = getAttributeName(method);
                        attributeTypes.put(attributeName, getScalarType(method.getReturnType()));

                        GlobalSecondaryIndex.Builder gsiBuilder = gsiBuilders.computeIfAbsent(indexName,
                                k -> GlobalSecondaryIndex.builder()
                                        .indexName(indexName)
                                        .projection(Projection.builder().projectionType(gsiProjectionType).build())
                                        .provisionedThroughput(ProvisionedThroughput.builder()
                                                .readCapacityUnits(readCapacity)
                                                .writeCapacityUnits(writeCapacity)
                                                .build()));

                        List<KeySchemaElement> keySchema = new ArrayList<>(gsiBuilder.build().keySchema());
                        keySchema.add(KeySchemaElement.builder()
                                .attributeName(attributeName)
                                .keyType(KeyType.RANGE)
                                .build());
                        gsiBuilder.keySchema(keySchema);
                        gsiBuilders.put(indexName, gsiBuilder);
                    }
                }
            }
        });

        // Same for fields
        ReflectionUtils.doWithFields(domainType, field -> {
            if (field.isAnnotationPresent(DynamoDbSecondaryPartitionKey.class)) {
                for (DynamoDbSecondaryPartitionKey annotation : field.getAnnotationsByType(DynamoDbSecondaryPartitionKey.class)) {
                    for (String indexName : annotation.indexNames()) {
                        String attributeName = getAttributeName(field);
                        attributeTypes.put(attributeName, getScalarType(field.getType()));

                        GlobalSecondaryIndex.Builder gsiBuilder = gsiBuilders.computeIfAbsent(indexName,
                                k -> GlobalSecondaryIndex.builder()
                                        .indexName(indexName)
                                        .projection(Projection.builder().projectionType(gsiProjectionType).build())
                                        .provisionedThroughput(ProvisionedThroughput.builder()
                                                .readCapacityUnits(readCapacity)
                                                .writeCapacityUnits(writeCapacity)
                                                .build()));

                        List<KeySchemaElement> keySchema = new ArrayList<>(gsiBuilder.build().keySchema());
                        keySchema.add(KeySchemaElement.builder()
                                .attributeName(attributeName)
                                .keyType(KeyType.HASH)
                                .build());
                        gsiBuilder.keySchema(keySchema);
                        gsiBuilders.put(indexName, gsiBuilder);
                    }
                }
            }

            if (field.isAnnotationPresent(DynamoDbSecondarySortKey.class)) {
                for (DynamoDbSecondarySortKey annotation : field.getAnnotationsByType(DynamoDbSecondarySortKey.class)) {
                    for (String indexName : annotation.indexNames()) {
                        String attributeName = getAttributeName(field);
                        attributeTypes.put(attributeName, getScalarType(field.getType()));

                        GlobalSecondaryIndex.Builder gsiBuilder = gsiBuilders.computeIfAbsent(indexName,
                                k -> GlobalSecondaryIndex.builder()
                                        .indexName(indexName)
                                        .projection(Projection.builder().projectionType(gsiProjectionType).build())
                                        .provisionedThroughput(ProvisionedThroughput.builder()
                                                .readCapacityUnits(readCapacity)
                                                .writeCapacityUnits(writeCapacity)
                                                .build()));

                        List<KeySchemaElement> keySchema = new ArrayList<>(gsiBuilder.build().keySchema());
                        keySchema.add(KeySchemaElement.builder()
                                .attributeName(attributeName)
                                .keyType(KeyType.RANGE)
                                .build());
                        gsiBuilder.keySchema(keySchema);
                        gsiBuilders.put(indexName, gsiBuilder);
                    }
                }
            }
        });

        // Sort keySchema for each GSI to ensure HASH before RANGE
        return gsiBuilders.values().stream()
                .map(builder -> {
                    GlobalSecondaryIndex gsi = builder.build();
                    List<KeySchemaElement> sortedKeySchema = new ArrayList<>(gsi.keySchema());
                    sortKeySchemaElements(sortedKeySchema);
                    return builder.keySchema(sortedKeySchema).build();
                })
                .collect(Collectors.toList());
    }

    private List<LocalSecondaryIndex> findLocalSecondaryIndexes(Class<T> domainType,
                                                                  Map<String, ScalarAttributeType> attributeTypes) {
        // Note: LSI creation is now handled automatically by Enhanced Client
        // This method is kept only for validation purposes
        return Collections.emptyList();
    }

    private String getAttributeName(Method method) {
        DynamoDbAttribute attr = method.getAnnotation(DynamoDbAttribute.class);
        if (attr != null && !attr.value().isEmpty()) {
            return attr.value();
        }

        // Convert getter name to attribute name (e.g., getUserId -> userId)
        String methodName = method.getName();
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
        } else if (methodName.startsWith("is") && methodName.length() > 2) {
            return Character.toLowerCase(methodName.charAt(2)) + methodName.substring(3);
        }
        return methodName;
    }

    private String getAttributeName(Field field) {
        DynamoDbAttribute attr = field.getAnnotation(DynamoDbAttribute.class);
        if (attr != null && !attr.value().isEmpty()) {
            return attr.value();
        }
        return field.getName();
    }

    private ScalarAttributeType getScalarType(Class<?> type) {
        if (String.class.equals(type)) {
            return ScalarAttributeType.S;
        } else if (Number.class.isAssignableFrom(type) || type.isPrimitive()) {
            return ScalarAttributeType.N;
        } else if (byte[].class.equals(type)) {
            return ScalarAttributeType.B;
        }
        // Default to String for complex types
        return ScalarAttributeType.S;
    }

    /**
     * Sorts KeySchemaElement list to ensure HASH key comes before RANGE key.
     * AWS SDK v2 requires this specific order in CreateTableRequest.
     */
    private void sortKeySchemaElements(List<KeySchemaElement> keySchema) {
        keySchema.sort((k1, k2) -> {
            if (k1.keyType() == k2.keyType()) return 0;
            return k1.keyType() == KeyType.HASH ? -1 : 1;
        });
    }

}
