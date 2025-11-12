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

import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteResult;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
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
}
