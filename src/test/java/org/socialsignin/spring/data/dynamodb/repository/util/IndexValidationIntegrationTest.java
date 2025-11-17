package org.socialsignin.spring.data.dynamodb.repository.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.socialsignin.spring.data.dynamodb.domain.sample.*;
import org.socialsignin.spring.data.dynamodb.mapping.DynamoDBMappingContext;
import org.socialsignin.spring.data.dynamodb.repository.support.DynamoDBEntityInformation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for validating GSI and LSI configuration errors.
 * These tests verify that our validation logic catches common configuration mistakes
 * with clear, actionable error messages.
 */
@SpringBootTest(classes = {
        org.socialsignin.spring.data.dynamodb.utils.DynamoDBLocalResource.class
})
@TestPropertySource(properties = {
        "spring.data.dynamodb.entity2ddl.auto=none"  // Don't auto-create tables
})
@DisplayName("Index Validation Integration Tests")
class IndexValidationIntegrationTest {

    @Autowired
    private DynamoDbClient amazonDynamoDB;

    @Autowired
    private DynamoDbEnhancedClient enhancedClient;

    @Autowired
    private DynamoDBMappingContext mappingContext;

    @Test
    @DisplayName("Should reject duplicate GSI partition keys with clear error message")
    void testDuplicateGsiPartitionKey() {
        // Given: An entity with two different attributes trying to be partition key for same index
        Class<InvalidEntityDuplicateGsiPartitionKey> entityClass = InvalidEntityDuplicateGsiPartitionKey.class;

        // When: Attempting to generate create table request
        // Then: Should throw IllegalStateException with specific error message
        assertThatThrownBy(() -> {
            triggerValidationFor(entityClass);
        })
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Invalid GSI configuration")
        .hasMessageContaining("InvalidEntityDuplicateGsiPartitionKey")
        .hasMessageContaining("duplicateIndex")
        .hasMessageContaining("multiple partition keys")
        .hasMessageContaining("status")
        .hasMessageContaining("category");
    }

    @Test
    @DisplayName("Should reject duplicate GSI sort keys with clear error message")
    void testDuplicateGsiSortKey() {
        // Given: An entity with two different attributes trying to be sort key for same index
        Class<InvalidEntityDuplicateGsiSortKey> entityClass = InvalidEntityDuplicateGsiSortKey.class;

        // When/Then: Should throw IllegalStateException with specific error message
        assertThatThrownBy(() -> {
            triggerValidationFor(entityClass);
        })
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Invalid GSI configuration")
        .hasMessageContaining("InvalidEntityDuplicateGsiSortKey")
        .hasMessageContaining("memberIndex")
        .hasMessageContaining("multiple sort keys")
        .hasMessageContaining("createdAt")
        .hasMessageContaining("updatedAt");
    }

    @Test
    @DisplayName("Should reject duplicate LSI sort keys with clear error message")
    void testDuplicateLsiSortKey() {
        // Given: An entity with two different attributes trying to be sort key for same LSI
        Class<InvalidEntityDuplicateLsiSortKey> entityClass = InvalidEntityDuplicateLsiSortKey.class;

        // When/Then: Should throw IllegalStateException with specific error message
        assertThatThrownBy(() -> {
            triggerValidationFor(entityClass);
        })
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Invalid LSI configuration")
        .hasMessageContaining("InvalidEntityDuplicateLsiSortKey")
        .hasMessageContaining("customerId-date-index")
        .hasMessageContaining("multiple sort keys")
        .hasMessageContaining("orderDate")
        .hasMessageContaining("createdDate");
    }

    @Test
    @DisplayName("Should allow same attribute with annotations on both method and field")
    void testValidMethodAndFieldAnnotations() {
        // Given: An entity with same attribute annotated on both method and field
        Class<ValidEntityMethodAndFieldAnnotations> entityClass = ValidEntityMethodAndFieldAnnotations.class;

        // When: Creating table synchronizer and attempting to generate table request
        // Then: Should NOT throw any exception
        triggerValidationFor(entityClass);
    }

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
