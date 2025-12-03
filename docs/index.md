# Spring Data DynamoDB - User Guide

**Version 7.x - AWS SDK v2**

A Spring Data module for DynamoDB, built on AWS SDK v2.

[![Build Status](https://github.com/prasanna0586/spring-data-dynamodb/actions/workflows/runTests.yml/badge.svg)](https://github.com/prasanna0586/spring-data-dynamodb/actions/workflows/runTests.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.prasanna0586/spring-data-dynamodb)](https://central.sonatype.com/artifact/io.github.prasanna0586/spring-data-dynamodb)
[![codecov](https://codecov.io/gh/prasanna0586/spring-data-dynamodb/branch/master/graph/badge.svg)](https://codecov.io/gh/prasanna0586/spring-data-dynamodb)
[![Coverage Report](https://img.shields.io/badge/coverage-report-blue)](https://prasanna0586.github.io/spring-data-dynamodb/coverage/)
[![Last Commit](https://img.shields.io/github/last-commit/prasanna0586/spring-data-dynamodb)](https://github.com/prasanna0586/spring-data-dynamodb/commits/master)
![analytics](https://static.scarf.sh/a.png?x-pxid=f3d71552-b010-4688-88df-fc08055a8068)

---

## Table of Contents

1. [Getting Started](#getting-started)
2. [Configuration](#configuration)
   - [Basic Configuration](#basic-configuration)
   - [Custom Credentials](#configuration-with-custom-credentials)
   - [DynamoDB Local](#configuration-with-custom-endpoint-dynamodb-local)
   - [XML-Based Configuration](#xml-based-configuration)
3. [Entities](#entities)
   - [Basic Entity](#basic-entity)
   - [Composite Key (Hash + Range)](#entity-with-composite-key-hash--range)
   - [Type Converters](#entity-with-type-converters)
4. [Repositories](#repositories)
   - [Repository Interfaces](#repository-interfaces)
   - [CRUD Operations](#crud-operations)
   - [Custom Repository Implementations](#custom-repository-implementations)
5. [Query Methods](#query-methods)
   - [Supported Comparison Operators](#supported-comparison-operators)
   - [Hash Key Queries](#hash-key-queries-efficient)
   - [Hash + Range Key Queries](#hash--range-key-queries-efficient)
   - [Scan Operations](#scan-operations-requires-enablescan)
6. [Indexes (GSI & LSI)](#indexes-gsi--lsi)
   - [Global Secondary Index (GSI)](#global-secondary-index-gsi)
   - [Local Secondary Index (LSI)](#local-secondary-index-lsi)
7. [Advanced Queries](#advanced-queries)
   - [@Query Annotation](#query-annotation)
   - [Projections](#projections)
8. [Batch Operations](#batch-operations)
9. [Type Converters](#type-converters)
10. [Auto-Generated Keys & Timestamps](#auto-generated-keys--timestamps)
11. [Event Listeners](#event-listeners)
12. [Pagination](#pagination)
    - [Query Size Limits and Pageable](#query-size-limits-and-pageable)
13. [DynamoDB Operations Template](#dynamodb-operations-template)
14. [Operational Features](#operational-features)
    - [Alter Table Name During Runtime](#alter-table-name-during-runtime)
    - [Multi-Repository Configuration](#multi-repository-configuration)
    - [Spring Data REST Integration](#spring-data-rest-integration)
    - [Amazon DynamoDB Accelerator (DAX)](#amazon-dynamodb-accelerator-dax)
    - [Autocreate Tables](#autocreate-tables)
15. [Access to Releases](#access-to-releases)
16. [Performance Optimization](#performance-optimization)
17. [Testing Strategies](#testing-strategies)
    - [Kotlin Example](#composite-primary-keys-kotlin-example)
18. [Best Practices](#best-practices)
19. [Troubleshooting](#troubleshooting)
20. [GraalVM Native Image Support](#graalvm-native-image-support)

---

## Getting Started

### Prerequisites

- **Java 21+**
- **Spring Boot 3.x** or **Spring Framework 6.x**
- **AWS Account** with DynamoDB access

### Maven Dependencies

```xml
<properties>
    <java.version>21</java.version>
    <aws-sdk.version>2.38.1</aws-sdk.version>
    <spring.data.dynamodb.version>7.0.0</spring.data.dynamodb.version>
</properties>

<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>software.amazon.awssdk</groupId>
            <artifactId>bom</artifactId>
            <version>${aws-sdk.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- Spring Data DynamoDB -->
    <dependency>
        <groupId>io.github.prasanna0586</groupId>
        <artifactId>spring-data-dynamodb</artifactId>
        <version>${spring.data.dynamodb.version}</version>
    </dependency>

    <!-- AWS SDK v2 DynamoDB Enhanced -->
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>dynamodb-enhanced</artifactId>
    </dependency>
</dependencies>
```

### Gradle Dependencies

```gradle
dependencies {
    implementation platform('software.amazon.awssdk:bom:2.38.1')
    implementation 'io.github.prasanna0586:spring-data-dynamodb:7.0.0'
    implementation 'software.amazon.awssdk:dynamodb-enhanced'
}
```

### Quick Start Example

**1. Configuration**

```java
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableDynamoDBRepositories(basePackages = "com.example.repository")
public class DynamoDBConfig {

    @Bean
    public DynamoDbClient amazonDynamoDB() {
        return DynamoDbClient.builder()
            .region(Region.US_EAST_1)
            .build();
    }
}
```

**Note:** You only need to define a `DynamoDbClient` bean. The library creates `DynamoDbEnhancedClient` internally.

**2. Entity**

```java
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;
import java.time.Instant;

@DynamoDbBean
public class User {

    private String userId;
    private String name;
    private String email;
    private Instant createdAt;
    private Long version;

    public User() {}

    @DynamoDbPartitionKey
    @DynamoDbAttribute("userId")
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    @DynamoDbAttribute("name")
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @DynamoDbAttribute("email")
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    @DynamoDbAttribute("createdAt")
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @DynamoDbVersionAttribute
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
```

**Important:** In AWS SDK v2, annotations must be placed on **getter methods**, not on fields.

**3. Repository**

```java
import org.socialsignin.spring.data.dynamodb.repository.EnableScan;
import org.springframework.data.repository.CrudRepository;

@EnableScan
public interface UserRepository extends CrudRepository<User, String> {
    List<User> findByName(String name);
}
```

**4. Usage**

```java
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    public User createUser(String name, String email) {
        User user = new User();
        user.setUserId(UUID.randomUUID().toString());
        user.setName(name);
        user.setEmail(email);
        user.setCreatedAt(Instant.now());
        return userRepository.save(user);
    }

    public List<User> getUsersByName(String name) {
        return userRepository.findByName(name);
    }
}
```

---

## Configuration

### Basic Configuration

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
            .region(Region.US_EAST_1)
            .build();
    }
}
```

**Note:** You only need to define a `DynamoDbClient` bean. The library creates `DynamoDbEnhancedClient` internally.

### Configuration with Custom Credentials

```java
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

@Bean
public DynamoDbClient amazonDynamoDB() {
    return DynamoDbClient.builder()
        .region(Region.US_EAST_1)
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKey, secretKey)))
        .build();
}
```

### Configuration with Custom Endpoint (DynamoDB Local)

```java
import java.net.URI;

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

### XML-Based Configuration

For applications using XML-based Spring configuration:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans xmlns="http://www.springframework.org/schema/beans"
       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
       xmlns:dynamodb="http://docs.socialsignin.org/schema/data/dynamodb"
       xsi:schemaLocation="http://www.springframework.org/schema/beans
                           http://www.springframework.org/schema/beans/spring-beans.xsd
                           http://docs.socialsignin.org/schema/data/dynamodb
                           http://derjust.github.io/spring-data-dynamodb/spring-dynamodb-1.0.xsd">

  <bean id="dynamoDbClient" class="software.amazon.awssdk.services.dynamodb.DynamoDbClient"
        factory-method="create" />

  <dynamodb:repositories base-package="com.acme.repositories"
                         amazon-dynamodb-ref="dynamoDbClient" />

</beans>
```

### @EnableDynamoDBRepositories Options

```java
@Configuration
@EnableDynamoDBRepositories(
    basePackages = "com.example.repository",
    amazonDynamoDBRef = "customDynamoDBClient",
    dynamoDBOperationsRef = "customDynamoDBOperations",
    marshallingMode = MarshallingMode.SDK_V2_NATIVE
)
public class DynamoDBConfig {
    // ...
}
```

**Available Options:**
- `basePackages`: Packages to scan for repositories
- `amazonDynamoDBRef`: Bean name for DynamoDB client (default: "amazonDynamoDB")
- `dynamoDBOperationsRef`: Bean name for DynamoDB operations
- `marshallingMode`: `SDK_V2_NATIVE` (default) or `SDK_V1_COMPATIBLE`

### Custom Table Name Resolution

```java
import org.socialsignin.spring.data.dynamodb.core.TableNameResolver;

@Value("${app.environment.prefix}")
private String environmentPrefix;

@Bean
public TableNameResolver tableNameResolver() {
    return new TableNameResolver() {
        @Override
        public <T> String resolveTableName(Class<T> domainClass, String baseTableName) {
            return environmentPrefix + "_" + baseTableName;
        }
    };
}
```

### Batch Retry Configuration

```java
import org.socialsignin.spring.data.dynamodb.core.BatchWriteRetryConfig;

@Bean
public BatchWriteRetryConfig batchWriteRetryConfig() {
    return new BatchWriteRetryConfig.Builder()
        .maxRetries(10)                // Default: 8
        .baseDelayMs(200L)             // Default: 100ms
        .maxDelayMs(30000L)            // Default: 20000ms
        .useJitter(true)               // Default: true
        .build();
}
```

---

## Entities

### Basic Entity

This example demonstrates a simple DynamoDB entity with a hash key only:

```java
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;
import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbAutoGeneratedUuid;

@DynamoDbBean
public class Customer {

    private String id;
    private String emailAddress;
    private String firstName;
    private String lastName;
    private Long version;
    private String calculatedField;

    public Customer() {}

    public Customer(String emailAddress, String firstName, String lastName) {
        this.emailAddress = emailAddress;
        this.firstName = firstName;
        this.lastName = lastName;
    }

    @DynamoDbPartitionKey
    @DynamoDbAutoGeneratedUuid  // Requires AutoGeneratedUuidExtension
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    @DynamoDbAttribute("emailAddress")
    public String getEmailAddress() { return emailAddress; }
    public void setEmailAddress(String emailAddress) { this.emailAddress = emailAddress; }

    @DynamoDbAttribute("firstName")
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    @DynamoDbAttribute("lastName")
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    @DynamoDbVersionAttribute
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    @DynamoDbIgnore
    public String getCalculatedField() { return calculatedField; }
    public void setCalculatedField(String calculatedField) { this.calculatedField = calculatedField; }
}
```

**Repository:**
```java
import org.springframework.data.repository.CrudRepository;

public interface CustomerRepository extends CrudRepository<Customer, String> {
}
```

This minimal setup enables complete CRUD functionality for hash-key-only DynamoDB tables.

### Entity with Composite Key (Hash + Range)

For tables with composite primary keys (partition key + sort key):

**Key Class:**
```java
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import java.io.Serializable;

@DynamoDbBean
public class PlaylistId implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userName;
    private String playlistName;

    public PlaylistId() {}

    public PlaylistId(String userName, String playlistName) {
        this.userName = userName;
        this.playlistName = playlistName;
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("userName")
    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("playlistName")
    public String getPlaylistName() {
        return playlistName;
    }

    public void setPlaylistName(String playlistName) {
        this.playlistName = playlistName;
    }

    // equals() and hashCode() required for composite keys
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlaylistId that = (PlaylistId) o;
        return Objects.equals(userName, that.userName) &&
               Objects.equals(playlistName, that.playlistName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userName, playlistName);
    }
}
```

**Entity Class:**
```java
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import org.springframework.data.annotation.Id;

@DynamoDbBean
public class Playlist {

    @Id
    private PlaylistId id;

    private String description;
    private Integer trackCount;

    public Playlist() {}

    @DynamoDbPartitionKey
    @DynamoDbAttribute("userName")
    public String getUserName() {
        return id != null ? id.getUserName() : null;
    }

    public void setUserName(String userName) {
        if (id == null) {
            id = new PlaylistId();
        }
        id.setUserName(userName);
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("playlistName")
    public String getPlaylistName() {
        return id != null ? id.getPlaylistName() : null;
    }

    public void setPlaylistName(String playlistName) {
        if (id == null) {
            id = new PlaylistId();
        }
        id.setPlaylistName(playlistName);
    }

    // Other getters and setters
}
```

**Repository:**
```java
import org.springframework.data.repository.CrudRepository;

public interface PlaylistRepository extends CrudRepository<Playlist, PlaylistId> {
    List<Playlist> findByUserName(String userName);
}
```

**Common Errors with Composite Keys:**
1. **DynamoDBMappingException** - Remove getters/setters from the `@Id` field itself
2. **Missing @Id annotation** - Verify all required annotations are present
3. **BatchDeleteException** - Use built-in `deleteById()` rather than custom delete methods
4. **NullPointerException** - Ensure entity includes lazy initialization in setters

### Entity with Type Converters

```java
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import org.socialsignin.spring.data.dynamodb.marshaller.Instant2IsoAttributeConverter;
import java.time.Instant;

@DynamoDbBean
public class Event {

    private String eventId;
    private Instant timestamp;
    private String data;

    public Event() {}

    @DynamoDbPartitionKey
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    @DynamoDbAttribute("timestamp")
    @DynamoDbConvertedBy(Instant2IsoAttributeConverter.class)
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    @DynamoDbAttribute("data")
    public String getData() { return data; }
    public void setData(String data) { this.data = data; }
}
```

### Supported Annotations

| Annotation | Purpose |
|------------|---------|
| `@DynamoDbBean` | Marks class as DynamoDB entity |
| `@DynamoDbPartitionKey` | Hash key / partition key |
| `@DynamoDbSortKey` | Range key / sort key |
| `@DynamoDbAttribute("name")` | Attribute name mapping |
| `@DynamoDbSecondaryPartitionKey(indexNames={...})` | GSI partition key |
| `@DynamoDbSecondarySortKey(indexNames={...})` | GSI/LSI sort key |
| `@DynamoDbConvertedBy(X.class)` | Custom type converter |
| `@DynamoDbVersionAttribute` | Optimistic locking |
| `@DynamoDbIgnore` | Exclude from persistence |
| `@DynamoDbAutoGeneratedUuid` | Auto-generate UUID (requires extension) |

---

## Repositories

### Repository Interfaces

**CrudRepository** (Recommended)
```java
import org.springframework.data.repository.CrudRepository;

public interface UserRepository extends CrudRepository<User, String> {
    // Inherits: save, findById, findAll, delete, count, etc.
}
```

**PagingAndSortingRepository**
```java
import org.springframework.data.repository.PagingAndSortingRepository;

public interface UserRepository extends PagingAndSortingRepository<User, String> {
    // Inherits: All CRUD + findAll(Pageable), findAll(Sort)
}
```

The library also provides `DynamoDBCrudRepository` and `DynamoDBPagingAndSortingRepository` as marker interfaces that extend the Spring Data interfaces above.

### CRUD Operations

```java
// Save
User user = new User();
user.setUserId("123");
user.setName("John Doe");
userRepository.save(user);

// Find by ID
Optional<User> user = userRepository.findById("123");

// Find all
Iterable<User> allUsers = userRepository.findAll();

// Delete
userRepository.deleteById("123");

// Count
long count = userRepository.count();

// Exists
boolean exists = userRepository.existsById("123");
```

### Batch Operations

```java
// Batch save
List<User> users = Arrays.asList(user1, user2, user3);
userRepository.saveAll(users);

// Batch find
List<String> ids = Arrays.asList("1", "2", "3");
List<User> users = userRepository.findAllById(ids);

// Batch delete
userRepository.deleteAll(users);
```

### Custom Repository Implementations

Spring-Data provides hooks for adding customized methods to repository beans.

**1. Create Custom Method Interface:**
```java
public interface UserCustomRepository {
    String fancyCustomMethod();
    List<User> findUsersWithComplexCriteria(String criteria);
}
```

**2. Extend Repository Interface:**
```java
public interface UserRepository
       extends DynamoDBCrudRepository<User, String>,
               UserCustomRepository {
    List<User> findByName(String name);
}
```

**3. Implement Custom Methods (suffix must be `Impl`):**
```java
import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;
import org.springframework.beans.factory.annotation.Autowired;

public class UserRepositoryImpl implements UserCustomRepository {

    @Autowired
    private DynamoDBOperations dynamoDBOperations;

    @Override
    public String fancyCustomMethod() {
        User user = dynamoDBOperations.load(User.class, "42");
        if (user == null) {
            return "Not found";
        }
        return user.getName();
    }

    @Override
    public List<User> findUsersWithComplexCriteria(String criteria) {
        // Use DynamoDBOperations for custom queries
        // ...
    }
}
```

**4. Use the Extended Repository:**
```java
@Service
public class UserService {

    @Autowired
    private UserRepository repository;

    public void callingMethod() {
        // Standard Spring Data methods
        repository.findByName("John");

        // Custom methods
        repository.fancyCustomMethod();
    }
}
```

Spring automatically merges all implementations under a single repository interface.

---

## Query Methods

### Supported Comparison Operators

Spring-data-dynamodb processes repository methods in two ways:

1. **Query Attempt**: Builds a DynamoDB QueryRequest if all properties in the method name are Hash Keys, Range Keys, GSI Hash Keys, or GSI Range Keys.

2. **Scan Fallback**: Falls back to a Scan operation for properties outside the above list (requires `@EnableScan` annotation on class or method).

| Spring Data | DynamoDB | Notes |
|-------------|----------|-------|
| `In` | EQ | With OR concatenation |
| `Containing` | CONTAINS | |
| `StartingWith` | BEGINS_WITH | |
| `Between` | BETWEEN | |
| `After` | GT | |
| `GreaterThan` | GT | |
| `Before` | LT | |
| `LessThan` | LT | |
| `GreaterThanEqual` | GE | |
| `LessThanEqual` | LE | |
| `IsNull` | NULL | |
| `IsNotNull` | NOT_NULL | |
| `True` | EQ | |
| `False` | EQ | |
| `Is` / `Equals` | EQ | Special conditions for HashKey/RangeKey |
| `Not` | NE | |

**Partial Support:**
- Sort operations are supported but only function on RangeKeys

**Unsupported Operators:**
- Case insensitivity
- Top
- First
- Like
- Not like

### Hash Key Queries (Efficient)

```java
public interface OrderRepository extends DynamoDBCrudRepository<Order, String> {

    // Query by partition key only
    List<Order> findByCustomerId(String customerId);

    // Query by partition key with sorting
    List<Order> findByCustomerIdOrderByOrderDateDesc(String customerId);

    // Query by partition key with count
    long countByCustomerId(String customerId);
}
```

### Hash + Range Key Queries (Efficient)

```java
public interface OrderRepository extends DynamoDBCrudRepository<Order, String> {

    // Exact match on both keys
    Order findByCustomerIdAndOrderDate(String customerId, String orderDate);

    // Range key comparison
    List<Order> findByCustomerIdAndOrderDateAfter(String customerId, String date);
    List<Order> findByCustomerIdAndOrderDateBefore(String customerId, String date);
    List<Order> findByCustomerIdAndOrderDateBetween(String customerId, String start, String end);

    // Range key prefix match
    List<Order> findByCustomerIdAndOrderDateStartingWith(String customerId, String prefix);
}
```

### Scan Operations (Requires @EnableScan)

```java
import org.socialsignin.spring.data.dynamodb.repository.EnableScan;
import org.socialsignin.spring.data.dynamodb.repository.EnableScanCount;

public interface UserRepository extends DynamoDBCrudRepository<User, String> {

    @EnableScan
    List<User> findByName(String name);

    @EnableScan
    List<User> findByAgeGreaterThan(Integer age);

    @EnableScan
    @EnableScanCount
    long countByStatus(String status);
}
```

**Warning:** Scan operations are expensive. Use `@EnableScan` to explicitly allow them. Consider using GSI/LSI for frequently queried attributes.

---

## Indexes (GSI & LSI)

### Global Secondary Index (GSI)

Global Secondary Indexes allow querying by non-key attributes efficiently.

**Entity with GSI:**
```java
@DynamoDbBean
public class Customer {

    @DynamoDbPartitionKey
    @DynamoDbAttribute("id")
    private String id;

    @DynamoDbSecondaryPartitionKey(indexNames = {"idx_global_emailAddress"})
    @DynamoDbAttribute("emailAddress")
    private String emailAddress;

    @DynamoDbAttribute("firstName")
    private String firstName;

    @DynamoDbAttribute("lastName")
    private String lastName;

    // Constructors and getters/setters
}
```

**Repository using GSI:**
```java
public interface CustomerRepository extends DynamoDBCrudRepository<Customer, String> {

    // Automatically uses idx_global_emailAddress GSI
    List<Customer> findByEmailAddress(String emailAddress);
}
```

Spring-data-dynamodb recognizes the `@DynamoDbSecondaryPartitionKey` annotation and sends a Query request to DynamoDB. Without this annotation, it falls back to Scan if `@EnableScan` is present.

**GSI with Range Key:**
```java
@DynamoDbBean
public class User {

    @DynamoDbPartitionKey
    @DynamoDbAttribute("userId")
    private String userId;

    @DynamoDbAttribute("name")
    private String name;

    @DynamoDbSecondaryPartitionKey(indexNames = {"email-index"})
    @DynamoDbAttribute("email")
    private String email;

    @DynamoDbSecondarySortKey(indexNames = {"email-index"})
    @DynamoDbAttribute("registrationDate")
    private String registrationDate;

    // Getters and setters
}
```

**Repository:**
```java
public interface UserRepository extends DynamoDBCrudRepository<User, String> {

    // Automatically uses email-index
    List<User> findByEmail(String email);

    // GSI with range key
    List<User> findByEmailAndRegistrationDateAfter(String email, String date);

    // GSI with sorting
    List<User> findByEmailOrderByRegistrationDateDesc(String email);
}
```

### Local Secondary Index (LSI)

LSIs share the same partition key as the base table but have a different sort key.

**Entity with LSI:**
```java
@DynamoDbBean
public class Order {

    @DynamoDbPartitionKey
    @DynamoDbAttribute("customerId")
    private String customerId;

    @DynamoDbSortKey
    @DynamoDbAttribute("orderDate")
    private String orderDate;

    @DynamoDbSecondarySortKey(indexNames = {"customer-amount-index"})
    @DynamoDbAttribute("amount")
    private Double amount;

    // Getters and setters
}
```

**Repository using LSI:**
```java
public interface OrderRepository extends DynamoDBCrudRepository<Order, String> {

    // Uses LSI with customerId (hash) + amount (LSI range)
    List<Order> findByCustomerIdAndAmountGreaterThan(String customerId, Double amount);

    // LSI with sorting
    List<Order> findByCustomerIdOrderByAmountDesc(String customerId);
}
```

---

## Advanced Queries

### @Query Annotation

#### Projections

Projections allow retrieving only specific attributes from DynamoDB, reducing data transfer and improving performance.

```java
import org.socialsignin.spring.data.dynamodb.repository.Query;

public interface UserRepository extends DynamoDBCrudRepository<User, String> {

    @Query(fields = "leaveDate")
    List<User> findByPostCode(String postCode);

    @Query(fields = "userId,name,email")
    List<User> findAllWithProjection();
}
```

**Important:** Unspecified attributes become null, including hash and range key attributes.

**Constraints:**
- When using Global Secondary Indexes, the index must contain desired fields (either through SELECT or ALL projection type)

#### Limit Results

```java
@Query(limit = 10)
List<User> findTop10ByName(String name);
```

#### Consistent Reads

```java
import org.socialsignin.spring.data.dynamodb.repository.QueryConstants.ConsistentReadMode;

@Query(consistentReads = ConsistentReadMode.CONSISTENT)
User findByUserId(String userId);
```

#### Filter Expressions

```java
import org.socialsignin.spring.data.dynamodb.repository.ExpressionAttribute;

@Query(
    filterExpression = "contains(#name, :searchTerm)",
    expressionMappingNames = {
        @ExpressionAttribute(key = "#name", value = "name")
    },
    expressionMappingValues = {
        @ExpressionAttribute(key = ":searchTerm", parameterName = "term")
    }
)
List<User> searchByName(@Param("term") String searchTerm);
```

**Complex Filter Expression:**
```java
@Query(
    filterExpression = "#status = :active AND #age BETWEEN :minAge AND :maxAge",
    expressionMappingNames = {
        @ExpressionAttribute(key = "#status", value = "status"),
        @ExpressionAttribute(key = "#age", value = "age")
    },
    expressionMappingValues = {
        @ExpressionAttribute(key = ":active", value = "ACTIVE"),
        @ExpressionAttribute(key = ":minAge", value = "18"),
        @ExpressionAttribute(key = ":maxAge", value = "65")
    }
)
List<User> findActiveUsersInAgeRange();
```

---

## Batch Operations

### Batch Save

```java
import org.socialsignin.spring.data.dynamodb.exception.BatchWriteException;

try {
    List<User> users = createUsers(100);
    userRepository.saveAll(users);
    // Automatically chunks into 25-item batches
    // Retries up to 8 times with exponential backoff
} catch (BatchWriteException e) {
    // Get entities that failed after retries
    List<User> failedUsers = e.getUnprocessedEntities(User.class);

    logger.error("Failed to save {} users after {} retries",
        failedUsers.size(),
        e.getRetriesAttempted());

    // Send to DLQ or retry later
    dlqService.sendToQueue("user-writes-dlq", failedUsers);
}
```

### Batch Load

```java
List<String> userIds = Arrays.asList("id1", "id2", "id3", ..., "id100");

// Automatically chunks into 100-item batches
List<User> users = userRepository.findAllById(userIds);
```

### Batch Delete

```java
import org.socialsignin.spring.data.dynamodb.exception.BatchDeleteException;

try {
    List<User> usersToDelete = getUsersToDelete();
    userRepository.deleteAll(usersToDelete);
} catch (BatchDeleteException e) {
    List<User> failedDeletes = e.getUnprocessedEntities(User.class);
    logger.error("Failed to delete {} users", failedDeletes.size());
}
```

### Performance Characteristics

| Operation | Batch Size | Auto-Retry | Typical Use Case |
|-----------|------------|------------|------------------|
| `saveAll()` | 25 items | Yes (8x) | Bulk inserts/updates |
| `findAllById()` | 100 items | No | Bulk reads |
| `deleteAll()` | 25 items | Yes (8x) | Bulk deletes |

---

## Type Converters

### Built-in Converters

```java
import org.socialsignin.spring.data.dynamodb.marshaller.*;

@DynamoDbBean
public class Event {

    // Instant -> ISO-8601 String
    @DynamoDbConvertedBy(Instant2IsoAttributeConverter.class)
    private Instant timestamp;

    // Instant -> Epoch Number
    @DynamoDbConvertedBy(Instant2EpocheAttributeConverter.class)
    private Instant eventTime;

    // Date -> ISO-8601 String
    @DynamoDbConvertedBy(Date2IsoAttributeConverter.class)
    private Date createdDate;

    // Date -> Epoch Number
    @DynamoDbConvertedBy(Date2EpocheAttributeConverter.class)
    private Date modifiedDate;
}
```

### Custom Converter

**1. Create Converter:**
```java
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

public class AddressConverter implements AttributeConverter<Address> {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public AttributeValue transformFrom(Address address) {
        if (address == null) {
            return null;
        }
        try {
            String json = mapper.writeValueAsString(address);
            return AttributeValue.builder().s(json).build();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize Address", e);
        }
    }

    @Override
    public Address transformTo(AttributeValue attributeValue) {
        if (attributeValue == null || attributeValue.s() == null) {
            return null;
        }
        try {
            return mapper.readValue(attributeValue.s(), Address.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize Address", e);
        }
    }

    @Override
    public EnhancedType<Address> type() {
        return EnhancedType.of(Address.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.S;  // Stored as String
    }
}
```

**2. Use Converter:**
```java
@DynamoDbBean
public class Customer {

    private String customerId;
    private Address shippingAddress;
    private Address billingAddress;

    public Customer() {}

    @DynamoDbPartitionKey
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    @DynamoDbConvertedBy(AddressConverter.class)
    public Address getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(Address shippingAddress) { this.shippingAddress = shippingAddress; }

    @DynamoDbConvertedBy(AddressConverter.class)
    public Address getBillingAddress() { return billingAddress; }
    public void setBillingAddress(Address billingAddress) { this.billingAddress = billingAddress; }
}
```

### Enum Converter

```java
public enum OrderStatus {
    PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED
}

public class OrderStatusConverter implements AttributeConverter<OrderStatus> {

    @Override
    public AttributeValue transformFrom(OrderStatus status) {
        return status == null ? null :
            AttributeValue.builder().s(status.name()).build();
    }

    @Override
    public OrderStatus transformTo(AttributeValue attributeValue) {
        return attributeValue == null || attributeValue.s() == null ? null :
            OrderStatus.valueOf(attributeValue.s());
    }

    @Override
    public EnhancedType<OrderStatus> type() {
        return EnhancedType.of(OrderStatus.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.S;
    }
}
```

---

## Auto-Generated Keys & Timestamps

### Auto-Generated UUID

There are two approaches for auto-generating UUIDs:

#### Option 1: AWS SDK v2 Native Extension (Recommended for SDK_V2_NATIVE mode)

**Note:** This option requires defining your own `DynamoDbEnhancedClient` bean with the extension. Normally, you don't need to define this bean.

**1. Configure Extension:**
```java
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.extensions.AutoGeneratedUuidExtension;

@Bean
public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
    return DynamoDbEnhancedClient.builder()
        .dynamoDbClient(dynamoDbClient)
        .extensions(AutoGeneratedUuidExtension.create())  // Required for UUID generation
        .build();
}
```

**2. Entity:**
```java
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbAutoGeneratedUuid;

@DynamoDbBean
public class Product {

    private String productId;
    private String name;

    public Product() {}

    @DynamoDbPartitionKey
    @DynamoDbAutoGeneratedUuid  // Automatically generated UUID
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    @DynamoDbAttribute("name")
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
```

#### Option 2: Library Annotation (SDK_V1_COMPATIBLE mode only)

**Note:** Only available in `SDK_V1_COMPATIBLE` mode for backward compatibility.

```java
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import org.socialsignin.spring.data.dynamodb.annotation.DynamoDBAutoGeneratedKey;

@DynamoDbBean
public class Product {

    private String productId;
    private String name;

    public Product() {}

    @DynamoDbPartitionKey
    @DynamoDBAutoGeneratedKey  // Auto-generated UUID (SDK_V1_COMPATIBLE only)
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    @DynamoDbAttribute("name")
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
```

### Auto-Generated Timestamps

**Note:** Only available in `SDK_V1_COMPATIBLE` mode.

```java
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import org.socialsignin.spring.data.dynamodb.annotation.DynamoDBAutoGeneratedTimestamp;
import org.socialsignin.spring.data.dynamodb.annotation.DynamoDBAutoGenerateStrategy;

@DynamoDbBean
public class AuditEntity {

    private String id;
    private Date createdAt;
    private Date updatedAt;

    public AuditEntity() {}

    @DynamoDbPartitionKey
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    @DynamoDBAutoGeneratedTimestamp(strategy = DynamoDBAutoGenerateStrategy.CREATE)
    public Date getCreatedAt() { return createdAt; }  // Set only on create
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    @DynamoDBAutoGeneratedTimestamp(strategy = DynamoDBAutoGenerateStrategy.ALWAYS)
    public Date getUpdatedAt() { return updatedAt; }  // Set on create and update
    public void setUpdatedAt(Date updatedAt) { this.updatedAt = updatedAt; }
}
```

### Spring Data Auditing (Recommended)

**1. Enable Auditing:**
```java
import org.socialsignin.spring.data.dynamodb.config.EnableDynamoDBAuditing;

@Configuration
@EnableDynamoDBAuditing
public class DynamoDBConfig {
    // ...
}
```

**2. Entity:**
```java
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import java.time.Instant;

@DynamoDbBean
public class AuditedEntity {

    private String id;
    private Instant createdAt;
    private Instant updatedAt;

    public AuditedEntity() {}

    @DynamoDbPartitionKey
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    @CreatedDate
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @LastModifiedDate
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
```

---

## Event Listeners

### Built-in Event Types

- `BeforeSaveEvent` - Before entity is saved
- `AfterSaveEvent` - After entity is saved
- `BeforeDeleteEvent` - Before entity is deleted
- `AfterDeleteEvent` - After entity is deleted
- `AfterLoadEvent` - After entity is loaded
- `AfterQueryEvent` - After query execution
- `AfterScanEvent` - After scan execution

### Creating an Event Listener

```java
import org.socialsignin.spring.data.dynamodb.mapping.event.AbstractDynamoDBEventListener;
import org.springframework.stereotype.Component;

@Component
public class UserEventListener extends AbstractDynamoDBEventListener<User> {

    @Override
    public void onBeforeSave(User user) {
        logger.info("Saving user: {}", user.getUserId());
        // Validate or modify before save
    }

    @Override
    public void onAfterSave(User user) {
        logger.info("User saved: {}", user.getUserId());
        // Send notification, update cache, etc.
    }

    @Override
    public void onBeforeDelete(User user) {
        logger.info("Deleting user: {}", user.getUserId());
        // Cleanup related data
    }

    @Override
    public void onAfterLoad(User user) {
        logger.debug("User loaded: {}", user.getUserId());
        // Post-load processing
    }
}
```

### Validation Event Listener

```java
import org.socialsignin.spring.data.dynamodb.mapping.event.ValidatingDynamoDBEventListener;
import jakarta.validation.Validator;

@Component
public class ValidationListener extends ValidatingDynamoDBEventListener {

    public ValidationListener(Validator validator) {
        super(validator);
    }
}
```

**Entity with Validation:**
```java
import jakarta.validation.constraints.*;

@DynamoDbBean
public class User {

    @DynamoDbPartitionKey
    @NotNull
    private String userId;

    @NotBlank
    @Size(min = 2, max = 100)
    private String name;

    @Email
    private String email;

    @Min(18)
    @Max(150)
    private Integer age;
}
```

---

## Pagination

### Basic Pagination

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public interface UserRepository extends DynamoDBPagingAndSortingRepository<User, String> {

    @EnableScan
    @EnableScanCount
    Page<User> findByStatus(String status, Pageable pageable);
}
```

**Usage:**
```java
// Page 1, 20 items per page
Pageable pageable = PageRequest.of(0, 20);
Page<User> page = userRepository.findByStatus("ACTIVE", pageable);

System.out.println("Total elements: " + page.getTotalElements());
System.out.println("Total pages: " + page.getTotalPages());
System.out.println("Current page: " + page.getNumber());
System.out.println("Has next: " + page.hasNext());

for (User user : page.getContent()) {
    System.out.println(user.getName());
}
```

### Pagination with Hash Key Query

```java
public interface OrderRepository extends DynamoDBPagingAndSortingRepository<Order, String> {

    Page<Order> findByCustomerId(String customerId, Pageable pageable);
}
```

### Query Size Limits and Pageable

The AWS SDK tries to load all results of a query into memory as one single list. To handle memory limitations, enable lazy loading:

**Lazy Loading Configuration:**
```java
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClientExtension;

@Bean
public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
    return DynamoDbEnhancedClient.builder()
        .dynamoDbClient(dynamoDbClient)
        // Configure pagination behavior through request settings
        .build();
}
```

With SDK v2, pagination is handled differently. Use the repository's Pageable support:

```java
public interface UserRepository extends DynamoDBPagingAndSortingRepository<User, String> {
    Page<User> findByStatus(String status, Pageable pageable);
}

// Usage
Pageable pageable = PageRequest.of(0, 100);  // First page, 100 items
Page<User> page = userRepository.findByStatus("ACTIVE", pageable);

// Iterate through pages
while (page.hasNext()) {
    pageable = page.nextPageable();
    page = userRepository.findByStatus("ACTIVE", pageable);
}
```

**Note:** Offset-based pagination requires scanning through items, which can be expensive for large offsets.

---

## DynamoDB Operations Template

For direct access to DynamoDB operations beyond repository methods.

### Inject DynamoDBOperations

```java
import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;

@Service
public class UserService {

    @Autowired
    private DynamoDBOperations dynamoDBOperations;
}
```

### Load Operations

```java
// Load by hash key
User user = dynamoDBOperations.load(User.class, "userId123");

// Load by hash + range key
Order order = dynamoDBOperations.load(Order.class, "customerId", "2024-01-15");

// Batch load
Map<Class<?>, List<Key>> itemsToGet = new HashMap<>();
itemsToGet.put(User.class, Arrays.asList(
    Key.builder().partitionValue("id1").build(),
    Key.builder().partitionValue("id2").build()
));
List<User> users = dynamoDBOperations.batchLoad(itemsToGet);
```

### Query Operations

```java
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

// Build custom query
QueryEnhancedRequest query = QueryEnhancedRequest.builder()
    .queryConditional(QueryConditional.keyEqualTo(
        Key.builder().partitionValue("customerId123").build()))
    .limit(10)
    .build();

PageIterable<Order> results = dynamoDBOperations.query(Order.class, query);
```

### Scan Operations

```java
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;

ScanEnhancedRequest scan = ScanEnhancedRequest.builder()
    .limit(50)
    .build();

PageIterable<User> results = dynamoDBOperations.scan(User.class, scan);
```

### Count Operations

```java
// Query count
int orderCount = dynamoDBOperations.count(Order.class, queryRequest);

// Scan count
int userCount = dynamoDBOperations.count(User.class, scanRequest);
```

---

## Operational Features

### Alter Table Name During Runtime

By default, table names are defined through entity class annotations. You can override this dynamically:

**Configuration with TableNameResolver:**
```java
import org.socialsignin.spring.data.dynamodb.core.TableNameResolver;

@Configuration
@EnableDynamoDBRepositories(basePackages = "com.acme.repository")
public class DynamoDBConfig {

    @Value("${app.environment:dev}")
    private String environment;

    @Bean
    public TableNameResolver tableNameResolver() {
        return (domainClass, baseTableName) -> {
            // Add environment prefix to all table names
            return environment + "_" + baseTableName;
        };
    }
}
```

**Alternative: Single Table Override:**
```java
import org.socialsignin.spring.data.dynamodb.core.TableNameResolver;

@Bean
public TableNameResolver tableNameResolver() {
    return new TableNameResolver() {
        @Override
        public <T> String resolveTableName(Class<T> domainClass, String baseTableName) {
            return "my_single_table";
        }
    };
}
```

**Important:** Use `new DynamoDBMapperConfig.Builder()` (with DEFAULT configuration) rather than `DynamoDBMapperConfig.builder()`, which sets fields to null and causes potential exceptions.

### Multi-Repository Configuration

When using multiple repository factories (e.g., JPA and DynamoDB) with Spring Data, explicitly specify which repositories each framework manages:

**Spring Boot Implementation:**
```java
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@EnableJpaRepositories(includeFilters = {
    @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {CustomerRepository.class})
})
@EnableDynamoDBRepositories(includeFilters = {
    @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = {DeviceValueRepository.class})
})
public class Application {
    // ...
}
```

**Configuration Class Alternative:**
```java
@Configuration
@EnableJpaRepositories(
    basePackages = "com.example.jpa",
    excludeFilters = @Filter(type = FilterType.ASSIGNABLE_TYPE,
                             classes = DynamoDBCrudRepository.class)
)
@EnableDynamoDBRepositories(
    basePackages = "com.example.dynamodb"
)
public class AppConfig {
    // ...
}
```

**Key Points:**
- Use `includeFilters`, `basePackages`, or `excludeFilters` for repository assignment
- This clarifies responsibility boundaries between different repository types
- Also controls which entities get registered with each framework

### Spring Data REST Integration

Spring-data-dynamodb is compatible with Spring Data REST. The library uses a `PersistentEntityResourceAssembler` which requires the `DynamoDBMappingContext` to be exposed as a Spring Bean.

**Configuration:**
```java
import org.socialsignin.spring.data.dynamodb.mapping.DynamoDBMappingContext;

@Configuration
@EnableDynamoDBRepositories(
    basePackages = "com.example.repository",
    mappingContextRef = "dynamoDBMappingContext"
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

    @Bean
    public DynamoDBMappingContext dynamoDBMappingContext() {
        return new DynamoDBMappingContext();
    }
}
```

**Important:** Even if a `DynamoDBMappingContext` bean already exists in the `ApplicationContext`, it must be explicitly registered via the `mappingContextRef` parameter.

### Amazon DynamoDB Accelerator (DAX)

Amazon DynamoDB Accelerator (DAX) is a fully managed, highly available, in-memory cache for DynamoDB that delivers up to 10x performance improvement.

`spring-data-dynamodb` maintains no entity state information - only metadata about entity classes gets cached. Since most classes function as Spring Bean singletons without shared state, DAX integration poses no compatibility issues.

**Note:** DAX integration with SDK v2 uses the `dax-java-sdk` v2 client. Configuration follows standard AWS SDK v2 patterns:

**Add Dependency (Maven):**
```xml
<dependency>
    <groupId>software.amazon.dax</groupId>
    <artifactId>amazon-dax-client</artifactId>
    <version>2.0.4</version>
</dependency>
```

**Configuration:**
```java
import software.amazon.dax.ClusterDaxClient;

@Configuration
@EnableDynamoDBRepositories(basePackages = "com.example.repository")
public class DaxConfig {

    @Value("${amazon.dax.endpoint}")
    private String daxEndpoint;

    @Bean
    public DynamoDbClient amazonDynamoDB() {
        // Use DAX client instead of standard DynamoDB client
        return ClusterDaxClient.builder()
            .overrideConfiguration(c -> c.addMetricPublisher(/* ... */))
            .endpointConfiguration(daxEndpoint)
            .region(Region.US_EAST_1)
            .build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient daxClient) {
        return DynamoDbEnhancedClient.builder()
            .dynamoDbClient(daxClient)
            .build();
    }
}
```

**Required Properties:**
- `amazon.dax.endpoint`: Your DAX cluster endpoint (found in AWS DynamoDB console)

### Autocreate Tables

This feature enables automatic table creation during application startup, inspired by Hibernate's autocreate functionality.

**Configuration Properties:**
```properties
spring.data.dynamodb.entity2ddl.auto=none
spring.data.dynamodb.entity2ddl.gsiProjectionType=ALL
spring.data.dynamodb.entity2ddl.readCapacity=10
spring.data.dynamodb.entity2ddl.writeCapacity=1
```

**Available Modes:**

| Mode | Description |
|------|-------------|
| `none` | No action performed (default) |
| `create-only` | Database creation will be generated on ApplicationContext startup |
| `drop` | Schema removed when the application context shuts down |
| `create` | Drops and recreates the schema on startup |
| `create-drop` | Removes and rebuilds the schema on startup; also drops it on shutdown |
| `validate` | Verifies the database schema structure during startup (capacity checks are excluded) |

**Creation Defaults:**
- All required Global Secondary Indexes (GSIs) use the configured projection type
- All GSIs use specified read/write capacity values

---

## Access to Releases

### Maven Central (Releases)

Regular releases are available via Maven Central with no additional setup required:

```xml
<dependency>
    <groupId>io.github.prasanna0586</groupId>
    <artifactId>spring-data-dynamodb</artifactId>
    <version>7.0.0</version>
</dependency>
```

### Sonatype Central Snapshots

Snapshot releases are built from the `master` branch when PRs merge. These are suitable for testing but not production-ready.

**Maven Configuration (pom.xml):**
```xml
<repositories>
  <repository>
    <id>central-snapshots</id>
    <url>https://central.sonatype.com/repository/maven-snapshots/</url>
    <releases><enabled>false</enabled></releases>
    <snapshots><enabled>true</enabled></snapshots>
  </repository>
</repositories>
```

**Maven Configuration (~/.m2/settings.xml - Recommended):**
```xml
<profiles>
  <profile>
     <id>allow-snapshots</id>
     <activation><activeByDefault>true</activeByDefault></activation>
     <repositories>
       <repository>
         <id>central-snapshots</id>
         <url>https://central.sonatype.com/repository/maven-snapshots/</url>
         <releases><enabled>false</enabled></releases>
         <snapshots><enabled>true</enabled></snapshots>
       </repository>
     </repositories>
   </profile>
</profiles>
```

**Gradle Configuration:**
```gradle
repositories {
    mavenCentral()  // For releases: https://repo.maven.org/maven2
    maven {
        url 'https://central.sonatype.com/repository/maven-snapshots/'  // For snapshots
    }
}
```

---

## Performance Optimization

### 1. Use Query Instead of Scan

**Inefficient (Scan):**
```java
@EnableScan
List<User> findByEmail(String email);  // Scans entire table
```

**Efficient (Query with GSI):**
```java
// Add GSI on email
@DynamoDbSecondaryPartitionKey(indexNames = {"email-index"})
private String email;

// No @EnableScan needed - uses query
List<User> findByEmail(String email);
```

### 2. Projection to Reduce Data Transfer

```java
@Query(fields = "userId,name,email")
List<User> findAllBasicInfo();  // Only fetches 3 attributes
```

### 3. Use Batch Operations

**Inefficient:**
```java
for (String id : userIds) {
    User user = userRepository.findById(id).orElse(null);
}
```

**Efficient:**
```java
List<User> users = userRepository.findAllById(userIds);
```

### 4. Limit Results

```java
@Query(limit = 100)
List<User> findRecent();
```

### 5. Use Consistent Reads Sparingly

```java
// Eventual consistency (faster, cheaper)
@Query(consistentReads = ConsistentReadMode.EVENTUAL)
List<User> findByStatus(String status);

// Strong consistency (slower, more expensive)
@Query(consistentReads = ConsistentReadMode.CONSISTENT)
User findByUserId(String userId);
```

### 6. Index Strategy

Design indexes based on query patterns:

```java
@DynamoDbBean
public class Order {

    private String customerId;
    private String orderDate;
    private String status;
    private String createdDate;
    private Double amount;

    public Order() {}

    @DynamoDbPartitionKey  // Main table hash key
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    @DynamoDbSortKey  // Main table range key
    public String getOrderDate() { return orderDate; }
    public void setOrderDate(String orderDate) { this.orderDate = orderDate; }

    // GSI for querying by status
    @DynamoDbSecondaryPartitionKey(indexNames = {"status-date-index"})
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @DynamoDbSecondarySortKey(indexNames = {"status-date-index"})
    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }

    // LSI for querying by amount
    @DynamoDbSecondarySortKey(indexNames = {"customer-amount-index"})
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
}
```

### 7. Batch Write Configuration

```java
@Bean
public BatchWriteRetryConfig batchWriteRetryConfig() {
    return new BatchWriteRetryConfig.Builder()
        .maxRetries(8)
        .baseDelayMs(100L)
        .maxDelayMs(20000L)
        .useJitter(true)
        .build();
}
```

### 8. Connection Pooling

```java
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import java.time.Duration;

@Bean
public DynamoDbClient amazonDynamoDB() {
    return DynamoDbClient.builder()
        .region(Region.US_EAST_1)
        .httpClientBuilder(ApacheHttpClient.builder()
            .maxConnections(100)  // Adjust based on load
            .connectionTimeout(Duration.ofSeconds(2))
            .socketTimeout(Duration.ofSeconds(30)))
        .build();
}
```

---

## Testing Strategies

### 1. DynamoDB Local with Docker

**docker-compose.yml:**
```yaml
version: '3.8'
services:
  dynamodb-local:
    image: amazon/dynamodb-local:latest
    ports:
      - "8000:8000"
    command: "-jar DynamoDBLocal.jar -sharedDb"
```

**Test Configuration:**
```java
@TestConfiguration
@Profile("test")
public class DynamoDBTestConfig {

    @Bean
    public DynamoDbClient amazonDynamoDB() {
        return DynamoDbClient.builder()
            .endpointOverride(URI.create("http://localhost:8000"))
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("dummy", "dummy")))
            .build();
    }
}
```

### 2. Testcontainers

**Maven Dependency:**
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
```

**Test with Testcontainers:**
```java
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@Testcontainers
class UserRepositoryIntegrationTest {

    @Container
    static GenericContainer<?> dynamoDb = new GenericContainer<>(
            DockerImageName.parse("amazon/dynamodb-local:latest"))
            .withExposedPorts(8000);

    @DynamicPropertySource
    static void dynamoDbProperties(DynamicPropertyRegistry registry) {
        registry.add("aws.dynamodb.endpoint",
            () -> "http://" + dynamoDb.getHost() + ":" + dynamoDb.getMappedPort(8000));
    }

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        // Create table before each test
        createTable();
    }

    @Test
    void testSaveAndFind() {
        User user = new User();
        user.setUserId("test-123");
        user.setName("Test User");

        userRepository.save(user);

        Optional<User> found = userRepository.findById("test-123");
        assertTrue(found.isPresent());
        assertEquals("Test User", found.get().getName());
    }
}
```

### 3. Table Creation for Tests

```java
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

@Component
@Profile("test")
public class TableCreator {

    @Autowired
    private DynamoDbClient dynamoDbClient;

    public void createUserTable() {
        CreateTableRequest request = CreateTableRequest.builder()
            .tableName("User")
            .keySchema(
                KeySchemaElement.builder()
                    .attributeName("userId")
                    .keyType(KeyType.HASH)
                    .build())
            .attributeDefinitions(
                AttributeDefinition.builder()
                    .attributeName("userId")
                    .attributeType(ScalarAttributeType.S)
                    .build())
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .build();

        try {
            dynamoDbClient.createTable(request);

            // Wait for table to be active
            dynamoDbClient.waiter().waitUntilTableExists(
                DescribeTableRequest.builder().tableName("User").build());
        } catch (ResourceInUseException e) {
            // Table already exists
        }
    }
}
```

### 4. Integration Test Example

```java
@SpringBootTest
@ActiveProfiles("test")
class OrderRepositoryIntegrationTest {

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @Test
    void testFindByCustomerId() {
        // Given
        Order order1 = createOrder("customer-1", "2024-01-15", 100.0);
        Order order2 = createOrder("customer-1", "2024-01-16", 200.0);
        Order order3 = createOrder("customer-2", "2024-01-15", 150.0);

        orderRepository.saveAll(Arrays.asList(order1, order2, order3));

        // When
        List<Order> orders = orderRepository.findByCustomerId("customer-1");

        // Then
        assertEquals(2, orders.size());
        assertTrue(orders.stream()
            .allMatch(o -> o.getCustomerId().equals("customer-1")));
    }

    @Test
    void testBatchOperations() {
        // Given
        List<Order> orders = IntStream.range(0, 50)
            .mapToObj(i -> createOrder("customer-" + i, "2024-01-15", i * 10.0))
            .collect(Collectors.toList());

        // When
        orderRepository.saveAll(orders);

        // Then
        long count = orderRepository.count();
        assertEquals(50, count);
    }

    private Order createOrder(String customerId, String orderDate, Double amount) {
        Order order = new Order();
        order.setCustomerId(customerId);
        order.setOrderDate(orderDate);
        order.setAmount(amount);
        return order;
    }
}
```

### Composite Primary Keys Kotlin Example

For Kotlin users, here's an example of modeling DynamoDB HASH/RANGE partition keys:

**Composite Key Class (Kotlin):**
```kotlin
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*

@DynamoDbBean
data class FoobarEntryId(
    @get:DynamoDbPartitionKey
    @get:DynamoDbAttribute("foobarCode")
    var foobarCode: String = "",

    @get:DynamoDbSortKey
    @get:DynamoDbAttribute("foobarDate")
    var foobarDate: String = ""
)
```

**Entity Class (Kotlin):**
```kotlin
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*
import org.springframework.data.annotation.Id

@DynamoDbBean
data class FoobarEntry(
    @Id
    var id: FoobarEntryId = FoobarEntryId(),

    @get:DynamoDbAttribute("foobarValue")
    var foobarValue: String = ""
) {
    @DynamoDbPartitionKey
    @DynamoDbAttribute("foobarCode")
    fun getFoobarCode(): String = id.foobarCode

    fun setFoobarCode(foobarCode: String) {
        id = id.copy(foobarCode = foobarCode)
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("foobarDate")
    fun getFoobarDate(): String = id.foobarDate

    fun setFoobarDate(foobarDate: String) {
        id = id.copy(foobarDate = foobarDate)
    }
}
```

**Repository (Kotlin):**
```kotlin
import org.socialsignin.spring.data.dynamodb.repository.DynamoDBCrudRepository

interface FoobarRepository : DynamoDBCrudRepository<FoobarEntry, FoobarEntryId> {
    fun findAllByFoobarCode(foobarCode: String): List<FoobarEntry>
}
```

**Test Example (Kotlin with JUnit 5):**
```kotlin
@SpringBootTest
@Testcontainers
class FoobarRepositoryIntegrationTest {

    @Container
    companion object {
        val dynamoDb = GenericContainer(DockerImageName.parse("amazon/dynamodb-local:latest"))
            .withExposedPorts(8000)
    }

    @Autowired
    lateinit var foobarRepository: FoobarRepository

    @Test
    fun `should save and query composite key entries`() {
        // Given
        val entry = FoobarEntry(
            id = FoobarEntryId("CODE001", "2024-01-15"),
            foobarValue = "Test Value"
        )

        // When
        foobarRepository.save(entry)
        val found = foobarRepository.findAllByFoobarCode("CODE001")

        // Then
        assertEquals(1, found.size)
        assertEquals("Test Value", found[0].foobarValue)
        assertEquals("CODE001", found[0].id.foobarCode)
        assertEquals("2024-01-15", found[0].id.foobarDate)
    }
}
```

---

## Best Practices

### 1. Design for Your Access Patterns

```java
// Design entity and indexes based on queries you need
@DynamoDbBean
public class Product {

    private String productId;
    private String category;
    private String sellerId;

    public Product() {}

    @DynamoDbPartitionKey  // Access by ID
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    @DynamoDbSecondaryPartitionKey(indexNames = {"category-index"})  // Query by category
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    @DynamoDbSecondaryPartitionKey(indexNames = {"seller-index"})  // Query by seller
    public String getSellerId() { return sellerId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }
}
```

### 2. Use Composite Keys Wisely

```java
// Good: Related data under same partition key
// In the getter method:
@DynamoDbPartitionKey  // Partition by customer
public String getCustomerId() { return customerId; }

@DynamoDbSortKey  // Sort by date
public String getOrderDate() { return orderDate; }

// This enables efficient queries like:
// - All orders for a customer
// - Orders in a date range for a customer
```

### 3. Handle Exceptions Properly

```java
try {
    userRepository.save(user);
} catch (ConditionalCheckFailedException e) {
    // Optimistic locking failure
    throw new ConcurrentModificationException("User was modified by another process");
} catch (ProvisionedThroughputExceededException e) {
    // Rate limiting - implement retry logic
    logger.warn("Throughput exceeded, retrying...");
    Thread.sleep(backoff);
    userRepository.save(user);
}
```

### 4. Use Optimistic Locking

```java
import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;

@DynamoDbBean
public class Account {

    private String accountId;
    private Double balance;
    private Long version;

    public Account() {}

    @DynamoDbPartitionKey
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public Double getBalance() { return balance; }
    public void setBalance(Double balance) { this.balance = balance; }

    @DynamoDbVersionAttribute  // Prevents concurrent modifications
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
```

### 5. Monitor Costs

```java
// Use projection to reduce data transfer costs
@Query(fields = "id,name")
List<User> findAllNamesOnly();

// Use GSI instead of scan to reduce read costs - on getter method:
@DynamoDbSecondaryPartitionKey(indexNames = {"status-index"})
public String getStatus() { return status; }
```

### 6. Implement Retry Logic for Batch Operations

```java
public void saveBatchWithRetry(List<User> users) {
    try {
        userRepository.saveAll(users);
    } catch (BatchWriteException e) {
        List<User> failed = e.getUnprocessedEntities(User.class);

        if (e.getRetriesAttempted() >= 8 && !failed.isEmpty()) {
            // Send to DLQ for manual intervention
            dlqService.sendToQueue("user-writes-dlq", failed);
            metricsService.incrementCounter("batch.write.failed", failed.size());
        }
    }
}
```

### 7. Use Table Name Resolver for Multi-Environment

```java

    @Value("${app.environment.prefix}")
    private String environmentPrefix;

    @Bean
    public TableNameResolver tableNameResolver() {
        return new TableNameResolver() {
            @Override
            public <T> String resolveTableName(Class<T> domainClass, String baseTableName) {
                return environmentPrefix != null ? environmentPrefix + "_" + baseTableName : baseTableName;
            }
        };
    }
```

---

## Troubleshooting

### Issue: ResourceNotFoundException

**Error:**
```
ResourceNotFoundException: Requested resource not found: Table: User not found
```

**Solution:**
- Ensure table exists in DynamoDB
- Verify table name matches entity class name (or use custom TableNameResolver)
- For tests, create table before running tests

### Issue: ValidationException on Boolean

**Error:**
```
ValidationException: One or more parameter values were invalid: Incorrect Binary Data
```

**Solution:**
Use native DynamoDB boolean type (default in SDK_V2_NATIVE):
```java
@EnableDynamoDBRepositories(
    marshallingMode = MarshallingMode.SDK_V2_NATIVE  // Default
)
```

### Issue: ConditionalCheckFailedException

**Error:**
```
ConditionalCheckFailedException: The conditional request failed
```

**Solution:**
This occurs with optimistic locking. Reload entity and retry:
```java
try {
    userRepository.save(user);
} catch (ConditionalCheckFailedException e) {
    User latestUser = userRepository.findById(user.getId()).orElseThrow();
    // Apply changes to latest version
    latestUser.setName(user.getName());
    userRepository.save(latestUser);
}
```

### Issue: ProvisionedThroughputExceededException

**Error:**
```
ProvisionedThroughputExceededException: The level of configured provisioned throughput for the table was exceeded
```

**Solution:**
- Increase table capacity
- Use batch operations to reduce request count
- Implement exponential backoff retry
- Consider on-demand billing mode

### Issue: ItemCollectionSizeLimitExceededException

**Error:**
```
ItemCollectionSizeLimitExceededException: Item collection size limit of 10 GB exceeded
```

**Solution:**
- Too much data under single partition key (10 GB limit)
- Redesign data model to distribute across more partitions
- Consider composite keys or different partitioning strategy

### Issue: Query Not Using Index

**Symptoms:** Slow queries, high read costs

**Solution:**
Check if query matches index structure:
```java
// Entity with GSI
@DynamoDbSecondaryPartitionKey(indexNames = {"status-index"})
private String status;

// This uses the index (efficient)
List<User> findByStatus(String status);

// This does NOT use the index (inefficient)
@EnableScan
List<User> findByName(String name);
```

### Enable Debug Logging

```yaml
logging:
  level:
    org.socialsignin.spring.data.dynamodb: DEBUG
    software.amazon.awssdk: DEBUG
    software.amazon.awssdk.request: DEBUG
```

---

## GraalVM Native Image Support

Spring Data DynamoDB 7.x supports GraalVM native image compilation out of the box. The library automatically registers reflection hints for DynamoDB entities and repositories during AOT processing.

### How It Works

The library includes:
- `DynamoDbRepositoryRegistrationAotProcessor` - Discovers entities and repositories at build time
- `DynamoDbRuntimeHints` - Registers reflection hints for AWS SDK and Spring Data classes

These are automatically activated via `META-INF/spring/aot.factories`.

### Building a Native Image

**Maven:**
```bash
mvn -Pnative native:compile
```

**Gradle:**
```bash
gradle nativeCompile
```

### Running Native Tests

```bash
# Start DynamoDB Local
docker run -d -p 8000:8000 amazon/dynamodb-local -jar DynamoDBLocal.jar -inMemory

# Run native tests
mvn -PnativeTest test
```

### Example Native Test

```java
@SpringBootTest
@EnabledInNativeImage
class DocumentRepositoryNativeTest {

    @Autowired
    private DocumentRepository repository;

    @Test
    void testSaveAndFind() {
        Document doc = new Document();
        doc.setId("native-test-1");
        doc.setContent("Hello from native!");

        repository.save(doc);

        Optional<Document> found = repository.findById("native-test-1");
        assertThat(found).isPresent();
    }
}
```

---

## Example Project

A complete working example demonstrating Spring Data DynamoDB with AWS SDK v2 is available:

**[validate-spring-data-dynamodb](https://github.com/prasanna0586/validate-spring-data-dynamodb/tree/spring-data-dynamodb-7.0.0-sdk-v2-migration)**

This example project demonstrates:
- Complete SDK v2 entity with multiple GSI annotations
- Optimistic locking with `@DynamoDbVersionAttribute`
- Custom `AttributeConverter` implementation for `Instant`
- Custom repository implementation using `DynamoDBOperations`
- `@Query` annotation with filter expressions and parameter binding
- `TableNameResolver` for environment-based table naming
- Integration tests with Testcontainers (75 tests)

---

## Additional Resources

- [AWS DynamoDB Developer Guide](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/)
- [AWS SDK for Java 2.x](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/)
- [DynamoDB Enhanced Client](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/dynamodb-enhanced-client.html)
- [Spring Data Commons](https://docs.spring.io/spring-data/commons/docs/current/reference/html/)
- [GitHub Repository](https://github.com/prasanna0586/spring-data-dynamodb)
- [Issue Tracker](https://github.com/prasanna0586/spring-data-dynamodb/issues)

---

## License

This project is licensed under the Apache License 2.0.

---

**Questions or Issues?** Please [open an issue](https://github.com/prasanna0586/spring-data-dynamodb/issues) on GitHub.
