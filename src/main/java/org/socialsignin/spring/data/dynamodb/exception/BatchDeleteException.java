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
package org.socialsignin.spring.data.dynamodb.exception;

import org.springframework.dao.DataAccessException;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Exception thrown when batch delete operations fail after exhausting retries.
 *
 * This exception provides access to:
 * - Unprocessed entities that could not be deleted from DynamoDB
 * - Number of retry attempts that were made
 * - Original exception if one was thrown (vs. items just being unprocessed)
 *
 * Following AWS SDK v2 best practices, unprocessed items are exposed to allow
 * consumers to implement custom recovery strategies (e.g., dead letter queues,
 * manual retry with different configuration, alerting, etc.)
 */
@SuppressWarnings("serial")
public class BatchDeleteException extends DataAccessException {

    private final List<Object> unprocessedEntities;
    private final int retriesAttempted;

    /**
     * Creates a BatchDeleteException with full context about the failure.
     *
     * @param msg Error message describing the failure
     * @param unprocessedEntities List of entity objects that could not be deleted
     * @param retriesAttempted Number of retry attempts that were made
     * @param cause Original exception if one was thrown, or null if items were just unprocessed
     */
    public BatchDeleteException(String msg, List<Object> unprocessedEntities, int retriesAttempted, Throwable cause) {
        super(msg, cause);
        this.unprocessedEntities = unprocessedEntities != null
            ? Collections.unmodifiableList(unprocessedEntities)
            : Collections.emptyList();
        this.retriesAttempted = retriesAttempted;
    }

    /**
     * Returns unprocessed entities filtered by the specified type.
     * This is a type-safe way to retrieve entities of a specific class.
     *
     * Example usage:
     * <pre>
     * try {
     *     repository.deleteAll(products);
     * } catch (BatchDeleteException e) {
     *     List&lt;Product&gt; failed = e.getUnprocessedEntities(Product.class);
     *     // Handle failed deletes (e.g., log, send to DLQ, alert)
     * }
     * </pre>
     *
     * @param entityClass The class of entities to retrieve
     * @param <T> The entity type
     * @return List of unprocessed entities of the specified type
     */
    public <T> List<T> getUnprocessedEntities(Class<T> entityClass) {
        return unprocessedEntities.stream()
                .filter(entityClass::isInstance)
                .map(entityClass::cast)
                .collect(Collectors.toList());
    }

    /**
     * Returns all unprocessed entities regardless of type.
     * Useful when batch operations involve multiple entity types.
     *
     * @return Unmodifiable list of all unprocessed entities
     */
    public List<Object> getUnprocessedEntities() {
        return unprocessedEntities;
    }

    /**
     * Returns the number of retry attempts that were made before giving up.
     *
     * @return Number of retries attempted
     */
    public int getRetriesAttempted() {
        return retriesAttempted;
    }

    /**
     * Checks if there was an actual exception thrown (vs. just unprocessed items).
     *
     * When true, getCause() will return a specific exception like:
     * - ProvisionedThroughputExceededException (throttling)
     * - ValidationException (invalid data)
     * - ResourceNotFoundException (table doesn't exist)
     *
     * When false, the failure was due to items remaining unprocessed after retries,
     * typically caused by persistent throttling or capacity issues.
     *
     * @return true if a specific exception was thrown, false if failure was due to unprocessed items
     */
    public boolean hasOriginalException() {
        return getCause() != null;
    }

    /**
     * Returns the number of entities that could not be processed.
     *
     * @return Count of unprocessed entities
     */
    public int getUnprocessedCount() {
        return unprocessedEntities.size();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());
        sb.append("; unprocessedCount=").append(unprocessedEntities.size());
        sb.append("; retriesAttempted=").append(retriesAttempted);
        if (hasOriginalException()) {
            sb.append("; originalException=").append(getCause().getClass().getSimpleName());
        }
        return sb.toString();
    }
}
