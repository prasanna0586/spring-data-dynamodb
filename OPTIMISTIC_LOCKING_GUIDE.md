# Optimistic Locking Guide

This guide explains how to use optimistic locking in spring-data-dynamodb 7.0.0 (SDK v2) for both existing SDK v1 users and new SDK v2 users.

## Overview

Optimistic locking prevents concurrent modification conflicts by tracking a version number on each item. When an item is updated, DynamoDB checks that the version hasn't changed since it was read, and automatically increments the version on successful writes.

## SDK v2 Built-in Support

AWS SDK v2's Enhanced Client has **built-in optimistic locking** through:
- `@DynamoDbVersionAttribute` annotation
- `VersionedRecordExtension` (loaded automatically)

This works seamlessly with both `SDK_V2_NATIVE` and `SDK_V1_COMPATIBLE` marshalling modes.

---

## For SDK_V2_NATIVE Users (New Projects)

### Basic Usage

Simply annotate a `Long` or `Integer` field with `@DynamoDbVersionAttribute`:

```java
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;

@DynamoDbBean
public class Product {

    @DynamoDbPartitionKey
    private String productId;

    @DynamoDbAttribute
    private String name;

    @DynamoDbAttribute
    private Double price;

    @DynamoDbVersionAttribute
    private Long version;  // Automatically managed by SDK v2

    // Getters and setters...
}
```

### How It Works

1. **Initial Save**: When a new item is created, `version` is set to `0`
2. **Subsequent Updates**: Each update increments `version` by `1`
3. **Concurrent Modification**: If two clients try to update the same item concurrently:
   - First update succeeds (version: 0 → 1)
   - Second update fails with `ConditionalCheckFailedException` (expected version 0, actual version 1)

### Configuration Options

You can customize the starting value and increment amount:

```java
@DynamoDbVersionAttribute(startAt = 1, incrementBy = 2)
private Long version;
```

- `startAt`: Initial version for new items (default: 0)
- `incrementBy`: Amount to increment on each update (default: 1)

### Handling Concurrent Modifications

```java
@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    public void updatePrice(String productId, Double newPrice) {
        try {
            Product product = productRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("Product not found"));

            product.setPrice(newPrice);
            productRepository.save(product);  // Optimistic locking applied automatically

        } catch (Exception e) {
            // Check if it's a concurrent modification
            if (e.getCause() instanceof software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException) {
                // Retry logic or notify user
                throw new ConcurrentModificationException("Product was modified by another user");
            }
            throw e;
        }
    }
}
```

---

## For SDK_V1_COMPATIBLE Users (Migrating from SDK v1)

### Migration Steps

**Before (SDK v1):**
```java
import com.amazonaws.services.dynamodbv2.datamodeling.*;

@DynamoDBTable(tableName = "Products")
public class Product {

    @DynamoDBHashKey
    private String productId;

    @DynamoDBAttribute
    private String name;

    @DynamoDBVersionAttribute
    private Long version;
}
```

**After (SDK v2 with SDK_V1_COMPATIBLE mode):**
```java
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;

@DynamoDbBean
public class Product {

    @DynamoDbPartitionKey
    private String productId;

    @DynamoDbAttribute
    private String name;

    @DynamoDbVersionAttribute  // Changed: DynamoDBVersionAttribute → DynamoDbVersionAttribute
    private Long version;
}
```

### Key Changes

| SDK v1 | SDK v2 |
|--------|--------|
| `@DynamoDBVersionAttribute` | `@DynamoDbVersionAttribute` |
| `com.amazonaws.services.dynamodbv2.datamodeling.*` | `software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.*` |

**Note:** Just change `DB` to `Db` in the annotation name!

### Behavior Compatibility

SDK v2's optimistic locking behavior is **identical** to SDK v1:
- ✅ Same version increment logic
- ✅ Same conditional check mechanism
- ✅ Same exception on concurrent modification (`ConditionalCheckFailedException`)
- ✅ Works with existing data (existing version values are preserved)

---

## Important Notes

### Supported Types

The version attribute must be a **numeric integer type**:
- `Long` / `long` (recommended)
- `Integer` / `int`

**Not supported:** `String`, `BigInteger`, `Double`, etc.

### DynamoDB Storage

The version attribute is stored as a **Number (N)** type in DynamoDB:
```json
{
  "productId": { "S": "PROD-123" },
  "name": { "S": "Widget" },
  "price": { "N": "19.99" },
  "version": { "N": "5" }
}
```

### Automatic Management

**You should NOT manually set the version field** in your application code:
```java
// ❌ WRONG - Don't do this:
Product product = new Product();
product.setProductId("PROD-123");
product.setVersion(0L);  // Don't set version manually!

// ✅ CORRECT - Let SDK manage it:
Product product = new Product();
product.setProductId("PROD-123");
// version will be set to 0 automatically on save
```

### Read Operations

Version attributes are **read automatically** and included in the returned object:
```java
Product product = productRepository.findById("PROD-123").get();
System.out.println("Current version: " + product.getVersion());  // e.g., 5
```

---

## Complete Example

### Entity
```java
@DynamoDbBean
public class BankAccount {

    @DynamoDbPartitionKey
    private String accountId;

    @DynamoDbAttribute
    private Double balance;

    @DynamoDbVersionAttribute
    private Long version;

    // Getters and setters...
}
```

### Repository
```java
public interface BankAccountRepository extends DynamoDBPagingAndSortingRepository<BankAccount, String> {
}
```

### Service with Retry Logic
```java
@Service
public class BankingService {

    @Autowired
    private BankAccountRepository accountRepository;

    public void transfer(String fromAccountId, String toAccountId, Double amount) {
        int maxRetries = 3;
        int attempt = 0;

        while (attempt < maxRetries) {
            try {
                // Read accounts
                BankAccount fromAccount = accountRepository.findById(fromAccountId)
                    .orElseThrow(() -> new NotFoundException("Account not found"));
                BankAccount toAccount = accountRepository.findById(toAccountId)
                    .orElseThrow(() -> new NotFoundException("Account not found"));

                // Modify balances
                fromAccount.setBalance(fromAccount.getBalance() - amount);
                toAccount.setBalance(toAccount.getBalance() + amount);

                // Save with optimistic locking
                accountRepository.save(fromAccount);  // Fails if version changed
                accountRepository.save(toAccount);    // Fails if version changed

                return;  // Success!

            } catch (Exception e) {
                if (e.getCause() instanceof ConditionalCheckFailedException) {
                    attempt++;
                    if (attempt >= maxRetries) {
                        throw new ConcurrentModificationException("Failed after " + maxRetries + " retries");
                    }
                    // Wait before retry
                    Thread.sleep(100 * attempt);
                } else {
                    throw e;
                }
            }
        }
    }
}
```

---

## Troubleshooting

### Version not incrementing

**Cause:** Version attribute not properly annotated
**Solution:** Ensure you're using `@DynamoDbVersionAttribute` (not `@DynamoDbAttribute`)

### ConditionalCheckFailedException on every update

**Cause:** Version field is being manually set or modified
**Solution:** Never set the version field in your code - let SDK manage it

### Version starts at wrong value

**Cause:** Default `startAt = 0` not desired
**Solution:** Configure starting value: `@DynamoDbVersionAttribute(startAt = 1)`

### Works in SDK v2 Native but not SDK_V1_COMPATIBLE mode

**Cause:** Both modes use the same optimistic locking mechanism
**Solution:** Verify you're using `@DynamoDbVersionAttribute` (SDK v2 annotation), not `@DynamoDBVersionAttribute` (SDK v1 annotation)

---

## Migration Checklist

For SDK v1 users migrating to SDK_V1_COMPATIBLE mode:

- [ ] Change `@DynamoDBVersionAttribute` to `@DynamoDbVersionAttribute`
- [ ] Update import from `com.amazonaws.services.dynamodbv2.datamodeling.*` to `software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.*`
- [ ] Verify version field is `Long` or `Integer` type
- [ ] Test concurrent modification scenarios
- [ ] Update exception handling to catch SDK v2's exceptions

---

## Summary

| Feature | SDK v1 | SDK v2 (Both Modes) |
|---------|--------|---------------------|
| **Annotation** | `@DynamoDBVersionAttribute` | `@DynamoDbVersionAttribute` |
| **Package** | `com.amazonaws.services.dynamodbv2.datamodeling` | `software.amazon.awssdk.enhanced.dynamodb.extensions.annotations` |
| **Supported Types** | Long, Integer | Long, Integer |
| **Default Start** | 0 | 0 (configurable) |
| **Default Increment** | 1 | 1 (configurable) |
| **Storage Type** | Number (N) | Number (N) |
| **Automatic** | Yes | Yes |
| **Exception** | ConditionalCheckFailedException | ConditionalCheckFailedException |

**Optimistic locking works identically in both SDK_V1_COMPATIBLE and SDK_V2_NATIVE modes!**
