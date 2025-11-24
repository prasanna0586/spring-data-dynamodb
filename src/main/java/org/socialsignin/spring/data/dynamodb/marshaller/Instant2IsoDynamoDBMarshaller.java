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
package org.socialsignin.spring.data.dynamodb.marshaller;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Marshaller for converting Instant to ISO-8601 String format with millisecond precision.
 * Format: "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
 *
 * @deprecated This class was created for SDK v1 compatibility. For new code using SDK v2,
 *             consider using AttributeConverter instead.
 * @since 1.0.0
 */
@Deprecated
public class Instant2IsoDynamoDBMarshaller {

    private static final String PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    @NonNull
    private DateTimeFormatter getDateFormat() {
        return DateTimeFormatter.ofPattern(PATTERN).withZone(ZoneOffset.UTC);
    }

    /**
     * Converts an Instant to String representation.
     *
     * @param object the Instant to convert
     * @return ISO-8601 String representation
     */
    @Nullable
    public String convert(Instant object) {
        return marshall(object);
    }

    /**
     * Marshalls an Instant to ISO-8601 String format.
     *
     * @param getterReturnResult the Instant to marshall
     * @return ISO-8601 String representation
     */
    @Nullable
    public String marshall(@Nullable Instant getterReturnResult) {
        if (getterReturnResult == null) {
            return null;
        } else {
            return getDateFormat().format(getterReturnResult);
        }
    }

    /**
     * Converts a String back to Instant.
     *
     * @param object the String to convert
     * @return Instant object
     */
    @Nullable
    public Instant unconvert(@NonNull String object) {
        return unmarshall(object);
    }

    /**
     * Unmarshalls a String to Instant.
     *
     * @param obj   the String to unmarshall
     * @return Instant object
     */
    @Nullable
    public Instant unmarshall(@NonNull String obj) {
        if (!StringUtils.hasLength(obj)) {
            return null;
        } else {
            return Instant.from(getDateFormat().parse(obj));
        }
    }

}
