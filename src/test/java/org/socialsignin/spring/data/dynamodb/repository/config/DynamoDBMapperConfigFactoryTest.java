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
package org.socialsignin.spring.data.dynamodb.repository.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.socialsignin.spring.data.dynamodb.core.TableNameResolver;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SDK v2 Migration Notes:
 * - SDK v1: DynamoDBMapperConfigFactory was a BeanPostProcessor for DynamoDBMapperConfig
 * - SDK v2: DynamoDBMapperConfigFactory is a FactoryBean<TableNameResolver>
 * - SDK v1: Complex configuration with TableNameOverride, ConversionSchema, TypeConverterFactory
 * - SDK v2: Simplified to just TableNameResolver - other concerns handled by DynamoDbEnhancedClient
 * - The factory now returns a default TableNameResolver that passes through the base table name unchanged
 */
@ExtendWith(MockitoExtension.class)
public class DynamoDBMapperConfigFactoryTest {

    @SuppressWarnings("deprecation")
    DynamoDBMapperConfigFactory underTest;

    @BeforeEach
    public void setUp() throws Exception {
        underTest = new DynamoDBMapperConfigFactory();
    }

    @Test
    public void testGetObject_ReturnsTableNameResolver() throws Exception {
        TableNameResolver resolver = underTest.getObject();

        assertNotNull(resolver);
    }

    @Test
    public void testGetObjectType_ReturnsTableNameResolverClass() {
        Class<?> objectType = underTest.getObjectType();

        assertEquals(TableNameResolver.class, objectType);
    }

    @Test
    public void testIsSingleton_ReturnsTrue() {
        boolean isSingleton = underTest.isSingleton();

        assertTrue(isSingleton);
    }

    @Test
    public void testDefaultResolver_ReturnsBaseTableNameUnchanged() throws Exception {
        TableNameResolver resolver = underTest.getObject();

        String tableName = resolver.resolveTableName(Object.class, "MyTable");

        assertEquals("MyTable", tableName);
    }

}
