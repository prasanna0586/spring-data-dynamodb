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
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.socialsignin.spring.data.dynamodb.repository.QueryConstants.QUERY_LIMIT_UNLIMITED;

/**
 * @author Prasanna Kumar Ramachandran
 */
public class DynamoDBQueryMethod<T, ID> extends QueryMethod {

    @NonNull
    private final Method method;
    private final boolean scanEnabledForRepository;
    private final boolean scanCountEnabledForRepository;
    @NonNull
    private final Optional<String> projectionExpression;
    @NonNull
    private final Optional<Integer> limitResults;
    @NonNull
    private final Optional<String> filterExpression;
    @Nullable
    private final ExpressionAttribute[] expressionAttributeNames;
    @Nullable
    private final ExpressionAttribute[] expressionAttributeValues;
    private final QueryConstants.ConsistentReadMode consistentReadMode;

    public DynamoDBQueryMethod(@NonNull Method method, @NonNull RepositoryMetadata metadata, @NonNull ProjectionFactory factory) {
        super(method, metadata, factory);
        this.method = method;
        this.scanEnabledForRepository = metadata.getRepositoryInterface().isAnnotationPresent(EnableScan.class);
        this.scanCountEnabledForRepository = metadata.getRepositoryInterface()
                .isAnnotationPresent(EnableScanCount.class);

        Query query = method.getAnnotation(Query.class);
        if (query != null) {
            String projections = query.fields();
            if (StringUtils.hasLength(projections)) {
                this.projectionExpression = Optional.of(query.fields());
            } else {
                this.projectionExpression = Optional.empty();
            }
            String filterExp = query.filterExpression();
            if (StringUtils.hasLength(filterExp)) {
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
    @NonNull
    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public DynamoDBEntityInformation<T, ID> getEntityInformation() {
        return new DynamoDBEntityMetadataSupport(getDomainClass()).getEntityInformation();
    }

    @NonNull
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

    @Nullable
    public ExpressionAttribute[] getExpressionAttributeNames() {
        if (expressionAttributeNames != null) {
            return expressionAttributeNames.clone();
        }
        return null;
    }

    @Nullable
    public ExpressionAttribute[] getExpressionAttributeValues() {
        if (expressionAttributeValues != null) {
            return expressionAttributeValues.clone();
        }
        return null;
    }
}
