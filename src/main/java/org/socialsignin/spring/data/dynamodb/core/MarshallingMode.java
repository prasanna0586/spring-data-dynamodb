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
package org.socialsignin.spring.data.dynamodb.core;

/**
 * Defines the marshalling behavior for attribute values when converting between
 * Java types and DynamoDB AttributeValue objects.
 *
 * <p>This configuration affects how Boolean, Date, and Instant types are stored in DynamoDB:
 *
 * <ul>
 * <li><b>SDK_V2_NATIVE</b> (default): Uses AWS SDK v2's native type mappings
 *     <ul>
 *     <li>Boolean → DynamoDB BOOL type</li>
 *     <li>Date/Instant → DynamoDB Number (epoch seconds) or custom converter</li>
 *     </ul>
 * </li>
 * <li><b>SDK_V1_COMPATIBLE</b> (opt-in): Maintains backward compatibility with AWS SDK v1
 *     <ul>
 *     <li>Boolean → DynamoDB Number ("1" for true, "0" for false)</li>
 *     <li>Date/Instant → DynamoDB String (ISO-8601 format)</li>
 *     </ul>
 * </li>
 * </ul>
 *
 * <p><b>Important:</b> SDK_V1_COMPATIBLE mode is provided for users migrating from
 * spring-data-dynamodb versions that used AWS SDK v1. If you have existing data in
 * DynamoDB created with SDK v1, you must use SDK_V1_COMPATIBLE mode to ensure queries
 * match your existing data correctly.
 *
 * <p><b>New projects should use SDK_V2_NATIVE</b> (the default) for modern, standard
 * DynamoDB type mappings.
 *
 * @author Michael Lavelle
 * @author Sebastian Just
 * @since 7.0.0
 */
public enum MarshallingMode {

    /**
     * Uses AWS SDK v2's native type mappings (default).
     * Recommended for new projects with no existing SDK v1 data.
     */
    SDK_V2_NATIVE,

    /**
     * Maintains backward compatibility with AWS SDK v1 type mappings.
     * Required for projects migrating from spring-data-dynamodb with SDK v1
     * that have existing data in DynamoDB.
     */
    SDK_V1_COMPATIBLE
}
