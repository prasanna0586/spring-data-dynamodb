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

import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;
import org.socialsignin.spring.data.dynamodb.query.Query;
import org.socialsignin.spring.data.dynamodb.repository.ExpressionAttribute;
import org.socialsignin.spring.data.dynamodb.repository.QueryConstants;
import org.springframework.data.domain.Sort;

import java.util.Map;
import java.util.Optional;

/**
 * @author Michael Lavelle
 * @author Sebastian Just
 */
public interface DynamoDBQueryCriteria<T, ID> {

	DynamoDBQueryCriteria<T, ID> withSingleValueCriteria(String propertyName, ComparisonOperator comparisonOperator,
			Object value, Class<?> type);

	DynamoDBQueryCriteria<T, ID> withNoValuedCriteria(String segment, ComparisonOperator null1);

	DynamoDBQueryCriteria<T, ID> withPropertyEquals(String segment, Object next, Class<?> type);

	DynamoDBQueryCriteria<T, ID> withPropertyIn(String segment, Iterable<?> o, Class<?> type);

	DynamoDBQueryCriteria<T, ID> withPropertyBetween(String segment, Object value1, Object value2, Class<?> type);

	DynamoDBQueryCriteria<T, ID> withSort(Sort sort);

	DynamoDBQueryCriteria<T, ID> withProjection(Optional<String> projection);

	DynamoDBQueryCriteria<T, ID> withLimit(Optional<Integer> limit);

	DynamoDBQueryCriteria<T, ID> withConsistentReads(QueryConstants.ConsistentReadMode reads);

	DynamoDBQueryCriteria<T, ID> withFilterExpression(Optional<String> filterExpression);

	DynamoDBQueryCriteria<T, ID> withExpressionAttributeNames(ExpressionAttribute[] names);

	DynamoDBQueryCriteria<T, ID> withExpressionAttributeValues(ExpressionAttribute[] values);

	DynamoDBQueryCriteria<T, ID> withMappedExpressionValues(Map<String, String> values);

	Query<T> buildQuery(DynamoDBOperations dynamoDBOperations);

	Query<Long> buildCountQuery(DynamoDBOperations dynamoDBOperations, boolean pageQuery);

}
