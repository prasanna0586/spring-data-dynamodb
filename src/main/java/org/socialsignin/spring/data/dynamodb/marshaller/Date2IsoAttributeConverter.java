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

import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Date;

/**
 * SDK v2 AttributeConverter that wraps Date2IsoDynamoDBMarshaller for SDK_V1_COMPATIBLE mode.
 * This provides backward compatibility for existing users while using SDK v2 APIs.
 */
public class Date2IsoAttributeConverter implements AttributeConverter<Date> {

    private final Date2IsoDynamoDBMarshaller marshaller = new Date2IsoDynamoDBMarshaller();

    @Override
    public AttributeValue transformFrom(Date input) {
        if (input == null) {
            return AttributeValue.builder().nul(true).build();
        }
        String marshalled = marshaller.marshall(input);
        return AttributeValue.builder().s(marshalled).build();
    }

    @Override
    public Date transformTo(AttributeValue input) {
        if (input == null || Boolean.TRUE.equals(input.nul())) {
            return null;
        }
        return marshaller.unmarshall(Date.class, input.s());
    }

    @Override
    public EnhancedType<Date> type() {
        return EnhancedType.of(Date.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.S;
    }
}
