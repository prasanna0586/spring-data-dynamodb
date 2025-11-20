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
package org.socialsignin.spring.data.dynamodb.core;

import org.socialsignin.spring.data.dynamodb.mapping.DynamoDBMappingContext;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteResult;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import java.util.List;
import java.util.Map;

/**
 * Interface to DynmaoDB - as seen from the Spring-Data world
 */
public interface DynamoDBOperations {

    <T> int count(Class<T> domainClass, QueryEnhancedRequest queryExpression);

    <T> int count(Class<T> domainClass, ScanEnhancedRequest scanExpression);

    <T> int count(Class<T> clazz, QueryRequest mutableQueryRequest);

    <T> PageIterable<T> query(Class<T> clazz, QueryRequest queryRequest);

    <T> PageIterable<T> query(Class<T> domainClass, QueryEnhancedRequest queryExpression);

    <T> PageIterable<T> scan(Class<T> domainClass, ScanEnhancedRequest scanExpression);

    <T> T load(Class<T> domainClass, Object hashKey, Object rangeKey);

    <T> T load(Class<T> domainClass, Object hashKey);

    <T> List<T> batchLoad(Map<Class<?>, List<Key>> itemsToGet);

    <T> T save(T entity);

    List<BatchWriteResult> batchSave(Iterable<?> entities);

    <T> T delete(T entity);

    List<BatchWriteResult> batchDelete(Iterable<?> entities);

    /**
     * Extracts unprocessed put items (saves) from batch write results.
     *
     * This method is used to extract the actual entity objects that failed to be written
     * after batch save operations, so they can be included in BatchWriteException for
     * consumer handling (retry, DLQ, alerting, etc.).
     *
     * @param results List of BatchWriteResult from batch save operations
     * @param entitiesByClass Original entities grouped by class (used to get table references)
     * @return List of unprocessed entity objects that failed to be saved
     * @since 7.0.0
     */
    List<Object> extractUnprocessedPutItems(
            List<BatchWriteResult> results,
            Map<Class<?>, List<Object>> entitiesByClass);

    /**
     * Extracts unprocessed delete items from batch write results.
     *
     * This method extracts the entity objects that failed to be deleted after batch
     * delete operations. Note that for deletes, SDK v2 returns Key objects, so we
     * reconstruct the entities from the original list.
     *
     * @param results List of BatchWriteResult from batch delete operations
     * @param entitiesByClass Original entities grouped by class (used to get table references and match keys)
     * @return List of unprocessed entity objects that failed to be deleted
     * @since 7.0.0
     */
    List<Object> extractUnprocessedDeleteItems(
            List<BatchWriteResult> results,
            Map<Class<?>, List<Object>> entitiesByClass);

    <T> String getOverriddenTableName(Class<T> domainClass, String tableName);

    /**
     * Provides access to the DynamoDB table schema of the underlying domain type.
     *
     * @param <T>
     *            The type of the domain type itself
     * @param domainClass
     *            A domain type
     *
     * @return Corresponding DynamoDB table schema
     */
    <T> TableSchema<T> getTableModel(Class<T> domainClass);

    /**
     * Provides access to the DynamoDB mapping context which contains configuration
     * such as marshalling mode for type conversions.
     *
     * @return The DynamoDB mapping context
     * @since 7.0.0
     */
    DynamoDBMappingContext getMappingContext();
}
