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
package org.socialsignin.spring.data.dynamodb.mapping;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.socialsignin.spring.data.dynamodb.repository.DynamoDBHashAndRangeKey;
import org.springframework.data.annotation.Id;
import org.springframework.data.mapping.model.Property;
import org.springframework.data.mapping.model.SimpleTypeHolder;
import org.springframework.data.util.TypeInformation;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SDK v2 Migration Notes:
 * - SDK v1: @DynamoDBHashKey → SDK v2: @DynamoDbPartitionKey
 * - The DynamoDBPersistentEntity implementation remains compatible with both annotations
 * - Test validates that the persistent entity can identify ID properties correctly
 */
@ExtendWith(MockitoExtension.class)
public class DynamoDBPersistentEntityTest {

    static class DynamoDBPersistentEntity {
        private String id;

        @Id
        private DynamoDBHashAndRangeKey hashRangeKey;

        @SuppressWarnings("unused")
        private String name;

        @DynamoDbPartitionKey
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }
    }

    @Mock
    private Comparator<DynamoDBPersistentProperty> comparator;

    private TypeInformation<DynamoDBPersistentEntity> cti = TypeInformation
            .of(DynamoDBPersistentEntity.class);
    private DynamoDBPersistentEntityImpl<DynamoDBPersistentEntity> underTest;

    @BeforeEach
    public void setUp() {
        underTest = new DynamoDBPersistentEntityImpl<>(cti, comparator);
    }

    @Test
    public void testSomeProperty() throws NoSuchFieldException {
        Property prop = Property.of(cti, DynamoDBPersistentEntity.class.getDeclaredField("name"));

        DynamoDBPersistentProperty property = new DynamoDBPersistentPropertyImpl(prop, underTest,
                SimpleTypeHolder.DEFAULT);
        DynamoDBPersistentProperty actual = underTest.returnPropertyIfBetterIdPropertyCandidateOrNull(property);

        assertNull(actual);
    }

    @Test
    public void testIdProperty() throws Exception {
        // notes:
        // The original test retrieved the "id" property using the field directly via getDeclaredField("id").
        // After the entity was updated to place the @DynamoDbPartitionKey annotation on the getter method
        // instead of on the field, the old approach no longer allowed DynamoDBPersistentPropertyImpl to detect
        // the annotation, since field lookup does not expose method-level metadata.
        //
        // To preserve the test’s original intent — verifying that the "id" property is correctly identified
        // as the hash key — we now construct the Property using a PropertyDescriptor. This allows the
        // underlying metadata system to inspect the getter (where the annotation now resides),
        // without modifying production code or changing the behavior that the test validates.

        PropertyDescriptor pd =
                new PropertyDescriptor("id", DynamoDBPersistentEntity.class, "getId", "setId");

        Property prop = Property.of(cti, pd);

        DynamoDBPersistentProperty property =
                new DynamoDBPersistentPropertyImpl(prop, underTest, SimpleTypeHolder.DEFAULT);

        DynamoDBPersistentProperty actual =
                underTest.returnPropertyIfBetterIdPropertyCandidateOrNull(property);

        assertNotNull(actual);
        assertTrue(actual.isHashKeyProperty());
    }

    @Test
    public void testCompositeIdProperty() throws NoSuchFieldException {
        Property prop = Property.of(cti, DynamoDBPersistentEntity.class.getDeclaredField("hashRangeKey"));
        DynamoDBPersistentProperty property = new DynamoDBPersistentPropertyImpl(prop, underTest,
                SimpleTypeHolder.DEFAULT);
        DynamoDBPersistentProperty actual = underTest.returnPropertyIfBetterIdPropertyCandidateOrNull(property);

        assertNotNull(actual);
        assertTrue(actual.isCompositeIdProperty());
    }
}
