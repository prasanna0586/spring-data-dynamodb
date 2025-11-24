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
import org.socialsignin.spring.data.dynamodb.mapping.DynamoDBMappingContext;
import org.socialsignin.spring.data.dynamodb.repository.support.DynamoDBEntityInformation;
import org.socialsignin.spring.data.dynamodb.repository.support.DynamoDBHashKeyExtractingEntityMetadata;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationContextEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.ContextStoppedEvent;
import org.springframework.data.repository.core.support.RepositoryProxyPostProcessor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.ReflectionUtils;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
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

    @NonNull
    private final Entity2DDL mode;
    private final ProjectionType gsiProjectionType;
    private final ProjectionType lsiProjectionType;
    private final long readCapacity;
    private final long writeCapacity;

    private final Collection<DynamoDBEntityInformation<T, ID>> registeredEntities = new ArrayList<>();

    public Entity2DynamoDBTableSynchronizer(DynamoDbClient amazonDynamoDB,
                                            DynamoDbEnhancedClient enhancedClient,
                                            DynamoDBMappingContext mappingContext,
                                            @NonNull Entity2DDL mode) {
        this(amazonDynamoDB, enhancedClient, mappingContext, mode.getConfigurationValue(),
                ProjectionType.ALL.name(), ProjectionType.ALL.name(), 10L, 10L);
    }

    public Entity2DynamoDBTableSynchronizer(DynamoDbClient amazonDynamoDB,
            @Qualifier("dynamoDB-DynamoDBMapper") DynamoDbEnhancedClient enhancedClient,
            @Qualifier("dynamoDBMappingContext") DynamoDBMappingContext mappingContext,
            @Value(CONFIGURATION_KEY_entity2ddl_auto) String mode,
            @Value(CONFIGURATION_KEY_entity2ddl_gsiProjectionType) String gsiProjectionType,
            @Value(CONFIGURATION_KEY_entity2ddl_lsiProjectionType) String lsiProjectionType,
            @Value(CONFIGURATION_KEY_entity2ddl_readCapacity) long readCapacity,
            @Value(CONFIGURATION_KEY_entity2ddl_writeCapacity) long writeCapacity) {
        this.amazonDynamoDB = amazonDynamoDB;

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
    public void onApplicationEvent(@NonNull ApplicationContextEvent event) {
        LOGGER.info("Checking repository classes with DynamoDB tables {} for {}",
                registeredEntities.stream().map(DynamoDBHashKeyExtractingEntityMetadata::getDynamoDBTableName).collect(Collectors.joining(", ")),
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

    protected void synchronize(@NonNull DynamoDBEntityInformation<T, ID> entityInformation, ApplicationContextEvent event)
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
     */
    private void performCreate(@NonNull DynamoDBEntityInformation<T, ID> entityInformation) {
        Class<T> domainType = entityInformation.getJavaType();
        String tableName = entityInformation.getDynamoDBTableName();

        LOGGER.info("Creating table {} for entity {}",
                tableName, domainType.getSimpleName());

        try {
            // Generate CreateTableRequest with manual GSI/LSI introspection
            // This is necessary because Enhanced Client's automatic table creation
            // doesn't properly handle @DynamoDbSecondaryPartitionKey with multiple index names
            CreateTableRequest createTableRequest = generateCreateTableRequest(domainType, tableName);

            // Apply GSI projection type and throughput
            CreateTableRequest.Builder builder = createTableRequest.toBuilder();
            if (createTableRequest.hasGlobalSecondaryIndexes()) {
                List<GlobalSecondaryIndex> gsis = createTableRequest.globalSecondaryIndexes().stream()
                        .map(gsi -> gsi.toBuilder()
                                .projection(Projection.builder()
                                        .projectionType(gsiProjectionType)
                                        .build())
                                .provisionedThroughput(ProvisionedThroughput.builder()
                                        .readCapacityUnits(readCapacity)
                                        .writeCapacityUnits(writeCapacity)
                                        .build())
                                .build())
                        .collect(Collectors.toList());
                builder.globalSecondaryIndexes(gsis);
            }

            // Apply LSI projection type
            if (createTableRequest.hasLocalSecondaryIndexes()) {
                List<LocalSecondaryIndex> lsis = createTableRequest.localSecondaryIndexes().stream()
                        .map(lsi -> lsi.toBuilder()
                                .projection(Projection.builder()
                                        .projectionType(lsiProjectionType)
                                        .build())
                                .build())
                        .collect(Collectors.toList());
                builder.localSecondaryIndexes(lsis);
            }

            // Create the table using low-level client
            amazonDynamoDB.createTable(builder.build());

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

        } catch (ResourceInUseException e) {
            LOGGER.debug("Table {} already exists", tableName);
        } catch (DynamoDbException e) {
            LOGGER.error("Failed to create table {} for entity {}. Error: {}",
                    tableName, domainType.getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }

    private void performDrop(@NonNull DynamoDBEntityInformation<T, ID> entityInformation) {
        Class<T> domainType = entityInformation.getJavaType();
        String tableName = entityInformation.getDynamoDBTableName();

        LOGGER.trace("Dropping table {} for entity {}", tableName, domainType);

        try {
            DeleteTableRequest dtr = DeleteTableRequest.builder()
                    .tableName(tableName)
                    .build();
            amazonDynamoDB.deleteTable(dtr);
            LOGGER.debug("Deleted table {} for entity {}", tableName, domainType);
        } catch (ResourceNotFoundException e) {
            LOGGER.debug("Table {} does not exist", tableName);
        }
    }

    /**
     * @param entityInformation The entity to check for it's table
     * @throws IllegalStateException is thrown if the existing table doesn't match the entity's annotation
     */
    private void performValidate(@NonNull DynamoDBEntityInformation<T, ID> entityInformation)
            throws IllegalStateException {
        Class<T> domainType = entityInformation.getJavaType();
        String tableName = entityInformation.getDynamoDBTableName();

        CreateTableRequest expected = generateCreateTableRequest(domainType, tableName);
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
    }

    private boolean compareGSI(@NonNull List<GlobalSecondaryIndex> expected, @NonNull List<GlobalSecondaryIndexDescription> actual) {
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
    private CreateTableRequest generateCreateTableRequest(@NonNull Class<T> domainType, String tableName) {

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

        // EC-7.1: Validate GSI count limit (DynamoDB allows max 20 GSIs per table)
        if (globalSecondaryIndexes.size() > 20) {
            throw new IllegalStateException(String.format(
                "Invalid table configuration for entity %s: Table has %d Global Secondary Indexes, but DynamoDB allows a maximum of 20 GSIs per table.",
                domainType.getSimpleName(), globalSecondaryIndexes.size()));
        }

        // EC-7.2: Validate LSI count limit (DynamoDB allows max 5 LSIs per table)
        if (localSecondaryIndexes.size() > 5) {
            throw new IllegalStateException(String.format(
                "Invalid table configuration for entity %s: Table has %d Local Secondary Indexes, but DynamoDB allows a maximum of 5 LSIs per table.",
                domainType.getSimpleName(), localSecondaryIndexes.size()));
        }

        // EC-4.1: Validate no GSI/LSI name conflicts
        Set<String> gsiNames = globalSecondaryIndexes.stream()
            .map(GlobalSecondaryIndex::indexName)
            .collect(Collectors.toSet());
        Set<String> lsiNames = localSecondaryIndexes.stream()
            .map(LocalSecondaryIndex::indexName)
            .collect(Collectors.toSet());

        Set<String> commonNames = new HashSet<>(gsiNames);
        commonNames.retainAll(lsiNames);

        if (!commonNames.isEmpty()) {
            throw new IllegalStateException(String.format(
                "Invalid index configuration for entity %s: The following index names are used for both GSI and LSI: %s. " +
                "Global Secondary Indexes and Local Secondary Indexes must have different names.",
                domainType.getSimpleName(), commonNames));
        }

        // EC-7.3: Validate index name lengths (DynamoDB max 255 characters)
        for (GlobalSecondaryIndex gsi : globalSecondaryIndexes) {
            if (gsi.indexName().length() > 255) {
                throw new IllegalStateException(String.format(
                    "Invalid GSI configuration for entity %s: Index name '%s' is %d characters long, but DynamoDB allows a maximum of 255 characters.",
                    domainType.getSimpleName(), gsi.indexName(), gsi.indexName().length()));
            }
        }

        for (LocalSecondaryIndex lsi : localSecondaryIndexes) {
            if (lsi.indexName().length() > 255) {
                throw new IllegalStateException(String.format(
                    "Invalid LSI configuration for entity %s: Index name '%s' is %d characters long, but DynamoDB allows a maximum of 255 characters.",
                    domainType.getSimpleName(), lsi.indexName(), lsi.indexName().length()));
            }
        }

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

    private void findPartitionKey(@NonNull Class<T> domainType, @NonNull List<KeySchemaElement> keySchema,
                                  @NonNull Map<String, ScalarAttributeType> attributeTypes) {
        Set<String> partitionKeys = new HashSet<>();

        ReflectionUtils.doWithMethods(domainType, method -> {
            if (method.isAnnotationPresent(DynamoDbPartitionKey.class)) {
                String attributeName = getAttributeName(method);
                partitionKeys.add(attributeName);
                keySchema.add(KeySchemaElement.builder()
                        .attributeName(attributeName)
                        .keyType(KeyType.HASH)
                        .build());
                addAttributeType(domainType, attributeName, getScalarType(method.getReturnType()),
                    attributeTypes, "partition key on method '" + method.getName() + "'");
            }
        });

        ReflectionUtils.doWithFields(domainType, field -> {
            if (field.isAnnotationPresent(DynamoDbPartitionKey.class)) {
                String attributeName = getAttributeName(field);
                partitionKeys.add(attributeName);
                keySchema.add(KeySchemaElement.builder()
                        .attributeName(attributeName)
                        .keyType(KeyType.HASH)
                        .build());
                addAttributeType(domainType, attributeName, getScalarType(field.getType()),
                    attributeTypes, "partition key on field '" + field.getName() + "'");
            }
        });

        // EC-1.1: Validate table has partition key
        if (partitionKeys.isEmpty()) {
            throw new IllegalStateException(String.format(
                "Invalid table configuration for entity %s: Table must have a partition key. " +
                "Add @DynamoDbPartitionKey annotation to one attribute.",
                domainType.getSimpleName()));
        }

        // EC-1.2: Validate only one partition key
        if (partitionKeys.size() > 1) {
            throw new IllegalStateException(String.format(
                "Invalid table configuration for entity %s: Table has multiple partition keys: %s. " +
                "A table can only have one partition key.",
                domainType.getSimpleName(), partitionKeys));
        }
    }

    private void findSortKey(@NonNull Class<T> domainType, @NonNull List<KeySchemaElement> keySchema,
                             @NonNull Map<String, ScalarAttributeType> attributeTypes) {
        Set<String> sortKeys = new HashSet<>();

        ReflectionUtils.doWithMethods(domainType, method -> {
            if (method.isAnnotationPresent(DynamoDbSortKey.class)) {
                String attributeName = getAttributeName(method);
                sortKeys.add(attributeName);
                keySchema.add(KeySchemaElement.builder()
                        .attributeName(attributeName)
                        .keyType(KeyType.RANGE)
                        .build());
                addAttributeType(domainType, attributeName, getScalarType(method.getReturnType()),
                    attributeTypes, "sort key on method '" + method.getName() + "'");
            }
        });

        ReflectionUtils.doWithFields(domainType, field -> {
            if (field.isAnnotationPresent(DynamoDbSortKey.class)) {
                String attributeName = getAttributeName(field);
                sortKeys.add(attributeName);
                keySchema.add(KeySchemaElement.builder()
                        .attributeName(attributeName)
                        .keyType(KeyType.RANGE)
                        .build());
                addAttributeType(domainType, attributeName, getScalarType(field.getType()),
                    attributeTypes, "sort key on field '" + field.getName() + "'");
            }
        });

        // EC-1.3: Validate only one sort key
        if (sortKeys.size() > 1) {
            throw new IllegalStateException(String.format(
                "Invalid table configuration for entity %s: Table has multiple sort keys: %s. " +
                "A table can only have one sort key.",
                domainType.getSimpleName(), sortKeys));
        }
    }

    @NonNull
    private List<GlobalSecondaryIndex> findGlobalSecondaryIndexes(@NonNull Class<T> domainType,
                                                                  @NonNull Map<String, ScalarAttributeType> attributeTypes) {
        Map<String, GlobalSecondaryIndex.Builder> gsiBuilders = new HashMap<>();

        // Track partition and sort keys per index for validation
        Map<String, String> indexPartitionKeys = new HashMap<>();
        Map<String, String> indexSortKeys = new HashMap<>();

        // First, find all index names that have a partition key (these are GSIs)
        Set<String> gsiIndexNames = findGsiIndexNames(domainType);

        // Find all GSI partition keys and sort keys
        ReflectionUtils.doWithMethods(domainType, method -> {
            if (method.isAnnotationPresent(DynamoDbSecondaryPartitionKey.class)) {
                for (DynamoDbSecondaryPartitionKey annotation : method.getAnnotationsByType(DynamoDbSecondaryPartitionKey.class)) {
                    for (String indexName : annotation.indexNames()) {
                        // EC-5.2: Validate index name is non-empty
                        if (indexName == null || indexName.trim().isEmpty()) {
                            throw new IllegalStateException(String.format(
                                "Invalid GSI configuration for entity %s: Index name cannot be null or empty. " +
                                "Check @DynamoDbSecondaryPartitionKey annotation on method '%s'.",
                                domainType.getSimpleName(), method.getName()));
                        }

                        String attributeName = getAttributeName(method);

                        // Validate: Check for duplicate partition keys
                        if (indexPartitionKeys.containsKey(indexName)) {
                            String existing = indexPartitionKeys.get(indexName);
                            if (!existing.equals(attributeName)) {
                                throw new IllegalStateException(String.format(
                                    "Invalid GSI configuration for entity %s: Index '%s' has multiple partition keys: '%s' and '%s'. " +
                                    "Each Global Secondary Index can only have one partition key.",
                                    domainType.getSimpleName(), indexName, existing, attributeName));
                            }
                        }
                        indexPartitionKeys.put(indexName, attributeName);

                        addAttributeType(domainType, attributeName, getScalarType(method.getReturnType()),
                            attributeTypes, "GSI '" + indexName + "' partition key on method '" + method.getName() + "'");

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
                        // Only process if this index is a GSI (has a partition key)
                        if (!gsiIndexNames.contains(indexName)) {
                            continue;
                        }

                        String attributeName = getAttributeName(method);

                        // Validate: Check for duplicate sort keys
                        if (indexSortKeys.containsKey(indexName)) {
                            String existing = indexSortKeys.get(indexName);
                            if (!existing.equals(attributeName)) {
                                throw new IllegalStateException(String.format(
                                    "Invalid GSI configuration for entity %s: Index '%s' has multiple sort keys: '%s' and '%s'. " +
                                    "Each Global Secondary Index can only have one sort key.",
                                    domainType.getSimpleName(), indexName, existing, attributeName));
                            }
                        }
                        indexSortKeys.put(indexName, attributeName);

                        addAttributeType(domainType, attributeName, getScalarType(method.getReturnType()),
                            attributeTypes, "GSI '" + indexName + "' sort key on method '" + method.getName() + "'");

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
                        // EC-5.2: Validate index name is non-empty
                        if (indexName == null || indexName.trim().isEmpty()) {
                            throw new IllegalStateException(String.format(
                                "Invalid GSI configuration for entity %s: Index name cannot be null or empty. " +
                                "Check @DynamoDbSecondaryPartitionKey annotation on field '%s'.",
                                domainType.getSimpleName(), field.getName()));
                        }

                        String attributeName = getAttributeName(field);

                        // Validate: Check for duplicate partition keys
                        if (indexPartitionKeys.containsKey(indexName)) {
                            String existing = indexPartitionKeys.get(indexName);
                            if (!existing.equals(attributeName)) {
                                throw new IllegalStateException(String.format(
                                    "Invalid GSI configuration for entity %s: Index '%s' has multiple partition keys: '%s' and '%s'. " +
                                    "Each Global Secondary Index can only have one partition key.",
                                    domainType.getSimpleName(), indexName, existing, attributeName));
                            }
                        }
                        indexPartitionKeys.put(indexName, attributeName);

                        addAttributeType(domainType, attributeName, getScalarType(field.getType()),
                            attributeTypes, "GSI '" + indexName + "' partition key on field '" + field.getName() + "'");

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
                        // Only process if this index is a GSI (has a partition key)
                        if (!gsiIndexNames.contains(indexName)) {
                            continue;
                        }

                        String attributeName = getAttributeName(field);

                        // Validate: Check for duplicate sort keys
                        if (indexSortKeys.containsKey(indexName)) {
                            String existing = indexSortKeys.get(indexName);
                            if (!existing.equals(attributeName)) {
                                throw new IllegalStateException(String.format(
                                    "Invalid GSI configuration for entity %s: Index '%s' has multiple sort keys: '%s' and '%s'. " +
                                    "Each Global Secondary Index can only have one sort key.",
                                    domainType.getSimpleName(), indexName, existing, attributeName));
                            }
                        }
                        indexSortKeys.put(indexName, attributeName);

                        addAttributeType(domainType, attributeName, getScalarType(field.getType()),
                            attributeTypes, "GSI '" + indexName + "' sort key on field '" + field.getName() + "'");

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

        // EC-2.3: Validate each GSI has a partition key
        // EC-2.4: Validate GSI is not empty
        for (Map.Entry<String, GlobalSecondaryIndex.Builder> entry : gsiBuilders.entrySet()) {
            String indexName = entry.getKey();
            GlobalSecondaryIndex gsi = entry.getValue().build();

            if (gsi.keySchema() == null || gsi.keySchema().isEmpty()) {
                throw new IllegalStateException(String.format(
                    "Invalid GSI configuration for entity %s: Index '%s' has no keys defined. " +
                    "Each Global Secondary Index must have at least a partition key.",
                    domainType.getSimpleName(), indexName));
            }

            boolean hasPartitionKey = gsi.keySchema().stream()
                .anyMatch(key -> key.keyType() == KeyType.HASH);

            if (!hasPartitionKey) {
                throw new IllegalStateException(String.format(
                    "Invalid GSI configuration for entity %s: Index '%s' has no partition key. " +
                    "Each Global Secondary Index must have a partition key (@DynamoDbSecondaryPartitionKey).",
                    domainType.getSimpleName(), indexName));
            }
        }

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

    @NonNull
    private List<LocalSecondaryIndex> findLocalSecondaryIndexes(@NonNull Class<T> domainType,
                                                                @NonNull Map<String, ScalarAttributeType> attributeTypes) {
        Map<String, LocalSecondaryIndex.Builder> lsiBuilders = new HashMap<>();

        // Track sort keys per LSI for validation
        Map<String, String> lsiSortKeys = new HashMap<>();

        // First, find the table's partition key attribute name (needed for LSI key schema)
        String tablePartitionKeyAttributeName = findTablePartitionKeyAttributeName(domainType);
        if (tablePartitionKeyAttributeName == null) {
            return Collections.emptyList();
        }

        // EC-3.2: Check if table has a sort key (required for LSIs)
        String tableSortKeyAttributeName = findTableSortKeyAttributeName(domainType);

        // Find all LSI sort keys
        // LSI is identified by @DynamoDbSecondarySortKey annotations that don't have a corresponding
        // @DynamoDbSecondaryPartitionKey for the same index name
        Set<String> gsiIndexNames = findGsiIndexNames(domainType);

        ReflectionUtils.doWithMethods(domainType, method -> {
            if (method.isAnnotationPresent(DynamoDbSecondarySortKey.class)) {
                for (DynamoDbSecondarySortKey annotation : method.getAnnotationsByType(DynamoDbSecondarySortKey.class)) {
                    for (String indexName : annotation.indexNames()) {
                        // If this index is also a GSI, skip it (it's not an LSI)
                        if (gsiIndexNames.contains(indexName)) {
                            continue;
                        }

                        // EC-5.2: Validate index name is non-empty
                        if (indexName == null || indexName.trim().isEmpty()) {
                            throw new IllegalStateException(String.format(
                                "Invalid LSI configuration for entity %s: Index name cannot be null or empty. " +
                                "Check @DynamoDbSecondarySortKey annotation on method '%s'.",
                                domainType.getSimpleName(), method.getName()));
                        }

                        // EC-3.2: Validate table has sort key (LSIs require composite primary key)
                        if (tableSortKeyAttributeName == null) {
                            throw new IllegalStateException(String.format(
                                "Invalid LSI configuration for entity %s: Index '%s' cannot be created because the table does not have a sort key. " +
                                "Local Secondary Indexes can only be created on tables with a composite primary key (partition key + sort key). " +
                                "Add @DynamoDbSortKey annotation to one attribute or use a Global Secondary Index instead.",
                                domainType.getSimpleName(), indexName));
                        }

                        String attributeName = getAttributeName(method);

                        // EC-3.3: Warn if LSI sort key is same as table sort key (redundant but valid)
                        warnIfLsiSortKeyMatchesTableSortKey(domainType, indexName, attributeName, tableSortKeyAttributeName);

                        // Validate: Check for duplicate sort keys in LSI
                        if (lsiSortKeys.containsKey(indexName)) {
                            String existing = lsiSortKeys.get(indexName);
                            if (!existing.equals(attributeName)) {
                                throw new IllegalStateException(String.format(
                                    "Invalid LSI configuration for entity %s: Index '%s' has multiple sort keys: '%s' and '%s'. " +
                                    "Each Local Secondary Index can only have one sort key.",
                                    domainType.getSimpleName(), indexName, existing, attributeName));
                            }
                        }
                        lsiSortKeys.put(indexName, attributeName);

                        addAttributeType(domainType, attributeName, getScalarType(method.getReturnType()),
                            attributeTypes, "LSI '" + indexName + "' sort key on method '" + method.getName() + "'");

                        LocalSecondaryIndex.Builder lsiBuilder = lsiBuilders.computeIfAbsent(indexName,
                                k -> LocalSecondaryIndex.builder()
                                        .indexName(indexName)
                                        .projection(Projection.builder().projectionType(lsiProjectionType).build()));

                        // LSI key schema: table's partition key + LSI's sort key
                        List<KeySchemaElement> keySchema = new ArrayList<>();
                        keySchema.add(KeySchemaElement.builder()
                                .attributeName(tablePartitionKeyAttributeName)
                                .keyType(KeyType.HASH)
                                .build());
                        keySchema.add(KeySchemaElement.builder()
                                .attributeName(attributeName)
                                .keyType(KeyType.RANGE)
                                .build());

                        lsiBuilder.keySchema(keySchema);
                        lsiBuilders.put(indexName, lsiBuilder);
                    }
                }
            }
        });

        // Same for fields
        ReflectionUtils.doWithFields(domainType, field -> {
            if (field.isAnnotationPresent(DynamoDbSecondarySortKey.class)) {
                for (DynamoDbSecondarySortKey annotation : field.getAnnotationsByType(DynamoDbSecondarySortKey.class)) {
                    for (String indexName : annotation.indexNames()) {
                        // If this index is also a GSI, skip it (it's not an LSI)
                        if (gsiIndexNames.contains(indexName)) {
                            continue;
                        }

                        // EC-5.2: Validate index name is non-empty
                        if (indexName == null || indexName.trim().isEmpty()) {
                            throw new IllegalStateException(String.format(
                                "Invalid LSI configuration for entity %s: Index name cannot be null or empty. " +
                                "Check @DynamoDbSecondarySortKey annotation on field '%s'.",
                                domainType.getSimpleName(), field.getName()));
                        }

                        // EC-3.2: Validate table has sort key (LSIs require composite primary key)
                        if (tableSortKeyAttributeName == null) {
                            throw new IllegalStateException(String.format(
                                "Invalid LSI configuration for entity %s: Index '%s' cannot be created because the table does not have a sort key. " +
                                "Local Secondary Indexes can only be created on tables with a composite primary key (partition key + sort key). " +
                                "Add @DynamoDbSortKey annotation to one attribute or use a Global Secondary Index instead.",
                                domainType.getSimpleName(), indexName));
                        }

                        String attributeName = getAttributeName(field);

                        // EC-3.3: Warn if LSI sort key is same as table sort key (redundant but valid)
                        warnIfLsiSortKeyMatchesTableSortKey(domainType, indexName, attributeName, tableSortKeyAttributeName);

                        // Validate: Check for duplicate sort keys in LSI
                        if (lsiSortKeys.containsKey(indexName)) {
                            String existing = lsiSortKeys.get(indexName);
                            if (!existing.equals(attributeName)) {
                                throw new IllegalStateException(String.format(
                                    "Invalid LSI configuration for entity %s: Index '%s' has multiple sort keys: '%s' and '%s'. " +
                                    "Each Local Secondary Index can only have one sort key.",
                                    domainType.getSimpleName(), indexName, existing, attributeName));
                            }
                        }
                        lsiSortKeys.put(indexName, attributeName);

                        addAttributeType(domainType, attributeName, getScalarType(field.getType()),
                            attributeTypes, "LSI '" + indexName + "' sort key on field '" + field.getName() + "'");

                        LocalSecondaryIndex.Builder lsiBuilder = lsiBuilders.computeIfAbsent(indexName,
                                k -> LocalSecondaryIndex.builder()
                                        .indexName(indexName)
                                        .projection(Projection.builder().projectionType(lsiProjectionType).build()));

                        // LSI key schema: table's partition key + LSI's sort key
                        List<KeySchemaElement> keySchema = new ArrayList<>();
                        keySchema.add(KeySchemaElement.builder()
                                .attributeName(tablePartitionKeyAttributeName)
                                .keyType(KeyType.HASH)
                                .build());
                        keySchema.add(KeySchemaElement.builder()
                                .attributeName(attributeName)
                                .keyType(KeyType.RANGE)
                                .build());

                        lsiBuilder.keySchema(keySchema);
                        lsiBuilders.put(indexName, lsiBuilder);
                    }
                }
            }
        });

        return lsiBuilders.values().stream()
                .map(LocalSecondaryIndex.Builder::build)
                .collect(Collectors.toList());
    }

    /**
     * Find the table's partition key attribute name.
     */
    private String findTablePartitionKeyAttributeName(@NonNull Class<T> domainType) {
        String[] result = new String[1];

        ReflectionUtils.doWithMethods(domainType, method -> {
            if (method.isAnnotationPresent(DynamoDbPartitionKey.class) && result[0] == null) {
                result[0] = getAttributeName(method);
            }
        });

        if (result[0] == null) {
            ReflectionUtils.doWithFields(domainType, field -> {
                if (field.isAnnotationPresent(DynamoDbPartitionKey.class) && result[0] == null) {
                    result[0] = getAttributeName(field);
                }
            });
        }

        return result[0];
    }

    /**
     * Find the table's sort key attribute name.
     */
    private String findTableSortKeyAttributeName(@NonNull Class<T> domainType) {
        String[] result = new String[1];

        ReflectionUtils.doWithMethods(domainType, method -> {
            if (method.isAnnotationPresent(DynamoDbSortKey.class) && result[0] == null) {
                result[0] = getAttributeName(method);
            }
        });

        if (result[0] == null) {
            ReflectionUtils.doWithFields(domainType, field -> {
                if (field.isAnnotationPresent(DynamoDbSortKey.class) && result[0] == null) {
                    result[0] = getAttributeName(field);
                }
            });
        }

        return result[0];
    }

    /**
     * Find all GSI index names to distinguish them from LSIs.
     */
    @NonNull
    private Set<String> findGsiIndexNames(@NonNull Class<T> domainType) {
        Set<String> gsiNames = new HashSet<>();

        ReflectionUtils.doWithMethods(domainType, method -> {
            if (method.isAnnotationPresent(DynamoDbSecondaryPartitionKey.class)) {
                for (DynamoDbSecondaryPartitionKey annotation : method.getAnnotationsByType(DynamoDbSecondaryPartitionKey.class)) {
                    gsiNames.addAll(Arrays.asList(annotation.indexNames()));
                }
            }
        });

        ReflectionUtils.doWithFields(domainType, field -> {
            if (field.isAnnotationPresent(DynamoDbSecondaryPartitionKey.class)) {
                for (DynamoDbSecondaryPartitionKey annotation : field.getAnnotationsByType(DynamoDbSecondaryPartitionKey.class)) {
                    gsiNames.addAll(Arrays.asList(annotation.indexNames()));
                }
            }
        });

        return gsiNames;
    }

    @NonNull
    private String getAttributeName(@NonNull Method method) {
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

    @NonNull
    private String getAttributeName(@NonNull Field field) {
        DynamoDbAttribute attr = field.getAnnotation(DynamoDbAttribute.class);
        if (attr != null && !attr.value().isEmpty()) {
            return attr.value();
        }
        return field.getName();
    }

    @NonNull
    private ScalarAttributeType getScalarType(@NonNull Class<?> type) {
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
     * EC-5.3: Add attribute type to map with validation for consistency.
     * Ensures that if the same attribute name is defined multiple times (e.g., on method and field),
     * they have the same type.
     */
    private void addAttributeType(@NonNull Class<T> domainType, String attributeName, ScalarAttributeType type,
                                  @NonNull Map<String, ScalarAttributeType> attributeTypes, String location) {
        if (attributeTypes.containsKey(attributeName)) {
            ScalarAttributeType existingType = attributeTypes.get(attributeName);
            if (existingType != type) {
                throw new IllegalStateException(String.format(
                    "Invalid attribute configuration for entity %s: Attribute '%s' has conflicting types. " +
                    "Found type '%s' at %s, but previously defined as type '%s'. " +
                    "Ensure the same attribute has consistent types across all annotations.",
                    domainType.getSimpleName(), attributeName, type, location, existingType));
            }
        }
        attributeTypes.put(attributeName, type);
    }

    /**
     * Sorts KeySchemaElement list to ensure HASH key comes before RANGE key.
     * AWS SDK v2 requires this specific order in CreateTableRequest.
     */
    private void sortKeySchemaElements(@NonNull List<KeySchemaElement> keySchema) {
        keySchema.sort((k1, k2) -> {
            if (k1.keyType() == k2.keyType()) return 0;
            return k1.keyType() == KeyType.HASH ? -1 : 1;
        });
    }

    /**
     * Warns if an LSI sort key is the same as the table's sort key (redundant but valid).
     * EC-3.3: Validate LSI configuration for redundancy.
     */
    private void warnIfLsiSortKeyMatchesTableSortKey(@NonNull Class<T> domainType, String indexName,
                                                     String attributeName, @Nullable String tableSortKeyAttributeName) {
        if (tableSortKeyAttributeName != null && tableSortKeyAttributeName.equals(attributeName)) {
            LOGGER.warn("LSI configuration for entity {}: Index '{}' uses '{}' as sort key, " +
                "which is the same as the table's sort key. This LSI is redundant and provides no benefit.",
                domainType.getSimpleName(), indexName, attributeName);
        }
    }

}
