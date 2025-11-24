/**
 * Copyright © 2018 spring-data-dynamodb (https://github.com/prasanna0586/spring-data-dynamodb)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p>
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

import java.time.Instant;

/**
 * SDK v2 AttributeConverter that wraps Instant2EpocheDynamoDBMarshaller for SDK_V1_COMPATIBLE mode.
 * This provides backward compatibility for existing users while using SDK v2 APIs.
 */
public class Instant2EpocheAttributeConverter implements AttributeConverter<Instant> {

    private final Instant2EpocheDynamoDBMarshaller marshaller = new Instant2EpocheDynamoDBMarshaller();

    @Override
    public AttributeValue transformFrom(@Nullable Instant input) {
        if (input == null) {
            return AttributeValue.builder().nul(true).build();
        }
        String marshalled = marshaller.marshall(input);
        return AttributeValue.builder().s(marshalled).build();
    }

    @Nullable
    @Override
    public Instant transformTo(@Nullable AttributeValue input) {
        if (input == null || Boolean.TRUE.equals(input.nul())) {
            return null;
        }
        return marshaller.unmarshall(input.s());
    }

    @NonNull
    @Override
    public EnhancedType<Instant> type() {
        return EnhancedType.of(Instant.class);
    }

    @NonNull
    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.S;
    }
}
