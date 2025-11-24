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
package org.socialsignin.spring.data.dynamodb.repository.query;

import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;
import org.socialsignin.spring.data.dynamodb.core.MarshallingMode;
import org.socialsignin.spring.data.dynamodb.mapping.DynamoDBMappingContext;
import org.socialsignin.spring.data.dynamodb.marshaller.Date2IsoDynamoDBMarshaller;
import org.socialsignin.spring.data.dynamodb.marshaller.Instant2IsoDynamoDBMarshaller;
import org.socialsignin.spring.data.dynamodb.query.Query;
import org.socialsignin.spring.data.dynamodb.repository.ExpressionAttribute;
import org.socialsignin.spring.data.dynamodb.repository.QueryConstants;
import org.socialsignin.spring.data.dynamodb.repository.support.DynamoDBEntityInformation;
import org.socialsignin.spring.data.dynamodb.repository.support.DynamoDBHashAndRangeKeyExtractingEntityMetadata;
import org.socialsignin.spring.data.dynamodb.utils.SortHandler;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.domain.Sort.Order;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.*;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.util.*;
import java.util.Map.Entry;

/**
 * Abstract base class for DynamoDB query criteria implementations.
 * Provides common functionality for building and executing DynamoDB queries and scans.
 * @param <T> the entity type
 * @param <ID> the ID type
 * @author Prasanna Kumar Ramachandran
 */
public abstract class AbstractDynamoDBQueryCriteria<T, ID> implements DynamoDBQueryCriteria<T, ID>, SortHandler {

    /**
     * The entity class type for this query criteria.
     */
    protected final Class<T> clazz;
    @NonNull
    private final DynamoDBEntityInformation<T, ID> entityInformation;
    @NonNull
    private final Map<String, String> attributeNamesByPropertyName;
    @Nullable
    private final String hashKeyPropertyName;
    /**
     * The DynamoDB mapping context for type conversions and schema information.
     */
    protected final DynamoDBMappingContext mappingContext;

    /**
     * Multi-value map of attribute conditions, keyed by attribute name.
     * Supports multiple conditions on the same attribute.
     */
    protected final MultiValueMap<String, Condition> attributeConditions;
    /**
     * Multi-value map of property conditions, keyed by property name.
     * Supports multiple conditions on the same property.
     */
    protected final MultiValueMap<String, Condition> propertyConditions;

    /**
     * The DynamoDB attribute value for the hash key.
     * This is the marshalled representation ready for DynamoDB.
     */
    protected Object hashKeyAttributeValue;
    /**
     * The original property value for the hash key (before any type conversions).
     */
    protected Object hashKeyPropertyValue;
    /**
     * The name of the global secondary index (GSI or LSI) to query, or null for main table queries.
     */
    @Nullable
    protected String globalSecondaryIndexName;
    /**
     * The sort specification for the query results.
     * Defaults to unsorted if not specified.
     */
    protected Sort sort = Sort.unsorted();
    /**
     * The projection expression specifying which attributes to return.
     * Null means all attributes are returned.
     */
    @Nullable
    protected String projection = null;
    /**
     * The maximum number of items to return from the query.
     * Null means no limit is applied.
     */
    @Nullable
    protected Integer limit = null;
    /**
     * The filter expression for further filtering query results after key conditions.
     * Null means no filter is applied.
     */
    @Nullable
    protected String filterExpression = null;
    /**
     * Array of expression attribute names for use in filter expressions.
     * Maps placeholder names to actual attribute names.
     */
    protected ExpressionAttribute[] expressionAttributeNames;
    /**
     * Array of expression attribute values for use in filter expressions.
     * Maps placeholder values to actual attribute values.
     */
    protected ExpressionAttribute[] expressionAttributeValues;
    /**
     * Map of expression value parameters to their string representations.
     * Used for parameter substitution in filter expressions.
     */
    protected Map<String, String> mappedExpressionValues;
    /**
     * The consistent read mode for the query (CONSISTENT, EVENTUAL, or DEFAULT).
     * Controls whether strongly consistent reads are used.
     */
    protected QueryConstants.ConsistentReadMode consistentReads = QueryConstants.ConsistentReadMode.DEFAULT;

    /**
     * Determines if these criteria is applicable for a single entity load operation.
     * @return true if these criteria represents a single entity load query, false otherwise
     */
    public abstract boolean isApplicableForLoad();

    /**
     * Builds a DynamoDB QueryRequest from the specified criteria.
     * Constructs key condition expressions for hash and range keys, handles projections,
     * sorting, filtering, and expression attributes.
     *
     * @param tableName the DynamoDB table name
     * @param theIndexName the index name to query (null for main table)
     * @param hashKeyAttributeName the hash key attribute name
     * @param rangeKeyAttributeName the range key attribute name (null if not present)
     * @param rangeKeyPropertyName the range key property name (null if not present)
     * @param hashKeyConditions conditions on the hash key
     * @param rangeKeyConditions conditions on the range key
     * @return a configured QueryRequest ready for DynamoDB
     */
    protected QueryRequest buildQueryRequest(String tableName, String theIndexName, String hashKeyAttributeName,
                                             @Nullable String rangeKeyAttributeName, @Nullable String rangeKeyPropertyName, @Nullable List<Condition> hashKeyConditions,
                                             @Nullable List<Condition> rangeKeyConditions) {

        // SDK v2: Build QueryRequest using modern keyConditionExpression
        QueryRequest queryRequest = QueryRequest.builder()
                .build();
        queryRequest = queryRequest.toBuilder().tableName(tableName).build();
        queryRequest = queryRequest.toBuilder().indexName(theIndexName).build();

        // Build KeyConditionExpression for ALL query types (main table, GSI, and LSI)
        // This is required by DynamoDB SDK v2 for all Query operations
        List<String> keyConditionParts = new ArrayList<>();
        Map<String, AttributeValue> keyExpressionValues = new HashMap<>();
        Map<String, String> keyExpressionNames = new HashMap<>();
        int nameCounter = 0;
        int valueCounter = 0;

        // Build hash key condition (always present for Query operations)
        if (hashKeyConditions != null && !hashKeyConditions.isEmpty()) {
            for (Condition hashKeyCondition : hashKeyConditions) {
                String namePlaceholder = "#pk" + nameCounter++;
                String conditionExpr = buildKeyConditionPart(namePlaceholder, hashKeyCondition, valueCounter, keyExpressionValues);
                keyConditionParts.add(conditionExpr);
                keyExpressionNames.put(namePlaceholder, hashKeyAttributeName);
                valueCounter = keyExpressionValues.size();
            }
        }

        // Build range key condition (if present)
        if (rangeKeyConditions != null && !rangeKeyConditions.isEmpty()) {
            for (Condition rangeKeyCondition : rangeKeyConditions) {
                String namePlaceholder = "#sk" + nameCounter++;
                String conditionExpr = buildKeyConditionPart(namePlaceholder, rangeKeyCondition, valueCounter, keyExpressionValues);
                keyConditionParts.add(conditionExpr);
                keyExpressionNames.put(namePlaceholder, rangeKeyAttributeName);
                valueCounter = keyExpressionValues.size();
            }
        }

        // For GSI queries, add ALL attribute conditions as key conditions
        // For main table queries, ONLY add attribute conditions that match the range key
        if (isApplicableForGlobalSecondaryIndex()) {
            // GSI: Add all attribute conditions as key conditions
            for (Entry<String, List<Condition>> singleAttributeConditions : attributeConditions.entrySet()) {
                for (Condition condition : singleAttributeConditions.getValue()) {
                    String namePlaceholder = "#k" + nameCounter++;
                    String conditionExpr = buildKeyConditionPart(namePlaceholder, condition, valueCounter, keyExpressionValues);
                    keyConditionParts.add(conditionExpr);
                    keyExpressionNames.put(namePlaceholder, singleAttributeConditions.getKey());
                    valueCounter = keyExpressionValues.size();
                }
            }
        } else if (rangeKeyAttributeName != null) {
            // Main table: Only add attribute conditions for the range key (GT, LT, BETWEEN, etc.)
            if (attributeConditions.containsKey(rangeKeyAttributeName)) {
                for (Condition condition : attributeConditions.get(rangeKeyAttributeName)) {
                    String namePlaceholder = "#sk" + nameCounter++;
                    String conditionExpr = buildKeyConditionPart(namePlaceholder, condition, valueCounter, keyExpressionValues);
                    keyConditionParts.add(conditionExpr);
                    keyExpressionNames.put(namePlaceholder, rangeKeyAttributeName);
                    valueCounter = keyExpressionValues.size();
                }
            }
        }

        // Set keyConditionExpression (required for all Query operations in SDK v2)
        if (!keyConditionParts.isEmpty()) {
            String keyConditionExpression = String.join(" AND ", keyConditionParts);
            queryRequest = queryRequest.toBuilder()
                    .keyConditionExpression(keyConditionExpression)
                    .build();

            if (!keyExpressionNames.isEmpty()) {
                queryRequest = queryRequest.toBuilder().expressionAttributeNames(keyExpressionNames).build();
            }
            if (!keyExpressionValues.isEmpty()) {
                queryRequest = queryRequest.toBuilder().expressionAttributeValues(keyExpressionValues).build();
            }
        }

        // Handle projection (select specific attributes)
        if (projection != null) {
            queryRequest = queryRequest.toBuilder().select(Select.SPECIFIC_ATTRIBUTES).build();
            queryRequest = queryRequest.toBuilder().projectionExpression(projection).build();
        } else if (isApplicableForGlobalSecondaryIndex()) {
            // For GSI queries without explicit projection, use ALL_PROJECTED_ATTRIBUTES
            queryRequest = queryRequest.toBuilder().select(Select.ALL_PROJECTED_ATTRIBUTES).build();
        }

        // Determine allowed sort properties based on query type
        // Check if this is an LSI query
        // : hash key is table partition key + index name is set
        boolean isLSIQuery = false;
        if (isApplicableForGlobalSecondaryIndex() && getHashKeyAttributeValue() != null) {
            String queryHashKeyPropertyName = getHashKeyPropertyName();
            boolean isTablePartitionKey = queryHashKeyPropertyName != null && queryHashKeyPropertyName.equals(entityInformation.getHashKeyPropertyName());
            boolean isGSIPartitionKey = entityInformation.isGlobalIndexHashKeyProperty(queryHashKeyPropertyName);
            // LSI: hash key is table partition, not a GSI partition key
            isLSIQuery = isTablePartitionKey && !isGSIPartitionKey;
        }

        List<String> allowedSortProperties = new ArrayList<>();

        if (isLSIQuery) {
            // LSI queries: ONLY allow sorting by the specific LSI range key being queried
            // Detect which LSI property has a condition
            String lsiPropertyWithCondition = null;
            if (entityInformation instanceof DynamoDBHashAndRangeKeyExtractingEntityMetadata<?, ?> compositeKeyEntityInfo) {
                Set<String> indexRangeKeyPropertyNames = compositeKeyEntityInfo.getIndexRangeKeyPropertyNames();

                if (indexRangeKeyPropertyNames != null) {
                    for (String indexRangeKeyPropertyName : indexRangeKeyPropertyNames) {
                        boolean hasCondition = propertyConditions.containsKey(indexRangeKeyPropertyName) ||
                            attributeConditions.containsKey(getAttributeName(indexRangeKeyPropertyName));
                        if (hasCondition) {
                            if (lsiPropertyWithCondition != null) {
                                throw new UnsupportedOperationException(
                                    "Cannot query multiple LSI range keys in a single query. Found conditions on: " +
                                    lsiPropertyWithCondition + " and " + indexRangeKeyPropertyName);
                            }
                            lsiPropertyWithCondition = indexRangeKeyPropertyName;
                        }
                    }
                }
            }

            if (lsiPropertyWithCondition != null) {
                allowedSortProperties.add(lsiPropertyWithCondition);
            } else {
                // Fallback: if we couldn't detect LSI property, use rangeKeyPropertyName
                if (rangeKeyPropertyName != null) {
                    allowedSortProperties.add(rangeKeyPropertyName);
                }
            }
        } else if (isApplicableForGlobalSecondaryIndex()) {
            // GSI queries: ONLY allow sorting by the specific GSI's range key being queried
            // Collect all GSI properties that have conditions
            List<String> gsiPropertiesWithConditions = new ArrayList<>();
            Map<String, String[]> gsiNamesByProperty = entityInformation.getGlobalSecondaryIndexNamesByPropertyName();

            for (Entry<String, List<Condition>> singlePropertyCondition : propertyConditions.entrySet()) {
                String propertyName = singlePropertyCondition.getKey();
                if (gsiNamesByProperty.containsKey(propertyName)) {
                    gsiPropertiesWithConditions.add(propertyName);
                }
            }

            // Find common GSI index that contains ALL properties with conditions
            String commonGsiIndexName = null;
            if (!gsiPropertiesWithConditions.isEmpty()) {
                // Start with the GSI indexes of the first property
                String firstProperty = gsiPropertiesWithConditions.getFirst();
                String[] firstPropertyIndexes = gsiNamesByProperty.get(firstProperty);

                if (firstPropertyIndexes != null) {
                    // For each GSI index of the first property, check if ALL other properties also belong to it
                    for (String candidateIndexName : firstPropertyIndexes) {
                        boolean allPropertiesInThisIndex = true;

                        for (String property : gsiPropertiesWithConditions) {
                            String[] propertyIndexes = gsiNamesByProperty.get(property);
                            boolean propertyInCandidateIndex = false;

                            if (propertyIndexes != null) {
                                for (String indexName : propertyIndexes) {
                                    if (indexName.equals(candidateIndexName)) {
                                        propertyInCandidateIndex = true;
                                        break;
                                    }
                                }
                            }

                            if (!propertyInCandidateIndex) {
                                allPropertiesInThisIndex = false;
                                break;
                            }
                        }

                        if (allPropertiesInThisIndex) {
                            commonGsiIndexName = candidateIndexName;
                            break; // Found a common GSI, use it
                        }
                    }
                }

                // If no common GSI found, these properties belong to different GSIs
                if (commonGsiIndexName == null) {
                    throw new UnsupportedOperationException(
                        "Cannot query properties from different GSI indexes in a single query. Found conditions on: " +
                        String.join(", ", gsiPropertiesWithConditions));
                }

                // Add all properties with conditions to allowed sort properties
                allowedSortProperties.addAll(gsiPropertiesWithConditions);

                // Add all range keys from the common GSI to allowed sort properties
                for (Entry<String, String[]> entry : gsiNamesByProperty.entrySet()) {
                    String propertyName = entry.getKey();
                    String[] indexNames = entry.getValue();

                    if (indexNames != null) {
                        for (String indexName : indexNames) {
                            if (indexName.equals(commonGsiIndexName)) {
                                // Check if this is a range key property for this GSI
                                if (entityInformation.isGlobalIndexRangeKeyProperty(propertyName)) {
                                    allowedSortProperties.add(propertyName);
                                }
                                break;
                            }
                        }
                    }
                }
            }
        } else {
            // Main table queries (no secondary index)
            // Allow sorting by main table range key
            if (rangeKeyPropertyName != null) {
                allowedSortProperties.add(rangeKeyPropertyName);
            }

            // Also allow sorting by any LSI range key for hash-only + OrderBy pattern
            // (e.g., findByCustomerIdOrderByOrderDateAsc)
            if (entityInformation instanceof DynamoDBHashAndRangeKeyExtractingEntityMetadata<?, ?> compositeKeyEntityInfo) {
                Set<String> indexRangeKeyPropertyNames = compositeKeyEntityInfo.getIndexRangeKeyPropertyNames();

                if (indexRangeKeyPropertyNames != null && sort != null) {
                    Iterator<Order> sortIterator = sort.iterator();
                    if (sortIterator.hasNext()) {
                        String sortProperty = sortIterator.next().getProperty();
                        // Only add LSI range key if it's the sort property (hash-only + OrderBy pattern)
                        if (indexRangeKeyPropertyNames.contains(sortProperty)) {
                            allowedSortProperties.add(sortProperty);
                        }
                    }
                }
            }
        }

        List<String> dedupedAllowedProps = new ArrayList<>(new HashSet<>(allowedSortProperties));
        queryRequest = applySortIfSpecified(queryRequest, dedupedAllowedProps);

        queryRequest = applyConsistentReads(queryRequest);

        // SDK v2: Use builder pattern for setting limit
        if (limit != null) {
            queryRequest = queryRequest.toBuilder().limit(limit).build();
        }

        if (filterExpression != null) {
            String filter = filterExpression;
            if (StringUtils.hasLength(filter)) {
                queryRequest = queryRequest.toBuilder().filterExpression(filter).build();

                // SDK v2: Build expression attribute names map and merge with existing key condition names
                if (expressionAttributeNames != null && expressionAttributeNames.length > 0) {
                    Map<String, String> attributeNamesMap = new HashMap<>(queryRequest.expressionAttributeNames() != null
                            ? queryRequest.expressionAttributeNames() : new HashMap<>());
                    for (ExpressionAttribute attribute : expressionAttributeNames) {
                        if (StringUtils.hasLength(attribute.key())) {
                            attributeNamesMap.put(attribute.key(), attribute.value());
                        }
                    }
                    if (!attributeNamesMap.isEmpty()) {
                        queryRequest = queryRequest.toBuilder().expressionAttributeNames(attributeNamesMap).build();
                    }
                }

                // SDK v2: Build expression attribute values map and merge with existing key condition values
                if (expressionAttributeValues != null && expressionAttributeValues.length > 0) {
                    Map<String, AttributeValue> attributeValuesMap = new HashMap<>(queryRequest.expressionAttributeValues() != null
                            ? queryRequest.expressionAttributeValues() : new HashMap<>());
                    for (ExpressionAttribute value : expressionAttributeValues) {
                        if (StringUtils.hasLength(value.key())) {
                            String stringValue;
                            if (mappedExpressionValues.containsKey(value.parameterName())) {
                                stringValue = mappedExpressionValues.get(value.parameterName());
                            } else {
                                stringValue = value.value();
                            }
                            // SDK v2: Use builder pattern for AttributeValue
                            attributeValuesMap.put(value.key(), AttributeValue.builder().s(stringValue).build());
                        }
                    }
                    if (!attributeValuesMap.isEmpty()) {
                        queryRequest = queryRequest.toBuilder().expressionAttributeValues(attributeValuesMap).build();
                    }
                }
            }
        }
        return queryRequest;
    }

    /**
     * Applies the consistent read mode to the QueryRequest.
     * @param queryRequest the QueryRequest to configure
     * @return the QueryRequest with consistent read setting applied
     */
    protected QueryRequest applyConsistentReads(@NonNull QueryRequest queryRequest) {
        return switch (consistentReads) {
            case CONSISTENT -> queryRequest.toBuilder().consistentRead(true).build();
            case EVENTUAL -> queryRequest.toBuilder().consistentRead(false).build();
            default -> queryRequest;
        };
    }

    /**
     * Applies sort order to the QueryRequest if sorting is specified and valid.
     * Validates that sort properties are permitted for the query type.
     * @param queryRequest the QueryRequest to configure
     * @param permittedPropertyNames the properties that are allowed to be sorted
     * @return the QueryRequest with sort order applied
     * @throws UnsupportedOperationException if sorting by invalid properties or multiple attributes
     */
    protected QueryRequest applySortIfSpecified(@NonNull QueryRequest queryRequest, @NonNull List<String> permittedPropertyNames) {
        if (permittedPropertyNames.size() > 2) {
            throw new UnsupportedOperationException("Can only sort by at most a single global hash and range key");
        }

        boolean sortAlreadySet = false;
        for (Order order : sort) {
            if (permittedPropertyNames.contains(order.getProperty())) {
                if (sortAlreadySet) {
                    throw new UnsupportedOperationException("Sorting by multiple attributes not possible");

                }
                // SDK v2: Check keyConditionExpression instead of deprecated keyConditions
                // For GSI queries: sorting with both hash and range conditions requires hash key equality
                // For main table queries: sorting with hash and range conditions is always allowed
                boolean hasMultipleKeyConditions = queryRequest.keyConditionExpression() != null
                        && queryRequest.keyConditionExpression().contains(" AND ");
                if (hasMultipleKeyConditions && isApplicableForGlobalSecondaryIndex() && !hasIndexHashKeyEqualCondition()) {
                    throw new UnsupportedOperationException(
                            "Sorting for global index queries with criteria on both hash and range not possible");

                }
                boolean scanForward = order.getDirection().equals(Direction.ASC);
                queryRequest = queryRequest.toBuilder().scanIndexForward(scanForward).build();
                sortAlreadySet = true;
            } else {
                throw new UnsupportedOperationException("Sorting only possible by " + permittedPropertyNames
                        + " for the criteria specified and not for " + order.getProperty());
            }
        }
        return queryRequest;
    }

    /**
     * Builds a key condition expression part from a Condition object.
     * Supports EQ, LT, LE, GT, GE, BETWEEN, and BEGINS_WITH operators (valid for key conditions).
     * @param namePlaceholder     The expression attribute name placeholder (e.g., "#pk", "#sk")
     * @param condition           The condition to convert
     * @param startValueCounter   Starting counter for value placeholders
     * @param expressionValues    Map to populate with expression attribute values
     * @return The key condition expression string
     */
    @NonNull
    private String buildKeyConditionPart(String namePlaceholder, @NonNull Condition condition, int startValueCounter,
                                         @NonNull Map<String, AttributeValue> expressionValues) {

        ComparisonOperator operator = condition.comparisonOperator();
        List<AttributeValue> attributeValueList = condition.attributeValueList();

        // Validate attributeValueList based on operator requirements
        if (attributeValueList == null || attributeValueList.isEmpty()) {
            if (operator != ComparisonOperator.NULL && operator != ComparisonOperator.NOT_NULL) {
                throw new IllegalArgumentException("Attribute value list cannot be null or empty for operator: " + operator);
            }
        }

        switch (operator) {
            case EQ:
                String eqPlaceholder = ":kval" + startValueCounter;
                expressionValues.put(eqPlaceholder, attributeValueList.getFirst());
                return namePlaceholder + " = " + eqPlaceholder;

            case LT:
                String ltPlaceholder = ":kval" + startValueCounter;
                expressionValues.put(ltPlaceholder, attributeValueList.getFirst());
                return namePlaceholder + " < " + ltPlaceholder;

            case LE:
                String lePlaceholder = ":kval" + startValueCounter;
                expressionValues.put(lePlaceholder, attributeValueList.getFirst());
                return namePlaceholder + " <= " + lePlaceholder;

            case GT:
                String gtPlaceholder = ":kval" + startValueCounter;
                expressionValues.put(gtPlaceholder, attributeValueList.getFirst());
                return namePlaceholder + " > " + gtPlaceholder;

            case GE:
                String gePlaceholder = ":kval" + startValueCounter;
                expressionValues.put(gePlaceholder, attributeValueList.getFirst());
                return namePlaceholder + " >= " + gePlaceholder;

            case BETWEEN:
                if (attributeValueList.size() != 2) {
                    throw new IllegalArgumentException("BETWEEN operator requires exactly 2 values, got: " + attributeValueList.size());
                }
                String betweenPlaceholder1 = ":kval" + startValueCounter;
                String betweenPlaceholder2 = ":kval" + (startValueCounter + 1);
                expressionValues.put(betweenPlaceholder1, attributeValueList.get(0));
                expressionValues.put(betweenPlaceholder2, attributeValueList.get(1));
                return namePlaceholder + " BETWEEN " + betweenPlaceholder1 + " AND " + betweenPlaceholder2;

            case BEGINS_WITH:
                String beginsPlaceholder = ":kval" + startValueCounter;
                expressionValues.put(beginsPlaceholder, attributeValueList.getFirst());
                return "begins_with(" + namePlaceholder + ", " + beginsPlaceholder + ")";

            default:
                throw new UnsupportedOperationException(
                        "Comparison operator " + operator + " not supported for key conditions. " +
                        "Only EQ, LT, LE, GT, GE, BETWEEN, and BEGINS_WITH are allowed.");
        }
    }

    /**
     * Checks if all current attribute conditions use comparison operators that are valid for queries.
     * Valid operators: EQ, LE, LT, GE, GT, BEGINS_WITH, BETWEEN.
     * @return true if all conditions use valid query operators, false otherwise
     */
    public boolean comparisonOperatorsPermittedForQuery() {
        List<ComparisonOperator> comparisonOperatorsPermittedForQuery = Arrays.asList(ComparisonOperator.EQ,
                ComparisonOperator.LE, ComparisonOperator.LT, ComparisonOperator.GE, ComparisonOperator.GT,
                ComparisonOperator.BEGINS_WITH, ComparisonOperator.BETWEEN);

        // Can only query on subset of Conditions
        for (Collection<Condition> conditions : attributeConditions.values()) {
            for (Condition condition : conditions) {
                if (!comparisonOperatorsPermittedForQuery
                        .contains(condition.comparisonOperator())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Gets the conditions on the hash key from the current criteria.
     * Handles both main table queries and index queries (LSI/GSI).
     * @return a list of hash key conditions, or null if no hash key conditions exist
     */
    @Nullable
    protected List<Condition> getHashKeyConditions() {
        List<Condition> hashKeyConditions = null;
        // For LSI: hash key is the table's partition key (not in globalSecondaryIndexNames map), only when using an index
        // For GSI: hash key is a GSI partition key (in globalSecondaryIndexNames map)
        boolean isLSIHashKey = isApplicableForGlobalSecondaryIndex()
                && getGlobalSecondaryIndexName() != null
                && getHashKeyPropertyName() != null
                && getHashKeyPropertyName().equals(entityInformation.getHashKeyPropertyName());
        boolean isGSIHashKey = isApplicableForGlobalSecondaryIndex()
                && entityInformation.getGlobalSecondaryIndexNamesByPropertyName().containsKey(getHashKeyPropertyName());

        // SDK v2: Also build hash key conditions for main table queries (not just LSI/GSI)
        boolean isMainTableHashKey = !isApplicableForGlobalSecondaryIndex();

        if (isLSIHashKey || isGSIHashKey || isMainTableHashKey) {
            if (getHashKeyAttributeValue() != null) {
                hashKeyConditions = Collections
                        .singletonList(createSingleValueCondition(getHashKeyPropertyName(), ComparisonOperator.EQ,
                                getHashKeyAttributeValue(), getHashKeyAttributeValue().getClass(), true));
            }
            if (hashKeyConditions == null && attributeConditions.containsKey(getHashKeyAttributeName())) {
                hashKeyConditions = attributeConditions.get(getHashKeyAttributeName());
            }

        }
        return hashKeyConditions;
    }

    /**
     * Constructor for AbstractDynamoDBQueryCriteria.
     * @param dynamoDBEntityInformation metadata about the DynamoDB entity
     * @param mappingContext the DynamoDB mapping context for type conversions
     */
    public AbstractDynamoDBQueryCriteria(@NonNull DynamoDBEntityInformation<T, ID> dynamoDBEntityInformation,
                                         DynamoDBMappingContext mappingContext) {
        this.clazz = dynamoDBEntityInformation.getJavaType();
        this.attributeConditions = new LinkedMultiValueMap<>();
        this.propertyConditions = new LinkedMultiValueMap<>();
        this.hashKeyPropertyName = dynamoDBEntityInformation.getHashKeyPropertyName();
        this.entityInformation = dynamoDBEntityInformation;
        this.attributeNamesByPropertyName = new HashMap<>();
        // TODO consider adding the table schema to
        // DynamoDBEntityInformation instead
        this.mappingContext = mappingContext;
    }

    /**
     * Gets the first declared index name for the specified attribute from the given candidate indexes.
     * @param indexNamesByAttributeName map of attribute names to their declared index names
     * @param indexNamesToCheck the list of candidate index names to search within
     * @param attributeName the attribute name to find an index for
     * @return the first matching index name, or null if no match is found
     */
    @Nullable
    private String getFirstDeclaredIndexNameForAttribute(@NonNull Map<String, String[]> indexNamesByAttributeName,
                                                         @NonNull List<String> indexNamesToCheck, String attributeName) {
        String indexName = null;
        String[] declaredOrderedIndexNamesForAttribute = indexNamesByAttributeName.get(attributeName);
        for (String declaredOrderedIndexNameForAttribute : declaredOrderedIndexNamesForAttribute) {
            if (indexName == null && indexNamesToCheck.contains(declaredOrderedIndexNameForAttribute)) {
                indexName = declaredOrderedIndexNameForAttribute;
            }
        }

        return indexName;
    }

    /**
     * Gets the global secondary index name that should be used for the query.
     * Performs index selection based on attribute conditions and sort requirements.
     * Prioritizes exact matches and sort-compatible indexes.
     *
     * @return the global secondary index name, or null if the main table should be queried
     * @throws RuntimeException if multiple indexes are defined on the same attribute set
     * @throws UnsupportedOperationException if conditions span multiple different GSI indexes
     */
    @Nullable
    protected String getGlobalSecondaryIndexName() {

        // Lazy evaluate the globalSecondaryIndexName if not already set

        // Check if we have a sort requirement
        boolean hasSortRequirement = false;
        if (sort != null) {
            Iterator<Order> sortIterator = sort.iterator();
            hasSortRequirement = sortIterator.hasNext();
        }

        // Check if hash key is a GSI partition key
        boolean shouldSelectIndex = isShouldSelectIndex(hasSortRequirement);

        if (globalSecondaryIndexName == null && shouldSelectIndex) {
            // Declare map of index names by attribute name which we will populate below -
            // this will be used to determine which index to use if multiple indexes are
            // applicable
            Map<String, String[]> indexNamesByAttributeName = new HashMap<>();
            // <p>
            // Declare map of attribute lists by index name which we will populate below -
            // this will be used to determine whether we have an exact match index for
            // specified attribute conditions
            MultiValueMap<String, String> attributeListsByIndexName = new LinkedMultiValueMap<>();

            // Populate the above maps
            for (Entry<String, String[]> indexNamesForPropertyNameEntry : entityInformation
                    .getGlobalSecondaryIndexNamesByPropertyName().entrySet()) {
                String propertyName = indexNamesForPropertyNameEntry.getKey();
                String attributeName = getAttributeName(propertyName);
                indexNamesByAttributeName.put(attributeName, indexNamesForPropertyNameEntry.getValue());
                for (String indexNameForPropertyName : indexNamesForPropertyNameEntry.getValue()) {
                    attributeListsByIndexName.add(indexNameForPropertyName, attributeName);
                }
            }

            // Declare lists to store matching index names
            List<String> exactMatchIndexNames = new ArrayList<>();
            List<String> partialMatchIndexNames = new ArrayList<>();

            // Populate matching index name lists - an index is either an exact match ( the
            // index attributes match all the specified criteria exactly)
            // or a partial match ( the properties for the specified criteria are contained
            // within the property set for an index )
            for (Entry<String, List<String>> attributeListForIndexNameEntry : attributeListsByIndexName.entrySet()) {
                String indexNameForAttributeList = attributeListForIndexNameEntry.getKey();
                List<String> attributeList = attributeListForIndexNameEntry.getValue();
                // Convert list to set for O(1) containment checks instead of O(n)
                Set<String> attributeSet = new HashSet<>(attributeList);
                if (attributeSet.containsAll(attributeConditions.keySet())) {
                    if (attributeConditions.keySet().containsAll(attributeSet)) {
                        exactMatchIndexNames.add(indexNameForAttributeList);
                    } else {
                        partialMatchIndexNames.add(indexNameForAttributeList);
                    }
                }
            }

            // Check if we have a sort requirement
            String sortPropertyName = null;
            if (sort != null) {
                Iterator<Order> sortIterator = sort.iterator();
                if (sortIterator.hasNext()) {
                    sortPropertyName = sortIterator.next().getProperty();
                }
            }

            // If we have a sort requirement, filter candidates to prefer indexes that support it
            if (sortPropertyName != null) {
                final String finalSortPropertyName = sortPropertyName;

                // Filter exact matches to prefer those that have the sort property as a range key
                List<String> sortCompatibleExactMatches = exactMatchIndexNames.stream()
                        .filter(indexName -> {
                            List<String> indexAttributes = attributeListsByIndexName.get(indexName);
                            return indexAttributes != null &&
                                   indexAttributes.contains(getAttributeName(finalSortPropertyName)) &&
                                   entityInformation.isGlobalIndexRangeKeyProperty(finalSortPropertyName);
                        })
                        .collect(java.util.stream.Collectors.toList());

                // Filter partial matches similarly
                List<String> sortCompatiblePartialMatches = partialMatchIndexNames.stream()
                        .filter(indexName -> {
                            List<String> indexAttributes = attributeListsByIndexName.get(indexName);
                            return indexAttributes != null &&
                                   indexAttributes.contains(getAttributeName(finalSortPropertyName)) &&
                                   entityInformation.isGlobalIndexRangeKeyProperty(finalSortPropertyName);
                        })
                        .collect(java.util.stream.Collectors.toList());

                // Prefer sort-compatible indexes if available
                if (!sortCompatibleExactMatches.isEmpty()) {
                    // We have sort-compatible exact matches - use them
                    exactMatchIndexNames = sortCompatibleExactMatches;
                    partialMatchIndexNames.clear(); // Clear partial matches since we have exact matches
                } else if (!sortCompatiblePartialMatches.isEmpty()) {
                    // We have NO sort-compatible exact matches, but we DO have sort-compatible partial matches
                    // In this case, the sort-compatible partial match is BETTER than a non-sort-compatible exact match
                    // Example: merchantId-transactionDate-index (partial, sort-compatible) is better than
                    //          merchantId-index (exact, NOT sort-compatible) for findByMerchantIdOrderByTransactionDateAsc
                    exactMatchIndexNames.clear(); // Clear non-sort-compatible exact matches
                    exactMatchIndexNames = sortCompatiblePartialMatches; // Promote sort-compatible partial matches
                    partialMatchIndexNames.clear();
                }
            }

            if (exactMatchIndexNames.size() > 1) {
                throw new RuntimeException(
                        "Multiple indexes defined on same attribute set:" + attributeConditions.keySet());
            } else if (exactMatchIndexNames.size() == 1) {
                globalSecondaryIndexName = exactMatchIndexNames.getFirst();
            } else if (partialMatchIndexNames.size() > 1) {
                if (attributeConditions.size() == 1) {
                    globalSecondaryIndexName = getFirstDeclaredIndexNameForAttribute(indexNamesByAttributeName,
                            partialMatchIndexNames, attributeConditions.keySet().iterator().next());
                }
                if (globalSecondaryIndexName == null) {
                    globalSecondaryIndexName = partialMatchIndexNames.getFirst();
                }
            } else if (partialMatchIndexNames.size() == 1) {
                globalSecondaryIndexName = partialMatchIndexNames.getFirst();
            }
        }
        return globalSecondaryIndexName;
    }

    /**
     * Determines whether index selection should be performed based on query conditions and sort requirements.
     * @param hasSortRequirement whether the query has a sort requirement
     * @return true if index selection should be performed, false to use main table
     */
    private boolean isShouldSelectIndex(boolean hasSortRequirement) {
        boolean hashKeyIsGSIPartitionKey = entityInformation.getGlobalSecondaryIndexNamesByPropertyName()
                .containsKey(getHashKeyPropertyName());

        // Run index selection if:
        // 1. We have attribute conditions (traditional GSI query), OR
        // 2. Hash key is a GSI partition key AND we have a sort requirement (hash-only GSI query with OrderBy)
        return (attributeConditions != null && !attributeConditions.isEmpty()) ||
               (hashKeyIsGSIPartitionKey && hasSortRequirement);
    }

    /**
     * Checks if the specified property name is the hash key property.
     * @param propertyName the property name to check
     * @return true if the property is the hash key, false otherwise
     */
    protected boolean isHashKeyProperty(String propertyName) {
        return hashKeyPropertyName != null && hashKeyPropertyName.equals(propertyName);
    }

    /**
     * Gets the hash key property name.
     * @return the hash key property name, or null if not set
     */
    @Nullable
    protected String getHashKeyPropertyName() {
        return hashKeyPropertyName;
    }

    /**
     * Gets the DynamoDB attribute name for the hash key property.
     * @return the hash key attribute name
     */
    protected String getHashKeyAttributeName() {
        return getAttributeName(getHashKeyPropertyName());
    }

    /**
     * Checks if there is an equality condition on the index hash key.
     * Handles both GSI and LSI hash keys.
     * @return true if an equality condition exists on the index hash key, false otherwise
     */
    protected boolean hasIndexHashKeyEqualCondition() {
        boolean hasIndexHashKeyEqualCondition = false;
        for (Map.Entry<String, List<Condition>> propertyConditionList : propertyConditions.entrySet()) {
            // Only consider table partition key if we're actually using an index (LSI case)
            boolean isGSIHashKey = entityInformation.isGlobalIndexHashKeyProperty(propertyConditionList.getKey());
            boolean isLSIHashKey = getGlobalSecondaryIndexName() != null
                    && propertyConditionList.getKey().equals(entityInformation.getHashKeyPropertyName());
            if (isGSIHashKey || isLSIHashKey) {
                for (Condition condition : propertyConditionList.getValue()) {
                    if (condition.comparisonOperator().equals(ComparisonOperator.EQ)) {
                        hasIndexHashKeyEqualCondition = true;
                    }
                }
            }
        }
        if (hashKeyAttributeValue != null) {
            // For LSI: hash key is the table's partition key (only when an index is being used)
            // For GSI: hash key is a GSI partition key
            boolean isTablePartitionKey = getGlobalSecondaryIndexName() != null
                    && hashKeyPropertyName != null
                    && hashKeyPropertyName.equals(entityInformation.getHashKeyPropertyName());
            boolean isGSIPartitionKey = entityInformation.isGlobalIndexHashKeyProperty(hashKeyPropertyName);
            if (isTablePartitionKey || isGSIPartitionKey) {
                hasIndexHashKeyEqualCondition = true;
            }
        }
        return hasIndexHashKeyEqualCondition;
    }

    /**
     * Checks if there is a condition on an index range key.
     * Handles both GSI and LSI range keys.
     * @return true if a condition exists on an index range key, false otherwise
     */
    protected boolean hasIndexRangeKeyCondition() {
        boolean hasIndexRangeKeyCondition = false;
        for (Map.Entry<String, List<Condition>> propertyConditionList : propertyConditions.entrySet()) {
            if (entityInformation.isGlobalIndexRangeKeyProperty(propertyConditionList.getKey())) {
                hasIndexRangeKeyCondition = true;
            }
        }
        if (hashKeyAttributeValue != null && entityInformation.isGlobalIndexRangeKeyProperty(hashKeyPropertyName)) {
            hasIndexRangeKeyCondition = true;
        }
        return hasIndexRangeKeyCondition;
    }

    /**
     * Determines if these criteria is applicable for querying a global secondary index (GSI or LSI).
     * Validates that the selected index has appropriate conditions and attributes.
     * @return true if the criteria can be executed against a secondary index, false if main table should be used
     */
    protected boolean isApplicableForGlobalSecondaryIndex() {
        boolean global = this.getGlobalSecondaryIndexName() != null;
        if (global && getHashKeyAttributeValue() != null) {
            // Check if the hash key used in the query is valid for this index
            // Valid cases:
            // 1. LSI: hash key is the table's partition key (not in globalSecondaryIndexNames map)
            // 2. GSI: hash key is a GSI partition key (in globalSecondaryIndexNames map)
            String queryHashKeyPropertyName = getHashKeyPropertyName();
            boolean isTablePartitionKey = queryHashKeyPropertyName != null && queryHashKeyPropertyName.equals(entityInformation.getHashKeyPropertyName());
            boolean isGSIPartitionKey = entityInformation.getGlobalSecondaryIndexNamesByPropertyName().containsKey(queryHashKeyPropertyName);

            // If the hash key is neither the table's partition key (LSI case) nor a GSI partition key, reject
            if (!isTablePartitionKey && !isGSIPartitionKey) {
                return false;
            }
        }

        int attributeConditionCount = attributeConditions.size();
        boolean attributeConditionsAppropriate = hasIndexHashKeyEqualCondition()
                && (attributeConditionCount == 1 || (attributeConditionCount == 2 && hasIndexRangeKeyCondition()));
        return global && (attributeConditionCount == 0 || attributeConditionsAppropriate)
                && comparisonOperatorsPermittedForQuery();

    }

    /**
     * Sets the hash key value for the query.
     * @param value the hash key value to set
     * @return this query criteria for method chaining
     * @throws IllegalArgumentException if value is null
     */
    @NonNull
    public DynamoDBQueryCriteria<T, ID> withHashKeyEquals(Object value) {
        Assert.notNull(value, "Creating conditions on null hash keys not supported: please specify a value for '"
                + getHashKeyPropertyName() + "'");

        hashKeyAttributeValue = getPropertyAttributeValue(getHashKeyPropertyName(), value);
        hashKeyPropertyValue = value;
        return this;
    }

    /**
     * Checks if a hash key value has been specified.
     * @return true if a hash key value is set, false otherwise
     */
    public boolean isHashKeySpecified() {
        return getHashKeyAttributeValue() != null;
    }

    /**
     * Gets the hash key attribute value.
     * @return the hash key attribute value
     */
    public Object getHashKeyAttributeValue() {
        return hashKeyAttributeValue;
    }

    /**
     * Gets the original hash key property value (before any type conversions).
     * @return the hash key property value
     */
    public Object getHashKeyPropertyValue() {
        return hashKeyPropertyValue;
    }

    /**
     * Gets the DynamoDB attribute name for the specified property name.
     * Caches the result for performance.
     * @param propertyName the property name to convert
     * @return the DynamoDB attribute name
     */
    protected String getAttributeName(String propertyName) {
        String attributeName = attributeNamesByPropertyName.get(propertyName);
        if (attributeName == null) {
            attributeName = entityInformation.getOverriddenAttributeName(propertyName).orElse(propertyName);
            attributeNamesByPropertyName.put(propertyName, attributeName);
        }
        return attributeName;

    }

    /**
     * Adds a BETWEEN criteria on the specified property.
     * Implementation of interface method.
     * @param propertyName the property name to filter on
     * @param value1 the lower bound value (inclusive)
     * @param value2 the upper bound value (inclusive)
     * @param type the type of the property
     * @return this query criteria for method chaining
     */
    @NonNull
    @Override
    public DynamoDBQueryCriteria<T, ID> withPropertyBetween(@NonNull String propertyName, Object value1, Object value2,
                                                            Class<?> type) {
        Condition condition = createCollectionCondition(propertyName, ComparisonOperator.BETWEEN,
                Arrays.asList(value1, value2), type);
        return withCondition(propertyName, condition);
    }

    /**
     * Adds an IN (membership) criteria on the specified property.
     * Implementation of interface method.
     * @param propertyName the property name to filter on
     * @param value an iterable of values to match against
     * @param propertyType the type of the property
     * @return this query criteria for method chaining
     */
    @NonNull
    @Override
    public DynamoDBQueryCriteria<T, ID> withPropertyIn(@NonNull String propertyName, @NonNull Iterable<?> value, Class<?> propertyType) {

        Condition condition = createCollectionCondition(propertyName, ComparisonOperator.IN, value, propertyType);
        return withCondition(propertyName, condition);
    }

    /**
     * Adds a single-value criteria to the query using the specified comparison operator.
     * Implementation of interface method.
     * @param propertyName the property name to filter on
     * @param comparisonOperator the comparison operator to apply
     * @param value the value to compare against
     * @param propertyType the type of the property
     * @return this query criteria for method chaining
     */
    @Override
    public DynamoDBQueryCriteria<T, ID> withSingleValueCriteria(@NonNull String propertyName,
                                                                @NonNull ComparisonOperator comparisonOperator, Object value, Class<?> propertyType) {
        if (comparisonOperator.equals(ComparisonOperator.EQ)) {
            return withPropertyEquals(propertyName, value, propertyType);
        } else {
            Condition condition = createSingleValueCondition(propertyName, comparisonOperator, value, propertyType,
                    false);
            return withCondition(propertyName, condition);
        }
    }

    /**
     * Builds a query object that can be executed to fetch results.
     * Implementation of interface method.
     * @param dynamoDBOperations the DynamoDB operations instance to use for execution
     * @return a Query object configured with the criteria
     */
    @Override
    public Query<T> buildQuery(DynamoDBOperations dynamoDBOperations) {
        if (isApplicableForLoad()) {
            return buildSingleEntityLoadQuery(dynamoDBOperations);
        } else {
            return buildFinderQuery(dynamoDBOperations);
        }
    }

    /**
     * Builds a count query that returns the number of matching items.
     * Implementation of interface method.
     * @param dynamoDBOperations the DynamoDB operations instance to use for execution
     * @param pageQuery whether this is for paginated query counting
     * @return a Query object that returns the count of matching items
     */
    @Override
    public Query<Long> buildCountQuery(DynamoDBOperations dynamoDBOperations, boolean pageQuery) {
        if (isApplicableForLoad()) {
            return buildSingleEntityCountQuery(dynamoDBOperations);
        } else {
            return buildFinderCountQuery(dynamoDBOperations, pageQuery);
        }
    }

    /**
     * Builds a query for loading a single entity by its keys.
     * Must be implemented by subclasses.
     * @param dynamoDBOperations the DynamoDB operations instance
     * @return a Query object for single entity load
     */
    protected abstract Query<T> buildSingleEntityLoadQuery(DynamoDBOperations dynamoDBOperations);

    /**
     * Builds a count query for a single entity.
     * Must be implemented by subclasses.
     * @param dynamoDBOperations the DynamoDB operations instance
     * @return a count Query object
     */
    protected abstract Query<Long> buildSingleEntityCountQuery(DynamoDBOperations dynamoDBOperations);

    /**
     * Builds a finder query that returns zero or more entities matching the criteria.
     * Must be implemented by subclasses.
     * @param dynamoDBOperations the DynamoDB operations instance
     * @return a Query object for finder operations
     */
    protected abstract Query<T> buildFinderQuery(DynamoDBOperations dynamoDBOperations);

    /**
     * Builds a count query for finder operations.
     * Must be implemented by subclasses.
     * @param dynamoDBOperations the DynamoDB operations instance
     * @param pageQuery whether this is for paginated query counting
     * @return a count Query object
     */
    protected abstract Query<Long> buildFinderCountQuery(DynamoDBOperations dynamoDBOperations, boolean pageQuery);

    /**
     * Checks if only the hash key is specified without any other conditions.
     * Must be implemented by subclasses.
     * @return true if only hash key is specified, false otherwise
     */
    protected abstract boolean isOnlyHashKeySpecified();

    /**
     * Adds a criteria with no value (e.g., NULL or NOT_NULL checks).
     * Implementation of interface method.
     * @param propertyName the property name to filter on
     * @param comparisonOperator the comparison operator for no-value conditions
     * @return this query criteria for method chaining
     */
    @NonNull
    @Override
    public DynamoDBQueryCriteria<T, ID> withNoValuedCriteria(@NonNull String propertyName,
                                                             ComparisonOperator comparisonOperator) {
        Condition condition = createNoValueCondition(comparisonOperator);
        return withCondition(propertyName, condition);

    }

    /**
     * Adds a condition to the query criteria for the specified property.
     * @param propertyName the property name to add the condition for
     * @param condition the condition to add
     * @return this query criteria for method chaining
     */
    @NonNull
    public DynamoDBQueryCriteria<T, ID> withCondition(@NonNull String propertyName, Condition condition) {
        attributeConditions.add(getAttributeName(propertyName), condition);
        propertyConditions.add(propertyName, condition);

        return this;
    }

    /**
     * Gets the property attribute value, applying any custom attribute converters if configured.
     * @param <V> the type of the property value
     * @param propertyName the property name
     * @param value the property value
     * @return the converted attribute value
     */
    @SuppressWarnings("unchecked")
    protected <V> Object getPropertyAttributeValue(final String propertyName, final V value) {
        // SDK v2: Check for custom attribute converters configured via @DynamoDbConvertedBy
        AttributeConverter<?> attributeConverter = entityInformation.getAttributeConverterForProperty(propertyName);

        if (attributeConverter != null) {
            // Cast is safe because the converter is for this specific property
            AttributeConverter<V> typedConverter = (AttributeConverter<V>) attributeConverter;
            // Convert the value using the custom converter
            return typedConverter.transformFrom(value);
        }

        // For standard types without custom converters, return the value as-is.
        // The TableSchema in SDK v2's Enhanced Client handles type conversions internally
        // when building and executing queries.
        return value;
    }

    /**
     * Creates a Condition object for operators that do not require a value.
     * Used for NULL and NOT_NULL comparison operators.
     * @param <V> the type parameter (unused but preserved for API consistency)
     * @param comparisonOperator the comparison operator (typically NULL or NOT_NULL)
     * @return a Condition object with the specified operator but no attribute values
     */
    protected <V> Condition createNoValueCondition(ComparisonOperator comparisonOperator) {

        return Condition.builder().comparisonOperator(comparisonOperator)
                .build();
    }

    @NonNull
    private List<String> getNumberListAsStringList(@NonNull List<Number> numberList) {
        List<String> list = new ArrayList<>();
        for (Number number : numberList) {
            if (number != null) {
                list.add(number.toString());
            } else {
                list.add(null);
            }
        }
        return list;
    }

    @NonNull
    @SuppressWarnings("deprecation")
    private List<String> getDateListAsStringList(@NonNull List<Date> dateList, MarshallingMode mode) {
        List<String> list = new ArrayList<>();
        if (mode == MarshallingMode.SDK_V1_COMPATIBLE) {
            // SDK v1 compatibility: Date marshalled to ISO format string
            Date2IsoDynamoDBMarshaller marshaller = new Date2IsoDynamoDBMarshaller();
            for (Date date : dateList) {
                if (date != null) {
                    list.add(marshaller.marshall(date));
                } else {
                    list.add(null);
                }
            }
        } else {
            // SDK v2 native: Date as epoch milliseconds in Number format
            for (Date date : dateList) {
                if (date != null) {
                    list.add(String.valueOf(date.getTime()));
                } else {
                    list.add(null);
                }
            }
        }
        return list;
    }

    @NonNull
    @SuppressWarnings("deprecation")
    private List<String> getInstantListAsStringList(@NonNull List<Instant> dateList, MarshallingMode mode) {
        // Both SDK v1 and v2 store Instant as String (ISO-8601 format)
        // AWS SDK v2 uses InstantAsStringAttributeConverter by default
        List<String> list = new ArrayList<>();
        if (mode == MarshallingMode.SDK_V1_COMPATIBLE) {
            // SDK v1 compatibility: Instant marshalled to ISO format string with millisecond precision
            Instant2IsoDynamoDBMarshaller marshaller = new Instant2IsoDynamoDBMarshaller();
            for (Instant date : dateList) {
                if (date != null) {
                    list.add(marshaller.marshall(date));
                } else {
                    list.add(null);
                }
            }
        } else {
            // SDK v2 native: Instant as ISO-8601 string (matches AWS SDK v2 InstantAsStringAttributeConverter)
            // Format: ISO-8601 with nanosecond precision, e.g., "1970-01-01T00:00:00.001Z"
            for (Instant date : dateList) {
                if (date != null) {
                    list.add(date.toString());
                } else {
                    list.add(null);
                }
            }
        }
        return list;
    }

    @NonNull
    private List<String> getBooleanListAsStringList(@NonNull List<Boolean> booleanList) {
        // Note: DynamoDB doesn't support a BOOL set type (only SS/NS/BS)
        // Boolean lists must always be stored as Number set "1"/"0" regardless of marshalling mode
        List<String> list = new ArrayList<>();
        for (Boolean booleanValue : booleanList) {
            if (booleanValue != null) {
                list.add(booleanValue ? "1" : "0");
            } else {
                list.add(null);
            }
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private <P> List<P> getAttributeValueAsList(@Nullable Object attributeValue) {
        if (attributeValue == null) {
            return null;
        }
        boolean isIterable = ClassUtils.isAssignable(Iterable.class, attributeValue.getClass());
        if (isIterable) {
            List<P> attributeValueAsList = new ArrayList<>();
            Iterable<P> iterable = (Iterable<P>) attributeValue;
            for (P attributeValueElement : iterable) {
                attributeValueAsList.add(attributeValueElement);
            }
            return attributeValueAsList;
        }
        return null;
    }

    /**
     * Adds an attribute value to the list, handling type conversions and collection expansion.
     *
     * @param <P> the type of the property
     * @param attributeValueList the list to add the attribute value to
     * @param attributeValue the value to add
     * @param propertyType the type of the property
     * @param expandCollectionValues whether to expand collection values into DynamoDB sets
     */
    protected <P> void addAttributeValue(@NonNull List<AttributeValue> attributeValueList,
                                         @Nullable Object attributeValue, @NonNull Class<P> propertyType, boolean expandCollectionValues) {
        AttributeValue.Builder attributeValueBuilder = AttributeValue.builder();

        if (ClassUtils.isAssignable(String.class, propertyType)) {
            List<String> attributeValueAsList = getAttributeValueAsList(attributeValue);
            if (expandCollectionValues && attributeValueAsList != null) {
                attributeValueBuilder.ss(attributeValueAsList);
            } else {
                attributeValueBuilder.s((String) attributeValue);
            }
        } else if (ClassUtils.isAssignable(Number.class, propertyType)) {

            List<Number> attributeValueAsList = getAttributeValueAsList(attributeValue);
            if (expandCollectionValues && attributeValueAsList != null) {
                List<String> attributeValueAsStringList = getNumberListAsStringList(attributeValueAsList);
                attributeValueBuilder.ns(attributeValueAsStringList);
            } else {
                assert attributeValue != null;
                attributeValueBuilder.n(attributeValue.toString());
            }
        } else if (ClassUtils.isAssignable(Boolean.class, propertyType)) {
            List<Boolean> attributeValueAsList = getAttributeValueAsList(attributeValue);
            if (expandCollectionValues && attributeValueAsList != null) {
                if (mappingContext.getMarshallingMode() == MarshallingMode.SDK_V1_COMPATIBLE) {
                    // SDK v1 compatibility: Boolean list stored as Number set "1"/"0"
                    List<String> attributeValueAsStringList = getBooleanListAsStringList(attributeValueAsList);
                    attributeValueBuilder.ns(attributeValueAsStringList);
                } else {
                    // SDK v2 native: Boolean list not directly supported in DynamoDB, use Number set
                    // (DynamoDB doesn't have a BOOL set type, only SS/NS/BS)
                    List<String> attributeValueAsStringList = getBooleanListAsStringList(attributeValueAsList);
                    attributeValueBuilder.ns(attributeValueAsStringList);
                }
            } else {
                if (mappingContext.getMarshallingMode() == MarshallingMode.SDK_V1_COMPATIBLE) {
                    // SDK v1 compatibility: Boolean stored as Number "1"/"0"
                    assert attributeValue != null;
                    boolean boolValue = (Boolean) attributeValue;
                    attributeValueBuilder.n(boolValue ? "1" : "0");
                } else {
                    // SDK v2 native: Boolean stored as BOOL type
                    attributeValueBuilder.bool((Boolean) attributeValue);
                }
            }
        } else if (ClassUtils.isAssignable(Date.class, propertyType)) {
            List<Date> attributeValueAsList = getAttributeValueAsList(attributeValue);
            if (expandCollectionValues && attributeValueAsList != null) {
                if (mappingContext.getMarshallingMode() == MarshallingMode.SDK_V1_COMPATIBLE) {
                    // SDK v1 compatibility: Date list stored as String set (ISO format)
                    List<String> attributeValueAsStringList = getDateListAsStringList(attributeValueAsList, MarshallingMode.SDK_V1_COMPATIBLE);
                    attributeValueBuilder.ss(attributeValueAsStringList);
                } else {
                    // SDK v2 native: Date list stored as Number set (epoch milliseconds)
                    List<String> attributeValueAsStringList = getDateListAsStringList(attributeValueAsList, MarshallingMode.SDK_V2_NATIVE);
                    attributeValueBuilder.ns(attributeValueAsStringList);
                }
            } else {
                if (mappingContext.getMarshallingMode() == MarshallingMode.SDK_V1_COMPATIBLE) {
                    // SDK v1 compatibility: Date stored as ISO format string
                    Date date = (Date) attributeValue;
                    String marshalledDate = new Date2IsoDynamoDBMarshaller().marshall(date);
                    attributeValueBuilder.s(marshalledDate);
                } else {
                    // SDK v2 native: Date stored as epoch milliseconds in Number format
                    assert attributeValue != null;
                    attributeValueBuilder.n(String.valueOf(((Date) attributeValue).getTime()));
                }
            }
        } else if (ClassUtils.isAssignable(Instant.class, propertyType)) {
            // Both SDK v1 and v2 store Instant as String (ISO-8601 format)
            // AWS SDK v2 uses InstantAsStringAttributeConverter by default
            List<Instant> attributeValueAsList = getAttributeValueAsList(attributeValue);
            if (expandCollectionValues && attributeValueAsList != null) {
                // Instant lists always stored as String set (ISO-8601 format) in both modes
                List<String> attributeValueAsStringList = getInstantListAsStringList(attributeValueAsList, mappingContext.getMarshallingMode());
                attributeValueBuilder.ss(attributeValueAsStringList);
            } else {
                if (mappingContext.getMarshallingMode() == MarshallingMode.SDK_V1_COMPATIBLE) {
                    // SDK v1 compatibility: Instant stored as ISO format string with millisecond precision
                    Instant date = (Instant) attributeValue;
                    String marshalledDate = new Instant2IsoDynamoDBMarshaller().marshall(date);
                    attributeValueBuilder.s(marshalledDate);
                } else {
                    // SDK v2 native: Instant stored as ISO-8601 string (matches AWS SDK v2 InstantAsStringAttributeConverter)
                    // Format: ISO-8601 with nanosecond precision, e.g., "1970-01-01T00:00:00.001Z"
                    assert attributeValue != null;
                    attributeValueBuilder.s(attributeValue.toString());
                }
            }
        } else {
            assert attributeValue != null;
            throw new RuntimeException("Cannot create condition for type:" + attributeValue.getClass()
                    + " property conditions must be String,Number or Boolean, or have an AttributeConverter configured");
        }
        attributeValueList.add(attributeValueBuilder.build());

    }

    /**
     * Creates a Condition object with a single value for the specified property.
     *
     * @param propertyName the property name
     * @param comparisonOperator the comparison operator
     * @param o the condition value
     * @param propertyType the type of the property
     * @param alreadyMarshalledIfRequired whether the value is already marshalled
     * @return a Condition object ready for use in queries
     * @throws IllegalArgumentException if o is null
     */
    protected Condition createSingleValueCondition(String propertyName, ComparisonOperator comparisonOperator, Object o,
            Class<?> propertyType, boolean alreadyMarshalledIfRequired) {

        Assert.notNull(o, "Creating conditions on null property values not supported: please specify a value for '"
                + propertyName + "'");

        List<AttributeValue> attributeValueList = new ArrayList<>(1);
        Object attributeValue = !alreadyMarshalledIfRequired ? getPropertyAttributeValue(propertyName, o) : o;
        if (ClassUtils.isAssignableValue(AttributeValue.class, attributeValue)) {
            attributeValueList.add((AttributeValue) attributeValue);
        } else {
            boolean marshalled = !alreadyMarshalledIfRequired && attributeValue != o
                    && !entityInformation.isCompositeHashAndRangeKeyProperty(propertyName);

            Class<?> targetPropertyType = marshalled ? String.class : propertyType;
            addAttributeValue(attributeValueList, attributeValue, targetPropertyType, true);
        }

        return Condition.builder().comparisonOperator(comparisonOperator).attributeValueList(attributeValueList)
                .build();
    }

    /**
     * Creates a Condition object with multiple values for the specified property.
     * Used for IN and BETWEEN conditions.
     *
     * @param propertyName the property name
     * @param comparisonOperator the comparison operator
     * @param o an iterable of values for the condition
     * @param propertyType the type of the property
     * @return a Condition object ready for use in queries
     * @throws IllegalArgumentException if o is null or empty
     */
    protected Condition createCollectionCondition(String propertyName, ComparisonOperator comparisonOperator,
                                                  @NonNull Iterable<?> o, Class<?> propertyType) {

        Assert.notNull(o, "Creating conditions on null property values not supported: please specify a value for '"
                + propertyName + "'");
        List<AttributeValue> attributeValueList = new ArrayList<>();
        boolean marshalled = false;
        for (Object object : o) {
            Object attributeValue = getPropertyAttributeValue(propertyName, object);
            if (ClassUtils.isAssignableValue(AttributeValue.class, attributeValue)) {
                attributeValueList.add((AttributeValue) attributeValue);
            } else {
                if (attributeValue != null) {
                    marshalled = attributeValue != object
                            && !entityInformation.isCompositeHashAndRangeKeyProperty(propertyName);
                }
                Class<?> targetPropertyType = marshalled ? String.class : propertyType;
                addAttributeValue(attributeValueList, attributeValue, targetPropertyType, false);
            }
        }

        return Condition.builder().comparisonOperator(comparisonOperator).attributeValueList(attributeValueList)
                .build();

    }

    /**
     * Sets the sort order for the query results.
     * Implementation of interface method.
     * @param sort the sort specification
     */
    @Override
    public void withSort(Sort sort) {
        this.sort = sort;
    }

    /**
     * Sets the projection expression to limit the attributes returned.
     * Implementation of interface method.
     * @param projection the projection expression specifying which attributes to return
     */
    @Override
    public void withProjection(@Nullable String projection) {
        this.projection = projection;
    }

    /**
     * Sets the maximum number of items to return from the query.
     * Implementation of interface method.
     * @param limit the maximum number of items to return
     */
    @Override
    public void withLimit(@Nullable Integer limit) {
        this.limit = limit;
    }

    /**
     * Sets the filter expression to further filter query results.
     * Implementation of interface method.
     * @param filter the filter expression to apply
     */
    @Override
    public void withFilterExpression(@Nullable String filter) {
        this.filterExpression = filter;
    }

    /**
     * Sets the expression attribute names for the query.
     * Implementation of interface method.
     * @param names an array of expression attribute names
     */
    @Override
    public void withExpressionAttributeNames(@Nullable ExpressionAttribute[] names) {
        if (names != null)
            this.expressionAttributeNames = names.clone();
    }

    /**
     * Sets the expression attribute values for the query.
     * Implementation of interface method.
     * @param values an array of expression attribute values
     */
    @Override
    public void withExpressionAttributeValues(@Nullable ExpressionAttribute[] values) {
        if (values != null)
            this.expressionAttributeValues = values.clone();
    }

    /**
     * Sets the consistent read mode for the query.
     * Implementation of interface method.
     * @param consistentReads the consistent read mode
     */
    @Override
    public void withConsistentReads(QueryConstants.ConsistentReadMode consistentReads) {
        this.consistentReads = consistentReads;
    }

    /**
     * Sets mapped expression values for parameter substitution.
     * Implementation of interface method.
     * @param map a map of parameter names to their string values
     */
    @Override
    public void withMappedExpressionValues(Map<String, String> map) {
        this.mappedExpressionValues = map;
    }
}
