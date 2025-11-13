# SDK v2 Migration - Execution Checklist

**Branch:** migrate-sdk-v1-to-v2
**Version:** 7.0.0-SNAPSHOT
**Java:** 21
**AWS SDK v2:** 2.38.1

Use this checklist to track progress during the 3-day migration.

---

## Pre-Migration Verification ✅

- [x] On correct branch: `migrate-sdk-v1-to-v2`
- [x] Clean working directory
- [x] AWS SDK v2 dependencies in pom.xml (2.38.1)
- [x] dynamodb-enhanced dependency present
- [x] Test infrastructure ready (DynamoDBLocalResource)
- [x] Migration plans created and reviewed

---

## Day 1: Core Migration

### Phase 1: DynamoDBTemplate (8-10 hours)

#### Step 1.1: Create TableNameResolver Interface (30 min)
- [ ] Create `src/main/java/org/socialsignin/spring/data/dynamodb/core/TableNameResolver.java`
  ```java
  public interface TableNameResolver {
      <T> String resolveTableName(Class<T> domainClass, String baseTableName);
  }
  ```
- [ ] Create default implementation (if needed)
- [ ] Add JavaDoc
- [ ] Compile and verify

#### Step 1.2: Update DynamoDBTemplate Fields (30 min)
- [ ] Open `DynamoDBTemplate.java`
- [ ] Remove field: `private final DynamoDBMapper dynamoDBMapper;`
- [ ] Remove field: `private final DynamoDBMapperConfig dynamoDBMapperConfig;`
- [ ] Add field: `private final DynamoDbEnhancedClient enhancedClient;`
- [ ] Add field: `private final TableNameResolver tableNameResolver;`
- [ ] Add field: `private final Map<Class<?>, DynamoDbTable<?>> tableCache = new ConcurrentHashMap<>();`
- [ ] Keep field: `private final DynamoDbClient amazonDynamoDB;` (for low-level operations)
- [ ] Compile (will have errors - expected)

#### Step 1.3: Update Constructor (30 min)
- [ ] Update constructor signature:
  ```java
  public DynamoDBTemplate(DynamoDbClient amazonDynamoDB,
                          DynamoDbEnhancedClient enhancedClient,
                          @Nullable TableNameResolver tableNameResolver,
                          @Nullable DynamoDBMappingContext mappingContext)
  ```
- [ ] Update constructor validation
- [ ] Update field assignments
- [ ] Handle null tableNameResolver (create default if needed)
- [ ] Update JavaDoc
- [ ] Compile (will still have errors in methods - expected)

#### Step 1.4: Implement Helper Methods (1 hour)
- [ ] Add `getTable(Class<T>)` method with caching
- [ ] Add `buildKey(Object, Object)` method
- [ ] Add `getTableSchema(Class<T>)` method
- [ ] Add `toAttributeValue(Object)` method (use existing logic from query criteria)
- [ ] Add `resolveTableName(Class<T>)` private method
- [ ] Compile and verify helpers work

#### Step 1.5: Migrate save() Method (30 min)
- [ ] Replace `dynamoDBMapper.save(entity)` with `getTable(entity.getClass()).putItem(entity)`
- [ ] Keep event publishing (BeforeSave, AfterSave)
- [ ] Test save operation
- [ ] Verify events fire correctly

#### Step 1.6: Migrate load() Methods (30 min)
- [ ] Update `load(Class<T>, Object)` - hash key only
  - [ ] Build key with `buildKey(hashKey, null)`
  - [ ] Use `getTable(domainClass).getItem(key)`
  - [ ] Keep AfterLoadEvent
- [ ] Update `load(Class<T>, Object, Object)` - hash + range key
  - [ ] Build key with `buildKey(hashKey, rangeKey)`
  - [ ] Use `getTable(domainClass).getItem(key)`
  - [ ] Keep AfterLoadEvent
- [ ] Test load operations

#### Step 1.7: Migrate delete() Method (30 min)
- [ ] Replace `dynamoDBMapper.delete(entity)` with `getTable(entity.getClass()).deleteItem(entity)`
- [ ] Keep event publishing (BeforeDelete, AfterDelete)
- [ ] Test delete operation

#### Step 1.8: Migrate batchLoad() (1 hour)
- [ ] Update signature: `Map<Class<?>, List<Key>>` (was `List<KeyPair>`)
- [ ] Create `BatchGetItemEnhancedRequest.Builder`
- [ ] Group by class and add items
- [ ] Execute batch get
- [ ] Collect results from pages
- [ ] Emit AfterLoadEvent for each item
- [ ] Test batch load

#### Step 1.9: Migrate batchSave() (1 hour)
- [ ] Update return type: `List<BatchWriteResult>` (was `List<FailedBatch>`)
- [ ] Emit BeforeSaveEvents
- [ ] Group entities by class
- [ ] Create `BatchWriteItemEnhancedRequest.Builder`
- [ ] Add write batches
- [ ] Execute batch write
- [ ] Collect results
- [ ] Emit AfterSaveEvents
- [ ] Test batch save

#### Step 1.10: Migrate batchDelete() (1 hour)
- [ ] Update return type: `List<BatchWriteResult>` (was `List<FailedBatch>`)
- [ ] Emit BeforeDeleteEvents
- [ ] Group entities by class
- [ ] Create `BatchWriteItemEnhancedRequest.Builder`
- [ ] Add delete batches
- [ ] Execute batch write
- [ ] Collect results
- [ ] Emit AfterDeleteEvents
- [ ] Test batch delete

#### Step 1.11: Migrate query(QueryRequest) (30 min)
- [ ] Update return type: `PageIterable<T>` (was `PaginatedQueryList<T>`)
- [ ] Use existing low-level query implementation
- [ ] Wrap in PageIterable if needed
- [ ] Emit AfterQueryEvent immediately
- [ ] Test query operation

#### Step 1.12: Migrate count() Methods (30 min)
- [ ] Review `count(Class<T>, QueryRequest)` - should already be SDK v2
- [ ] Update `count(Class<T>, QueryEnhancedRequest)` - implement properly
- [ ] Update `count(Class<T>, ScanEnhancedRequest)` - implement properly
- [ ] Test count operations

#### Step 1.13: Update getOverriddenTableName() (30 min)
- [ ] Replace DynamoDBMapperConfig logic with TableNameResolver
- [ ] Keep backward compatibility if possible
- [ ] Test table name resolution

#### Step 1.14: Update getTableModel() (30 min)
- [ ] Change return type: `TableSchema<T>` (was `DynamoDBMapperTableModel<T>`)
- [ ] Return `TableSchema.fromBean(domainClass)`
- [ ] Test table model retrieval

#### Step 1.15: Remove SDK v1 Imports (15 min)
- [ ] Remove all `com.amazonaws.services.dynamodbv2.datamodeling.*` imports
- [ ] Remove all `com.amazonaws.services.dynamodbv2.model.*` imports from mapper usage
- [ ] Keep only SDK v2 imports
- [ ] Compile clean

#### Step 1.16: Update DynamoDBTemplateTest (1-2 hours)
- [ ] Replace DynamoDBMapper mocks with DynamoDbEnhancedClient mocks
- [ ] Update all test cases
- [ ] Verify all tests pass
- [ ] Add tests for new functionality if needed

#### Step 1.17: Run Integration Tests (30 min)
- [ ] Run basic integration tests
- [ ] Verify CRUD operations work
- [ ] Fix any issues found
- [ ] Document any blockers

**Phase 1 Checkpoint:** DynamoDBTemplate fully migrated and tested ✅

---

### Phase 2: Repository Implementations (6-8 hours)

#### Step 2.1: Migrate SimpleDynamoDBCrudRepository (3-4 hours)
- [ ] Open `SimpleDynamoDBCrudRepository.java`
- [ ] Update batchLoad calls - remove KeyPair construction
  - [ ] Change to use `Key` objects directly
  - [ ] Update key building logic
- [ ] Update return types:
  - [ ] `batchSave()`: `List<BatchWriteResult>` (was `List<FailedBatch>`)
  - [ ] `batchDelete()`: `List<BatchWriteResult>` (was `List<FailedBatch>`)
- [ ] Remove SDK v1 imports
- [ ] Compile and verify
- [ ] Update `SimpleDynamoDBCrudRepositoryTest.java`
- [ ] Run tests

#### Step 2.2: Migrate SimpleDynamoDBPagingAndSortingRepository (2 hours)
- [ ] Open `SimpleDynamoDBPagingAndSortingRepository.java`
- [ ] Update PaginatedScanList usage to PageIterable
- [ ] Update scan operations
- [ ] Verify paging behavior matches
- [ ] Remove SDK v1 imports
- [ ] Compile and verify
- [ ] Run paging tests

#### Step 2.3: Migrate AbstractDynamoDBQuery (2 hours)
- [ ] Open `AbstractDynamoDBQuery.java`
- [ ] Update batch delete return type: `List<BatchWriteResult>` (was `List<FailedBatch>`)
- [ ] Update error handling for new result type
- [ ] Remove `DynamoDBMapper.FailedBatch` import
- [ ] Compile and verify
- [ ] Update query unit tests
- [ ] Run tests

#### Step 2.4: Verify Repository Integration (1 hour)
- [ ] Run all repository tests
- [ ] Run basic integration tests
- [ ] Verify batch operations work
- [ ] Verify paging works
- [ ] Fix any issues

**Phase 2 Checkpoint:** All repository implementations using SDK v2 ✅

---

## Day 2: Configuration and Support

### Phase 3: Configuration and Factory Classes (4-6 hours)

#### Step 3.1: DynamoDBMapperFactory Decision (1 hour)
- [ ] Review usages of DynamoDBMapperFactory
- [ ] **Decision:** DELETE or MIGRATE?
  - [ ] Option A: Delete (recommended) - update dependent code
  - [ ] Option B: Migrate to create DynamoDbEnhancedClient
- [ ] Execute chosen option
- [ ] Update configuration classes that use it
- [ ] Test configuration

#### Step 3.2: Replace DynamoDBMapperConfigFactory (2-3 hours)
- [ ] Create `DefaultTableNameResolver.java`
  ```java
  public class DefaultTableNameResolver implements TableNameResolver {
      @Override
      public <T> String resolveTableName(Class<T> domainClass, String baseTableName) {
          return baseTableName; // No override
      }
  }
  ```
- [ ] Create `TableNamePrefixResolver.java`
  ```java
  public class TableNamePrefixResolver implements TableNameResolver {
      private final String prefix;
      @Override
      public <T> String resolveTableName(Class<T> domainClass, String baseTableName) {
          return prefix + baseTableName;
      }
  }
  ```
- [ ] Create `TableNameOverrideResolver.java`
- [ ] Update DynamoDBMapperConfigFactory or replace with TableNameResolverFactory
- [ ] Update tests
- [ ] Verify table name resolution works

#### Step 3.3: Migrate DynamoDBRepositoryBean (CDI) (2 hours)
- [ ] Open `DynamoDBRepositoryBean.java`
- [ ] Replace DynamoDBMapper injection with DynamoDbEnhancedClient
- [ ] Replace DynamoDBMapperConfig with TableNameResolver
- [ ] Update bean creation logic
- [ ] Remove SDK v1 imports
- [ ] Test CDI integration
- [ ] Update CDI tests if needed

**Phase 3 Checkpoint:** Configuration layer migrated ✅

---

### Phase 4: Support Classes and Utilities (6-8 hours)

#### Step 4.1: Migrate Annotation Extraction (3 hours)
- [ ] Open `DynamoDBHashAndRangeKeyMethodExtractorImpl.java`
- [ ] Replace @DynamoDBHashKey with @DynamoDbPartitionKey
  - [ ] Update all `isAnnotationPresent(DynamoDBHashKey.class)` calls
  - [ ] Update all `getAnnotation(DynamoDBHashKey.class)` calls
- [ ] Replace @DynamoDBRangeKey with @DynamoDbSortKey
  - [ ] Update all `isAnnotationPresent(DynamoDBRangeKey.class)` calls
  - [ ] Update all `getAnnotation(DynamoDBRangeKey.class)` calls
- [ ] Remove SDK v1 imports
- [ ] Compile and test
- [ ] Run reflection tests

#### Step 4.2: Migrate DynamoDBPersistentPropertyImpl (2 hours)
- [ ] Open `DynamoDBPersistentPropertyImpl.java`
- [ ] Replace all annotation references:
  - [ ] @DynamoDBHashKey → @DynamoDbPartitionKey
  - [ ] @DynamoDBRangeKey → @DynamoDbSortKey
  - [ ] @DynamoDBIgnore → @DynamoDbIgnore
  - [ ] @DynamoDBVersionAttribute → @DynamoDbVersionAttribute
  - [ ] @DynamoDBAttribute → @DynamoDbAttribute
- [ ] Remove SDK v1 imports
- [ ] Compile and test
- [ ] Run property tests

#### Step 4.3: Update Entity Information Classes (1 hour)
- [ ] Open `DynamoDBIdIsHashKeyEntityInformationImpl.java`
- [ ] Update annotation references in JavaDoc/comments
- [ ] Open `DynamoDBHashAndRangeKey.java`
- [ ] Replace annotation usage
- [ ] Test metadata extraction

#### Step 4.4: Marshallers Decision (2-3 hours)
- [ ] **Decision:** KEEP, REMOVE, or DEPRECATE?
  - [ ] Option A: Remove (breaking change)
  - [ ] Option B: Keep as-is (technical debt)
  - [ ] Option C: Keep but deprecate, document AttributeConverter (recommended)
- [ ] If keeping:
  - [ ] Add @Deprecated annotations
  - [ ] Update JavaDoc to recommend AttributeConverter
  - [ ] Create example AttributeConverter implementations in tests
- [ ] If removing:
  - [ ] Remove marshaller classes
  - [ ] Update references to use AttributeConverter
  - [ ] Update migration guide
- [ ] Test marshalling

**Phase 4 Checkpoint:** All support classes migrated ✅

---

## Day 3: Testing and Validation

### Phase 5: Test Updates and Validation (12-16 hours)

#### Step 5.1: High Priority Unit Tests (4-6 hours)
- [ ] Update `DynamoDBTemplateTest.java` (if not done in Phase 1)
  - [ ] Replace DynamoDBMapper mocks
  - [ ] Update all assertions
  - [ ] Verify all tests pass
- [ ] Update `PartTreeDynamoDBQueryUnitTest.java`
  - [ ] Update query expression handling
  - [ ] Verify query parsing
  - [ ] Run tests
- [ ] Update `SimpleDynamoDBCrudRepositoryTest.java` (if not done in Phase 2)
  - [ ] Update batch tests
  - [ ] Verify CRUD operations
  - [ ] Run tests
- [ ] Update `DynamoDBMapperConfigFactoryTest.java`
  - [ ] Update or delete based on Phase 3 decision
- [ ] Update `QueryExpressionCountQueryTest.java`
  - [ ] Update to Enhanced Client patterns
  - [ ] Run tests
- [ ] Update `ScanExpressionCountQueryTest.java`
  - [ ] Update to Enhanced Client patterns
  - [ ] Run tests
- [ ] Update other high-priority unit tests

#### Step 5.2: Medium Priority Unit Tests (2-4 hours)
- [ ] Update event listener tests
  - [ ] `AbstractDynamoDBEventListenerTest.java`
  - [ ] `ValidatingDynamoDBEventListenerTest.java`
  - [ ] `LoggingEventListenerTest.java`
  - [ ] Update PaginatedQueryList/ScanList to PageIterable
- [ ] Update mapping tests
  - [ ] `DynamoDBMappingContextTest.java`
  - [ ] `DynamoDBPersistentEntityTest.java`
  - [ ] `DynamoDBPersistentPropertyImplUnitTest.java`
- [ ] Update property tests
- [ ] Run all unit tests

#### Step 5.3: Integration Tests Review (4-6 hours)

##### Tests Needing Updates
- [ ] `TransactionalOperationsIntegrationTest.java`
  - [ ] Remove DynamoDBMapper mocks if present
  - [ ] Verify transactions work with SDK v2
- [ ] `AdvancedQueryPatternsIntegrationTest.java`
  - [ ] Update query expression usage
  - [ ] Verify complex filters work
- [ ] `ErrorRecoveryIntegrationTest.java`
  - [ ] Update batch error handling
  - [ ] Verify retry logic
- [ ] `BatchOperationsAtScaleIntegrationTest.java`
  - [ ] Verify large batch operations
  - [ ] Check performance
- [ ] `ProjectionTypesIntegrationTest.java`
  - [ ] Verify projections work
- [ ] `EnumTypesIntegrationTest.java`
  - [ ] Verify enum marshalling
- [ ] Update other integration tests as needed

##### Tests to Verify (Likely OK)
- [ ] `CRUDOperationsIntegrationTest.java` - Run and verify
- [ ] `HashRangeKeyIntegrationTest.java` - Run and verify
- [ ] `GlobalSecondaryIndexWithRangeKeyIntegrationTest.java` - Run and verify
- [ ] `LocalSecondaryIndexIntegrationTest.java` - Run and verify
- [ ] Run all other basic integration tests

##### Custom Implementations
- [ ] Update `DocumentMetadataRepositoryImpl.java`
  - [ ] Remove DynamoDBQueryExpression usage
  - [ ] Update to QueryEnhancedRequest
  - [ ] Remove PaginatedQueryList usage
  - [ ] Update to PageIterable
  - [ ] Verify parallel query execution

#### Step 5.4: Test Entity Updates (2-3 hours)
Update annotations in all 26 test entity classes:

- [ ] `User.java` - @DynamoDBTable → @DynamoDbBean, keys
- [ ] `Playlist.java` - @DynamoDBTable → @DynamoDbBean, keys
- [ ] `Product.java` - @DynamoDBTable → @DynamoDbBean, keys
- [ ] `Task.java` - annotations
- [ ] `BankAccount.java` - annotations
- [ ] `CustomerOrder.java` - annotations
- [ ] `Feed.java` - annotations
- [ ] `Installation.java` - annotations
- [ ] `Department.java` - annotations
- [ ] `Employee.java` - annotations
- [ ] `Sensor.java` - annotations
- [ ] `Metric.java` - annotations
- [ ] `Alert.java` - annotations
- [ ] `Configuration.java` - annotations
- [ ] `Session.java` - annotations
- [ ] `Profile.java` - annotations
- [ ] `Comment.java` - annotations
- [ ] `Rating.java` - annotations
- [ ] `Review.java` - annotations
- [ ] `Notification.java` - annotations
- [ ] `Message.java` - annotations
- [ ] `Conversation.java` - annotations
- [ ] `Transaction.java` - annotations
- [ ] `Invoice.java` - annotations
- [ ] `Receipt.java` - annotations
- [ ] `Subscription.java` - annotations

**Annotation Mapping:**
- @DynamoDBTable → @DynamoDbBean
- @DynamoDBHashKey → @DynamoDbPartitionKey
- @DynamoDBRangeKey → @DynamoDbSortKey
- @DynamoDBAttribute → @DynamoDbAttribute
- @DynamoDBDocument → @DynamoDbDocument
- @DynamoDBAutoGeneratedKey → Custom converter
- @DynamoDBIndexHashKey → @DynamoDbSecondaryPartitionKey
- @DynamoDBTyped → @DynamoDbConvertedBy
- @DynamoDBVersionAttribute → @DynamoDbVersionAttribute

#### Step 5.5: Full Test Suite (2-4 hours)
- [ ] Run complete Maven build: `mvn clean test`
- [ ] Verify all unit tests pass (100%)
- [ ] Verify all integration tests pass (100%)
- [ ] Check test coverage - no regression
- [ ] Run performance benchmarks
  - [ ] Compare with baseline
  - [ ] Ensure < 10% regression
- [ ] Fix any remaining failures
- [ ] Document any known issues

**Phase 5 Checkpoint:** All tests passing ✅

---

## Post-Migration Tasks

### Documentation Updates
- [ ] Review and update `MIGRATION_GUIDE_V1_TO_V2.md`
  - [ ] Add DynamoDBTemplate constructor changes
  - [ ] Add batch operation return type changes
  - [ ] Add configuration changes
  - [ ] Add code examples
- [ ] Update `README.md`
  - [ ] Update getting started examples
  - [ ] Update configuration examples
  - [ ] Update version to 7.0.0
- [ ] Update JavaDoc
  - [ ] Review all public APIs
  - [ ] Update references to SDK v2
  - [ ] Remove references to deprecated SDK v1 types
- [ ] Update `CHANGELOG.md`
  - [ ] List all breaking changes
  - [ ] List all improvements
  - [ ] List migration steps

### Code Quality
- [ ] Run spotless/formatter if configured
- [ ] Check for TODO/FIXME comments
- [ ] Remove commented-out code
- [ ] Verify consistent code style
- [ ] Check for unused imports
- [ ] Check for deprecation warnings

### Build and CI
- [ ] Full Maven build: `mvn clean install`
- [ ] Verify build succeeds
- [ ] Verify JavaDoc generation works
- [ ] Verify source jar creation
- [ ] Check for any build warnings
- [ ] Run CI pipeline (if configured)

### Final Verification
- [ ] No SDK v1 imports in main source (except compatibility if decided)
- [ ] No compilation errors or warnings
- [ ] All tests pass (unit + integration)
- [ ] Performance benchmarks acceptable
- [ ] Documentation complete
- [ ] Migration guide accurate

---

## Commit Strategy

### Commit After Each Phase
- [ ] Phase 1 commit: "Migrate DynamoDBTemplate to SDK v2 Enhanced Client"
- [ ] Phase 2 commit: "Migrate repository implementations to SDK v2"
- [ ] Phase 3 commit: "Migrate configuration and factory classes to SDK v2"
- [ ] Phase 4 commit: "Migrate support classes and utilities to SDK v2"
- [ ] Phase 5 commit: "Update all tests for SDK v2 migration"
- [ ] Final commit: "Complete SDK v2 migration - update documentation"

### Commit Message Format
```
<Short description>

- Detailed change 1
- Detailed change 2
- Detailed change 3

Refs: #<issue-number> (if applicable)
```

### Do NOT Include
- ❌ "Generated with Claude Code"
- ❌ "Co-Authored-By: Claude"
- ✅ Only technical content

---

## Emergency Rollback

If critical issues arise:
1. Document the issue
2. Commit current work (even if incomplete)
3. Assess:
   - Can be fixed quickly? (< 2 hours) → Fix it
   - Requires redesign? → Pause and reassess
   - Blocker found? → Document and escalate
4. Remember: All work is on `migrate-sdk-v1-to-v2` branch
5. Main branch is safe and untouched

---

## Success Verification

### Must All Be True
- ✅ mvn clean install succeeds
- ✅ All tests pass (100%)
- ✅ No SDK v1 imports in main source
- ✅ No compilation errors
- ✅ Documentation updated
- ✅ Migration guide complete
- ✅ Performance acceptable

### Quality Metrics
- Test Coverage: ≥ current
- Build Time: ≤ 110% of current
- Test Time: ≤ 110% of current
- Throughput: ≥ 90% of current

---

## Notes and Learnings

Use this section to document:
- Issues encountered
- Solutions found
- Time estimates vs actuals
- Lessons learned
- Suggestions for similar migrations

---

**Status:** Ready to begin execution
**Next Action:** Start Day 1, Phase 1, Step 1.1
**Estimated Completion:** End of Day 3
