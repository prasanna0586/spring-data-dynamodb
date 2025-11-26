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
package org.socialsignin.spring.data.dynamodb.marshaller;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.time.Instant;

/**
 * Marshaller for converting Instant to epoch milliseconds stored as String.
 * Format: String representation of milliseconds since Unix epoch (1970-01-01T00:00:00Z)
 * @deprecated This class was created for SDK v1 compatibility. For new code using SDK v2,
 *             consider using AttributeConverter instead.
 * @since 1.0.0
 */
@Deprecated
public class Instant2EpocheDynamoDBMarshaller {

    /**
     * Default constructor.
     */
    public Instant2EpocheDynamoDBMarshaller() {
    }

    /**
     * Converts an Instant to String representation.
     * @param object the Instant to convert
     * @return String representation of epoch milliseconds
     */
    @Nullable
    public String convert(Instant object) {
        return marshall(object);
    }

    /**
     * Marshalls an Instant to epoch milliseconds String format.
     * @param getterReturnResult the Instant to marshall
     * @return String representation of epoch milliseconds
     */
    @Nullable
    public String marshall(@Nullable Instant getterReturnResult) {
        if (getterReturnResult == null) {
            return null;
        } else {
            return Long.toString(getterReturnResult.toEpochMilli());
        }
    }

    /**
     * Converts a String back to Instant.
     * @param object the String to convert
     * @return Instant object
     */
    @Nullable
    public Instant unconvert(@NonNull String object) {
        return unmarshall(object);
    }

    /**
     * Unmarshalls a String to Instant.
     * @param obj   the String to unmarshall (epoch milliseconds)
     * @return Instant object
     */
    @Nullable
    public Instant unmarshall(@NonNull String obj) {
        if (!StringUtils.hasLength(obj)) {
            return null;
        } else {
            return Instant.ofEpochMilli(Long.parseLong(obj));
        }
    }

}
