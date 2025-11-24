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
package org.socialsignin.spring.data.dynamodb.mapping;

import org.springframework.lang.Nullable;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;

/**
 * @author Prasanna Kumar Ramachandran
 *
 * @deprecated This class was created for SDK v1 compatibility. {@link DateFormat} is not thread-safe.
 *             <br>
 *             For new code using SDK v2, use {@link org.socialsignin.spring.data.dynamodb.marshaller.DateDynamoDBMarshaller} instead.
 *
 * @see org.socialsignin.spring.data.dynamodb.marshaller.DateDynamoDBMarshaller
 */
@Deprecated
public class AbstractDynamoDBDateMarshaller {

    private final DateFormat dateFormat;

    public AbstractDynamoDBDateMarshaller(DateFormat dateFormat) {
        this.dateFormat = dateFormat;
    }

    /**
     * Marshalls a Date to String format.
     *
     * @param getterReturnResult the Date to marshall
     * @return String representation of the date
     */
    @Nullable
    public String marshall(@Nullable Date getterReturnResult) {
        if (getterReturnResult == null) {
            return null;
        } else {
            return dateFormat.format(getterReturnResult);
        }
    }

    /**
     * Unmarshalls a String to Date.
     *
     * @param obj   the String to unmarshall
     * @return Date object
     * @throws IllegalArgumentException if parsing fails
     */
    @Nullable
    public Date unmarshall(@Nullable String obj) throws IllegalArgumentException {
        if (obj == null) {
            return null;
        } else {
            try {
                return dateFormat.parse(obj);
            } catch (ParseException e) {
                throw new IllegalArgumentException("Could not unmarshall '" + obj + "' via " + dateFormat, e);
            }
        }
    }

}
