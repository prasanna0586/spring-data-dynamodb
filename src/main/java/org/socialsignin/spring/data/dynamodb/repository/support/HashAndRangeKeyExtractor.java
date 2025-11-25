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

import org.springframework.lang.Nullable;

/**
 * Extractor for hash and range keys from an ID object.
 * @param <ID> the ID type
 * @param <H> the hash key type
 * @author Prasanna Kumar Ramachandran
 */
public interface HashAndRangeKeyExtractor<ID, H> extends HashKeyExtractor<ID, H> {

    /**
     * Extracts the range key from the given ID object.
     * @param id the ID object to extract the range key from
     * @return the range key, or null if not found
     */
    @Nullable
    Object getRangeKey(ID id);

}
