# Spring Data DynamoDB Migration Guide
## Migrating from AWS SDK v1 to AWS SDK v2 (Version 6.x to 7.x)

---

## Table of Contents

1. [Overview](#overview)
2. [What's New in Version 7.0](#whats-new-in-version-70)
3. [Breaking Changes Summary](#breaking-changes-summary)
4. [Step-by-Step Migration Guide](#step-by-step-migration-guide)
   - [Step 1: Update Dependencies](#step-1-update-dependencies)
   - [Step 2: Update Configuration](#step-2-update-configuration)
   - [Step 3: Update Entity Annotations](#step-3-update-entity-annotations)
   - [Step 4: Update Type Converters](#step-4-update-type-converters)
   - [Step 5: Update Exception Handling](#step-5-update-exception-handling)
   - [Step 6: Update Auto-Generated Keys](#step-6-update-auto-generated-keys-if-used)
5. [Type Marshalling Modes](#type-marshalling-modes)
6. [Batch Operations Enhancements](#batch-operations-enhancements)
7. [Complete Migration Example](#complete-migration-example)
8. [Testing with SNAPSHOT Versions](#testing-with-snapshot-versions)
9. [Migration Checklist](#migration-checklist)
10. [Troubleshooting](#troubleshooting)
11. [FAQ](#faq)

---

## Overview

Spring Data DynamoDB version 7.0 represents a **major upgrade** from AWS SDK v1 to AWS SDK v2. This migration brings improved performance, better error handling, and access to the latest AWS DynamoDB features. However, it introduces breaking changes that require updates to your application code.

### Key Changes

- **AWS SDK v2**: Complete migration from `com.amazonaws` to `software.amazon.awssdk`
- **Enhanced Client**: Uses DynamoDB Enhanced Client for better type safety
- **New Annotations**: Entity annotations updated to AWS SDK v2 standards
- **Automatic Retry**: Built-in exponential backoff for batch operations
- **Type Marshalling**: Configurable modes for backward compatibility

### Who Should Migrate?

- Applications currently using Spring Data DynamoDB 6.x or earlier
- Applications looking to leverage AWS SDK v2 features
- Applications requiring improved batch operation error handling
- New projects should start directly with version 7.x

### Prerequisites

- **Java 21+** (required for version 7.x)
- **Spring Boot 3.x** or **Spring Framework 6.x**
- Understanding of your current DynamoDB schema
- Access to test environment for validation

---

## What's New in Version 7.0

### 🚀 AWS SDK v2 Integration

Complete migration to AWS SDK v2 brings:
- Better performance and reduced latency
- Improved connection pooling
- Non-blocking I/O support
- Better HTTP/2 support

### 🔄 Automatic Batch Retry

Batch operations now include automatic exponential backoff retry:
- Up to 8 retries by default (configurable)
- Exponential backoff: 100ms → 200ms → 400ms → 800ms...
- Jitter to prevent thundering herd
- Detailed metrics on retry attempts

### 🎯 Enhanced Error Handling

Improved exception APIs provide:
- Type-safe access to failed entities
- Detailed retry information
- Better exception messages
- Original exception preservation

### 🔧 Configurable Type Marshalling

Choose between:
- **SDK_V2_NATIVE**: Modern SDK v2 type system
- **SDK_V1_COMPATIBLE**: Backward compatible with SDK v1 data

### 📊 Better Type Safety

- Stronger type checking with Enhanced Client
- Improved generic type resolution
- Better IDE support and autocomplete

---

## Breaking Changes Summary

| Category | Change | Impact |
|----------|--------|--------|
| **Dependencies** | `com.amazonaws` → `software.amazon.awssdk` | HIGH - All SDK imports must change |
| **Client** | `AmazonDynamoDB` → `DynamoDbClient` | HIGH - Configuration must be updated |
| **Annotations** | `@DynamoDBTable` → `@DynamoDbBean` | HIGH - All entities must be updated |
| **Annotations** | `@DynamoDBHashKey` → `@DynamoDbPartitionKey` | HIGH - All entities must be updated |
| **Annotations** | `@DynamoDBRangeKey` → `@DynamoDbSortKey` | MEDIUM - If using composite keys |
| **Annotations** | `@DynamoDBAttribute(attributeName=)` → `@DynamoDbAttribute("")` | MEDIUM - Parameter style changed |
| **Converters** | `DynamoDBTypeConverter` → `AttributeConverter` | MEDIUM - All custom converters |
| **Exceptions** | `BatchWriteException` API changed | LOW - If using batch operations |
| **Auto-Gen Keys** | New annotation and configuration | LOW - If using auto-generated keys |

---

## Step-by-Step Migration Guide

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

**Before (SDK v1):**
```gradle
dependencies {
    implementation 'io.github.prasanna0586:spring-data-dynamodb:6.0.4'
    implementation 'com.amazonaws:aws-java-sdk-dynamodb:1.12.772'
}
```

**After (SDK v2):**
```gradle
dependencies {
    implementation platform('software.amazon.awssdk:bom:2.38.1')
    implementation 'io.github.prasanna0586:spring-data-dynamodb:7.0.0'
    implementation 'software.amazon.awssdk:dynamodb-enhanced'
}
```

#### Important Notes

⚠️ **Remove all SDK v1 dependencies** - AWS SDK v1 and v2 cannot coexist in the same application due to class conflicts.

✅ **Use BOM for version management** - The AWS SDK v2 BOM ensures all AWS dependencies use compatible versions.

---

### Step 2: Update Configuration

#### DynamoDB Client Configuration

**Before (SDK v1):**
```java
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;

@Configuration
@EnableDynamoDBRepositories(basePackages = "com.example.repository")
public class DynamoDBConfig {

    @Value("${aws.dynamodb.endpoint}")
    private String endpoint;

    @Value("${aws.region}")
    private String region;

    @Value("${aws.accessKey}")
    private String accessKey;

    @Value("${aws.secretKey}")
    private String secretKey;

    @Bean
    public AmazonDynamoDB amazonDynamoDB() {
        return AmazonDynamoDBClientBuilder.standard()
            .withEndpointConfiguration(
                new AwsClientBuilder.EndpointConfiguration(endpoint, region))
            .withCredentials(new AWSStaticCredentialsProvider(
                new BasicAWSCredentials(accessKey, secretKey)))
            .build();
    }
}
```

**After (SDK v2):**
```java
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.socialsignin.spring.data.dynamodb.core.MarshallingMode;

import java.net.URI;

@Configuration
@EnableDynamoDBRepositories(
    basePackages = "com.example.repository",
    marshallingMode = MarshallingMode.SDK_V1_COMPATIBLE  // For existing data
)
public class DynamoDBConfig {

    @Value("${aws.dynamodb.endpoint}")
    private String endpoint;

    @Value("${aws.region}")
    private String region;

    @Value("${aws.accessKey}")
    private String accessKey;

    @Value("${aws.secretKey}")
    private String secretKey;

    @Bean
    public DynamoDbClient amazonDynamoDB() {
        return DynamoDbClient.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)))
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

#### Using Default Credentials Provider (Recommended)

For production, use AWS's default credential chain instead of hardcoded credentials:

```java
@Bean
public DynamoDbClient amazonDynamoDB() {
    return DynamoDbClient.builder()
        .region(Region.of(region))
        // Uses default credentials chain: environment variables,
        // system properties, IAM role, etc.
        .build();
}
```

#### Configuration for Local DynamoDB

```java
@Bean
@Profile("local")
public DynamoDbClient amazonDynamoDB() {
    return DynamoDbClient.builder()
        .endpointOverride(URI.create("http://localhost:8000"))
        .region(Region.US_EAST_1)
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create("dummy", "dummy")))
        .build();
}
```

#### Key Changes

| SDK v1 | SDK v2 | Notes |
|--------|--------|-------|
| `AmazonDynamoDB` | `DynamoDbClient` | Client interface renamed |
| N/A | `DynamoDbEnhancedClient` | **New required bean** |
| `withEndpointConfiguration()` | `endpointOverride(URI.create())` | Takes URI instead of object |
| `withRegion(String)` | `region(Region.of())` | Uses Region enum |
| `BasicAWSCredentials` | `AwsBasicCredentials` | Package changed |
| `AWSStaticCredentialsProvider` | `StaticCredentialsProvider` | Name shortened |

---

### Step 3: Update Entity Annotations

#### Simple Entity Example

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

    @DynamoDBVersionAttribute
    private Long version;

    @DynamoDBIgnore
    private String calculatedField;

    // Getters and setters
}
```

**After (SDK v2):**
```java
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@DynamoDbBean
public class Product {

    @DynamoDbPartitionKey
    private String id;

    @DynamoDbAttribute
    private String name;

    @DynamoDbAttribute
    private Double price;

    @DynamoDbVersionAttribute
    private Long version;

    @DynamoDbIgnore
    private String calculatedField;

    // Getters and setters
}
```

#### Entity with Composite Key

**Before (SDK v1):**
```java
@DynamoDBTable(tableName = "Orders")
public class Order {

    @DynamoDBHashKey(attributeName = "customerId")
    private String customerId;

    @DynamoDBRangeKey(attributeName = "orderDate")
    private String orderDate;

    @DynamoDBAttribute(attributeName = "totalAmount")
    private Double totalAmount;

    // Getters and setters
}
```

**After (SDK v2):**
```java
@DynamoDbBean
public class Order {

    @DynamoDbPartitionKey
    @DynamoDbAttribute("customerId")
    private String customerId;

    @DynamoDbSortKey
    @DynamoDbAttribute("orderDate")
    private String orderDate;

    @DynamoDbAttribute("totalAmount")
    private Double totalAmount;

    // Getters and setters
}
```

#### Entity with Global Secondary Index

**Before (SDK v1):**
```java
@DynamoDBTable(tableName = "DocumentMetadata")
public class DocumentMetadata {

    @DynamoDBHashKey(attributeName = "documentId")
    private String documentId;

    @DynamoDBIndexHashKey(
        globalSecondaryIndexNames = {"memberId-createdAt-index"},
        attributeName = "memberId"
    )
    private Integer memberId;

    @DynamoDBIndexRangeKey(
        globalSecondaryIndexName = "memberId-createdAt-index",
        attributeName = "createdAt"
    )
    @DynamoDBTypeConverted(converter = InstantConverter.class)
    private Instant createdAt;

    @DynamoDBAttribute
    private String title;

    // Getters and setters
}
```

**After (SDK v2):**
```java
import org.socialsignin.spring.data.dynamodb.marshaller.Instant2IsoAttributeConverter;

@DynamoDbBean
public class DocumentMetadata {

    @DynamoDbPartitionKey
    @DynamoDbAttribute("documentId")
    private String documentId;

    @DynamoDbSecondaryPartitionKey(indexNames = {"memberId-createdAt-index"})
    @DynamoDbAttribute("memberId")
    private Integer memberId;

    @DynamoDbSecondarySortKey(indexNames = {"memberId-createdAt-index"})
    @DynamoDbAttribute("createdAt")
    @DynamoDbConvertedBy(Instant2IsoAttributeConverter.class)
    private Instant createdAt;

    @DynamoDbAttribute("title")
    private String title;

    // Getters and setters
}
```

#### Annotation Mapping Reference

| SDK v1 Annotation | SDK v2 Annotation | Notes |
|-------------------|-------------------|-------|
| `@DynamoDBTable(tableName="X")` | `@DynamoDbBean` | Table name inferred from class name |
| `@DynamoDBHashKey` | `@DynamoDbPartitionKey` | Terminology changed |
| `@DynamoDBRangeKey` | `@DynamoDbSortKey` | Terminology changed |
| `@DynamoDBIndexHashKey(globalSecondaryIndexNames={...})` | `@DynamoDbSecondaryPartitionKey(indexNames={...})` | Parameter renamed |
| `@DynamoDBIndexRangeKey(globalSecondaryIndexName="X")` | `@DynamoDbSecondarySortKey(indexNames={"X"})` | Now accepts array |
| `@DynamoDBAttribute(attributeName="X")` | `@DynamoDbAttribute("X")` | Uses value parameter |
| `@DynamoDBTypeConverted(converter=X.class)` | `@DynamoDbConvertedBy(X.class)` | Name shortened |
| `@DynamoDBVersionAttribute` | `@DynamoDbVersionAttribute` | No change |
| `@DynamoDBIgnore` | `@DynamoDbIgnore` | Minor change |

#### Import Changes

Update all imports from:
```java
import com.amazonaws.services.dynamodbv2.datamodeling.*;
```

To:
```java
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
```

---

### Step 4: Update Type Converters

#### Basic Type Converter

**Before (SDK v1):**
```java
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;
import java.time.Instant;

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
import java.time.Instant;

public class Instant2IsoAttributeConverter implements AttributeConverter<Instant> {

    @Override
    public AttributeValue transformFrom(Instant instant) {
        return instant == null ? null :
            AttributeValue.builder().s(instant.toString()).build();
    }

    @Override
    public Instant transformTo(AttributeValue attributeValue) {
        if (attributeValue == null || attributeValue.s() == null) {
            return null;
        }
        return Instant.parse(attributeValue.s());
    }

    @Override
    public EnhancedType<Instant> type() {
        return EnhancedType.of(Instant.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.S;  // String type
    }
}
```

#### Complex Type Converter (JSON)

**Before (SDK v1):**
```java
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AddressConverter implements DynamoDBTypeConverter<String, Address> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String convert(Address address) {
        try {
            return address != null ? mapper.writeValueAsString(address) : null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert Address to String", e);
        }
    }

    @Override
    public Address unconvert(String value) {
        try {
            return value != null ? mapper.readValue(value, Address.class) : null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert String to Address", e);
        }
    }
}
```

**After (SDK v2):**
```java
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AddressAttributeConverter implements AttributeConverter<Address> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public AttributeValue transformFrom(Address address) {
        if (address == null) {
            return null;
        }
        try {
            String json = mapper.writeValueAsString(address);
            return AttributeValue.builder().s(json).build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert Address to AttributeValue", e);
        }
    }

    @Override
    public Address transformTo(AttributeValue attributeValue) {
        if (attributeValue == null || attributeValue.s() == null) {
            return null;
        }
        try {
            return mapper.readValue(attributeValue.s(), Address.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert AttributeValue to Address", e);
        }
    }

    @Override
    public EnhancedType<Address> type() {
        return EnhancedType.of(Address.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.S;
    }
}
```

#### Built-in Converters Provided

The library now provides several built-in converters:

| Converter Class | Converts | Storage Format |
|----------------|----------|----------------|
| `Instant2IsoAttributeConverter` | `Instant` | String (ISO-8601) |
| `Instant2EpocheAttributeConverter` | `Instant` | Number (epoch millis) |
| `Date2IsoAttributeConverter` | `Date` | String (ISO-8601) |
| `Date2EpocheAttributeConverter` | `Date` | Number (epoch millis) |

Usage:
```java
@DynamoDbAttribute("createdAt")
@DynamoDbConvertedBy(Instant2IsoAttributeConverter.class)
private Instant createdAt;
```

#### Key Changes

- Two interfaces (`DynamoDBTypeConverter` and `DynamoDBMarshaller`) unified into `AttributeConverter`
- Must implement 4 methods instead of 2
- Works with SDK v2's `AttributeValue` objects
- Must specify `EnhancedType` and `AttributeValueType`

---

### Step 5: Update Exception Handling

#### Batch Write Exception Handling

**Before (SDK v1):**
```java
import org.springframework.dao.DataAccessException;

public void saveProducts(List<Product> products) {
    try {
        productRepository.saveAll(products);
    } catch (DataAccessException e) {
        // Limited error information
        logger.error("Batch write failed: {}", e.getMessage());

        // No access to which items failed
        // Must manually retry entire batch
    }
}
```

**After (SDK v2):**
```java
import org.socialsignin.spring.data.dynamodb.exception.BatchWriteException;

public void saveProducts(List<Product> products) {
    try {
        productRepository.saveAll(products);
        // Automatically retried up to 8 times with exponential backoff
    } catch (BatchWriteException e) {
        // Get type-safe access to failed entities
        List<Product> failedProducts = e.getUnprocessedEntities(Product.class);

        // Log detailed information
        logger.error("Failed to save {} out of {} products after {} retry attempts",
            failedProducts.size(),
            products.size(),
            e.getRetriesAttempted());

        // Send failed items to DLQ for later processing
        dlqService.sendToQueue("product-writes-dlq", failedProducts);

        // Check if there was a specific exception
        if (e.hasOriginalException()) {
            Throwable cause = e.getCause();
            if (cause instanceof ProvisionedThroughputExceededException) {
                metricsService.recordThrottling("product_writes");
            }
        }
    }
}
```

#### Batch Delete Exception Handling

**After (SDK v2):**
```java
import org.socialsignin.spring.data.dynamodb.exception.BatchDeleteException;

public void deleteProducts(List<String> productIds) {
    try {
        productRepository.deleteAllById(productIds);
    } catch (BatchDeleteException e) {
        // Get IDs that failed to delete
        List<String> failedIds = e.getUnprocessedEntities(String.class);

        logger.error("Failed to delete {} product IDs after {} retries",
            failedIds.size(),
            e.getRetriesAttempted());

        // Handle failed deletes
        retryLaterService.scheduleRetry("product-deletes", failedIds);
    }
}
```

#### New Exception API Methods

Both `BatchWriteException` and `BatchDeleteException` provide:

```java
// Get unprocessed entities with type safety
public <T> List<T> getUnprocessedEntities(Class<T> entityClass)

// Get all unprocessed entities
public List<Object> getUnprocessedEntities()

// Get count of unprocessed items
public int getUnprocessedCount()

// Get number of retry attempts made
public int getRetriesAttempted()

// Check if there was a specific underlying exception
public boolean hasOriginalException()
```

---

### Step 6: Update Auto-Generated Keys (If Used)

#### SDK v2 Native Approach

**Configuration:**
```java
import software.amazon.awssdk.enhanced.dynamodb.extensions.AutoGeneratedUuidExtension;

@Bean
public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
    return DynamoDbEnhancedClient.builder()
        .dynamoDbClient(dynamoDbClient)
        .extensions(AutoGeneratedUuidExtension.create())  // Required
        .build();
}
```

**Entity:**
```java
import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbAutoGeneratedUuid;

@DynamoDbBean
public class Product {

    @DynamoDbPartitionKey
    @DynamoDbAutoGeneratedUuid  // SDK v2 annotation
    private String productId;

    // Other fields...
}
```

#### Spring Data DynamoDB Approach (Backward Compatible)

**Entity:**
```java
import org.socialsignin.spring.data.dynamodb.annotation.DynamoDBAutoGeneratedKey;
import org.socialsignin.spring.data.dynamodb.annotation.DynamoDBAutoGeneratedTimestamp;
import org.socialsignin.spring.data.dynamodb.annotation.DynamoDBAutoGenerateStrategy;

@DynamoDbBean
public class Product {

    @DynamoDbPartitionKey
    @DynamoDBAutoGeneratedKey  // Custom annotation
    private String productId;

    @DynamoDbAttribute("createdAt")
    @DynamoDBAutoGeneratedTimestamp(strategy = DynamoDBAutoGenerateStrategy.CREATE)
    private Date createdAt;

    @DynamoDbAttribute("updatedAt")
    @DynamoDBAutoGeneratedTimestamp(strategy = DynamoDBAutoGenerateStrategy.ALWAYS)
    private Date updatedAt;

    // Other fields...
}
```

**Key Differences:**
- SDK v2 native: Only supports UUID generation, requires extension configuration
- Spring Data DynamoDB: Supports UUID and timestamp generation, no extra configuration

---

## Type Marshalling Modes

### Overview

Version 7.0 introduces **configurable type marshalling** to handle differences between SDK v1 and SDK v2 type systems.

### Marshalling Modes

```java
public enum MarshallingMode {
    SDK_V2_NATIVE,      // Default - uses SDK v2 native types
    SDK_V1_COMPATIBLE   // Backward compatible with SDK v1 data
}
```

### Type Differences

| Java Type | SDK v1 Storage | SDK v2 Native | SDK_V1_COMPATIBLE | SDK_V2_NATIVE |
|-----------|----------------|---------------|-------------------|---------------|
| `Boolean` | Number (0/1) | BOOL | Number (0/1) | BOOL |
| `Date` | String (ISO) | Number (epoch) | String (ISO) | Number (epoch) |
| `Instant` | String (custom) | String (ISO ns) | String (ISO ms) | String (ISO ns) |

### Configuration

```java
@Configuration
@EnableDynamoDBRepositories(
    basePackages = "com.example.repository",
    marshallingMode = MarshallingMode.SDK_V1_COMPATIBLE  // Set mode here
)
public class DynamoDBConfig {
    // ...
}
```

### When to Use Each Mode

#### SDK_V2_NATIVE (Default)
**Use when:**
- Starting a new project with no existing DynamoDB data
- Want to use SDK v2 native type system
- Don't need backward compatibility with SDK v1

**Benefits:**
- Better type safety
- Smaller storage footprint for booleans (BOOL vs Number)
- Native DynamoDB types

#### SDK_V1_COMPATIBLE
**Use when:**
- Migrating from SDK v1 version of this library
- Have existing data in DynamoDB created with SDK v1
- Need to maintain data compatibility during migration

**Benefits:**
- No data migration required
- Queries work immediately with existing data
- Gradual migration path

### Migration Path

1. **Start with SDK_V1_COMPATIBLE:**
```java
@EnableDynamoDBRepositories(
    marshallingMode = MarshallingMode.SDK_V1_COMPATIBLE
)
```

2. **Validate all queries work with existing data**

3. **Later migrate data to SDK v2 format:**
   - Write migration scripts to convert data types
   - Test thoroughly
   - Switch to `SDK_V2_NATIVE`

4. **Update configuration:**
```java
@EnableDynamoDBRepositories(
    marshallingMode = MarshallingMode.SDK_V2_NATIVE
)
```

---

## Batch Operations Enhancements

### Automatic Retry with Exponential Backoff

Version 7.0 includes automatic retry logic for batch operations:

**Default Behavior:**
- Max retries: 8
- Base delay: 100ms
- Max delay: 20 seconds
- Backoff: 100ms → 200ms → 400ms → 800ms → 1.6s → 3.2s → 6.4s → 12.8s → 20s
- Jitter: Enabled (adds randomization to prevent thundering herd)

### Custom Retry Configuration

```java
import org.socialsignin.spring.data.dynamodb.core.BatchWriteRetryConfig;

@Configuration
public class DynamoDBConfig {

    @Bean
    public BatchWriteRetryConfig batchWriteRetryConfig() {
        return new BatchWriteRetryConfig.Builder()
            .maxRetries(10)                // Default: 8
            .baseDelayMs(200L)             // Default: 100ms
            .maxDelayMs(30000L)            // Default: 20000ms
            .useJitter(true)               // Default: true
            .build();
    }
}
```

### Disable Retries

```java
@Bean
public BatchWriteRetryConfig batchWriteRetryConfig() {
    return new BatchWriteRetryConfig.Builder()
        .disableRetries()  // Sets maxRetries to 0
        .build();
}
```

### Monitoring Retry Attempts

```java
try {
    productRepository.saveAll(products);
} catch (BatchWriteException e) {
    int retries = e.getRetriesAttempted();
    metricsService.recordBatchRetries("product_writes", retries);

    if (retries == 8) {
        // Exhausted all retries
        alertService.sendAlert("Maximum batch retries exceeded for product writes");
    }
}
```

---

## Complete Migration Example

### Before: SDK v1 Application

**pom.xml:**
```xml
<properties>
    <java.version>17</java.version>
    <spring-boot.version>3.2.0</spring-boot.version>
    <aws-java-sdk.version>1.12.772</aws-java-sdk.version>
    <spring.data.dynamodb.version>6.0.4</spring.data.dynamodb.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

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

**DynamoDBConfig.java:**
```java
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;

@Configuration
@EnableDynamoDBRepositories(basePackages = "com.example.repository")
public class DynamoDBConfig {

    @Value("${aws.dynamodb.endpoint:}")
    private String endpoint;

    @Value("${aws.region}")
    private String region;

    @Bean
    public AmazonDynamoDB amazonDynamoDB() {
        AmazonDynamoDBClientBuilder builder = AmazonDynamoDBClientBuilder.standard()
            .withRegion(region);

        if (!endpoint.isEmpty()) {
            builder.withEndpointConfiguration(
                new AwsClientBuilder.EndpointConfiguration(endpoint, region));
        }

        return builder.build();
    }
}
```

**Product.java:**
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

    @DynamoDBAttribute
    @DynamoDBTypeConverted(converter = InstantConverter.class)
    private Instant createdAt;

    @DynamoDBVersionAttribute
    private Long version;

    // Getters and setters
}
```

**InstantConverter.java:**
```java
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;

public class InstantConverter implements DynamoDBTypeConverter<String, Instant> {
    @Override
    public String convert(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    @Override
    public Instant unconvert(String s) {
        return s != null ? Instant.parse(s) : null;
    }
}
```

**ProductRepository.java:**
```java
public interface ProductRepository extends
    DynamoDBPagingAndSortingRepository<Product, String> {

    List<Product> findByName(String name);
}
```

**ProductService.java:**
```java
@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    public Product createProduct(Product product) {
        product.setCreatedAt(Instant.now());
        return productRepository.save(product);
    }

    public List<Product> saveProducts(List<Product> products) {
        try {
            return (List<Product>) productRepository.saveAll(products);
        } catch (Exception e) {
            logger.error("Batch write failed: {}", e.getMessage());
            throw e;
        }
    }
}
```

### After: SDK v2 Application

**pom.xml:**
```xml
<properties>
    <java.version>21</java.version>
    <spring-boot.version>3.2.0</spring-boot.version>
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
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

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

**DynamoDBConfig.java:**
```java
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import org.socialsignin.spring.data.dynamodb.core.MarshallingMode;
import org.socialsignin.spring.data.dynamodb.core.BatchWriteRetryConfig;

import java.net.URI;

@Configuration
@EnableDynamoDBRepositories(
    basePackages = "com.example.repository",
    marshallingMode = MarshallingMode.SDK_V1_COMPATIBLE
)
public class DynamoDBConfig {

    @Value("${aws.dynamodb.endpoint:}")
    private String endpoint;

    @Value("${aws.region}")
    private String region;

    @Bean
    public DynamoDbClient amazonDynamoDB() {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create());

        if (!endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        return builder.build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient amazonDynamoDB) {
        return DynamoDbEnhancedClient.builder()
            .dynamoDbClient(amazonDynamoDB)
            .build();
    }

    @Bean
    public BatchWriteRetryConfig batchWriteRetryConfig() {
        return new BatchWriteRetryConfig.Builder()
            .maxRetries(8)
            .baseDelayMs(100L)
            .maxDelayMs(20000L)
            .useJitter(true)
            .build();
    }
}
```

**Product.java:**
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

    @DynamoDbAttribute
    @DynamoDbConvertedBy(Instant2IsoAttributeConverter.class)
    private Instant createdAt;

    @DynamoDbVersionAttribute
    private Long version;

    // Getters and setters
}
```

**InstantConverter.java:**
```java
// No longer needed - use built-in Instant2IsoAttributeConverter
// Or keep as custom converter with AttributeConverter interface
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

**ProductRepository.java:**
```java
// NO CHANGES NEEDED
public interface ProductRepository extends
    DynamoDBPagingAndSortingRepository<Product, String> {

    List<Product> findByName(String name);
}
```

**ProductService.java:**
```java
import org.socialsignin.spring.data.dynamodb.exception.BatchWriteException;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DeadLetterQueueService dlqService;

    public Product createProduct(Product product) {
        product.setCreatedAt(Instant.now());
        return productRepository.save(product);
    }

    public List<Product> saveProducts(List<Product> products) {
        try {
            return (List<Product>) productRepository.saveAll(products);
            // Automatically retried up to 8 times with exponential backoff
        } catch (BatchWriteException e) {
            // Get type-safe access to failed entities
            List<Product> failedProducts = e.getUnprocessedEntities(Product.class);

            logger.error("Failed to save {} out of {} products after {} retries",
                failedProducts.size(),
                products.size(),
                e.getRetriesAttempted());

            // Send to DLQ for later processing
            dlqService.sendToQueue("product-writes-dlq", failedProducts);

            throw e;
        }
    }
}
```

---

## Testing with SNAPSHOT Versions

### Overview

Before the official 7.0.0 release, you can test your migration using SNAPSHOT versions. This allows you to validate the migration in your environment and provide feedback.

### Adding Snapshot Repository

SNAPSHOT versions are published to the Maven Central Snapshot Repository. You need to add this repository to your build configuration.

#### Maven Configuration

Add the snapshot repository to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>maven-central-snapshots</id>
        <url>https://central.sonatype.com/repository/maven-snapshots/</url>
        <snapshots>
            <enabled>true</enabled>
            <updatePolicy>always</updatePolicy>
        </snapshots>
        <releases>
            <enabled>false</enabled>
        </releases>
    </repository>
</repositories>
```

Then use the SNAPSHOT version:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>bom</artifactId>
            <version>2.38.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>io.github.prasanna0586</groupId>
        <artifactId>spring-data-dynamodb</artifactId>
        <version>7.0.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

#### Gradle Configuration

Add the snapshot repository to your `build.gradle` or `build.gradle.kts`:

**Groovy DSL (`build.gradle`):**
```groovy
repositories {
    mavenCentral()
    maven {
        url 'https://central.sonatype.com/repository/maven-snapshots/'
    }
}

dependencies {
    implementation(platform('software.amazon.awssdk:bom:2.38.1'))
    implementation 'io.github.prasanna0586:spring-data-dynamodb:7.0.0-SNAPSHOT'
}
```

**Kotlin DSL (`build.gradle.kts`):**
```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
    }
}

dependencies {
    implementation(platform("software.amazon.awssdk:bom:2.38.1"))
    implementation("io.github.prasanna0586:spring-data-dynamodb:7.0.0-SNAPSHOT")
}
```

### SNAPSHOT Version Updates

SNAPSHOT versions are mutable and can be updated. To ensure you're testing with the latest SNAPSHOT:

#### Maven

Force update snapshots:
```bash
mvn clean install -U
```

Or configure automatic updates in your `pom.xml`:
```xml
<repository>
    <id>maven-central-snapshots</id>
    <url>https://central.sonatype.com/repository/maven-snapshots/</url>
    <snapshots>
        <enabled>true</enabled>
        <updatePolicy>always</updatePolicy>  <!-- Update every build -->
    </snapshots>
</repository>
```

Other update policy options:
- `always` - Check for updates on every build
- `daily` - Check once per day (default)
- `interval:XXX` - Check every XXX minutes
- `never` - Never check for updates

#### Gradle

Gradle caches snapshots for 24 hours by default. To force refresh:

```bash
./gradlew build --refresh-dependencies
```

Or configure snapshot cache duration:

**Groovy DSL:**
```groovy
configurations.all {
    resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
}
```

**Kotlin DSL:**
```kotlin
configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}
```

### Testing Strategy with SNAPSHOTS

#### 1. Create a Test Branch

```bash
git checkout -b test-dynamodb-v7-snapshot
```

#### 2. Set Up Test Configuration

Create a dedicated test configuration file:

**`src/test/resources/application-snapshot-test.yml`:**
```yaml
spring:
  dynamodb:
    marshalling-mode: SDK_V1_COMPATIBLE  # Start with backward compatibility

# Use DynamoDB Local for testing
aws:
  dynamodb:
    endpoint: http://localhost:8000
    region: us-west-2

logging:
  level:
    org.socialsignin.spring.data.dynamodb: DEBUG
    software.amazon.awssdk: INFO
```

#### 3. Test with DynamoDB Local

Use DynamoDB Local in Docker for isolated testing:

```bash
# Start DynamoDB Local
docker run -d -p 8000:8000 amazon/dynamodb-local:latest
```

Or use Testcontainers in your tests:

```java
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
class MigrationIntegrationTest {

    @Container
    static GenericContainer<?> dynamoDb = new GenericContainer<>(
            DockerImageName.parse("amazon/dynamodb-local:latest"))
            .withExposedPorts(8000);

    @DynamicPropertySource
    static void dynamoDbProperties(DynamicPropertyRegistry registry) {
        registry.add("aws.dynamodb.endpoint",
            () -> "http://" + dynamoDb.getHost() + ":" + dynamoDb.getMappedPort(8000));
    }

    @Test
    void testMigrationCompatibility() {
        // Test your entities and repositories
    }
}
```

#### 4. Comprehensive Test Checklist

Test all critical functionality:

**Entity Operations:**
```java
@Test
void testCRUDOperations() {
    // Create
    Product product = new Product();
    product.setId("test-123");
    product.setName("Test Product");
    Product saved = productRepository.save(product);

    // Read
    Optional<Product> found = productRepository.findById("test-123");
    assertTrue(found.isPresent());

    // Update
    found.get().setName("Updated Product");
    productRepository.save(found.get());

    // Delete
    productRepository.deleteById("test-123");
}
```

**Query Methods:**
```java
@Test
void testCustomQueries() {
    // Test all custom query methods
    List<Product> products = productRepository.findByCategory("Electronics");
    assertNotNull(products);

    List<Product> expensive = productRepository.findByPriceGreaterThan(100.0);
    assertNotNull(expensive);
}
```

**Batch Operations:**
```java
@Test
void testBatchOperations() {
    List<Product> products = Arrays.asList(
        createProduct("1", "Product 1"),
        createProduct("2", "Product 2"),
        createProduct("3", "Product 3")
    );

    // Batch save
    productRepository.saveAll(products);

    // Batch load
    List<Product> loaded = productRepository.findAllById(
        Arrays.asList("1", "2", "3")
    );
    assertEquals(3, loaded.size());
}
```

**Type Converters:**
```java
@Test
void testTypeConversions() {
    // Test Date/Instant conversions
    Product product = new Product();
    product.setCreatedDate(new Date());
    product.setLastModified(Instant.now());

    Product saved = productRepository.save(product);
    Product loaded = productRepository.findById(saved.getId()).orElseThrow();

    assertNotNull(loaded.getCreatedDate());
    assertNotNull(loaded.getLastModified());
}
```

#### 5. Test Both Marshalling Modes

**Test SDK_V1_COMPATIBLE mode:**
```yaml
# application-v1-compatible.yml
spring:
  dynamodb:
    marshalling-mode: SDK_V1_COMPATIBLE
```

**Test SDK_V2_NATIVE mode:**
```yaml
# application-v2-native.yml
spring:
  dynamodb:
    marshalling-mode: SDK_V2_NATIVE
```

Run tests with both profiles:
```bash
# Test with V1 compatible mode
mvn test -Dspring.profiles.active=v1-compatible

# Test with V2 native mode
mvn test -Dspring.profiles.active=v2-native
```

#### 6. Performance Testing

Compare performance between versions:

```java
@Test
void comparePerformance() {
    int iterations = 1000;

    // Warm up
    for (int i = 0; i < 100; i++) {
        productRepository.save(createProduct("warm-" + i, "Warmup"));
    }

    // Measure
    long start = System.currentTimeMillis();
    for (int i = 0; i < iterations; i++) {
        productRepository.save(createProduct("perf-" + i, "Performance Test"));
    }
    long duration = System.currentTimeMillis() - start;

    System.out.println("Saved " + iterations + " items in " + duration + "ms");
    assertTrue(duration < 30000, "Performance regression detected");
}
```

### Reporting Issues with SNAPSHOT Versions

When testing SNAPSHOT versions, if you encounter issues:

1. **Verify SNAPSHOT Version**:
```bash
# Maven
mvn dependency:tree | grep spring-data-dynamodb

# Gradle
./gradlew dependencies | grep spring-data-dynamodb
```

2. **Enable Debug Logging**:
```yaml
logging:
  level:
    org.socialsignin.spring.data.dynamodb: DEBUG
    software.amazon.awssdk.enhanced: DEBUG
```

3. **Create Minimal Reproducible Example**:
```java
@SpringBootTest
class MinimalReproducibleTest {

    @Autowired
    private YourRepository repository;

    @Test
    void reproduceIssue() {
        // Minimal code that reproduces the issue
    }
}
```

4. **Report on GitHub**:
- Include SNAPSHOT version: `7.0.0-SNAPSHOT (date: YYYY-MM-DD)`
- Include AWS SDK version
- Include full stack trace
- Include minimal reproducible example
- Tag issue with `snapshot-testing` label

### Moving from SNAPSHOT to Release

Once 7.0.0 is officially released:

1. **Remove Snapshot Repository**:
```xml
<!-- Remove from pom.xml -->
<repositories>
    <repository>
        <id>maven-central-snapshots</id>
        ...
    </repository>
</repositories>
```

2. **Update to Release Version**:
```xml
<dependency>
    <groupId>io.github.prasanna0586</groupId>
    <artifactId>spring-data-dynamodb</artifactId>
    <version>7.0.0</version>  <!-- Official release -->
</dependency>
```

3. **Clean Build**:
```bash
# Maven
mvn clean install

# Gradle
./gradlew clean build
```

### Best Practices for SNAPSHOT Testing

✅ **DO:**
- Test in isolated environment first
- Use DynamoDB Local or dedicated test tables
- Test both marshalling modes
- Document any issues found
- Keep SNAPSHOT testing branch separate
- Update snapshots regularly during testing

❌ **DON'T:**
- Use SNAPSHOT versions in production
- Rely on SNAPSHOT for long-term projects
- Mix SNAPSHOT and release versions in dependencies
- Skip testing after SNAPSHOT updates
- Deploy SNAPSHOT versions to production environments

### Example Test Project Structure

```
my-app/
├── pom.xml (or build.gradle)
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/
│   │   │       ├── config/
│   │   │       │   └── DynamoDBConfig.java
│   │   │       ├── entity/
│   │   │       │   └── Product.java
│   │   │       └── repository/
│   │   │           └── ProductRepository.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── application-snapshot-test.yml
│   └── test/
│       ├── java/
│       │   └── com/example/
│       │       ├── integration/
│       │       │   ├── MigrationIntegrationTest.java
│       │       │   ├── BatchOperationsTest.java
│       │       │   └── QueryMethodsTest.java
│       │       └── performance/
│       │           └── PerformanceComparisonTest.java
│       └── resources/
│           ├── application-test.yml
│           └── test-data.json
```

---

## Migration Checklist

Use this checklist to track your migration progress:

### Pre-Migration
- [ ] Review this migration guide completely
- [ ] Identify all DynamoDB entities in your application
- [ ] Document current type converters in use
- [ ] Verify Java 21+ is available
- [ ] Create test environment for validation
- [ ] Back up existing DynamoDB data (if applicable)
- [ ] Plan rollback strategy

### Step 1: Dependencies
- [ ] Update to Java 21 or higher
- [ ] Remove all `com.amazonaws:aws-java-sdk-*` dependencies
- [ ] Add AWS SDK v2 BOM to dependency management
- [ ] Update `spring-data-dynamodb` to version 7.0.0+
- [ ] Add `software.amazon.awssdk:dynamodb-enhanced` dependency
- [ ] Clean and rebuild project
- [ ] Resolve any dependency conflicts

### Step 2: Configuration
- [ ] Update imports from `com.amazonaws` to `software.amazon.awssdk`
- [ ] Replace `AmazonDynamoDB` bean with `DynamoDbClient`
- [ ] Add `DynamoDbEnhancedClient` bean
- [ ] Update credential provider configuration
- [ ] Update endpoint configuration (if using local DynamoDB)
- [ ] Add `marshallingMode` to `@EnableDynamoDBRepositories`
- [ ] Add `BatchWriteRetryConfig` bean (optional, for custom retry)
- [ ] Update region configuration to use `Region` enum

### Step 3: Entity Annotations
- [ ] Update all entity class imports
- [ ] Replace `@DynamoDBTable` with `@DynamoDbBean`
- [ ] Replace `@DynamoDBHashKey` with `@DynamoDbPartitionKey`
- [ ] Replace `@DynamoDBRangeKey` with `@DynamoDbSortKey`
- [ ] Replace `@DynamoDBIndexHashKey` with `@DynamoDbSecondaryPartitionKey`
- [ ] Replace `@DynamoDBIndexRangeKey` with `@DynamoDbSecondarySortKey`
- [ ] Update `@DynamoDBAttribute` to `@DynamoDbAttribute`
- [ ] Replace `@DynamoDBIgnore` with `@DynamoDbIgnore`
- [ ] Update index name parameters (`globalSecondaryIndexName` → `indexNames`)

### Step 4: Type Converters
- [ ] Identify all custom `DynamoDBTypeConverter` implementations
- [ ] Identify all custom `DynamoDBMarshaller` implementations
- [ ] Migrate converters to `AttributeConverter` interface
- [ ] Replace `@DynamoDBTypeConverted` with `@DynamoDbConvertedBy`
- [ ] Replace `@DynamoDBMarshalling` with `@DynamoDbConvertedBy`
- [ ] Use built-in converters where possible (`Instant2IsoAttributeConverter`, etc.)
- [ ] Test all custom converters

### Step 5: Exception Handling
- [ ] Update all `BatchWriteException` catch blocks
- [ ] Update all `BatchDeleteException` catch blocks
- [ ] Implement DLQ or recovery logic using `getUnprocessedEntities()`
- [ ] Add monitoring for `getRetriesAttempted()`
- [ ] Remove manual retry logic (if using automatic retry)
- [ ] Update error logging to use new exception methods

### Step 6: Auto-Generated Keys (If Used)
- [ ] Choose approach: SDK v2 native or Spring Data DynamoDB
- [ ] If SDK v2 native: Add `AutoGeneratedUuidExtension` to enhanced client
- [ ] If SDK v2 native: Replace with `@DynamoDbAutoGeneratedUuid`
- [ ] If Spring Data DynamoDB: Use `@DynamoDBAutoGeneratedKey`
- [ ] Test auto-generation behavior

### Step 7: Repository Interfaces
- [ ] Review all repository interfaces (usually no changes needed)
- [ ] Review all custom query methods
- [ ] Verify method signatures match

### Step 8: Testing
- [ ] Compile application successfully
- [ ] Run all unit tests
- [ ] Run all integration tests
- [ ] Test against DynamoDB Local
- [ ] Test CRUD operations
- [ ] Test batch operations
- [ ] Test custom query methods
- [ ] Test pagination
- [ ] Test exception scenarios
- [ ] Test with existing data (if using SDK_V1_COMPATIBLE)
- [ ] Performance test critical paths
- [ ] Load test if applicable

### Step 9: Deployment
- [ ] Deploy to test/staging environment
- [ ] Smoke test critical functionality
- [ ] Monitor logs for errors
- [ ] Monitor CloudWatch metrics
- [ ] Verify batch retry behavior
- [ ] Check exception handling
- [ ] Prepare rollback plan
- [ ] Deploy to production (with monitoring)

### Post-Deployment
- [ ] Monitor application health
- [ ] Monitor DynamoDB metrics
- [ ] Check for unexpected errors
- [ ] Validate data integrity
- [ ] Document any issues encountered
- [ ] Update team documentation
- [ ] Plan data migration to SDK_V2_NATIVE (if using SDK_V1_COMPATIBLE)

---

## Troubleshooting

### Common Issues and Solutions

#### Issue 1: ClassNotFoundException for AWS SDK Classes

**Error:**
```
java.lang.ClassNotFoundException: com.amazonaws.services.dynamodbv2.AmazonDynamoDB
```

**Cause:** Old SDK v1 imports still present in code or old SDK v1 dependency still in classpath

**Solution:**
1. Search project for all `import com.amazonaws` statements
2. Replace with corresponding `software.amazon.awssdk` imports
3. Verify all SDK v1 dependencies removed from `pom.xml` or `build.gradle`
4. Clean and rebuild: `mvn clean install` or `./gradlew clean build`

#### Issue 2: NoSuchMethodError for DynamoDbEnhancedClient

**Error:**
```
java.lang.NoSuchMethodError: software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient.builder()
```

**Cause:** Version mismatch between AWS SDK v2 dependencies

**Solution:**
1. Use AWS SDK v2 BOM for version management:
```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>bom</artifactId>
            <version>2.38.1</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```
2. Remove explicit versions from individual AWS dependencies
3. Clean and rebuild

#### Issue 3: Table Not Found

**Error:**
```
ResourceNotFoundException: Requested resource not found: Table: Products not found
```

**Cause:** Table name inference changed from SDK v1

**Solution:**
The `@DynamoDbBean` annotation infers table name from class name. If your table name differs:

**Option 1:** Rename your class to match table name
```java
@DynamoDbBean
public class Products {  // Matches "Products" table
    // ...
}
```

**Option 2:** Use `@DynamoDbAttribute` at class level (not standard, but possible with custom table schema)

**Option 3:** Configure table name override in repository configuration (requires custom setup)

#### Issue 4: Type Conversion Errors

**Error:**
```
AttributeConverterException: Cannot convert attribute value to type Instant
```

**Cause:** Marshalling mode mismatch with existing data

**Solution:**
1. If migrating from SDK v1 with existing data, use SDK_V1_COMPATIBLE mode:
```java
@EnableDynamoDBRepositories(
    marshallingMode = MarshallingMode.SDK_V1_COMPATIBLE
)
```

2. If starting fresh, use SDK_V2_NATIVE (default):
```java
@EnableDynamoDBRepositories(
    marshallingMode = MarshallingMode.SDK_V2_NATIVE
)
```

#### Issue 5: Boolean Fields Not Queryable

**Error:**
```
ValidationException: Invalid comparison operator for Boolean
```

**Cause:** Boolean stored as Number (0/1) in SDK v1 format, but querying with BOOL comparisons

**Solution:**
Use SDK_V1_COMPATIBLE marshalling mode:
```java
@EnableDynamoDBRepositories(
    marshallingMode = MarshallingMode.SDK_V1_COMPATIBLE
)
```

#### Issue 6: Cannot Inject DynamoDbEnhancedClient

**Error:**
```
NoSuchBeanDefinitionException: No qualifying bean of type 'software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient'
```

**Cause:** Missing `DynamoDbEnhancedClient` bean in configuration

**Solution:**
Add bean to configuration:
```java
@Bean
public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient amazonDynamoDB) {
    return DynamoDbEnhancedClient.builder()
        .dynamoDbClient(amazonDynamoDB)
        .build();
}
```

#### Issue 7: Batch Operations Failing Silently

**Issue:** Batch operations completing but some items not saved

**Cause:** Not handling `BatchWriteException` which contains failed items

**Solution:**
Always catch and handle batch exceptions:
```java
try {
    repository.saveAll(items);
} catch (BatchWriteException e) {
    List<MyEntity> failed = e.getUnprocessedEntities(MyEntity.class);
    // Handle failed items
    logger.error("Failed to save {} items", failed.size());
}
```

#### Issue 8: Auto-Generated UUID Not Working

**Error:**
```
java.lang.NullPointerException: Cannot invoke "String.length()" because "this.id" is null
```

**Cause:** Missing `AutoGeneratedUuidExtension` in enhanced client configuration

**Solution:**
Add extension to enhanced client:
```java
import software.amazon.awssdk.enhanced.dynamodb.extensions.AutoGeneratedUuidExtension;

@Bean
public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient client) {
    return DynamoDbEnhancedClient.builder()
        .dynamoDbClient(client)
        .extensions(AutoGeneratedUuidExtension.create())
        .build();
}
```

#### Issue 9: Optimistic Locking Not Working

**Error:** Items updated without version check

**Cause:** `@DynamoDbVersionAttribute` not properly recognized

**Solution:**
Ensure version attribute is properly annotated and has correct type (Long or Integer):
```java
@DynamoDbVersionAttribute
private Long version;  // Must be Long or Integer
```

#### Issue 10: Custom Converter Not Applied

**Error:** Data stored in wrong format

**Cause:** Converter not properly registered or annotation missing

**Solution:**
1. Verify `@DynamoDbConvertedBy` annotation present:
```java
@DynamoDbAttribute("createdAt")
@DynamoDbConvertedBy(InstantConverter.class)
private Instant createdAt;
```

2. Verify converter implements all required methods:
```java
public class InstantConverter implements AttributeConverter<Instant> {
    // Must implement all 4 methods:
    // transformFrom, transformTo, type, attributeValueType
}
```

### Debugging Tips

1. **Enable DEBUG logging:**
```yaml
logging:
  level:
    org.socialsignin.spring.data.dynamodb: DEBUG
    software.amazon.awssdk: DEBUG
```

2. **Verify table schema:**
```java
@Bean
CommandLineRunner verifySchema(DynamoDbClient client) {
    return args -> {
        DescribeTableResponse response = client.describeTable(
            DescribeTableRequest.builder()
                .tableName("YourTableName")
                .build()
        );
        System.out.println(response);
    };
}
```

3. **Test with DynamoDB Local first:**
```xml
<dependency>
    <groupId>com.amazonaws</groupId>
    <artifactId>DynamoDBLocal</artifactId>
    <version>2.0.0</version>
    <scope>test</scope>
</dependency>
```

```java
@Bean
@Profile("local")
public DynamoDbClient amazonDynamoDB() {
    return DynamoDbClient.builder()
        .endpointOverride(URI.create("http://localhost:8000"))
        .region(Region.US_EAST_1)
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create("dummy", "dummy")))
        .build();
}
```

---

## FAQ

### Q1: Can I use SDK v1 and SDK v2 in the same application?

**A:** No. AWS SDK v1 and SDK v2 have conflicting classes and cannot coexist in the same classpath. You must completely remove all SDK v1 dependencies before migrating to SDK v2.

### Q2: Do I need to migrate my data in DynamoDB?

**A:** It depends on your marshalling mode choice:
- **SDK_V1_COMPATIBLE**: No data migration needed. This mode reads/writes data in a format compatible with SDK v1.
- **SDK_V2_NATIVE**: Data migration may be needed for Boolean and Date types if you have existing data.

### Q3: Will my existing queries still work?

**A:** Yes, if you use **SDK_V1_COMPATIBLE** marshalling mode. This ensures backward compatibility with data created using SDK v1 version of the library.

### Q4: What's the performance impact of the migration?

**A:** SDK v2 generally provides **better performance** due to:
- Improved connection pooling
- Better HTTP/2 support
- Non-blocking I/O
- Reduced memory footprint

Actual performance gains depend on your usage patterns.

### Q5: Can I gradually migrate my application?

**A:** No. Because SDK v1 and v2 cannot coexist, you must migrate the entire application at once. However, you can:
1. Use **SDK_V1_COMPATIBLE** mode initially for data compatibility
2. Later switch to **SDK_V2_NATIVE** after data migration

### Q6: Are repository interfaces affected by the migration?

**A:** No. Repository interfaces (`DynamoDBPagingAndSortingRepository`, etc.) remain unchanged. The migration is transparent at the repository level.

### Q7: How do I know which marshalling mode to use?

**Decision tree:**
- Have existing data? → Use **SDK_V1_COMPATIBLE**
- New project? → Use **SDK_V2_NATIVE**
- Want better type safety? → Use **SDK_V2_NATIVE** (may need data migration)

### Q8: What happens to my custom DynamoDBTypeConverter implementations?

**A:** They must be migrated to the `AttributeConverter` interface. The library provides several built-in converters you can use instead of custom implementations for common types (Instant, Date).

### Q9: Is the automatic batch retry enabled by default?

**A:** Yes. Batch operations automatically retry up to 8 times with exponential backoff. You can customize or disable this behavior using `BatchWriteRetryConfig`.

### Q10: How do I test the migration?

**A:** Best practices:
1. Use DynamoDB Local for initial testing
2. Test with production-like data in staging
3. Use SDK_V1_COMPATIBLE mode initially
4. Gradually rollout to production with monitoring

### Q11: What if I encounter an issue not covered in this guide?

**A:**
1. Check the [GitHub Issues](https://github.com/prasanna0586/spring-data-dynamodb/issues)
2. Enable DEBUG logging for detailed error messages
3. Open a new issue with:
   - Spring Data DynamoDB version
   - AWS SDK version
   - Stack trace
   - Minimal reproducible example

### Q12: Can I roll back to SDK v1 if needed?

**A:** Yes, but you'll need to:
1. Restore previous version of your application
2. If you used SDK_V1_COMPATIBLE mode, no data changes needed
3. If you used SDK_V2_NATIVE and migrated data, you may need to restore data from backup

### Q13: What's the difference between @DynamoDbBean and @DynamoDBTable?

**A:**
- `@DynamoDBTable`: SDK v1 annotation, required table name parameter
- `@DynamoDbBean`: SDK v2 annotation, infers table name from class name

### Q14: How does version 7.0 handle null values?

**A:** SDK v2 handles nulls more strictly than SDK v1. Null values are not stored in DynamoDB (consistent with DynamoDB best practices). Use `@DynamoDbIgnore` for calculated fields.

### Q15: Can I use Spring Boot Actuator health checks with SDK v2?

**A:** Yes. Configure a health indicator:
```java
@Component
public class DynamoDBHealthIndicator implements HealthIndicator {

    @Autowired
    private DynamoDbClient dynamoDbClient;

    @Override
    public Health health() {
        try {
            dynamoDbClient.listTables();
            return Health.up().build();
        } catch (Exception e) {
            return Health.down(e).build();
        }
    }
}
```

---

## Additional Resources

### Documentation
- [AWS SDK for Java 2.x Developer Guide](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/)
- [DynamoDB Enhanced Client Guide](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/dynamodb-enhanced-client.html)
- [Spring Data DynamoDB GitHub Repository](https://github.com/prasanna0586/spring-data-dynamodb)

### Tools
- [DynamoDB Local](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBLocal.html) - For local testing
- [NoSQL Workbench](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/workbench.html) - Visual tool for DynamoDB

### Support
- [GitHub Issues](https://github.com/prasanna0586/spring-data-dynamodb/issues) - Report bugs or request features
- [Stack Overflow](https://stackoverflow.com/questions/tagged/spring-data-dynamodb) - Community support

---

## Version History

| Version | Release Date | Key Changes |
|---------|--------------|-------------|
| 7.0.0 | 2025-01-XX | Initial SDK v2 migration release |
| 6.0.4 | 2024-XX-XX | Last SDK v1 version |

---

## License

This migration guide is part of the Spring Data DynamoDB project and is licensed under the Apache License 2.0.

---

**Need Help?** If you encounter issues during migration, please [open an issue](https://github.com/prasanna0586/spring-data-dynamodb/issues) with:
- Your Spring Data DynamoDB version
- AWS SDK version
- Spring Boot version
- Complete stack trace
- Minimal reproducible example

Happy Migrating! 🚀
