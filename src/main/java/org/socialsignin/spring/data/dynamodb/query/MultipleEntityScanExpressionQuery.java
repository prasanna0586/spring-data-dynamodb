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
import org.springframework.util.Assert;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class MultipleEntityScanExpressionQuery<T> extends AbstractMultipleEntityQuery<T> {

    private final ScanEnhancedRequest scanRequest;

    public MultipleEntityScanExpressionQuery(DynamoDBOperations dynamoDBOperations, Class<T> clazz,
            ScanEnhancedRequest scanRequest) {
        super(dynamoDBOperations, clazz);
        this.scanRequest = scanRequest;
    }

    @NonNull
    @Override
    public List<T> getResultList() {
        assertScanEnabled(isScanEnabled());

        // SDK v2 returns PageIterable, convert to List
        PageIterable<T> pageIterable = dynamoDBOperations.scan(clazz, scanRequest);

        // If a limit is specified in the scan request, we need to respect it when collecting results.
        // DynamoDB's limit parameter specifies the max number of items to EXAMINE (before filtering),
        // not the number to RETURN (after filtering). When a filterExpression is present, multiple
        // pages may be returned, each with items that passed the filter. We need to stop collecting
        // once we reach the user-specified limit.
        Integer userLimit = scanRequest.limit();

        if (userLimit == null) {
            // No limit specified, collect all items
            return StreamSupport.stream(pageIterable.items().spliterator(), false)
                    .collect(Collectors.toList());
        }

        // Limit specified, collect up to the limit
        List<T> results = new ArrayList<>();
        for (Page<T> page : pageIterable) {
            if (results.size() >= userLimit) {
                break; // Stop collecting once we've reached the limit
            }

            // Add only as many items as needed to reach the limit
            int remainingSlots = userLimit - results.size();
            List<T> pageItems = page.items();
            if (pageItems.size() <= remainingSlots) {
                results.addAll(pageItems);
            } else {
                results.addAll(pageItems.subList(0, remainingSlots));
                break;
            }
        }
        return results;
    }

    public void assertScanEnabled(boolean scanEnabled) {
        Assert.isTrue(scanEnabled, "Scanning for this query is not enabled.  "
                + "To enable annotate your repository method with @EnableScan, or "
                + "enable scanning for all repository methods by annotating your repository interface with @EnableScan");
    }

}
