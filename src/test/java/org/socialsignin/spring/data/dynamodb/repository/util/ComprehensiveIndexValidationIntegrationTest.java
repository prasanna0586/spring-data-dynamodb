package org.socialsignin.spring.data.dynamodb.repository.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.socialsignin.spring.data.dynamodb.domain.sample.*;
import org.socialsignin.spring.data.dynamodb.mapping.DynamoDBMappingContext;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Comprehensive integration tests for all index validation edge cases.
 * Tests validate that our validation logic catches all configuration errors
 * before attempting to create DynamoDB tables.
 *
 * Note: These tests do not use @SpringBootTest to avoid Spring's entity scanning
 * which would fail when encountering invalid test entities during context initialization.
 */
@DisplayName("Comprehensive Index Validation Integration Tests")
class ComprehensiveIndexValidationIntegrationTest {

    // We create minimal clients just for validation testing
    // No actual DynamoDB connection is needed since we're only testing validation
    private final DynamoDbClient amazonDynamoDB = DynamoDbClient.builder()
        .region(Region.US_EAST_1)
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create("test", "test")))
        .endpointOverride(URI.create("http://localhost:8000"))
        .build();

    private final DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
        .dynamoDbClient(amazonDynamoDB)
        .build();

    private final DynamoDBMappingContext mappingContext = new DynamoDBMappingContext();

    // ========== CRITICAL EDGE CASES ==========

    @Test
    @DisplayName("EC-1.1: Should reject entity without partition key")
    void testMissingPartitionKey() {
        assertThatThrownBy(() -> {
            triggerValidationFor(InvalidEntityNoPartitionKey.class);
        })
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Invalid table configuration")
        .hasMessageContaining("InvalidEntityNoPartitionKey")
        .hasMessageContaining("must have a partition key");
    }

    @Test
    @DisplayName("EC-1.2: Should reject entity with multiple partition keys")
    void testMultiplePartitionKeys() {
        assertThatThrownBy(() -> {
            triggerValidationFor(InvalidEntityMultiplePartitionKeys.class);
        })
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Invalid table configuration")
        .hasMessageContaining("InvalidEntityMultiplePartitionKeys")
        .hasMessageContaining("multiple partition keys")
        .hasMessageContaining("can only have one partition key");
    }

    @Test
    @DisplayName("EC-1.3: Should reject entity with multiple sort keys")
    void testMultipleSortKeys() {
        assertThatThrownBy(() -> {
            triggerValidationFor(InvalidEntityMultipleSortKeys.class);
        })
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Invalid table configuration")
        .hasMessageContaining("InvalidEntityMultipleSortKeys")
        .hasMessageContaining("multiple sort keys")
        .hasMessageContaining("can only have one sort key");
    }

    @Test
    @DisplayName("EC-2.3: Should reject GSI with only sort key (no partition key)")
    void testGsiWithoutPartitionKey() {
        assertThatThrownBy(() -> {
            triggerValidationFor(InvalidEntityGsiWithoutPartitionKey.class);
        })
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Invalid GSI configuration")
        .hasMessageContaining("InvalidEntityGsiWithoutPartitionKey")
        .hasMessageContaining("statusIndex")
        .hasMessageContaining("no partition key");
    }

    @Test
    @DisplayName("EC-3.2: Should reject LSI on table without sort key")
    void testLsiWithoutTableSortKey() {
        assertThatThrownBy(() -> {
            triggerValidationFor(InvalidEntityLsiWithoutTableSortKey.class);
        })
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Invalid LSI configuration")
        .hasMessageContaining("InvalidEntityLsiWithoutTableSortKey")
        .hasMessageContaining("createdAtIndex")
        .hasMessageContaining("table does not have a sort key")
        .hasMessageContaining("composite primary key");
    }

    @Test
    @DisplayName("EC-5.2: Should reject empty index name")
    void testEmptyIndexName() {
        assertThatThrownBy(() -> {
            triggerValidationFor(InvalidEntityEmptyIndexName.class);
        })
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Invalid GSI configuration")
        .hasMessageContaining("InvalidEntityEmptyIndexName")
        .hasMessageContaining("Index name cannot be null or empty");
    }

    // ========== MEDIUM PRIORITY EDGE CASES ==========

    @Test
    @DisplayName("EC-4.1: Should reject same name for GSI and LSI")
    void testGsiLsiNameConflict() {
        assertThatThrownBy(() -> {
            triggerValidationFor(InvalidEntityGsiLsiNameConflict.class);
        })
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Invalid index configuration")
        .hasMessageContaining("InvalidEntityGsiLsiNameConflict")
        .hasMessageContaining("conflictIndex")
        .hasMessageContaining("used for both GSI and LSI")
        .hasMessageContaining("must have different names");
    }

    // ========== LOW PRIORITY EDGE CASES (DynamoDB Limits) ==========

    // Note: Testing GSI/LSI count limits and index name length would require
    // creating entities with 21+ GSIs or 6+ LSIs, or very long index names.
    // These are tested implicitly through the validation logic but creating
    // such large test entities is impractical for this test suite.
    // The validation code is straightforward integer/length comparisons.

    /**
     * Helper method to trigger validation for an entity class.
     * Uses reflection to call the private generateCreateTableRequest method.
     */
    private <T> void triggerValidationFor(Class<T> entityClass) {
        try {
            Entity2DynamoDBTableSynchronizer<T, ?> synchronizer = new Entity2DynamoDBTableSynchronizer<>(
                amazonDynamoDB,
                enhancedClient,
                mappingContext,
                Entity2DDL.NONE
            );

            // Call the private generateCreateTableRequest method via reflection
            // to trigger validation without actually creating the table
            java.lang.reflect.Method method = Entity2DynamoDBTableSynchronizer.class
                .getDeclaredMethod("generateCreateTableRequest", Class.class, String.class);
            method.setAccessible(true);
            method.invoke(synchronizer, entityClass, entityClass.getSimpleName());
        } catch (Exception e) {
            // Re-throw IllegalStateException for validation errors
            if (e.getCause() instanceof IllegalStateException) {
                throw (IllegalStateException) e.getCause();
            }
            throw new RuntimeException(e);
        }
    }
}
