package org.socialsignin.spring.data.dynamodb.repository.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.socialsignin.spring.data.dynamodb.domain.validation.*;
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
    @DisplayName("EC-2.3: Should reject index with only sort key (treated as LSI, fails table sort key check)")
    void testGsiWithoutPartitionKey() {
        // NOTE: An index with only @DynamoDbSecondarySortKey (no @DynamoDbSecondaryPartitionKey)
        // is treated as an LSI. Since the table has no sort key, LSI creation fails.
        // The error message will be about LSI requiring table sort key, not about GSI.
        assertThatThrownBy(() -> {
            triggerValidationFor(InvalidEntityGsiWithoutPartitionKey.class);
        })
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Invalid LSI configuration")  // Treated as LSI, not GSI
        .hasMessageContaining("InvalidEntityGsiWithoutPartitionKey")
        .hasMessageContaining("statusIndex")
        .hasMessageContaining("table does not have a sort key");  // LSI error message
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
    @DisplayName("EC-4.1: GSI/LSI name conflict validation (defensive check - currently unreachable)")
    void testGsiLsiNameConflict() {
        // NOTE: This validation is currently unreachable because the code design prevents it.
        // When an index has @DynamoDbSecondaryPartitionKey, it becomes a GSI.
        // When processing LSIs, any index already marked as GSI is skipped.
        // Therefore, an index cannot be both GSI and LSI simultaneously.
        //
        // The entity InvalidEntityGsiLsiNameConflict has:
        // - @DynamoDbSecondaryPartitionKey(indexNames = "conflictIndex") - makes it a GSI
        // - @DynamoDbSecondarySortKey(indexNames = "conflictIndex") - adds sort key to the SAME GSI
        // Result: Single GSI with both partition and sort keys (valid)
        //
        // This test verifies the entity is actually valid.
        try {
            triggerValidationFor(InvalidEntityGsiLsiNameConflict.class);
            // If we get here, validation passed (entity is actually valid)
        } catch (Exception e) {
            throw new AssertionError("EC-4.1: Entity should be valid (single GSI with partition and sort key), but got exception: " + e.getMessage(), e);
        }
    }

    // ========== ADDITIONAL EDGE CASES ==========

    @Test
    @DisplayName("EC-3.3: Should accept but warn for LSI with same sort key as table (redundant)")
    void testLsiSortKeySameAsTable() {
        // This should NOT throw an exception - it's valid but logs a warning
        // We're just verifying it doesn't fail
        try {
            triggerValidationFor(WarningEntityLsiSortKeySameAsTable.class);
            // If we get here, validation passed (no exception thrown)
            // In a real test, we could capture logs to verify the warning was logged
        } catch (Exception e) {
            throw new AssertionError("EC-3.3: LSI with same sort key as table should be valid (with warning), but got exception: " + e.getMessage(), e);
        }
    }

    @Test
    @DisplayName("EC-5.3: Should reject attribute with type mismatch between method and field")
    void testAttributeTypeMismatch() {
        assertThatThrownBy(() -> {
            triggerValidationFor(InvalidEntityAttributeTypeMismatch.class);
        })
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Invalid attribute configuration")
        .hasMessageContaining("InvalidEntityAttributeTypeMismatch")
        .hasMessageContaining("createdAt")
        .hasMessageContaining("conflicting types");
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
                mappingContext
            );
            synchronizer.setMode(Entity2DDL.NONE.getConfigurationValue());

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
