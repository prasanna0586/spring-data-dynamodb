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
package org.socialsignin.spring.data.dynamodb.query;

import org.springframework.lang.NonNull;

import java.util.Collections;
import java.util.List;

/**
 * A static query implementation that returns a pre-computed result.
 * @param <T> the entity type returned by the query
 * @author Prasanna Kumar Ramachandran
 */
public class StaticQuery<T> extends AbstractQuery<T> {

    private final T result;
    @NonNull
    private final List<T> resultList;

    /**
     * Creates a new static query with the given result.
     * @param result the pre-computed result to return
     */
    public StaticQuery(T result) {
        this.result = result;
        this.resultList = Collections.singletonList(result);
    }

    @NonNull
    @Override
    public List<T> getResultList() {
        return resultList;
    }

    @Override
    public T getSingleResult() {
        return result;
    }
}
