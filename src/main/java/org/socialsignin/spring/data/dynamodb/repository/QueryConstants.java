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
package org.socialsignin.spring.data.dynamodb.repository;

public class QueryConstants {

    private QueryConstants() {
    }

    public static final int QUERY_LIMIT_UNLIMITED = Integer.MIN_VALUE;

    public enum ConsistentReadMode {
        /**
         * Use the default configured in the DynamoDBMapper
         *
         * @see com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig
         * @see <a href=
         *      "https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/DynamoDBMapper.OptionalConfig.html">DynamoDBMapper
         *      Configuration</a>
         */
        DEFAULT,
        /**
         * Set consistent read mode to true
         *
         * @see <a href=
         *      "https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/dynamodbv2/datamodeling/DynamoDBQueryExpression.html#setConsistentRead-boolean-">Consistent
         *      Reads</a>
         * @see com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression#setConsistentRead(boolean)
         */
        CONSISTENT,
        /**
         * Set consistent read mode to false
         *
         * @see <a href=
         *      "https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/dynamodbv2/datamodeling/DynamoDBQueryExpression.html#setConsistentRead-boolean-">Consistent
         *      Reads</a>
         * @see com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression#setConsistentRead(boolean)
         */
        EVENTUAL
    }

}
