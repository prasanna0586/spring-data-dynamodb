package org.socialsignin.spring.data.dynamodb.domain.sample;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;

import java.time.Instant;

/**
 * Custom DynamoDB type converter for java.time.Instant to String and vice versa.
 * Stores Instant values as ISO-8601 formatted strings in DynamoDB.
 */
public class InstantConverter implements DynamoDBTypeConverter<String, Instant> {

    @Override
    public String convert(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    @Override
    public Instant unconvert(String s) {
        return s != null ? Instant.parse(s) : null;
    }
}
