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
package org.socialsignin.spring.data.dynamodb.repository.support;

import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import org.junit.jupiter.api.Test;
import org.socialsignin.spring.data.dynamodb.domain.sample.User;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * SDK v2 Migration Notes:
 * - SDK v1: DynamoDBMarshaller → SDK v2: AttributeConverter
 * - SDK v1: getMarshallerForProperty() → SDK v2: getAttributeConverterForProperty()
 * - The DynamoDBEntityMetadataSupport class internally handles SDK v2 conversion
 * - getAttributeConverterForProperty now returns AttributeConverter instead of DynamoDBMarshaller
 */
public class DynamoDBEntityMetadataSupportUnitTest {

    @Test
    public void testGetAttributeConverterForProperty_WhenAnnotationIsOnField_AndReturnsAttributeConverter() {
        DynamoDBEntityMetadataSupport<User, ?> support = new DynamoDBEntityMetadataSupport<>(User.class);
        AttributeConverter<?> fieldAnnotation = support.getAttributeConverterForProperty("joinYear");
        assertNotNull(fieldAnnotation);
    }

    @Test
    public void testGetAttributeConverterForProperty_WhenAnnotationIsOnMethod_AndReturnsAttributeConverter() {
        DynamoDBEntityMetadataSupport<User, ?> support = new DynamoDBEntityMetadataSupport<>(User.class);
        AttributeConverter<?> methodAnnotation = support.getAttributeConverterForProperty("leaveDate");
        assertNotNull(methodAnnotation);
    }
}
