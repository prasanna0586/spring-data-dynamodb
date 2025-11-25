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
package org.socialsignin.spring.data.dynamodb.mapping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socialsignin.spring.data.dynamodb.core.MarshallingMode;
import org.springframework.data.mapping.context.AbstractMappingContext;
import org.springframework.data.mapping.model.Property;
import org.springframework.data.mapping.model.SimpleTypeHolder;
import org.springframework.data.util.TypeInformation;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Default implementation of a {@link org.springframework.data.mapping.context.MappingContext} for DynamoDB using
 * {@link DynamoDBPersistentEntityImpl} and {@link DynamoDBPersistentProperty} as primary abstractions.
 * @author Prasanna Kumar Ramachandran
 */
public class DynamoDBMappingContext
        extends AbstractMappingContext<DynamoDBPersistentEntityImpl<?>, DynamoDBPersistentProperty> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DynamoDBMappingContext.class);

    @NonNull
    private final MarshallingMode marshallingMode;

    /**
     * Creates a new DynamoDBMappingContext with the specified marshalling mode.
     * @param marshallingMode The marshalling mode to use for type conversions
     * @since 7.0.0
     */
    public DynamoDBMappingContext(@Nullable MarshallingMode marshallingMode) {
        this.marshallingMode = marshallingMode != null ? marshallingMode : MarshallingMode.SDK_V2_NATIVE;
        LOGGER.info("DynamoDBMappingContext initialized with Marshalling Mode: {}", this.marshallingMode);
    }

    /**
     * Creates a new DynamoDBMappingContext with SDK_V2_NATIVE marshalling mode (default).
     */
    public DynamoDBMappingContext() {
        this(MarshallingMode.SDK_V2_NATIVE);
    }

    /**
     * Returns the marshalling mode configured for this mapping context.
     * @return The marshalling mode
     * @since 7.0.0
     */
    @NonNull
    public MarshallingMode getMarshallingMode() {
        return marshallingMode;
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.mapping.context.AbstractMappingContext#shouldCreatePersistentEntityFor(org.springframework.data.util.TypeInformation)
     */
    @NonNull
    @Override
    protected <T> DynamoDBPersistentEntityImpl<?> createPersistentEntity(@NonNull TypeInformation<T> typeInformation) {
        return new DynamoDBPersistentEntityImpl<>(typeInformation, null);

    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.mapping.AbstractMappingContext#createPersistentProperty(java.lang.reflect.Field, java.beans.PropertyDescriptor, org.springframework.data.mapping.MutablePersistentEntity, org.springframework.data.mapping.SimpleTypeHolder)
     */
    @NonNull
    @Override
    protected DynamoDBPersistentProperty createPersistentProperty(@NonNull Property property,
                                                                  @NonNull DynamoDBPersistentEntityImpl<?> owner, @NonNull SimpleTypeHolder simpleTypeHolder) {
        return new DynamoDBPersistentPropertyImpl(property, owner, simpleTypeHolder);
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.mapping.context.AbstractMappingContext#shouldCreatePersistentEntityFor(org.springframework.data.util.TypeInformation)
     */
    @Override
    protected boolean shouldCreatePersistentEntityFor(@NonNull TypeInformation<?> type) {

        boolean hasPartitionKey = false;
        boolean hasSortKey = false;
        for (Method method : type.getType().getMethods()) {
            if (method.isAnnotationPresent(DynamoDbPartitionKey.class)) {
                hasPartitionKey = true;
            }
            if (method.isAnnotationPresent(DynamoDbSortKey.class)) {
                hasSortKey = true;
            }

        }
        for (Field field : type.getType().getFields()) {
            if (field.isAnnotationPresent(DynamoDbPartitionKey.class)) {
                hasPartitionKey = true;
            }
            if (field.isAnnotationPresent(DynamoDbSortKey.class)) {
                hasSortKey = true;
            }

        }
        // SDK v2: Check for @DynamoDbBean or @DynamoDbImmutable annotations
        return type.getType().isAnnotationPresent(DynamoDbBean.class)
                || type.getType().isAnnotationPresent(DynamoDbImmutable.class)
                || (hasPartitionKey && hasSortKey);
    }

}
