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
package org.socialsignin.spring.data.dynamodb.core;

import org.socialsignin.spring.data.dynamodb.mapping.DynamoDBMappingContext;
import org.socialsignin.spring.data.dynamodb.mapping.event.*;
import org.socialsignin.spring.data.dynamodb.marshaller.Date2IsoDynamoDBMarshaller;
import org.socialsignin.spring.data.dynamodb.marshaller.Instant2IsoDynamoDBMarshaller;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mapping.callback.EntityCallbacks;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.Select;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DynamoDBTemplate implements DynamoDBOperations, ApplicationContextAware {
    @NonNull
    private final DynamoDbEnhancedClient enhancedClient;
    @NonNull
    private final DynamoDbClient amazonDynamoDB;
    @Nullable
    private final TableNameResolver tableNameResolver;
    @NonNull
    private final DynamoDBMappingContext mappingContext;
    private final Map<Class<?>, DynamoDbTable<?>> tableCache = new ConcurrentHashMap<>();
    private ApplicationEventPublisher eventPublisher;
    @Nullable
    private EntityCallbacks entityCallbacks;

    /**
     * Initializes a new {@code DynamoDBTemplate} using AWS SDK v2.
     * @param amazonDynamoDB
     *            The low-level DynamoDB client for direct operations, must not be {@code null}
     * @param enhancedClient
     *            The DynamoDB Enhanced Client for object mapping, must not be {@code null}
     * @param tableNameResolver
     *            Optional resolver for table name overrides/prefixes, can be {@code null}
     * @param mappingContext
     *            The DynamoDB mapping context (uses default SDK_V2_NATIVE if null)
     * @since 7.0.0
     */
    public DynamoDBTemplate(@NonNull DynamoDbClient amazonDynamoDB,
                            @NonNull DynamoDbEnhancedClient enhancedClient,
                            @Nullable TableNameResolver tableNameResolver,
                            @Nullable DynamoDBMappingContext mappingContext) {
        Assert.notNull(amazonDynamoDB, "amazonDynamoDB must not be null!");
        Assert.notNull(enhancedClient, "enhancedClient must not be null!");

        this.amazonDynamoDB = amazonDynamoDB;
        this.enhancedClient = enhancedClient;
        this.tableNameResolver = tableNameResolver;
        this.mappingContext = mappingContext != null ? mappingContext : new DynamoDBMappingContext();
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.eventPublisher = applicationContext;

        // Try to obtain EntityCallbacks if available in the application context
        try {
            this.entityCallbacks = EntityCallbacks.create(applicationContext);
        } catch (Exception e) {
            // EntityCallbacks not available, callbacks won't be invoked
            this.entityCallbacks = null;
        }
    }

    /**
     * Gets or creates a DynamoDbTable instance for the given domain class.
     * Tables are cached for performance.
     * <p>
     * The TableSchema is created based on the marshalling mode configured in the mapping context:
     * <ul>
     * <li>SDK_V2_NATIVE: Uses standard SDK v2 type mappings</li>
     * <li>SDK_V1_COMPATIBLE: Supports SDK v1-compatible attribute converters (requires @DynamoDbConvertedBy annotations)</li>
     * </ul>
     * @param <T>         The domain class type
     * @param domainClass The domain class
     * @return The DynamoDbTable instance for the given class
     */
    @SuppressWarnings("unchecked")
    private <T> DynamoDbTable<T> getTable(@NonNull Class<T> domainClass) {
        return (DynamoDbTable<T>) tableCache.computeIfAbsent(domainClass, clazz -> {
            MarshallingMode mode = mappingContext.getMarshallingMode();
            TableSchema<T> schema = TableSchemaFactory.createTableSchema(domainClass);
            String tableName = resolveTableName(domainClass);
            return enhancedClient.table(tableName, schema);
        });
    }

    /**
     * Resolves the table name for the given domain class, applying any configured overrides.
     * @param <T>         The domain class type
     * @param domainClass The domain class
     * @return The resolved table name
     */
    private <T> String resolveTableName(@NonNull Class<T> domainClass) {
        // Use class simple name as base table name
        // In SDK v2, the table name is not stored in the @DynamoDbBean annotation
        // It must be explicitly provided when creating the table or via TableNameResolver
        String baseTableName = domainClass.getSimpleName();

        // Apply resolver if configured
        if (tableNameResolver != null) {
            return tableNameResolver.resolveTableName(domainClass, baseTableName);
        }

        return baseTableName;
    }

    /**
     * Builds a Key object for SDK v2 operations.
     * @param hashKeyValue  The partition key value
     * @param rangeKeyValue The sort key value (can be null for hash-key-only tables)
     * @return The constructed Key
     */
    @NonNull
    private Key buildKey(@NonNull Object hashKeyValue, @Nullable Object rangeKeyValue) {
        Key.Builder keyBuilder = Key.builder()
                .partitionValue(toAttributeValue(hashKeyValue));

        if (rangeKeyValue != null) {
            keyBuilder.sortValue(toAttributeValue(rangeKeyValue));
        }

        return keyBuilder.build();
    }

    /**
     * Converts a Java object to SDK v2 AttributeValue.
     * <p>
     * Marshalling behavior depends on the configured MarshallingMode.
     * @param value The Java object to convert
     * @return The SDK v2 AttributeValue
     */
    private AttributeValue toAttributeValue(@NonNull Object value) {
        switch (value) {
            case AttributeValue attributeValue -> {
                return attributeValue;
            }
            case String s -> {
                return AttributeValue.builder().s(s).build();
            }
            case Number number -> {
                return AttributeValue.builder().n(value.toString()).build();
            }
            case Boolean b -> {
                if (mappingContext.getMarshallingMode() == MarshallingMode.SDK_V1_COMPATIBLE) {
                    // SDK v1 compatibility: Boolean stored as "1" or "0" in Number format
                    boolean boolValue = b;
                    return AttributeValue.builder().n(boolValue ? "1" : "0").build();
                } else {
                    // SDK v2 native: Boolean stored as BOOL type
                    return AttributeValue.builder().bool(b).build();
                }
            }
            case java.util.Date date -> {
                if (mappingContext.getMarshallingMode() == MarshallingMode.SDK_V1_COMPATIBLE) {
                    // SDK v1 compatibility: Date marshalled to ISO format string
                    String marshalledDate = new Date2IsoDynamoDBMarshaller().marshall(date);
                    return AttributeValue.builder().s(marshalledDate).build();
                } else {
                    // SDK v2 native: Date as epoch milliseconds in Number format
                    return AttributeValue.builder().n(String.valueOf(date.getTime())).build();
                }
            }
            case java.time.Instant instant -> {
                // Both SDK v1 and v2 store Instant as String (ISO-8601 format)
                // AWS SDK v2 uses InstantAsStringAttributeConverter by default
                if (mappingContext.getMarshallingMode() == MarshallingMode.SDK_V1_COMPATIBLE) {
                    // SDK v1 compatibility: Instant marshalled to ISO format string with millisecond precision
                    String marshalledDate = new Instant2IsoDynamoDBMarshaller().marshall(instant);
                    return AttributeValue.builder().s(marshalledDate).build();
                } else {
                    // SDK v2 native: Instant as ISO-8601 string (matches AWS SDK v2 InstantAsStringAttributeConverter)
                    // Format: ISO-8601 with nanosecond precision, e.g., "1970-01-01T00:00:00.001Z"
                    return AttributeValue.builder().s(instant.toString()).build();
                }
                // Both SDK v1 and v2 store Instant as String (ISO-8601 format)
                // AWS SDK v2 uses InstantAsStringAttributeConverter by default
            }
            case byte[] bytes -> {
                return AttributeValue.builder().b(software.amazon.awssdk.core.SdkBytes.fromByteArray(bytes)).build();
            }
            default -> {
                // Fallback: convert to string
                return AttributeValue.builder().s(value.toString()).build();
            }
        }

    }

    @Override
    public <T> T load(@NonNull Class<T> domainClass, @NonNull Object hashKey, Object rangeKey) {
        DynamoDbTable<T> table = getTable(domainClass);
        Key key = buildKey(hashKey, rangeKey);
        T entity = table.getItem(key);
        maybeEmitEvent(entity, AfterLoadEvent::new);

        return entity;
    }

    @Override
    public <T> T load(@NonNull Class<T> domainClass, @NonNull Object hashKey) {
        DynamoDbTable<T> table = getTable(domainClass);
        Key key = buildKey(hashKey, null);
        T entity = table.getItem(key);
        maybeEmitEvent(entity, AfterLoadEvent::new);

        return entity;
    }

    @NonNull
    @SuppressWarnings("unchecked")
    @Override
    public <T> List<T> batchLoad(@NonNull Map<Class<?>, List<Key>> itemsToGet) {
        // Pre-allocate result list to avoid resizing
        int totalKeys = itemsToGet.values().stream().mapToInt(List::size).sum();
        List<T> results = new ArrayList<>(totalKeys);

        // SDK v2 Enhanced Client requires separate batch requests per table
        for (Map.Entry<Class<?>, List<Key>> entry : itemsToGet.entrySet()) {
            @SuppressWarnings("unchecked")
            Class<Object> domainClass = (Class<Object>) entry.getKey();
            List<Key> keys = entry.getValue();

            if (keys.isEmpty()) {
                continue;
            }

            DynamoDbTable<Object> table = getTable(domainClass);

            // DynamoDB BatchGetItem has a limit of 100 items per request
            // See: https://docs.aws.amazon.com/amazondynamodb/latest/APIReference/API_BatchGetItem.html
            // If more than 100 items are requested, chunk them into multiple batches
            final int batchSize = 100;
            int totalKeyCount = keys.size();

            for (int startIndex = 0; startIndex < totalKeyCount; startIndex += batchSize) {
                int endIndex = Math.min(startIndex + batchSize, totalKeyCount);
                List<Key> keysBatch = keys.subList(startIndex, endIndex);

                // Create batch get request for this chunk
                BatchGetItemEnhancedRequest.Builder requestBuilder = BatchGetItemEnhancedRequest.builder();

                ReadBatch.Builder<Object> batchBuilder = ReadBatch.builder(domainClass)
                        .mappedTableResource(table);

                for (Key key : keysBatch) {
                    batchBuilder.addGetItem(key);
                }

                requestBuilder.addReadBatch(batchBuilder.build());

                // Execute batch get
                BatchGetResultPageIterable resultPages = enhancedClient.batchGetItem(requestBuilder.build());

                // Collect results
                for (BatchGetResultPage page : resultPages) {
                    List<?> pageResults = page.resultsForTable(table);
                    for (Object entity : pageResults) {
                        maybeEmitEvent(entity, AfterLoadEvent::new);
                        results.add((T) entity);
                    }
                }
            }
        }

        return results;
    }

    @Override
    public <T> T save(T entity) {
        // IMPORTANT: Call BeforeConvertCallback BEFORE auto-generation.
        // This ensures the auditing handler determines "isNew" based on the entity's actual state.
        // Then auto-generation sets the ID, making the entity ready for persistence.
        //
        // Call BeforeConvertCallback - allows entity modification (e.g., auditing)
        entity = maybeCallBeforeConvert(entity, resolveTableName(entity.getClass()));

        // Process auto-generated keys for SDK_V1_COMPATIBLE mode
        if (mappingContext.getMarshallingMode() == MarshallingMode.SDK_V1_COMPATIBLE) {
            AutoGeneratedKeyHelper.processAutoGeneratedKeys(entity);
        }

        // Publish legacy BeforeSaveEvent for backward compatibility
        maybeEmitEvent(entity, BeforeSaveEvent::new);

        @SuppressWarnings("unchecked")
        DynamoDbTable<T> table = (DynamoDbTable<T>) getTable(entity.getClass());

        // Use updateItem instead of putItem to properly handle @DynamoDbVersionAttribute.
        // AWS SDK v2 Enhanced Client's putItem does not update the local object's version field,
        // but updateItem returns the complete updated entity with the new version.
        // updateItem works for both new items (insert) and existing items (update).
        //
        // See: https://github.com/aws/aws-sdk-java-v2/issues/3278
        T savedEntity = table.updateItem(entity);

        maybeEmitEvent(savedEntity, AfterSaveEvent::new);
        return savedEntity;
    }

    /**
     * Invokes {@link BeforeConvertCallback} if {@link EntityCallbacks} are available.
     * <p>
     * Returns the potentially modified entity.
     * @param entity the entity to process
     * @param tableName the table name
     * @param <T> entity type
     * @return the potentially modified entity
     */
    @NonNull
    private <T> T maybeCallBeforeConvert(@NonNull T entity, String tableName) {
        if (entityCallbacks != null) {
            return entityCallbacks.callback(BeforeConvertCallback.class, entity, tableName);
        }
        return entity;
    }

    @NonNull
    @Override
    public List<BatchWriteResult> batchSave(@NonNull Iterable<?> entities) {
        // Process auto-generated keys for SDK_V1_COMPATIBLE mode
        if (mappingContext.getMarshallingMode() == MarshallingMode.SDK_V1_COMPATIBLE) {
            entities.forEach(AutoGeneratedKeyHelper::processAutoGeneratedKeys);
        }

        entities.forEach(it -> maybeEmitEvent(it, BeforeSaveEvent::new));

        // Group entities by class
        Map<Class<?>, List<Object>> entitiesByClass = new HashMap<>();
        for (Object entity : entities) {
            entitiesByClass.computeIfAbsent(entity.getClass(), k -> new ArrayList<>()).add(entity);
        }

        List<BatchWriteResult> results = new ArrayList<>();

        // If no entities to save, return empty results
        if (entitiesByClass.isEmpty()) {
            return results;
        }

        // DynamoDB has a limit of 25 items per batch write request
        // We need to split entities into chunks and process them separately
        final int BATCH_WRITE_MAX_SIZE = 25;

        // Collect all entities into a flat list for batching
        List<Object> allEntities = new ArrayList<>();
        for (List<Object> classEntities : entitiesByClass.values()) {
            allEntities.addAll(classEntities);
        }

        // Process in chunks of 25
        for (int i = 0; i < allEntities.size(); i += BATCH_WRITE_MAX_SIZE) {
            int endIndex = Math.min(i + BATCH_WRITE_MAX_SIZE, allEntities.size());
            List<Object> chunk = allEntities.subList(i, endIndex);

            // Re-group this chunk by class
            Map<Class<?>, List<Object>> chunkByClass = new HashMap<>();
            for (Object entity : chunk) {
                chunkByClass.computeIfAbsent(entity.getClass(), k -> new ArrayList<>()).add(entity);
            }

            // Create batch write request for this chunk
            BatchWriteItemEnhancedRequest.Builder requestBuilder = BatchWriteItemEnhancedRequest.builder();

            for (Map.Entry<Class<?>, List<Object>> entry : chunkByClass.entrySet()) {
                @SuppressWarnings("unchecked")
                Class<Object> domainClass = (Class<Object>) entry.getKey();
                List<Object> classEntities = entry.getValue();

                DynamoDbTable<Object> table = getTable(domainClass);

                WriteBatch.Builder<Object> batchBuilder = WriteBatch.builder(domainClass)
                        .mappedTableResource(table);

                for (Object entity : classEntities) {
                    batchBuilder.addPutItem(entity);
                }

                requestBuilder.addWriteBatch(batchBuilder.build());
            }

            // Execute batch write for this chunk
            BatchWriteResult result = enhancedClient.batchWriteItem(requestBuilder.build());
            results.add(result);
        }

        entities.forEach(it -> maybeEmitEvent(it, AfterSaveEvent::new));
        return results;
    }

    @NonNull
    @Override
    public <T> T delete(@NonNull T entity) {
        maybeEmitEvent(entity, BeforeDeleteEvent::new);

        @SuppressWarnings("unchecked")
        DynamoDbTable<T> table = (DynamoDbTable<T>) getTable(entity.getClass());
        table.deleteItem(entity);

        maybeEmitEvent(entity, AfterDeleteEvent::new);
        return entity;
    }

    @NonNull
    @Override
    public List<BatchWriteResult> batchDelete(@NonNull Iterable<?> entities) {
        entities.forEach(it -> maybeEmitEvent(it, BeforeDeleteEvent::new));

        // Group entities by class
        Map<Class<?>, List<Object>> entitiesByClass = new HashMap<>();
        for (Object entity : entities) {
            entitiesByClass.computeIfAbsent(entity.getClass(), k -> new ArrayList<>()).add(entity);
        }

        List<BatchWriteResult> results = new ArrayList<>();

        // If no entities to delete, return empty results
        if (entitiesByClass.isEmpty()) {
            return results;
        }

        // DynamoDB has a limit of 25 items per batch write request
        // We need to split entities into chunks and process them separately
        final int BATCH_WRITE_MAX_SIZE = 25;

        // Collect all entities into a flat list for batching
        List<Object> allEntities = new ArrayList<>();
        for (List<Object> classEntities : entitiesByClass.values()) {
            allEntities.addAll(classEntities);
        }

        // Process in chunks of 25
        for (int i = 0; i < allEntities.size(); i += BATCH_WRITE_MAX_SIZE) {
            int endIndex = Math.min(i + BATCH_WRITE_MAX_SIZE, allEntities.size());
            List<Object> chunk = allEntities.subList(i, endIndex);

            // Re-group this chunk by class
            Map<Class<?>, List<Object>> chunkByClass = new HashMap<>();
            for (Object entity : chunk) {
                chunkByClass.computeIfAbsent(entity.getClass(), k -> new ArrayList<>()).add(entity);
            }

            // Create batch write request for this chunk
            BatchWriteItemEnhancedRequest.Builder requestBuilder = BatchWriteItemEnhancedRequest.builder();

            for (Map.Entry<Class<?>, List<Object>> entry : chunkByClass.entrySet()) {
                @SuppressWarnings("unchecked")
                Class<Object> domainClass = (Class<Object>) entry.getKey();
                List<Object> classEntities = entry.getValue();

                DynamoDbTable<Object> table = getTable(domainClass);

                WriteBatch.Builder<Object> batchBuilder = WriteBatch.builder(domainClass)
                        .mappedTableResource(table);

                for (Object entity : classEntities) {
                    batchBuilder.addDeleteItem(entity);
                }

                requestBuilder.addWriteBatch(batchBuilder.build());
            }

            // Execute batch write for this chunk
            BatchWriteResult result = enhancedClient.batchWriteItem(requestBuilder.build());
            results.add(result);
        }

        entities.forEach(it -> maybeEmitEvent(it, AfterDeleteEvent::new));
        return results;
    }

    /**
     * Extracts unprocessed put items (saves) from batch write results.
     * <p>
     * This method is used to extract the actual entity objects that failed to be written
     * after batch save operations, so they can be included in BatchWriteException for
     * consumer handling (retry, DLQ, alerting, etc.).
     * @param results List of BatchWriteResult from batch save operations
     * @param entitiesByClass Original entities grouped by class (used to get table references)
     * @return List of unprocessed entity objects that failed to be saved
     */
    @NonNull
    public List<Object> extractUnprocessedPutItems(
            @NonNull List<BatchWriteResult> results,
            @NonNull Map<Class<?>, List<Object>> entitiesByClass) {

        List<Object> unprocessedEntities = new ArrayList<>();

        for (BatchWriteResult result : results) {
            // Check each table we attempted to write to
            for (Map.Entry<Class<?>, List<Object>> entry : entitiesByClass.entrySet()) {
                @SuppressWarnings("unchecked")
                Class<Object> domainClass = (Class<Object>) entry.getKey();
                DynamoDbTable<Object> table = getTable(domainClass);

                // Extract unprocessed put items for this table
                List<Object> unprocessedPuts = result.unprocessedPutItemsForTable(table);
                if (unprocessedPuts != null && !unprocessedPuts.isEmpty()) {
                    unprocessedEntities.addAll(unprocessedPuts);
                }
            }
        }

        return unprocessedEntities;
    }

    /**
     * Extracts unprocessed delete items from batch write results.
     * <p>
     * This method extracts the entity objects that failed to be deleted after batch
     * delete operations. Note that for deletes, SDK v2 returns Key objects, so we
     * reconstruct the entities from the original list.
     * @param results List of BatchWriteResult from batch delete operations
     * @param entitiesByClass Original entities grouped by class (used to get table references and match keys)
     * @return List of unprocessed entity objects that failed to be deleted
     */
    @NonNull
    public List<Object> extractUnprocessedDeleteItems(
            @NonNull List<BatchWriteResult> results,
            @NonNull Map<Class<?>, List<Object>> entitiesByClass) {

        List<Object> unprocessedEntities = new ArrayList<>();

        for (BatchWriteResult result : results) {
            // Check each table we attempted to delete from
            for (Map.Entry<Class<?>, List<Object>> entry : entitiesByClass.entrySet()) {
                @SuppressWarnings("unchecked")
                Class<Object> domainClass = (Class<Object>) entry.getKey();
                DynamoDbTable<Object> table = getTable(domainClass);

                // Extract unprocessed delete keys for this table
                List<Key> unprocessedKeys = result.unprocessedDeleteItemsForTable(table);
                if (unprocessedKeys != null && !unprocessedKeys.isEmpty()) {
                    // Convert keys back to entities by matching against original entities
                    // This is necessary because delete returns keys, not full items
                    List<Object> originalEntities = entry.getValue();
                    for (Key key : unprocessedKeys) {
                        // Find the matching entity from the original list
                        for (Object originalEntity : originalEntities) {
                            Key entityKey = table.keyFrom(originalEntity);
                            if (entityKey.equals(key)) {
                                unprocessedEntities.add(originalEntity);
                                break;
                            }
                        }
                    }
                }
            }
        }

        return unprocessedEntities;
    }

    @NonNull
    @Override
    public <T> PageIterable<T> query(@NonNull Class<T> clazz, QueryRequest queryRequest) {
        DynamoDbTable<T> table = getTable(clazz);

        // Manually paginate through query results to avoid infinite iterator issue
        List<Page<T>> allPages = new ArrayList<>();
        QueryResponse queryResult;
        QueryRequest mutableQueryRequest = queryRequest;

        do {
            queryResult = amazonDynamoDB.query(mutableQueryRequest);

            // Convert items from the response to entities
            List<T> items = queryResult.items().stream()
                    .map(itemMap -> table.tableSchema().mapToItem(itemMap))
                    .collect(Collectors.toList());

            // Create a Page with the items and add to results
            allPages.add(Page.builder(clazz).items(items).build());

            // Check if there are more pages - lastEvaluatedKey can be empty map {} instead of null
            if (queryResult.lastEvaluatedKey() == null || queryResult.lastEvaluatedKey().isEmpty()) {
                break;
            }

            // Set up the next request with the lastEvaluatedKey
            mutableQueryRequest = mutableQueryRequest.toBuilder()
                    .exclusiveStartKey(queryResult.lastEvaluatedKey())
                    .build();
        } while (true);

        // Convert List<Page<T>> to PageIterable<T>
        return PageIterable.create(allPages::iterator);
    }

    @Override
    public <T> PageIterable<T> query(@NonNull Class<T> domainClass, QueryEnhancedRequest queryRequest) {
        DynamoDbTable<T> table = getTable(domainClass);
        PageIterable<T> results = table.query(queryRequest);
        maybeEmitEvent(results, AfterQueryEvent::new);
        return results;
    }

    @Override
    public <T> PageIterable<T> scan(@NonNull Class<T> domainClass, ScanEnhancedRequest scanRequest) {
        DynamoDbTable<T> table = getTable(domainClass);
        PageIterable<T> results = table.scan(scanRequest);
        maybeEmitEvent(results, AfterScanEvent::new);
        return results;
    }

    @Override
    public <T> int count(@NonNull Class<T> domainClass, QueryEnhancedRequest queryRequest) {
        DynamoDbTable<T> table = getTable(domainClass);
        PageIterable<T> results = table.query(queryRequest);

        // Count all items across all pages
        int count = 0;
        for (Page<T> page : results) {
            count += page.items().size();
        }
        return count;
    }

    @Override
    public <T> int count(@NonNull Class<T> domainClass, @NonNull ScanEnhancedRequest scanRequest) {
        DynamoDbTable<T> table = getTable(domainClass);
        String tableName = table.tableName();

        // Convert ScanEnhancedRequest to low-level ScanRequest with SELECT COUNT
        software.amazon.awssdk.services.dynamodb.model.ScanRequest.Builder scanBuilder =
            software.amazon.awssdk.services.dynamodb.model.ScanRequest.builder()
                .tableName(tableName)
                .select(software.amazon.awssdk.services.dynamodb.model.Select.COUNT);

        // Copy filter expression if present
        if (scanRequest.filterExpression() != null) {
            scanBuilder.filterExpression(scanRequest.filterExpression().expression());
            if (scanRequest.filterExpression().expressionValues() != null) {
                scanBuilder.expressionAttributeValues(scanRequest.filterExpression().expressionValues());
            }
            if (scanRequest.filterExpression().expressionNames() != null) {
                scanBuilder.expressionAttributeNames(scanRequest.filterExpression().expressionNames());
            }
        }

        // Copy limit if present
        if (scanRequest.limit() != null) {
            scanBuilder.limit(scanRequest.limit());
        }

        // Paginate through scan results counting items
        int count = 0;
        software.amazon.awssdk.services.dynamodb.model.ScanResponse scanResult;
        software.amazon.awssdk.services.dynamodb.model.ScanRequest mutableScanRequest = scanBuilder.build();

        do {
            scanResult = amazonDynamoDB.scan(mutableScanRequest);
            count += scanResult.count();

            // Check if there are more pages to scan
            if (scanResult.lastEvaluatedKey() == null || scanResult.lastEvaluatedKey().isEmpty()) {
                break;
            }

            mutableScanRequest = mutableScanRequest.toBuilder()
                .exclusiveStartKey(scanResult.lastEvaluatedKey())
                .build();
        } while (true);

        return count;
    }

    @Override
    public <T> int count(Class<T> clazz, QueryRequest mutableQueryRequest) {
        mutableQueryRequest = mutableQueryRequest.toBuilder().select(Select.COUNT).build();

        // Count queries can also be truncated for large datasets
        int count = 0;
        QueryResponse queryResult;
        do {
            queryResult = amazonDynamoDB.query(mutableQueryRequest);
            count += queryResult.count();

            // Check if there are more pages - lastEvaluatedKey can be empty map {} instead of null
            if (queryResult.lastEvaluatedKey() == null || queryResult.lastEvaluatedKey().isEmpty()) {
                break;
            }

            mutableQueryRequest = mutableQueryRequest.toBuilder().exclusiveStartKey(queryResult.lastEvaluatedKey()).build();
        } while (true);

        return count;
    }

    @Override
    public <T> String getOverriddenTableName(Class<T> domainClass, String tableName) {
        if (tableNameResolver != null) {
            return tableNameResolver.resolveTableName(domainClass, tableName);
        }
        return tableName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> TableSchema<T> getTableModel(Class<T> domainClass) {
        return TableSchema.fromBean(domainClass);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public DynamoDBMappingContext getMappingContext() {
        return mappingContext;
    }

    protected <T> void maybeEmitEvent(@Nullable T source, @NonNull Function<T, DynamoDBMappingEvent<T>> factory) {
        if (eventPublisher != null) {
            if (source != null) {
                DynamoDBMappingEvent<T> event = factory.apply(source);

                eventPublisher.publishEvent(event);
            }
        }

    }
}
