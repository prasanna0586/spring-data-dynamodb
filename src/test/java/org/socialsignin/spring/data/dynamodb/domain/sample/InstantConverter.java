package org.socialsignin.spring.data.dynamodb.domain.sample;

import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.AttributeValueType;
import software.amazon.awssdk.enhanced.dynamodb.EnhancedType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;

/**
 * Custom DynamoDB type converter for java.time.Instant to String and vice versa.
 * Stores Instant values as ISO-8601 formatted strings in DynamoDB.
 */
public class InstantConverter implements AttributeConverter<Instant> {

    @Override
    public AttributeValue transformFrom(Instant instant) {
        if (instant == null) {
            return AttributeValue.builder().nul(true).build();
        }
        return AttributeValue.builder().s(instant.toString()).build();
    }

    @Override
    public Instant transformTo(AttributeValue input) {
        if (input == null || Boolean.TRUE.equals(input.nul())) {
            return null;
        }
        return Instant.parse(input.s());
    }

    @Override
    public EnhancedType<Instant> type() {
        return EnhancedType.of(Instant.class);
    }

    @Override
    public AttributeValueType attributeValueType() {
        return AttributeValueType.S;
    }
}
