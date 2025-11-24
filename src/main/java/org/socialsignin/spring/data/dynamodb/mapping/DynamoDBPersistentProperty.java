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
package org.socialsignin.spring.data.dynamodb.mapping;

import org.springframework.data.mapping.PersistentProperty;

/**
 * Interface for a DynamoDB-specific {@link PersistentProperty}.
 * @author Prasanna Kumar Ramachandran
 */
public interface DynamoDBPersistentProperty extends PersistentProperty<DynamoDBPersistentProperty> {

    /**
     * Checks if this property is the hash key property.
     *
     * @return true if this is the hash key property, false otherwise
     */
    boolean isHashKeyProperty();

    /**
     * Checks if this property is a composite ID property.
     *
     * @return true if this is a composite ID property, false otherwise
     */
    boolean isCompositeIdProperty();

}
