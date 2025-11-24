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
package org.socialsignin.spring.data.dynamodb.query;

import org.springframework.lang.Nullable;

import java.util.List;

public interface Query<T> {

    /**
     * Execute a SELECT query and return the query results as a List.
     * @return a list of the results
     * @throws IllegalStateException
     *             if called for a Java Persistence query language UPDATE or DELETE statement
     */
    @Nullable
    List<T> getResultList();

    /**
     * Execute a SELECT query that returns a single result.
     * @return the result
     */
    @Nullable
    T getSingleResult();

    /**
     * Enables or disables scan operations for this query.
     * @param scanEnabled true to enable scan operations, false otherwise
     */
    void setScanEnabled(boolean scanEnabled);

    /**
     * Enables or disables counting during scan operations for this query.
     * @param scanCountEnabled true to enable scan count operations, false otherwise
     */
    void setScanCountEnabled(boolean scanCountEnabled);

    /**
     * Checks if scan count operations are enabled for this query.
     * @return true if scan count operations are enabled, false otherwise
     */
    boolean isScanCountEnabled();

    /**
     * Checks if scan operations are enabled for this query.
     * @return true if scan operations are enabled, false otherwise
     */
    boolean isScanEnabled();

}
