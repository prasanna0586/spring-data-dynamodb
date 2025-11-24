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
package org.socialsignin.spring.data.dynamodb.repository.support;

import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;
import org.socialsignin.spring.data.dynamodb.exception.BatchWriteException;
import org.socialsignin.spring.data.dynamodb.repository.DynamoDBCrudRepository;
import org.socialsignin.spring.data.dynamodb.utils.ExceptionHandler;
import org.socialsignin.spring.data.dynamodb.utils.SortHandler;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteResult;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.StreamSupport;

/**
 * Default implementation of the {@link org.springframework.data.repository.CrudRepository} interface.
 *
 * @param <T>
 *            the type of the entity to handle
 * @param <ID>
 *            the type of the entity's identifier
 */
public class SimpleDynamoDBCrudRepository<T, ID>
        implements DynamoDBCrudRepository<T, ID>, SortHandler, ExceptionHandler {

    protected DynamoDBEntityInformation<T, ID> entityInformation;

    protected Class<T> domainType;

    protected EnableScanPermissions enableScanPermissions;

    protected DynamoDBOperations dynamoDBOperations;

    public SimpleDynamoDBCrudRepository(DynamoDBEntityInformation<T, ID> entityInformation,
            DynamoDBOperations dynamoDBOperations, EnableScanPermissions enableScanPermissions) {
        Assert.notNull(entityInformation, "entityInformation must not be null");
        Assert.notNull(dynamoDBOperations, "dynamoDBOperations must not be null");

        this.entityInformation = entityInformation;
        this.dynamoDBOperations = dynamoDBOperations;
        this.domainType = entityInformation.getJavaType();
        this.enableScanPermissions = enableScanPermissions;
    }

    /**
     * Converts a Java object to SDK v2 AttributeValue for Key construction.
     *
     * @param value The value to convert
     * @return The AttributeValue representation
     */
    private AttributeValue toAttributeValue(Object value) {
        return switch (value) {
            case null -> AttributeValue.builder().nul(true).build();
            case String s -> AttributeValue.builder().s(s).build();
            case Number number -> AttributeValue.builder().n(value.toString()).build();
            default ->
                // Fallback: convert to string
                    AttributeValue.builder().s(value.toString()).build();
        };

    }

    @Override
    public Optional<T> findById(ID id) {

        Assert.notNull(id, "The given id must not be null!");

        T result;
        if (entityInformation.isRangeKeyAware()) {
            result = dynamoDBOperations.load(domainType, entityInformation.getHashKey(id),
                    entityInformation.getRangeKey(id));
        } else {
            result = dynamoDBOperations.load(domainType, entityInformation.getHashKey(id));
        }

        return Optional.ofNullable(result);
    }

    @Override
    public List<T> findAllById(Iterable<ID> ids) {

        Assert.notNull(ids, "The given ids must not be null!");

        // Works only with non-parallel streams!
        AtomicInteger idx = new AtomicInteger();
        List<Key> keys = StreamSupport.stream(ids.spliterator(), false).map(id -> {

            Assert.notNull(id, "The given id at position " + idx.getAndIncrement() + " must not be null!");

            if (entityInformation.isRangeKeyAware()) {
                return Key.builder()
                        .partitionValue(toAttributeValue(entityInformation.getHashKey(id)))
                        .sortValue(toAttributeValue(entityInformation.getRangeKey(id)))
                        .build();
            } else {
                return Key.builder()
                        .partitionValue(toAttributeValue(id))
                        .build();
            }
        }).toList();

        Map<Class<?>, List<Key>> keysMap = Collections.singletonMap(domainType, keys);
        return dynamoDBOperations.batchLoad(keysMap);
    }

    @Override
    public <S extends T> S save(S entity) {
        // Return the saved entity from DynamoDBOperations.save() to properly handle
        // @DynamoDbVersionAttribute and other attributes that may be updated during save.
        return dynamoDBOperations.save(entity);
    }

    /**
     * {@inheritDoc}
     *
     * @throws BatchWriteException
     *             in case of an error during saving
     */
    @Override
    public <S extends T> Iterable<S> saveAll(Iterable<S> entities)
            throws BatchWriteException, IllegalArgumentException {

        Assert.notNull(entities, "The given Iterable of entities not be null!");

        // Group entities by class for extraction if needed
        Map<Class<?>, List<Object>> entitiesByClass = new HashMap<>();
        for (S entity : entities) {
            entitiesByClass.computeIfAbsent(entity.getClass(), k -> new ArrayList<>()).add(entity);
        }

        List<BatchWriteResult> batchResults = dynamoDBOperations.batchSave(entities);

        // Extract unprocessed entities using SDK v2 extraction
        List<Object> unprocessedEntities = dynamoDBOperations.extractUnprocessedPutItems(
                batchResults, entitiesByClass);

        if (unprocessedEntities.isEmpty()) {
            // Happy path - all entities were successfully saved
            return entities;
        } else {
            // Throw exception with actual unprocessed entities
            throw repackageToException(
                    unprocessedEntities,
                    0, // SDK v2 client handles retries internally
                    null, // No exception, just unprocessed items
                    BatchWriteException.class);
        }
    }

    @Override
    public boolean existsById(ID id) {

        Assert.notNull(id, "The given id must not be null!");
        return findById(id).isPresent();
    }

    void assertScanEnabled(boolean scanEnabled, String methodName) {
        Assert.isTrue(scanEnabled, "Scanning for unpaginated " + methodName + "() queries is not enabled.  "
                + "To enable, re-implement the " + methodName
                + "() method in your repository interface and annotate with @EnableScan, or "
                + "enable scanning for all repository methods by annotating your repository interface with @EnableScan");
    }

    @Override
    public List<T> findAll() {

        assertScanEnabled(enableScanPermissions.isFindAllUnpaginatedScanEnabled(), "findAll");
        ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder().build();
        return dynamoDBOperations.scan(domainType, scanRequest).items().stream().toList();
    }

    @Override
    public long count() {
        assertScanEnabled(enableScanPermissions.isCountUnpaginatedScanEnabled(), "count");
        final ScanEnhancedRequest scanRequest = ScanEnhancedRequest.builder().build();
        return dynamoDBOperations.count(domainType, scanRequest);
    }

    @Override
    public void deleteById(ID id) {

        Assert.notNull(id, "The given id must not be null!");

        Optional<T> entity = findById(id);

        if (entity.isPresent()) {
            dynamoDBOperations.delete(entity.get());

        } else {
            throw new EmptyResultDataAccessException(String.format("No %s entity with id %s exists!", domainType, id),
                    1);
        }
    }

    @Override
    public void delete(T entity) {
        Assert.notNull(entity, "The entity must not be null!");
        dynamoDBOperations.delete(entity);
    }

    @Override
    public void deleteAllById(Iterable<? extends ID> ids) {
        var bla = StreamSupport.stream(ids.spliterator(), false).map(id -> (ID) id).toList();
        deleteAll(findAllById(bla));
    }

    @Override
    public void deleteAll(Iterable<? extends T> entities) {

        Assert.notNull(entities, "The given Iterable of entities not be null!");
        dynamoDBOperations.batchDelete(entities);
    }

    @Override
    public void deleteAll() {

        assertScanEnabled(enableScanPermissions.isDeleteAllUnpaginatedScanEnabled(), "deleteAll");
        dynamoDBOperations.batchDelete(findAll());
    }

    @NonNull
    public DynamoDBEntityInformation<T, ID> getEntityInformation() {
        return this.entityInformation;
    }
}
