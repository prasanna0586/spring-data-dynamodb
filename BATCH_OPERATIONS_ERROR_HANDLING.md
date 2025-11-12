# Batch Operations Error Handling - Production Guide

## Overview

With AWS SDK v2 migration, batch operations error handling has changed to align with AWS best practices. This guide explains how to handle `BatchWriteException` and `BatchDeleteException` in production environments.

Both batch write (`saveAll()`) and batch delete (`deleteAll()`) operations now provide:
- Access to unprocessed entities
- Retry context information
- Original exception details
- Type-safe entity retrieval

## What Changed from SDK v1

### SDK v1 (Before)
```java
try {
    productRepository.saveAll(products);
    // or
    productRepository.deleteAll(products);
} catch (BatchWriteException | BatchDeleteException e) {
    // Generic error message only
    // No access to which items failed
    // Had to retry entire batch manually
}
```

### SDK v2 (After)
```java
try {
    productRepository.saveAll(products);
    // Success - all items saved (possibly after automatic retries)
} catch (BatchWriteException e) {
    // Get actual entities that failed
    List<Product> failed = e.getUnprocessedEntities(Product.class);

    // Check if there was a specific exception
    if (e.hasOriginalException()) {
        // Handle specific error types
    }

    // Know how many retries were attempted
    int retries = e.getRetriesAttempted();
}

// Same API for batch deletes
try {
    productRepository.deleteAll(products);
} catch (BatchDeleteException e) {
    List<Product> failedDeletes = e.getUnprocessedEntities(Product.class);
    // ... handle failed deletes
}
```

## Understanding Batch Exceptions

Both `BatchWriteException` and `BatchDeleteException` provide the same rich API with three key pieces of information:

1. **Unprocessed Entities** - Actual objects that couldn't be written/deleted
2. **Retries Attempted** - Number of retry attempts made (default: 8)
3. **Original Exception** - Specific error if one was thrown

## Production Use Cases

### 1. Dead Letter Queue (DLQ) Pattern

Store failed items for later processing:

```java
@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DeadLetterQueueService dlqService;

    @Autowired
    private MetricsService metricsService;

    public void saveProducts(List<Product> products) {
        try {
            productRepository.saveAll(products);
            metricsService.recordSuccess("product_batch_write", products.size());

        } catch (BatchWriteException e) {
            // Get entities that couldn't be written
            List<Product> failed = e.getUnprocessedEntities(Product.class);

            // Send to DLQ for later processing
            dlqService.sendToQueue("product-writes-dlq", failed);

            // Record metrics
            metricsService.recordFailure("product_batch_write",
                failed.size(),
                e.getRetriesAttempted());

            // Log with context
            logger.error("Failed to save {} products after {} retries. Sent to DLQ.",
                failed.size(), e.getRetriesAttempted(), e);

            // Decide whether to throw or handle gracefully
            // For non-critical operations, might just log and continue
            if (isCriticalOperation()) {
                throw e;
            }
        }
    }

    public void deleteExpiredProducts(List<Product> expiredProducts) {
        try {
            productRepository.deleteAll(expiredProducts);
            metricsService.recordSuccess("product_batch_delete", expiredProducts.size());

        } catch (BatchDeleteException e) {
            // Get entities that couldn't be deleted
            List<Product> failed = e.getUnprocessedEntities(Product.class);

            // Send to DLQ for retry
            dlqService.sendToQueue("product-deletes-dlq", failed);

            // Record metrics
            metricsService.recordFailure("product_batch_delete",
                failed.size(),
                e.getRetriesAttempted());

            logger.error("Failed to delete {} expired products after {} retries.",
                failed.size(), e.getRetriesAttempted(), e);
        }
    }
}
```

### 2. Alerting on Specific Error Types

Handle different error types appropriately:

```java
public void saveCriticalData(List<Order> orders) {
    try {
        orderRepository.saveAll(orders);

    } catch (BatchWriteException e) {
        List<Order> failed = e.getUnprocessedEntities(Order.class);

        if (e.hasOriginalException()) {
            Throwable cause = e.getCause();

            if (cause instanceof ProvisionedThroughputExceededException) {
                // Throttling - need more capacity
                alertingService.sendAlert(
                    AlertLevel.HIGH,
                    "DynamoDB Throttling Detected",
                    String.format("Failed to write %d orders. Consider increasing provisioned capacity.",
                        failed.size())
                );

                // Could implement custom retry with different strategy
                retryWithCustomStrategy(failed);

            } else if (cause instanceof ValidationException) {
                // Data validation issue - don't retry
                alertingService.sendAlert(
                    AlertLevel.CRITICAL,
                    "Invalid Order Data",
                    "Order data failed DynamoDB validation: " + cause.getMessage()
                );

                // Log problematic orders for investigation
                failed.forEach(order ->
                    logger.error("Invalid order data: {}", order));

            } else if (cause instanceof ResourceNotFoundException) {
                // Table doesn't exist - critical infrastructure issue
                alertingService.sendAlert(
                    AlertLevel.CRITICAL,
                    "DynamoDB Table Missing",
                    "Orders table not found: " + cause.getMessage()
                );
                throw e; // Can't recover from this
            }

        } else {
            // No specific exception - items just remained unprocessed after retries
            // Likely persistent throttling or capacity issues
            alertingService.sendAlert(
                AlertLevel.MEDIUM,
                "Persistent Write Failures",
                String.format("%d orders unprocessed after %d retries. Check DynamoDB metrics.",
                    failed.size(), e.getRetriesAttempted())
            );
        }
    }
}
```

### 3. Custom Retry with Different Strategy

Implement application-level retry with custom configuration:

```java
public void saveWithCustomRetry(List<Product> products, int maxAttempts) {
    List<Product> remaining = new ArrayList<>(products);
    int attempt = 0;

    while (!remaining.isEmpty() && attempt < maxAttempts) {
        try {
            productRepository.saveAll(remaining);
            // Success - all items saved
            return;

        } catch (BatchWriteException e) {
            attempt++;
            remaining = e.getUnprocessedEntities(Product.class);

            if (remaining.isEmpty()) {
                // All items eventually succeeded
                logger.info("All items saved after {} attempts", attempt);
                return;
            }

            if (attempt < maxAttempts) {
                // Custom backoff strategy (e.g., longer delays)
                long delay = calculateCustomBackoff(attempt);
                logger.warn("Attempt {} failed. Retrying {} items after {}ms",
                    attempt, remaining.size(), delay);

                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Retry interrupted", ie);
                }
            } else {
                // Max attempts reached
                logger.error("Failed to save {} items after {} attempts",
                    remaining.size(), maxAttempts);
                throw e;
            }
        }
    }
}

private long calculateCustomBackoff(int attempt) {
    // Custom strategy: longer delays than default
    return Math.min(1000L * (1L << attempt), 60000L); // Max 60 seconds
}
```

### 4. Monitoring and Metrics

Track batch write failures for operational visibility:

```java
@Service
public class MonitoredBatchWriter {

    @Autowired
    private CloudWatchClient cloudWatch;

    public void saveWithMonitoring(List<Product> products) {
        long startTime = System.currentTimeMillis();

        try {
            productRepository.saveAll(products);

            // Record success metrics
            publishMetric("BatchWriteSuccess", products.size());
            publishMetric("BatchWriteLatency", System.currentTimeMillis() - startTime);

        } catch (BatchWriteException e) {
            List<Product> failed = e.getUnprocessedEntities(Product.class);

            // Record failure metrics
            publishMetric("BatchWriteFailure", failed.size());
            publishMetric("BatchWriteRetries", e.getRetriesAttempted());

            // Calculate failure rate
            double failureRate = (double) failed.size() / products.size();
            publishMetric("BatchWriteFailureRate", failureRate * 100);

            // Record exception type if available
            if (e.hasOriginalException()) {
                String exceptionType = e.getCause().getClass().getSimpleName();
                publishMetric("BatchWriteException_" + exceptionType, 1);
            }

            // Check if failure rate exceeds threshold
            if (failureRate > 0.10) { // 10% threshold
                alertingService.sendAlert(
                    AlertLevel.HIGH,
                    "High Batch Write Failure Rate",
                    String.format("%.1f%% of items failed to write", failureRate * 100)
                );
            }

            throw e;
        }
    }

    private void publishMetric(String metricName, double value) {
        // Publish to CloudWatch or your metrics system
        cloudWatch.putMetricData(builder -> builder
            .namespace("MyApp/DynamoDB")
            .metricData(data -> data
                .metricName(metricName)
                .value(value)
                .timestamp(Instant.now())
            )
        );
    }
}
```

### 5. Partial Success Handling

Handle scenarios where partial success is acceptable:

```java
public BatchWriteResult saveOptionalData(List<CacheEntry> entries) {
    try {
        cacheRepository.saveAll(entries);
        return new BatchWriteResult(entries.size(), 0, Collections.emptyList());

    } catch (BatchWriteException e) {
        List<CacheEntry> failed = e.getUnprocessedEntities(CacheEntry.class);
        int succeeded = entries.size() - failed.size();

        // For cache entries, partial failure might be acceptable
        logger.warn("Cache write partially failed: {} succeeded, {} failed",
            succeeded, failed.size());

        // Return result indicating partial success
        return new BatchWriteResult(succeeded, failed.size(), failed);
    }
}

public static class BatchWriteResult {
    private final int succeeded;
    private final int failed;
    private final List<?> failedEntities;

    // Constructor and getters...
}
```

### 6. Transaction Rollback Pattern

For operations requiring all-or-nothing semantics:

```java
@Transactional
public void saveOrderWithItems(Order order, List<OrderItem> items) {
    try {
        // Save order first
        orderRepository.save(order);

        // Then save all items
        orderItemRepository.saveAll(items);

    } catch (BatchWriteException e) {
        // Batch write failed - need to clean up order
        List<OrderItem> failed = e.getUnprocessedEntities(OrderItem.class);

        logger.error("Failed to save {} order items for order {}. Rolling back.",
            failed.size(), order.getId());

        // Delete the order since items couldn't be saved
        orderRepository.delete(order);

        // Throw to propagate failure
        throw new OrderCreationException(
            "Failed to create order: could not save all items",
            order,
            failed,
            e
        );
    }
}
```

## Best Practices

### 1. Always Extract Unprocessed Entities
```java
// ✅ Good - Get specific failed entities
List<Product> failed = e.getUnprocessedEntities(Product.class);
dlqService.send(failed);

// ❌ Bad - Just log generic error
logger.error("Batch write failed", e);
```

### 2. Check for Original Exception
```java
// ✅ Good - Handle different error types
if (e.hasOriginalException()) {
    if (e.getCause() instanceof ProvisionedThroughputExceededException) {
        // Handle throttling
    }
}

// ❌ Bad - Treat all failures the same
logger.error("Failed", e);
```

### 3. Monitor Retry Counts
```java
// ✅ Good - Track retries for capacity planning
if (e.getRetriesAttempted() >= 8) {
    alertService.alert("Exhausted all retries - capacity issue?");
}

// ❌ Bad - Ignore retry information
throw e;
```

### 4. Implement Appropriate Recovery
```java
// ✅ Good - Context-appropriate recovery
if (isCriticalData) {
    throw e; // Fail fast for critical operations
} else {
    sendToDLQ(e.getUnprocessedEntities()); // Graceful degradation
}

// ❌ Bad - Same handling for all scenarios
throw e;
```

## Testing Error Scenarios

Example test cases:

```java
@Test
void shouldHandleThrottlingGracefully() {
    // Simulate throttling scenario
    when(mockTemplate.batchSave(any()))
        .thenThrow(new ProvisionedThroughputExceededException("Throttled"));

    List<Product> products = createProducts(10);

    BatchWriteException ex = assertThrows(BatchWriteException.class,
        () -> productService.saveProducts(products));

    // Verify error handling
    assertTrue(ex.hasOriginalException());
    assertEquals(ProvisionedThroughputExceededException.class,
        ex.getCause().getClass());

    // Verify DLQ was called
    verify(dlqService).sendToQueue(eq("product-dlq"), anyList());
}

@Test
void shouldRetryUnprocessedItems() {
    Product p1 = new Product("1", "Item1");
    Product p2 = new Product("2", "Item2");

    List<Object> unprocessed = Arrays.asList(p1, p2);
    BatchWriteException ex = new BatchWriteException(
        "Some items failed",
        unprocessed,
        8,
        null
    );

    List<Product> failed = ex.getUnprocessedEntities(Product.class);
    assertEquals(2, failed.size());

    // Implement custom retry
    customRetryService.retry(failed);
}
```

## Configuration Recommendations

### Development
```java
@Bean
public BatchWriteRetryConfig devRetryConfig() {
    return new BatchWriteRetryConfig.Builder()
        .maxRetries(3)          // Fail faster in dev
        .baseDelayMs(50L)       // Shorter delays
        .useJitter(false)       // Predictable timing for debugging
        .build();
}
```

### Production
```java
@Bean
public BatchWriteRetryConfig prodRetryConfig() {
    return new BatchWriteRetryConfig(); // Use AWS defaults
    // - maxRetries: 8
    // - baseDelay: 100ms
    // - maxDelay: 20 seconds
    // - jitter: enabled
}
```

### High-Throughput
```java
@Bean
public BatchWriteRetryConfig highThroughputRetryConfig() {
    return new BatchWriteRetryConfig.Builder()
        .maxRetries(12)         // More retries
        .baseDelayMs(200L)      // Start with longer delay
        .maxDelayMs(60000L)     // Cap at 1 minute
        .useJitter(true)        // Spread load
        .build();
}
```

## Summary

The new `BatchWriteException` provides production-grade error handling by:

✅ Exposing unprocessed entities for custom recovery
✅ Preserving original exceptions for specific error handling
✅ Providing retry context for monitoring and alerting
✅ Enabling type-safe entity retrieval
✅ Supporting partial success scenarios

This aligns with AWS SDK v2 best practices and enables robust production applications.
