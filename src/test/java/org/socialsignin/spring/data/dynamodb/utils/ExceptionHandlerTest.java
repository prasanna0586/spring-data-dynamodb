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
package org.socialsignin.spring.data.dynamodb.utils;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.socialsignin.spring.data.dynamodb.exception.BatchWriteException;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SDK v2 ExceptionHandler functionality.
 *
 * SDK v2 Migration Notes:
 * - SDK v1: Used DynamoDBMapper.FailedBatch with Exception field
 * - SDK v2: Uses BatchWriteResult with unprocessed items (no exceptions in result)
 * - SDK v2: Exceptions are passed separately to repackageToException()
 */
public class ExceptionHandlerTest {

    private final ExceptionHandler underTest = new ExceptionHandler() {
    };

    @Test
    public void testEmpty() {
        // SDK v2: Test with empty unprocessed entities list
        BatchWriteException exception = underTest.repackageToException(
                Collections.emptyList(),  // No unprocessed entities
                0,                         // No retries attempted
                null,                      // No exception thrown
                BatchWriteException.class);

        assertNotNull(exception);
        assertTrue(exception.getMessage().contains("0 unprocessed entities"));
        assertEquals(0, exception.getUnprocessedEntities().size());
    }

    @Test
    public void testWithUnprocessedEntities() {
        // SDK v2: Test with unprocessed entities after retries
        List<Object> unprocessedEntities = new ArrayList<>();
        unprocessedEntities.add("entity1");
        unprocessedEntities.add("entity2");

        BatchWriteException actual = underTest.repackageToException(
                unprocessedEntities,
                3,                         // 3 retries attempted
                null,                      // No exception, just unprocessed items
                BatchWriteException.class);

        assertNotNull(actual);
        assertTrue(actual.getMessage().contains("2 unprocessed entities"));
        assertTrue(actual.getMessage().contains("3 retry attempts"));
        assertTrue(actual.getMessage().contains("persistent throttling"));
        assertEquals(2, actual.getUnprocessedEntities().size());
        assertEquals(3, actual.getRetriesAttempted());
        assertNull(actual.getCause()); // No exception was thrown
    }

    @Test
    public void testWithException() {
        // SDK v2: Test when an actual exception was thrown during batch operation
        List<Object> unprocessedEntities = new ArrayList<>();
        unprocessedEntities.add("entity1");

        Exception originalException = new RuntimeException("DynamoDB service error");

        BatchWriteException actual = underTest.repackageToException(
                unprocessedEntities,
                2,                         // 2 retries attempted
                originalException,         // Exception that was thrown
                BatchWriteException.class);

        assertNotNull(actual);
        assertTrue(actual.getMessage().contains("failed with exception"));
        assertTrue(actual.getMessage().contains("RuntimeException"));
        assertTrue(actual.getMessage().contains("1 entities could not be processed"));

        assertNotNull(actual.getCause());
        assertEquals("DynamoDB service error", actual.getCause().getMessage());
        assertEquals(1, actual.getUnprocessedEntities().size());
        assertEquals(2, actual.getRetriesAttempted());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testLegacyMethodSignature() {
        // Test deprecated legacy method signature (for backward compatibility during migration)
        List<BatchWriteResult> failedBatches = new ArrayList<>();
        BatchWriteResult mockResult = Mockito.mock(BatchWriteResult.class);
        failedBatches.add(mockResult);

        BatchWriteException actual = underTest.repackageToException(
                failedBatches,
                BatchWriteException.class);

        assertNotNull(actual);
        assertTrue(actual.getMessage().contains("1 batch"));
        assertTrue(actual.getMessage().contains("Unable to extract specific entities"));
    }
}
