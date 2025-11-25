# Spring Data DynamoDB Migration Guide
## Migrating from AWS SDK v1 to AWS SDK v2 (Version 6.x to 7.0.0)

---

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Step-by-Step Migration](#step-by-step-migration)
4. [Marshalling Modes](#marshalling-modes)
5. [Complete Examples](#complete-examples)
6. [Troubleshooting](#troubleshooting)
7. [FAQ](#faq)

---

## Overview

Spring Data DynamoDB version 7.0.0 migrates from AWS SDK v1 to AWS SDK v2, bringing improved performance, better error handling, and access to the latest AWS DynamoDB features.

### Key Changes

- **AWS SDK v2**: Complete migration from `com.amazonaws` to `software.amazon.awssdk`
- **Enhanced Client**: Uses DynamoDB Enhanced Client for better type safety
- **New Annotations**: Entity annotations updated to AWS SDK v2 standards
- **Automatic Retry**: Built-in exponential backoff for batch operations
- **Marshalling Modes**: Configure via `@EnableDynamoDBRepositories` annotation (not application properties)

### Prerequisites

- **Java 21+** (required)
- **Spring Boot 3.x** or **Spring Framework 6.x**
- Remove **all** AWS SDK v1 dependencies

---

## Step-by-Step Migration

### Step 1: Update Dependencies

#### Maven

**Before (SDK v1):**
```xml
<properties>
    <aws-java-sdk.version>1.12.772</aws-java-sdk.version>
    <spring.data.dynamodb.version>6.0.4</spring.data.dynamodb.version>
</properties>

<dependencies>
    <dependency>
        <groupId>io.github.prasanna0586</groupId>
        <artifactId>spring-data-dynamodb</artifactId>
        <version>${spring.data.dynamodb.version}</version>
    </dependency>
    <dependency>
        <groupId>com.amazonaws</groupId>
        <artifactId>aws-java-sdk-dynamodb</artifactId>
        <version>${aws-java-sdk.version}</version>
    </dependency>
</dependencies>
```

**After (SDK v2):**
```xml
<properties>
    <aws-java-sdk.version>2.38.1</aws-java-sdk.version>
    <spring.data.dynamodb.version>7.0.0</spring.data.dynamodb.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>bom</artifactId>
            <version>${aws-java-sdk.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.github.prasanna0586</groupId>
        <artifactId>spring-data-dynamodb</artifactId>
        <version>${spring.data.dynamodb.version}</version>
    </dependency>
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>dynamodb-enhanced</artifactId>
    </dependency>
</dependencies>
```

#### Gradle

```gradle
dependencies {
    implementation platform('software.amazon.awssdk:bom:2.38.1')
    implementation 'io.github.prasanna0586:spring-data-dynamodb:7.0.0'
    implementation 'software.amazon.awssdk:dynamodb-enhanced'
}
```

⚠️ **Remove all SDK v1 dependencies** - SDK v1 and v2 cannot coexist.

---

### Step 2: Update Configuration

**Configure marshalling mode in `@EnableDynamoDBRepositories` annotation** (not in application.properties).

**Before (SDK v1):**
```java
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;

@Configuration
@EnableDynamoDBRepositories(basePackages = "com.example.repository")
public class DynamoDBConfig {

    @Bean
    public AmazonDynamoDB amazonDynamoDB() {
        return AmazonDynamoDBClientBuilder.standard()
            .withRegion(region)
            .build();
    }
}
```

**After (SDK v2):**
```java
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;

@Configuration
@EnableDynamoDBRepositories(basePackages = "com.example.repository")
public class DynamoDBConfig {

    @Bean
    public DynamoDbClient amazonDynamoDB() {
        return DynamoDbClient.builder()
            .region(Region.of(region))
            .build();
    }
}
```

**Key Points:**
- Only `DynamoDbClient` bean is required - the library creates `DynamoDbEnhancedClient` internally
- For existing SDK v1 data, configure marshalling mode in `@EnableDynamoDBRepositories` annotation
- Use `SDK_V1_COMPATIBLE` for existing data, `SDK_V2_NATIVE` for new projects (default)

---

### Step 3: Update Entity Annotations

| SDK v1 | SDK v2 |
|--------|--------|
| `@DynamoDBTable(tableName="X")` | `@DynamoDbBean` |
| `@DynamoDBHashKey` | `@DynamoDbPartitionKey` |
| `@DynamoDBRangeKey` | `@DynamoDbSortKey` |
| `@DynamoDBAttribute(attributeName="X")` | `@DynamoDbAttribute("X")` |
| `@DynamoDBIndexHashKey(globalSecondaryIndexNames={...})` | `@DynamoDbSecondaryPartitionKey(indexNames={...})` |
| `@DynamoDBIndexRangeKey(globalSecondaryIndexName="X")` | `@DynamoDbSecondarySortKey(indexNames={"X"})` |
| `@DynamoDBTypeConverted(converter=X.class)` | `@DynamoDbConvertedBy(X.class)` |
| `@DynamoDBVersionAttribute` | `@DynamoDbVersionAttribute` (from `extensions.annotations`) |
| `@DynamoDBIgnore` | `@DynamoDbIgnore` |

**Before (SDK v1):**
```java
import com.amazonaws.services.dynamodbv2.datamodeling.*;

@DynamoDBTable(tableName = "Products")
public class Product {

    @DynamoDBHashKey
    private String id;

    @DynamoDBAttribute
    private String name;

    @DynamoDBAttribute
    private Double price;

    @DynamoDBTypeConverted(converter = InstantConverter.class)
    private Instant createdAt;

    // Getters and setters
}
```

**After (SDK v2):**
```java
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@DynamoDbBean
public class Product {

    private String id;
    private String name;
    private Double price;
    private Instant createdAt;

    // Default constructor required
    public Product() {}

    @DynamoDbPartitionKey
    @DynamoDbAttribute("id")
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @DynamoDbAttribute("name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @DynamoDbAttribute("price")
    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    @DynamoDbAttribute("createdAt")
    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
```

**Important:** In SDK v2, annotations must be placed on **getter methods**, not on fields.

---

### Step 4: Update Type Converters

**Before (SDK v1):**
```java
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;

public class InstantConverter implements DynamoDBTypeConverter<String, Instant> {
    @Override
    public String convert(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    @Override
    public Instant unconvert(String value) {
        return value != null ? Instant.parse(value) : null;
    }
}
```

**After (SDK v2):**
```java
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class InstantConverter implements AttributeConverter<Instant> {

    @Override
    public AttributeValue transformFrom(Instant instant) {
        return instant == null ? null :
            AttributeValue.builder().s(instant.toString()).build();
    }

    @Override
    public Instant transformTo(AttributeValue attributeValue) {
        return attributeValue == null || attributeValue.s() == null ? null :
            Instant.parse(attributeValue.s());
    }

    @Override
    public EnhancedType<Instant> type() {
        return EnhancedType.of(Instant.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.S;
    }
}
```

#### Built-in Converters

| Converter Class | Converts | Format |
|----------------|----------|--------|
| `Instant2IsoAttributeConverter` | `Instant` | String (ISO-8601) |
| `Instant2EpocheAttributeConverter` | `Instant` | Number (epoch ms) |
| `Date2IsoAttributeConverter` | `Date` | String (ISO-8601) |
| `Date2EpocheAttributeConverter` | `Date` | Number (epoch ms) |

---

### Step 5: Update Exception Handling

**Before (SDK v1):**
```java
try {
    productRepository.saveAll(products);
} catch (Exception e) {
    logger.error("Batch write failed: {}", e.getMessage());
}
```

**After (SDK v2):**
```java
import org.socialsignin.spring.data.dynamodb.exception.BatchWriteException;

try {
    productRepository.saveAll(products);
    // Automatically retried up to 8 times with exponential backoff
} catch (BatchWriteException e) {
    // Type-safe access to failed entities
    List<Product> failedProducts = e.getUnprocessedEntities(Product.class);

    logger.error("Failed to save {} out of {} products after {} retries",
        failedProducts.size(),
        products.size(),
        e.getRetriesAttempted());

    // Handle failed items (DLQ, retry, etc.)
}
```

---

## Marshalling Modes

**Configure marshalling mode in `@EnableDynamoDBRepositories` annotation.**

### SDK_V1_COMPATIBLE Mode

Use when migrating from SDK v1 with existing data.

```java
@Configuration
@EnableDynamoDBRepositories(
    basePackages = "com.example.repository",
    marshallingMode = MarshallingMode.SDK_V1_COMPATIBLE
)
public class DynamoDBConfig {
    // ...
}
```

**Type Mapping (SDK_V1_COMPATIBLE):**
- `Boolean` → Number (0/1) - Same as SDK v1
- `Date` → String (ISO-8601)
- `Instant` → String (ISO-8601 milliseconds)

**Use when:**
- Migrating from SDK v1 version of this library
- Have existing data in DynamoDB
- Need zero-downtime migration

**Example Entity:**
```java
@DynamoDbBean
public class User {
    private String userId;
    private Boolean active;
    private Date createdDate;
    private Instant lastLogin;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("userId")
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    // active stored as Number 0/1
    @DynamoDbAttribute("active")
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    // createdDate stored as ISO String
    @DynamoDbAttribute("createdDate")
    public Date getCreatedDate() { return createdDate; }
    public void setCreatedDate(Date createdDate) { this.createdDate = createdDate; }

    // lastLogin stored as ISO String
    @DynamoDbAttribute("lastLogin")
    public Instant getLastLogin() { return lastLogin; }
    public void setLastLogin(Instant lastLogin) { this.lastLogin = lastLogin; }
}
```

### SDK_V2_NATIVE Mode

Use for new projects or after data migration.

```java
@Configuration
@EnableDynamoDBRepositories(
    basePackages = "com.example.repository",
    marshallingMode = MarshallingMode.SDK_V2_NATIVE  // or omit (default)
)
public class DynamoDBConfig {
    // ...
}
```

**Type Mapping (SDK_V2_NATIVE):**
- `Boolean` → BOOL - Native DynamoDB boolean type
- `Date` → Number (epoch milliseconds)
- `Instant` → String (ISO-8601 nanoseconds)

**Use when:**
- Starting a new project
- After migrating existing data
- Want native DynamoDB types

**Example Entity:**
```java
@DynamoDbBean
public class User {
    private String userId;
    private Boolean active;
    private Date createdDate;
    private Instant lastLogin;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("userId")
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    // active stored as BOOL
    @DynamoDbAttribute("active")
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    // createdDate stored as Number (epoch)
    @DynamoDbAttribute("createdDate")
    public Date getCreatedDate() { return createdDate; }
    public void setCreatedDate(Date createdDate) { this.createdDate = createdDate; }

    // lastLogin stored as ISO String (nanosecond precision)
    @DynamoDbAttribute("lastLogin")
    public Instant getLastLogin() { return lastLogin; }
    public void setLastLogin(Instant lastLogin) { this.lastLogin = lastLogin; }
}
```

### Choosing the Right Mode

| Scenario | Mode | Why |
|----------|------|-----|
| Migrating from SDK v1 | `SDK_V1_COMPATIBLE` | No data migration needed |
| New project | `SDK_V2_NATIVE` | Better types, smaller storage |
| Testing migration | `SDK_V1_COMPATIBLE` → `SDK_V2_NATIVE` | Gradual transition |

---

## Complete Examples

### SDK_V1_COMPATIBLE Mode Example (Existing Data)

**Configuration:**
```java
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.socialsignin.spring.data.dynamodb.core.MarshallingMode;

@Configuration
@EnableDynamoDBRepositories(
    basePackages = "com.example.repository",
    marshallingMode = MarshallingMode.SDK_V1_COMPATIBLE
)
public class DynamoDBConfig {

    @Bean
    public DynamoDbClient amazonDynamoDB() {
        return DynamoDbClient.builder()
            .region(Region.US_EAST_1)
            .build();
    }
}
```

**Entity:**
```java
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@DynamoDbBean
public class Order {

    private String customerId;
    private String orderDate;
    private Double amount;
    private Boolean isActive;
    private Instant createdAt;

    public Order() {}

    @DynamoDbPartitionKey
    @DynamoDbAttribute("customerId")
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    @DynamoDbSortKey
    @DynamoDbAttribute("orderDate")
    public String getOrderDate() { return orderDate; }
    public void setOrderDate(String orderDate) { this.orderDate = orderDate; }

    @DynamoDbAttribute("amount")
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    // Will be stored as Number 0/1 in SDK_V1_COMPATIBLE mode
    @DynamoDbAttribute("isActive")
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    // Will be stored as ISO String
    @DynamoDbAttribute("createdAt")
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

**Repository:**
```java
public interface OrderRepository extends
    DynamoDBPagingAndSortingRepository<Order, String> {

    List<Order> findByCustomerIdAndOrderDate(String customerId, String orderDate);
}
```

### SDK_V2_NATIVE Mode Example (New Project)

**Configuration:**
```java
@Configuration
@EnableDynamoDBRepositories(basePackages = "com.example.repository")  // SDK_V2_NATIVE is default
public class DynamoDBConfig {

    @Bean
    public DynamoDbClient amazonDynamoDB() {
        return DynamoDbClient.builder()
            .region(Region.US_EAST_1)
            .build();
    }
}
```

**Entity:**
```java
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@DynamoDbBean
public class Order {

    private String customerId;
    private String orderDate;
    private Double amount;
    private Boolean isActive;
    private Instant createdAt;

    public Order() {}

    @DynamoDbPartitionKey
    @DynamoDbAttribute("customerId")
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    @DynamoDbSortKey
    @DynamoDbAttribute("orderDate")
    public String getOrderDate() { return orderDate; }
    public void setOrderDate(String orderDate) { this.orderDate = orderDate; }

    @DynamoDbAttribute("amount")
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }

    // Will be stored as BOOL (native) in SDK_V2_NATIVE mode
    @DynamoDbAttribute("isActive")
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    // Will be stored as String (ISO-8601 nanosecond)
    @DynamoDbAttribute("createdAt")
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
```

**Repository:**
```java
public interface OrderRepository extends
    DynamoDBPagingAndSortingRepository<Order, String> {

    List<Order> findByCustomerIdAndOrderDate(String customerId, String orderDate);
}
```

---

## Troubleshooting

### Issue: Type Conversion Errors

**Error:**
```
AttributeConverterException: Cannot convert attribute value to type Instant
```

**Solution:**
Use the correct marshalling mode for your data:
```java
@EnableDynamoDBRepositories(
    marshallingMode = MarshallingMode.SDK_V1_COMPATIBLE  // For existing SDK v1 data
)
```

### Issue: Boolean Fields Not Working

**Error:**
```
ValidationException: Invalid comparison operator for Boolean
```

**Solution:**
Your data has Boolean stored as Number (0/1). Use SDK_V1_COMPATIBLE mode:
```java
@EnableDynamoDBRepositories(
    marshallingMode = MarshallingMode.SDK_V1_COMPATIBLE
)
```

### Issue: Missing DynamoDbClient Bean

**Error:**
```
NoSuchBeanDefinitionException: No qualifying bean of type 'DynamoDbClient'
```

**Solution:**
Add the required bean:
```java
@Bean
public DynamoDbClient amazonDynamoDB() {
    return DynamoDbClient.builder()
        .region(Region.US_EAST_1)
        .build();
}
```

**Note:** You do NOT need to define a `DynamoDbEnhancedClient` bean - the library creates it internally from the `DynamoDbClient`.

---

## FAQ

### Q1: Can I configure marshalling mode in application.properties?

**A:** No. Marshalling mode must be configured in the `@EnableDynamoDBRepositories` annotation:

```java
@EnableDynamoDBRepositories(
    basePackages = "com.example.repository",
    marshallingMode = MarshallingMode.SDK_V1_COMPATIBLE
)
```

### Q2: Which version should I use for testing?

**A:** Start with version **7.0.0** or later. This is the first stable release with SDK v2 support.

```xml
<dependency>
    <groupId>io.github.prasanna0586</groupId>
    <artifactId>spring-data-dynamodb</artifactId>
    <version>7.0.0</version>
</dependency>
```

### Q3: Do I need to migrate my data?

**A:**
- **SDK_V1_COMPATIBLE**: No data migration needed
- **SDK_V2_NATIVE**: Data migration needed for Boolean and Date types if migrating from SDK v1

### Q4: Will my existing queries still work?

**A:** Yes, if you use `SDK_V1_COMPATIBLE` mode. Repository interfaces remain unchanged.

### Q5: What's the performance impact?

**A:** SDK v2 generally provides better performance:
- Improved connection pooling
- Better HTTP/2 support
- Non-blocking I/O
- Reduced memory footprint

### Q6: Can SDK v1 and v2 coexist?

**A:** No. You must completely remove all SDK v1 dependencies before migrating.

### Q7: How do I test the migration?

**Best practices:**
1. Use DynamoDB Local for initial testing
2. Start with `SDK_V1_COMPATIBLE` mode
3. Test all CRUD operations
4. Test batch operations
5. Test custom query methods
6. Deploy to staging before production

---

## Example Project

A complete working example demonstrating the SDK v1 to v2 migration is available:

**Repository:** [validate-spring-data-dynamodb](https://github.com/prasanna0586/validate-spring-data-dynamodb/tree/spring-data-dynamodb-7.0.0-sdk-v2-migration)

**Migration Commit:** [b804494](https://github.com/prasanna0586/validate-spring-data-dynamodb/commit/b804494bfe0cb8a325d43bf5aa4ad6b7ecbef8ef) - Shows the exact changes made to migrate from 6.0.4 to 7.0.0

### What the Migration Commit Changed

#### Dependencies
```diff
- com.amazonaws:aws-java-sdk-dynamodb:1.12.772
+ software.amazon.awssdk:dynamodb-enhanced:2.38.1
+ spring-data-dynamodb:7.0.0
```

#### Entity Annotations
```diff
- @DynamoDBTable(tableName = "DocumentMetadata")
+ @DynamoDbBean

- @DynamoDBHashKey
+ @DynamoDbPartitionKey

- @DynamoDBIndexHashKey(globalSecondaryIndexNames = {...})
+ @DynamoDbSecondaryPartitionKey(indexNames = {...})

- @DynamoDBIndexRangeKey(globalSecondaryIndexName = "...")
+ @DynamoDbSecondarySortKey(indexNames = {"..."})

- @DynamoDBVersionAttribute
+ @DynamoDbVersionAttribute  // from extensions.annotations
```

#### Type Converter
```diff
- public class InstantConverter implements DynamoDBTypeConverter<String, Instant> {
-     public String convert(Instant instant) { ... }
-     public Instant unconvert(String value) { ... }
- }
+ public class InstantConverter implements AttributeConverter<Instant> {
+     public AttributeValue transformFrom(Instant instant) { ... }
+     public Instant transformTo(AttributeValue attributeValue) { ... }
+     public EnhancedType<Instant> type() { ... }
+     public AttributeValueType attributeValueType() { ... }
+ }
```

#### Configuration
```diff
- @Bean
- public AmazonDynamoDB amazonDynamoDB() {
-     return AmazonDynamoDBClientBuilder.standard()
-         .withEndpointConfiguration(...)
-         .build();
- }
+ @Bean
+ public DynamoDbClient amazonDynamoDB() {
+     return DynamoDbClient.builder()
+         .endpointOverride(URI.create(endpoint))
+         .region(Region.of(region))
+         .build();
+ }
```

#### Custom Repository Queries
```diff
- DynamoDBQueryExpression<T> query = new DynamoDBQueryExpression<>()
-     .withIndexName("index-name")
-     .withHashKeyValues(gsiKey);
+ QueryRequest queryRequest = QueryRequest.builder()
+     .tableName(tableName)
+     .indexName("index-name")
+     .keyConditionExpression("memberId = :memberId")
+     .expressionAttributeValues(expressionValues)
+     .build();
```

#### AttributeValue Construction
```diff
- new AttributeValue().withS(value)
- new AttributeValue().withN(value)
+ AttributeValue.builder().s(value).build()
+ AttributeValue.builder().n(value).build()
```

### Project Features Demonstrated
- Complete SDK v2 entity with multiple GSI annotations
- Optimistic locking with `@DynamoDbVersionAttribute`
- Custom `AttributeConverter` for `Instant` types
- Custom repository implementation using `DynamoDBOperations`
- `@Query` annotation with filter expressions
- `TableNameResolver` for environment-based table naming
- Integration tests with Testcontainers (75 tests)

---

## Additional Resources

- [AWS SDK for Java 2.x Developer Guide](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/)
- [DynamoDB Enhanced Client Guide](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/dynamodb-enhanced-client.html)
- [Spring Data DynamoDB GitHub](https://github.com/prasanna0586/spring-data-dynamodb)
- [GitHub Issues](https://github.com/prasanna0586/spring-data-dynamodb/issues)

---

**Need Help?** Open an issue on GitHub with:
- Spring Data DynamoDB version (7.0.0+)
- AWS SDK version
- Spring Boot version
- Complete stack trace
- Minimal reproducible example
