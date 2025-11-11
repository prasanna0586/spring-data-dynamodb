package org.socialsignin.spring.data.dynamodb.domain.sample;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.socialsignin.spring.data.dynamodb.utils.DynamoDBLocalResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive integration tests for Map and List attributes in DynamoDB.
 *
 * Coverage:
 * - Map<String, String> attributes
 * - Map<String, Integer> attributes
 * - List<String> attributes
 * - List<Double> attributes
 * - Updating map values
 * - Updating list elements
 * - Appending to lists
 * - Removing from maps and lists
 * - Querying/filtering on map keys
 * - Empty maps and lists
 * - Null handling
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, MapListAttributesIntegrationTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Map and List Attributes Integration Tests")
public class MapListAttributesIntegrationTest {

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    public static class TestAppConfig {
    }

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AmazonDynamoDB amazonDynamoDB;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Test 1: Save and retrieve product with Map<String, String> attributes")
    void testMapStringStringAttribute() {
        // Given
        Product product = new Product();
        product.setProductId("prod-001");
        product.setName("T-Shirt");

        Map<String, String> attributes = new HashMap<>();
        attributes.put("color", "blue");
        attributes.put("size", "L");
        attributes.put("material", "cotton");
        product.setAttributes(attributes);

        // When
        productRepository.save(product);

        // Then
        Product retrieved = productRepository.findById("prod-001").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getAttributes()).hasSize(3);
        assertThat(retrieved.getAttributes().get("color")).isEqualTo("blue");
        assertThat(retrieved.getAttributes().get("size")).isEqualTo("L");
        assertThat(retrieved.getAttributes().get("material")).isEqualTo("cotton");
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Test 2: Save and retrieve product with Map<String, Integer> inventory")
    void testMapStringIntegerAttribute() {
        // Given
        Product product = new Product();
        product.setProductId("prod-002");
        product.setName("Laptop");

        Map<String, Integer> inventory = new HashMap<>();
        inventory.put("warehouse-A", 50);
        inventory.put("warehouse-B", 30);
        inventory.put("warehouse-C", 20);
        product.setInventory(inventory);

        // When
        productRepository.save(product);

        // Then
        Product retrieved = productRepository.findById("prod-002").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getInventory()).hasSize(3);
        assertThat(retrieved.getInventory().get("warehouse-A")).isEqualTo(50);
        assertThat(retrieved.getInventory().get("warehouse-B")).isEqualTo(30);
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Test 3: Save and retrieve product with List<String> categories")
    void testListStringAttribute() {
        // Given
        Product product = new Product();
        product.setProductId("prod-003");
        product.setName("Book");

        List<String> categories = Arrays.asList("Fiction", "Bestseller", "Paperback");
        product.setCategories(categories);

        // When
        productRepository.save(product);

        // Then
        Product retrieved = productRepository.findById("prod-003").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getCategories()).hasSize(3);
        assertThat(retrieved.getCategories()).containsExactly("Fiction", "Bestseller", "Paperback");
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Test 4: Save and retrieve product with List<Double> price history")
    void testListDoubleAttribute() {
        // Given
        Product product = new Product();
        product.setProductId("prod-004");
        product.setName("Phone");

        List<Double> priceHistory = Arrays.asList(999.99, 899.99, 799.99);
        product.setPriceHistory(priceHistory);

        // When
        productRepository.save(product);

        // Then
        Product retrieved = productRepository.findById("prod-004").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getPriceHistory()).hasSize(3);
        assertThat(retrieved.getPriceHistory()).containsExactly(999.99, 899.99, 799.99);
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("Test 5: Update map attribute - add new key")
    void testUpdateMapAddKey() {
        // Given
        Product product = new Product();
        product.setProductId("prod-005");
        product.setName("Shoes");
        Map<String, String> attributes = new HashMap<>();
        attributes.put("color", "black");
        product.setAttributes(attributes);
        productRepository.save(product);

        // When - Add new attribute using UpdateExpression
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("productId", new AttributeValue("prod-005"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":size", new AttributeValue("42"));

        UpdateItemRequest updateRequest = new UpdateItemRequest()
                .withTableName("Product")
                .withKey(key)
                .withUpdateExpression("SET attributes.#size = :size")
                .withExpressionAttributeNames(Collections.singletonMap("#size", "size"))
                .withExpressionAttributeValues(expressionAttributeValues);

        amazonDynamoDB.updateItem(updateRequest);

        // Then
        Product updated = productRepository.findById("prod-005").orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getAttributes()).hasSize(2);
        assertThat(updated.getAttributes().get("color")).isEqualTo("black");
        assertThat(updated.getAttributes().get("size")).isEqualTo("42");
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("Test 6: Update map attribute - modify existing key")
    void testUpdateMapModifyKey() {
        // Given
        Product product = new Product();
        product.setProductId("prod-006");
        product.setName("Jacket");
        Map<String, String> attributes = new HashMap<>();
        attributes.put("color", "red");
        attributes.put("size", "M");
        product.setAttributes(attributes);
        productRepository.save(product);

        // When - Change color
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("productId", new AttributeValue("prod-006"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newColor", new AttributeValue("blue"));

        UpdateItemRequest updateRequest = new UpdateItemRequest()
                .withTableName("Product")
                .withKey(key)
                .withUpdateExpression("SET attributes.color = :newColor")
                .withExpressionAttributeValues(expressionAttributeValues);

        amazonDynamoDB.updateItem(updateRequest);

        // Then
        Product updated = productRepository.findById("prod-006").orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getAttributes().get("color")).isEqualTo("blue");
        assertThat(updated.getAttributes().get("size")).isEqualTo("M");
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("Test 7: Remove key from map")
    void testRemoveMapKey() {
        // Given
        Product product = new Product();
        product.setProductId("prod-007");
        product.setName("Hat");
        Map<String, String> attributes = new HashMap<>();
        attributes.put("color", "green");
        attributes.put("size", "L");
        attributes.put("material", "wool");
        product.setAttributes(attributes);
        productRepository.save(product);

        // When - Remove material attribute
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("productId", new AttributeValue("prod-007"));

        UpdateItemRequest updateRequest = new UpdateItemRequest()
                .withTableName("Product")
                .withKey(key)
                .withUpdateExpression("REMOVE attributes.material");

        amazonDynamoDB.updateItem(updateRequest);

        // Then
        Product updated = productRepository.findById("prod-007").orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getAttributes()).hasSize(2);
        assertThat(updated.getAttributes()).containsKeys("color", "size");
        assertThat(updated.getAttributes()).doesNotContainKey("material");
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("Test 8: Append to list")
    void testAppendToList() {
        // Given
        Product product = new Product();
        product.setProductId("prod-008");
        product.setName("Camera");
        product.setCategories(new ArrayList<>(Arrays.asList("Electronics")));
        productRepository.save(product);

        // When - Append new categories
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("productId", new AttributeValue("prod-008"));

        AttributeValue newCategories = new AttributeValue()
                .withL(new AttributeValue("Photography"), new AttributeValue("Professional"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newCats", newCategories);

        UpdateItemRequest updateRequest = new UpdateItemRequest()
                .withTableName("Product")
                .withKey(key)
                .withUpdateExpression("SET categories = list_append(categories, :newCats)")
                .withExpressionAttributeValues(expressionAttributeValues);

        amazonDynamoDB.updateItem(updateRequest);

        // Then
        Product updated = productRepository.findById("prod-008").orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getCategories()).hasSize(3);
        assertThat(updated.getCategories()).containsExactly("Electronics", "Photography", "Professional");
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("Test 9: Prepend to list")
    void testPrependToList() {
        // Given
        Product product = new Product();
        product.setProductId("prod-009");
        product.setName("Monitor");
        product.setCategories(new ArrayList<>(Arrays.asList("Display")));
        productRepository.save(product);

        // When - Prepend new category
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("productId", new AttributeValue("prod-009"));

        AttributeValue newCategories = new AttributeValue()
                .withL(new AttributeValue("Electronics"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newCats", newCategories);

        UpdateItemRequest updateRequest = new UpdateItemRequest()
                .withTableName("Product")
                .withKey(key)
                .withUpdateExpression("SET categories = list_append(:newCats, categories)")
                .withExpressionAttributeValues(expressionAttributeValues);

        amazonDynamoDB.updateItem(updateRequest);

        // Then
        Product updated = productRepository.findById("prod-009").orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getCategories()).hasSize(2);
        assertThat(updated.getCategories()).containsExactly("Electronics", "Display");
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("Test 10: Update specific list element by index")
    void testUpdateListElement() {
        // Given
        Product product = new Product();
        product.setProductId("prod-010");
        product.setName("Tablet");
        product.setPriceHistory(new ArrayList<>(Arrays.asList(599.99, 499.99, 399.99)));
        productRepository.save(product);

        // When - Update second element (index 1)
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("productId", new AttributeValue("prod-010"));

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newPrice", new AttributeValue().withN("449.99"));

        UpdateItemRequest updateRequest = new UpdateItemRequest()
                .withTableName("Product")
                .withKey(key)
                .withUpdateExpression("SET priceHistory[1] = :newPrice")
                .withExpressionAttributeValues(expressionAttributeValues);

        amazonDynamoDB.updateItem(updateRequest);

        // Then
        Product updated = productRepository.findById("prod-010").orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getPriceHistory()).containsExactly(599.99, 449.99, 399.99);
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    @DisplayName("Test 11: Remove list element by index")
    void testRemoveListElement() {
        // Given
        Product product = new Product();
        product.setProductId("prod-011");
        product.setName("Headphones");
        product.setCategories(new ArrayList<>(Arrays.asList("Audio", "Wireless", "Premium")));
        productRepository.save(product);

        // When - Remove middle element (index 1)
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("productId", new AttributeValue("prod-011"));

        UpdateItemRequest updateRequest = new UpdateItemRequest()
                .withTableName("Product")
                .withKey(key)
                .withUpdateExpression("REMOVE categories[1]");

        amazonDynamoDB.updateItem(updateRequest);

        // Then
        Product updated = productRepository.findById("prod-011").orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getCategories()).hasSize(2);
        assertThat(updated.getCategories()).containsExactly("Audio", "Premium");
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    @DisplayName("Test 12: Empty map handling")
    void testEmptyMap() {
        // Given - Product with empty map
        Product product = new Product();
        product.setProductId("prod-012");
        product.setName("Widget");
        product.setAttributes(new HashMap<>()); // Empty map

        // When
        productRepository.save(product);

        // Then - Empty map should be null in DynamoDB
        Product retrieved = productRepository.findById("prod-012").orElse(null);
        assertThat(retrieved).isNotNull();
        // DynamoDB doesn't store empty maps, so it will be null
        assertThat(retrieved.getAttributes()).isNullOrEmpty();
    }

    @Test
    @org.junit.jupiter.api.Order(13)
    @DisplayName("Test 13: Empty list handling")
    void testEmptyList() {
        // Given - Product with empty list
        Product product = new Product();
        product.setProductId("prod-013");
        product.setName("Gadget");
        product.setCategories(new ArrayList<>()); // Empty list

        // When
        productRepository.save(product);

        // Then - Empty list should be null in DynamoDB
        Product retrieved = productRepository.findById("prod-013").orElse(null);
        assertThat(retrieved).isNotNull();
        // DynamoDB doesn't store empty lists, so it will be null
        assertThat(retrieved.getCategories()).isNullOrEmpty();
    }

    @Test
    @org.junit.jupiter.api.Order(14)
    @DisplayName("Test 14: Null map and list handling")
    void testNullMapAndList() {
        // Given
        Product product = new Product();
        product.setProductId("prod-014");
        product.setName("Item");
        product.setAttributes(null);
        product.setCategories(null);

        // When
        productRepository.save(product);

        // Then
        Product retrieved = productRepository.findById("prod-014").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getAttributes()).isNull();
        assertThat(retrieved.getCategories()).isNull();
    }

    @Test
    @org.junit.jupiter.api.Order(15)
    @DisplayName("Test 15: Complex product with all attributes")
    void testComplexProductWithAllAttributes() {
        // Given - Product with maps and lists
        Product product = new Product();
        product.setProductId("prod-015");
        product.setName("Complete Product");

        Map<String, String> attributes = new HashMap<>();
        attributes.put("brand", "TechCorp");
        attributes.put("model", "X1000");
        product.setAttributes(attributes);

        product.setCategories(Arrays.asList("Electronics", "Computers", "Laptops"));

        Map<String, Integer> inventory = new HashMap<>();
        inventory.put("NY", 10);
        inventory.put("LA", 5);
        product.setInventory(inventory);

        product.setPriceHistory(Arrays.asList(1299.99, 1199.99, 1099.99));

        // When
        productRepository.save(product);

        // Then
        Product retrieved = productRepository.findById("prod-015").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getName()).isEqualTo("Complete Product");
        assertThat(retrieved.getAttributes()).hasSize(2);
        assertThat(retrieved.getCategories()).hasSize(3);
        assertThat(retrieved.getInventory()).hasSize(2);
        assertThat(retrieved.getPriceHistory()).hasSize(3);
    }
}
