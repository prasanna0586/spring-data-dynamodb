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
 * Query creator for DynamoDB entity queries returning entity results.
 *
 * @param <T> the entity type
 * @param <ID> the entity ID type
 */
public class DynamoDBQueryCreator<T, ID> extends AbstractDynamoDBQueryCreator<T, ID, T> {

    public DynamoDBQueryCreator(@NonNull PartTree tree, @NonNull ParameterAccessor parameterAccessor,
                                DynamoDBEntityInformation<T, ID> entityMetadata, @Nullable String projection, @Nullable Integer limit,
                                QueryConstants.ConsistentReadMode consistentReads, @Nullable String filterExpression,
                                ExpressionAttribute[] names, ExpressionAttribute[] values, DynamoDBOperations dynamoDBOperations) {
        super(tree, parameterAccessor, entityMetadata, projection, limit, consistentReads, filterExpression, names,
                values, dynamoDBOperations);
    }

    @NonNull
    @Override
    protected Query<T> complete(@Nullable DynamoDBQueryCriteria<T, ID> criteria, @NonNull Sort sort) {
        if (criteria == null) {
            return new StaticQuery<>(null);
        } else {
            criteria.withSort(sort);
            criteria.withProjection(projection);
            criteria.withLimit(limit);
            criteria.withConsistentReads(consistentReads);
            criteria.withFilterExpression(filterExpression);
            criteria.withExpressionAttributeNames(expressionAttributeNames);
            criteria.withExpressionAttributeValues(expressionAttributeValues);
            criteria.withMappedExpressionValues(mappedExpressionValues);
            return criteria.buildQuery(dynamoDBOperations);
        }
    }

}
