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
package org.socialsignin.spring.data.dynamodb.config;

/**
 * Constants to declare bean names used by the namespace configuration.
 * <p>
 * This utility class provides centralized bean name constants used throughout
 * the DynamoDB configuration and namespace parsing.
 * @author Prasanna Kumar Ramachandran
 */
public abstract class BeanNames {

    /**
     * Bean name constant for the DynamoDB mapping context.
     * The value is "dynamoDBMappingContext".
     */
    public static final String MAPPING_CONTEXT_BEAN_NAME = "dynamoDBMappingContext";

}
