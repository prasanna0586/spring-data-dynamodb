/*
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
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes a DynamoDB query request that returns multiple entities.
 * @param <T> the entity type
 */
public class MultipleEntityQueryRequestQuery<T> extends AbstractMultipleEntityQuery<T> {

    private final DynamoDBOperations dynamoDBOperations;
    private final QueryRequest queryRequest;

    /**
     * Creates a new query for executing a DynamoDB query request.
     * @param dynamoDBOperations the DynamoDB operations instance
     * @param clazz the entity class
     * @param queryRequest the query request to execute
     */
    public MultipleEntityQueryRequestQuery(DynamoDBOperations dynamoDBOperations, Class<T> clazz,
            QueryRequest queryRequest) {
        super(null, clazz);
        this.queryRequest = queryRequest;
        this.dynamoDBOperations = dynamoDBOperations;
    }

    @NonNull
    @Override
    public List<T> getResultList() {
        // SDK v2: query() returns PageIterable<T>, convert to List<T>
        PageIterable<T> pageIterable = dynamoDBOperations.query(clazz, queryRequest);
        List<T> results = new ArrayList<>();

        // If a limit is specified in the query request, we need to respect it when collecting results.
        // DynamoDB's limit parameter specifies the max number of items to EXAMINE (before filtering),
        // not the number to RETURN (after filtering). When a filterExpression is present, multiple
        // pages may be returned, each with items that passed the filter. We need to stop collecting
        // once we reach the user-specified limit.
        Integer userLimit = queryRequest.limit();

        for (Page<T> page : pageIterable) {
            if (userLimit != null && results.size() >= userLimit) {
                break; // Stop collecting once we've reached the limit
            }

            if (userLimit != null) {
                // Add only as many items as needed to reach the limit
                int remainingSlots = userLimit - results.size();
                List<T> pageItems = page.items();
                if (pageItems.size() <= remainingSlots) {
                    results.addAll(pageItems);
                } else {
                    results.addAll(pageItems.subList(0, remainingSlots));
                    break;
                }
            } else {
                // No limit specified, add all items
                results.addAll(page.items());
            }
        }
        return results;
    }

}
