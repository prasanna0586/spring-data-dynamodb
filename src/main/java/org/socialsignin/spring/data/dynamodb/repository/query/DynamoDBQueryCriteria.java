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
import org.socialsignin.spring.data.dynamodb.query.Query;
import org.socialsignin.spring.data.dynamodb.repository.ExpressionAttribute;
import org.socialsignin.spring.data.dynamodb.repository.QueryConstants;
import org.springframework.data.domain.Sort;
import org.springframework.lang.Nullable;
import software.amazon.awssdk.services.dynamodb.model.ComparisonOperator;

import java.util.Map;

/**
 * Interface for building and executing DynamoDB query criteria.
 * Supports building queries with various condition types and executing them through the DynamoDB API.
 * @param <T> the entity type
 * @param <ID> the ID type
 * @author Prasanna Kumar Ramachandran
 */
public interface DynamoDBQueryCriteria<T, ID> {

    /**
     * Adds a single-value criteria to the query using the specified comparison operator.
     * @param propertyName the property name to filter on
     * @param comparisonOperator the comparison operator to apply
     * @param value the value to compare against
     * @param type the type of the property
     * @return this query criteria for method chaining
     */
    DynamoDBQueryCriteria<T, ID> withSingleValueCriteria(String propertyName, ComparisonOperator comparisonOperator,
            Object value, Class<?> type);

    /**
     * Adds a criteria with no value (e.g., NULL or NOT_NULL checks).
     * @param segment the property name to filter on
     * @param null1 the comparison operator for no-value conditions
     * @return this query criteria for method chaining
     */
    DynamoDBQueryCriteria<T, ID> withNoValuedCriteria(String segment, ComparisonOperator null1);

    /**
     * Adds an equality criteria on the specified property.
     * @param segment the property name to filter on
     * @param next the value to match
     * @param type the type of the property
     * @return this query criteria for method chaining
     */
    DynamoDBQueryCriteria<T, ID> withPropertyEquals(String segment, Object next, Class<?> type);

    /**
     * Adds an IN (membership) criteria on the specified property.
     * @param segment the property name to filter on
     * @param o an iterable of values to match against
     * @param type the type of the property
     * @return this query criteria for method chaining
     */
    DynamoDBQueryCriteria<T, ID> withPropertyIn(String segment, Iterable<?> o, Class<?> type);

    /**
     * Adds a BETWEEN criteria on the specified property.
     * @param segment the property name to filter on
     * @param value1 the lower bound value (inclusive)
     * @param value2 the upper bound value (inclusive)
     * @param type the type of the property
     * @return this query criteria for method chaining
     */
    DynamoDBQueryCriteria<T, ID> withPropertyBetween(String segment, Object value1, Object value2, Class<?> type);

    /**
     * Sets the sort order for the query results.
     * @param sort the sort specification
     */
    void withSort(Sort sort);

    /**
     * Sets the projection expression to limit the attributes returned.
     * @param projection the projection expression specifying which attributes to return
     */
    void withProjection(@Nullable String projection);

    /**
     * Sets the maximum number of items to return from the query.
     * @param limit the maximum number of items to return
     */
    void withLimit(@Nullable Integer limit);

    /**
     * Sets the consistent read mode for the query.
     * @param reads the consistent read mode
     */
    void withConsistentReads(QueryConstants.ConsistentReadMode reads);

    /**
     * Sets the filter expression to further filter query results.
     * @param filterExpression the filter expression to apply
     */
    void withFilterExpression(@Nullable String filterExpression);

    /**
     * Sets the expression attribute names for the query.
     * @param names an array of expression attribute names
     */
    void withExpressionAttributeNames(ExpressionAttribute[] names);

    /**
     * Sets the expression attribute values for the query.
     * @param values an array of expression attribute values
     */
    void withExpressionAttributeValues(ExpressionAttribute[] values);

    /**
     * Sets mapped expression values for parameter substitution.
     * @param values a map of parameter names to their string values
     */
    void withMappedExpressionValues(Map<String, String> values);

    /**
     * Builds a query object that can be executed to fetch results.
     * @param dynamoDBOperations the DynamoDB operations instance to use for execution
     * @return a Query object configured with the criteria
     */
    Query<T> buildQuery(DynamoDBOperations dynamoDBOperations);

    /**
     * Builds a count query that returns the number of matching items.
     * @param dynamoDBOperations the DynamoDB operations instance to use for execution
     * @param pageQuery whether this is for paginated query counting
     * @return a Query object that returns the count of matching items
     */
    Query<Long> buildCountQuery(DynamoDBOperations dynamoDBOperations, boolean pageQuery);

}
