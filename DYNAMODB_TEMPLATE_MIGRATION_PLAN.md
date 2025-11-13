# DynamoDBTemplate Migration Plan: AWS SDK v1 to v2

**Date Created:** 2025-11-12
**Target File:** `src/main/java/org/socialsignin/spring/data/dynamodb/core/DynamoDBTemplate.java`
**Current Status:** Uses SDK v1 `DynamoDBMapper` internally
**Target Status:** Use SDK v2 `DynamoDbEnhancedClient`
**Interface Status:** `DynamoDBOperations` interface already uses SDK v2 signatures ✓

---

## Executive Summary

The DynamoDBTemplate class is the **core implementation** of DynamoDBOperations and serves as the primary interface between Spring Data DynamoDB and AWS DynamoDB. It currently uses AWS SDK v1's `DynamoDBMapper` internally but implements an interface that already expects SDK v2 types.

**Key Challenge:** The interface-implementation mismatch means DynamoDBTemplate cannot compile/work properly until fully migrated.

**Migration Complexity:** HIGH
**Estimated Time:** 6-8 hours of focused work
**Risk Level:** CRITICAL (affects all repository operations)

---

## Current State Analysis

### Fields (Lines 40-44)
```java
private final DynamoDBMapper dynamoDBMapper;              // SDK v1 ❌
private final DynamoDbClient amazonDynamoDB;              // SDK v2 ✓
private final DynamoDBMapperConfig dynamoDBMapperConfig;  // SDK v1 ❌
private final DynamoDBMappingContext mappingContext;      // SDK v2 ✓
private ApplicationEventPublisher eventPublisher;         // Spring ✓
```

### SDK v1 Dependencies to Remove
1. `DynamoDBMapper` - Main object mapper
2. `DynamoDBMapperConfig` - Configuration object
3. `DynamoDBQueryExpression<T>` - Query expression builder (unused in current implementation)
4. `DynamoDBScanExpression` - Scan expression builder (unused in current implementation)
5. `PaginatedQueryList<T>` - Query result wrapper (used in line 165-178)
6. `PaginatedScanList<T>` - Scan result wrapper (unused in current implementation)
7. `KeyPair` - Batch load key pair (used in line 119)
8. `FailedBatch` - Batch operation failure (used in lines 137, 155)
9. `DynamoDBMapperTableModel<T>` - Table metadata (used in line 215)

### Methods Status Matrix

| Method | Lines | SDK v1 Usage | SDK v2 Target | Complexity | Priority |
|--------|-------|--------------|---------------|------------|----------|
| **Constructor** | 60-70 | DynamoDBMapper, DynamoDBMapperConfig | DynamoDbEnhancedClient | HIGH | P0 |
| save() | 128-133 | dynamoDBMapper.save() | table.putItem() | LOW | P1 |
| delete() | 147-152 | dynamoDBMapper.delete() | table.deleteItem() | LOW | P1 |
| load(hashKey, rangeKey) | 95-100 | dynamoDBMapper.load() | table.getItem(Key) | MEDIUM | P1 |
| load(hashKey) | 103-108 | dynamoDBMapper.load() | table.getItem(Key) | MEDIUM | P1 |
| batchLoad() | 119-125 | dynamoDBMapper.batchLoad(KeyPair) | batchGetItem(Key) | HIGH | P2 |
| batchSave() | 137-144 | dynamoDBMapper.batchSave() | batchWriteItem() | HIGH | P2 |
| batchDelete() | 155-162 | dynamoDBMapper.batchDelete() | batchWriteItem() | HIGH | P2 |
| query(QueryRequest) | 165-178 | Returns PaginatedQueryList | Return PageIterable | HIGH | P3 |
| count(QueryRequest) | 181-194 | Low-level SDK v2 | Already correct | LOW | P4 |
| getOverriddenTableName() | 197-209 | DynamoDBMapperConfig | Custom resolver | MEDIUM | P3 |
| getTableModel() | 215-217 | DynamoDBMapperTableModel | TableSchema | LOW | P1 |
| getMappingContext() | 223-225 | N/A | Already correct | DONE | - |

**Note:** Methods accepting `QueryEnhancedRequest` and `ScanEnhancedRequest` are **not currently implemented** in DynamoDBTemplate but are defined in the DynamoDBOperations interface. We need to ADD these implementations.

---

## Migration Strategy: Phased Approach

### Phase 0: Preparation (30 minutes)

**Goal:** Set up infrastructure and understand the Enhanced Client API

**Tasks:**
1. Research SDK v2 Enhanced Client patterns:
   - Creating `DynamoDbEnhancedClient` from `DynamoDbClient`
   - Getting `DynamoDbTable<T>` instances
   - Building `Key` objects for get/delete operations
   - Understanding `TableSchema` creation and caching

2. Create helper methods (add to DynamoDBTemplate):
   ```java
   private <T> DynamoDbTable<T> getTable(Class<T> domainClass)
   private <T> Key buildKey(Object hashKey, Object rangeKey)
   private <T> TableSchema<T> getTableSchema(Class<T> domainClass)
   ```

3. Create table cache (field):
   ```java
   private final Map<Class<?>, DynamoDbTable<?>> tableCache = new ConcurrentHashMap<>();
   ```

**Verification:** Can create DynamoDbTable instances and retrieve TableSchema

---

### Phase 1: Constructor and Core Fields (1 hour)

**Goal:** Replace SDK v1 core dependencies with SDK v2

**Current Constructor (Lines 60-70):**
```java
public DynamoDBTemplate(DynamoDbClient amazonDynamoDB, DynamoDBMapper dynamoDBMapper,
        DynamoDBMapperConfig dynamoDBMapperConfig, DynamoDBMappingContext mappingContext) {
    Assert.notNull(amazonDynamoDB, "amazonDynamoDB must not be null!");
    Assert.notNull(dynamoDBMapper, "dynamoDBMapper must not be null!");
    Assert.notNull(dynamoDBMapperConfig, "dynamoDBMapperConfig must not be null!");

    this.amazonDynamoDB = amazonDynamoDB;
    this.dynamoDBMapper = dynamoDBMapper;
    this.dynamoDBMapperConfig = dynamoDBMapperConfig;
    this.mappingContext = mappingContext != null ? mappingContext : new DynamoDBMappingContext();
}
```

**New Constructor:**
```java
public DynamoDBTemplate(DynamoDbClient amazonDynamoDB, DynamoDbEnhancedClient enhancedClient,
        TableNameResolver tableNameResolver, DynamoDBMappingContext mappingContext) {
    Assert.notNull(amazonDynamoDB, "amazonDynamoDB must not be null!");
    Assert.notNull(enhancedClient, "enhancedClient must not be null!");

    this.amazonDynamoDB = amazonDynamoDB;
    this.enhancedClient = enhancedClient;
    this.tableNameResolver = tableNameResolver;
    this.mappingContext = mappingContext != null ? mappingContext : new DynamoDBMappingContext();
}
```

**New Fields:**
```java
private final DynamoDbEnhancedClient enhancedClient;
private final DynamoDbClient amazonDynamoDB;  // Keep for low-level operations
private final TableNameResolver tableNameResolver;  // Custom interface for table name resolution
private final DynamoDBMappingContext mappingContext;
private final Map<Class<?>, DynamoDbTable<?>> tableCache = new ConcurrentHashMap<>();
private ApplicationEventPublisher eventPublisher;
```

**Tasks:**
1. Remove `DynamoDBMapper` field
2. Remove `DynamoDBMapperConfig` field
3. Add `DynamoDbEnhancedClient` field
4. Add `TableNameResolver` field (custom interface)
5. Add `tableCache` field
6. Update constructor signature
7. Update constructor validation
8. Update JavaDoc

**Affected Classes:**
- `DynamoDBRepositoryConfigExtension` - Creates DynamoDBTemplate instances (will need update)
- Test classes that instantiate DynamoDBTemplate

**Verification:**
- Constructor compiles
- Can create DynamoDBTemplate instance with new signature

---

### Phase 2: Helper Methods (1 hour)

**Goal:** Create utility methods for SDK v2 operations

**Add these private methods:**

```java
/**
 * Gets or creates a DynamoDbTable instance for the given domain class.
 * Tables are cached for performance.
 */
@SuppressWarnings("unchecked")
private <T> DynamoDbTable<T> getTable(Class<T> domainClass) {
    return (DynamoDbTable<T>) tableCache.computeIfAbsent(domainClass, clazz -> {
        TableSchema<T> schema = getTableSchema(domainClass);
        String tableName = getTableName(domainClass);
        return enhancedClient.table(tableName, schema);
    });
}

/**
 * Gets the TableSchema for the given domain class.
 * Uses the mappingContext to get table model information.
 */
private <T> TableSchema<T> getTableSchema(Class<T> domainClass) {
    // Implementation will use mappingContext to build TableSchema
    // This is where we'll use the existing getTableModel() logic
    return TableSchema.fromBean(domainClass);
}

/**
 * Gets the table name for the given domain class, applying any overrides.
 */
private <T> String getTableName(Class<T> domainClass) {
    // Extract table name from annotations
    String tableName = extractTableNameFromAnnotations(domainClass);

    // Apply overrides if configured
    if (tableNameResolver != null) {
        tableName = tableNameResolver.resolveTableName(domainClass, tableName);
    }

    return tableName;
}

/**
 * Extracts table name from @DynamoDbBean or entity annotations.
 */
private <T> String extractTableNameFromAnnotations(Class<T> domainClass) {
    // Check for table name in annotations
    // This preserves existing table name logic
    return domainClass.getSimpleName();
}

/**
 * Builds a Key object for SDK v2 operations.
 */
private Key buildKey(Object hashKeyValue, @Nullable Object rangeKeyValue) {
    Key.Builder keyBuilder = Key.builder().partitionValue(toAttributeValue(hashKeyValue));

    if (rangeKeyValue != null) {
        keyBuilder.sortValue(toAttributeValue(rangeKeyValue));
    }

    return keyBuilder.build();
}

/**
 * Converts a Java object to SDK v2 AttributeValue.
 * Respects marshalling mode from mappingContext.
 */
private AttributeValue toAttributeValue(Object value) {
    if (value == null) {
        return AttributeValue.builder().nul(true).build();
    }

    if (value instanceof String) {
        return AttributeValue.builder().s((String) value).build();
    } else if (value instanceof Number) {
        return AttributeValue.builder().n(value.toString()).build();
    } else if (value instanceof Boolean) {
        if (mappingContext.getMarshallingMode() == MarshallingMode.SDK_V1_COMPATIBLE) {
            boolean boolValue = ((Boolean) value).booleanValue();
            return AttributeValue.builder().n(boolValue ? "1" : "0").build();
        } else {
            return AttributeValue.builder().bool((Boolean) value).build();
        }
    }
    // Add more type conversions as needed

    // Fallback: convert to string
    return AttributeValue.builder().s(value.toString()).build();
}
```

**Tasks:**
1. Add `getTable()` method
2. Add `getTableSchema()` method
3. Add `getTableName()` method
4. Add `extractTableNameFromAnnotations()` method
5. Add `buildKey()` method
6. Add `toAttributeValue()` method
7. Create `TableNameResolver` interface in same package

**TableNameResolver Interface:**
```java
package org.socialsignin.spring.data.dynamodb.core;

/**
 * Strategy interface for resolving DynamoDB table names.
 * Replaces SDK v1 DynamoDBMapperConfig.TableNameOverride functionality.
 */
public interface TableNameResolver {
    /**
     * Resolves the table name for a given domain class.
     *
     * @param domainClass The domain class
     * @param baseTableName The base table name from annotations
     * @return The resolved table name (may include prefix/override)
     */
    <T> String resolveTableName(Class<T> domainClass, String baseTableName);
}
```

**Verification:**
- Helper methods compile
- Can get DynamoDbTable instance
- Can build Key objects
- Can convert Java objects to AttributeValue

---

### Phase 3: Simple CRUD Operations (1.5 hours)

**Goal:** Migrate save(), load(), delete() methods

#### 3.1 save() Method (Lines 128-133)

**Current Implementation:**
```java
@Override
public <T> T save(T entity) {
    maybeEmitEvent(entity, BeforeSaveEvent::new);
    dynamoDBMapper.save(entity);
    maybeEmitEvent(entity, AfterSaveEvent::new);
    return entity;
}
```

**New Implementation:**
```java
@Override
public <T> T save(T entity) {
    maybeEmitEvent(entity, BeforeSaveEvent::new);

    @SuppressWarnings("unchecked")
    DynamoDbTable<T> table = (DynamoDbTable<T>) getTable(entity.getClass());
    table.putItem(entity);

    maybeEmitEvent(entity, AfterSaveEvent::new);
    return entity;
}
```

**Complexity:** LOW
**Risk:** LOW (straightforward mapping)

#### 3.2 load() Methods (Lines 95-108)

**Current Implementation:**
```java
@Override
public <T> T load(Class<T> domainClass, Object hashKey, Object rangeKey) {
    T entity = dynamoDBMapper.load(domainClass, hashKey, rangeKey);
    maybeEmitEvent(entity, AfterLoadEvent::new);
    return entity;
}

@Override
public <T> T load(Class<T> domainClass, Object hashKey) {
    T entity = dynamoDBMapper.load(domainClass, hashKey);
    maybeEmitEvent(entity, AfterLoadEvent::new);
    return entity;
}
```

**New Implementation:**
```java
@Override
public <T> T load(Class<T> domainClass, Object hashKey, Object rangeKey) {
    DynamoDbTable<T> table = getTable(domainClass);
    Key key = buildKey(hashKey, rangeKey);
    T entity = table.getItem(key);
    maybeEmitEvent(entity, AfterLoadEvent::new);
    return entity;
}

@Override
public <T> T load(Class<T> domainClass, Object hashKey) {
    DynamoDbTable<T> table = getTable(domainClass);
    Key key = buildKey(hashKey, null);
    T entity = table.getItem(key);
    maybeEmitEvent(entity, AfterLoadEvent::new);
    return entity;
}
```

**Complexity:** MEDIUM
**Risk:** MEDIUM (Key building needs testing)

#### 3.3 delete() Method (Lines 147-152)

**Current Implementation:**
```java
@Override
public <T> T delete(T entity) {
    maybeEmitEvent(entity, BeforeDeleteEvent::new);
    dynamoDBMapper.delete(entity);
    maybeEmitEvent(entity, AfterDeleteEvent::new);
    return entity;
}
```

**New Implementation:**
```java
@Override
public <T> T delete(T entity) {
    maybeEmitEvent(entity, BeforeDeleteEvent::new);

    @SuppressWarnings("unchecked")
    DynamoDbTable<T> table = (DynamoDbTable<T>) getTable(entity.getClass());
    table.deleteItem(entity);

    maybeEmitEvent(entity, AfterDeleteEvent::new);
    return entity;
}
```

**Complexity:** LOW
**Risk:** LOW

**Verification:**
- Can save entities
- Can load entities by hash key
- Can load entities by hash + range key
- Can delete entities
- Events are published correctly

---

### Phase 4: Batch Operations (2 hours)

**Goal:** Migrate batchLoad(), batchSave(), batchDelete()

#### 4.1 batchLoad() (Lines 119-125)

**Current Implementation:**
```java
@SuppressWarnings("unchecked")
@Override
public <T> List<T> batchLoad(Map<Class<?>, List<KeyPair>> itemsToGet) {
    return dynamoDBMapper.batchLoad(itemsToGet).values().stream()
        .flatMap(v -> v.stream())
        .map(e -> (T) e)
        .map(entity -> {
            maybeEmitEvent(entity, AfterLoadEvent::new);
            return entity;
        })
        .collect(Collectors.toList());
}
```

**New Implementation:**
```java
@SuppressWarnings("unchecked")
@Override
public <T> List<T> batchLoad(Map<Class<?>, List<Key>> itemsToGet) {
    List<T> results = new ArrayList<>();

    // SDK v2 Enhanced Client requires separate batch requests per table
    for (Map.Entry<Class<?>, List<Key>> entry : itemsToGet.entrySet()) {
        Class<?> domainClass = entry.getKey();
        List<Key> keys = entry.getValue();

        DynamoDbTable<?> table = getTable(domainClass);

        // Create batch get request
        BatchGetItemEnhancedRequest.Builder requestBuilder = BatchGetItemEnhancedRequest.builder();

        for (Key key : keys) {
            requestBuilder.addGetItem(table, key);
        }

        // Execute batch get
        BatchGetResultPageIterable resultPages = enhancedClient.batchGetItem(requestBuilder.build());

        for (BatchGetResultPage page : resultPages) {
            List<?> pageResults = page.resultsForTable(table);
            for (Object entity : pageResults) {
                maybeEmitEvent(entity, AfterLoadEvent::new);
                results.add((T) entity);
            }
        }
    }

    return results;
}
```

**Complexity:** HIGH
**Risk:** HIGH (Different batch API structure)
**Note:** SDK v2 batch operations work differently - may need to group by table

#### 4.2 batchSave() (Lines 137-144)

**Current Implementation:**
```java
@Override
public List<FailedBatch> batchSave(Iterable<?> entities) {
    entities.forEach(it -> maybeEmitEvent(it, BeforeSaveEvent::new));
    List<FailedBatch> result = dynamoDBMapper.batchSave(entities);
    entities.forEach(it -> maybeEmitEvent(it, AfterSaveEvent::new));
    return result;
}
```

**New Implementation:**
```java
@Override
public List<BatchWriteResult> batchSave(Iterable<?> entities) {
    entities.forEach(it -> maybeEmitEvent(it, BeforeSaveEvent::new));

    // Group entities by class for batch operations
    Map<Class<?>, List<Object>> entitiesByClass = new HashMap<>();
    for (Object entity : entities) {
        entitiesByClass.computeIfAbsent(entity.getClass(), k -> new ArrayList<>()).add(entity);
    }

    List<BatchWriteResult> results = new ArrayList<>();

    // SDK v2: Create batch write request
    BatchWriteItemEnhancedRequest.Builder requestBuilder = BatchWriteItemEnhancedRequest.builder();

    for (Map.Entry<Class<?>, List<Object>> entry : entitiesByClass.entrySet()) {
        DynamoDbTable table = getTable(entry.getKey());

        for (Object entity : entry.getValue()) {
            requestBuilder.addPutItem(table, entity);
        }
    }

    // Execute with retry handling
    BatchWriteResult result = enhancedClient.batchWriteItem(requestBuilder.build());
    results.add(result);

    // Handle unprocessed items if any (automatic retry already done by SDK)

    entities.forEach(it -> maybeEmitEvent(it, AfterSaveEvent::new));
    return results;
}
```

**Complexity:** HIGH
**Risk:** HIGH (Error handling structure changed)
**Note:** SDK v2 has automatic retry built-in, but we need to check for unprocessed items

#### 4.3 batchDelete() (Lines 155-162)

**Similar pattern to batchSave(), but using deleteItem instead of putItem**

**New Implementation:**
```java
@Override
public List<BatchWriteResult> batchDelete(Iterable<?> entities) {
    entities.forEach(it -> maybeEmitEvent(it, BeforeDeleteEvent::new));

    // Group entities by class
    Map<Class<?>, List<Object>> entitiesByClass = new HashMap<>();
    for (Object entity : entities) {
        entitiesByClass.computeIfAbsent(entity.getClass(), k -> new ArrayList<>()).add(entity);
    }

    List<BatchWriteResult> results = new ArrayList<>();

    BatchWriteItemEnhancedRequest.Builder requestBuilder = BatchWriteItemEnhancedRequest.builder();

    for (Map.Entry<Class<?>, List<Object>> entry : entitiesByClass.entrySet()) {
        DynamoDbTable table = getTable(entry.getKey());

        for (Object entity : entry.getValue()) {
            requestBuilder.addDeleteItem(table, entity);
        }
    }

    BatchWriteResult result = enhancedClient.batchWriteItem(requestBuilder.build());
    results.add(result);

    entities.forEach(it -> maybeEmitEvent(it, AfterDeleteEvent::new));
    return results;
}
```

**Complexity:** HIGH
**Risk:** HIGH

**Verification:**
- Can batch load multiple entities
- Can batch save multiple entities
- Can batch delete multiple entities
- Error handling works correctly
- Events are published for all entities

---

### Phase 5: Query and Scan Operations (2 hours)

**Goal:** Add implementations for QueryEnhancedRequest and ScanEnhancedRequest methods

**Note:** These methods are defined in DynamoDBOperations interface but **NOT implemented** in DynamoDBTemplate!

#### 5.1 Add count(QueryEnhancedRequest) (NEW METHOD)

```java
@Override
public <T> int count(Class<T> domainClass, QueryEnhancedRequest queryExpression) {
    DynamoDbTable<T> table = getTable(domainClass);

    // SDK v2 doesn't have a direct count for queries, need to iterate
    PageIterable<T> results = table.query(queryExpression);

    int count = 0;
    for (Page<T> page : results) {
        count += page.items().size();
    }

    return count;
}
```

**Complexity:** MEDIUM
**Risk:** MEDIUM (No direct count API in SDK v2)

#### 5.2 Add count(ScanEnhancedRequest) (NEW METHOD)

```java
@Override
public <T> int count(Class<T> domainClass, ScanEnhancedRequest scanExpression) {
    DynamoDbTable<T> table = getTable(domainClass);

    PageIterable<T> results = table.scan(scanExpression);

    int count = 0;
    for (Page<T> page : results) {
        count += page.items().size();
    }

    return count;
}
```

**Complexity:** MEDIUM
**Risk:** MEDIUM

#### 5.3 Add query(QueryEnhancedRequest) (NEW METHOD)

```java
@Override
public <T> PageIterable<T> query(Class<T> domainClass, QueryEnhancedRequest queryExpression) {
    DynamoDbTable<T> table = getTable(domainClass);
    PageIterable<T> results = table.query(queryExpression);

    // Emit events for results (lazy evaluation issue - may need eager evaluation)
    // TODO: Consider event publishing strategy for PageIterable

    return results;
}
```

**Complexity:** MEDIUM
**Risk:** MEDIUM (Event publishing timing)

#### 5.4 Add scan(ScanEnhancedRequest) (NEW METHOD)

```java
@Override
public <T> PageIterable<T> scan(Class<T> domainClass, ScanEnhancedRequest scanExpression) {
    DynamoDbTable<T> table = getTable(domainClass);
    PageIterable<T> results = table.scan(scanExpression);

    // TODO: Consider event publishing strategy

    return results;
}
```

**Complexity:** MEDIUM
**Risk:** MEDIUM

#### 5.5 Update query(QueryRequest) (Lines 165-178)

**Current Implementation:**
```java
@Override
public <T> PaginatedQueryList<T> query(Class<T> clazz, QueryRequest queryRequest) {
    QueryResponse queryResult = amazonDynamoDB.query(queryRequest);

    if (queryRequest.limit() != null) {
        queryResult = queryResult.toBuilder().lastEvaluatedKey(null).build();
    }

    return new PaginatedQueryList<T>(dynamoDBMapper, clazz, amazonDynamoDB,
        queryRequest, queryResult,
        dynamoDBMapperConfig.getPaginationLoadingStrategy(),
        dynamoDBMapperConfig);
}
```

**Problem:** Returns SDK v1 `PaginatedQueryList` but interface expects `PageIterable`

**New Implementation:**
```java
@Override
public <T> PageIterable<T> query(Class<T> clazz, QueryRequest queryRequest) {
    // Use low-level client to execute query
    // Convert results to PageIterable

    // Option 1: Convert to eager list and wrap
    List<T> results = new ArrayList<>();
    QueryResponse queryResult = amazonDynamoDB.query(queryRequest);

    // Deserialize items
    DynamoDbTable<T> table = getTable(clazz);
    TableSchema<T> schema = getTableSchema(clazz);

    for (Map<String, AttributeValue> item : queryResult.items()) {
        T entity = schema.mapToItem(item);
        results.add(entity);
    }

    // Handle pagination if needed
    while (queryResult.lastEvaluatedKey() != null && !queryResult.lastEvaluatedKey().isEmpty()) {
        queryRequest = queryRequest.toBuilder()
            .exclusiveStartKey(queryResult.lastEvaluatedKey())
            .build();
        queryResult = amazonDynamoDB.query(queryRequest);

        for (Map<String, AttributeValue> item : queryResult.items()) {
            T entity = schema.mapToItem(item);
            results.add(entity);
        }

        if (queryRequest.limit() != null) {
            break; // Respect limit
        }
    }

    // Wrap in PageIterable (may need custom implementation)
    return createPageIterable(results);
}

private <T> PageIterable<T> createPageIterable(List<T> items) {
    // Create a simple PageIterable wrapper
    // This is a compatibility shim
    // TODO: Implement custom PageIterable wrapper
    throw new UnsupportedOperationException("Not yet implemented");
}
```

**Complexity:** HIGH
**Risk:** HIGH (Complex conversion, may need custom PageIterable implementation)

**Verification:**
- Can query with QueryEnhancedRequest
- Can scan with ScanEnhancedRequest
- Can count query/scan results
- Can query with low-level QueryRequest
- PageIterable works correctly

---

### Phase 6: Configuration Methods (1 hour)

**Goal:** Migrate getOverriddenTableName() and getTableModel()

#### 6.1 getOverriddenTableName() (Lines 197-209)

**Current Implementation:**
```java
@Override
public <T> String getOverriddenTableName(Class<T> domainClass, String tableName) {
    if (dynamoDBMapperConfig.getTableNameOverride() != null) {
        if (dynamoDBMapperConfig.getTableNameOverride().getTableName() != null) {
            tableName = dynamoDBMapperConfig.getTableNameOverride().getTableName();
        } else {
            tableName = dynamoDBMapperConfig.getTableNameOverride().getTableNamePrefix() + tableName;
        }
    } else if (dynamoDBMapperConfig.getTableNameResolver() != null) {
        tableName = dynamoDBMapperConfig.getTableNameResolver().getTableName(domainClass, dynamoDBMapperConfig);
    }

    return tableName;
}
```

**New Implementation:**
```java
@Override
public <T> String getOverriddenTableName(Class<T> domainClass, String tableName) {
    if (tableNameResolver != null) {
        return tableNameResolver.resolveTableName(domainClass, tableName);
    }
    return tableName;
}
```

**Complexity:** LOW (already using custom interface)
**Risk:** LOW

#### 6.2 getTableModel() (Lines 215-217)

**Current Implementation:**
```java
@Override
public <T> DynamoDBMapperTableModel<T> getTableModel(Class<T> domainClass) {
    return dynamoDBMapper.getTableModel(domainClass, dynamoDBMapperConfig);
}
```

**New Implementation:**
```java
@Override
public <T> TableSchema<T> getTableModel(Class<T> domainClass) {
    return getTableSchema(domainClass);
}
```

**Complexity:** LOW (uses helper method)
**Risk:** LOW

**Verification:**
- Table name resolution works
- Can get TableSchema for any domain class

---

### Phase 7: Update Imports and Cleanup (30 minutes)

**Goal:** Remove SDK v1 imports and clean up code

**Remove These Imports:**
```java
import com.amazonaws.services.dynamodbv2.datamodeling.*;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper.FailedBatch;
```

**Add These Imports:**
```java
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
```

**Update JavaDoc:**
- Update class-level documentation
- Update constructor documentation
- Update method documentation where SDK v1 types were mentioned

**Cleanup:**
- Remove unused variables
- Remove unnecessary suppressions
- Format code
- Check for compilation warnings

**Verification:**
- No SDK v1 imports remain
- All imports are organized
- JavaDoc is updated
- Code compiles without warnings

---

## Dependency Updates Required

### DynamoDBRepositoryConfigExtension

**File:** `src/main/java/org/socialsignin/spring/data/dynamodb/repository/config/DynamoDBRepositoryConfigExtension.java`

**Changes Needed:**
1. Update DynamoDBTemplate bean creation (currently lines 156-177)
2. Remove DynamoDBMapper bean creation
3. Remove DynamoDBMapperConfig bean creation
4. Add DynamoDbEnhancedClient bean creation
5. Add TableNameResolver bean creation (if configured)

**Current Code (Lines 156-177):**
```java
BeanDefinitionBuilder dynamoDBTemplateBuilder = BeanDefinitionBuilder
    .genericBeanDefinition(DynamoDBTemplate.class);
dynamoDBTemplateBuilder.addConstructorArgReference(dynamoDBRef);

if (StringUtils.hasText(dynamoDBMapperRef)) {
    dynamoDBTemplateBuilder.addConstructorArgReference(dynamoDBMapperRef);
} else {
    dynamoDBTemplateBuilder.addConstructorArgReference(this.dynamoDBMapperName);
}

if (StringUtils.hasText(dynamoDBMapperConfigRef)) {
    dynamoDBTemplateBuilder.addConstructorArgReference(dynamoDBMapperConfigRef);
} else {
    dynamoDBTemplateBuilder.addConstructorArgReference(this.dynamoDBMapperConfigName);
}

dynamoDBTemplateBuilder.addConstructorArgReference(finalDynamoDBMappingContextRef);
```

**New Code:**
```java
BeanDefinitionBuilder dynamoDBTemplateBuilder = BeanDefinitionBuilder
    .genericBeanDefinition(DynamoDBTemplate.class);

// DynamoDbClient
dynamoDBTemplateBuilder.addConstructorArgReference(dynamoDBRef);

// DynamoDbEnhancedClient
dynamoDBTemplateBuilder.addConstructorArgReference(this.enhancedClientName);

// TableNameResolver (optional)
if (StringUtils.hasText(tableNameResolverRef)) {
    dynamoDBTemplateBuilder.addConstructorArgReference(tableNameResolverRef);
} else {
    dynamoDBTemplateBuilder.addConstructorArgValue(null);
}

// DynamoDBMappingContext
dynamoDBTemplateBuilder.addConstructorArgReference(finalDynamoDBMappingContextRef);
```

**New Bean Definitions Needed:**
```java
// Add DynamoDbEnhancedClient bean
private String registerDynamoDbEnhancedClient(BeanDefinitionRegistry registry, String dynamoDBRef) {
    BeanDefinitionBuilder enhancedClientBuilder = BeanDefinitionBuilder
        .genericBeanDefinition(DynamoDbEnhancedClientFactory.class);
    enhancedClientBuilder.addConstructorArgReference(dynamoDBRef);

    String enhancedClientName = getBeanNameWithModulePrefix("DynamoDbEnhancedClient");
    registry.registerBeanDefinition(enhancedClientName, enhancedClientBuilder.getBeanDefinition());

    return enhancedClientName;
}
```

**Complexity:** MEDIUM
**Risk:** MEDIUM (affects all Spring configuration)

---

## Testing Strategy

### Unit Tests

Create/update tests for each method:

1. **Constructor Tests**
   - Test with valid parameters
   - Test with null parameters (should fail)
   - Test enhanced client creation

2. **CRUD Operation Tests**
   - save() with various entity types
   - load() with hash key only
   - load() with hash + range key
   - delete() with entity

3. **Batch Operation Tests**
   - batchLoad() with multiple classes
   - batchSave() with success
   - batchSave() with failures
   - batchDelete() with success
   - batchDelete() with failures

4. **Query/Scan Tests**
   - query() with QueryEnhancedRequest
   - scan() with ScanEnhancedRequest
   - count() with various requests
   - Pagination handling

5. **Configuration Tests**
   - Table name resolution
   - Table schema retrieval
   - Table caching

### Integration Tests

1. Test against LocalStack or DynamoDB Local
2. Test with real entity classes
3. Test event publishing
4. Test error scenarios
5. Test pagination with large datasets

---

## Risk Mitigation

### High-Risk Areas

1. **Pagination Behavior Change**
   - **Risk:** PaginatedQueryList has lazy loading, PageIterable behaves differently
   - **Mitigation:**
     - Add extensive pagination tests
     - Document behavioral changes
     - Consider eager evaluation option for compatibility

2. **Type Conversion**
   - **Risk:** Converting between Java types and AttributeValue may have subtle differences
   - **Mitigation:**
     - Reuse existing marshalling logic from MarshallingMode
     - Test all supported types
     - Handle edge cases (null, empty strings, etc.)

3. **Batch Operation Errors**
   - **Risk:** FailedBatch → BatchWriteResult has different structure
   - **Mitigation:**
     - Comprehensive error handling tests
     - Document error structure changes
     - Ensure unprocessed items are handled

4. **Table Name Resolution**
   - **Risk:** Custom table name logic may not work the same
   - **Mitigation:**
     - Preserve all existing table name override logic
     - Test with various configurations
     - Document any behavioral changes

5. **Event Publishing**
   - **Risk:** Event timing may change with lazy PageIterable
   - **Mitigation:**
     - Review event publishing strategy
     - Consider eager evaluation for events
     - Test event order and timing

### Rollback Plan

If migration fails:
1. Keep SDK v1 dependencies in pom.xml temporarily
2. Create feature branch for migration
3. Test extensively before merging
4. Have ability to revert commit if issues found

---

## Success Criteria

### Compilation
- [ ] DynamoDBTemplate compiles without errors
- [ ] All implementing classes compile
- [ ] No SDK v1 imports remain in DynamoDBTemplate

### Functionality
- [ ] All 15 methods work correctly
- [ ] CRUD operations work
- [ ] Batch operations work
- [ ] Query/scan operations work
- [ ] Events are published correctly

### Testing
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] New tests added for SDK v2 behavior
- [ ] Edge cases tested

### Documentation
- [ ] JavaDoc updated
- [ ] Migration notes documented
- [ ] Behavioral changes documented

### Performance
- [ ] No significant performance degradation
- [ ] Table caching works correctly
- [ ] Pagination is efficient

---

## Timeline Estimate

| Phase | Task | Time | Cumulative |
|-------|------|------|------------|
| 0 | Preparation & Research | 30 min | 30 min |
| 1 | Constructor & Core Fields | 1 hour | 1.5 hours |
| 2 | Helper Methods | 1 hour | 2.5 hours |
| 3 | Simple CRUD Operations | 1.5 hours | 4 hours |
| 4 | Batch Operations | 2 hours | 6 hours |
| 5 | Query/Scan Operations | 2 hours | 8 hours |
| 6 | Configuration Methods | 1 hour | 9 hours |
| 7 | Imports & Cleanup | 30 min | 9.5 hours |
| - | Testing & Fixes | 2-3 hours | **11-12 hours** |
| - | Documentation | 1 hour | **12-13 hours** |

**Total Estimated Time:** 12-13 hours of focused work

**Recommended Schedule:**
- **Day 1 (4 hours):** Phases 0-2 (prep, constructor, helpers)
- **Day 2 (4 hours):** Phase 3 (CRUD operations) + testing
- **Day 3 (5 hours):** Phases 4-7 (batch, query, config, cleanup) + final testing

---

## Open Questions

1. **PageIterable vs PaginatedQueryList:**
   - Should we create a compatibility wrapper?
   - Should we eagerly evaluate for event publishing?
   - How to handle lazy loading semantics?

2. **TableSchema Creation:**
   - Should we use `TableSchema.fromBean()` or `TableSchema.fromClass()`?
   - How to handle custom attribute converters?
   - How to integrate with existing MarshallingMode?

3. **Error Handling:**
   - Should we wrap SDK v2 exceptions?
   - How to maintain backward compatibility with error messages?
   - Should we add retry logic for batch operations?

4. **Table Name Resolution:**
   - Should TableNameResolver be optional or required?
   - How to handle table name prefixes?
   - Should we support table name overrides per operation?

5. **Event Publishing:**
   - When to publish events for PageIterable results?
   - Should we add new event types for SDK v2?
   - How to handle event publishing with streaming results?

---

## Additional Considerations

### Feature Parity

Ensure SDK v2 implementation provides same features as SDK v1:
- ✓ CRUD operations
- ✓ Batch operations
- ✓ Query/scan operations
- ✓ Table name overrides
- ✓ Event publishing
- ? Pagination strategy configuration
- ? Consistent read configuration
- ? Conditional writes/updates

### Performance Considerations

- Table caching should improve performance
- Batch operations may have different performance characteristics
- PageIterable lazy loading vs PaginatedList eager loading

### Backward Compatibility

- Interface signatures already changed (by design)
- Behavioral changes should be documented
- Migration guide should be updated

---

## Next Steps (Tomorrow)

1. **Review this plan** - Identify any gaps or concerns
2. **Set up test environment** - Ensure LocalStack or DynamoDB Local is available
3. **Create feature branch** - Work in isolation
4. **Start Phase 0** - Research and helper method setup
5. **Iterate through phases** - One at a time, testing as we go

---

## Notes

- Keep original PaginatedQueryList classes for now (used by other classes)
- Don't remove SDK v1 dependencies from pom.xml until everything compiles
- Commit frequently with clear messages
- Run tests after each phase
- Update this plan as we discover issues

---

**End of Migration Plan**
