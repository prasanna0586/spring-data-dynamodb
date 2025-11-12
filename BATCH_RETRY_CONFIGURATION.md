# Batch Write Retry Configuration

## Overview

AWS SDK v2 changed how batch write operations handle failures. Instead of throwing exceptions for individual item failures, operations return "unprocessed items" that should be retried with exponential backoff.

This library now implements AWS-recommended retry logic with configurable exponential backoff.

## Default Behavior

By default, batch write operations (`saveAll()`, `deleteAll()`) will automatically retry unprocessed items using AWS SDK for Java 2.x settings:

- **Max retries**: 8 attempts (DynamoDB default)
- **Base delay**: 100ms (doubles each retry: 100ms, 200ms, 400ms, 800ms, 1600ms...)
- **Max delay**: 20 seconds (caps exponential growth)
- **Jitter**: Enabled (adds randomization to prevent thundering herd)

## Configuration

### Using Spring Boot Configuration

```java
@Configuration
public class DynamoDBConfig {

    @Bean
    public BatchWriteRetryConfig batchWriteRetryConfig() {
        // Use defaults (AWS recommended)
        return new BatchWriteRetryConfig();
    }

    @Bean
    public DynamoDBTemplate dynamoDBTemplate(
            DynamoDbClient dynamoDbClient,
            DynamoDBMapper dynamoDBMapper,
            DynamoDBMapperConfig mapperConfig,
            BatchWriteRetryConfig retryConfig) {
        return new DynamoDBTemplate(dynamoDbClient, dynamoDBMapper, mapperConfig, retryConfig);
    }
}
```

### Custom Configuration

```java
@Bean
public BatchWriteRetryConfig batchWriteRetryConfig() {
    return new BatchWriteRetryConfig.Builder()
        .maxRetries(15)              // More retries for high-throughput scenarios
        .baseDelayMs(100L)           // Start with longer delay
        .maxDelayMs(60000L)          // Cap at 1 minute
        .useJitter(true)             // Keep jitter enabled (recommended)
        .build();
}
```

### Disable Retries

```java
@Bean
public BatchWriteRetryConfig batchWriteRetryConfig() {
    return new BatchWriteRetryConfig.Builder()
        .disableRetries()  // maxRetries = 0, fails immediately
        .build();
}
```

## How It Works

### 1. Batch Write Operation

```java
List<User> users = Arrays.asList(user1, user2, user3);
userRepository.saveAll(users);  // Automatically retries unprocessed items
```

### 2. Internal Flow

```
Attempt 1: Write 100 items
  └─> 10 items unprocessed (throttled)
  └─> Wait 100ms

Attempt 2: Write 10 items
  └─> 2 items unprocessed
  └─> Wait 200ms

Attempt 3: Write 2 items
  └─> Success! All items processed
```

### 3. Retry Exhausted

If items remain unprocessed after max retries, a `BatchWriteException` is thrown:

```
BatchWriteException: Processing of entities failed after retries!
  Caused by: RuntimeException: Batch write operation failed with 1 batch(es)
  containing unprocessed items after exhausting retries...
```

## Migration from SDK v1

### SDK v1 Behavior (Before)
- Batch operations threw exceptions immediately on failure
- No automatic retry logic
- Rich exception details (ProvisionedThroughputExceededException, etc.)

### SDK v2 Behavior (After)
- Batch operations return unprocessed items instead of throwing exceptions
- Automatic retry with exponential backoff
- Generic error message after retry exhaustion (AWS doesn't provide specific error reasons for unprocessed items)

### Code Changes Required

**Before (SDK v1):**
```java
try {
    userRepository.saveAll(users);
} catch (ProvisionedThroughputExceededException e) {
    // Handle throttling
}
```

**After (SDK v2):**
```java
try {
    userRepository.saveAll(users);
    // Success - items saved (possibly after internal retries)
} catch (BatchWriteException e) {
    // Only thrown if retries exhausted
    // Could be throttling, capacity limits, or conflicts
    // Increase table throughput or adjust retry config
}
```

## Why Unprocessed Items Occur

AWS returns unprocessed items when:

1. **Throttling**: Provisioned throughput exceeded
2. **Capacity limits**: Too many concurrent requests to the same partition
3. **Transaction conflicts**: Concurrent updates to the same item
4. **Service limits**: Batch size or item size limits

AWS expects you to retry these items with exponential backoff, which this library now handles automatically.

## Best Practices

### 1. Use Default Configuration
The defaults follow AWS recommendations and work well for most use cases.

### 2. Monitor CloudWatch Metrics
- `UserErrors` - indicates throttling or invalid requests
- `SystemErrors` - indicates AWS service issues
- `ConsumedReadCapacityUnits` / `ConsumedWriteCapacityUnits`

### 3. Adjust Provisioned Throughput
If retries are frequently exhausted, consider:
- Increasing table/GSI provisioned capacity
- Using on-demand billing mode
- Implementing application-level rate limiting

### 4. Consider Batch Size
Smaller batches (10-15 items) are less likely to be throttled than large batches (25 items).

### 5. Use Jitter
Keep jitter enabled to prevent thundering herd when multiple clients retry simultaneously.

## Implementation Status

This feature is currently being implemented as part of the AWS SDK v2 migration:

- ✅ `BatchWriteRetryConfig` - Configuration class created
- ✅ `ExceptionHandler` - Updated for SDK v2
- ⏳ `DynamoDBTemplate` - Needs SDK v2 migration + retry logic integration
- ⏳ Integration tests - To be added

Once `DynamoDBTemplate` is migrated to SDK v2, retry logic will be fully functional.

## References

- [AWS DynamoDB Error Handling](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Programming.Errors.html)
- [AWS SDK v2 Batch Operations](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/ddb-en-client-use-multiop-batch.html)
- [Exponential Backoff and Jitter](https://aws.amazon.com/blogs/architecture/exponential-backoff-and-jitter/)
