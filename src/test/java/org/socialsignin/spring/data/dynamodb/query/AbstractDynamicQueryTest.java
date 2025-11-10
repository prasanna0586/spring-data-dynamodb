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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;
import org.socialsignin.spring.data.dynamodb.domain.sample.User;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class AbstractDynamicQueryTest {

    private static class QueryTest<T> extends AbstractDynamicQuery<T> {
        public QueryTest(DynamoDBOperations dynamoDBOperations, Class<T> clazz) {
            super(dynamoDBOperations, clazz);
        }

        @Override
        public List<T> getResultList() {
            return null;
        }

        @Override
        public T getSingleResult() {
            return null;
        }
    }

    @Mock
    private DynamoDBOperations dynamoDBOperations;
    private AbstractQuery<User> underTest;

    @BeforeEach
    public void setUp() {
        underTest = new QueryTest<>(dynamoDBOperations, User.class);
    }

    @Test
    public void testSetter() {
        assertFalse(underTest.isScanCountEnabled());
        assertFalse(underTest.isScanEnabled());

        underTest.setScanCountEnabled(true);
        underTest.setScanEnabled(true);

        assertTrue(underTest.isScanCountEnabled());
        assertTrue(underTest.isScanEnabled());
    }

}
