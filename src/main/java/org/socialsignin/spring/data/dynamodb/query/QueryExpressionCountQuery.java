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
import org.springframework.lang.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

/**
 * @author Prasanna Kumar Ramachandran
 */
public class QueryExpressionCountQuery<T> extends AbstractSingleEntityQuery<Long> {

    private final QueryEnhancedRequest queryRequest;
    private final Class<T> domainClass;

    public QueryExpressionCountQuery(DynamoDBOperations dynamoDBOperations, Class<T> clazz,
            QueryEnhancedRequest queryRequest) {
        super(dynamoDBOperations, Long.class);
        this.queryRequest = queryRequest;
        this.domainClass = clazz;
    }

    @NonNull
    @Override
    public Long getSingleResult() {
        return (long) dynamoDBOperations.count(domainClass, queryRequest);
    }

}
