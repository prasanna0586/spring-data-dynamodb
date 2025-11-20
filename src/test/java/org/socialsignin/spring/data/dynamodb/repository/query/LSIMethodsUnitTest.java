package org.socialsignin.spring.data.dynamodb.repository.query;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;
import org.socialsignin.spring.data.dynamodb.domain.sample.ProductOrder;
import org.socialsignin.spring.data.dynamodb.domain.sample.ProductOrderId;
import org.socialsignin.spring.data.dynamodb.mapping.DynamoDBMappingContext;
import org.socialsignin.spring.data.dynamodb.repository.support.DynamoDBIdIsHashAndRangeKeyEntityInformation;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.ComparisonOperator;
import software.amazon.awssdk.services.dynamodb.model.Condition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for LSI-specific methods in AbstractDynamoDBQueryCriteria.
 *
 * Tests the core logic for Local Secondary Index handling:
 * 1. isApplicableForGlobalSecondaryIndex() - Should recognize LSI queries
 * 2. hasIndexHashKeyEqualCondition() - Should handle LSI hash keys (table partition key)
 * 3. getHashKeyConditions() - Should generate conditions for LSI
 *
 * These tests verify the fixes made to distinguish between LSI and GSI.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("LSI Methods Unit Tests")
public class LSIMethodsUnitTest {

    @Mock
    private DynamoDBOperations mockDynamoDBOperations;

    @Mock
    private DynamoDBIdIsHashAndRangeKeyEntityInformation<ProductOrder, ProductOrderId> mockEntityInformation;

    @Mock
    private DynamoDBMappingContext mockMappingContext;

    @Mock
    private TableSchema<ProductOrder> mockTableSchema;

    private TestableAbstractDynamoDBQueryCriteria<ProductOrder, ProductOrderId> criteria;

    @BeforeEach
    void setUp() {
        // Setup basic entity information
        when(mockEntityInformation.getJavaType()).thenReturn(ProductOrder.class);
        when(mockEntityInformation.getHashKeyPropertyName()).thenReturn("customerId");
        when(mockEntityInformation.getRangeKeyPropertyName()).thenReturn("orderId");
        when(mockEntityInformation.getDynamoDBTableName()).thenReturn("ProductOrder");

        // Setup global secondary index names
        Map<String, String[]> globalSecondaryIndexNames = new HashMap<>();
        // LSI uses table partition key, so customerId is NOT in this map
        // Only true GSI hash keys would be in this map
        when(mockEntityInformation.getGlobalSecondaryIndexNamesByPropertyName())
                .thenReturn(globalSecondaryIndexNames);

        criteria = new TestableAbstractDynamoDBQueryCriteria<>(
                mockEntityInformation, mockTableSchema, mockMappingContext);
    }

    @Test
    @DisplayName("Test 1: isApplicableForGlobalSecondaryIndex() returns true when LSI index is set")
    void testIsApplicableForGlobalSecondaryIndex_WithLSI() {
        // Given - Set an LSI index name
        criteria.setGlobalSecondaryIndexName("customerId-orderDate-index");

        // When
        boolean result = criteria.isApplicableForGlobalSecondaryIndex();

        // Then - Should return true for LSI
        assertTrue(result, "Should recognize LSI query as applicable for index");
    }

    @Test
    @DisplayName("Test 2: isApplicableForGlobalSecondaryIndex() returns false when no index is set")
    void testIsApplicableForGlobalSecondaryIndex_WithoutIndex() {
        // Given - No index set
        criteria.setGlobalSecondaryIndexName(null);

        // When
        boolean result = criteria.isApplicableForGlobalSecondaryIndex();

        // Then
        assertFalse(result, "Should return false when no index is set");
    }

    @Test
    @DisplayName("Test 3: isApplicableForGlobalSecondaryIndex() with LSI and table partition key")
    void testIsApplicableForGlobalSecondaryIndex_WithLSIAndTablePartitionKey() {
        // Given - LSI query with table partition key as hash key
        criteria.setGlobalSecondaryIndexName("customerId-orderDate-index");
        criteria.setHashKeyPropertyName("customerId"); // Table partition key
        criteria.setHashKeyAttributeValue("customer-001");

        // When
        boolean result = criteria.isApplicableForGlobalSecondaryIndex();

        // Then - Should return true even though hash key is table partition key (LSI case)
        assertTrue(result, "Should recognize LSI query even with table partition key");
    }

    @Test
    @DisplayName("Test 4: hasIndexHashKeyEqualCondition() returns true for LSI with table partition key")
    void testHasIndexHashKeyEqualCondition_WithLSI() {
        // Given - LSI setup with table partition key
        criteria.setGlobalSecondaryIndexName("customerId-orderDate-index");
        criteria.setHashKeyPropertyName("customerId"); // Table partition key used as LSI hash key
        criteria.setHashKeyAttributeValue("customer-001");

        // When
        boolean result = criteria.hasIndexHashKeyEqualCondition();

        // Then - Should return true because LSI uses table partition key
        assertTrue(result, "Should recognize table partition key as LSI hash key when index is set");
    }

    @Test
    @DisplayName("Test 5: hasIndexHashKeyEqualCondition() returns false when no index is set")
    void testHasIndexHashKeyEqualCondition_WithoutIndex() {
        // Given - No index set
        criteria.setGlobalSecondaryIndexName(null);
        criteria.setHashKeyPropertyName("customerId");
        criteria.setHashKeyAttributeValue("customer-001");

        // When
        boolean result = criteria.hasIndexHashKeyEqualCondition();

        // Then - Should return false when not using an index
        assertFalse(result, "Should return false when no index is being used");
    }

    @Test
    @DisplayName("Test 6: hasIndexHashKeyEqualCondition() with GSI hash key")
    void testHasIndexHashKeyEqualCondition_WithGSI() {
        // Given - GSI setup with different hash key
        Map<String, String[]> gsiMap = new HashMap<>();
        gsiMap.put("status", new String[]{"status-index"});
        when(mockEntityInformation.getGlobalSecondaryIndexNamesByPropertyName()).thenReturn(gsiMap);
        when(mockEntityInformation.isGlobalIndexHashKeyProperty("status")).thenReturn(true);

        criteria.setGlobalSecondaryIndexName("status-index");
        criteria.setHashKeyPropertyName("status");
        criteria.setHashKeyAttributeValue("COMPLETED");

        // When
        boolean result = criteria.hasIndexHashKeyEqualCondition();

        // Then - Should return true for GSI hash key
        assertTrue(result, "Should recognize GSI hash key");
    }

    @Test
    @DisplayName("Test 7: getHashKeyConditions() returns conditions for LSI")
    void testGetHashKeyConditions_WithLSI() {
        // Given - LSI setup
        criteria.setGlobalSecondaryIndexName("customerId-orderDate-index");
        criteria.setHashKeyPropertyName("customerId");
        criteria.setHashKeyAttributeValue("customer-001");

        // When
        List<Condition> conditions = criteria.getHashKeyConditions();

        // Then - Should return conditions for LSI hash key
        assertNotNull(conditions, "Should return conditions for LSI");
        assertEquals(1, conditions.size(), "Should have one condition");
    }

    @Test
    @DisplayName("Test 8: getHashKeyConditions() returns a condition for main table queries when not using index")
    void testGetHashKeyConditions_WithoutIndex() {
        // Note: In SDK v2, main table queries also generate hash key conditions.
        // This test validates the new behavior instead of expecting null (SDK v1 behavior).

        // Given - No index
        criteria.setGlobalSecondaryIndexName(null);
        criteria.setHashKeyPropertyName("customerId");
        criteria.setHashKeyAttributeValue("customer-001");

        // When
        List<Condition> conditions = criteria.getHashKeyConditions();

        // Then - Should return a condition for main table queries (new behavior)
        assertNotNull(conditions, "Should return conditions for main table queries");
        assertEquals(1, conditions.size());
        assertEquals(ComparisonOperator.EQ, conditions.get(0).comparisonOperator());
        assertEquals(1, conditions.get(0).attributeValueList().size());
        assertEquals("customer-001", conditions.get(0).attributeValueList().get(0).s());
    }


    @Test
    @DisplayName("Test 9: LSI vs GSI detection - LSI uses table partition key")
    void testLSI_vs_GSI_Detection() {
        // Test LSI case
        criteria.setGlobalSecondaryIndexName("customerId-orderDate-index");
        criteria.setHashKeyPropertyName("customerId"); // Table partition key
        criteria.setHashKeyAttributeValue("customer-001");

        assertTrue(criteria.isApplicableForGlobalSecondaryIndex(), "LSI should be applicable");
        assertTrue(criteria.hasIndexHashKeyEqualCondition(), "LSI should have hash key condition");
        assertNotNull(criteria.getHashKeyConditions(), "LSI should return hash key conditions");

        // Test GSI case
        Map<String, String[]> gsiMap = new HashMap<>();
        gsiMap.put("merchantId", new String[]{"merchantId-index"});
        when(mockEntityInformation.getGlobalSecondaryIndexNamesByPropertyName()).thenReturn(gsiMap);
        when(mockEntityInformation.isGlobalIndexHashKeyProperty("merchantId")).thenReturn(true);

        criteria.setGlobalSecondaryIndexName("merchantId-index");
        criteria.setHashKeyPropertyName("merchantId"); // Different from table partition key
        criteria.setHashKeyAttributeValue("MERCHANT_A");

        assertTrue(criteria.isApplicableForGlobalSecondaryIndex(), "GSI should be applicable");
        assertTrue(criteria.hasIndexHashKeyEqualCondition(), "GSI should have hash key condition");
        assertNotNull(criteria.getHashKeyConditions(), "GSI should return hash key conditions");
    }

    @Test
    @DisplayName("Test 10: getHashKeyConditions() distinguishes LSI from GSI")
    void testGetHashKeyConditions_DistinguishesLSIFromGSI() {
        // LSI case - uses table partition key
        criteria.setGlobalSecondaryIndexName("customerId-orderDate-index");
        criteria.setHashKeyPropertyName("customerId");
        criteria.setHashKeyAttributeValue("customer-001");

        List<Condition> lsiConditions = criteria.getHashKeyConditions();
        assertNotNull(lsiConditions, "LSI should generate hash key conditions");

        // GSI case - uses different hash key
        Map<String, String[]> gsiMap = new HashMap<>();
        gsiMap.put("status", new String[]{"status-index"});
        when(mockEntityInformation.getGlobalSecondaryIndexNamesByPropertyName()).thenReturn(gsiMap);

        criteria.setGlobalSecondaryIndexName("status-index");
        criteria.setHashKeyPropertyName("status");
        criteria.setHashKeyAttributeValue("COMPLETED");

        List<Condition> gsiConditions = criteria.getHashKeyConditions();
        assertNotNull(gsiConditions, "GSI should generate hash key conditions");
    }

    /**
     * Testable subclass of AbstractDynamoDBQueryCriteria to expose protected methods for testing.
     */
    private static class TestableAbstractDynamoDBQueryCriteria<T, ID> extends DynamoDBEntityWithHashAndRangeKeyCriteria<T, ID> {

        private String globalSecondaryIndexName;
        private String customHashKeyPropertyName;

        public TestableAbstractDynamoDBQueryCriteria(DynamoDBIdIsHashAndRangeKeyEntityInformation<T, ID> entityInformation,
                                                     TableSchema<T> tableSchema,
                                                     DynamoDBMappingContext mappingContext) {
            super(entityInformation, tableSchema, mappingContext);
        }

        public void setGlobalSecondaryIndexName(String indexName) {
            this.globalSecondaryIndexName = indexName;
        }

        @Override
        protected String getGlobalSecondaryIndexName() {
            return globalSecondaryIndexName;
        }

        public void setHashKeyPropertyName(String propertyName) {
            this.customHashKeyPropertyName = propertyName;
        }

        @Override
        protected String getHashKeyPropertyName() {
            return customHashKeyPropertyName != null ? customHashKeyPropertyName : super.getHashKeyPropertyName();
        }

        public void setHashKeyAttributeValue(Object value) {
            this.hashKeyAttributeValue = value;
        }

        // Expose protected methods for testing
        @Override
        public boolean isApplicableForGlobalSecondaryIndex() {
            return super.isApplicableForGlobalSecondaryIndex();
        }

        @Override
        public boolean hasIndexHashKeyEqualCondition() {
            return super.hasIndexHashKeyEqualCondition();
        }

        @Override
        public List<Condition> getHashKeyConditions() {
            return super.getHashKeyConditions();
        }
    }
}
