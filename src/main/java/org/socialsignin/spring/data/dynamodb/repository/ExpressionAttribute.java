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
package org.socialsignin.spring.data.dynamodb.repository;

/**
 * Annotation for defining expression attribute mappings in DynamoDB queries.
 */
public @interface ExpressionAttribute {
    /**
     * The expression attribute name or value key.
     * @return the key
     */
    String key() default "";

    /**
     * The expression attribute value.
     * @return the value
     */
    String value() default "";

    /**
     * The parameter name for method parameter binding.
     * @return the parameter name
     */
    String parameterName() default "";
}
