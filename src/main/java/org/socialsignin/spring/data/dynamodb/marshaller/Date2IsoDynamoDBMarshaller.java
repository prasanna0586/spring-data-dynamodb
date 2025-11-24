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
package org.socialsignin.spring.data.dynamodb.marshaller;

import org.springframework.lang.NonNull;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * Marshaller for converting Date to ISO-8601 String format with millisecond precision.
 * Format: "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" in UTC timezone
 * @deprecated This class was created for SDK v1 compatibility. For new code using SDK v2,
 *             consider using AttributeConverter instead.
 * @since 1.0.0
 */
@Deprecated
public class Date2IsoDynamoDBMarshaller extends DateDynamoDBMarshaller {

    /**
     * Constructs a new Date2IsoDynamoDBMarshaller.
     */
    public Date2IsoDynamoDBMarshaller() {
    }

    private static final String PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    @NonNull
    @Override
    public DateFormat getDateFormat() {
        SimpleDateFormat df = new SimpleDateFormat(PATTERN);
        df.setTimeZone(UTC);
        return df;
    }
}
