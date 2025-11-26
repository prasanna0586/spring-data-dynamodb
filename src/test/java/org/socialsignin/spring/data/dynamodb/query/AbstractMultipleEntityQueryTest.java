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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;
import org.socialsignin.spring.data.dynamodb.domain.sample.User;
import org.springframework.dao.IncorrectResultSizeDataAccessException;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class AbstractMultipleEntityQueryTest {

    private static class TestAbstractMultipleEntityQuery extends AbstractMultipleEntityQuery<User> {
        private final List<User> resultList;

        public TestAbstractMultipleEntityQuery(DynamoDBOperations dynamoDBOperations, User... resultEntities) {
            super(dynamoDBOperations, User.class);
            resultList = Arrays.asList(resultEntities);
        }

        @Override
        public List<User> getResultList() {
            return resultList;
        }
    }

    @Mock
    private DynamoDBOperations dynamoDBOperations;
    @Mock
    private User entity;

    private AbstractMultipleEntityQuery<User> underTest;

    @Test
    public void testNullResult() {
        underTest = new TestAbstractMultipleEntityQuery(dynamoDBOperations);

        assertNull(underTest.getSingleResult());
    }

    @Test
    public void testSingleResult() {
        underTest = new TestAbstractMultipleEntityQuery(dynamoDBOperations, entity);

        assertSame(entity, underTest.getSingleResult());
    }

    @Test
    public void testMultiResult() {
        underTest = new TestAbstractMultipleEntityQuery(dynamoDBOperations, entity, entity);

        IncorrectResultSizeDataAccessException exception = assertThrows(IncorrectResultSizeDataAccessException.class, () -> {
            underTest.getSingleResult();
        });

        assertTrue(exception.getMessage().contains("result returns more than one elements"));
    }
}
