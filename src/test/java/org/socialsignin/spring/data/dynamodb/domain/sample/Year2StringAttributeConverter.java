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

import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * AttributeConverter for marshalling Date to year string format (yyyy).
 * Used in SDK v2 as a replacement for DynamoDBYearMarshaller.
 */
public class Year2StringAttributeConverter implements AttributeConverter<Date> {

    private static final String PATTERN = "yyyy";
    private final DateFormat dateFormat = new SimpleDateFormat(PATTERN);

    @Override
    public AttributeValue transformFrom(Date input) {
        if (input == null) {
            return AttributeValue.builder().nul(true).build();
        }
        synchronized (dateFormat) {
            String yearString = dateFormat.format(input);
            return AttributeValue.builder().s(yearString).build();
        }
    }

    @Override
    public Date transformTo(AttributeValue input) {
        if (input == null || Boolean.TRUE.equals(input.nul())) {
            return null;
        }
        try {
            synchronized (dateFormat) {
                return dateFormat.parse(input.s());
            }
        } catch (ParseException e) {
            throw new RuntimeException("Failed to parse year: " + input.s(), e);
        }
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
