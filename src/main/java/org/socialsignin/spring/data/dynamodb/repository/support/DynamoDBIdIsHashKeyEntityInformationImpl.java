/**
 * Copyright © 2018 spring-data-dynamodb (https://github.com/prasanna0586/spring-data-dynamodb)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p>
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
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.util.Map;
import java.util.Optional;

/**
 * Encapsulates minimal information needed to load DynamoDB entities. This default implementation is NOT range-key aware
 * - getRangeKey(ID id) will always return null.
 * <p>
 * Delegates to wrapped DynamoDBHashKeyExtractingEntityMetadata component for many operations - it is the responsibility
 * of calling clients to ensure they pass in a valid DynamoDBHashKeyExtractingEntityMetadata implementation for this entity.
 * <p>
 * Entities of type T must have a public getter method of return type ID annotated with @DynamoDbPartitionKey to ensure
 * correct behavior.
 * @param <T> the entity type
 * @param <ID> the ID type
 * @author Prasanna Kumar Ramachandran
 */
public class DynamoDBIdIsHashKeyEntityInformationImpl<T, ID> extends FieldAndGetterReflectionEntityInformation<T, ID>
        implements DynamoDBEntityInformation<T, ID> {

    private final DynamoDBHashKeyExtractingEntityMetadata<T> metadata;
    @NonNull
    private final HashKeyExtractor<ID, ID> hashKeyExtractor;
    @Nullable
    private final String projection = null;
    @Nullable
    private final Integer limit = null;

    public DynamoDBIdIsHashKeyEntityInformationImpl(@NonNull Class<T> domainClass,
                                                    DynamoDBHashKeyExtractingEntityMetadata<T> metadata) {
        super(domainClass, DynamoDbPartitionKey.class);
        this.metadata = metadata;
        this.hashKeyExtractor = new HashKeyIsIdHashKeyExtractor<>(getIdType());
    }

    @NonNull
    @Override
    public Optional<String> getProjection() {
        return Optional.empty();
    }

    @NonNull
    @Override
    public Optional<Integer> getLimit() {
        return Optional.empty();
    }

    @Nullable
    @Override
    public Object getHashKey(@NonNull final ID id) {
        Assert.isAssignable(getIdType(), id.getClass(),
                "Expected ID type to be the same as the return type of the hash key method ( " + getIdType() + " ) : ");
        return hashKeyExtractor.getHashKey(id);
    }

    // The following methods simply delegate to metadata, or always return
    // constants

    @Override
    public Optional<String> getOverriddenAttributeName(String attributeName) {
        return metadata.getOverriddenAttributeName(attributeName);
    }

    @Override
    public boolean isHashKeyProperty(String propertyName) {
        return metadata.isHashKeyProperty(propertyName);
    }

    @Override
    public boolean isCompositeHashAndRangeKeyProperty(String propertyName) {
        return false;
    }

    @Override
    public AttributeConverter<?> getAttributeConverterForProperty(String propertyName) {
        return metadata.getAttributeConverterForProperty(propertyName);
    }

    @Override
    public String getDynamoDBTableName() {
        return metadata.getDynamoDBTableName();
    }

    @Override
    public String getHashKeyPropertyName() {
        return metadata.getHashKeyPropertyName();
    }

    @Override
    public Map<String, String[]> getGlobalSecondaryIndexNamesByPropertyName() {
        return metadata.getGlobalSecondaryIndexNamesByPropertyName();
    }

    @Override
    public boolean isGlobalIndexHashKeyProperty(String propertyName) {
        return metadata.isGlobalIndexHashKeyProperty(propertyName);
    }

    @Override
    public boolean isGlobalIndexRangeKeyProperty(String propertyName) {
        return metadata.isGlobalIndexRangeKeyProperty(propertyName);
    }

}
