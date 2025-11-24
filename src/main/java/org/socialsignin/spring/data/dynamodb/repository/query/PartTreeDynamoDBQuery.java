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
import org.socialsignin.spring.data.dynamodb.query.Query;
import org.springframework.data.repository.query.Parameters;
import org.springframework.data.repository.query.ParametersParameterAccessor;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * @author Prasanna Kumar Ramachandran
 */
public class PartTreeDynamoDBQuery<T, ID> extends AbstractDynamoDBQuery<T, ID> implements RepositoryQuery {

    @NonNull
    private final Parameters<?, ?> parameters;
    @NonNull
    private final PartTree tree;

    public PartTreeDynamoDBQuery(DynamoDBOperations dynamoDBOperations, @NonNull DynamoDBQueryMethod<T, ID> method) {
        super(dynamoDBOperations, method);
        this.parameters = method.getParameters();
        this.tree = new PartTree(method.getName(), method.getEntityType());
    }

    @NonNull
    protected DynamoDBQueryCreator<T, ID> createQueryCreator(@NonNull ParametersParameterAccessor accessor) {
        DynamoDBQueryMethod<T, ID> queryMethod = getQueryMethod();
        return new DynamoDBQueryCreator<>(tree, accessor, queryMethod.getEntityInformation(),
                queryMethod.getProjectionExpression().orElse(null), queryMethod.getLimitResults().orElse(null),
                queryMethod.getConsistentReadMode(), queryMethod.getFilterExpression().orElse(null),
                queryMethod.getExpressionAttributeNames(), queryMethod.getExpressionAttributeValues(),
                dynamoDBOperations);
    }

    @NonNull
    protected DynamoDBCountQueryCreator<T, ID> createCountQueryCreator(@NonNull ParametersParameterAccessor accessor,
                                                                       boolean pageQuery) {
        DynamoDBQueryMethod<T, ID> queryMethod = getQueryMethod();
        return new DynamoDBCountQueryCreator<>(tree, accessor, queryMethod.getEntityInformation(),
                queryMethod.getFilterExpression().orElse(null), queryMethod.getExpressionAttributeNames(),
                queryMethod.getExpressionAttributeValues(), dynamoDBOperations, pageQuery);
    }

    @NonNull
    @Override
    public Query<T> doCreateQuery(@NonNull Object[] values) {
        ParametersParameterAccessor accessor = new ParametersParameterAccessor(parameters, values);
        DynamoDBQueryCreator<T, ID> queryCreator = createQueryCreator(accessor);
        return queryCreator.createQuery();

    }

    @NonNull
    @Override
    public Query<Long> doCreateCountQuery(@NonNull Object[] values, boolean pageQuery) {
        ParametersParameterAccessor accessor = new ParametersParameterAccessor(parameters, values);
        DynamoDBCountQueryCreator<T, ID> queryCreator = createCountQueryCreator(accessor, pageQuery);
        return queryCreator.createQuery();

    }

    @Override
    protected boolean isCountQuery() {
        return tree.isCountProjection();
    }

    @Override
    protected boolean isExistsQuery() {
        return tree.isExistsProjection();
    }

    @Override
    protected boolean isDeleteQuery() {
        return tree.isDelete();
    }

    @Nullable
    @Override
    protected Integer getResultsRestrictionIfApplicable() {

        if (tree.isLimiting()) {
            return tree.getMaxResults();
        }
        return null;
    }

    @Override
    protected boolean isSingleEntityResultsRestriction() {
        Integer resultsRestiction = getResultsRestrictionIfApplicable();
        return resultsRestiction != null && resultsRestiction.equals(1);
    }

}
