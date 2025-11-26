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
package org.socialsignin.spring.data.dynamodb.utils;

import org.socialsignin.spring.data.dynamodb.exception.BatchDeleteException;
import org.socialsignin.spring.data.dynamodb.exception.BatchWriteException;
import org.springframework.dao.DataAccessException;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteResult;

import java.util.List;

/**
 * Interface for handling exceptions in batch operations (write and delete).
 *
 * This interface provides methods to convert SDK v2 batch operation failures
 * into rich exception objects that expose unprocessed entities for consumer handling.
 */
public interface ExceptionHandler {

    /**
     * Repackages batch operation failures into a rich exception with unprocessed entity information.
     * <p>
     * This method is called after retry logic has been exhausted. It creates an exception that:
     * - Exposes unprocessed entities for custom recovery logic
     * - Includes retry attempt count
     * - Preserves any thrown exceptions
     * <p>
     * Following AWS SDK v2 best practices, unprocessed items are returned to the caller
     * for custom handling (e.g., dead letter queue, custom retry, alerting).
     * <p>
     * Supports both batch write and batch delete operations.
     * @param unprocessedEntities List of entity objects that could not be written/deleted after retries
     * @param retriesAttempted Number of retry attempts that were made
     * @param cause Original exception if one was thrown, or null if items were just unprocessed
     * @param targetType The exception class to instantiate (BatchWriteException or BatchDeleteException)
     * @param <T> Exception type extending DataAccessException
     * @return Exception instance with full failure context
     */
    @NonNull
    default <T extends DataAccessException> T repackageToException(
            @NonNull List<Object> unprocessedEntities,
            int retriesAttempted,
            @Nullable Throwable cause,
            @NonNull Class<T> targetType) {

        // SDK v2: After retry exhaustion, expose unprocessed items for consumer handling
        // Unprocessed items typically indicate persistent throttling, capacity limits, or transaction conflicts

        // Determine operation type based on exception class
        String operationType = targetType == BatchDeleteException.class ? "delete" : "write";

        String message;
        if (cause != null) {
            // An actual exception was thrown
            message = String.format(
                    "Batch %s operation failed with exception: %s. %d entities could not be processed.",
                    operationType,
                    cause.getClass().getSimpleName(),
                    unprocessedEntities.size());
        } else {
            // Items remained unprocessed after retries
            message = String.format(
                    "Batch %s operation failed with %d unprocessed entities after %d retry attempts. " +
                    "Items were not processed despite exponential backoff, likely due to persistent throttling, " +
                    "insufficient provisioned capacity, or transaction conflicts. " +
                    "Consider increasing table provisioned throughput or adjusting retry configuration.",
                    operationType,
                    unprocessedEntities.size(),
                    retriesAttempted);
        }

        if (targetType == BatchWriteException.class) {
            @SuppressWarnings("unchecked")
            T exception = (T) new BatchWriteException(message, unprocessedEntities, retriesAttempted, cause);
            return exception;
        } else if (targetType == BatchDeleteException.class) {
            @SuppressWarnings("unchecked")
            T exception = (T) new BatchDeleteException(message, unprocessedEntities, retriesAttempted, cause);
            return exception;
        } else {
            throw new IllegalArgumentException(
                    "targetType must be BatchWriteException or BatchDeleteException for SDK v2. Received: " + targetType.getName());
        }
    }

    /**
     * Legacy method signature maintained for internal compatibility during migration.
     * This will be removed once DynamoDBTemplate is fully migrated to SDK v2.
     * <p>
     * Converts failed batch results into a rich exception with unprocessed entity information.
     * @param <T> Exception type extending DataAccessException
     * @param failedBatches List of batch results containing unprocessed items
     * @param targetType The exception class to instantiate (BatchWriteException or BatchDeleteException)
     * @return Exception instance with failure context
     * @deprecated Use {@link #repackageToException(List, int, Throwable, Class)} instead
     */
    @NonNull
    @Deprecated
    default <T extends DataAccessException> T repackageToException(
            @NonNull List<BatchWriteResult> failedBatches,
            @NonNull Class<T> targetType) {

        // Temporary implementation until DynamoDBTemplate is migrated
        // Cannot extract actual unprocessed entities without table references
        String message = String.format(
                "Batch write operation failed with %d batch(es) containing unprocessed items. " +
                "Unable to extract specific entities (requires DynamoDBTemplate migration to SDK v2).",
                failedBatches.size());

        if (targetType == BatchWriteException.class) {
            @SuppressWarnings("unchecked")
            T exception = (T) new BatchWriteException(
                    message,
                    List.of(), // Empty - can't extract without table references
                    0,
                    new RuntimeException(message));
            return exception;
        } else {
            throw new IllegalArgumentException(
                    "targetType must be BatchWriteException for SDK v2. Received: " + targetType.getName());
        }
    }
}
