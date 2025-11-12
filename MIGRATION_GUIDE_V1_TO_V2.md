# Migration Guide: AWS SDK v1 to SDK v2

## Overview

This guide helps you migrate from the AWS SDK v1 version of spring-data-dynamodb to the AWS SDK v2 version. This is a **major upgrade** with breaking changes that align with AWS SDK v2 best practices and improve production resilience.

### What's Changing

- **AWS SDK**: v1.x → v2.x
- **DynamoDB Client**: `AmazonDynamoDB` → `DynamoDbClient` + `DynamoDbEnhancedClient`
- **Annotations**: SDK v1 annotations → SDK v2 Enhanced Client annotations
- **Error Handling**: Simple exceptions → Rich exceptions with unprocessed entity access
- **Retry Logic**: Manual → Automatic with configurable exponential backoff

### Migration Status

| Component | Status | Breaking Changes |
|-----------|--------|-----------------|
| **Batch Operations** | ✅ Complete | Yes - Exception APIs |
| **Entity Annotations** | ✅ Complete | Yes - Annotation names |
| **Event Listeners** | ✅ Complete | Yes - API types |
| **Type Converters** | ✅ Complete | Yes - Unified to AttributeConverter |
| **Type Marshalling** | ✅ Complete | **Critical** - Data format changes |
| **Repository Config** | ✅ Complete | Minor - Supports both old and new annotations |
| **Query Operations** | ✅ Complete | Yes - Marshalling behavior |
| DynamoDBTemplate | ⏳ Pending | TBD |

---

## Breaking Changes

### 1. Batch Operations Exception Handling

#### BatchWriteException

**Before (SDK v1):**
```java
public class BatchWriteException extends DataAccessException {
    public BatchWriteException(String msg, Throwable cause) {
        super(msg, cause);
    }
}

// Usage
try {
    productRepository.saveAll(products);
} catch (BatchWriteException e) {
    // Only have generic error message
    logger.error("Batch write failed: {}", e.getMessage());
    // No way to know which items failed
    // No retry information
}
```

**After (SDK v2):**
```java
public class BatchWriteException extends DataAccessException {
    public BatchWriteException(String msg, List<Object> unprocessed, int retries, Throwable cause);
    public <T> List<T> getUnprocessedEntities(Class<T> entityClass);
    public List<Object> getUnprocessedEntities();
    public int getRetriesAttempted();
    public boolean hasOriginalException();
    public int getUnprocessedCount();
}

// Usage
try {
    productRepository.saveAll(products);
    // Automatically retried up to 8 times with exponential backoff
} catch (BatchWriteException e) {
    // Get specific entities that failed
    List<Product> failed = e.getUnprocessedEntities(Product.class);

    // Send to dead letter queue
    dlqService.send("product-write-dlq", failed);

    // Check for specific exceptions
    if (e.hasOriginalException()) {
        if (e.getCause() instanceof ProvisionedThroughputExceededException) {
            alertService.alert("Throttling detected");
        }
    }

    // Monitor retry attempts
    metricsService.record("batch_write_retries", e.getRetriesAttempted());
}
```

#### BatchDeleteException

**Before (SDK v1):**
```java
public class BatchDeleteException extends DataAccessException {
    public BatchDeleteException(String msg, Throwable cause);
}

try {
    productRepository.deleteAll(products);
} catch (BatchDeleteException e) {
    logger.error("Batch delete failed: {}", e.getMessage());
}
```

**After (SDK v2):**
```java
public class BatchDeleteException extends DataAccessException {
    // Same rich API as BatchWriteException
    public BatchDeleteException(String msg, List<Object> unprocessed, int retries, Throwable cause);
    public <T> List<T> getUnprocessedEntities(Class<T> entityClass);
    // ... same methods as BatchWriteException
}

try {
    productRepository.deleteAll(products);
} catch (BatchDeleteException e) {
    List<Product> failedDeletes = e.getUnprocessedEntities(Product.class);
    dlqService.send("product-delete-dlq", failedDeletes);
}
```

**Action Required:**
- ✅ Update exception handlers to use new API
- ✅ Implement recovery strategies using `getUnprocessedEntities()`
- ✅ Add monitoring for `getRetriesAttempted()`
- ✅ Configure retry behavior if defaults don't fit your needs

---

### 2. Entity Annotations

All DynamoDB annotations have changed from SDK v1 to SDK v2 Enhanced Client annotations.

#### Table-Level Annotations

**Before (SDK v1):**
```java
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;

@DynamoDBTable(tableName = "Products")
public class Product {
    // ...
}
```

**After (SDK v2):**
```java
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
// OR for immutable entities:
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;

@DynamoDbBean
public class Product {
    // ... table name inferred from class name or configured via TableSchema
}

// OR for immutable entities with builders:
@DynamoDbImmutable(builder = Product.Builder.class)
public class Product {
    // ...
}
```

**When to use which:**
- `@DynamoDbBean` - For standard mutable POJOs with getters/setters
- `@DynamoDbImmutable` - For immutable entities (records, builder pattern, etc.)

#### Key Annotations

**Before (SDK v1):**
```java
import com.amazonaws.services.dynamodbv2.datamodeling.*;

@DynamoDBHashKey(attributeName = "id")
public String getId() { return id; }

@DynamoDBRangeKey(attributeName = "sortKey")
public String getSortKey() { return sortKey; }

@DynamoDBIndexHashKey(globalSecondaryIndexName = "GSI1")
public String getGsiHashKey() { return gsiHashKey; }

@DynamoDBIndexRangeKey(globalSecondaryIndexName = "GSI1")
public String getGsiRangeKey() { return gsiRangeKey; }
```

**After (SDK v2):**
```java
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@DynamoDbPartitionKey
public String getId() { return id; }

@DynamoDbSortKey
public String getSortKey() { return sortKey; }

@DynamoDbSecondaryPartitionKey(indexNames = {"GSI1"})
public String getGsiHashKey() { return gsiHashKey; }

@DynamoDbSecondarySortKey(indexNames = {"GSI1"})
public String getGsiRangeKey() { return gsiRangeKey; }
```

#### Attribute Annotations

**Before (SDK v1):**
```java
@DynamoDBAttribute(attributeName = "productName")
public String getName() { return name; }

@DynamoDBIgnore
public String getCalculatedField() { return calculated; }

@DynamoDBVersionAttribute
public Long getVersion() { return version; }
```

**After (SDK v2):**
```java
@DynamoDbAttribute("productName")  // Note: value() not attributeName()
public String getName() { return name; }

@DynamoDbIgnore
public String getCalculatedField() { return calculated; }

@DynamoDbVersionAttribute
public Long getVersion() { return version; }
```

**Action Required:**
- ✅ Replace all `@DynamoDBTable` with `@DynamoDbBean` or `@DynamoDbImmutable`
- ✅ Replace `@DynamoDBHashKey` with `@DynamoDbPartitionKey`
- ✅ Replace `@DynamoDBRangeKey` with `@DynamoDbSortKey`
- ✅ Replace `@DynamoDBIndexHashKey` with `@DynamoDbSecondaryPartitionKey`
- ✅ Replace `@DynamoDBIndexRangeKey` with `@DynamoDbSecondarySortKey`
- ✅ Update `@DynamoDBAttribute(attributeName=...)` to `@DynamoDbAttribute("...")`
- ✅ Note: Index annotations now use `indexNames` array instead of single `globalSecondaryIndexName`

---

### 3. Type Converters and Marshallers

SDK v2 unifies type conversion under a single `AttributeConverter` interface.

**Before (SDK v1):**
```java
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshaller;

// Type converter
public class StatusConverter implements DynamoDBTypeConverter<String, Status> {
    @Override
    public String convert(Status status) { return status.name(); }

    @Override
    public Status unconvert(String value) { return Status.valueOf(value); }
}

// Marshaller
public class JsonMarshaller implements DynamoDBMarshaller<MyObject> {
    @Override
    public String marshall(MyObject obj) { return toJson(obj); }

    @Override
    public MyObject unmarshall(Class<MyObject> clazz, String json) { return fromJson(json); }
}

// Usage
@DynamoDBTypeConverted(converter = StatusConverter.class)
public Status getStatus() { return status; }

@DynamoDBMarshalling(marshallerClass = JsonMarshaller.class)
public MyObject getData() { return data; }
```

**After (SDK v2):**
```java
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

// Single unified converter interface
public class StatusConverter implements AttributeConverter<Status> {
    @Override
    public AttributeValue transformFrom(Status status) {
        return AttributeValue.builder().s(status.name()).build();
    }

    @Override
    public Status transformTo(AttributeValue attributeValue) {
        return Status.valueOf(attributeValue.s());
    }

    @Override
    public EnhancedType<Status> type() {
        return EnhancedType.of(Status.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.S;
    }
}

// Usage
@DynamoDbConvertedBy(StatusConverter.class)
public Status getStatus() { return status; }
```

**Action Required:**
- ✅ Consolidate `DynamoDBTypeConverter` and `DynamoDBMarshaller` to `AttributeConverter`
- ✅ Replace `@DynamoDBTypeConverted` with `@DynamoDbConvertedBy`
- ✅ Replace `@DynamoDBMarshalling` with `@DynamoDbConvertedBy`
- ✅ Update converter implementations to use SDK v2 `AttributeValue` types

---

### 4. Type Marshalling and Data Compatibility ⚠️ CRITICAL

**This is the most important breaking change in version 7.0.0.** SDK v2 uses different native type mappings than SDK v1, which affects how `Boolean`, `Date`, and `Instant` values are stored in DynamoDB.

#### Understanding the Change

**SDK v1 Type Mappings:**
- `Boolean` → Number ("1" = true, "0" = false)
- `Date` → String (ISO-8601 format: "2024-01-15T10:30:00.000Z")
- `Instant` → String (ISO-8601 format: "2024-01-15T10:30:00.000Z")

**SDK v2 Native Type Mappings:**
- `Boolean` → BOOL (native DynamoDB boolean type)
- `Date` → Number (epoch milliseconds: 1705318200000) *
- `Instant` → String (ISO-8601 with nanosecond precision: "2024-01-15T10:30:00.000000000Z")

*Note: AWS SDK v2 Enhanced Client doesn't provide a default converter for `java.util.Date`. Our library stores it as Number for better query performance.

#### Marshalling Mode Configuration

To handle this breaking change, version 7.0.0 introduces **configurable marshalling modes**:

```java
@Configuration
@EnableDynamoDBRepositories(
    basePackages = "com.example.repositories",
    marshallingMode = MarshallingMode.SDK_V2_NATIVE  // or SDK_V1_COMPATIBLE
)
public class DynamoDBConfig {
    // ...
}
```

#### Mode Comparison

| Type | SDK_V1_COMPATIBLE | SDK_V2_NATIVE |
|------|-------------------|---------------|
| `Boolean` | Number ("1"/"0") | BOOL (true/false) |
| `Date` | String (ISO-8601) | Number (epoch ms) |
| `Instant` | String (ISO-8601)† | String (ISO-8601)‡ |
| `Boolean` List | Number Set | Number Set* |
| `Date` List | String Set | Number Set |
| `Instant` List | String Set | String Set |

*Note: DynamoDB doesn't support BOOL sets, so Boolean lists always use Number Set "1"/"0" in both modes.

†SDK v1 format: `"2024-01-15T10:30:00.123Z"` (millisecond precision)

‡SDK v2 format: `"2024-01-15T10:30:00.123456789Z"` (nanosecond precision, matches AWS SDK v2 `InstantAsStringAttributeConverter`)

#### Choosing the Right Mode

**Use `SDK_V2_NATIVE` (default) if:**
- ✅ You're starting a new project
- ✅ You have no existing DynamoDB data
- ✅ You're migrating and can update your data
- ✅ You want native SDK v2 behavior and better performance

**Use `SDK_V1_COMPATIBLE` if:**
- ⚠️ You have existing data in DynamoDB from SDK v1
- ⚠️ You cannot migrate/update existing data
- ⚠️ You need backward compatibility during migration
- ⚠️ Multiple applications access the same tables (some using SDK v1)

#### Migration Scenarios

**Scenario 1: New Project**

```java
@Configuration
@EnableDynamoDBRepositories(basePackages = "com.example.repositories")
// marshallingMode defaults to SDK_V2_NATIVE
public class DynamoDBConfig {
    // Nothing to configure - use defaults
}
```

**Scenario 2: Existing Project with Data Migration**

```java
// Step 1: Deploy with SDK_V1_COMPATIBLE mode
@EnableDynamoDBRepositories(
    basePackages = "com.example.repositories",
    marshallingMode = MarshallingMode.SDK_V1_COMPATIBLE
)

// Step 2: Run data migration script to convert types in DynamoDB
// - Boolean: "1"/"0" → true/false
// - Date: "2024-01-15T10:30:00Z" → 1705318200000
// - Instant: "2024-01-15T10:30:00Z" → 1705318200

// Step 3: Switch to SDK_V2_NATIVE mode
@EnableDynamoDBRepositories(
    basePackages = "com.example.repositories",
    marshallingMode = MarshallingMode.SDK_V2_NATIVE
)
```

**Scenario 3: Existing Project - Keep Compatibility**

```java
// Maintain SDK v1 compatibility permanently
@EnableDynamoDBRepositories(
    basePackages = "com.example.repositories",
    marshallingMode = MarshallingMode.SDK_V1_COMPATIBLE
)
public class DynamoDBConfig {
    // Your existing data continues to work
}
```

#### Example: Type Marshalling Impact

**Entity:**
```java
@DynamoDbBean
public class User {
    private String id;
    private Boolean active;
    private Date createdAt;
    private Instant lastLoginAt;

    // getters/setters...
}
```

**What Gets Stored in DynamoDB:**

With `SDK_V1_COMPATIBLE`:
```json
{
  "id": {"S": "user-123"},
  "active": {"N": "1"},
  "createdAt": {"S": "2024-01-15T10:30:00.000Z"},
  "lastLoginAt": {"S": "2024-01-15T10:30:00.000Z"}
}
```

With `SDK_V2_NATIVE`:
```json
{
  "id": {"S": "user-123"},
  "active": {"BOOL": true},
  "createdAt": {"N": "1705318200000"},
  "lastLoginAt": {"S": "2024-01-15T10:30:00.000000000Z"}
}
```

**Key Difference:** `Instant` values are stored as String in **both modes**, but with different precision (millisecond vs nanosecond). This ensures compatibility with AWS SDK v2 Enhanced Client's default `InstantAsStringAttributeConverter`.

#### Query and Scan Operations

The marshalling mode affects all query and scan operations:

```java
// Find by boolean value
List<User> activeUsers = userRepository.findByActive(true);

// SDK_V1_COMPATIBLE: Queries for active = "1" (Number)
// SDK_V2_NATIVE: Queries for active = true (BOOL)

// Find by date range
List<User> recentUsers = userRepository.findByCreatedAtAfter(yesterday);

// SDK_V1_COMPATIBLE: Compares ISO strings (Date)
// SDK_V2_NATIVE: Compares epoch milliseconds (Date stored as Number)

// Find by instant range
List<User> recentLogins = userRepository.findByLastLoginAtAfter(lastHour);

// SDK_V1_COMPATIBLE: Compares ISO strings with millisecond precision
// SDK_V2_NATIVE: Compares ISO strings with nanosecond precision
// Note: Both modes use string comparison for Instant (matches AWS SDK v2)
```

#### Data Migration Script Example

If migrating existing data from SDK v1 to SDK v2 native format:

```python
# Example migration script (pseudo-code)
import boto3
from datetime import datetime

dynamodb = boto3.resource('dynamodb')
table = dynamodb.Table('Users')

# Scan all items
response = table.scan()

for item in response['Items']:
    updates = {}

    # Convert Boolean: "1"/"0" → true/false
    if 'active' in item and item['active'].get('N'):
        updates['active'] = {'BOOL': item['active']['N'] == '1'}

    # Convert Date: ISO string → epoch milliseconds
    if 'createdAt' in item and item['createdAt'].get('S'):
        dt = datetime.fromisoformat(item['createdAt']['S'])
        updates['createdAt'] = {'N': str(int(dt.timestamp() * 1000))}

    # Instant: No conversion needed! Both modes use String (ISO-8601)
    # SDK v1: millisecond precision, SDK v2: nanosecond precision
    # The string format is compatible across both modes

    # Update item if changes needed
    if updates:
        table.update_item(
            Key={'id': item['id']},
            UpdateExpression='SET ' + ', '.join([f'{k} = :{k}' for k in updates]),
            ExpressionAttributeValues={f':{k}': v for k, v in updates.items()}
        )
```

**Important:** `Instant` values don't require migration! Both modes store them as ISO-8601 strings, ensuring seamless compatibility.

#### Important Considerations

1. **Index Queries**: If you have GSIs/LSIs on Boolean/Date/Instant fields, the index data format will also change. You may need to rebuild indexes after migration.

2. **Mixed Mode Access**: If multiple applications access the same table, they must all use the same marshalling mode. Mixing modes will cause data inconsistencies.

3. **Testing**: Always test with both modes in a non-production environment before migrating.

4. **Performance**: `SDK_V2_NATIVE` may offer better query performance for numeric comparisons on Date fields. Note that Instant fields use string comparison in both modes (matching AWS SDK v2 behavior).

5. **Boolean Sets**: DynamoDB doesn't support BOOL sets, so Boolean lists always use Number Set regardless of mode.

**Action Required:**
- 🔴 **CRITICAL**: Choose your marshalling mode based on your data situation
- 🔴 Add `marshallingMode = MarshallingMode.SDK_V1_COMPATIBLE` if you have existing data
- 🔴 Plan data migration if you want to use `SDK_V2_NATIVE` with existing data
- ✅ Test thoroughly with your chosen mode before production deployment
- ✅ Ensure all applications accessing the same tables use the same mode

---

### 5. Event Listeners

Event listener APIs have changed to use SDK v2 types.

**Before (SDK v1):**
```java
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedScanList;

public class MyEventListener extends AbstractDynamoDBEventListener<Product> {
    @Override
    public void onAfterQuery(Product product) {
        // Called for each item in query results
    }

    @Override
    public void onAfterScan(Product product) {
        // Called for each item in scan results
    }
}

// Events used PaginatedQueryList and PaginatedScanList internally
```

**After (SDK v2):**
```java
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import java.util.stream.StreamSupport;

public class MyEventListener extends AbstractDynamoDBEventListener<Product> {
    @Override
    public void onAfterQuery(Product product) {
        // Called for each item in query results (same API)
    }

    @Override
    public void onAfterScan(Product product) {
        // Called for each item in scan results (same API)
    }
}

// Events now use PageIterable internally
// Event listener implementation updated to use:
// StreamSupport.stream(pageIterable.items().spliterator(), false)
```

**Action Required:**
- ✅ No changes required to your event listener implementations
- ✅ Event listener contract remains the same (still called per-item)
- ⚠️ Internal change only: library now uses `PageIterable` instead of `PaginatedQueryList`/`PaginatedScanList`

---

### 6. Retry Configuration

**New Feature:** Configurable automatic retry for batch operations.

**Before (SDK v1):**
```java
// No automatic retry - had to implement manually
try {
    productRepository.saveAll(products);
} catch (BatchWriteException e) {
    // Manual retry logic required
    retryWithExponentialBackoff(products);
}
```

**After (SDK v2):**
```java
// Automatic retry with AWS defaults (8 retries, exponential backoff)
try {
    productRepository.saveAll(products);
    // Automatically retried up to 8 times
} catch (BatchWriteException e) {
    // Only throws if all retries exhausted
    List<Product> failed = e.getUnprocessedEntities(Product.class);
}

// Configure custom retry behavior
@Configuration
public class DynamoDBConfig {

    @Bean
    public BatchWriteRetryConfig batchWriteRetryConfig() {
        return new BatchWriteRetryConfig.Builder()
            .maxRetries(12)              // Default: 8
            .baseDelayMs(200L)           // Default: 100ms
            .maxDelayMs(60000L)          // Default: 20 seconds
            .useJitter(true)             // Default: true
            .build();
    }

    @Bean
    public DynamoDBTemplate dynamoDBTemplate(
            DynamoDbClient client,
            DynamoDBMapper mapper,
            DynamoDBMapperConfig config,
            BatchWriteRetryConfig retryConfig) {
        return new DynamoDBTemplate(client, mapper, config, retryConfig);
    }
}
```

**Default Retry Settings (AWS SDK v2 Standards):**
- Max retries: **8** (DynamoDB default)
- Base delay: **100ms**
- Max delay: **20 seconds**
- Exponential backoff: 100ms → 200ms → 400ms → 800ms → 1600ms → ...
- Jitter: **Enabled** (randomizes delay to prevent thundering herd)

**Action Required:**
- ✅ Review default retry settings for your use case
- ✅ Configure custom retry behavior if needed (dev vs prod)
- ✅ Update monitoring to track `getRetriesAttempted()`
- ✅ Remove manual retry logic if you were implementing it

---

## Configuration Changes

### Repository Scanning

**Before (SDK v1):**
```java
@Configuration
@EnableDynamoDBRepositories(basePackages = "com.example.repositories")
public class DynamoDBConfig {
    // Only scanned for @DynamoDBTable
}
```

**After (SDK v2):**
```java
@Configuration
@EnableDynamoDBRepositories(basePackages = "com.example.repositories")
public class DynamoDBConfig {
    // Now scans for both @DynamoDbBean AND @DynamoDbImmutable
}
```

**Action Required:**
- ✅ No changes required - backward compatible
- ✅ Can use either `@DynamoDbBean` or `@DynamoDbImmutable` on entities

---

## Step-by-Step Migration

### Phase 1: Update Dependencies

1. **Update pom.xml or build.gradle**
   ```xml
   <dependency>
       <groupId>io.github.boostchicken</groupId>
       <artifactId>spring-data-dynamodb</artifactId>
       <version>X.X.X</version> <!-- SDK v2 version -->
   </dependency>
   ```

2. **Remove SDK v1 dependencies** (if explicitly declared)
   ```xml
   <!-- Remove these -->
   <dependency>
       <groupId>com.amazonaws</groupId>
       <artifactId>aws-java-sdk-dynamodb</artifactId>
   </dependency>
   ```

3. **Add SDK v2 dependencies** (if not transitively included)
   ```xml
   <dependency>
       <groupId>software.amazon.awssdk</groupId>
       <artifactId>dynamodb-enhanced</artifactId>
       <version>2.x.x</version>
   </dependency>
   ```

### Phase 2: Update Entity Annotations

1. **Update imports**
   - Find: `com.amazonaws.services.dynamodbv2.datamodeling`
   - Replace: `software.amazon.awssdk.enhanced.dynamodb.mapper.annotations`

2. **Update table annotations**
   - Replace `@DynamoDBTable` with `@DynamoDbBean` (or `@DynamoDbImmutable`)

3. **Update key annotations**
   - Replace `@DynamoDBHashKey` → `@DynamoDbPartitionKey`
   - Replace `@DynamoDBRangeKey` → `@DynamoDbSortKey`
   - Replace `@DynamoDBIndexHashKey` → `@DynamoDbSecondaryPartitionKey(indexNames = {...})`
   - Replace `@DynamoDBIndexRangeKey` → `@DynamoDbSecondarySortKey(indexNames = {...})`

4. **Update attribute annotations**
   - Replace `@DynamoDBAttribute(attributeName = "x")` → `@DynamoDbAttribute("x")`

### Phase 3: Update Type Converters

1. **Consolidate converters**
   - Migrate `DynamoDBTypeConverter` → `AttributeConverter`
   - Migrate `DynamoDBMarshaller` → `AttributeConverter`

2. **Update annotations**
   - Replace `@DynamoDBTypeConverted(converter = ...)` → `@DynamoDbConvertedBy(...)`
   - Replace `@DynamoDBMarshalling(marshallerClass = ...)` → `@DynamoDbConvertedBy(...)`

### Phase 4: Configure Marshalling Mode (CRITICAL)

1. **Assess your data situation**
   - Do you have existing DynamoDB data? → Use `SDK_V1_COMPATIBLE`
   - Starting fresh or can migrate data? → Use `SDK_V2_NATIVE`

2. **Update configuration**
   ```java
   @Configuration
   @EnableDynamoDBRepositories(
       basePackages = "com.example.repositories",
       marshallingMode = MarshallingMode.SDK_V1_COMPATIBLE  // If you have existing data
   )
   public class DynamoDBConfig {
       // ...
   }
   ```

3. **Test with your chosen mode**
   - Verify Boolean queries work correctly
   - Verify Date/Instant queries work correctly
   - Check that data reads match what's in DynamoDB

4. **Plan data migration (optional)**
   - If using SDK_V2_NATIVE and have existing data
   - Create migration script (see example in section 4)
   - Test in non-production first
   - Schedule maintenance window

### Phase 5: Update Exception Handling

1. **Update batch write handlers**
   ```java
   try {
       repository.saveAll(entities);
   } catch (BatchWriteException e) {
       // OLD: logger.error("Failed: {}", e.getMessage());

       // NEW: Get specific failed entities
       List<Entity> failed = e.getUnprocessedEntities(Entity.class);
       dlqService.send(failed);
       metricsService.record("retries", e.getRetriesAttempted());
   }
   ```

2. **Update batch delete handlers**
   ```java
   try {
       repository.deleteAll(entities);
   } catch (BatchDeleteException e) {
       // Same rich API as BatchWriteException
       List<Entity> failed = e.getUnprocessedEntities(Entity.class);
       dlqService.send(failed);
   }
   ```

### Phase 6: Configure Retry Behavior

1. **Review defaults** (8 retries, 100ms base delay, 20s max)

2. **Configure if needed**
   ```java
   @Bean
   public BatchWriteRetryConfig retryConfig() {
       return new BatchWriteRetryConfig.Builder()
           .maxRetries(12)
           .baseDelayMs(200L)
           .build();
   }
   ```

### Phase 7: Testing

1. **Unit tests** - Update mocks to use SDK v2 types
2. **Integration tests** - Test with real DynamoDB (or LocalStack)
3. **Error scenarios** - Test throttling, validation errors
4. **Monitoring** - Verify metrics, alerts work with new APIs

---

## New Features & Capabilities

### 1. Unprocessed Entity Access

Get specific entities that failed processing:
```java
catch (BatchWriteException e) {
    List<Product> failed = e.getUnprocessedEntities(Product.class);
    // Implement custom recovery logic
}
```

### 2. Automatic Retry with Exponential Backoff

No manual retry logic needed - automatically handles transient failures:
```java
// Automatically retries with exponential backoff
productRepository.saveAll(products);
```

### 3. Retry Context Tracking

Monitor retry attempts for capacity planning:
```java
catch (BatchWriteException e) {
    metricsService.record("batch_write_retries", e.getRetriesAttempted());
    if (e.getRetriesAttempted() >= 8) {
        alertService.alert("Exhausted retries - capacity issue?");
    }
}
```

### 4. Exception Type Awareness

Handle specific errors differently:
```java
if (e.hasOriginalException()) {
    if (e.getCause() instanceof ProvisionedThroughputExceededException) {
        // Throttling - need more capacity
    } else if (e.getCause() instanceof ValidationException) {
        // Invalid data - don't retry
    }
}
```

### 5. Immutable Entity Support

Use immutable entities with `@DynamoDbImmutable`:
```java
@DynamoDbImmutable(builder = Product.Builder.class)
public class Product {
    private final String id;
    private final String name;
    // ... immutable fields with builder
}
```

---

## Troubleshooting

### Issue: "Cannot resolve annotation @DynamoDbBean"

**Cause:** Missing SDK v2 Enhanced Client dependency

**Solution:**
```xml
<dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>dynamodb-enhanced</artifactId>
    <version>2.x.x</version>
</dependency>
```

### Issue: "BatchWriteException constructor not found"

**Cause:** Old exception handler code expecting SDK v1 constructor

**Solution:** Update to use new constructor:
```java
// OLD: new BatchWriteException(msg, cause)
// NEW: new BatchWriteException(msg, unprocessedEntities, retries, cause)
```

### Issue: "Table name not found"

**Cause:** SDK v2 infers table name from class name by default

**Solution:** Configure via `DynamoDBMapperConfig` or use `TableSchema` with explicit table name

### Issue: "Unprocessed items after retries"

**Cause:** Persistent throttling or capacity issues

**Solution:**
- Increase provisioned throughput
- Use on-demand billing
- Implement application-level rate limiting
- Adjust retry configuration (more retries, longer delays)

### Issue: "AttributeConverter not working"

**Cause:** Incorrect converter implementation

**Solution:** Ensure converter implements all required methods:
```java
public class MyConverter implements AttributeConverter<MyType> {
    public AttributeValue transformFrom(MyType input);
    public MyType transformTo(AttributeValue input);
    public EnhancedType<MyType> type();
    public AttributeValueType attributeValueType();
}
```

---

## FAQ

### Q: Is this a breaking change?

**A:** Yes. Major breaking changes in:
- Exception APIs (BatchWriteException, BatchDeleteException)
- Entity annotations (all @DynamoDB* → @DynamoDb*)
- Type converter interfaces (unified to AttributeConverter)

### Q: Can I migrate incrementally?

**A:** No. This is an all-or-nothing migration because:
- AWS SDK v1 and v2 cannot coexist in the same dependency tree
- Annotation changes affect all entities
- Exception API changes affect all batch operation handlers

### Q: What if I don't handle BatchWriteException?

**A:** The exception will propagate up. Key differences:
- SDK v1: Threw immediately on any failure
- SDK v2: Automatically retries 8 times before throwing

### Q: Will automatic retry increase latency?

**A:** Only when failures occur:
- Success: No additional latency
- Partial failure: Retries with exponential backoff (100ms, 200ms, 400ms...)
- Full failure: Max ~40 seconds (8 retries with 20s max delay)

### Q: How do I disable automatic retry?

**A:**
```java
@Bean
public BatchWriteRetryConfig noRetryConfig() {
    return new BatchWriteRetryConfig.Builder()
        .disableRetries()  // maxRetries = 0
        .build();
}
```

### Q: Do I need to update my event listeners?

**A:** No. The event listener contract remains unchanged. Internal implementation uses SDK v2 types, but your listeners continue to work as before.

### Q: What about @DynamoDBTable(tableName = "...")?

**A:** SDK v2 infers table name from class name. To override:
- Use `DynamoDBMapperConfig` with `TableNameOverride`
- Or configure via `TableSchema`

### Q: Can I use both @DynamoDbBean and @DynamoDbImmutable in the same project?

**A:** Yes! Use `@DynamoDbBean` for standard POJOs and `@DynamoDbImmutable` for immutable entities as needed.

### Q: Which marshalling mode should I use?

**A:**
- **SDK_V1_COMPATIBLE** if you have existing DynamoDB data from SDK v1. This maintains backward compatibility with your existing data format.
- **SDK_V2_NATIVE** (default) if you're starting a new project or can migrate your existing data. This uses AWS SDK v2's native type mappings and may offer better performance.

### Q: What happens if I choose the wrong marshalling mode?

**A:**
- If you use **SDK_V2_NATIVE** with existing SDK v1 data, your queries will fail to match existing records (e.g., searching for `Boolean true` won't match records with Number "1").
- If you use **SDK_V1_COMPATIBLE** with SDK v2 native data, you'll have the opposite problem.
- Always test your chosen mode thoroughly in a non-production environment first.

### Q: Can I change marshalling mode after deployment?

**A:** Yes, but with care:
1. You'll need to migrate all existing data to the new format
2. All applications accessing the same tables must be updated simultaneously
3. Consider using a blue-green deployment strategy
4. Test extensively in staging first

### Q: Do I need to migrate data if I use SDK_V1_COMPATIBLE mode?

**A:** No! That's the point of SDK_V1_COMPATIBLE mode. It maintains backward compatibility with your existing data, so no migration is needed.

---

## Additional Resources

### Documentation
- [Batch Operations Error Handling Guide](BATCH_OPERATIONS_ERROR_HANDLING.md)
- [Batch Retry Configuration Guide](BATCH_RETRY_CONFIGURATION.md)
- [SDK v2 Implementation Summary](SDK_V2_BATCH_WRITE_MIGRATION_SUMMARY.md)

### AWS Documentation
- [AWS SDK for Java 2.x Developer Guide](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/)
- [DynamoDB Enhanced Client](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/ddb-en-client.html)
- [DynamoDB Error Handling](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Programming.Errors.html)

### Examples
- See `BATCH_OPERATIONS_ERROR_HANDLING.md` for production patterns
- See `BatchWriteExceptionTest.java` and `BatchDeleteExceptionTest.java` for usage examples

---

## Support

If you encounter issues during migration:
1. Check the [Troubleshooting](#troubleshooting) section
2. Review the [FAQ](#faq)
3. File an issue with:
   - Error message
   - SDK versions (before/after)
   - Code snippet showing the issue
   - Migration step where issue occurred

---

## Summary Checklist

Use this checklist to track your migration progress:

### Dependencies
- [ ] Updated to SDK v2 version of spring-data-dynamodb
- [ ] Removed SDK v1 dependencies
- [ ] Added SDK v2 Enhanced Client dependency

### Entity Annotations
- [ ] Replaced `@DynamoDBTable` with `@DynamoDbBean` or `@DynamoDbImmutable`
- [ ] Replaced `@DynamoDBHashKey` with `@DynamoDbPartitionKey`
- [ ] Replaced `@DynamoDBRangeKey` with `@DynamoDbSortKey`
- [ ] Replaced `@DynamoDBIndexHashKey` with `@DynamoDbSecondaryPartitionKey`
- [ ] Replaced `@DynamoDBIndexRangeKey` with `@DynamoDbSecondarySortKey`
- [ ] Updated `@DynamoDBAttribute` to `@DynamoDbAttribute`

### Type Converters
- [ ] Migrated `DynamoDBTypeConverter` to `AttributeConverter`
- [ ] Migrated `DynamoDBMarshaller` to `AttributeConverter`
- [ ] Updated converter annotations to `@DynamoDbConvertedBy`

### Type Marshalling (CRITICAL)
- [ ] **Determined marshalling mode** (SDK_V2_NATIVE or SDK_V1_COMPATIBLE)
- [ ] **Added `marshallingMode` to `@EnableDynamoDBRepositories`** if using SDK_V1_COMPATIBLE
- [ ] Planned data migration strategy if using SDK_V2_NATIVE with existing data
- [ ] Verified all applications accessing same tables use same mode
- [ ] Tested Boolean, Date, and Instant queries with chosen mode

### Exception Handling
- [ ] Updated BatchWriteException handlers to use new API
- [ ] Updated BatchDeleteException handlers to use new API
- [ ] Implemented DLQ or recovery logic using `getUnprocessedEntities()`
- [ ] Added monitoring for `getRetriesAttempted()`

### Configuration
- [ ] Reviewed default retry settings
- [ ] Configured custom retry behavior if needed
- [ ] Updated Spring configuration beans

### Testing
- [ ] Unit tests pass with SDK v2
- [ ] Integration tests pass with SDK v2
- [ ] Error scenario tests updated
- [ ] Monitoring and metrics verified

### Deployment
- [ ] Staged rollout plan created
- [ ] Rollback plan prepared
- [ ] Team trained on new exception handling
- [ ] Documentation updated

---

**Migration Guide Version:** 2.0
**Last Updated:** 2025-01-12
**Spring Data DynamoDB Version:** 7.0.0-SNAPSHOT (includes configurable marshalling modes)
