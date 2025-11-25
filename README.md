[![Build Status](https://github.com/prasanna0586/spring-data-dynamodb/actions/workflows/runTests.yml/badge.svg)](https://github.com/prasanna0586/spring-data-dynamodb/actions/workflows/runTests.yml)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.prasanna0586/spring-data-dynamodb/badge.svg)](https://search.maven.org/search?q=g:io.github.prasanna0586)
[![codecov](https://codecov.io/gh/prasanna0586/spring-data-dynamodb/branch/master/graph/badge.svg)](https://codecov.io/gh/prasanna0586/spring-data-dynamodb)
[![Coverage Report](https://img.shields.io/badge/coverage-report-blue)](https://prasanna0586.github.io/spring-data-dynamodb/coverage/)

# Spring Data DynamoDB #

The primary goal of the [Spring Data](https://projects.spring.io/spring-data/) project is to make it easier to build Spring-powered applications that use data access technologies.

This module deals with enhanced support for a data access layer built on [AWS DynamoDB](https://aws.amazon.com/dynamodb/) using **AWS SDK v2**.

## Supported Features ##

* Implementation of [CRUD methods](https://docs.spring.io/spring-data/commons/docs/current/reference/html/#repositories.definition) for DynamoDB Entities
* Dynamic query generation from [query method names](https://docs.spring.io/spring-data/commons/docs/current/reference/html/#repositories.query-methods.query-creation)
* Transparent handling of Hash and Hash/Range key entities
* Global Secondary Index (GSI) and Local Secondary Index (LSI) support
* Projections and custom query methods
* Possibility to integrate [custom repository code](https://prasanna0586.github.io/spring-data-dynamodb/#custom-repository-implementations)
* Built-in batch operations with automatic retry and exponential backoff
* Type-safe exception handling for batch operations
* Easy Spring annotation based integration
* REST support via [spring-data-rest](https://projects.spring.io/spring-data-rest/)

## Requirements ##

* **Java 21+**
* Spring Boot 3.x or Spring Framework 6.x
* AWS SDK v2

## Quick Start ##

Download the JAR through [Maven Central](https://mvnrepository.com/artifact/io.github.prasanna0586/spring-data-dynamodb)

```xml
<dependency>
  <groupId>io.github.prasanna0586</groupId>
  <artifactId>spring-data-dynamodb</artifactId>
  <version>7.0.0</version>
</dependency>
```

You also need AWS SDK v2 DynamoDB Enhanced Client:

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
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>dynamodb-enhanced</artifactId>
    </dependency>
</dependencies>
```

Create a DynamoDB entity `User`:

```java
@DynamoDbBean
public class User {

    private String id;
    private String firstName;
    private String lastName;

    public User() {
        // Default constructor is required by AWS DynamoDB SDK
    }

    public User(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("id")
    public String getId() {
        return id;
    }

    @DynamoDbAttribute("firstName")
    public String getFirstName() {
        return firstName;
    }

    @DynamoDbAttribute("lastName")
    public String getLastName() {
        return lastName;
    }

    // setters, hashCode & equals
}
```

Create a CRUD repository interface `UserRepository`:

```java
@EnableScan
public interface UserRepository extends DynamoDBCrudRepository<User, String> {
    List<User> findByLastName(String lastName);
    List<User> findByFirstName(String firstName);
}
```

or for paging and sorting...

```java
public interface PagingUserRepository extends DynamoDBPagingAndSortingRepository<User, String> {
    Page<User> findByLastName(String lastName, Pageable pageable);
    Page<User> findByFirstName(String firstName, Pageable pageable);

    @EnableScan
    @EnableScanCount
    Page<User> findAll(Pageable pageable);
}
```

Create the configuration class `DynamoDBConfig`:

```java
@Configuration
@EnableDynamoDBRepositories(basePackageClasses = UserRepository.class)
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

## Documentation ##

For complete documentation, examples, and advanced topics, visit the [User Guide](https://prasanna0586.github.io/spring-data-dynamodb/).

Topics covered include:
* [Configuration options](https://prasanna0586.github.io/spring-data-dynamodb/#configuration)
* [Entity mapping with Hash and Range keys](https://prasanna0586.github.io/spring-data-dynamodb/#entities)
* [Query methods and supported operators](https://prasanna0586.github.io/spring-data-dynamodb/#query-methods)
* [Global and Local Secondary Indexes](https://prasanna0586.github.io/spring-data-dynamodb/#indexes-gsi--lsi)
* [Batch operations](https://prasanna0586.github.io/spring-data-dynamodb/#batch-operations)
* [Type converters](https://prasanna0586.github.io/spring-data-dynamodb/#type-converters)
* [Event listeners](https://prasanna0586.github.io/spring-data-dynamodb/#event-listeners)
* [Pagination](https://prasanna0586.github.io/spring-data-dynamodb/#pagination)

## Migration from SDK v1 ##

If you are migrating from version 6.x (AWS SDK v1) to version 7.x (AWS SDK v2), please refer to the [Migration Guide](MIGRATION_GUIDE.md).

Key changes include:
* New AWS SDK v2 annotations (`@DynamoDbBean`, `@DynamoDbPartitionKey`, etc.)
* Updated configuration using `DynamoDbClient` and `DynamoDbEnhancedClient`
* Marshalling modes for backward compatibility with existing data

## Version & Spring Framework compatibility ##

The major and minor number of this library refers to the compatible Spring framework version. The build number is used as specified by SEMVER.

| `spring-data-dynamodb` version | Spring Boot compatibility | Spring Framework compatibility | Spring Data compatibility | AWS SDK   |
|--------------------------------|---------------------------|--------------------------------|---------------------------|-----------|
| 6.0.3                          | >= 3.2.5                  | >= 6.1.6                       | 2023.1.5                  | v1        |
| 6.0.4                          | >= 3.5.6                  | >= 6.2.11                      | 2025.0.4                  | v1        |
| 7.0.0                          | >= 3.5.6                  | >= 6.2.11                      | 2025.0.4                  | v2        |

`spring-data-dynamodb` depends directly on `spring-data` as well as `spring-tx`.

`compile` and `runtime` dependencies are kept to a minimum to allow easy integration, for example into Spring Boot projects.
