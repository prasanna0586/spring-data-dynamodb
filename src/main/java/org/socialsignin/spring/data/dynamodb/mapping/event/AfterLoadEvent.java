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

import org.springframework.lang.NonNull;

import java.io.Serial;

/**
 * Event published after an entity is loaded from DynamoDB.
 * @param <T> the entity type
 * @author Prasanna Kumar Ramachandran
 */
public class AfterLoadEvent<T> extends DynamoDBMappingEvent<T> {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs an AfterLoadEvent.
     * @param source the entity that was loaded
     */
    public AfterLoadEvent(@NonNull T source) {
        super(source);
    }

}
