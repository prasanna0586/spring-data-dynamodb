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
package org.socialsignin.spring.data.dynamodb.repository;

import org.socialsignin.spring.data.dynamodb.repository.QueryConstants.ConsistentReadMode;

import java.lang.annotation.*;

import static org.socialsignin.spring.data.dynamodb.repository.QueryConstants.QUERY_LIMIT_UNLIMITED;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Query {

	/**
	 * A string that identifies the attributes you want. To retrieve a single
	 * attribute, specify its name. For multiple attributes, the names must be
	 * comma-separated.
	 *
	 * @see <a href=
	 *      "https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Expressions.ProjectionExpressions.html">Projection
	 *      Expressions</a>
	 * @return comma-separated list of fields
	 */
	String fields() default "";

	/**
	 * An integer to limit the number of elements returned.
	 *
	 * @see <a href=
	 *      "https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Expressions.ProjectionExpressions.html">Projection
	 *      Expressions</a>
	 * @return number of results to return
	 */
	int limit() default QUERY_LIMIT_UNLIMITED;

	/**
	 * Override consistent read mode set in the DynamoDBMapper
	 *
	 * @see <a href=
	 *      "https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/HowItWorks.ReadConsistency.html">Read Consistency</a>
	 * @see <a href=
	 *      "https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBMapper.OptionalConfig.html">DynamoDBMapper Configuration</a>
	 * @return ConsistentReadMode to enforce on query
	 */
	ConsistentReadMode consistentReads() default ConsistentReadMode.DEFAULT;

	/**
	 * Set filter expressions for a query
	 *
	 * @see <a href=
	 * 		"https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Query.html#Query.FilterExpression">Filter Expressions</a>
	 * @see <a href=
	 * 	 	"https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Expressions.OperatorsAndFunctions.html"> Expression Syntax</a>
	 *
	 *
	 * Example: {@code @Query(filterExpression = "contains(#field, :value)",
	 * 	           				expressionMappingNames = {@ExpressionAttribute(key = "#field", value = "name")},
	 * 							expressionMappingValues = {@ExpressionAttribute(key=":value", value = "John Doe")})}
	 *
	 * @return filter expression for query
	 */
	String filterExpression() default "";

	/**
	 * Set filter expressions for a query
	 *
	 * @see <a href=
	 * 		"https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Query.html#Query.FilterExpression">Filter Expressions</a>
	 * @see <a href=
	 * 	 	"https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Expressions.OperatorsAndFunctions.html"> Expression Syntax</a>
	 *
	 *
	 * Example: {@code @Query(filterExpression = "contains(#field, :value)",
	 * 	           				expressionMappingNames = {@ExpressionAttribute(key = "#field", value = "name")},
	 * 							expressionMappingValues = {@ExpressionAttribute(key=":value", value = "John Doe")})}
	 *
	 * @return expression name mappings for query
	 */
	ExpressionAttribute[] expressionMappingNames() default @ExpressionAttribute;

	/**
	 * Set filter expressions for a query
	 *
	 * @see <a href=
	 * 		"https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Query.html#Query.FilterExpression">Filter Expressions</a>
	 * @see <a href=
	 * 	 	"https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Expressions.OperatorsAndFunctions.html">Expression Syntax</a>
	 *
	 *
	 * Example: {@code @Query(filterExpression = "contains(#field, :value)",
	 * 	           				expressionMappingNames = {@ExpressionAttribute(key = "#field", value = "name")},
	 * 							expressionMappingValues = {@ExpressionAttribute(key=":value", value = "John Doe")})}
	 *
	 * @return expression value mappings for query
	 */
	ExpressionAttribute[] expressionMappingValues() default @ExpressionAttribute;
}