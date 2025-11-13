# Spring Data DynamoDB - Complete SDK v2 Migration Plan

**Date Created:** 2025-11-12
**Current Branch:** migrate-sdk-v1-to-v2
**Target Version:** 7.0.0
**Objective:** Complete migration from AWS SDK v1 to v2 without cutting corners, maintaining behavioral and functional equivalence

---

## Executive Summary

### Current Status
- **Migration Progress:** ~35% complete
- **Files Migrated:** 30 files (interfaces, query criteria, mapping context)
- **Files Remaining:** 78 files (17 main source + 61 test files)
- **Critical Blocker:** DynamoDBTemplate still uses SDK v1 DynamoDBMapper internally

### Key Achievement So Far
- ✅ DynamoDBOperations interface fully migrated to SDK v2 signatures
- ✅ Query criteria classes migrated (AbstractDynamoDBQueryCriteria, Entity criteria)
- ✅ DynamoDBMappingContext migrated with configurable marshalling modes
- ✅ Marshalling mode system implemented (SDK_V2_NATIVE default, SDK_V1_COMPATIBLE opt-in)
- ✅ Type marshalling research completed and documented

### Remaining Work
The migration is in a **hybrid state** where:
- Interface layer uses SDK v2 types (QueryEnhancedRequest, ScanEnhancedRequest, Key, TableSchema)
- Implementation layer still uses SDK v1 (DynamoDBMapper, DynamoDBMapperConfig)
- Tests are mixed (some updated, most still use SDK v1 mocks)

### Critical Path to Completion
**DynamoDBTemplate** is the central bottleneck. Once migrated, all dependent classes can be updated rapidly.

---

## Complete File Inventory

### Already Migrated (30 files) ✅
1. DynamoDBOperations (interface)
2. AbstractDynamoDBQueryCriteria
3. DynamoDBEntityWithHashKeyOnlyCriteria
4. DynamoDBEntityWithHashAndRangeKeyCriteria
5. DynamoDBMappingContext
6. DynamoDBRepositoryFactory
7. DynamoDBRepositoryConfigExtension
8. DynamoDBEntityMetadataSupport
9. DynamoDBHashAndRangeKeyExtractingEntityMetadataImpl
10. DynamoDBIdIsHashAndRangeKeyEntityInformationImpl
11. Entity2DynamoDBTableSynchronizer
12. ExceptionHandler
13. DynamoDBRepositoryExtension (CDI)
14. MultipleEntityQueryRequestQuery
15. MultipleEntityQueryExpressionQuery
16. MultipleEntityScanExpressionQuery
17. QueryRequestCountQuery
18. QueryExpressionCountQuery
19. ScanExpressionCountQuery
20. DynamoDBQueryCreator
21. DynamoDBCountQueryCreator
22. AbstractDynamoDBQueryCreator
23. MarshallingMode (enum)
24. AbstractDynamoDBConfiguration
25. DynamoDBLocalResource (test infrastructure)
26. Plus 5 other support classes

### Main Source Files to Migrate (17 files) ⚠️

#### Priority 0: Critical - Must Do First (2 files)
1. **DynamoDBTemplate.java** - 237 lines
   - Core operations wrapper using DynamoDBMapper
   - Implements DynamoDBOperations interface
   - All CRUD, batch, query, scan operations
   - **Estimated Time:** 8-10 hours
   - **Complexity:** HIGH

2. **DynamoDBMapperFactory.java** - 50 lines
   - Creates DynamoDBMapper instances
   - Used by configuration layer
   - **Estimated Time:** 1-2 hours (or DELETE if not needed)
   - **Complexity:** LOW

#### Priority 1: High - Depends on DynamoDBTemplate (5 files)
3. **SimpleDynamoDBCrudRepository.java** - 150 lines
   - Uses KeyPair for batch operations
   - Returns FailedBatch from batch methods
   - **Estimated Time:** 3-4 hours
   - **Complexity:** HIGH

4. **SimpleDynamoDBPagingAndSortingRepository.java** - 80 lines
   - Uses PaginatedScanList
   - Depends on SimpleDynamoDBCrudRepository
   - **Estimated Time:** 2 hours
   - **Complexity:** MEDIUM

5. **AbstractDynamoDBQuery.java** - 300 lines
   - Batch delete returns FailedBatch
   - Query execution framework
   - **Estimated Time:** 3 hours
   - **Complexity:** MEDIUM

6. **DynamoDBMapperConfigFactory.java** - 120 lines
   - Creates DynamoDBMapperConfig
   - Config validation and merging
   - **Estimated Time:** 2-3 hours (or REPLACE with TableNameResolver)
   - **Complexity:** MEDIUM

7. **DynamoDBRepositoryBean.java** (CDI) - 150 lines
   - CDI integration
   - **Estimated Time:** 2 hours
   - **Complexity:** MEDIUM

#### Priority 2: Medium - Support Classes (5 files)
8. **DynamoDBHashAndRangeKeyMethodExtractorImpl.java** - 120 lines
   - Reflection-based annotation extraction
   - Uses @DynamoDBHashKey, @DynamoDBRangeKey
   - **Estimated Time:** 2 hours
   - **Complexity:** MEDIUM

9. **DynamoDBIdIsHashKeyEntityInformationImpl.java** - 60 lines
   - Entity metadata for hash-key-only entities
   - **Estimated Time:** 1 hour
   - **Complexity:** LOW

10. **DynamoDBHashAndRangeKey.java** - 80 lines
    - Utility class with SDK v1 annotations
    - **Estimated Time:** 1 hour
    - **Complexity:** LOW

11. **DynamoDBPersistentPropertyImpl.java** - 200 lines
    - Uses @DynamoDBHashKey, @DynamoDBIgnore, @DynamoDBVersionAttribute
    - **Estimated Time:** 2-3 hours
    - **Complexity:** MEDIUM

12. **Entity2DynamoDBTableSynchronizer.java** - 80 lines (already partially migrated)
    - Table synchronization utility
    - **Estimated Time:** 1 hour (review and finish)
    - **Complexity:** LOW

#### Priority 3: Low - Marshallers (5 files)
13-17. **Marshaller Classes** (5 files total)
    - Date2IsoDynamoDBMarshaller.java
    - Date2EpocheDynamoDBMarshaller.java
    - Instant2IsoDynamoDBMarshaller.java
    - Instant2EpocheDynamoDBMarshaller.java
    - AbstractDynamoDBDateMarshaller.java
    - Implement AttributeConverter instead of DynamoDBMarshaller
    - **Estimated Time:** 3-4 hours total (might not be needed)
    - **Complexity:** LOW

### Test Files to Update (61 files) 🧪

#### Unit Tests - High Priority (8 files)
1. **DynamoDBTemplateTest.java** - Extensive DynamoDBMapper mocking
2. **PartTreeDynamoDBQueryUnitTest.java** - Query expression tests
3. **SimpleDynamoDBCrudRepositoryTest.java** - CRUD operations
4. **DynamoDBMapperConfigFactoryTest.java** - Config factory tests
5. **Entity2DynamoDBTableSynchronizerTest.java** - Table sync tests
6. **DynamoDBRepositoryFactoryBeanTest.java** - Factory bean tests
7. **QueryExpressionCountQueryTest.java** - Count query tests
8. **ScanExpressionCountQueryTest.java** - Scan count tests
   - **Estimated Time:** 6-8 hours total
   - **Complexity:** MEDIUM

#### Unit Tests - Medium Priority (12 files)
9-20. Event listener tests, mapping tests, property tests, etc.
   - **Estimated Time:** 6-8 hours total
   - **Complexity:** LOW-MEDIUM

#### Integration Tests - Review and Update (27 files)
21-47. Integration tests (most already use SDK v2 client, need review)
   - TransactionalOperationsIntegrationTest.java - PARTIAL update needed
   - AdvancedQueryPatternsIntegrationTest.java - Update needed
   - ErrorRecoveryIntegrationTest.java - PARTIAL update needed
   - BatchOperationsAtScaleIntegrationTest.java - Update needed
   - ProjectionTypesIntegrationTest.java - Update needed
   - EnumTypesIntegrationTest.java - Update needed
   - Plus 21 other integration tests
   - **Estimated Time:** 8-12 hours total
   - **Complexity:** LOW-MEDIUM (most just need verification)

#### Test Entity Classes (26 files)
48-73. Domain entity classes with SDK v1 annotations
   - User.java, Playlist.java, Product.java, Task.java, etc.
   - Simple annotation replacements (@DynamoDBTable → @DynamoDbBean, etc.)
   - **Estimated Time:** 3-4 hours total
   - **Complexity:** LOW

#### Custom Test Implementations (1 file)
74. **DocumentMetadataRepositoryImpl.java**
   - Custom repository with parallel query execution
   - Uses DynamoDBQueryExpression and PaginatedQueryList
   - **Estimated Time:** 1-2 hours
   - **Complexity:** MEDIUM

---

## Migration Strategy: 5-Phase Approach

### Phase 1: DynamoDBTemplate Migration (Day 1 - Morning)
**Duration:** 8-10 hours
**Files:** 1 critical file
**Goal:** Replace DynamoDBMapper with DynamoDbEnhancedClient

#### 1.1 Preparation (30 min)
- Create TableNameResolver interface
- Review Enhanced Client patterns
- Set up helper method signatures

#### 1.2 Core Implementation (6 hours)
- Replace constructor parameters (DynamoDBMapper → DynamoDbEnhancedClient)
- Implement helper methods:
  - `getTable(Class<T>)` - Get/cache DynamoDbTable instances
  - `buildKey(hashKey, rangeKey)` - Build Key objects
  - `getTableSchema(Class<T>)` - Get TableSchema instances
  - `toAttributeValue(Object)` - Convert Java objects to AttributeValue
- Migrate CRUD operations:
  - save() → table.putItem()
  - load() → table.getItem(key)
  - delete() → table.deleteItem()
- Migrate batch operations:
  - batchLoad() → BatchGetItemEnhancedRequest
  - batchSave() → BatchWriteItemEnhancedRequest
  - batchDelete() → BatchWriteItemEnhancedRequest
- Migrate query/scan:
  - query(QueryRequest) → Use PageIterable
  - count() methods → Already using SDK v2

#### 1.3 Configuration Methods (1 hour)
- Update getOverriddenTableName() to use TableNameResolver
- Update getTableModel() to return TableSchema<T>

#### 1.4 Testing (1-2 hours)
- Update DynamoDBTemplateTest.java
- Run integration tests
- Verify all operations work

#### 1.5 Deliverables
- ✅ DynamoDBTemplate fully migrated
- ✅ TableNameResolver interface created
- ✅ DynamoDBTemplateTest passing
- ✅ Integration tests passing

---

### Phase 2: Repository Implementations (Day 1 - Afternoon)
**Duration:** 6-8 hours
**Files:** 3 core repository files
**Goal:** Update repository implementations to use SDK v2 types

#### 2.1 SimpleDynamoDBCrudRepository (3-4 hours)
- Remove KeyPair usage in batchLoad
- Update to use Key objects from SDK v2
- Change FailedBatch return type to BatchWriteResult
- Update batch operation logic
- Test: SimpleDynamoDBCrudRepositoryTest.java

#### 2.2 SimpleDynamoDBPagingAndSortingRepository (2 hours)
- Update PaginatedScanList usage to PageIterable
- Update scan operations
- Verify paging behavior
- Test: Paging integration tests

#### 2.3 AbstractDynamoDBQuery (2 hours)
- Update batch delete to return BatchWriteResult
- Verify query execution framework
- Test: Query unit tests

#### 2.4 Deliverables
- ✅ All repository implementations using SDK v2
- ✅ Repository unit tests passing
- ✅ Basic integration tests passing

---

### Phase 3: Configuration and Factory Classes (Day 2 - Morning)
**Duration:** 4-6 hours
**Files:** 3 configuration files
**Goal:** Update or remove factory classes

#### 3.1 DynamoDBMapperFactory (1 hour)
**Decision Point:** DELETE or MIGRATE?
- Option A: Delete entirely (no longer need DynamoDBMapper)
- Option B: Keep as compatibility layer (not recommended)
- **Recommendation:** DELETE and update dependent code

#### 3.2 DynamoDBMapperConfigFactory (2-3 hours)
**Decision Point:** REPLACE or MIGRATE?
- Option A: Replace with TableNameResolverFactory
- Option B: Migrate to Enhanced Client config
- **Recommendation:** REPLACE with simpler TableNameResolver pattern

Create new classes:
- TableNameResolver interface (already created in Phase 1)
- DefaultTableNameResolver implementation
- TableNamePrefixResolver implementation
- TableNameOverrideResolver implementation

#### 3.3 DynamoDBRepositoryBean (CDI) (2 hours)
- Update CDI bean creation
- Use DynamoDbEnhancedClient instead of DynamoDBMapper
- Update injection points
- Test: CDI integration tests

#### 3.4 Deliverables
- ✅ Factory classes migrated or removed
- ✅ TableNameResolver implementations created
- ✅ Configuration tests passing
- ✅ CDI integration working

---

### Phase 4: Support Classes and Utilities (Day 2 - Afternoon)
**Duration:** 6-8 hours
**Files:** 7 support files
**Goal:** Complete migration of metadata and utility classes

#### 4.1 Annotation Extraction (3 hours)
- DynamoDBHashAndRangeKeyMethodExtractorImpl.java
  - Replace @DynamoDBHashKey → @DynamoDbPartitionKey
  - Replace @DynamoDBRangeKey → @DynamoDbSortKey
  - Update reflection logic
- DynamoDBPersistentPropertyImpl.java
  - Update all annotation references
  - @DynamoDBIgnore → @DynamoDbIgnore
  - @DynamoDBVersionAttribute → @DynamoDbVersionAttribute
- Test: Reflection and metadata tests

#### 4.2 Entity Information Classes (2 hours)
- DynamoDBIdIsHashKeyEntityInformationImpl.java
  - Update annotation references in comments/docs
  - Verify metadata extraction
- DynamoDBHashAndRangeKey.java
  - Update annotations used
  - Test: Hash/range key tests

#### 4.3 Marshallers (2-3 hours)
**Decision Point:** KEEP or REMOVE?
- Current marshallers implement DynamoDBMarshaller interface (SDK v1)
- SDK v2 uses AttributeConverter pattern
- **Recommendation:** Keep for now, mark as deprecated, create AttributeConverter equivalents

Actions:
- Document that marshallers are for backward compatibility
- Create AttributeConverter implementations for new code
- Update references in marshalling mode logic
- Test: Marshaller unit tests

#### 4.4 Deliverables
- ✅ All support classes migrated
- ✅ Metadata extraction working
- ✅ Marshaller strategy decided and implemented
- ✅ Unit tests passing

---

### Phase 5: Test Updates and Validation (Day 3)
**Duration:** 12-16 hours
**Files:** 61 test files
**Goal:** All tests passing with SDK v2

#### 5.1 Unit Test Updates (4-6 hours)
**High Priority Unit Tests:**
1. DynamoDBTemplateTest.java
   - Remove DynamoDBMapper mocks
   - Mock DynamoDbEnhancedClient instead
   - Update all test assertions
2. PartTreeDynamoDBQueryUnitTest.java
   - Update query expression handling
   - Verify query parsing still works
3. SimpleDynamoDBCrudRepositoryTest.java
   - Update batch operation tests
   - Verify CRUD operations
4. DynamoDBMapperConfigFactoryTest.java
   - Update or delete based on Phase 3 decisions
5. Query/Scan expression count tests
   - Update to use Enhanced Client patterns

**Medium Priority Unit Tests:**
6-20. Event listeners, mapping tests, property tests
   - Update mocks and test data
   - Verify events still fire correctly

#### 5.2 Integration Test Review (4-6 hours)
**Review Strategy:**
- Most integration tests already use SDK v2 for direct client operations
- Focus on tests that mock DynamoDBMapper
- Update custom repository implementations

**Tests Needing Updates:**
1. TransactionalOperationsIntegrationTest.java - PARTIAL update
2. AdvancedQueryPatternsIntegrationTest.java - Full update
3. ErrorRecoveryIntegrationTest.java - PARTIAL update
4. BatchOperationsAtScaleIntegrationTest.java - Full update
5. ProjectionTypesIntegrationTest.java - Review and update
6. EnumTypesIntegrationTest.java - Review and update
7. DocumentMetadataRepositoryImpl.java - Update custom implementation
8. Plus 20+ other tests - REVIEW for consistency

**Tests Likely OK:**
- CRUDOperationsIntegrationTest.java
- HashRangeKeyIntegrationTest.java
- GlobalSecondaryIndexWithRangeKeyIntegrationTest.java
- LocalSecondaryIndexIntegrationTest.java
- And other basic integration tests

#### 5.3 Test Entity Updates (2-3 hours)
**26 entity classes with SDK v1 annotations:**

Annotation Replacements:
- @DynamoDBTable → @DynamoDbBean or @DynamoDbImmutable
- @DynamoDBHashKey → @DynamoDbPartitionKey
- @DynamoDBRangeKey → @DynamoDbSortKey
- @DynamoDBAttribute → @DynamoDbAttribute
- @DynamoDBDocument → @DynamoDbDocument
- @DynamoDBAutoGeneratedKey → @DynamoDbAutoGeneratedTimestampAttribute or custom
- @DynamoDBIndexHashKey → @DynamoDbSecondaryPartitionKey
- @DynamoDBTyped → @DynamoDbConvertedBy
- @DynamoDBVersionAttribute → @DynamoDbVersionAttribute

**Entities to Update:**
User.java, Playlist.java, Product.java, Task.java, BankAccount.java, CustomerOrder.java, Feed.java, Installation.java, Department.java, Employee.java, Sensor.java, Metric.java, Alert.java, Configuration.java, Session.java, Profile.java, Comment.java, Rating.java, Review.java, Notification.java, Message.java, Conversation.java, Transaction.java, Invoice.java, Receipt.java, Subscription.java

#### 5.4 Full Test Suite Run (2-4 hours)
- Run all unit tests
- Run all integration tests
- Verify coverage
- Fix any remaining issues
- Performance testing

#### 5.5 Deliverables
- ✅ All unit tests passing
- ✅ All integration tests passing
- ✅ Test entities updated
- ✅ Test coverage maintained or improved
- ✅ No regressions

---

## SDK v1 to v2 Type Mappings

### Core Types
| SDK v1 | SDK v2 | Notes |
|--------|--------|-------|
| `DynamoDBMapper` | `DynamoDbEnhancedClient` | Core client |
| `DynamoDBMapperConfig` | Custom `TableNameResolver` | Configuration |
| `DynamoDBQueryExpression<T>` | `QueryEnhancedRequest` | Query building |
| `DynamoDBScanExpression` | `ScanEnhancedRequest` | Scan building |
| `PaginatedQueryList<T>` | `PageIterable<T>` | Query results |
| `PaginatedScanList<T>` | `PageIterable<T>` | Scan results |
| `KeyPair` | `Key` | Batch operation keys |
| `FailedBatch` | `BatchWriteResult` | Batch failures |
| `DynamoDBMapperTableModel<T>` | `TableSchema<T>` | Table metadata |

### Annotations
| SDK v1 | SDK v2 | Notes |
|--------|--------|-------|
| `@DynamoDBTable` | `@DynamoDbBean` | Class annotation |
| `@DynamoDBHashKey` | `@DynamoDbPartitionKey` | Partition key |
| `@DynamoDBRangeKey` | `@DynamoDbSortKey` | Sort key |
| `@DynamoDBAttribute` | `@DynamoDbAttribute` | Attribute name |
| `@DynamoDBIgnore` | `@DynamoDbIgnore` | Ignore field |
| `@DynamoDBVersionAttribute` | `@DynamoDbVersionAttribute` | Optimistic locking |
| `@DynamoDBDocument` | `@DynamoDbDocument` | Nested document |
| `@DynamoDBAutoGeneratedKey` | Custom converter | Auto-generation |
| `@DynamoDBIndexHashKey` | `@DynamoDbSecondaryPartitionKey` | GSI partition |
| `@DynamoDBIndexRangeKey` | `@DynamoDbSecondarySortKey` | GSI sort |
| `@DynamoDBTyped` | `@DynamoDbConvertedBy` | Type converter |

### Marshalling
| SDK v1 | SDK v2 | Notes |
|--------|--------|-------|
| `DynamoDBMarshaller<T>` | `AttributeConverter<T>` | Type conversion |
| `DynamoDBTypeConverter<S,T>` | `AttributeConverter<T>` | Unified interface |

---

## Key Implementation Patterns

### Pattern 1: DynamoDbTable Creation and Caching
```java
// Bad: Creating table instance every time
DynamoDbTable<User> table = enhancedClient.table("User", TableSchema.fromBean(User.class));

// Good: Cache table instances
private final Map<Class<?>, DynamoDbTable<?>> tableCache = new ConcurrentHashMap<>();

@SuppressWarnings("unchecked")
private <T> DynamoDbTable<T> getTable(Class<T> domainClass) {
    return (DynamoDbTable<T>) tableCache.computeIfAbsent(domainClass, clazz -> {
        TableSchema<T> schema = TableSchema.fromBean(domainClass);
        String tableName = resolveTableName(domainClass);
        return enhancedClient.table(tableName, schema);
    });
}
```

### Pattern 2: Key Building
```java
// SDK v1 style
Object hashKey = "user123";
Object rangeKey = "2024-01-01";
User user = mapper.load(User.class, hashKey, rangeKey);

// SDK v2 style
private Key buildKey(Object hashKeyValue, @Nullable Object rangeKeyValue) {
    Key.Builder keyBuilder = Key.builder()
        .partitionValue(toAttributeValue(hashKeyValue));

    if (rangeKeyValue != null) {
        keyBuilder.sortValue(toAttributeValue(rangeKeyValue));
    }

    return keyBuilder.build();
}

Key key = buildKey("user123", "2024-01-01");
User user = getTable(User.class).getItem(key);
```

### Pattern 3: Batch Operations
```java
// SDK v1 style
Map<Class<?>, List<KeyPair>> itemsToGet = new HashMap<>();
itemsToGet.put(User.class, Arrays.asList(new KeyPair().withHashKey("user1")));
List<Object> results = mapper.batchLoad(itemsToGet).values().stream()
    .flatMap(List::stream)
    .collect(Collectors.toList());

// SDK v2 style
BatchGetItemEnhancedRequest.Builder requestBuilder = BatchGetItemEnhancedRequest.builder();
DynamoDbTable<User> table = getTable(User.class);
requestBuilder.addGetItem(table, Key.builder().partitionValue("user1").build());

BatchGetResultPageIterable resultPages = enhancedClient.batchGetItem(requestBuilder.build());
List<User> results = new ArrayList<>();
for (BatchGetResultPage page : resultPages) {
    results.addAll(page.resultsForTable(table));
}
```

### Pattern 4: Query with Pagination
```java
// SDK v1 style
DynamoDBQueryExpression<User> queryExpression = new DynamoDBQueryExpression<User>()
    .withHashKeyValues(new User().withId("user1"));
PaginatedQueryList<User> results = mapper.query(User.class, queryExpression);

// SDK v2 style
QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
    .queryConditional(QueryConditional.keyEqualTo(Key.builder().partitionValue("user1").build()))
    .build();
PageIterable<User> results = getTable(User.class).query(queryRequest);
```

### Pattern 5: Type Conversion with Marshalling Mode
```java
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
            // SDK v1 compatibility: Boolean as Number "1"/"0"
            return AttributeValue.builder().n(((Boolean) value) ? "1" : "0").build();
        } else {
            // SDK v2 native: Boolean as BOOL type
            return AttributeValue.builder().bool((Boolean) value).build();
        }
    } else if (value instanceof Date) {
        if (mappingContext.getMarshallingMode() == MarshallingMode.SDK_V1_COMPATIBLE) {
            // SDK v1 compatibility: Date as ISO string
            String isoDate = new Date2IsoDynamoDBMarshaller().marshall((Date) value);
            return AttributeValue.builder().s(isoDate).build();
        } else {
            // SDK v2 native: Date as epoch milliseconds
            return AttributeValue.builder().n(String.valueOf(((Date) value).getTime())).build();
        }
    } else if (value instanceof Instant) {
        // Both modes use String (ISO-8601) for Instant
        if (mappingContext.getMarshallingMode() == MarshallingMode.SDK_V1_COMPATIBLE) {
            String isoDate = new Instant2IsoDynamoDBMarshaller().marshall((Instant) value);
            return AttributeValue.builder().s(isoDate).build();
        } else {
            // SDK v2: ISO-8601 with nanosecond precision
            return AttributeValue.builder().s(((Instant) value).toString()).build();
        }
    }

    // Fallback
    return AttributeValue.builder().s(value.toString()).build();
}
```

---

## Timeline and Effort Estimates

### Realistic Timeline (3 Days)

#### Day 1: Core Migration (16 hours)
- **Morning (8 hours):** Phase 1 - DynamoDBTemplate
  - 0.5h: Preparation
  - 6h: Core implementation
  - 1h: Configuration methods
  - 0.5h: Initial testing

- **Afternoon (8 hours):** Phase 2 - Repository Implementations
  - 3h: SimpleDynamoDBCrudRepository
  - 2h: SimpleDynamoDBPagingAndSortingRepository
  - 2h: AbstractDynamoDBQuery
  - 1h: Testing and verification

#### Day 2: Configuration and Support (16 hours)
- **Morning (8 hours):** Phase 3 - Configuration and Factory Classes
  - 1h: DynamoDBMapperFactory (delete/migrate decision)
  - 3h: DynamoDBMapperConfigFactory (replace with TableNameResolver)
  - 2h: DynamoDBRepositoryBean (CDI)
  - 2h: Testing and integration

- **Afternoon (8 hours):** Phase 4 - Support Classes and Utilities
  - 3h: Annotation extraction classes
  - 2h: Entity information classes
  - 2h: Marshallers (keep/remove decision)
  - 1h: Testing

#### Day 3: Testing and Validation (16 hours)
- **Morning (8 hours):** Phase 5 Part 1 - Unit Tests
  - 4h: High-priority unit tests (8 files)
  - 3h: Medium-priority unit tests (12 files)
  - 1h: Test data and mocks

- **Afternoon (8 hours):** Phase 5 Part 2 - Integration and Entity Tests
  - 3h: Integration test updates (27 files - mostly review)
  - 2h: Test entity annotation updates (26 files)
  - 2h: Full test suite run
  - 1h: Bug fixes and final verification

### Optimistic Timeline (2 Days)
If everything goes smoothly and no major issues are found:
- Day 1: Phases 1-2 (12 hours)
- Day 2: Phases 3-5 (14 hours)

### Pessimistic Timeline (5 Days)
If complex issues arise or unexpected dependencies are found:
- Day 1: Phase 1 only (10 hours)
- Day 2: Phase 2 + partial Phase 3 (10 hours)
- Day 3: Complete Phase 3 + Phase 4 (10 hours)
- Day 4: Phase 5 Part 1 (10 hours)
- Day 5: Phase 5 Part 2 + buffer (10 hours)

---

## Testing Strategy

### Unit Testing Approach
1. **Mock Strategy:**
   - Replace DynamoDBMapper mocks with DynamoDbEnhancedClient mocks
   - Use Mockito to mock table.putItem(), table.getItem(), etc.
   - Maintain same test coverage

2. **Test Data:**
   - Keep existing test data
   - Update any SDK v1-specific constructs

3. **Assertion Updates:**
   - Verify same behavior
   - Update assertions for new return types (PageIterable vs PaginatedQueryList)

### Integration Testing Approach
1. **DynamoDB Local:**
   - Already uses SDK v2 DynamoDbClient ✅
   - No changes needed to test infrastructure

2. **Test Execution:**
   - Run tests after each phase
   - Don't proceed to next phase until tests pass

3. **Custom Repository Tests:**
   - Update DocumentMetadataRepositoryImpl
   - Verify parallel query execution still works

### Performance Testing
1. **Baseline Metrics:**
   - Capture current performance before migration
   - Query latency, throughput, batch operation timing

2. **Post-Migration Metrics:**
   - Compare with baseline
   - Ensure no performance regression
   - Enhanced Client may actually be faster due to improved connection pooling

3. **Load Testing:**
   - BatchOperationsAtScaleIntegrationTest
   - PerformanceBenchmarkIntegrationTest
   - Verify behavior under load

---

## Risk Mitigation

### Risk 1: Breaking Changes in Behavior
**Risk Level:** HIGH
**Description:** SDK v2 Enhanced Client may have subtle behavioral differences from SDK v1 DynamoDBMapper

**Mitigation:**
- Comprehensive integration test suite already exists
- Marshalling mode system allows SDK v1 compatibility
- Incremental migration with testing at each step
- Keep branch isolated until fully verified

### Risk 2: Pagination Behavior Changes
**Risk Level:** MEDIUM
**Description:** PaginatedQueryList/PaginatedScanList vs PageIterable may behave differently

**Mitigation:**
- Document pagination behavior differences
- Test lazy loading scenarios
- Verify limit handling
- Test with various page sizes

### Risk 3: Type Conversion Issues
**Risk Level:** MEDIUM
**Description:** Boolean/Date/Instant marshalling differences could break existing data access

**Mitigation:**
- Marshalling mode system already implemented ✅
- SDK_V1_COMPATIBLE mode for backward compatibility
- Extensive type marshalling tests
- Migration guide documents all type changes

### Risk 4: Batch Operation Error Handling
**Risk Level:** MEDIUM
**Description:** FailedBatch vs BatchWriteResult have different error structures

**Mitigation:**
- Update error handling in repository implementations
- Test batch failure scenarios
- ErrorRecoveryIntegrationTest covers this
- Document new error handling patterns

### Risk 5: Performance Regression
**Risk Level:** LOW
**Description:** Migration might impact performance

**Mitigation:**
- Performance benchmarks already exist
- Table caching reduces overhead
- Enhanced Client has better connection pooling
- Performance tests in integration suite

### Risk 6: Missing Edge Cases
**Risk Level:** MEDIUM
**Description:** Complex query patterns or edge cases might break

**Mitigation:**
- 84 test files provide extensive coverage
- AdvancedQueryPatternsIntegrationTest
- ComplexConditionalExpressionsIntegrationTest
- ParallelScansAndErrorHandlingIntegrationTest
- All edge cases already have tests

---

## Success Criteria

### Functional Requirements
- ✅ All CRUD operations work (save, load, delete)
- ✅ All batch operations work (batchLoad, batchSave, batchDelete)
- ✅ All query operations work (query, scan, count)
- ✅ Pagination works correctly
- ✅ Event publishing works (BeforeSave, AfterSave, etc.)
- ✅ Table name resolution works
- ✅ Marshalling modes work (SDK_V2_NATIVE and SDK_V1_COMPATIBLE)
- ✅ All annotations work (@DynamoDbPartitionKey, @DynamoDbSortKey, etc.)

### Test Requirements
- ✅ All unit tests pass (100%)
- ✅ All integration tests pass (100%)
- ✅ No test coverage regression
- ✅ New tests added for SDK v2-specific features

### Code Quality Requirements
- ✅ No SDK v1 imports remaining (except in test compatibility layers if needed)
- ✅ All deprecation warnings addressed
- ✅ Code compiles without errors or warnings
- ✅ JavaDoc updated to reference SDK v2
- ✅ Migration guide complete and accurate

### Performance Requirements
- ✅ No significant performance regression (< 10% slower)
- ✅ Memory usage similar or better
- ✅ Connection pooling efficient
- ✅ Table caching working

### Documentation Requirements
- ✅ MIGRATION_GUIDE_V1_TO_V2.md updated
- ✅ README.md updated with SDK v2 examples
- ✅ JavaDoc reflects SDK v2 usage
- ✅ Breaking changes documented
- ✅ Configuration examples updated

---

## Breaking Changes (Unavoidable)

### Constructor Changes
**DynamoDBTemplate:**
```java
// OLD (SDK v1)
public DynamoDBTemplate(DynamoDbClient client, DynamoDBMapper mapper,
                        DynamoDBMapperConfig config, DynamoDBMappingContext context)

// NEW (SDK v2)
public DynamoDBTemplate(DynamoDbClient client, DynamoDbEnhancedClient enhancedClient,
                        TableNameResolver nameResolver, DynamoDBMappingContext context)
```

**Impact:** Users must update configuration to provide DynamoDbEnhancedClient
**Mitigation:** Provide example configurations in migration guide

### Return Type Changes
**Batch Operations:**
```java
// OLD
List<FailedBatch> batchSave(Iterable<?> entities);
List<FailedBatch> batchDelete(Iterable<?> entities);

// NEW
List<BatchWriteResult> batchSave(Iterable<?> entities);
List<BatchWriteResult> batchDelete(Iterable<?> entities);
```

**Impact:** Users handling batch failures must update error handling
**Mitigation:** Document new error structure, provide migration examples

**Query/Scan Results:**
```java
// OLD (via DynamoDBOperations but not in interface anymore)
PaginatedQueryList<T> query(...)
PaginatedScanList<T> scan(...)

// NEW
PageIterable<T> query(...)
PageIterable<T> scan(...)
```

**Impact:** Users iterating results must update
**Mitigation:** PageIterable is more standard, document pagination patterns

### Configuration Changes
**Table Name Resolution:**
```java
// OLD
DynamoDBMapperConfig.TableNameOverride override = ...
DynamoDBMapperConfig.TableNameResolver resolver = ...

// NEW
TableNameResolver resolver = ...
```

**Impact:** Users with custom table name logic must migrate
**Mitigation:** Provide TableNameResolver implementations covering common cases

### Annotation Changes (Only for Users Creating New Entities)
```java
// OLD
@DynamoDBTable(tableName = "Users")
public class User {
    @DynamoDBHashKey
    private String id;

    @DynamoDBRangeKey
    private String timestamp;
}

// NEW
@DynamoDbBean
public class User {
    @DynamoDbPartitionKey
    private String id;

    @DynamoDbSortKey
    private String timestamp;
}
```

**Impact:** New entity classes must use SDK v2 annotations
**Mitigation:** Document all annotation mappings, provide examples

---

## Open Questions and Decisions

### Question 1: Keep or Remove DynamoDBMapperFactory?
**Options:**
- A) Delete entirely (recommended)
- B) Keep as thin wrapper around Enhanced Client

**Recommendation:** DELETE
**Rationale:** No longer needed since we're not using DynamoDBMapper
**Action:** Update dependent code to inject DynamoDbEnhancedClient directly

### Question 2: Replace or Migrate DynamoDBMapperConfigFactory?
**Options:**
- A) Replace with TableNameResolverFactory
- B) Migrate to Enhanced Client config

**Recommendation:** REPLACE with simpler TableNameResolver pattern
**Rationale:** More flexible, easier to test, follows Spring patterns
**Action:** Create TableNameResolver interface and implementations

### Question 3: Marshallers - Keep, Remove, or Deprecate?
**Options:**
- A) Remove all marshallers
- B) Keep as-is for backward compatibility
- C) Keep but deprecate, create AttributeConverter equivalents

**Recommendation:** Option C
**Rationale:** Existing code may depend on them, but new code should use SDK v2 patterns
**Action:** Mark @Deprecated, create AttributeConverter examples in docs

### Question 4: PaginatedQueryList Compatibility Layer?
**Options:**
- A) Remove completely, force users to PageIterable
- B) Create compatibility wrapper PaginatedQueryList → PageIterable
- C) Keep old methods deprecated

**Recommendation:** Option A
**Rationale:** PageIterable is standard, cleaner API, less maintenance
**Action:** Document migration pattern in guide

### Question 5: Event Publishing Timing
**Question:** When to emit events with lazy PageIterable results?

**Options:**
- A) Emit immediately after query (before results fetched)
- B) Emit as results are consumed (lazy)
- C) Emit only after all results fetched (eager)

**Recommendation:** Option A (maintain existing behavior)
**Rationale:** Consistent with current behavior, events signal operation completion
**Action:** Emit AfterQueryEvent/AfterScanEvent immediately after creating PageIterable

---

## Dependencies and Prerequisites

### Required Before Starting
1. ✅ Git branch: `migrate-sdk-v1-to-v2` (already on this branch)
2. ✅ Clean working directory (current status: clean)
3. ✅ All tests passing on main branch
4. ✅ DynamoDB Local available for integration tests
5. ✅ Java 8+ (check project requirements)
6. ✅ Maven or Gradle build working

### External Dependencies
1. ✅ AWS SDK v2 dependencies already in pom.xml/build.gradle
2. ✅ Spring Data Commons compatible versions
3. ✅ DynamoDB Enhanced Client available
4. ✅ Test containers for DynamoDB Local

### Knowledge Prerequisites
1. ✅ Understanding of DynamoDB data model
2. ✅ Familiarity with SDK v1 DynamoDBMapper
3. ✅ Understanding of SDK v2 Enhanced Client
4. ✅ Spring Data repository patterns
5. ✅ Understanding of type marshalling requirements

---

## Rollback Plan

### If Migration Fails
1. **Branch Protection:**
   - All work on `migrate-sdk-v1-to-v2` branch
   - `master` branch untouched
   - Can abandon branch if needed

2. **Incremental Commits:**
   - Commit after each phase
   - Can rollback to last working phase
   - Git history preserved

3. **Testing Gates:**
   - Don't proceed to next phase if tests fail
   - Fix issues before continuing
   - Maintains stable state

### If Major Issues Found
1. **Pause and Assess:**
   - Document the issue
   - Estimate fix time
   - Decide: fix now or defer

2. **Communication:**
   - Update status
   - Adjust timeline
   - Re-evaluate approach if needed

---

## Post-Migration Checklist

### Code Quality
- [ ] No SDK v1 imports in main source
- [ ] No SDK v1 imports in tests (except compatibility layers)
- [ ] All deprecation warnings addressed
- [ ] Code compiles clean
- [ ] No commented-out code
- [ ] Consistent code style

### Testing
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] Performance tests pass
- [ ] Manual testing completed
- [ ] Edge cases verified

### Documentation
- [ ] MIGRATION_GUIDE_V1_TO_V2.md complete
- [ ] README.md updated
- [ ] JavaDoc updated
- [ ] CHANGELOG updated
- [ ] Breaking changes documented

### Build and CI
- [ ] Maven/Gradle build succeeds
- [ ] CI pipeline passes
- [ ] Test coverage maintained
- [ ] No new warnings or errors

### Version and Release
- [ ] Version bumped to 7.0.0
- [ ] Release notes drafted
- [ ] Migration guide published
- [ ] Examples updated

---

## Success Metrics

### Quantitative Metrics
- **Code Coverage:** Maintain ≥ current coverage (target: no regression)
- **Build Time:** ≤ 10% increase
- **Test Execution Time:** ≤ 10% increase
- **Performance:** ≤ 10% regression in throughput
- **Lines of Code:** Expect similar LOC (maybe 5-10% reduction due to simpler APIs)

### Qualitative Metrics
- **Code Maintainability:** Should improve (SDK v2 is more modern)
- **API Clarity:** Should improve (Enhanced Client is cleaner)
- **Error Messages:** Should be more helpful (SDK v2 exceptions are better)
- **Developer Experience:** Should improve (better IDE support, clearer patterns)

---

## Resources and References

### AWS SDK v2 Documentation
- [AWS SDK for Java 2.x Developer Guide](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/)
- [DynamoDB Enhanced Client Guide](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/dynamodb-enhanced-client.html)
- [SDK v2 Migration Guide (Official AWS)](https://docs.aws.amazon.com/sdk-for-java/latest/migration-guide/)

### Project Documentation
- `MIGRATION_GUIDE_V1_TO_V2.md` - User-facing migration guide
- `TYPE_MARSHALLING_RESEARCH.md` - Type marshalling behavior documentation
- `DYNAMODB_TEMPLATE_MIGRATION_PLAN.md` - Detailed DynamoDBTemplate migration plan (Phase 1)

### Code Examples
- Spring Data DynamoDB Examples (if available)
- AWS SDK v2 Samples
- Enhanced Client Examples in AWS docs

---

## Daily Progress Tracking

### Day 1 Goals
- [ ] Phase 1: DynamoDBTemplate migrated and tested
- [ ] Phase 2: Repository implementations migrated
- [ ] Core functionality working
- [ ] Basic integration tests passing

### Day 2 Goals
- [ ] Phase 3: Configuration classes migrated
- [ ] Phase 4: Support classes migrated
- [ ] All core features working
- [ ] Unit tests updated

### Day 3 Goals
- [ ] Phase 5: All tests updated and passing
- [ ] Full regression testing complete
- [ ] Documentation complete
- [ ] Ready for review/merge

---

## Conclusion

This migration plan provides a comprehensive, step-by-step approach to migrating Spring Data DynamoDB from AWS SDK v1 to v2. The plan is:

- **Thorough:** Covers all 78 files with SDK v1 dependencies
- **Realistic:** 3-day timeline with buffer for issues
- **Risk-Aware:** Identifies and mitigates key risks
- **Test-Driven:** Heavy emphasis on testing at each phase
- **Well-Documented:** Clear patterns and examples
- **Reversible:** Incremental approach with rollback options

**Critical Success Factor:** DynamoDBTemplate is the bottleneck. Once migrated (Phase 1), all other components can be updated rapidly.

**Key Principle:** No shortcuts, maintain behavioral equivalence, comprehensive testing at each step.

**Expected Outcome:** Fully functional SDK v2 implementation ready for 7.0.0 release, maintaining backward compatibility through marshalling mode system where possible, with clear migration path for unavoidable breaking changes.

---

**Plan Status:** READY FOR EXECUTION
**Next Action:** Begin Phase 1 - DynamoDBTemplate Migration
**Estimated Completion:** 3 days (48 hours of focused work)
