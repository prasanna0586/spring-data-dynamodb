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
import org.springframework.util.Assert;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class MultipleEntityScanExpressionQuery<T> extends AbstractMultipleEntityQuery<T> {

    private ScanEnhancedRequest scanRequest;

    public MultipleEntityScanExpressionQuery(DynamoDBOperations dynamoDBOperations, Class<T> clazz,
            ScanEnhancedRequest scanRequest) {
        super(dynamoDBOperations, clazz);
        this.scanRequest = scanRequest;
    }

    @Override
    public List<T> getResultList() {
        assertScanEnabled(isScanEnabled());
        // SDK v2 returns PageIterable, convert to List
        return StreamSupport.stream(dynamoDBOperations.scan(clazz, scanRequest).items().spliterator(), false)
                .collect(Collectors.toList());
    }

    public void assertScanEnabled(boolean scanEnabled) {
        Assert.isTrue(scanEnabled, "Scanning for this query is not enabled.  "
                + "To enable annotate your repository method with @EnableScan, or "
                + "enable scanning for all repository methods by annotating your repository interface with @EnableScan");
    }

}
