package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.springframework.data.annotation.Id;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Domain model for testing Map and List attributes in DynamoDB.
 *
 * SDK v2 Migration Notes:
 * - SDK v1: @DynamoDBTable → SDK v2: @DynamoDbBean (table name resolved at runtime)
 * - SDK v1: @DynamoDBHashKey → SDK v2: @DynamoDbPartitionKey
 * - SDK v1: @DynamoDBAttribute → SDK v2: @DynamoDbAttribute
 * - Map and List types are natively supported in SDK v2
 * - Map<String, String> stored as DynamoDB Map (M) type
 * - Map<String, Integer> stored as DynamoDB Map (M) type with Number values
 * - List<String> stored as DynamoDB List (L) type
 * - List<Double> stored as DynamoDB List (L) type with Number values
 */
@DynamoDbBean
public class Product {

    @Id
    private String productId;

    private String name;
    private Map<String, String> attributes;  // Key-value pairs (color, size, etc.)
    private List<String> categories;         // List of categories
    private Map<String, Integer> inventory;  // Warehouse -> quantity
    private List<Double> priceHistory;       // Historical prices

    public Product() {
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("productId")
    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    @DynamoDbAttribute("name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Map<String, String> attribute - stored as DynamoDB Map (M) type.
     * SDK v2 natively supports Map types without custom converters.
     */
    @DynamoDbAttribute("attributes")
    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    /**
     * List<String> attribute - stored as DynamoDB List (L) type.
     * SDK v2 natively supports List types without custom converters.
     */
    @DynamoDbAttribute("categories")
    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    /**
     * Map<String, Integer> attribute - stored as DynamoDB Map (M) type with Number values.
     * SDK v2 automatically converts Integer to DynamoDB Number type.
     */
    @DynamoDbAttribute("inventory")
    public Map<String, Integer> getInventory() {
        return inventory;
    }

    public void setInventory(Map<String, Integer> inventory) {
        this.inventory = inventory;
    }

    /**
     * List<Double> attribute - stored as DynamoDB List (L) type with Number values.
     * SDK v2 automatically converts Double to DynamoDB Number type.
     */
    @DynamoDbAttribute("priceHistory")
    public List<Double> getPriceHistory() {
        return priceHistory;
    }

    public void setPriceHistory(List<Double> priceHistory) {
        this.priceHistory = priceHistory;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return Objects.equals(productId, product.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId);
    }

    @Override
    public String toString() {
        return "Product{" +
                "productId='" + productId + '\'' +
                ", name='" + name + '\'' +
                ", attributes=" + attributes +
                ", categories=" + categories +
                ", inventory=" + inventory +
                ", priceHistory=" + priceHistory +
                '}';
    }
}
