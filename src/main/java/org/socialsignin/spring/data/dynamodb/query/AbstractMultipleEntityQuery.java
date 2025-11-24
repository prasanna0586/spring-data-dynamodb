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

import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * @author Prasanna Kumar Ramachandran
 */
public abstract class AbstractMultipleEntityQuery<T> extends AbstractDynamicQuery<T> implements Query<T> {

    public AbstractMultipleEntityQuery(DynamoDBOperations dynamoDBOperations, Class<T> clazz) {
        super(dynamoDBOperations, clazz);
    }

    @Nullable
    @Override
    public T getSingleResult() {
        List<T> results = getResultList();
        if (results != null && results.size() > 1) {
            throw new IncorrectResultSizeDataAccessException("result returns more than one elements", 1,
                    results.size());
        }
        if (results == null || results.isEmpty()) {
            // return null here as Spring will convert that to Optional if nessassary
            // https://jira.spring.io/browse/DATACMNS-483
            return null;
        } else {
            return results.getFirst();
        }
    }
}
