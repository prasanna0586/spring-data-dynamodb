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
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * @author Michael Lavelle
 * @author Sebastian Just
 */
public class DynamoDBEntityMetadataSupport<T, ID> implements DynamoDBHashKeyExtractingEntityMetadata<T> {

    private final Class<T> domainType;
    private boolean hasRangeKey;
    private String hashKeyPropertyName;
    private List<String> globalIndexHashKeyPropertyNames;
    private List<String> globalIndexRangeKeyPropertyNames;

    private String dynamoDBTableName;
    private Map<String, String[]> globalSecondaryIndexNames = new HashMap<>();

    @Override
    public String getDynamoDBTableName() {
        return dynamoDBTableName;
    }

    /**
     * Creates a new {@link DynamoDBEntityMetadataSupport} for the given domain type.
     *
     * @param domainType
     *            must not be {@literal null}.
     */
    public DynamoDBEntityMetadataSupport(final Class<T> domainType) {
        this(domainType, null);
    }

    /**
     * Creates a new {@link DynamoDBEntityMetadataSupport} for the given domain type and dynamoDB mapper config.
     *
     * @param domainType
     *            must not be {@literal null}.
     * @param dynamoDBOperations
     *            dynamoDBOperations as populated from Spring Data DynamoDB Configuration
     */
    public DynamoDBEntityMetadataSupport(final Class<T> domainType, DynamoDBOperations dynamoDBOperations) {

        Assert.notNull(domainType, "Domain type must not be null!");
        this.domainType = domainType;

        DynamoDbBean table = this.domainType.getAnnotation(DynamoDbBean.class);
        DynamoDbImmutable immutableTable = this.domainType.getAnnotation(DynamoDbImmutable.class);
        Assert.isTrue(table != null || immutableTable != null, "Domain type must be annotated with @DynamoDbBean or @DynamoDbImmutable!");

        // In SDK v2, table name is typically inferred from class name or set via TableSchema
        // For now, use the class simple name as default
        String tableName = domainType.getSimpleName();

        if (dynamoDBOperations != null) {
            this.dynamoDBTableName = dynamoDBOperations.getOverriddenTableName(domainType, tableName);
        } else {
            this.dynamoDBTableName = tableName;
        }
        this.hashKeyPropertyName = null;
        this.globalSecondaryIndexNames = new HashMap<>();
        this.globalIndexHashKeyPropertyNames = new ArrayList<>();
        this.globalIndexRangeKeyPropertyNames = new ArrayList<>();
        ReflectionUtils.doWithMethods(domainType, method -> {
            if (method.getAnnotation(DynamoDbPartitionKey.class) != null) {
                hashKeyPropertyName = getPropertyNameForAccessorMethod(method);
            }
            if (method.getAnnotation(DynamoDbSortKey.class) != null) {
                hasRangeKey = true;
            }
            DynamoDbSecondarySortKey dynamoDBRangeKeyAnnotation = method.getAnnotation(DynamoDbSecondarySortKey.class);
            DynamoDbSecondaryPartitionKey dynamoDBHashKeyAnnotation = method.getAnnotation(DynamoDbSecondaryPartitionKey.class);

            if (dynamoDBRangeKeyAnnotation != null) {
                addGlobalSecondaryIndexNames(method, dynamoDBRangeKeyAnnotation);
            }
            if (dynamoDBHashKeyAnnotation != null) {
                addGlobalSecondaryIndexNames(method, dynamoDBHashKeyAnnotation);
            }
        });
        ReflectionUtils.doWithFields(domainType, field -> {
            if (field.getAnnotation(DynamoDbPartitionKey.class) != null) {
                hashKeyPropertyName = getPropertyNameForField(field);
            }
            if (field.getAnnotation(DynamoDbSortKey.class) != null) {
                hasRangeKey = true;
            }
            DynamoDbSecondarySortKey dynamoDBRangeKeyAnnotation = field.getAnnotation(DynamoDbSecondarySortKey.class);
            DynamoDbSecondaryPartitionKey dynamoDBHashKeyAnnotation = field.getAnnotation(DynamoDbSecondaryPartitionKey.class);

            if (dynamoDBRangeKeyAnnotation != null) {
                addGlobalSecondaryIndexNames(field, dynamoDBRangeKeyAnnotation);
            }
            if (dynamoDBHashKeyAnnotation != null) {
                addGlobalSecondaryIndexNames(field, dynamoDBHashKeyAnnotation);
            }
        });
        Assert.notNull(hashKeyPropertyName, "Unable to find hash key field or getter method on " + domainType + "!");
    }

    public DynamoDBEntityInformation<T, ID> getEntityInformation() {

        if (hasRangeKey) {
            DynamoDBHashAndRangeKeyExtractingEntityMetadataImpl<T, ID> metadata = new DynamoDBHashAndRangeKeyExtractingEntityMetadataImpl<T, ID>(
                    domainType);
            return new DynamoDBIdIsHashAndRangeKeyEntityInformationImpl<>(domainType, metadata);
        } else {
            return new DynamoDBIdIsHashKeyEntityInformationImpl<>(domainType, this);
        }
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.repository.core.EntityMetadata#getJavaType()
     */
    @Override
    public Class<T> getJavaType() {
        return domainType;
    }

    @Override
    public boolean isHashKeyProperty(String propertyName) {
        return hashKeyPropertyName.equals(propertyName);
    }

    protected boolean isFieldAnnotatedWith(final String propertyName, final Class<? extends Annotation> annotation) {

        Field field = findField(propertyName);
        return field != null && field.getAnnotation(annotation) != null;
    }

    private String toGetMethodName(String propertyName) {
        String methodName = propertyName.substring(0, 1).toUpperCase();
        if (propertyName.length() > 1) {
            methodName = methodName + propertyName.substring(1);
        }
        return "get" + methodName;
    }

    protected String toSetterMethodNameFromAccessorMethod(Method method) {
        String accessorMethodName = method.getName();
        if (accessorMethodName.startsWith("get")) {
            return "set" + accessorMethodName.substring(3);
        } else if (accessorMethodName.startsWith("is")) {
            return "is" + accessorMethodName.substring(2);
        }
        return null;
    }

    private String toIsMethodName(String propertyName) {
        String methodName = propertyName.substring(0, 1).toUpperCase();
        if (propertyName.length() > 1) {
            methodName = methodName + propertyName.substring(1);
        }
        return "is" + methodName;
    }

    private Method findMethod(String propertyName) {
        Method method = ReflectionUtils.findMethod(domainType, toGetMethodName(propertyName));
        if (method == null) {
            method = ReflectionUtils.findMethod(domainType, toIsMethodName(propertyName));
        }
        return method;

    }

    private Field findField(String propertyName) {
        return ReflectionUtils.findField(domainType, propertyName);
    }

    public String getOverriddenAttributeName(Method method) {

        if (method != null) {
            // In SDK v2, @DynamoDbAttribute is used to override attribute names
            if (method.getAnnotation(DynamoDbAttribute.class) != null
                    && StringUtils.hasText(method.getAnnotation(DynamoDbAttribute.class).value())) {
                return method.getAnnotation(DynamoDbAttribute.class).value();
            }
            // Note: SDK v2 key annotations don't support attribute name overrides like SDK v1
            // Attribute names are derived from property names
        }
        return null;

    }

    @Override
    public Optional<String> getOverriddenAttributeName(final String propertyName) {

        Method method = findMethod(propertyName);
        if (method != null) {
            // In SDK v2, @DynamoDbAttribute is used to override attribute names
            if (method.getAnnotation(DynamoDbAttribute.class) != null
                    && StringUtils.hasText(method.getAnnotation(DynamoDbAttribute.class).value())) {
                return Optional.of(method.getAnnotation(DynamoDbAttribute.class).value());
            }
            // Note: SDK v2 key annotations don't support attribute name overrides like SDK v1
        }

        Field field = findField(propertyName);
        if (field != null) {
            // In SDK v2, @DynamoDbAttribute is used to override attribute names
            if (field.getAnnotation(DynamoDbAttribute.class) != null
                    && StringUtils.hasText(field.getAnnotation(DynamoDbAttribute.class).value())) {
                return Optional.of(field.getAnnotation(DynamoDbAttribute.class).value());
            }
            // Note: SDK v2 key annotations don't support attribute name overrides like SDK v1
        }
        return Optional.empty();

    }

    @Override
    public AttributeConverter<?> getAttributeConverterForProperty(final String propertyName) {
        // SDK v2 uses @DynamoDbConvertedBy annotation for custom converters
        DynamoDbConvertedBy annotation = null;

        Method method = findMethod(propertyName);
        if (method != null) {
            annotation = method.getAnnotation(DynamoDbConvertedBy.class);
        }

        if (annotation == null) {
            Field field = findField(propertyName);
            if (field != null) {
                annotation = field.getAnnotation(DynamoDbConvertedBy.class);
            }
        }

        if (annotation != null) {
            try {
                return annotation.value().getDeclaredConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                    | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                throw new RuntimeException(e);
            }
        }

        // No custom converter annotation found, check if AWS SDK v2 has a default converter for this type
        // This allows enums and other types to work natively without requiring custom converters
        Class<?> propertyType = null;
        if (method != null) {
            propertyType = method.getReturnType();
        } else {
            Field field = findField(propertyName);
            if (field != null) {
                propertyType = field.getType();
            }
        }

        if (propertyType != null) {
            // AWS SDK v2's DefaultAttributeConverterProvider cannot create converters for raw collection types
            // (Set, List, Map) without generic type parameters. When we get the type via field.getType() or
            // method.getReturnType(), we lose the generic type information (e.g., Set<String> becomes Set).
            // DynamoDB natively supports these collection types, so no custom converter is needed.
            // Return null to indicate no converter is available - the value will be handled by DynamoDB's
            // native type support.
            if (java.util.Collection.class.isAssignableFrom(propertyType) ||
                java.util.Map.class.isAssignableFrom(propertyType)) {
                return null;
            }

            software.amazon.awssdk.enhanced.dynamodb.DefaultAttributeConverterProvider defaultProvider =
                software.amazon.awssdk.enhanced.dynamodb.DefaultAttributeConverterProvider.create();
            return defaultProvider.converterFor(software.amazon.awssdk.enhanced.dynamodb.EnhancedType.of(propertyType));
        }

        return null;
    }

    protected String getPropertyNameForAccessorMethod(Method method) {
        String methodName = method.getName();
        String propertyName = null;
        if (methodName.startsWith("get")) {
            propertyName = methodName.substring(3);
        } else if (methodName.startsWith("is")) {
            propertyName = methodName.substring(2);
        }
        Assert.notNull(propertyName, "Hash or range key annotated accessor methods must start with 'get' or 'is'");

        String firstLetter = propertyName.substring(0, 1);
        String remainder = propertyName.substring(1);
        return firstLetter.toLowerCase() + remainder;
    }

    protected String getPropertyNameForField(Field field) {
        return field.getName();
    }

    @Override
    public String getHashKeyPropertyName() {
        return hashKeyPropertyName;
    }

    private void addGlobalSecondaryIndexNames(Method method, DynamoDbSecondarySortKey dynamoDBSecondarySortKey) {

        // SDK v2 uses indexNames() which returns array of index names for both GSI and LSI
        if (dynamoDBSecondarySortKey.indexNames() != null
                && dynamoDBSecondarySortKey.indexNames().length > 0) {
            String propertyName = getPropertyNameForAccessorMethod(method);

            globalSecondaryIndexNames.put(propertyName, dynamoDBSecondarySortKey.indexNames());
            globalIndexRangeKeyPropertyNames.add(propertyName);
        }

    }

    private void addGlobalSecondaryIndexNames(Field field, DynamoDbSecondarySortKey dynamoDBSecondarySortKey) {

        // SDK v2 uses indexNames() which returns array of index names for both GSI and LSI
        if (dynamoDBSecondarySortKey.indexNames() != null
                && dynamoDBSecondarySortKey.indexNames().length > 0) {
            String propertyName = getPropertyNameForField(field);

            globalSecondaryIndexNames.put(propertyName, dynamoDBSecondarySortKey.indexNames());
            globalIndexRangeKeyPropertyNames.add(propertyName);
        }

    }

    private void addGlobalSecondaryIndexNames(Method method, DynamoDbSecondaryPartitionKey dynamoDBSecondaryPartitionKey) {

        // SDK v2 uses indexNames() which returns array of index names for GSI
        if (dynamoDBSecondaryPartitionKey.indexNames() != null
                && dynamoDBSecondaryPartitionKey.indexNames().length > 0) {
            String propertyName = getPropertyNameForAccessorMethod(method);

            globalSecondaryIndexNames.put(propertyName, dynamoDBSecondaryPartitionKey.indexNames());
            globalIndexHashKeyPropertyNames.add(propertyName);
        }
    }

    private void addGlobalSecondaryIndexNames(Field field, DynamoDbSecondaryPartitionKey dynamoDBSecondaryPartitionKey) {

        // SDK v2 uses indexNames() which returns array of index names for GSI
        if (dynamoDBSecondaryPartitionKey.indexNames() != null
                && dynamoDBSecondaryPartitionKey.indexNames().length > 0) {
            String propertyName = getPropertyNameForField(field);

            globalSecondaryIndexNames.put(propertyName, dynamoDBSecondaryPartitionKey.indexNames());
            globalIndexHashKeyPropertyNames.add(propertyName);
        }
    }

    @Override
    public Map<String, String[]> getGlobalSecondaryIndexNamesByPropertyName() {
        return globalSecondaryIndexNames;
    }

    @Override
    public boolean isGlobalIndexHashKeyProperty(String propertyName) {
        return globalIndexHashKeyPropertyNames.contains(propertyName);
    }

    @Override
    public boolean isGlobalIndexRangeKeyProperty(String propertyName) {
        return globalIndexRangeKeyPropertyNames.contains(propertyName);
    }

}
