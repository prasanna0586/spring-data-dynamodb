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
 * @author Prasanna Kumar Ramachandran
 */
public abstract class AbstractDynamoDBQueryCriteria<T, ID> implements DynamoDBQueryCriteria<T, ID>, SortHandler {

    protected Class<T> clazz;
    @NonNull
    private final DynamoDBEntityInformation<T, ID> entityInformation;
    @NonNull
    private final Map<String, String> attributeNamesByPropertyName;
    private final String hashKeyPropertyName;
    protected final DynamoDBMappingContext mappingContext;

    protected MultiValueMap<String, Condition> attributeConditions;
    protected MultiValueMap<String, Condition> propertyConditions;

    protected Object hashKeyAttributeValue;
    protected Object hashKeyPropertyValue;
    @Nullable
    protected String globalSecondaryIndexName;
    protected Sort sort = Sort.unsorted();
    protected Optional<String> projection = Optional.empty();
    protected Optional<Integer> limit = Optional.empty();
    protected Optional<String> filterExpression = Optional.empty();
    protected ExpressionAttribute[] expressionAttributeNames;
    protected ExpressionAttribute[] expressionAttributeValues;
    protected Map<String, String> mappedExpressionValues;
    protected QueryConstants.ConsistentReadMode consistentReads = QueryConstants.ConsistentReadMode.DEFAULT;

    public abstract boolean isApplicableForLoad();

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
        if (hashKeyConditions != null && hashKeyConditions.size() > 0) {
            for (Condition hashKeyCondition : hashKeyConditions) {
                String namePlaceholder = "#pk" + nameCounter++;
                String conditionExpr = buildKeyConditionPart(namePlaceholder, hashKeyCondition, valueCounter, keyExpressionValues);
                keyConditionParts.add(conditionExpr);
                keyExpressionNames.put(namePlaceholder, hashKeyAttributeName);
                valueCounter = keyExpressionValues.size();
            }
        }

        // Build range key condition (if present)
        if (rangeKeyConditions != null && rangeKeyConditions.size() > 0) {
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
        if (projection.isPresent()) {
            queryRequest = queryRequest.toBuilder().select(Select.SPECIFIC_ATTRIBUTES).build();
            queryRequest = queryRequest.toBuilder().projectionExpression(projection.get()).build();
        } else if (isApplicableForGlobalSecondaryIndex()) {
            // For GSI queries without explicit projection, use ALL_PROJECTED_ATTRIBUTES
            queryRequest = queryRequest.toBuilder().select(Select.ALL_PROJECTED_ATTRIBUTES).build();
        }

        // Determine allowed sort properties based on query type
        // Check if this is an LSI query
        // LSI query: hash key is table partition key + index name is set
        boolean isLSIQuery = false;
        if (isApplicableForGlobalSecondaryIndex() && getHashKeyAttributeValue() != null) {
            String queryHashKeyPropertyName = getHashKeyPropertyName();
            boolean isTablePartitionKey = queryHashKeyPropertyName.equals(entityInformation.getHashKeyPropertyName());
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

                if (indexRangeKeyPropertyNames != null && sort != null && sort.iterator().hasNext()) {
                    String sortProperty = sort.iterator().next().getProperty();
                    // Only add LSI range key if it's the sort property (hash-only + OrderBy pattern)
                    if (indexRangeKeyPropertyNames.contains(sortProperty)) {
                        allowedSortProperties.add(sortProperty);
                    }
                }
            }
        }

        List<String> dedupedAllowedProps = new ArrayList<>(new HashSet<>(allowedSortProperties));
        queryRequest = applySortIfSpecified(queryRequest, dedupedAllowedProps);

        queryRequest = applyConsistentReads(queryRequest);

        // SDK v2: Use builder pattern for setting limit
        if (limit.isPresent()) {
            queryRequest = queryRequest.toBuilder().limit(limit.get()).build();
        }

        if (filterExpression.isPresent()) {
            String filter = filterExpression.get();
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

    protected QueryRequest applyConsistentReads(@NonNull QueryRequest queryRequest) {
        return switch (consistentReads) {
            case CONSISTENT -> queryRequest.toBuilder().consistentRead(true).build();
            case EVENTUAL -> queryRequest.toBuilder().consistentRead(false).build();
            default -> queryRequest;
        };
    }

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
     *
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

    @Nullable
    protected List<Condition> getHashKeyConditions() {
        List<Condition> hashKeyConditions = null;
        // For LSI: hash key is the table's partition key (not in globalSecondaryIndexNames map), only when using an index
        // For GSI: hash key is a GSI partition key (in globalSecondaryIndexNames map)
        boolean isLSIHashKey = isApplicableForGlobalSecondaryIndex()
                && getGlobalSecondaryIndexName() != null
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

    @Nullable
    protected String getGlobalSecondaryIndexName() {

        // Lazy evaluate the globalSecondaryIndexName if not already set

        // Check if we have a sort requirement
        boolean hasSortRequirement = sort != null && sort.iterator().hasNext();

        // Check if hash key is a GSI partition key
        boolean shouldSelectIndex = isShouldSelectIndex(hasSortRequirement);

        if (globalSecondaryIndexName == null && shouldSelectIndex) {
            // Declare map of index names by attribute name which we will populate below -
            // this will be used to determine which index to use if multiple indexes are
            // applicable
            Map<String, String[]> indexNamesByAttributeName = new HashMap<>();

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
            if (sort != null && sort.iterator().hasNext()) {
                sortPropertyName = sort.iterator().next().getProperty();
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

    private boolean isShouldSelectIndex(boolean hasSortRequirement) {
        boolean hashKeyIsGSIPartitionKey = entityInformation.getGlobalSecondaryIndexNamesByPropertyName()
                .containsKey(getHashKeyPropertyName());

        // Run index selection if:
        // 1. We have attribute conditions (traditional GSI query), OR
        // 2. Hash key is a GSI partition key AND we have a sort requirement (hash-only GSI query with OrderBy)
        boolean shouldSelectIndex = (attributeConditions != null && !attributeConditions.isEmpty()) ||
                                    (hashKeyIsGSIPartitionKey && hasSortRequirement);
        return shouldSelectIndex;
    }

    protected boolean isHashKeyProperty(String propertyName) {
        return hashKeyPropertyName.equals(propertyName);
    }

    protected String getHashKeyPropertyName() {
        return hashKeyPropertyName;
    }

    protected String getHashKeyAttributeName() {
        return getAttributeName(getHashKeyPropertyName());
    }

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
                    && hashKeyPropertyName.equals(entityInformation.getHashKeyPropertyName());
            boolean isGSIPartitionKey = entityInformation.isGlobalIndexHashKeyProperty(hashKeyPropertyName);
            if (isTablePartitionKey || isGSIPartitionKey) {
                hasIndexHashKeyEqualCondition = true;
            }
        }
        return hasIndexHashKeyEqualCondition;
    }

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

    protected boolean isApplicableForGlobalSecondaryIndex() {
        boolean global = this.getGlobalSecondaryIndexName() != null;
        if (global && getHashKeyAttributeValue() != null) {
            // Check if the hash key used in the query is valid for this index
            // Valid cases:
            // 1. LSI: hash key is the table's partition key (not in globalSecondaryIndexNames map)
            // 2. GSI: hash key is a GSI partition key (in globalSecondaryIndexNames map)
            String queryHashKeyPropertyName = getHashKeyPropertyName();
            boolean isTablePartitionKey = queryHashKeyPropertyName.equals(entityInformation.getHashKeyPropertyName());
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

    @NonNull
    public DynamoDBQueryCriteria<T, ID> withHashKeyEquals(Object value) {
        Assert.notNull(value, "Creating conditions on null hash keys not supported: please specify a value for '"
                + getHashKeyPropertyName() + "'");

        hashKeyAttributeValue = getPropertyAttributeValue(getHashKeyPropertyName(), value);
        hashKeyPropertyValue = value;
        return this;
    }

    public boolean isHashKeySpecified() {
        return getHashKeyAttributeValue() != null;
    }

    public Object getHashKeyAttributeValue() {
        return hashKeyAttributeValue;
    }

    public Object getHashKeyPropertyValue() {
        return hashKeyPropertyValue;
    }

    protected String getAttributeName(String propertyName) {
        String attributeName = attributeNamesByPropertyName.get(propertyName);
        if (attributeName == null) {
            attributeName = entityInformation.getOverriddenAttributeName(propertyName).orElse(propertyName);
            attributeNamesByPropertyName.put(propertyName, attributeName);
        }
        return attributeName;

    }

    @Override
    public DynamoDBQueryCriteria<T, ID> withPropertyBetween(@NonNull String propertyName, Object value1, Object value2,
                                                            Class<?> type) {
        Condition condition = createCollectionCondition(propertyName, ComparisonOperator.BETWEEN,
                Arrays.asList(value1, value2), type);
        return withCondition(propertyName, condition);
    }

    @Override
    public DynamoDBQueryCriteria<T, ID> withPropertyIn(@NonNull String propertyName, @NonNull Iterable<?> value, Class<?> propertyType) {

        Condition condition = createCollectionCondition(propertyName, ComparisonOperator.IN, value, propertyType);
        return withCondition(propertyName, condition);
    }

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

    @Override
    public Query<T> buildQuery(DynamoDBOperations dynamoDBOperations) {
        if (isApplicableForLoad()) {
            return buildSingleEntityLoadQuery(dynamoDBOperations);
        } else {
            return buildFinderQuery(dynamoDBOperations);
        }
    }

    @Override
    public Query<Long> buildCountQuery(DynamoDBOperations dynamoDBOperations, boolean pageQuery) {
        if (isApplicableForLoad()) {
            return buildSingleEntityCountQuery(dynamoDBOperations);
        } else {
            return buildFinderCountQuery(dynamoDBOperations, pageQuery);
        }
    }

    protected abstract Query<T> buildSingleEntityLoadQuery(DynamoDBOperations dynamoDBOperations);

    protected abstract Query<Long> buildSingleEntityCountQuery(DynamoDBOperations dynamoDBOperations);

    protected abstract Query<T> buildFinderQuery(DynamoDBOperations dynamoDBOperations);

    protected abstract Query<Long> buildFinderCountQuery(DynamoDBOperations dynamoDBOperations, boolean pageQuery);

    protected abstract boolean isOnlyHashKeySpecified();

    @Override
    public DynamoDBQueryCriteria<T, ID> withNoValuedCriteria(@NonNull String propertyName,
                                                             ComparisonOperator comparisonOperator) {
        Condition condition = createNoValueCondition(comparisonOperator);
        return withCondition(propertyName, condition);

    }

    @NonNull
    public DynamoDBQueryCriteria<T, ID> withCondition(@NonNull String propertyName, Condition condition) {
        attributeConditions.add(getAttributeName(propertyName), condition);
        propertyConditions.add(propertyName, condition);

        return this;
    }

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
                    attributeValueBuilder.s(((Instant) attributeValue).toString());
                }
            }
        } else {
            assert attributeValue != null;
            throw new RuntimeException("Cannot create condition for type:" + attributeValue.getClass()
                    + " property conditions must be String,Number or Boolean, or have an AttributeConverter configured");
        }
        attributeValueList.add(attributeValueBuilder.build());

    }

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

    @Override
    public void withSort(Sort sort) {
        this.sort = sort;
    }

    @Override
    public void withProjection(Optional<String> projection) {
        this.projection = projection;
    }

    @Override
    public void withLimit(Optional<Integer> limit) {
        this.limit = limit;
    }

    @Override
    public void withFilterExpression(Optional<String> filter) {
        this.filterExpression = filter;
    }

    @Override
    public void withExpressionAttributeNames(@Nullable ExpressionAttribute[] names) {
        if (names != null)
            this.expressionAttributeNames = names.clone();
    }

    @Override
    public void withExpressionAttributeValues(@Nullable ExpressionAttribute[] values) {
        if (values != null)
            this.expressionAttributeValues = values.clone();
    }

    @Override
    public void withConsistentReads(QueryConstants.ConsistentReadMode consistentReads) {
        this.consistentReads = consistentReads;
    }

    @Override
    public void withMappedExpressionValues(Map<String, String> map) {
        this.mappedExpressionValues = map;
    }
}
