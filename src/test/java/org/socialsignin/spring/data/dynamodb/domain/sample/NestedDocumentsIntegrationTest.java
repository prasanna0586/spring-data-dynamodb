package org.socialsignin.spring.data.dynamodb.domain.sample;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive integration tests for nested documents using @DynamoDBDocument.
 *
 * Coverage:
 * - Nested objects (@DynamoDBDocument)
 * - List of nested objects
 * - Multiple nested objects in single entity
 * - Updating nested object attributes
 * - Null nested objects
 * - Deep nesting scenarios
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, NestedDocumentsIntegrationTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Nested Documents (@DynamoDBDocument) Integration Tests")
public class NestedDocumentsIntegrationTest {

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    public static class TestAppConfig {
    }

    @Autowired
    private CustomerOrderRepository orderRepository;

    @Autowired
    private DynamoDbClient amazonDynamoDB;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Test 1: Save and retrieve order with nested Address")
    void testNestedAddressObject() {
        // Given
        CustomerOrder order = new CustomerOrder();
        order.setOrderId("order-001");
        order.setCustomerId("customer-001");
        order.setOrderDate(Instant.now());
        order.setStatus("PENDING");

        Address shippingAddress = new Address(
                "123 Main St",
                "Springfield",
                "IL",
                "62701",
                "USA"
        );
        order.setShippingAddress(shippingAddress);

        // When
        orderRepository.save(order);

        // Then
        CustomerOrder retrieved = orderRepository.findById("order-001").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getShippingAddress()).isNotNull();
        assertThat(retrieved.getShippingAddress().getStreet()).isEqualTo("123 Main St");
        assertThat(retrieved.getShippingAddress().getCity()).isEqualTo("Springfield");
        assertThat(retrieved.getShippingAddress().getState()).isEqualTo("IL");
        assertThat(retrieved.getShippingAddress().getZipCode()).isEqualTo("62701");
        assertThat(retrieved.getShippingAddress().getCountry()).isEqualTo("USA");
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Test 2: Save order with multiple nested Address objects")
    void testMultipleNestedAddresses() {
        // Given
        CustomerOrder order = new CustomerOrder();
        order.setOrderId("order-002");
        order.setCustomerId("customer-002");
        order.setOrderDate(Instant.now());
        order.setStatus("PROCESSING");

        Address shippingAddress = new Address("456 Oak Ave", "Portland", "OR", "97201", "USA");
        Address billingAddress = new Address("789 Pine Rd", "Seattle", "WA", "98101", "USA");

        order.setShippingAddress(shippingAddress);
        order.setBillingAddress(billingAddress);

        // When
        orderRepository.save(order);

        // Then
        CustomerOrder retrieved = orderRepository.findById("order-002").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getShippingAddress().getCity()).isEqualTo("Portland");
        assertThat(retrieved.getBillingAddress().getCity()).isEqualTo("Seattle");
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Test 3: Save order with List of nested OrderItem objects")
    void testListOfNestedObjects() {
        // Given
        CustomerOrder order = new CustomerOrder();
        order.setOrderId("order-003");
        order.setCustomerId("customer-003");
        order.setOrderDate(Instant.now());
        order.setStatus("CONFIRMED");

        List<OrderItem> items = Arrays.asList(
                new OrderItem("prod-001", "Laptop", 1, 1299.99),
                new OrderItem("prod-002", "Mouse", 2, 29.99),
                new OrderItem("prod-003", "Keyboard", 1, 89.99)
        );
        order.setItems(items);
        order.setTotalAmount(1449.96);

        // When
        orderRepository.save(order);

        // Then
        CustomerOrder retrieved = orderRepository.findById("order-003").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getItems()).hasSize(3);
        assertThat(retrieved.getItems().get(0).getProductName()).isEqualTo("Laptop");
        assertThat(retrieved.getItems().get(0).getQuantity()).isEqualTo(1);
        assertThat(retrieved.getItems().get(0).getPrice()).isEqualTo(1299.99);
        assertThat(retrieved.getItems().get(1).getProductName()).isEqualTo("Mouse");
        assertThat(retrieved.getItems().get(1).getQuantity()).isEqualTo(2);
        assertThat(retrieved.getItems().get(2).getProductName()).isEqualTo("Keyboard");
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Test 4: Complex order with addresses and items")
    void testComplexOrderWithAllNestedObjects() {
        // Given - Complete order
        CustomerOrder order = new CustomerOrder();
        order.setOrderId("order-004");
        order.setCustomerId("customer-004");
        order.setOrderDate(Instant.now());
        order.setStatus("SHIPPED");

        order.setShippingAddress(new Address("111 First St", "Boston", "MA", "02101", "USA"));
        order.setBillingAddress(new Address("222 Second Ave", "Cambridge", "MA", "02138", "USA"));

        order.setItems(Arrays.asList(
                new OrderItem("prod-004", "Monitor", 2, 299.99),
                new OrderItem("prod-005", "Cable", 3, 15.99)
        ));
        order.setTotalAmount(647.95);

        // When
        orderRepository.save(order);

        // Then
        CustomerOrder retrieved = orderRepository.findById("order-004").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getShippingAddress().getCity()).isEqualTo("Boston");
        assertThat(retrieved.getBillingAddress().getCity()).isEqualTo("Cambridge");
        assertThat(retrieved.getItems()).hasSize(2);
        assertThat(retrieved.getTotalAmount()).isEqualTo(647.95);
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("Test 5: Update nested object attribute")
    void testUpdateNestedObjectAttribute() {
        // Given
        CustomerOrder order = new CustomerOrder();
        order.setOrderId("order-005");
        order.setCustomerId("customer-005");
        order.setOrderDate(Instant.now());
        order.setShippingAddress(new Address("333 Third Blvd", "Miami", "FL", "33101", "USA"));
        orderRepository.save(order);

        // When - Update shipping address city
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("orderId", AttributeValue.builder().s("order-005").build());

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newCity", AttributeValue.builder().s("Fort Lauderdale").build());

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName("CustomerOrder")
                .key(key)
                .updateExpression("SET shippingAddress.city = :newCity")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        amazonDynamoDB.updateItem(updateRequest);

        // Then
        CustomerOrder updated = orderRepository.findById("order-005").orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getShippingAddress().getCity()).isEqualTo("Fort Lauderdale");
        assertThat(updated.getShippingAddress().getStreet()).isEqualTo("333 Third Blvd"); // Unchanged
        assertThat(updated.getShippingAddress().getState()).isEqualTo("FL"); // Unchanged
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("Test 6: Update nested object in list by index")
    void testUpdateNestedObjectInList() {
        // Given
        CustomerOrder order = new CustomerOrder();
        order.setOrderId("order-006");
        order.setCustomerId("customer-006");
        order.setOrderDate(Instant.now());
        order.setItems(Arrays.asList(
                new OrderItem("prod-006", "Phone", 1, 699.99),
                new OrderItem("prod-007", "Case", 1, 19.99)
        ));
        orderRepository.save(order);

        // When - Update quantity of first item
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("orderId", AttributeValue.builder().s("order-006").build());

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newQty", AttributeValue.builder().n("2")
                .build());

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName("CustomerOrder")
                .key(key)
                .updateExpression("SET #items[0].quantity = :newQty")
                .expressionAttributeNames(Collections.singletonMap("#items", "items"))
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        amazonDynamoDB.updateItem(updateRequest);

        // Then
        CustomerOrder updated = orderRepository.findById("order-006").orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(updated.getItems().get(1).getQuantity()).isEqualTo(1); // Unchanged
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("Test 7: Append new nested object to list")
    void testAppendNestedObjectToList() {
        // Given
        CustomerOrder order = new CustomerOrder();
        order.setOrderId("order-007");
        order.setCustomerId("customer-007");
        order.setOrderDate(Instant.now());
        order.setItems(new ArrayList<>(Arrays.asList(
                new OrderItem("prod-008", "Tablet", 1, 499.99)
        )));
        orderRepository.save(order);

        // When - Append new item
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("orderId", AttributeValue.builder().s("order-007").build());

        // Create new OrderItem as Map
        Map<String, AttributeValue> newItemMap = new HashMap<>();
        newItemMap.put("productId", AttributeValue.builder().s("prod-009").build());
        newItemMap.put("productName", AttributeValue.builder().s("Stylus").build());
        newItemMap.put("quantity", AttributeValue.builder().n("1")
                .build());
        newItemMap.put("price", AttributeValue.builder().n("79.99")
                .build());
        newItemMap.put("totalPrice", AttributeValue.builder().n("79.99")
                .build());

        AttributeValue newItemList = AttributeValue.builder().l(AttributeValue.builder().m(newItemMap)
                .build())
                .build();

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newItem", newItemList);

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName("CustomerOrder")
                .key(key)
                .updateExpression("SET #items = list_append(#items, :newItem)")
                .expressionAttributeNames(Collections.singletonMap("#items", "items"))
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        amazonDynamoDB.updateItem(updateRequest);

        // Then
        CustomerOrder updated = orderRepository.findById("order-007").orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getItems()).hasSize(2);
        assertThat(updated.getItems().get(1).getProductName()).isEqualTo("Stylus");
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("Test 8: Null nested object handling")
    void testNullNestedObject() {
        // Given - Order without addresses
        CustomerOrder order = new CustomerOrder();
        order.setOrderId("order-008");
        order.setCustomerId("customer-008");
        order.setOrderDate(Instant.now());
        order.setShippingAddress(null);
        order.setBillingAddress(null);

        // When
        orderRepository.save(order);

        // Then
        CustomerOrder retrieved = orderRepository.findById("order-008").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getShippingAddress()).isNull();
        assertThat(retrieved.getBillingAddress()).isNull();
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("Test 9: Empty list of nested objects")
    void testEmptyListOfNestedObjects() {
        // Given - Order with empty items list
        CustomerOrder order = new CustomerOrder();
        order.setOrderId("order-009");
        order.setCustomerId("customer-009");
        order.setOrderDate(Instant.now());
        order.setItems(new ArrayList<>());

        // When
        orderRepository.save(order);

        // Then - Empty list stored as null in DynamoDB
        CustomerOrder retrieved = orderRepository.findById("order-009").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getItems()).isNullOrEmpty();
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("Test 10: Replace entire nested object")
    void testReplaceEntireNestedObject() {
        // Given
        CustomerOrder order = new CustomerOrder();
        order.setOrderId("order-010");
        order.setCustomerId("customer-010");
        order.setOrderDate(Instant.now());
        order.setShippingAddress(new Address("444 Fourth Way", "Denver", "CO", "80201", "USA"));
        orderRepository.save(order);

        // When - Replace entire shipping address
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("orderId", AttributeValue.builder().s("order-010").build());

        Map<String, AttributeValue> newAddressMap = new HashMap<>();
        newAddressMap.put("street", AttributeValue.builder().s("555 Fifth Plaza").build());
        newAddressMap.put("city", AttributeValue.builder().s("Boulder").build());
        newAddressMap.put("state", AttributeValue.builder().s("CO").build());
        newAddressMap.put("zipCode", AttributeValue.builder().s("80301").build());
        newAddressMap.put("country", AttributeValue.builder().s("USA").build());

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newAddress", AttributeValue.builder().m(newAddressMap)
                .build());

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName("CustomerOrder")
                .key(key)
                .updateExpression("SET shippingAddress = :newAddress")
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        amazonDynamoDB.updateItem(updateRequest);

        // Then
        CustomerOrder updated = orderRepository.findById("order-010").orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getShippingAddress().getStreet()).isEqualTo("555 Fifth Plaza");
        assertThat(updated.getShippingAddress().getCity()).isEqualTo("Boulder");
    }
}
