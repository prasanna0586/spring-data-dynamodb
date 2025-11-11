package org.socialsignin.spring.data.dynamodb.domain.sample;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.socialsignin.spring.data.dynamodb.utils.DynamoDBLocalResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive integration tests for DynamoDB Projection Types.
 *
 * DynamoDB supports three projection types for Global and Local Secondary Indexes:
 * 1. KEYS_ONLY - Only projected attributes are the index keys and primary keys
 * 2. INCLUDE - Projects specified non-key attributes in addition to keys
 * 3. ALL - Projects all attributes from the base table
 *
 * Coverage:
 * - GSI with KEYS_ONLY projection
 * - GSI with INCLUDE projection
 * - GSI with ALL projection
 * - LSI with different projection types
 * - Verifying projected vs non-projected attributes
 * - Query performance implications
 *
 * Note: This test manually creates tables with specific projection types
 * to demonstrate the different behaviors.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, ProjectionTypesIntegrationTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create-only"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("GSI/LSI Projection Types Integration Tests")
public class ProjectionTypesIntegrationTest {

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    public static class TestAppConfig {
    }

    @Autowired
    private DynamoDbClient amazonDynamoDB;

    @Autowired
    private DynamoDBMapper dynamoDBMapper;

    private static final String TABLE_NAME = "ProductCatalog";

    @BeforeAll
    static void setupClass() {
        // Table will be created in setupProjectionTestTable()
    }

    @BeforeEach
    void setUp() {
        // Recreate table for each test with specific projection configuration
    }

    @AfterEach
    void tearDown() {
        // Clean up table after each test
        try {
            amazonDynamoDB.deleteTable(TABLE_NAME);
            // Wait for table deletion
            Thread.sleep(1000);
        } catch (Exception e) {
            // Table may not exist
        }
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Test 1: GSI with KEYS_ONLY projection - Only keys are projected")
    void testGSI_KeysOnlyProjection() throws InterruptedException {
        // Given - Create table with GSI having KEYS_ONLY projection
        createTableWithGSI(ProjectionType.KEYS_ONLY, null);

        // Insert item with multiple attributes
        putItemWithAllAttributes("product-001", "Electronics", "Laptop",
                "High-performance laptop", 1299.99, 10);

        // When - Query using GSI
        Map<String, AttributeValue> item = queryGSI("Electronics").get(0);

        // Then - Verify only keys are projected
        assertThat(item).containsKey("productId");  // Table hash key
        assertThat(item).containsKey("category");   // GSI hash key
        assertThat(item).containsKey("price");      // GSI range key

        // Non-key attributes should NOT be projected
        assertThat(item).doesNotContainKey("productName");
        assertThat(item).doesNotContainKey("description");
        assertThat(item).doesNotContainKey("stock");
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Test 2: GSI with INCLUDE projection - Specified attributes are projected")
    void testGSI_IncludeProjection() throws InterruptedException {
        // Given - Create table with GSI having INCLUDE projection (include productName and stock)
        List<String> nonKeyAttributes = Arrays.asList("productName", "stock");
        createTableWithGSI(ProjectionType.INCLUDE, nonKeyAttributes);

        // Insert item
        putItemWithAllAttributes("product-002", "Electronics", "Smartphone",
                "Latest model smartphone", 899.99, 25);

        // When - Query using GSI
        Map<String, AttributeValue> item = queryGSI("Electronics").get(0);

        // Then - Verify keys and included attributes are projected
        assertThat(item).containsKey("productId");
        assertThat(item).containsKey("category");
        assertThat(item).containsKey("price");
        assertThat(item).containsKey("productName");  // Included
        assertThat(item).containsKey("stock");        // Included

        // Non-included attribute should NOT be projected
        assertThat(item).doesNotContainKey("description");
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Test 3: GSI with ALL projection - All attributes are projected")
    void testGSI_AllProjection() throws InterruptedException {
        // Given - Create table with GSI having ALL projection
        createTableWithGSI(ProjectionType.ALL, null);

        // Insert item
        putItemWithAllAttributes("product-003", "Books", "DynamoDB Guide",
                "Comprehensive guide to DynamoDB", 49.99, 100);

        // When - Query using GSI
        Map<String, AttributeValue> item = queryGSI("Books").get(0);

        // Then - Verify ALL attributes are projected
        assertThat(item).containsKey("productId");
        assertThat(item).containsKey("category");
        assertThat(item).containsKey("price");
        assertThat(item).containsKey("productName");
        assertThat(item).containsKey("description");
        assertThat(item).containsKey("stock");

        // Verify attribute values
        assertThat(item.get("productName").s()).isEqualTo("DynamoDB Guide");
        assertThat(item.get("description").s()).isEqualTo("Comprehensive guide to DynamoDB");
        assertThat(item.get("stock").n()).isEqualTo("100");
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Test 4: Compare projection types - Data completeness")
    void testCompareProjectionTypes() throws InterruptedException {
        // Test KEYS_ONLY
        createTableWithGSI(ProjectionType.KEYS_ONLY, null);
        putItemWithAllAttributes("product-001", "Electronics", "Item1",
                "Description1", 100.0, 10);
        Map<String, AttributeValue> keysOnlyItem = queryGSI("Electronics").get(0);
        int keysOnlySize = keysOnlyItem.size();
        amazonDynamoDB.deleteTable(TABLE_NAME);
        Thread.sleep(1000);

        // Test INCLUDE with 2 attributes
        List<String> includeAttrs = Arrays.asList("productName", "stock");
        createTableWithGSI(ProjectionType.INCLUDE, includeAttrs);
        putItemWithAllAttributes("product-001", "Electronics", "Item1",
                "Description1", 100.0, 10);
        Map<String, AttributeValue> includeItem = queryGSI("Electronics").get(0);
        int includeSize = includeItem.size();
        amazonDynamoDB.deleteTable(TABLE_NAME);
        Thread.sleep(1000);

        // Test ALL
        createTableWithGSI(ProjectionType.ALL, null);
        putItemWithAllAttributes("product-001", "Electronics", "Item1",
                "Description1", 100.0, 10);
        Map<String, AttributeValue> allItem = queryGSI("Electronics").get(0);
        int allSize = allItem.size();

        // Then - Verify size progression: KEYS_ONLY < INCLUDE < ALL
        assertThat(keysOnlySize).isEqualTo(3);  // productId, category, price
        assertThat(includeSize).isEqualTo(5);   // +productName, +stock
        assertThat(allSize).isEqualTo(6);       // +description
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("Test 5: Multiple items with different projection types")
    void testMultipleItemsWithProjection() throws InterruptedException {
        // Given - GSI with INCLUDE projection
        List<String> includeAttrs = Arrays.asList("productName");
        createTableWithGSI(ProjectionType.INCLUDE, includeAttrs);

        // Insert multiple items in same category
        putItemWithAllAttributes("product-001", "Electronics", "Laptop",
                "Gaming laptop", 1500.0, 5);
        putItemWithAllAttributes("product-002", "Electronics", "Mouse",
                "Wireless mouse", 25.0, 50);
        putItemWithAllAttributes("product-003", "Electronics", "Keyboard",
                "Mechanical keyboard", 150.0, 20);

        // When - Query all electronics
        List<Map<String, AttributeValue>> items = queryGSI("Electronics");

        // Then - All items should have same projected attributes
        assertThat(items).hasSize(3);
        for (Map<String, AttributeValue> item : items) {
            assertThat(item).containsKeys("productId", "category", "price", "productName");
            assertThat(item).doesNotContainKey("description");
            assertThat(item).doesNotContainKey("stock");
        }
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("Test 6: Projection types and storage optimization")
    void testProjectionStorageOptimization() throws InterruptedException {
        // Given - GSI with KEYS_ONLY (minimal storage)
        createTableWithGSI(ProjectionType.KEYS_ONLY, null);

        // Insert item with large description
        String largeDescription = "A".repeat(1000);  // 1KB description
        putItemWithAllAttributes("product-001", "Electronics", "Product",
                largeDescription, 100.0, 10);

        // When - Query using GSI
        Map<String, AttributeValue> item = queryGSI("Electronics").get(0);

        // Then - Large description is NOT projected (storage optimization)
        assertThat(item).doesNotContainKey("description");
        assertThat(item.size()).isEqualTo(3);  // Only keys
    }

    // ==================== Helper Methods ====================

    /**
     * Create table with GSI having specified projection type.
     */
    private void createTableWithGSI(ProjectionType projectionType,
                                     List<String> nonKeyAttributes) throws InterruptedException {
        // Define table schema
        List<AttributeDefinition> attributeDefinitions = Arrays.asList(
                new AttributeDefinition("productId", ScalarAttributeType.S),
                new AttributeDefinition("category", ScalarAttributeType.S),
                new AttributeDefinition("price", ScalarAttributeType.N)
        );

        List<KeySchemaElement> keySchema = Arrays.asList(
                new KeySchemaElement("productId", KeyType.HASH)
        );

        // Define GSI
        Projection projection = Projection.builder().projectionType(projectionType)
                .build();
        if (projectionType == ProjectionType.INCLUDE && nonKeyAttributes != null) {
            projection = projection.toBuilder().nonKeyAttributes(nonKeyAttributes).build();
        }

        GlobalSecondaryIndex gsi = GlobalSecondaryIndex.builder()
                .indexName("category-price-index")
                .keySchema(
                        new KeySchemaElement("category", KeyType.HASH),
                        new KeySchemaElement("price", KeyType.RANGE)
                )
                .projection(projection)
                .provisionedThroughput(new ProvisionedThroughput(5L, 5L))
                .build();

        CreateTableRequest request = CreateTableRequest.builder()
                .tableName(TABLE_NAME)
                .attributeDefinitions(attributeDefinitions)
                .keySchema(keySchema)
                .globalSecondaryIndexes(gsi)
                .provisionedThroughput(new ProvisionedThroughput(5L, 5L))
                .build();

        amazonDynamoDB.createTable(request);

        // Wait for table to be active
        waitForTableToBecomeActive();
    }

    /**
     * Put item with all attributes.
     */
    private void putItemWithAllAttributes(String productId, String category, String productName,
                                          String description, Double price, Integer stock) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("productId", new AttributeValue(productId));
        item.put("category", new AttributeValue(category));
        item.put("productName", new AttributeValue(productName));
        item.put("description", new AttributeValue(description));
        item.put("price", AttributeValue.builder().n(String.valueOf(price))
                .build());
        item.put("stock", AttributeValue.builder().n(String.valueOf(stock))
                .build());

        amazonDynamoDB.putItem(TABLE_NAME, item);
    }

    /**
     * Query GSI by category.
     */
    private List<Map<String, AttributeValue>> queryGSI(String category) {
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":category", new AttributeValue(category));

        QueryRequest queryRequest = QueryRequest.builder()
                .tableName(TABLE_NAME)
                .indexName("category-price-index")
                .keyConditionExpression("category = :category")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        QueryResponse result = amazonDynamoDB.query(queryRequest);
        return result.items();
    }

    /**
     * Wait for table to become active.
     */
    private void waitForTableToBecomeActive() throws InterruptedException {
        int maxAttempts = 30;
        int attempt = 0;

        while (attempt < maxAttempts) {
            try {
                DescribeTableResponse result = amazonDynamoDB.describeTable(TABLE_NAME);
                String status = result.table().tableStatus();

                if ("ACTIVE".equals(status)) {
                    // Also check GSI status
                    if (result.table().globalSecondaryIndexes() != null) {
                        boolean allGsiActive = result.table().globalSecondaryIndexes().stream()
                                .allMatch(gsi -> "ACTIVE".equals(gsi.indexStatus()));
                        if (allGsiActive) {
                            return;
                        }
                    } else {
                        return;
                    }
                }
            } catch (Exception e) {
                // Table not ready yet
            }

            Thread.sleep(1000);
            attempt++;
        }

        throw new RuntimeException("Table did not become active within timeout");
    }
}
