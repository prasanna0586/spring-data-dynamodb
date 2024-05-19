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

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import org.junit.Test;
import org.socialsignin.spring.data.dynamodb.exception.BatchWriteException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class ExceptionHandlerTest {

    private ExceptionHandler underTest = new ExceptionHandler() {
    };

    @Test
    public void testEmpty() {
        underTest.repackageToException(Collections.emptyList(), BatchWriteException.class);

        assertTrue(true);
    }

    @Test
    public void testSimple() {
        List<DynamoDBMapper.FailedBatch> failedBatches = new ArrayList<>();
        DynamoDBMapper.FailedBatch fb1 = new DynamoDBMapper.FailedBatch();
        fb1.setException(new Exception("Test Exception"));
        failedBatches.add(fb1);
        DynamoDBMapper.FailedBatch fb2 = new DynamoDBMapper.FailedBatch();
        fb2.setException(new Exception("Followup Exception"));
        failedBatches.add(fb2);

        BatchWriteException actual = underTest.repackageToException(failedBatches, BatchWriteException.class);

        assertEquals("Processing of entities failed!", actual.getMessage());

        assertNotNull(actual.getCause());
        assertEquals("Test Exception", actual.getCause().getMessage());
        assertNull(actual.getCause().getCause());

        assertEquals(1, actual.getSuppressed().length);
        assertEquals("Followup Exception", actual.getSuppressed()[0].getMessage());
    }
}
