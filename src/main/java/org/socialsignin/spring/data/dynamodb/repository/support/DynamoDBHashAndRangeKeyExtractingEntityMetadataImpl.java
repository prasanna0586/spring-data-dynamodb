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
package org.socialsignin.spring.data.dynamodb.repository.support;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * Entity metadata implementation for DynamoDB entities with both hash and range keys.
 *
 * Handles extraction and management of composite hash/range key properties and their accessors.
 * @param <T> the entity type
 * @param <ID> the ID type (typically a composite key type)
 * @author Prasanna Kumar Ramachandran
 */
public class DynamoDBHashAndRangeKeyExtractingEntityMetadataImpl<T, ID> extends DynamoDBEntityMetadataSupport<T, ID>
        implements DynamoDBHashAndRangeKeyExtractingEntityMetadata<T, ID> {

    @NonNull
    private final DynamoDBHashAndRangeKeyMethodExtractor<T> hashAndRangeKeyMethodExtractor;

    @Nullable
    private Method hashKeySetterMethod;
    @Nullable
    private Field hashKeyField;

    /**
     * Creates a new DynamoDBHashAndRangeKeyExtractingEntityMetadataImpl for the given domain type.
     *
     * @param domainType the entity class
     */
    public DynamoDBHashAndRangeKeyExtractingEntityMetadataImpl(@NonNull final Class<T> domainType) {
        super(domainType);
        this.hashAndRangeKeyMethodExtractor = new DynamoDBHashAndRangeKeyMethodExtractorImpl<>(getJavaType());
        ReflectionUtils.doWithMethods(domainType, method -> {
            if (method.getAnnotation(DynamoDbPartitionKey.class) != null) {
                String setterMethodName = toSetterMethodNameFromAccessorMethod(method);
                if (setterMethodName != null) {
                    hashKeySetterMethod = ReflectionUtils.findMethod(domainType, setterMethodName,
                            method.getReturnType());
                }
            }
        });
        ReflectionUtils.doWithFields(domainType, field -> {
            if (field.getAnnotation(DynamoDbPartitionKey.class) != null) {

                hashKeyField = ReflectionUtils.findField(domainType, field.getName());

            }
        });
        Assert.isTrue(hashKeySetterMethod != null || hashKeyField != null,
                "Unable to find hash key field or setter method on " + domainType + "!");
        Assert.isTrue(hashKeySetterMethod == null || hashKeyField == null,
                "Found both hash key field and setter method on " + domainType + "!");

    }

    @NonNull
    @Override
    public <H> HashAndRangeKeyExtractor<ID, H> getHashAndRangeKeyExtractor(@NonNull Class<ID> idClass) {
        return new CompositeIdHashAndRangeKeyExtractor<>(idClass);
    }

    @NonNull
    @Override
    public String getRangeKeyPropertyName() {
        return getPropertyNameForAccessorMethod(hashAndRangeKeyMethodExtractor.getRangeKeyMethod());
    }

    @NonNull
    @Override
    public Set<String> getIndexRangeKeyPropertyNames() {
        final Set<String> propertyNames = new HashSet<>();
        ReflectionUtils.doWithMethods(getJavaType(), method -> {
            if (method.getAnnotation(DynamoDbSecondarySortKey.class) != null) {
                if (method.getAnnotation(DynamoDbSecondarySortKey.class).indexNames() != null
                        && method.getAnnotation(DynamoDbSecondarySortKey.class).indexNames().length > 0) {
                    propertyNames.add(getPropertyNameForAccessorMethod(method));
                }
            }
        });
        ReflectionUtils.doWithFields(getJavaType(), field -> {
            if (field.getAnnotation(DynamoDbSecondarySortKey.class) != null) {
                if (field.getAnnotation(DynamoDbSecondarySortKey.class).indexNames() != null
                        && field.getAnnotation(DynamoDbSecondarySortKey.class).indexNames().length > 0) {
                    propertyNames.add(getPropertyNameForField(field));
                }
            }
        });
        return propertyNames;
    }

    @NonNull
    public T getHashKeyPropotypeEntityForHashKey(Object hashKey) {

        try {
            T entity = getJavaType().getDeclaredConstructor().newInstance();
            if (hashKeySetterMethod != null) {
                ReflectionUtils.invokeMethod(hashKeySetterMethod, entity, hashKey);
            } else if (hashKeyField != null) {
                ReflectionUtils.setField(hashKeyField, entity, hashKey);
            }

            return entity;
        } catch (InstantiationException | IllegalAccessException | NoSuchMethodException
                | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isCompositeHashAndRangeKeyProperty(@NonNull String propertyName) {
        return isFieldAnnotatedWith(propertyName);
    }

}
