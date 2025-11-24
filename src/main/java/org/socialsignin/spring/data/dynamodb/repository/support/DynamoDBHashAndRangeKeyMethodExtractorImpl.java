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

import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.ReflectionUtils.FieldCallback;
import org.springframework.util.ReflectionUtils.MethodCallback;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * @author Prasanna Kumar Ramachandran
 */
public class DynamoDBHashAndRangeKeyMethodExtractorImpl<T> implements DynamoDBHashAndRangeKeyMethodExtractor<T> {

    @NonNull
    private final Class<T> idType;
    private Method hashKeyMethod;
    private Method rangeKeyMethod;

    private Field hashKeyField;
    private Field rangeKeyField;

    /**
     * Creates a new {@link DynamoDBHashAndRangeKeyMethodExtractor} for the given domain type.
     *
     * @param idType
     *            must not be {@literal null}.
     */
    public DynamoDBHashAndRangeKeyMethodExtractorImpl(@NonNull final Class<T> idType) {

        Assert.notNull(idType, "Id type must not be null!");
        this.idType = idType;
        ReflectionUtils.doWithMethods(idType, method -> {
            if (method.getAnnotation(DynamoDbPartitionKey.class) != null) {
                Assert.isNull(hashKeyMethod,
                        "Multiple methods annotated by @DynamoDbPartitionKey within type " + idType.getName() + "!");
                ReflectionUtils.makeAccessible(method);
                hashKeyMethod = method;
            }
        });
        ReflectionUtils.doWithFields(idType, field -> {
            if (field.getAnnotation(DynamoDbPartitionKey.class) != null) {
                Assert.isNull(hashKeyField,
                        "Multiple fields annotated by @DynamoDbPartitionKey within type " + idType.getName() + "!");
                ReflectionUtils.makeAccessible(field);

                hashKeyField = field;
            }
        });
        ReflectionUtils.doWithMethods(idType, method -> {
            if (method.getAnnotation(DynamoDbSortKey.class) != null) {
                Assert.isNull(rangeKeyMethod,
                        "Multiple methods annotated by @DynamoDbSortKey within type " + idType.getName() + "!");
                ReflectionUtils.makeAccessible(method);
                rangeKeyMethod = method;
            }
        });
        ReflectionUtils.doWithFields(idType, field -> {
            if (field.getAnnotation(DynamoDbSortKey.class) != null) {
                Assert.isNull(rangeKeyField,
                        "Multiple fields annotated by @DynamoDbSortKey within type " + idType.getName() + "!");
                ReflectionUtils.makeAccessible(field);
                rangeKeyField = field;
            }
        });
        if (hashKeyMethod == null && hashKeyField == null) {
            throw new IllegalArgumentException(
                    "No method or field annotated by @DynamoDbPartitionKey within type " + idType.getName() + "!");
        }
        if (rangeKeyMethod == null && rangeKeyField == null) {
            throw new IllegalArgumentException(
                    "No method or field annotated by @DynamoDbSortKey within type " + idType.getName() + "!");
        }
        if (hashKeyMethod != null && hashKeyField != null) {
            throw new IllegalArgumentException(
                    "Both method and field annotated by @DynamoDbPartitionKey within type " + idType.getName() + "!");
        }
        if (rangeKeyMethod != null && rangeKeyField != null) {
            throw new IllegalArgumentException(
                    "Both method and field annotated by @DynamoDbSortKey within type " + idType.getName() + "!");
        }
    }

    @NonNull
    @Override
    public Class<T> getJavaType() {
        return idType;
    }

    @Override
    public Method getHashKeyMethod() {

        return hashKeyMethod;
    }

    @Override
    public Method getRangeKeyMethod() {
        return rangeKeyMethod;
    }

    @Override
    public Field getHashKeyField() {

        return hashKeyField;
    }

    @Override
    public Field getRangeKeyField() {
        return rangeKeyField;
    }

}
