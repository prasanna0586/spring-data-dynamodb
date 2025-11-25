/*
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
 * A QueryMethod implementation for DynamoDB repositories that provides metadata about
 * a query method including projection expressions, filters, and scan settings.
 * @param <T> the entity type
 * @param <ID> the ID type of the entity
 * @author Prasanna Kumar Ramachandran
 */
public class DynamoDBQueryMethod<T, ID> extends QueryMethod {

    @NonNull
    private final Method method;
    private final boolean scanEnabledForRepository;
    private final boolean scanCountEnabledForRepository;
    @Nullable
    private final String projectionExpression;
    @Nullable
    private final Integer limitResults;
    @Nullable
    private final String filterExpression;
    @Nullable
    private final ExpressionAttribute[] expressionAttributeNames;
    @Nullable
    private final ExpressionAttribute[] expressionAttributeValues;
    private final QueryConstants.ConsistentReadMode consistentReadMode;

    /**
     * Creates a new DynamoDBQueryMethod.
     * @param method the query method
     * @param metadata the repository metadata
     * @param factory the projection factory
     */
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
                this.projectionExpression = query.fields();
            } else {
                this.projectionExpression = null;
            }
            String filterExp = query.filterExpression();
            if (StringUtils.hasLength(filterExp)) {
                this.filterExpression = filterExp;
            } else {
                this.filterExpression = null;
            }
            this.expressionAttributeValues = query.expressionMappingValues();
            this.expressionAttributeNames = query.expressionMappingNames();
            int limit = query.limit();
            if (limit != QUERY_LIMIT_UNLIMITED) {
                this.limitResults = query.limit();
            } else {
                this.limitResults = null;
            }
            this.consistentReadMode = query.consistentReads();
        } else {
            this.projectionExpression = null;
            this.limitResults = null;
            this.consistentReadMode = QueryConstants.ConsistentReadMode.DEFAULT;
            this.filterExpression = null;
            this.expressionAttributeNames = null;
            this.expressionAttributeValues = null;
        }
    }

    /**
     * Checks if scan operations are enabled for this query method.
     * @return true if scan is enabled, false otherwise
     */
    public boolean isScanEnabled() {
        return scanEnabledForRepository || method.isAnnotationPresent(EnableScan.class);
    }

    /**
     * Checks if scan count operations are enabled for this query method.
     * @return true if scan count is enabled, false otherwise
     */
    public boolean isScanCountEnabled() {
        return scanCountEnabledForRepository || method.isAnnotationPresent(EnableScanCount.class);
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.repository.query.QueryMethod#getEntityInformation()
     */
    @NonNull
    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public DynamoDBEntityInformation<T, ID> getEntityInformation() {
        return new DynamoDBEntityMetadataSupport(getDomainClass()).getEntityInformation();
    }

    /**
     * Gets the entity type for this query method.
     * @return the entity type
     */
    @NonNull
    public Class<T> getEntityType() {

        return getEntityInformation().getJavaType();
    }

    /**
     * Gets the projection expression if configured.
     * @return optional containing the projection expression
     */
    @NonNull
    public Optional<String> getProjectionExpression() {
        return Optional.ofNullable(this.projectionExpression);
    }

    /**
     * Gets the limit for query results if configured.
     * @return optional containing the limit
     */
    @NonNull
    public Optional<Integer> getLimitResults() {
        return Optional.ofNullable(this.limitResults);
    }

    /**
     * Gets the consistent read mode configuration.
     * @return the consistent read mode
     */
    public QueryConstants.ConsistentReadMode getConsistentReadMode() {
        return this.consistentReadMode;
    }

    /**
     * Gets the filter expression if configured.
     * @return optional containing the filter expression
     */
    @NonNull
    public Optional<String> getFilterExpression() {
        return Optional.ofNullable(this.filterExpression);
    }

    /**
     * Gets the expression attribute names for the query.
     * @return array of expression attribute names or null
     */
    @Nullable
    public ExpressionAttribute[] getExpressionAttributeNames() {
        if (expressionAttributeNames != null) {
            return expressionAttributeNames.clone();
        }
        return null;
    }

    /**
     * Gets the expression attribute values for the query.
     * @return array of expression attribute values or null
     */
    @Nullable
    public ExpressionAttribute[] getExpressionAttributeValues() {
        if (expressionAttributeValues != null) {
            return expressionAttributeValues.clone();
        }
        return null;
    }
}
