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
package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.socialsignin.spring.data.dynamodb.utils.DynamoDBLocalResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive type converter integration test for SDK_V2_NATIVE mode.
 *
 * This test validates that all DynamoDB-supported types are correctly converted and marshalled
 * when using SDK_V2_NATIVE mode (native SDK v2 mode - the default).
 * @author Prasanna Kumar Ramachandran
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, TypeConverterV2NativeIntegrationTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@DisplayName("Type Converter Tests - V2_NATIVE Mode")
public class TypeConverterV2NativeIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(TypeConverterV2NativeIntegrationTest.class);

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    // Note: marshallingMode defaults to SDK_V2_NATIVE
    public static class TestAppConfig {
    }

    @Autowired
    private TypeTestEntityRepository repository;

    // NOTE: DynamoDbClient is injected ONLY for testing purposes - to inspect raw DynamoDB storage format
    // and verify how data is actually stored (e.g., booleans as true/false vs 0/1).
    // Normal application code should NOT need direct DynamoDbClient access.
    @Autowired
    private software.amazon.awssdk.services.dynamodb.DynamoDbClient dynamoDbClient;

    // No BeforeEach needed - each test uses unique IDs

    @Test
    @DisplayName("Test all types - complete entity")
    void testAllTypes() {
        // Create entity with all types populated
        TypeTestEntity entity = createFullyPopulatedEntity("test-1");

        // Save
        repository.save(entity);

        // Retrieve
        TypeTestEntity retrieved = repository.findById("test-1").orElse(null);

        // Verify all fields
        assertThat(retrieved).isNotNull();
        verifyAllFields(entity, retrieved);
    }

    @Test
    @DisplayName("Test Boolean types - primitive and wrapper")
    void testBooleanTypes() {
        TypeTestEntity entity = new TypeTestEntity();
        entity.setId("boolean-test");
        entity.setPrimitiveBoolean(true);
        entity.setWrapperBoolean(Boolean.FALSE);

        repository.save(entity);

        // Log raw storage format to verify booleans are stored as true/false (BOOL) in V2_NATIVE mode
        logRawStorage("boolean-test", "primitiveBoolean", "wrapperBoolean");

        TypeTestEntity retrieved = repository.findById("boolean-test").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.isPrimitiveBoolean()).isTrue();
        assertThat(retrieved.getWrapperBoolean()).isFalse();
    }

    @Test
    @DisplayName("Test Boolean wrapper - null handling")
    void testBooleanWrapperNull() {
        TypeTestEntity entity = new TypeTestEntity();
        entity.setId("boolean-null-test");
        entity.setPrimitiveBoolean(false);
        entity.setWrapperBoolean(null);

        repository.save(entity);

        TypeTestEntity retrieved = repository.findById("boolean-null-test").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.isPrimitiveBoolean()).isFalse();
        assertThat(retrieved.getWrapperBoolean()).isNull();
    }

    @Test
    @DisplayName("Test numeric types - primitives")
    void testNumericPrimitives() {
        TypeTestEntity entity = new TypeTestEntity();
        entity.setId("numeric-primitives");
        entity.setPrimitiveByte((byte) 127);
        entity.setPrimitiveShort((short) 32000);
        entity.setPrimitiveInt(123456);
        entity.setPrimitiveLong(9876543210L);
        entity.setPrimitiveFloat(3.14f);
        entity.setPrimitiveDouble(2.71828);

        repository.save(entity);

        TypeTestEntity retrieved = repository.findById("numeric-primitives").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getPrimitiveByte()).isEqualTo((byte) 127);
        assertThat(retrieved.getPrimitiveShort()).isEqualTo((short) 32000);
        assertThat(retrieved.getPrimitiveInt()).isEqualTo(123456);
        assertThat(retrieved.getPrimitiveLong()).isEqualTo(9876543210L);
        assertThat(retrieved.getPrimitiveFloat()).isEqualTo(3.14f);
        assertThat(retrieved.getPrimitiveDouble()).isEqualTo(2.71828);
    }

    @Test
    @DisplayName("Test numeric types - wrappers with null")
    void testNumericWrappersWithNull() {
        TypeTestEntity entity = new TypeTestEntity();
        entity.setId("numeric-wrappers-null");
        entity.setWrapperByte((byte) 100);
        entity.setWrapperShort(null);
        entity.setWrapperInteger(42);
        entity.setWrapperLong(null);
        entity.setWrapperFloat(1.5f);
        entity.setWrapperDouble(null);

        repository.save(entity);

        TypeTestEntity retrieved = repository.findById("numeric-wrappers-null").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getWrapperByte()).isEqualTo((byte) 100);
        assertThat(retrieved.getWrapperShort()).isNull();
        assertThat(retrieved.getWrapperInteger()).isEqualTo(42);
        assertThat(retrieved.getWrapperLong()).isNull();
        assertThat(retrieved.getWrapperFloat()).isEqualTo(1.5f);
        assertThat(retrieved.getWrapperDouble()).isNull();
    }

    @Test
    @DisplayName("Test BigDecimal and BigInteger")
    void testBigNumberTypes() {
        TypeTestEntity entity = new TypeTestEntity();
        entity.setId("big-numbers");
        entity.setBigDecimalValue(new BigDecimal("123456.789"));
        entity.setBigIntegerValue(new BigInteger("987654321098765432109876543210"));

        repository.save(entity);

        TypeTestEntity retrieved = repository.findById("big-numbers").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getBigDecimalValue()).isEqualByComparingTo(new BigDecimal("123456.789"));
        assertThat(retrieved.getBigIntegerValue()).isEqualTo(new BigInteger("987654321098765432109876543210"));
    }

    @Test
    @DisplayName("Test Enum type")
    void testEnumType() {
        TypeTestEntity entity = new TypeTestEntity();
        entity.setId("enum-test");
        entity.setEnumValue(TaskStatus.IN_PROGRESS);

        repository.save(entity);

        TypeTestEntity retrieved = repository.findById("enum-test").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getEnumValue()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("Test Date and Time types - ISO converters")
    void testDateTimeTypes() {
        TypeTestEntity entity = new TypeTestEntity();
        entity.setId("datetime-test");

        Date now = new Date();
        // Use truncatedTo(ChronoUnit.MILLIS) because ISO string conversion loses sub-millisecond precision
        Instant instant = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);

        entity.setDateValue(now);
        entity.setInstantValue(instant);

        repository.save(entity);

        // Log raw storage format to verify Date/Instant are stored as numbers (epoch) in V2_NATIVE mode
        logRawStorage("datetime-test", "dateValue", "instantValue");

        TypeTestEntity retrieved = repository.findById("datetime-test").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getDateValue()).isEqualTo(now);
        assertThat(retrieved.getInstantValue()).isEqualTo(instant);
    }

    @Test
    @DisplayName("Test Date and Time types - Epoch converters")
    void testDateTimeEpochTypes() {
        TypeTestEntity entity = new TypeTestEntity();
        entity.setId("datetime-epoch-test");

        Date now = new Date();
        // Epoch converters use toEpochMilli() which truncates to millisecond precision
        Instant instant = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);

        entity.setDateEpochValue(now);
        entity.setInstantEpochValue(instant);

        repository.save(entity);

        // Log raw storage format to verify Date/Instant with Epoch converters
        logRawStorage("datetime-epoch-test", "dateEpochValue", "instantEpochValue");

        TypeTestEntity retrieved = repository.findById("datetime-epoch-test").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getDateEpochValue()).isEqualTo(now);
        assertThat(retrieved.getInstantEpochValue()).isEqualTo(instant);
    }

    @Test
    @DisplayName("Test String collections - List, Set, Map")
    void testStringCollections() {
        TypeTestEntity entity = new TypeTestEntity();
        entity.setId("string-collections");

        entity.setStringList(Arrays.asList("a", "b", "c"));
        entity.setStringSet(new HashSet<>(Arrays.asList("x", "y", "z")));

        Map<String, String> map = new HashMap<>();
        map.put("key1", "value1");
        map.put("key2", "value2");
        entity.setStringMap(map);

        repository.save(entity);

        TypeTestEntity retrieved = repository.findById("string-collections").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getStringList()).containsExactly("a", "b", "c");
        assertThat(retrieved.getStringSet()).containsExactlyInAnyOrder("x", "y", "z");
        assertThat(retrieved.getStringMap()).containsEntry("key1", "value1");
        assertThat(retrieved.getStringMap()).containsEntry("key2", "value2");
    }

    @Test
    @DisplayName("Test Number collections - List, Set, Map")
    void testNumberCollections() {
        TypeTestEntity entity = new TypeTestEntity();
        entity.setId("number-collections");

        entity.setIntegerList(Arrays.asList(1, 2, 3, 4, 5));
        entity.setIntegerSet(new HashSet<>(Arrays.asList(10, 20, 30)));
        entity.setDoubleList(Arrays.asList(1.1, 2.2, 3.3));
        entity.setLongSet(new HashSet<>(Arrays.asList(100L, 200L, 300L)));

        Map<String, Integer> intMap = new HashMap<>();
        intMap.put("count", 42);
        intMap.put("total", 100);
        entity.setIntegerMap(intMap);

        repository.save(entity);

        TypeTestEntity retrieved = repository.findById("number-collections").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getIntegerList()).containsExactly(1, 2, 3, 4, 5);
        assertThat(retrieved.getIntegerSet()).containsExactlyInAnyOrder(10, 20, 30);
        assertThat(retrieved.getDoubleList()).containsExactly(1.1, 2.2, 3.3);
        assertThat(retrieved.getLongSet()).containsExactlyInAnyOrder(100L, 200L, 300L);
        assertThat(retrieved.getIntegerMap()).containsEntry("count", 42);
    }

    @Test
    @DisplayName("Test Boolean map")
    void testBooleanMap() {
        TypeTestEntity entity = new TypeTestEntity();
        entity.setId("boolean-map");

        Map<String, Boolean> boolMap = new HashMap<>();
        boolMap.put("enabled", true);
        boolMap.put("disabled", false);
        // Note: DynamoDB does not allow null values in maps, so we don't test null here
        entity.setBooleanMap(boolMap);

        repository.save(entity);

        // Log raw storage format to verify map boolean values are stored as true/false in V2_NATIVE mode
        logRawStorage("boolean-map", "booleanMap");

        TypeTestEntity retrieved = repository.findById("boolean-map").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getBooleanMap()).containsEntry("enabled", true);
        assertThat(retrieved.getBooleanMap()).containsEntry("disabled", false);
    }

    @Test
    @DisplayName("Test empty collections")
    void testEmptyCollections() {
        TypeTestEntity entity = new TypeTestEntity();
        entity.setId("empty-collections");

        entity.setStringList(new ArrayList<>());
        // Note: DynamoDB does not allow empty sets, so we skip testing empty sets
        entity.setStringMap(new HashMap<>());

        repository.save(entity);

        TypeTestEntity retrieved = repository.findById("empty-collections").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getStringList()).isEmpty();
        assertThat(retrieved.getStringMap()).isEmpty();
        // stringSet was not set, so it should be null
        assertThat(retrieved.getStringSet()).isNull();
    }

    // Helper methods

    /**
     * Logs the raw DynamoDB storage format for inspection.
     * This is useful for verifying how data is actually stored (e.g., booleans as true/false vs 0/1).
     *
     * @param id The entity ID to inspect
     * @param attributeNames The attribute names to log
     */
    private void logRawStorage(String id, String... attributeNames) {
        java.util.Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> key = new java.util.HashMap<>();
        key.put("id", software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder().s(id).build());

        software.amazon.awssdk.services.dynamodb.model.GetItemResponse response = dynamoDbClient.getItem(
            software.amazon.awssdk.services.dynamodb.model.GetItemRequest.builder()
                .tableName("TypeTestEntity")
                .key(key)
                .build()
        );

        java.util.Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> item = response.item();

        logger.info("\n=== V2_NATIVE: Raw Storage Format for ID: " + id + " ===");
        for (String attrName : attributeNames) {
            software.amazon.awssdk.services.dynamodb.model.AttributeValue value = item.get(attrName);
            logger.info(attrName + ": " + value);
        }
        logger.info("========================================\n");
    }

    private TypeTestEntity createFullyPopulatedEntity(String id) {
        TypeTestEntity entity = new TypeTestEntity();
        entity.setId(id);

        // Booleans
        entity.setPrimitiveBoolean(true);
        entity.setWrapperBoolean(Boolean.FALSE);

        // Numeric primitives
        entity.setPrimitiveByte((byte) 127);
        entity.setPrimitiveShort((short) 32000);
        entity.setPrimitiveInt(123456);
        entity.setPrimitiveLong(9876543210L);
        entity.setPrimitiveFloat(3.14f);
        entity.setPrimitiveDouble(2.71828);

        // Numeric wrappers
        entity.setWrapperByte((byte) 100);
        entity.setWrapperShort((short) 500);
        entity.setWrapperInteger(42);
        entity.setWrapperLong(999L);
        entity.setWrapperFloat(1.5f);
        entity.setWrapperDouble(6.28);

        // Big numbers
        entity.setBigDecimalValue(new BigDecimal("123456.789"));
        entity.setBigIntegerValue(new BigInteger("987654321098765432109876543210"));

        // String
        entity.setStringValue("test-string");

        // Enum
        entity.setEnumValue(TaskStatus.COMPLETED);

        // Date/Time
        entity.setDateValue(new Date());
        entity.setInstantValue(Instant.now());

        // Collections
        entity.setStringList(Arrays.asList("a", "b", "c"));
        entity.setStringSet(new HashSet<>(Arrays.asList("x", "y", "z")));
        Map<String, String> stringMap = new HashMap<>();
        stringMap.put("key1", "value1");
        entity.setStringMap(stringMap);

        entity.setIntegerList(Arrays.asList(1, 2, 3));
        entity.setIntegerSet(new HashSet<>(Arrays.asList(10, 20, 30)));
        Map<String, Integer> intMap = new HashMap<>();
        intMap.put("count", 42);
        entity.setIntegerMap(intMap);

        entity.setDoubleList(Arrays.asList(1.1, 2.2, 3.3));
        entity.setLongSet(new HashSet<>(Arrays.asList(100L, 200L)));
        Map<String, Boolean> boolMap = new HashMap<>();
        boolMap.put("enabled", true);
        entity.setBooleanMap(boolMap);

        return entity;
    }

    private void verifyAllFields(TypeTestEntity expected, TypeTestEntity actual) {
        assertThat(actual.getId()).isEqualTo(expected.getId());
        assertThat(actual.isPrimitiveBoolean()).isEqualTo(expected.isPrimitiveBoolean());
        assertThat(actual.getWrapperBoolean()).isEqualTo(expected.getWrapperBoolean());
        assertThat(actual.getPrimitiveByte()).isEqualTo(expected.getPrimitiveByte());
        assertThat(actual.getPrimitiveShort()).isEqualTo(expected.getPrimitiveShort());
        assertThat(actual.getPrimitiveInt()).isEqualTo(expected.getPrimitiveInt());
        assertThat(actual.getPrimitiveLong()).isEqualTo(expected.getPrimitiveLong());
        assertThat(actual.getPrimitiveFloat()).isEqualTo(expected.getPrimitiveFloat());
        assertThat(actual.getPrimitiveDouble()).isEqualTo(expected.getPrimitiveDouble());
        assertThat(actual.getStringValue()).isEqualTo(expected.getStringValue());
        assertThat(actual.getEnumValue()).isEqualTo(expected.getEnumValue());
    }
}
