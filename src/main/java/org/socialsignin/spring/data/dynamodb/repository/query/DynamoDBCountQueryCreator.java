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
import org.socialsignin.spring.data.dynamodb.query.StaticQuery;
import org.socialsignin.spring.data.dynamodb.repository.ExpressionAttribute;
import org.socialsignin.spring.data.dynamodb.repository.QueryConstants;
import org.socialsignin.spring.data.dynamodb.repository.support.DynamoDBEntityInformation;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.ParameterAccessor;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * A specialized query creator for DynamoDB count queries.
 * Extends AbstractDynamoDBQueryCreator to generate count queries that determine the number of entities
 * matching the specified criteria. Supports both regular count queries and pagination count queries.
 * @param <T> the entity type being queried
 * @param <ID> the ID type of the entity
 */
public class DynamoDBCountQueryCreator<T, ID> extends AbstractDynamoDBQueryCreator<T, ID, Long> {

    private final boolean pageQuery;

    /**
     * Constructs a DynamoDBCountQueryCreator without parameter bindings.
     * This constructor is deprecated and will be removed in version 5.3.0.
     * @deprecated use the new constructor with all required fields, will be removed in 5.3.0
     * @param tree the PartTree representing the query method structure
     * @param entityMetadata metadata information about the entity being queried
     * @param dynamoDBOperations the DynamoDB operations instance for query execution
     * @param pageQuery true if this count is for pagination purposes, false otherwise
     */
    @Deprecated
    public DynamoDBCountQueryCreator(@NonNull PartTree tree, DynamoDBEntityInformation<T, ID> entityMetadata,
                                     DynamoDBOperations dynamoDBOperations, boolean pageQuery) {
        super(tree, entityMetadata, null, null, QueryConstants.ConsistentReadMode.DEFAULT,
                null, null, null, dynamoDBOperations);
        this.pageQuery = pageQuery;
    }

    /**
     * Constructs a DynamoDBCountQueryCreator with parameter bindings and filter expression support.
     * This is the primary constructor that supports all count query options including filter expressions
     * and expression attribute substitutions.
     * <p>
     * @param tree the PartTree representing the query method structure
     * @param parameterAccessor accessor for retrieving parameter values from the query method
     * @param entityMetadata metadata information about the entity being queried
     * @param filterExpression optional filter expression for additional filtering beyond key conditions
     * @param names optional array of expression attribute names for substitution in filter expressions
     * @param values optional array of expression attribute values for substitution in filter expressions
     * @param dynamoDBOperations the DynamoDB operations instance for query execution
     * @param pageQuery true if this count is for pagination purposes, false otherwise
     */
    public DynamoDBCountQueryCreator(@NonNull PartTree tree, @NonNull ParameterAccessor parameterAccessor,
                                     DynamoDBEntityInformation<T, ID> entityMetadata, @Nullable String filterExpression,
                                     ExpressionAttribute[] names, ExpressionAttribute[] values, DynamoDBOperations dynamoDBOperations,
                                     boolean pageQuery) {

        super(tree, parameterAccessor, entityMetadata, null, null,
                QueryConstants.ConsistentReadMode.DEFAULT, filterExpression, names, values, dynamoDBOperations);
        this.pageQuery = pageQuery;

    }

    @NonNull
    @Override
    protected Query<Long> complete(@Nullable DynamoDBQueryCriteria<T, ID> criteria, @NonNull Sort sort) {
        if (criteria == null) {
            return new StaticQuery<>(1L);
        } else {
            criteria.withFilterExpression(filterExpression);
            criteria.withExpressionAttributeNames(expressionAttributeNames);
            criteria.withExpressionAttributeValues(expressionAttributeValues);
            criteria.withMappedExpressionValues(mappedExpressionValues);
            return criteria.buildCountQuery(dynamoDBOperations, pageQuery);
        }
    }

}
