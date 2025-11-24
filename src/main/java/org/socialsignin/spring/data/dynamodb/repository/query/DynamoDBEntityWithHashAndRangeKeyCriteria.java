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

import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;
import org.socialsignin.spring.data.dynamodb.core.MarshallingMode;
import org.socialsignin.spring.data.dynamodb.mapping.DynamoDBMappingContext;
import org.socialsignin.spring.data.dynamodb.query.*;
import org.socialsignin.spring.data.dynamodb.repository.support.DynamoDBIdIsHashAndRangeKeyEntityInformation;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ComparisonOperator;
import software.amazon.awssdk.services.dynamodb.model.Condition;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import java.util.*;
import java.util.Map.Entry;

/**
 * @author Prasanna Kumar Ramachandran
 */
public class DynamoDBEntityWithHashAndRangeKeyCriteria<T, ID> extends AbstractDynamoDBQueryCriteria<T, ID> {

    private Object rangeKeyAttributeValue;
    private Object rangeKeyPropertyValue;
    private final String rangeKeyPropertyName;
    @NonNull
    private final Set<String> indexRangeKeyPropertyNames;
    @NonNull
    private final DynamoDBIdIsHashAndRangeKeyEntityInformation<T, ID> entityInformation;

    protected String getRangeKeyAttributeName() {
        return getAttributeName(getRangeKeyPropertyName());
    }

    protected String getRangeKeyPropertyName() {
        return rangeKeyPropertyName;
    }

    protected boolean isRangeKeyProperty(String propertyName) {
        return rangeKeyPropertyName.equals(propertyName);
    }

    public DynamoDBEntityWithHashAndRangeKeyCriteria(
            @NonNull DynamoDBIdIsHashAndRangeKeyEntityInformation<T, ID> entityInformation,
            TableSchema<T> tableModel,
            DynamoDBMappingContext mappingContext) {

        super(entityInformation, mappingContext);
        this.rangeKeyPropertyName = entityInformation.getRangeKeyPropertyName();
        Set<String> indexRangeProps = entityInformation.getIndexRangeKeyPropertyNames();
        if (indexRangeProps == null) {
            indexRangeProps = new HashSet<>();
        }
        this.indexRangeKeyPropertyNames = indexRangeProps;
        this.entityInformation = entityInformation;
    }

    @NonNull
    public Set<String> getIndexRangeKeyAttributeNames() {
        Set<String> indexRangeKeyAttributeNames = new HashSet<>();
        for (String indexRangeKeyPropertyName : indexRangeKeyPropertyNames) {
            indexRangeKeyAttributeNames.add(getAttributeName(indexRangeKeyPropertyName));
        }
        return indexRangeKeyAttributeNames;
    }

    protected Object getRangeKeyAttributeValue() {
        return rangeKeyAttributeValue;
    }

    protected Object getRangeKeyPropertyValue() {
        return rangeKeyPropertyValue;
    }

    protected boolean isRangeKeySpecified() {
        return getRangeKeyAttributeValue() != null;
    }

    @NonNull
    protected Query<T> buildSingleEntityLoadQuery(DynamoDBOperations dynamoDBOperations) {
        return new SingleEntityLoadByHashAndRangeKeyQuery<>(dynamoDBOperations, entityInformation.getJavaType(),
                getHashKeyPropertyValue(), getRangeKeyPropertyValue());
    }

    @NonNull
    protected Query<Long> buildSingleEntityCountQuery(DynamoDBOperations dynamoDBOperations) {
        return new CountByHashAndRangeKeyQuery<>(dynamoDBOperations, entityInformation.getJavaType(),
                getHashKeyPropertyValue(), getRangeKeyPropertyValue());
    }

    private void checkComparisonOperatorPermittedForCompositeHashAndRangeKey(ComparisonOperator comparisonOperator) {

        if (!ComparisonOperator.EQ.equals(comparisonOperator) && !ComparisonOperator.CONTAINS.equals(comparisonOperator)
                && !ComparisonOperator.BEGINS_WITH.equals(comparisonOperator)) {
            throw new UnsupportedOperationException(
                    "Only EQ,CONTAINS,BEGINS_WITH supported for composite id comparison");
        }

    }

    @SuppressWarnings("unchecked")
    @Override
    public DynamoDBQueryCriteria<T, ID> withSingleValueCriteria(@NonNull String propertyName,
                                                                @NonNull ComparisonOperator comparisonOperator, Object value, Class<?> propertyType) {

        if (entityInformation.isCompositeHashAndRangeKeyProperty(propertyName)) {
            checkComparisonOperatorPermittedForCompositeHashAndRangeKey(comparisonOperator);
            Object hashKey = entityInformation.getHashKey((ID) value);
            Object rangeKey = entityInformation.getRangeKey((ID) value);
            if (hashKey != null) {
                withSingleValueCriteria(getHashKeyPropertyName(), comparisonOperator, hashKey, hashKey.getClass());
            }
            if (rangeKey != null) {
                withSingleValueCriteria(getRangeKeyPropertyName(), comparisonOperator, rangeKey, rangeKey.getClass());
            }
            return this;
        } else {
            return super.withSingleValueCriteria(propertyName, comparisonOperator, value, propertyType);
        }
    }

    @Nullable
    protected List<Condition> getRangeKeyConditions() {
        List<Condition> rangeKeyConditions = null;
        if (isApplicableForGlobalSecondaryIndex() && entityInformation.getGlobalSecondaryIndexNamesByPropertyName()
                .containsKey(getRangeKeyPropertyName())) {
            rangeKeyConditions = getRangeKeyAttributeValue() == null ? null
                    : Collections
                            .singletonList(createSingleValueCondition(getRangeKeyPropertyName(), ComparisonOperator.EQ,
                                    getRangeKeyAttributeValue(), getRangeKeyAttributeValue().getClass(), true));

        }
        return rangeKeyConditions;
    }

    @NonNull
    protected Query<T> buildFinderQuery(@NonNull DynamoDBOperations dynamoDBOperations) {
        if (isApplicableForQuery()) {
            // SDK v2: Use QueryRequest for both GSI and regular queries
            String tableName = dynamoDBOperations.getOverriddenTableName(clazz,
                    entityInformation.getDynamoDBTableName());
            String indexName = isApplicableForGlobalSecondaryIndex() ? getGlobalSecondaryIndexName() : getLocalSecondaryIndexName();

            // Determine the correct range key info based on query type
            String rangeKeyAttrName;
            String rangeKeyPropName;

            // Check if this is an LSI query: getLocalSecondaryIndexName() returns non-null
            String lsiIndexName = getLocalSecondaryIndexName();
            if (lsiIndexName != null) {
                // LSI query: use the LSI range key, not main table range key
                String lsiPropertyName = getLSIPropertyNameWithCondition();
                if (lsiPropertyName != null) {
                    rangeKeyAttrName = getAttributeName(lsiPropertyName);
                    rangeKeyPropName = lsiPropertyName;
                } else {
                    // Fallback to main table range key
                    rangeKeyAttrName = getRangeKeyAttributeName();
                    rangeKeyPropName = this.getRangeKeyPropertyName();
                }
            } else {
                // Main table or GSI query: use main table range key
                rangeKeyAttrName = getRangeKeyAttributeName();
                rangeKeyPropName = this.getRangeKeyPropertyName();
            }

            QueryRequest queryRequest = buildQueryRequest(tableName, indexName,
                    getHashKeyAttributeName(), rangeKeyAttrName, rangeKeyPropName,
                    getHashKeyConditions(), getRangeKeyConditions());
            return new MultipleEntityQueryRequestQuery<>(dynamoDBOperations, entityInformation.getJavaType(),
                    queryRequest);
        } else {
            return new MultipleEntityScanExpressionQuery<>(dynamoDBOperations, clazz, buildScanExpression());
        }
    }

    @NonNull
    protected Query<Long> buildFinderCountQuery(@NonNull DynamoDBOperations dynamoDBOperations, boolean pageQuery) {
        if (isApplicableForQuery()) {
            // SDK v2: Use QueryRequest for both GSI and regular queries
            String tableName = dynamoDBOperations.getOverriddenTableName(clazz,
                    entityInformation.getDynamoDBTableName());
            String indexName = isApplicableForGlobalSecondaryIndex() ? getGlobalSecondaryIndexName() : getLocalSecondaryIndexName();

            // Determine the correct range key info based on query type
            String rangeKeyAttrName;
            String rangeKeyPropName;

            // Check if this is an LSI query: getLocalSecondaryIndexName() returns non-null
            String lsiIndexName = getLocalSecondaryIndexName();
            if (lsiIndexName != null) {
                // LSI query: use the LSI range key, not main table range key
                String lsiPropertyName = getLSIPropertyNameWithCondition();
                if (lsiPropertyName != null) {
                    rangeKeyAttrName = getAttributeName(lsiPropertyName);
                    rangeKeyPropName = lsiPropertyName;
                } else {
                    // Fallback to main table range key
                    rangeKeyAttrName = getRangeKeyAttributeName();
                    rangeKeyPropName = this.getRangeKeyPropertyName();
                }
            } else {
                // Main table or GSI query: use main table range key
                rangeKeyAttrName = getRangeKeyAttributeName();
                rangeKeyPropName = this.getRangeKeyPropertyName();
            }

            QueryRequest queryRequest = buildQueryRequest(tableName, indexName,
                    getHashKeyAttributeName(), rangeKeyAttrName, rangeKeyPropName,
                    getHashKeyConditions(), getRangeKeyConditions());
            return new QueryRequestCountQuery(dynamoDBOperations, queryRequest);
        } else {
            return new ScanExpressionCountQuery<>(dynamoDBOperations, clazz, buildScanExpression(), pageQuery);
        }
    }

    @Override
    public boolean isApplicableForLoad() {
        return attributeConditions.isEmpty() && isHashAndRangeKeySpecified();
    }

    protected boolean isHashAndRangeKeySpecified() {
        return isHashKeySpecified() && isRangeKeySpecified();
    }

    protected boolean isOnlyASingleAttributeConditionAndItIsOnEitherRangeOrIndexRangeKey() {
        boolean isOnlyASingleAttributeConditionAndItIsOnEitherRangeOrIndexRangeKey = false;
        if (!isRangeKeySpecified() && attributeConditions.size() == 1) {
            Entry<String, List<Condition>> conditionsEntry = attributeConditions.entrySet().iterator().next();
            if (conditionsEntry.getKey().equals(getRangeKeyAttributeName())
                    || getIndexRangeKeyAttributeNames().contains(conditionsEntry.getKey())) {
                if (conditionsEntry.getValue().size() == 1) {
                    isOnlyASingleAttributeConditionAndItIsOnEitherRangeOrIndexRangeKey = true;
                }
            }
        }
        return isOnlyASingleAttributeConditionAndItIsOnEitherRangeOrIndexRangeKey;

    }

    @Override
    protected boolean hasIndexHashKeyEqualCondition() {

        boolean hasCondition = super.hasIndexHashKeyEqualCondition();
        if (!hasCondition) {
            if (rangeKeyAttributeValue != null
                    && entityInformation.isGlobalIndexHashKeyProperty(rangeKeyPropertyName)) {
                hasCondition = true;
            }
        }
        return hasCondition;
    }

    @Override
    protected boolean hasIndexRangeKeyCondition() {
        boolean hasCondition = super.hasIndexRangeKeyCondition();
        if (!hasCondition) {
            if (rangeKeyAttributeValue != null
                    && entityInformation.isGlobalIndexRangeKeyProperty(rangeKeyPropertyName)) {
                hasCondition = true;
            }
        }
        return hasCondition;
    }

    protected boolean isApplicableForGlobalSecondaryIndex() {
        boolean global = super.isApplicableForGlobalSecondaryIndex();
        if (global && getRangeKeyAttributeValue() != null && !entityInformation
                .getGlobalSecondaryIndexNamesByPropertyName().containsKey(getRangeKeyPropertyName())) {
            return false;
        }

        return global;

    }

    @Nullable
    protected String getGlobalSecondaryIndexName() {
        // Get the target global secondary index name using the property
        // conditions
        String globalSecondaryIndexName = super.getGlobalSecondaryIndexName();

        // Hash and Range Entities store range key equals conditions as
        // rangeKeyAttributeValue attribute instead of as property condition
        // Check this attribute and if specified in the query conditions and
        // it's the only global secondary index range candidate,
        // then set the index range key to be that associated with the range key
        if (globalSecondaryIndexName == null) {
            if (this.hashKeyAttributeValue == null && getRangeKeyAttributeValue() != null) {
                String[] rangeKeyIndexNames = entityInformation.getGlobalSecondaryIndexNamesByPropertyName()
                        .get(this.getRangeKeyPropertyName());
                globalSecondaryIndexName = rangeKeyIndexNames != null && rangeKeyIndexNames.length > 0
                        ? rangeKeyIndexNames[0]
                        : null;
            }
        }
        return globalSecondaryIndexName;
    }

    /**
     * Get the Local Secondary Index (LSI) name for queries that use an LSI.
     * LSI queries are identified by:
     * 1. Hash key condition + LSI range key condition, OR
     * 2. Hash key only + OrderBy on LSI range key
     *
     * @return LSI index name if applicable, null otherwise
     */
    @Nullable
    protected String getLocalSecondaryIndexName() {
        // Check if any LSI range key property has a condition
        for (String indexRangeKeyPropertyName : indexRangeKeyPropertyNames) {
            String attributeName = getAttributeName(indexRangeKeyPropertyName);
            if (propertyConditions.containsKey(indexRangeKeyPropertyName) ||
                attributeConditions.containsKey(attributeName)) {
                // Found LSI property with condition - get its index name
                String[] indexNames = entityInformation.getGlobalSecondaryIndexNamesByPropertyName()
                        .get(indexRangeKeyPropertyName);
                if (indexNames != null && indexNames.length > 0) {
                    return indexNames[0]; // Return first index name (LSI typically has one index per property)
                }
            }
        }

        // No LSI condition found - check if this is a hash-only query with OrderBy on LSI
        if (isOnlyHashKeySpecified() && sort != null && sort.iterator().hasNext()) {
            String sortProperty = sort.iterator().next().getProperty();
            if (indexRangeKeyPropertyNames.contains(sortProperty)) {
                // Hash-only + OrderBy LSI pattern - get the LSI index name
                String[] indexNames = entityInformation.getGlobalSecondaryIndexNamesByPropertyName()
                        .get(sortProperty);
                if (indexNames != null && indexNames.length > 0) {
                    return indexNames[0];
                }
            }
        }

        return null; // No LSI applicable
    }

    /**
     * Detect which LSI property (if any) has a condition.
     * This is used to determine the correct range key for LSI queries.
     *
     * @return LSI property name that has a condition, or null if none
     */
    @Nullable
    protected String getLSIPropertyNameWithCondition() {
        if (indexRangeKeyPropertyNames == null || indexRangeKeyPropertyNames.isEmpty()) {
            return null;
        }

        // Check which LSI property has a condition
        for (String indexRangeKeyPropertyName : indexRangeKeyPropertyNames) {
            String attributeName = getAttributeName(indexRangeKeyPropertyName);
            if (propertyConditions.containsKey(indexRangeKeyPropertyName) ||
                attributeConditions.containsKey(attributeName)) {
                return indexRangeKeyPropertyName;
            }
        }

        return null;
    }

    public boolean isApplicableForQuery() {

        return isOnlyHashKeySpecified()
                || (isHashKeySpecified() && isOnlyASingleAttributeConditionAndItIsOnEitherRangeOrIndexRangeKey()
                        && comparisonOperatorsPermittedForQuery())
                || isApplicableForGlobalSecondaryIndex();

    }

    @NonNull
    public ScanEnhancedRequest buildScanExpression() {
        ensureNoSort(sort);

        // SDK v2: Build ScanEnhancedRequest using builder pattern
        ScanEnhancedRequest.Builder requestBuilder = ScanEnhancedRequest.builder();

        // Build filter expression from conditions
        List<String> filterParts = new ArrayList<>();
        Map<String, AttributeValue> expressionValues = new HashMap<>();
        Map<String, String> expressionNames = new HashMap<>();
        int valueCounter = 0;
        int nameCounter = 0;

        // Add hash key filter if specified
        if (isHashKeySpecified()) {
            String attributeName = getHashKeyAttributeName();
            String namePlaceholder = "#n" + nameCounter++;
            String valuePlaceholder = ":hval" + valueCounter++;

            // Always use expression attribute name (defensive approach for reserved keywords)
            filterParts.add(namePlaceholder + " = " + valuePlaceholder);
            expressionNames.put(namePlaceholder, attributeName);
            expressionValues.put(valuePlaceholder, convertToAttributeValue(getHashKeyAttributeValue()));
        }

        // Add range key filter if specified
        if (isRangeKeySpecified()) {
            String attributeName = getRangeKeyAttributeName();
            String namePlaceholder = "#n" + nameCounter++;
            String valuePlaceholder = ":rval" + valueCounter++;

            // Always use expression attribute name (defensive approach for reserved keywords)
            filterParts.add(namePlaceholder + " = " + valuePlaceholder);
            expressionNames.put(namePlaceholder, attributeName);
            expressionValues.put(valuePlaceholder, convertToAttributeValue(getRangeKeyAttributeValue()));
        }

        // Convert all attribute conditions to expression format
        for (Map.Entry<String, List<Condition>> conditionEntry : attributeConditions.entrySet()) {
            String attributeName = conditionEntry.getKey();
            for (Condition condition : conditionEntry.getValue()) {
                // Convert Condition to Expression syntax with reserved keyword handling
                String expressionPart = convertConditionToExpression(attributeName, condition, nameCounter, valueCounter, expressionValues, expressionNames);
                filterParts.add(expressionPart);
                // Update counters based on how many values and names were added
                valueCounter = expressionValues.size();
                nameCounter = expressionNames.size();
            }
        }

        // Combine filter parts with AND
        if (!filterParts.isEmpty()) {
            String filterExpression = String.join(" AND ", filterParts);
            Expression.Builder exprBuilder = Expression.builder()
                    .expression(filterExpression)
                    .expressionValues(expressionValues);

            // Add expression attribute names if any were used
            if (!expressionNames.isEmpty()) {
                exprBuilder.expressionNames(expressionNames);
            }

            requestBuilder.filterExpression(exprBuilder.build());
        }

        // Apply limit if present
        if (limit != null) {
            requestBuilder.limit(limit);
        }

        return requestBuilder.build();
    }

    /**
     * Converts SDK v1 Condition object to SDK v2 Expression syntax string.
     * Also populates the expressionValues and expressionNames maps with the necessary values.
     * Uses expression attribute names for all attributes to handle reserved keywords defensively.
     */
    @NonNull
    private String convertConditionToExpression(String attributeName, @NonNull Condition condition, int startNameCounter,
                                                int startValueCounter, @NonNull Map<String, AttributeValue> expressionValues, @NonNull Map<String, String> expressionNames) {

        ComparisonOperator operator = condition.comparisonOperator();
        List<AttributeValue> attributeValueList = condition.attributeValueList();

        // Always use expression attribute name (defensive approach for reserved keywords)
        String namePlaceholder = "#n" + startNameCounter;
        expressionNames.put(namePlaceholder, attributeName);

        switch (operator) {
            case EQ:
                String eqPlaceholder = ":val" + startValueCounter;
                expressionValues.put(eqPlaceholder, attributeValueList.getFirst());
                return namePlaceholder + " = " + eqPlaceholder;

            case NE:
                String nePlaceholder = ":val" + startValueCounter;
                expressionValues.put(nePlaceholder, attributeValueList.getFirst());
                return namePlaceholder + " <> " + nePlaceholder;

            case LT:
                String ltPlaceholder = ":val" + startValueCounter;
                expressionValues.put(ltPlaceholder, attributeValueList.getFirst());
                return namePlaceholder + " < " + ltPlaceholder;

            case LE:
                String lePlaceholder = ":val" + startValueCounter;
                expressionValues.put(lePlaceholder, attributeValueList.getFirst());
                return namePlaceholder + " <= " + lePlaceholder;

            case GT:
                String gtPlaceholder = ":val" + startValueCounter;
                expressionValues.put(gtPlaceholder, attributeValueList.getFirst());
                return namePlaceholder + " > " + gtPlaceholder;

            case GE:
                String gePlaceholder = ":val" + startValueCounter;
                expressionValues.put(gePlaceholder, attributeValueList.getFirst());
                return namePlaceholder + " >= " + gePlaceholder;

            case BETWEEN:
                String betweenPlaceholder1 = ":val" + startValueCounter;
                String betweenPlaceholder2 = ":val" + (startValueCounter + 1);
                expressionValues.put(betweenPlaceholder1, attributeValueList.get(0));
                expressionValues.put(betweenPlaceholder2, attributeValueList.get(1));
                return namePlaceholder + " BETWEEN " + betweenPlaceholder1 + " AND " + betweenPlaceholder2;

            case IN:
                List<String> inPlaceholders = new ArrayList<>();
                for (int i = 0; i < attributeValueList.size(); i++) {
                    String placeholder = ":val" + (startValueCounter + i);
                    expressionValues.put(placeholder, attributeValueList.get(i));
                    inPlaceholders.add(placeholder);
                }
                return namePlaceholder + " IN (" + String.join(", ", inPlaceholders) + ")";

            case BEGINS_WITH:
                String beginsPlaceholder = ":val" + startValueCounter;
                expressionValues.put(beginsPlaceholder, attributeValueList.getFirst());
                return "begins_with(" + namePlaceholder + ", " + beginsPlaceholder + ")";

            case CONTAINS:
                String containsPlaceholder = ":val" + startValueCounter;
                expressionValues.put(containsPlaceholder, attributeValueList.getFirst());
                return "contains(" + namePlaceholder + ", " + containsPlaceholder + ")";

            case NOT_CONTAINS:
                String notContainsPlaceholder = ":val" + startValueCounter;
                expressionValues.put(notContainsPlaceholder, attributeValueList.getFirst());
                return "NOT contains(" + namePlaceholder + ", " + notContainsPlaceholder + ")";

            case NULL:
                return "attribute_not_exists(" + namePlaceholder + ")";

            case NOT_NULL:
                return "attribute_exists(" + namePlaceholder + ")";

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
    private AttributeValue convertToAttributeValue(@NonNull Object value) {
        switch (value) {
            case null -> {
                return AttributeValue.builder().nul(true).build();
            }
            case AttributeValue attributeValue -> {
                // Already an AttributeValue
                return attributeValue;
                // Already an AttributeValue
            }
            case String s -> {
                return AttributeValue.builder().s(s).build();
            }
            case Number number -> {
                return AttributeValue.builder().n(value.toString()).build();
            }
            case Boolean boolValue -> {
                if (mappingContext.getMarshallingMode() == MarshallingMode.SDK_V1_COMPATIBLE) {
                    // SDK v1 compatibility: Boolean stored as "1" or "0" in Number format
                    return AttributeValue.builder().n(boolValue ? "1" : "0").build();
                } else {
                    // SDK v2 native: Boolean stored as BOOL type
                    return AttributeValue.builder().bool(boolValue).build();
                }
            }
            case Date date -> {
                if (mappingContext.getMarshallingMode() == MarshallingMode.SDK_V1_COMPATIBLE) {
                    // SDK v1 compatibility: Date marshalled to ISO format string
                    String marshalledDate = new org.socialsignin.spring.data.dynamodb.marshaller.Date2IsoDynamoDBMarshaller().marshall(date);
                    return AttributeValue.builder().s(marshalledDate).build();
                } else {
                    // SDK v2 native: Date as epoch milliseconds in Number format
                    return AttributeValue.builder().n(String.valueOf(date.getTime())).build();
                }
            }
            case java.time.Instant instant -> {
                // Both SDK v1 and v2 store Instant as String (ISO-8601 format)
                // AWS SDK v2 uses InstantAsStringAttributeConverter by default
                if (mappingContext.getMarshallingMode() == MarshallingMode.SDK_V1_COMPATIBLE) {
                    // SDK v1 compatibility: Instant marshalled to ISO format string with millisecond precision
                    String marshalledDate = new org.socialsignin.spring.data.dynamodb.marshaller.Instant2IsoDynamoDBMarshaller().marshall(instant);
                    return AttributeValue.builder().s(marshalledDate).build();
                } else {
                    // SDK v2 native: Instant as ISO-8601 string (matches AWS SDK v2 InstantAsStringAttributeConverter)
                    // Format: ISO-8601 with nanosecond precision, e.g., "1970-01-01T00:00:00.001Z"
                    return AttributeValue.builder().s(instant.toString()).build();
                }
                // Both SDK v1 and v2 store Instant as String (ISO-8601 format)
                // AWS SDK v2 uses InstantAsStringAttributeConverter by default
            }
            case byte[] bytes -> {
                return AttributeValue.builder().b(software.amazon.awssdk.core.SdkBytes.fromByteArray(bytes)).build();
            }
            default -> {
                // Fallback: convert to string
                return AttributeValue.builder().s(value.toString()).build();
            }
        }

    }

    @NonNull
    public DynamoDBQueryCriteria<T, ID> withRangeKeyEquals(Object value) {
        Assert.notNull(value, "Creating conditions on null range keys not supported: please specify a value for '"
                + getRangeKeyPropertyName() + "'");

        rangeKeyAttributeValue = getPropertyAttributeValue(getRangeKeyPropertyName(), value);
        rangeKeyPropertyValue = value;
        return this;
    }

    @NonNull
    @SuppressWarnings("unchecked")
    @Override
    public DynamoDBQueryCriteria<T, ID> withPropertyEquals(@NonNull String propertyName, Object value, Class<?> propertyType) {
        if (isHashKeyProperty(propertyName)) {
            return withHashKeyEquals(value);
        } else if (isRangeKeyProperty(propertyName)) {
            return withRangeKeyEquals(value);
        } else if (entityInformation.isCompositeHashAndRangeKeyProperty(propertyName)) {
            Assert.notNull(value,
                    "Creating conditions on null composite id properties not supported: please specify a value for '"
                            + propertyName + "'");
            Object hashKey = entityInformation.getHashKey((ID) value);
            Object rangeKey = entityInformation.getRangeKey((ID) value);
            if (hashKey != null) {
                withHashKeyEquals(hashKey);
            }
            if (rangeKey != null) {
                withRangeKeyEquals(rangeKey);
            }
            return this;
        } else {
            Condition condition = createSingleValueCondition(propertyName, ComparisonOperator.EQ, value, propertyType,
                    false);
            return withCondition(propertyName, condition);
        }

    }

    @Override
    protected boolean isOnlyHashKeySpecified() {
        return isHashKeySpecified() && attributeConditions.isEmpty() && !isRangeKeySpecified();
    }

}
