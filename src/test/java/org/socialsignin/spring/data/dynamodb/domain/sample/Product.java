package org.socialsignin.spring.data.dynamodb.domain.sample;

import com.amazonaws.services.dynamodbv2.datamodeling.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Domain model for testing Map and List attributes in DynamoDB.
 */
@DynamoDBTable(tableName = "Product")
public class Product {

    private String productId;
    private String name;
    private Map<String, String> attributes;  // Key-value pairs (color, size, etc.)
    private List<String> categories;         // List of categories
    private Map<String, Integer> inventory;  // Warehouse -> quantity
    private List<Double> priceHistory;       // Historical prices

    public Product() {
    }

    @DynamoDBHashKey(attributeName = "productId")
    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    @DynamoDBAttribute(attributeName = "name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @DynamoDBAttribute(attributeName = "attributes")
    public Map<String, String> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    @DynamoDBAttribute(attributeName = "categories")
    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    @DynamoDBAttribute(attributeName = "inventory")
    public Map<String, Integer> getInventory() {
        return inventory;
    }

    public void setInventory(Map<String, Integer> inventory) {
        this.inventory = inventory;
    }

    @DynamoDBAttribute(attributeName = "priceHistory")
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
