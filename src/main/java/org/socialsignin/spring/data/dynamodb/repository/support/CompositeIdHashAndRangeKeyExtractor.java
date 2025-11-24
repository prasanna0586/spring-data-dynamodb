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
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;

/**
 * Implementation of hash and range key extractor for composite ID classes using reflection.
 * @param <ID> the composite ID type
 * @param <H> the hash key type
 * @author Prasanna Kumar Ramachandran
 */
public class CompositeIdHashAndRangeKeyExtractor<ID, H> implements HashAndRangeKeyExtractor<ID, H> {

    @NonNull
    private final DynamoDBHashAndRangeKeyMethodExtractor<ID> hashAndRangeKeyMethodExtractor;

    /**
     * Constructs a new CompositeIdHashAndRangeKeyExtractor for the given ID class.
     * @param idClass the composite ID class to extract hash and range keys from
     */
    public CompositeIdHashAndRangeKeyExtractor(@NonNull Class<ID> idClass) {
        this.hashAndRangeKeyMethodExtractor = new DynamoDBHashAndRangeKeyMethodExtractorImpl<>(idClass);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    @Override
    public H getHashKey(ID id) {
        Method method = hashAndRangeKeyMethodExtractor.getHashKeyMethod();
        if (method != null) {
            return (H) ReflectionUtils.invokeMethod(method, id);
        } else {
            return (H) ReflectionUtils.getField(hashAndRangeKeyMethodExtractor.getHashKeyField(), id);
        }
    }

    @Nullable
    @Override
    public Object getRangeKey(ID id) {
        Method method = hashAndRangeKeyMethodExtractor.getRangeKeyMethod();
        if (method != null) {
            return ReflectionUtils.invokeMethod(method, id);
        } else {
            return ReflectionUtils.getField(hashAndRangeKeyMethodExtractor.getRangeKeyField(), id);
        }
    }

}
