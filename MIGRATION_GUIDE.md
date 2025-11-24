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
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.socialsignin.spring.data.dynamodb.core.MarshallingMode;

@Configuration
@EnableDynamoDBRepositories(
    basePackages = "com.example.repository",
    marshallingMode = MarshallingMode.SDK_V1_COMPATIBLE  // For existing data
)
public class DynamoDBConfig {

    @Bean
    public DynamoDbClient amazonDynamoDB() {
        return DynamoDbClient.builder()
            .region(Region.of(region))
            .build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient amazonDynamoDB) {
        return DynamoDbEnhancedClient.builder()
            .dynamoDbClient(amazonDynamoDB)
            .build();
    }
}
```

**Key Points:**
- Marshalling mode is configured in `@EnableDynamoDBRepositories` annotation
- `DynamoDbEnhancedClient` bean is now **required**
- Use `SDK_V1_COMPATIBLE` for existing data, `SDK_V2_NATIVE` for new projects

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
| `@DynamoDBVersionAttribute` | `@DynamoDbVersionAttribute` |
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
import org.socialsignin.spring.data.dynamodb.marshaller.Instant2IsoAttributeConverter;

@DynamoDbBean
public class Product {

    @DynamoDbPartitionKey
    private String id;

    @DynamoDbAttribute
    private String name;

    @DynamoDbAttribute
    private Double price;

    @DynamoDbConvertedBy(Instant2IsoAttributeConverter.class)
    private Instant createdAt;

    // Getters and setters
}
```

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
    @DynamoDbPartitionKey
    private String userId;

    private Boolean active;  // Stored as Number 0/1
    private Date createdDate;  // Stored as ISO String
    private Instant lastLogin;  // Stored as ISO String

    // Getters and setters
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
    @DynamoDbPartitionKey
    private String userId;

    private Boolean active;  // Stored as BOOL
    private Date createdDate;  // Stored as Number (epoch)
    private Instant lastLogin;  // Stored as ISO String (nanosecond precision)

    // Getters and setters
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
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
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

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient amazonDynamoDB) {
        return DynamoDbEnhancedClient.builder()
            .dynamoDbClient(amazonDynamoDB)
            .build();
    }
}
```

**Entity:**
```java
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import org.socialsignin.spring.data.dynamodb.marshaller.Instant2IsoAttributeConverter;

@DynamoDbBean
public class Order {

    @DynamoDbPartitionKey
    @DynamoDbAttribute("customerId")
    private String customerId;

    @DynamoDbSortKey
    @DynamoDbAttribute("orderDate")
    private String orderDate;

    @DynamoDbAttribute("amount")
    private Double amount;

    @DynamoDbAttribute("isActive")
    private Boolean isActive;  // Will be stored as Number 0/1

    @DynamoDbAttribute("createdAt")
    @DynamoDbConvertedBy(Instant2IsoAttributeConverter.class)
    private Instant createdAt;  // Will be stored as ISO String

    // Getters and setters
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
@EnableDynamoDBRepositories(
    basePackages = "com.example.repository",
    marshallingMode = MarshallingMode.SDK_V2_NATIVE  // or omit (default)
)
public class DynamoDBConfig {

    @Bean
    public DynamoDbClient amazonDynamoDB() {
        return DynamoDbClient.builder()
            .region(Region.US_EAST_1)
            .build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient amazonDynamoDB) {
        return DynamoDbEnhancedClient.builder()
            .dynamoDbClient(amazonDynamoDB)
            .build();
    }
}
```

**Entity:**
```java
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@DynamoDbBean
public class Order {

    @DynamoDbPartitionKey
    @DynamoDbAttribute("customerId")
    private String customerId;

    @DynamoDbSortKey
    @DynamoDbAttribute("orderDate")
    private String orderDate;

    @DynamoDbAttribute("amount")
    private Double amount;

    @DynamoDbAttribute("isActive")
    private Boolean isActive;  // Will be stored as BOOL (native)

    @DynamoDbAttribute("createdAt")
    private Instant createdAt;  // Will be stored as String (ISO-8601 nanosecond)

    // Getters and setters
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

### Issue: Missing DynamoDbEnhancedClient Bean

**Error:**
```
NoSuchBeanDefinitionException: No qualifying bean of type 'DynamoDbEnhancedClient'
```

**Solution:**
Add the required bean:
```java
@Bean
public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient amazonDynamoDB) {
    return DynamoDbEnhancedClient.builder()
        .dynamoDbClient(amazonDynamoDB)
        .build();
}
```

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
