# SDK v2 Migration - Quick Reference

**Last Updated:** 2025-11-12
**Branch:** migrate-sdk-v1-to-v2
**Status:** Ready to begin implementation

---

## What's Done ✅

### Already Migrated (30 files)
- DynamoDBOperations interface
- Query criteria classes (AbstractDynamoDBQueryCriteria, etc.)
- DynamoDBMappingContext
- Marshalling mode system (SDK_V2_NATIVE default, SDK_V1_COMPATIBLE opt-in)
- Type marshalling (Boolean, Date, Instant) with research completed
- Repository factory and configuration classes
- Entity metadata support classes
- Test infrastructure (DynamoDBLocalResource)

### Current State
- Interface layer: SDK v2 ✅
- Implementation layer: SDK v1 (DynamoDBMapper) ⚠️
- Tests: Mixed state 🔄

---

## What's Left (78 files)

### Main Source Files (17 files)
1. **DynamoDBTemplate.java** - CRITICAL (8-10 hours)
2. SimpleDynamoDBCrudRepository.java (3-4 hours)
3. SimpleDynamoDBPagingAndSortingRepository.java (2 hours)
4. AbstractDynamoDBQuery.java (3 hours)
5. DynamoDBMapperFactory.java (1 hour - or DELETE)
6. DynamoDBMapperConfigFactory.java (2-3 hours - REPLACE)
7. DynamoDBRepositoryBean.java (2 hours)
8. DynamoDBHashAndRangeKeyMethodExtractorImpl.java (2 hours)
9. DynamoDBPersistentPropertyImpl.java (2-3 hours)
10. Plus 8 more support files (8-10 hours total)

### Test Files (61 files)
- Unit tests: 20 files (10-14 hours)
- Integration tests: 27 files (8-12 hours - mostly review)
- Test entities: 26 files (3-4 hours - annotation updates)
- Custom implementations: 1 file (1-2 hours)

---

## 3-Day Plan

### Day 1: Core Migration (16 hours)
**Morning:** DynamoDBTemplate (8 hours)
- Create TableNameResolver interface
- Replace DynamoDBMapper with DynamoDbEnhancedClient
- Migrate all CRUD, batch, query operations
- Initial testing

**Afternoon:** Repository Implementations (8 hours)
- SimpleDynamoDBCrudRepository (remove KeyPair, FailedBatch)
- SimpleDynamoDBPagingAndSortingRepository (PageIterable)
- AbstractDynamoDBQuery (BatchWriteResult)

### Day 2: Config and Support (16 hours)
**Morning:** Configuration (8 hours)
- Delete/migrate DynamoDBMapperFactory
- Replace DynamoDBMapperConfigFactory with TableNameResolver
- Update DynamoDBRepositoryBean (CDI)

**Afternoon:** Support Classes (8 hours)
- Annotation extraction classes
- Entity information classes
- Marshallers (keep/deprecate decision)

### Day 3: Tests and Validation (16 hours)
**Morning:** Unit Tests (8 hours)
- Update DynamoDBTemplateTest
- Update repository tests
- Update query/scan tests

**Afternoon:** Integration Tests (8 hours)
- Review and update integration tests
- Update test entity annotations
- Full regression testing

---

## Critical Path

```
DynamoDBTemplate (BLOCKER)
    ↓
Repository Implementations
    ↓
Configuration Classes
    ↓
Support Classes
    ↓
Tests
```

**Key Insight:** Everything depends on DynamoDBTemplate. Once that's migrated, the rest flows quickly.

---

## Key Type Mappings

| SDK v1 | SDK v2 |
|--------|--------|
| `DynamoDBMapper` | `DynamoDbEnhancedClient` |
| `DynamoDBMapperConfig` | `TableNameResolver` |
| `DynamoDBQueryExpression` | `QueryEnhancedRequest` |
| `DynamoDBScanExpression` | `ScanEnhancedRequest` |
| `PaginatedQueryList` | `PageIterable` |
| `KeyPair` | `Key` |
| `FailedBatch` | `BatchWriteResult` |
| `DynamoDBMapperTableModel` | `TableSchema` |

---

## Key Patterns to Use

### Pattern 1: Table Caching
```java
private final Map<Class<?>, DynamoDbTable<?>> tableCache = new ConcurrentHashMap<>();

private <T> DynamoDbTable<T> getTable(Class<T> domainClass) {
    return (DynamoDbTable<T>) tableCache.computeIfAbsent(domainClass,
        clazz -> enhancedClient.table(resolveTableName(clazz),
                                      TableSchema.fromBean(clazz)));
}
```

### Pattern 2: Key Building
```java
private Key buildKey(Object hashKey, Object rangeKey) {
    Key.Builder builder = Key.builder().partitionValue(toAttributeValue(hashKey));
    if (rangeKey != null) {
        builder.sortValue(toAttributeValue(rangeKey));
    }
    return builder.build();
}
```

### Pattern 3: Batch Operations
```java
// Batch Get
BatchGetItemEnhancedRequest.Builder request = BatchGetItemEnhancedRequest.builder();
for (Key key : keys) {
    request.addGetItem(table, key);
}
BatchGetResultPageIterable results = enhancedClient.batchGetItem(request.build());

// Batch Write
BatchWriteItemEnhancedRequest.Builder request = BatchWriteItemEnhancedRequest.builder();
WriteBatch.Builder<T> batch = WriteBatch.builder(domainClass).mappedTableResource(table);
for (T entity : entities) {
    batch.addPutItem(entity);
}
request.addWriteBatch(batch.build());
BatchWriteResult result = enhancedClient.batchWriteItem(request.build());
```

---

## Breaking Changes

### Constructor
```java
// OLD
DynamoDBTemplate(DynamoDbClient client, DynamoDBMapper mapper,
                 DynamoDBMapperConfig config, DynamoDBMappingContext context)

// NEW
DynamoDBTemplate(DynamoDbClient client, DynamoDbEnhancedClient enhancedClient,
                 TableNameResolver nameResolver, DynamoDBMappingContext context)
```

### Return Types
- `List<FailedBatch>` → `List<BatchWriteResult>`
- `PaginatedQueryList<T>` → `PageIterable<T>`
- `DynamoDBMapperTableModel<T>` → `TableSchema<T>`

### Annotations (for new entities)
- `@DynamoDBTable` → `@DynamoDbBean`
- `@DynamoDBHashKey` → `@DynamoDbPartitionKey`
- `@DynamoDBRangeKey` → `@DynamoDbSortKey`

---

## Decision Points

### Day 1 Decisions
1. **DynamoDBMapperFactory:** DELETE (recommended)
2. **Event Publishing with PageIterable:** Emit immediately (Option A)

### Day 2 Decisions
3. **DynamoDBMapperConfigFactory:** REPLACE with TableNameResolver (recommended)
4. **Marshallers:** Keep but deprecate, create AttributeConverter docs (Option C)

### Day 3 Decisions
5. **PaginatedQueryList compatibility:** Remove completely, use PageIterable (Option A)

---

## Success Criteria

- ✅ All tests pass (100%)
- ✅ No SDK v1 imports in main source
- ✅ No performance regression (< 10%)
- ✅ All CRUD, batch, query, scan operations work
- ✅ Marshalling modes work correctly
- ✅ Documentation updated

---

## Risk Mitigation

1. **Pagination Behavior:** Test with various page sizes and limits
2. **Type Conversion:** Marshalling mode system already handles this ✅
3. **Batch Errors:** Test failure scenarios
4. **Performance:** Run benchmarks
5. **Edge Cases:** 84 test files provide coverage

---

## Resources

- **Master Plan:** `MASTER_SDK_V2_MIGRATION_PLAN.md` (comprehensive, 700+ lines)
- **DynamoDB Template Plan:** `DYNAMODB_TEMPLATE_MIGRATION_PLAN.md` (Phase 1 details)
- **Migration Guide:** `MIGRATION_GUIDE_V1_TO_V2.md` (user-facing)
- **Type Marshalling:** `TYPE_MARSHALLING_RESEARCH.md` (research findings)

---

## Quick Start Tomorrow

1. Read this document (5 min)
2. Review `MASTER_SDK_V2_MIGRATION_PLAN.md` Phase 1 section (10 min)
3. Start Phase 1: DynamoDBTemplate migration
   - Create `TableNameResolver.java` interface
   - Update `DynamoDBTemplate.java` constructor
   - Implement helper methods
   - Migrate operations one by one
   - Test as you go

---

## Files to Start With Tomorrow

1. Create: `src/main/java/org/socialsignin/spring/data/dynamodb/core/TableNameResolver.java`
2. Modify: `src/main/java/org/socialsignin/spring/data/dynamodb/core/DynamoDBTemplate.java`
3. Test: `src/test/java/org/socialsignin/spring/data/dynamodb/core/DynamoDBTemplateTest.java`

---

**Status:** All research complete, plan ready, ready to implement tomorrow morning!
