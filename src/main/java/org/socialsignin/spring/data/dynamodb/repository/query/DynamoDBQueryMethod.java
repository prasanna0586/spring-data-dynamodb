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

import org.socialsignin.spring.data.dynamodb.repository.*;
import org.socialsignin.spring.data.dynamodb.repository.support.DynamoDBEntityInformation;
import org.socialsignin.spring.data.dynamodb.repository.support.DynamoDBEntityMetadataSupport;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.socialsignin.spring.data.dynamodb.repository.QueryConstants.QUERY_LIMIT_UNLIMITED;

/**
 * @author Michael Lavelle
 * @author Sebastian Just
 */
public class DynamoDBQueryMethod<T, ID> extends QueryMethod {

    private final Method method;
    private final boolean scanEnabledForRepository;
    private final boolean scanCountEnabledForRepository;
    private final Optional<String> projectionExpression;
    private final Optional<Integer> limitResults;
    private final Optional<String> filterExpression;
    private final ExpressionAttribute[] expressionAttributeNames;
    private final ExpressionAttribute[] expressionAttributeValues;
    private final QueryConstants.ConsistentReadMode consistentReadMode;

    public DynamoDBQueryMethod(Method method, RepositoryMetadata metadata, ProjectionFactory factory) {
        super(method, metadata, factory);
        this.method = method;
        this.scanEnabledForRepository = metadata.getRepositoryInterface().isAnnotationPresent(EnableScan.class);
        this.scanCountEnabledForRepository = metadata.getRepositoryInterface()
                .isAnnotationPresent(EnableScanCount.class);

        Query query = method.getAnnotation(Query.class);
        if (query != null) {
            String projections = query.fields();
            if (!StringUtils.isEmpty(projections)) {
                this.projectionExpression = Optional.of(query.fields());
            } else {
                this.projectionExpression = Optional.empty();
            }
            String filterExp = query.filterExpression();
            if (!StringUtils.isEmpty(filterExp)) {
                this.filterExpression = Optional.of(filterExp);
            } else {
                this.filterExpression = Optional.empty();
            }
            this.expressionAttributeValues = query.expressionMappingValues();
            this.expressionAttributeNames = query.expressionMappingNames();
            int limit = query.limit();
            if (limit != QUERY_LIMIT_UNLIMITED) {
                this.limitResults = Optional.of(query.limit());
            } else {
                this.limitResults = Optional.empty();
            }
            this.consistentReadMode = query.consistentReads();
        } else {
            this.projectionExpression = Optional.empty();
            this.limitResults = Optional.empty();
            this.consistentReadMode = QueryConstants.ConsistentReadMode.DEFAULT;
            this.filterExpression = Optional.empty();
            this.expressionAttributeNames = null;
            this.expressionAttributeValues = null;
        }
    }

    /**
     * Returns the actual return type of the method.
     *
     * @return
     */
    Class<?> getReturnType() {

        return method.getReturnType();
    }

    public boolean isScanEnabled() {
        return scanEnabledForRepository || method.isAnnotationPresent(EnableScan.class);
    }

    public boolean isScanCountEnabled() {
        return scanCountEnabledForRepository || method.isAnnotationPresent(EnableScanCount.class);
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.repository.query.QueryMethod#getEntityInformation ()
     */
    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public DynamoDBEntityInformation<T, ID> getEntityInformation() {
        return new DynamoDBEntityMetadataSupport(getDomainClass()).getEntityInformation();
    }

    public Class<T> getEntityType() {

        return getEntityInformation().getJavaType();
    }

    public Optional<String> getProjectionExpression() {
        return this.projectionExpression;
    }

    public Optional<Integer> getLimitResults() {
        return this.limitResults;
    }

    public QueryConstants.ConsistentReadMode getConsistentReadMode() {
        return this.consistentReadMode;
    }

    public Optional<String> getFilterExpression() {
        return this.filterExpression;
    }

    public ExpressionAttribute[] getExpressionAttributeNames() {
        if (expressionAttributeNames != null) {
            return expressionAttributeNames.clone();
        }
        return null;
    }

    public ExpressionAttribute[] getExpressionAttributeValues() {
        if (expressionAttributeValues != null) {
            return expressionAttributeValues.clone();
        }
        return null;
    }
}
