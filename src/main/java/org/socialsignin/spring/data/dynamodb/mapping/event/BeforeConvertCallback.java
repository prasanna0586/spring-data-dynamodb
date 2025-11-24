/**
 * Copyright © 2018 spring-data-dynamodb (https://github.com/prasanna0586/spring-data-dynamodb)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.socialsignin.spring.data.dynamodb.mapping.event;

import org.springframework.data.mapping.callback.EntityCallback;

/**
 * Entity callback triggered before an entity is converted to be persisted in DynamoDB.
 * Allows modification of the entity before conversion.
 * @param <T> the entity type
 * @author Prasanna Kumar Ramachandran
 */
@FunctionalInterface
public interface BeforeConvertCallback<T> extends EntityCallback<T> {

    /**
     * Entity callback method invoked before a domain object is converted to be persisted.
     * Can return either the same or a modified instance of the domain object.
     * @param entity the domain object to save
     * @param tableName the name of the table the entity will be persisted to
     * @return the domain object to be persisted (can be modified)
     */
    T onBeforeConvert(T entity, String tableName);
}
