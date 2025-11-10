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
package org.socialsignin.spring.data.dynamodb.marshaller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Date2EpocheDynamoDBMarshallerTest {

    private Date2EpocheDynamoDBMarshaller underTest;

    @BeforeEach
    public void setUp() {
        underTest = new Date2EpocheDynamoDBMarshaller();
    }

    @Test
    public void testNullMarshall() {
        String actual = underTest.marshall(null);

        assertNull(actual);
    }

    @Test
    public void testMarshall() {
        assertEquals("0", underTest.marshall(new Date(0)));
        assertEquals("0", underTest.convert(new Date(0)));
    }

    @Test
    public void testUnmarshallNull() {
        Date actual = underTest.unmarshall(Date.class, null);

        assertNull(actual);
    }

    @Test
    public void testUnmarshall() {
        assertEquals(new Date(0), underTest.unmarshall(Date.class, "0"));
        assertEquals(new Date(0), underTest.unconvert("0"));
        ;

    }

    @Test
    public void testUnmarshallGarbage() {
        assertThrows(NumberFormatException.class, () -> {
            underTest.unmarshall(Date.class, "something");
        });
    }
}
