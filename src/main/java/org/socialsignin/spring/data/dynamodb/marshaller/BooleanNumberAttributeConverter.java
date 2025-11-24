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
package org.socialsignin.spring.data.dynamodb.marshaller;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * SDK v2 AttributeConverter that converts Boolean to Number for SDK_V1_COMPATIBLE mode.
 * This provides backward compatibility with SDK v1's boolean marshalling behavior.
 *
 * <p>Conversion:</p>
 * <ul>
 * <li>true → DynamoDB Number "1"</li>
 * <li>false → DynamoDB Number "0"</li>
 * <li>null → DynamoDB NULL</li>
 * </ul>
 *
 * @since 7.0.0
 */
public class BooleanNumberAttributeConverter implements AttributeConverter<Boolean> {

    @Override
    public AttributeValue transformFrom(@Nullable Boolean input) {
        if (input == null) {
            return AttributeValue.builder().nul(true).build();
        }
        // Convert boolean to number: true = 1, false = 0
        return AttributeValue.builder().n(input ? "1" : "0").build();
    }

    @Nullable
    @Override
    public Boolean transformTo(@Nullable AttributeValue input) {
        if (input == null || Boolean.TRUE.equals(input.nul())) {
            return null;
        }

        // Handle Number type (SDK v1 format)
        if (input.n() != null) {
            return !"0".equals(input.n()) && !"0.0".equals(input.n());
        }

        // Handle BOOL type (in case data is already in SDK v2 format)
        if (input.bool() != null) {
            return input.bool();
        }

        // Handle String type (just in case)
        if (input.s() != null) {
            return "1".equals(input.s()) || "true".equalsIgnoreCase(input.s());
        }

        return false;
    }

    @NonNull
    @Override
    public EnhancedType<Boolean> type() {
        return EnhancedType.of(Boolean.class);
    }

    @NonNull
    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.N;
    }
}
