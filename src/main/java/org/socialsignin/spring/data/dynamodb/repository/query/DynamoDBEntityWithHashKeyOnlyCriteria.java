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

import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;
import org.socialsignin.spring.data.dynamodb.core.MarshallingMode;
import org.socialsignin.spring.data.dynamodb.mapping.DynamoDBMappingContext;
import org.socialsignin.spring.data.dynamodb.query.*;
import org.socialsignin.spring.data.dynamodb.repository.support.DynamoDBEntityInformation;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

/**
 * @author Michael Lavelle
 * @author Sebastian Just
 */
public class DynamoDBEntityWithHashKeyOnlyCriteria<T, ID> extends AbstractDynamoDBQueryCriteria<T, ID> {

    private final DynamoDBEntityInformation<T, ID> entityInformation;

    public DynamoDBEntityWithHashKeyOnlyCriteria(DynamoDBEntityInformation<T, ID> entityInformation,
            TableSchema<T> tableModel, DynamoDBMappingContext mappingContext) {
        super(entityInformation, tableModel, mappingContext);
        this.entityInformation = entityInformation;
    }

    protected Query<T> buildSingleEntityLoadQuery(DynamoDBOperations dynamoDBOperations) {
        return new SingleEntityLoadByHashKeyQuery<>(dynamoDBOperations, clazz, getHashKeyPropertyValue());
    }

    protected Query<Long> buildSingleEntityCountQuery(DynamoDBOperations dynamoDBOperations) {
        return new CountByHashKeyQuery<>(dynamoDBOperations, clazz, getHashKeyPropertyValue());
    }

    protected Query<T> buildFinderQuery(DynamoDBOperations dynamoDBOperations) {
        if (isApplicableForGlobalSecondaryIndex()) {

            List<Condition> hashKeyConditions = getHashKeyConditions();
            QueryRequest queryRequest = buildQueryRequest(
                    dynamoDBOperations.getOverriddenTableName(clazz, entityInformation.getDynamoDBTableName()),
                    getGlobalSecondaryIndexName(), getHashKeyAttributeName(), null, null, hashKeyConditions, null);
            return new MultipleEntityQueryRequestQuery<>(dynamoDBOperations, entityInformation.getJavaType(),
                    queryRequest);
        } else {
            return new MultipleEntityScanExpressionQuery<>(dynamoDBOperations, clazz, buildScanExpression());
        }
    }

    protected Query<Long> buildFinderCountQuery(DynamoDBOperations dynamoDBOperations, boolean pageQuery) {
        if (isApplicableForGlobalSecondaryIndex()) {

            List<Condition> hashKeyConditions = getHashKeyConditions();
            QueryRequest queryRequest = buildQueryRequest(
                    dynamoDBOperations.getOverriddenTableName(clazz, entityInformation.getDynamoDBTableName()),
                    getGlobalSecondaryIndexName(), getHashKeyAttributeName(), null, null, hashKeyConditions, null);
            queryRequest = queryRequest.toBuilder().select(Select.COUNT).build();
            return new QueryRequestCountQuery(dynamoDBOperations, queryRequest);

        } else {
            return new ScanExpressionCountQuery<>(dynamoDBOperations, clazz, buildScanExpression(), pageQuery);
        }
    }

    @Override
    protected boolean isOnlyHashKeySpecified() {
        return attributeConditions.size() == 0 && isHashKeySpecified();
    }

    @Override
    public boolean isApplicableForLoad() {
        return isOnlyHashKeySpecified();
    }

    public ScanEnhancedRequest buildScanExpression() {
        ensureNoSort(sort);

        // SDK v2: Build ScanEnhancedRequest using builder pattern
        ScanEnhancedRequest.Builder requestBuilder = ScanEnhancedRequest.builder();

        // Build filter expression from conditions
        List<String> filterParts = new ArrayList<>();
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        int valueCounter = 0;

        // Add hash key filter if specified
        if (isHashKeySpecified()) {
            String valuePlaceholder = ":hval" + valueCounter++;
            filterParts.add(getHashKeyAttributeName() + " = " + valuePlaceholder);
            expressionValues.put(valuePlaceholder, convertToAttributeValue(getHashKeyAttributeValue()));
        }

        // Convert all attribute conditions to expression format
        for (Map.Entry<String, List<Condition>> conditionEntry : attributeConditions.entrySet()) {
            String attributeName = conditionEntry.getKey();
            for (Condition condition : conditionEntry.getValue()) {
                // Convert Condition to Expression syntax
                String expressionPart = convertConditionToExpression(attributeName, condition, valueCounter, expressionValues);
                filterParts.add(expressionPart);
                // Update value counter based on how many values were added
                valueCounter = expressionValues.size();
            }
        }

        // Combine filter parts with AND
        if (!filterParts.isEmpty()) {
            String filterExpression = String.join(" AND ", filterParts);
            Expression.Builder exprBuilder = Expression.builder()
                    .expression(filterExpression)
                    .expressionValues(expressionValues);
            requestBuilder.filterExpression(exprBuilder.build());
        }

        // Apply limit if present
        limit.ifPresent(requestBuilder::limit);

        return requestBuilder.build();
    }

    /**
     * Converts SDK v1 Condition object to SDK v2 Expression syntax string.
     * Also populates the expressionValues map with the necessary attribute values.
     */
    private String convertConditionToExpression(String attributeName, Condition condition, int startValueCounter,
            Map<String, AttributeValue> expressionValues) {

        ComparisonOperator operator = condition.comparisonOperator();
        List<AttributeValue> attributeValueList = condition.attributeValueList();

        switch (operator) {
            case EQ:
                String eqPlaceholder = ":val" + startValueCounter;
                expressionValues.put(eqPlaceholder, attributeValueList.get(0));
                return attributeName + " = " + eqPlaceholder;

            case NE:
                String nePlaceholder = ":val" + startValueCounter;
                expressionValues.put(nePlaceholder, attributeValueList.get(0));
                return attributeName + " <> " + nePlaceholder;

            case LT:
                String ltPlaceholder = ":val" + startValueCounter;
                expressionValues.put(ltPlaceholder, attributeValueList.get(0));
                return attributeName + " < " + ltPlaceholder;

            case LE:
                String lePlaceholder = ":val" + startValueCounter;
                expressionValues.put(lePlaceholder, attributeValueList.get(0));
                return attributeName + " <= " + lePlaceholder;

            case GT:
                String gtPlaceholder = ":val" + startValueCounter;
                expressionValues.put(gtPlaceholder, attributeValueList.get(0));
                return attributeName + " > " + gtPlaceholder;

            case GE:
                String gePlaceholder = ":val" + startValueCounter;
                expressionValues.put(gePlaceholder, attributeValueList.get(0));
                return attributeName + " >= " + gePlaceholder;

            case BETWEEN:
                String betweenPlaceholder1 = ":val" + startValueCounter;
                String betweenPlaceholder2 = ":val" + (startValueCounter + 1);
                expressionValues.put(betweenPlaceholder1, attributeValueList.get(0));
                expressionValues.put(betweenPlaceholder2, attributeValueList.get(1));
                return attributeName + " BETWEEN " + betweenPlaceholder1 + " AND " + betweenPlaceholder2;

            case IN:
                List<String> inPlaceholders = new ArrayList<>();
                for (int i = 0; i < attributeValueList.size(); i++) {
                    String placeholder = ":val" + (startValueCounter + i);
                    expressionValues.put(placeholder, attributeValueList.get(i));
                    inPlaceholders.add(placeholder);
                }
                return attributeName + " IN (" + String.join(", ", inPlaceholders) + ")";

            case BEGINS_WITH:
                String beginsPlaceholder = ":val" + startValueCounter;
                expressionValues.put(beginsPlaceholder, attributeValueList.get(0));
                return "begins_with(" + attributeName + ", " + beginsPlaceholder + ")";

            case CONTAINS:
                String containsPlaceholder = ":val" + startValueCounter;
                expressionValues.put(containsPlaceholder, attributeValueList.get(0));
                return "contains(" + attributeName + ", " + containsPlaceholder + ")";

            case NOT_CONTAINS:
                String notContainsPlaceholder = ":val" + startValueCounter;
                expressionValues.put(notContainsPlaceholder, attributeValueList.get(0));
                return "NOT contains(" + attributeName + ", " + notContainsPlaceholder + ")";

            case NULL:
                return "attribute_not_exists(" + attributeName + ")";

            case NOT_NULL:
                return "attribute_exists(" + attributeName + ")";

            default:
                throw new UnsupportedOperationException("Unsupported comparison operator for scan: " + operator);
        }
    }

    /**
     * Converts a Java object to SDK v2 AttributeValue.
     * Marshalling behavior depends on the configured MarshallingMode:
     * - SDK_V2_NATIVE: Uses AWS SDK v2's native type mappings (Boolean → BOOL)
     * - SDK_V1_COMPATIBLE: Maintains backward compatibility (Boolean → Number "1"/"0", Date/Instant → ISO String)
     */
    private AttributeValue convertToAttributeValue(Object value) {
        if (value == null) {
            return AttributeValue.builder().nul(true).build();
        }

        if (value instanceof AttributeValue) {
            // Already an AttributeValue
            return (AttributeValue) value;
        }

        if (value instanceof String) {
            return AttributeValue.builder().s((String) value).build();
        } else if (value instanceof Number) {
            return AttributeValue.builder().n(value.toString()).build();
        } else if (value instanceof Boolean) {
            if (mappingContext.getMarshallingMode() == MarshallingMode.SDK_V1_COMPATIBLE) {
                // SDK v1 compatibility: Boolean stored as "1" or "0" in Number format
                boolean boolValue = ((Boolean) value).booleanValue();
                return AttributeValue.builder().n(boolValue ? "1" : "0").build();
            } else {
                // SDK v2 native: Boolean stored as BOOL type
                return AttributeValue.builder().bool((Boolean) value).build();
            }
        } else if (value instanceof java.util.Date) {
            if (mappingContext.getMarshallingMode() == MarshallingMode.SDK_V1_COMPATIBLE) {
                // SDK v1 compatibility: Date marshalled to ISO format string
                java.util.Date date = (java.util.Date) value;
                String marshalledDate = new org.socialsignin.spring.data.dynamodb.marshaller.Date2IsoDynamoDBMarshaller().marshall(date);
                return AttributeValue.builder().s(marshalledDate).build();
            } else {
                // SDK v2 native: Date as epoch milliseconds in Number format
                return AttributeValue.builder().n(String.valueOf(((java.util.Date) value).getTime())).build();
            }
        } else if (value instanceof java.time.Instant) {
            // Both SDK v1 and v2 store Instant as String (ISO-8601 format)
            // AWS SDK v2 uses InstantAsStringAttributeConverter by default
            java.time.Instant instant = (java.time.Instant) value;
            if (mappingContext.getMarshallingMode() == MarshallingMode.SDK_V1_COMPATIBLE) {
                // SDK v1 compatibility: Instant marshalled to ISO format string with millisecond precision
                String marshalledDate = new org.socialsignin.spring.data.dynamodb.marshaller.Instant2IsoDynamoDBMarshaller().marshall(instant);
                return AttributeValue.builder().s(marshalledDate).build();
            } else {
                // SDK v2 native: Instant as ISO-8601 string (matches AWS SDK v2 InstantAsStringAttributeConverter)
                // Format: ISO-8601 with nanosecond precision, e.g., "1970-01-01T00:00:00.001Z"
                return AttributeValue.builder().s(instant.toString()).build();
            }
        } else if (value instanceof byte[]) {
            return AttributeValue.builder().b(software.amazon.awssdk.core.SdkBytes.fromByteArray((byte[]) value)).build();
        } else {
            // Fallback: convert to string
            return AttributeValue.builder().s(value.toString()).build();
        }
    }

    @Override
    public DynamoDBQueryCriteria<T, ID> withPropertyEquals(String propertyName, Object value, Class<?> propertyType) {
        if (isHashKeyProperty(propertyName)) {
            return withHashKeyEquals(value);
        } else {
            Condition condition = createSingleValueCondition(propertyName, ComparisonOperator.EQ, value, propertyType,
                    false);
            return withCondition(propertyName, condition);
        }
    }

}
