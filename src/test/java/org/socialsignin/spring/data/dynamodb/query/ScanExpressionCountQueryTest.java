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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;
import org.socialsignin.spring.data.dynamodb.domain.sample.User;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class ScanExpressionCountQueryTest {

    @Mock
    private DynamoDBOperations dynamoDBOperations;
    @Mock
    private ScanEnhancedRequest scanExpression;

    private ScanExpressionCountQuery<User> underTest;

    @Test
    public void testScanCountEnabledTrueTrue() {
        underTest = new ScanExpressionCountQuery<>(dynamoDBOperations, User.class, scanExpression, true);

        underTest.assertScanCountEnabled(true);

        assertTrue(true);
    }

    @Test
    public void testScanCountEnabledTrueFalse() {
        underTest = new ScanExpressionCountQuery<>(dynamoDBOperations, User.class, scanExpression, true);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            underTest.assertScanCountEnabled(false);
        });

        assertTrue(exception.getMessage().contains("Scanning for the total counts for this query is not enabled. "
                + " To enable annotate your repository method with @EnableScanCount, or enable scanning for all repository methods by annotating your repository interface with @EnableScanCount. "
                + " This total count is required to serve this Page query - if total counts are not desired an alternative approach could be to replace the Page query with a Slice query"));
    }

    @Test
    public void testScanCountEnabledFalseTrue() {
        underTest = new ScanExpressionCountQuery<>(dynamoDBOperations, User.class, scanExpression, false);

        underTest.assertScanCountEnabled(true);

        assertTrue(true);
    }

    @Test
    public void testScanCountEnabledFalseFalse() {
        underTest = new ScanExpressionCountQuery<>(dynamoDBOperations, User.class, scanExpression, false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            underTest.assertScanCountEnabled(false);
        });

        assertTrue(exception.getMessage().contains("Scanning for counts for this query is not enabled. "
                + " To enable annotate your repository method with @EnableScanCount, or enable scanning for all repository methods by annotating your repository interface with @EnableScanCount"));
    }
}
