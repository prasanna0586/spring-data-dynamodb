/*
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
package org.socialsignin.spring.data.dynamodb.core;

import org.socialsignin.spring.data.dynamodb.marshaller.*;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

/**
 * Factory for creating TableSchema instances based on the marshalling mode.
 *
 * <p>This factory provides a simple abstraction over TableSchema creation to support
 * both SDK_V2_NATIVE and SDK_V1_COMPATIBLE marshalling modes.
 *
 * <p><b>For SDK_V1_COMPATIBLE mode, users MUST annotate their entity fields with
 * {@code @DynamoDbConvertedBy} to specify the appropriate converter.</b> See the
 * {@link #createTableSchema(Class)} method documentation for details.
 * @author Prasanna Kumar Ramachandran
 * @since 7.0.0
 */
public class TableSchemaFactory {

    /**
     * Default constructor.
     */
    public TableSchemaFactory() {
    }

    /**
     * Creates a TableSchema for the given domain class based on the marshalling mode.
     *
     * <p><b>SDK_V2_NATIVE Mode (Default):</b></p>
     * <p>Uses standard AWS SDK v2 type mappings:
     * <ul>
     * <li>Date → DynamoDB Number (epoch milliseconds)</li>
     * <li>Instant → DynamoDB Number (epoch seconds with nanosecond precision)</li>
     * <li>Boolean → DynamoDB BOOL type</li>
     * </ul>
     * <p>No special annotations required beyond standard SDK v2 annotations
     * ({@code @DynamoDbBean}, {@code @DynamoDbPartitionKey}, etc.)
     *
     * <p><b>SDK_V1_COMPATIBLE Mode:</b></p>
     * <p>Enables backward compatibility with AWS SDK v1 data formats. Users MUST annotate
     * their Date, Instant, and Boolean fields with {@code @DynamoDbConvertedBy} to specify
     * the appropriate converter.
     *
     * <p><b>Required Annotations for SDK_V1_COMPATIBLE Mode:</b></p>
     * <pre>
     * import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
     * import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;
     * import org.socialsignin.spring.data.dynamodb.marshaller.*;
     *
     * {@code @DynamoDbBean}
     * public class User {
     *     {@code @DynamoDbPartitionKey}
     *     private String userId;
     *
     *     // For Date fields (SDK v1 default was ISO-8601 string):
     *     {@code @DynamoDbConvertedBy(Date2IsoAttributeConverter.class)}
     *     private Date createdDate;
     *
     *     // For Date fields with epoch milliseconds (if you used that in SDK v1):
     *     {@code @DynamoDbConvertedBy(Date2EpocheAttributeConverter.class)}
     *     private Date lastModified;
     *
     *     // For Instant fields with ISO-8601 format:
     *     {@code @DynamoDbConvertedBy(Instant2IsoAttributeConverter.class)}
     *     private Instant timestamp;
     *
     *     // For Instant fields with epoch milliseconds:
     *     {@code @DynamoDbConvertedBy(Instant2EpocheAttributeConverter.class)}
     *     private Instant eventTime;
     *
     *     // For Boolean fields (SDK v1 stored as Number "1"/"0"):
     *     {@code @DynamoDbConvertedBy(BooleanNumberAttributeConverter.class)}
     *     private Boolean active;
     *
     *     // For optimistic locking (SDK v1 @DynamoDBVersionAttribute → SDK v2 @DynamoDbVersionAttribute):
     *     {@code @DynamoDbVersionAttribute}
     *     private Long version;
     *
     *     // Getters and setters...
     * }
     * </pre>
     *
     * <p><b>Available Converters for SDK_V1_COMPATIBLE Mode:</b></p>
     * <ul>
     * <li>{@link Date2IsoAttributeConverter} - Date ↔ ISO-8601 String (e.g., "2024-01-15T10:30:00Z")</li>
     * <li>{@link Date2EpocheAttributeConverter} - Date ↔ Epoch milliseconds String (e.g., "1705318200000")</li>
     * <li>{@link Instant2IsoAttributeConverter} - Instant ↔ ISO-8601 String</li>
     * <li>{@link Instant2EpocheAttributeConverter} - Instant ↔ Epoch milliseconds String</li>
     * <li>{@link BooleanNumberAttributeConverter} - Boolean ↔ Number ("1" for true, "0" for false)</li>
     * </ul>
     *
     * <p><b>Important Notes:</b></p>
     * <ul>
     * <li>The marshalling mode affects entity persistence (save/load operations)</li>
     * <li>Query and scan operations use the mode from {@link org.socialsignin.spring.data.dynamodb.mapping.DynamoDBMappingContext}</li>
     * <li>Both modes use the same SDK v2 annotations ({@code @DynamoDbBean}, {@code @DynamoDbPartitionKey}, etc.)</li>
     * <li>SDK_V1_COMPATIBLE mode requires {@code @DynamoDbConvertedBy} annotations for Date/Instant/Boolean fields</li>
     * </ul>
     * @param <T>             The domain class type
     * @param domainClass     The domain class
     * @return A TableSchema instance configured for the specified marshalling mode
     */
    public static <T> TableSchema<T> createTableSchema(Class<T> domainClass) {
        // Both modes use TableSchema.fromBean() which respects @DynamoDbConvertedBy annotations
        // The difference is:
        // - SDK_V2_NATIVE: Uses SDK v2 default converters (no @DynamoDbConvertedBy needed)
        // - SDK_V1_COMPATIBLE: Users must add @DynamoDbConvertedBy annotations to get SDK v1 behavior
        return TableSchema.fromBean(domainClass);
    }

}
