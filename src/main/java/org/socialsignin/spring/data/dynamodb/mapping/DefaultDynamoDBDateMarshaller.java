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
package org.socialsignin.spring.data.dynamodb.mapping;

import java.io.Serial;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * Default date marshaller that uses ISO 8601 format with UTC timezone.
 *
 * @author Prasanna Kumar Ramachandran
 * @deprecated Consider using {@link org.socialsignin.spring.data.dynamodb.marshaller.Date2IsoDynamoDBMarshaller}
 */
@Deprecated
public class DefaultDynamoDBDateMarshaller extends AbstractDynamoDBDateMarshaller {

    private static final class UTCSimpleDateFormat extends SimpleDateFormat {
        @Serial
        private static final long serialVersionUID = 1L;

        private UTCSimpleDateFormat() {
            super("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            setTimeZone(TimeZone.getTimeZone("UTC"));
        }

        @Override
        public String toString() {
            return toPattern();
        }
    }

    /**
     * Constructs a new DefaultDynamoDBDateMarshaller with UTC timezone.
     */
    public DefaultDynamoDBDateMarshaller() {
        super(new UTCSimpleDateFormat());
    }
}
