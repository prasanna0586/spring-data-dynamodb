[![Build Status](https://github.com/prasanna0586/spring-data-dynamodb/actions/workflows/runTests.yml/badge.svg)](https://github.com/prasanna0586/spring-data-dynamodb/actions/workflows/runTests.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.prasanna0586/spring-data-dynamodb)](https://central.sonatype.com/artifact/io.github.prasanna0586/spring-data-dynamodb)
[![codecov](https://codecov.io/gh/prasanna0586/spring-data-dynamodb/branch/master/graph/badge.svg)](https://codecov.io/gh/prasanna0586/spring-data-dynamodb)
[![Coverage Report](https://img.shields.io/badge/coverage-report-blue)](https://prasanna0586.github.io/spring-data-dynamodb/coverage/)
[![Last Commit](https://img.shields.io/github/last-commit/prasanna0586/spring-data-dynamodb)](https://github.com/prasanna0586/spring-data-dynamodb/commits/master)

# Spring Data DynamoDB #

Spring Data DynamoDB makes it easy to build Spring-powered applications that use [AWS DynamoDB](https://aws.amazon.com/dynamodb/) for persistence, following the familiar patterns of the [Spring Data](https://projects.spring.io/spring-data/) family.

This module is built on **AWS SDK v2**.

## Supported Features ##

* Implementation of [CRUD methods](https://docs.spring.io/spring-data/commons/docs/current/reference/html/#repositories.definition) for DynamoDB entities
* Dynamic query generation from [query method names](https://docs.spring.io/spring-data/commons/docs/current/reference/html/#repositories.query-methods.query-creation)
* `@Query` annotation with filter expressions
* Transparent handling of Hash and Hash/Range key entities
* Global Secondary Index (GSI) and Local Secondary Index (LSI) support
* Pagination and sorting support
* Projections and [custom repository implementations](https://prasanna0586.github.io/spring-data-dynamodb/docs/#custom-repository-implementations)
* Built-in batch operations with automatic retry and exponential backoff
* Event listeners (BeforeSave, AfterSave, BeforeDelete, AfterDelete, etc.)
* Auditing support via `@EnableDynamoDBAuditing`
* GraalVM native image compatibility
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

    @DynamoDbPartitionKey
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
}
```

Create a CRUD repository interface `UserRepository`:

```java
@EnableScan
public interface UserRepository extends CrudRepository<User, String> {

    List<User> findByLastName(String lastName);

    Optional<User> findByFirstName(String firstName);
}
```

or for paging and sorting...

```java
public interface PagingUserRepository extends PagingAndSortingRepository<User, String> {

    @EnableScan
    Page<User> findByLastName(String lastName, Pageable pageable);

    @EnableScan
    @EnableScanCount
    Page<User> findAll(Pageable pageable);
}
```

Create the configuration class `DynamoDBConfig`:

```java
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

## Documentation ##

For complete documentation, examples, and advanced topics, visit the [User Guide](https://prasanna0586.github.io/spring-data-dynamodb/docs/).

Topics covered include:
* [Configuration options](https://prasanna0586.github.io/spring-data-dynamodb/docs/#configuration)
* [Entity mapping with Hash and Range keys](https://prasanna0586.github.io/spring-data-dynamodb/docs/#entities)
* [Query methods and supported operators](https://prasanna0586.github.io/spring-data-dynamodb/docs/#query-methods)
* [Global and Local Secondary Indexes](https://prasanna0586.github.io/spring-data-dynamodb/docs/#indexes-gsi--lsi)
* [Batch operations](https://prasanna0586.github.io/spring-data-dynamodb/docs/#batch-operations)
* [Type converters](https://prasanna0586.github.io/spring-data-dynamodb/docs/#type-converters)
* [Event listeners](https://prasanna0586.github.io/spring-data-dynamodb/docs/#event-listeners)
* [Pagination](https://prasanna0586.github.io/spring-data-dynamodb/docs/#pagination)

## Migration from SDK v1 ##

If you are migrating from version 6.x (AWS SDK v1) to version 7.x (AWS SDK v2), please refer to the [Migration Guide](MIGRATION_GUIDE.md).

Key changes include:
* New AWS SDK v2 annotations (`@DynamoDbBean`, `@DynamoDbPartitionKey`, etc.)
* Simplified configuration - only `DynamoDbClient` bean required
* Marshalling modes for backward compatibility with existing data

## Example Project ##

A complete working example is available at [validate-spring-data-dynamodb](https://github.com/prasanna0586/validate-spring-data-dynamodb/tree/spring-data-dynamodb-7.0.0-sdk-v2-migration).

This example demonstrates GSI annotations, optimistic locking, custom converters, `@Query` annotations with filter expressions, and integration tests with Testcontainers.

## Version & Spring Framework compatibility ##

The major and minor number of this library refers to the compatible Spring framework version. The build number is used as specified by SEMVER.

| `spring-data-dynamodb` version | Spring Boot compatibility | Spring Framework compatibility | Spring Data compatibility | AWS SDK   |
|--------------------------------|---------------------------|--------------------------------|---------------------------|-----------|
| 6.0.3                          | >= 3.2.5                  | >= 6.1.6                       | 2023.1.5                  | v1        |
| 6.0.4                          | >= 3.5.6                  | >= 6.2.11                      | 2025.0.4                  | v1        |
| 7.0.0                          | >= 3.5.6                  | >= 6.2.11                      | 2025.0.4                  | v2        |

`spring-data-dynamodb` depends directly on `spring-data` as well as `spring-tx`.

`compile` and `runtime` dependencies are kept to a minimum to allow easy integration, for example into Spring Boot projects.

## Development ##

### Running Tests

Tests use [Testcontainers](https://testcontainers.com/) which automatically manages DynamoDB Local. Just ensure Docker is running:

```bash
mvn clean verify
```

### Additional Documentation

* [Releasing to Maven Central](RELEASING.md)
* [Code Coverage](COVERAGE.md)
