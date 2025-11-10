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
package org.socialsignin.spring.data.dynamodb.query;

import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class QueryRequestCountQueryTest {
    @Mock
    private DynamoDBOperations dynamoDBOperations;
    @Mock
    private QueryRequest queryRequest;

    private QueryRequestCountQuery underTest;

    @BeforeEach
    public void setUp() {
        underTest = new QueryRequestCountQuery(dynamoDBOperations, queryRequest);
    }

    @Test
    public void testGetSingleResult() {
        int expected = ThreadLocalRandom.current().nextInt();
        when(dynamoDBOperations.count(Long.class, queryRequest)).thenReturn(expected);

        Long actual = underTest.getSingleResult();

        assertEquals(Long.valueOf(expected), actual);
    }
}
