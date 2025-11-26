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

import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.NamedQueries;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.QueryLookupStrategy.Key;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * Factory class for creating {@link QueryLookupStrategy} implementations for DynamoDB repositories.
 * Supports different lookup strategies including CREATE, CREATE_IF_NOT_FOUND, and DECLARED query patterns.
 * @author Prasanna Kumar Ramachandran
 */
public class DynamoDBQueryLookupStrategy {

    /**
     * Private constructor to prevent instantiation.
     */
    private DynamoDBQueryLookupStrategy() {

    }

    /**
     * Base class for {@link QueryLookupStrategy} implementations that need access to DynamoDB operations.
     * <p>
     * @author Prasanna Kumar Ramachandran
     */
    private abstract static class AbstractQueryLookupStrategy implements QueryLookupStrategy {

        /**
         * Names of methods that are handled by the base repository implementation and should not
         * have queries created for them. These are standard CRUD operations from Spring Data's
         * CrudRepository and PagingAndSortingRepository interfaces.
         */
        private static final Set<String> BASE_REPOSITORY_METHOD_NAMES = Set.of(
                "save", "saveAll", "findById", "existsById", "findAll", "findAllById",
                "count", "deleteById", "delete", "deleteAllById", "deleteAll"
        );

        protected final DynamoDBOperations dynamoDBOperations;

        public AbstractQueryLookupStrategy(DynamoDBOperations dynamoDBOperations) {

            this.dynamoDBOperations = dynamoDBOperations;
        }

        /**
         * Checks if the given method is a base CRUD operation that is handled by the repository
         * implementation class (e.g., SimpleDynamoDBCrudRepository) and should not have a query created.
         * <p>
         * This is particularly important for GraalVM native image support, where the AOT processor
         * calls resolveQuery for ALL repository methods during build time. Returning a no-op query for base
         * CRUD methods tells Spring Data to use the repository implementation instead of trying
         * to create a derived query.
         *
         * @param method the method to check
         * @return true if the method is a base CRUD operation
         */
        protected boolean isBaseRepositoryMethod(Method method) {
            // Check if the method name matches a known CRUD operation
            if (!BASE_REPOSITORY_METHOD_NAMES.contains(method.getName())) {
                return false;
            }

            // Check if the method is declared in Spring Data base interfaces
            Class<?> declaringClass = method.getDeclaringClass();
            String declaringClassName = declaringClass.getName();
            return declaringClassName.startsWith("org.springframework.data.repository.");
        }

        /*
         * (non-Javadoc)
         * @see org.springframework.data.repository.query.QueryLookupStrategy#resolveQuery(java.lang.reflect.Method, org.springframework.data.repository.core.RepositoryMetadata, org.springframework.data.repository.core.NamedQueries)
         */
        @NonNull
        @Override
        public final RepositoryQuery resolveQuery(@NonNull Method method, @NonNull RepositoryMetadata metadata, @NonNull ProjectionFactory factory,
                                                  @NonNull NamedQueries namedQueries) {

            // Return a no-op query for base CRUD operations - they are handled by the repository implementation
            if (isBaseRepositoryMethod(method)) {
                return new NoOpRepositoryQuery(method);
            }

            return createDynamoDBQuery(method, metadata, factory, metadata.getDomainType(), metadata.getIdType(),
                    namedQueries);
        }

        protected abstract <T, ID> RepositoryQuery createDynamoDBQuery(Method method, RepositoryMetadata metadata,
                ProjectionFactory factory, Class<T> entityClass, Class<ID> idClass, NamedQueries namedQueries);
    }

    /**
     * {@link QueryLookupStrategy} to create a query from the method name.
     * <p>
     * @author Prasanna Kumar Ramachandran
     */
    private static class CreateQueryLookupStrategy extends AbstractQueryLookupStrategy {

        public CreateQueryLookupStrategy(DynamoDBOperations dynamoDBOperations) {

            super(dynamoDBOperations);
        }

        @NonNull
        @Override
        protected <T, ID> RepositoryQuery createDynamoDBQuery(@NonNull Method method, @NonNull RepositoryMetadata metadata,
                                                              @NonNull ProjectionFactory factory, Class<T> entityClass, Class<ID> idClass, NamedQueries namedQueries) {
            try {
                return new PartTreeDynamoDBQuery<>(dynamoDBOperations,
                        new DynamoDBQueryMethod<T, ID>(method, metadata, factory));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        String.format("Could not create query metamodel for method %s!", method), e);
            }
        }

    }

    /**
     * {@link QueryLookupStrategy} that tries to detect a declared query declared via
     * {@link org.socialsignin.spring.data.dynamodb.query.Query} annotation
     * <p>
     * @author Prasanna Kumar Ramachandran
     */
    private static class DeclaredQueryLookupStrategy extends AbstractQueryLookupStrategy {

        public DeclaredQueryLookupStrategy(DynamoDBOperations dynamoDBOperations) {

            super(dynamoDBOperations);
        }

        @Override
        protected <T, ID> RepositoryQuery createDynamoDBQuery(Method method, RepositoryMetadata metadata,
                ProjectionFactory factory, Class<T> entityClass, Class<ID> idClass, NamedQueries namedQueries) {
            throw new UnsupportedOperationException("Declared Queries not supported at this time");
        }

    }

    /**
     * {@link QueryLookupStrategy} to try to detect a declared query first
     * (e.g., {@link org.socialsignin.spring.data.dynamodb.repository.Query}). In case none is found we fall back on query creation.
     * <p>
     * @author Prasanna Kumar Ramachandran
     */
    private static class CreateIfNotFoundQueryLookupStrategy extends AbstractQueryLookupStrategy {

        @NonNull
        private final DeclaredQueryLookupStrategy strategy;
        @NonNull
        private final CreateQueryLookupStrategy createStrategy;

        public CreateIfNotFoundQueryLookupStrategy(DynamoDBOperations dynamoDBOperations) {

            super(dynamoDBOperations);
            this.strategy = new DeclaredQueryLookupStrategy(dynamoDBOperations);
            this.createStrategy = new CreateQueryLookupStrategy(dynamoDBOperations);
        }

        @NonNull
        @Override
        protected <T, ID> RepositoryQuery createDynamoDBQuery(@NonNull Method method, @NonNull RepositoryMetadata metadata,
                                                              @NonNull ProjectionFactory factory, Class<T> entityClass, Class<ID> idClass, NamedQueries namedQueries) {
            try {
                return strategy.createDynamoDBQuery(method, metadata, factory, entityClass, idClass, namedQueries);
            } catch (IllegalStateException | UnsupportedOperationException e) {
                return createStrategy.createDynamoDBQuery(method, metadata, factory, entityClass, idClass,
                        namedQueries);
            }

        }
    }

    /**
     * Creates a {@link QueryLookupStrategy} for the given DynamoDB operations.
     * @param dynamoDBOperations The current operation
     * @param key The key of the entity
     * @return The created {@link QueryLookupStrategy}
     */
    @NonNull
    public static QueryLookupStrategy create(DynamoDBOperations dynamoDBOperations, @Nullable Key key) {

        if (key == null) {
            return new CreateQueryLookupStrategy(dynamoDBOperations);
        }

        return switch (key) {
            case CREATE -> new CreateQueryLookupStrategy(dynamoDBOperations);
            case CREATE_IF_NOT_FOUND -> new CreateIfNotFoundQueryLookupStrategy(dynamoDBOperations);
            default -> throw new IllegalArgumentException(String.format("Unsupported query lookup strategy %s!", key));
        };
    }

    /**
     * A no-operation {@link RepositoryQuery} used as a placeholder for base CRUD methods.
     * <p>
     * This query is returned for methods like save, delete, findAll, etc. that are implemented
     * by the base repository class (SimpleDynamoDBCrudRepository). The actual method invocation
     * will be handled by the repository implementation, not by this query.
     * <p>
     * This is necessary for GraalVM native image support where the AOT processor requires
     * all query methods to return a non-null RepositoryQuery, but base CRUD methods should
     * delegate to the repository implementation rather than execute as derived queries.
     */
    private static class NoOpRepositoryQuery implements RepositoryQuery {

        private final Method method;

        NoOpRepositoryQuery(Method method) {
            this.method = method;
        }

        @Override
        public Object execute(Object[] parameters) {
            // This should never be called as base CRUD methods are handled by the repository implementation
            throw new UnsupportedOperationException(
                    String.format("Method %s should be handled by the repository implementation, not as a query method",
                            method.getName()));
        }

        @Override
        public QueryMethod getQueryMethod() {
            // Return null as this is a placeholder query - the actual query method is not used
            return null;
        }
    }

}
