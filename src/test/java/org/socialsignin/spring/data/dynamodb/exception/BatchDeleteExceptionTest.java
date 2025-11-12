/**
 * Copyright © 2018 spring-data-dynamodb (https://github.com/prasanna0586/spring-data-dynamodb)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.socialsignin.spring.data.dynamodb.exception;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BatchDeleteExceptionTest {

    // Test entity classes
    static class Product {
        String id;
        String name;

        Product(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    static class Order {
        String orderId;

        Order(String orderId) {
            this.orderId = orderId;
        }
    }

    @Test
    void shouldExposeUnprocessedEntities() {
        Product p1 = new Product("1", "Item1");
        Product p2 = new Product("2", "Item2");
        List<Object> unprocessed = Arrays.asList(p1, p2);

        BatchDeleteException ex = new BatchDeleteException(
                "Failed to delete items",
                unprocessed,
                3,
                null);

        assertEquals(2, ex.getUnprocessedCount());
        assertEquals(unprocessed, ex.getUnprocessedEntities());
    }

    @Test
    void shouldFilterUnprocessedEntitiesByType() {
        Product p1 = new Product("1", "Item1");
        Product p2 = new Product("2", "Item2");
        Order o1 = new Order("order-1");

        List<Object> unprocessed = Arrays.asList(p1, o1, p2);

        BatchDeleteException ex = new BatchDeleteException(
                "Failed to delete items",
                unprocessed,
                5,
                null);

        // Get only Product entities
        List<Product> products = ex.getUnprocessedEntities(Product.class);
        assertEquals(2, products.size());
        assertTrue(products.contains(p1));
        assertTrue(products.contains(p2));

        // Get only Order entities
        List<Order> orders = ex.getUnprocessedEntities(Order.class);
        assertEquals(1, orders.size());
        assertTrue(orders.contains(o1));
    }

    @Test
    void shouldExposeRetriesAttempted() {
        BatchDeleteException ex = new BatchDeleteException(
                "Failed",
                Collections.emptyList(),
                8,
                null);

        assertEquals(8, ex.getRetriesAttempted());
    }

    @Test
    void shouldIndicateWhenOriginalExceptionExists() {
        RuntimeException cause = new RuntimeException("Throttled");

        BatchDeleteException ex = new BatchDeleteException(
                "Failed",
                Collections.emptyList(),
                3,
                cause);

        assertTrue(ex.hasOriginalException());
        assertEquals(cause, ex.getCause());
    }

    @Test
    void shouldIndicateWhenNoOriginalException() {
        BatchDeleteException ex = new BatchDeleteException(
                "Failed",
                Collections.emptyList(),
                3,
                null);

        assertFalse(ex.hasOriginalException());
        assertNull(ex.getCause());
    }

    @Test
    void shouldHandleNullUnprocessedEntities() {
        BatchDeleteException ex = new BatchDeleteException(
                "Failed",
                null,
                0,
                null);

        assertNotNull(ex.getUnprocessedEntities());
        assertTrue(ex.getUnprocessedEntities().isEmpty());
        assertEquals(0, ex.getUnprocessedCount());
    }

    @Test
    void shouldReturnUnmodifiableList() {
        Product p1 = new Product("1", "Item1");
        List<Object> unprocessed = Arrays.asList(p1);

        BatchDeleteException ex = new BatchDeleteException(
                "Failed",
                unprocessed,
                0,
                null);

        List<Object> entities = ex.getUnprocessedEntities();
        assertThrows(UnsupportedOperationException.class, () ->
                entities.add(new Product("2", "Item2")));
    }

    @Test
    void shouldIncludeDetailsInToString() {
        Product p1 = new Product("1", "Item1");
        RuntimeException cause = new RuntimeException("Throttled");

        BatchDeleteException ex = new BatchDeleteException(
                "Failed to delete",
                Arrays.asList(p1),
                5,
                cause);

        String str = ex.toString();
        assertTrue(str.contains("unprocessedCount=1"));
        assertTrue(str.contains("retriesAttempted=5"));
        assertTrue(str.contains("originalException=RuntimeException"));
    }

    @Test
    void shouldHandleToStringWithoutOriginalException() {
        Product p1 = new Product("1", "Item1");

        BatchDeleteException ex = new BatchDeleteException(
                "Failed to delete",
                Arrays.asList(p1),
                3,
                null);

        String str = ex.toString();
        assertTrue(str.contains("unprocessedCount=1"));
        assertTrue(str.contains("retriesAttempted=3"));
        assertFalse(str.contains("originalException="));
    }

    @Test
    void shouldReturnEmptyListWhenNoMatchingType() {
        Product p1 = new Product("1", "Item1");

        BatchDeleteException ex = new BatchDeleteException(
                "Failed",
                Arrays.asList(p1),
                0,
                null);

        List<Order> orders = ex.getUnprocessedEntities(Order.class);
        assertTrue(orders.isEmpty());
    }

    @Test
    void shouldPreserveMessage() {
        String message = "Custom error message";
        BatchDeleteException ex = new BatchDeleteException(
                message,
                Collections.emptyList(),
                0,
                null);

        assertTrue(ex.getMessage().contains(message));
    }

    @Test
    void typeSafeCastingShouldWork() {
        Product p1 = new Product("1", "Item1");
        Product p2 = new Product("2", "Item2");

        BatchDeleteException ex = new BatchDeleteException(
                "Failed",
                Arrays.asList(p1, p2),
                0,
                null);

        // Type-safe retrieval
        List<Product> products = ex.getUnprocessedEntities(Product.class);

        // Can safely use Product-specific methods
        assertEquals("1", products.get(0).id);
        assertEquals("Item1", products.get(0).name);
        assertEquals("2", products.get(1).id);
        assertEquals("Item2", products.get(1).name);
    }

    @Test
    void shouldHandleEmptyUnprocessedList() {
        BatchDeleteException ex = new BatchDeleteException(
                "No unprocessed items",
                Collections.emptyList(),
                8,
                null);

        assertEquals(0, ex.getUnprocessedCount());
        assertTrue(ex.getUnprocessedEntities().isEmpty());
        assertTrue(ex.getUnprocessedEntities(Product.class).isEmpty());
    }
}
