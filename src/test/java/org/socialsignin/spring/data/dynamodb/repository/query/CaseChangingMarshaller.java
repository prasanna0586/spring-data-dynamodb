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
package org.socialsignin.spring.data.dynamodb.repository.query;

import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * Custom AttributeConverter for SDK v2 that converts strings to lowercase when storing
 * and converts them to uppercase when reading from DynamoDB.
 *
 * Migration from SDK v1 DynamoDBMarshaller:
 * - SDK v1: DynamoDBMarshaller<String> with marshall() and unmarshall() methods
 * - SDK v2: AttributeConverter<String> with transformFrom() and transformTo() methods
 */
public class CaseChangingMarshaller implements AttributeConverter<String> {

    /**
     * Convert from Java object (String) to DynamoDB AttributeValue.
     * Stores the string in lowercase format.
     *
     * @param input The string value to store (will be converted to lowercase)
     * @return AttributeValue containing the lowercase string
     */
    @Override
    public AttributeValue transformFrom(String input) {
        if (input == null) {
            return AttributeValue.builder().nul(true).build();
        }
        return AttributeValue.builder().s(input.toLowerCase()).build();
    }

    /**
     * Convert from DynamoDB AttributeValue to Java object (String).
     * Reads the string and converts it to uppercase.
     *
     * @param input The AttributeValue from DynamoDB (lowercase string)
     * @return The uppercase string
     */
    @Override
    public String transformTo(AttributeValue input) {
        if (input == null || input.nul() != null && input.nul()) {
            return null;
        }
        String value = input.s();
        return value == null ? null : value.toUpperCase();
    }

    /**
     * Specifies the type of the Java object.
     *
     * @return EnhancedType for String
     */
    @Override
    public EnhancedType<String> type() {
        return EnhancedType.of(String.class);
    }

    /**
     * Specifies the DynamoDB attribute type.
     *
     * @return AttributeValueType.S for String
     */
    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.S;
    }
}
