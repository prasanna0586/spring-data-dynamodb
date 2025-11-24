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

import org.springframework.util.StringUtils;

import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;

/**
 * Abstract base class for Date marshalling.
 * Provides conversion between Date objects and String representations.
 *
 * @deprecated This class was created for SDK v1 compatibility. For new code using SDK v2,
 *             consider using AttributeConverter instead.
 * @since 1.0.0
 */
@Deprecated
public abstract class DateDynamoDBMarshaller {

    public abstract DateFormat getDateFormat();

    /**
     * Converts a Date to String representation.
     *
     * @param object the Date to convert
     * @return String representation of the date
     */
    public String convert(Date object) {
        return marshall(object);
    }

    /**
     * Marshalls a Date to String format.
     *
     * @param getterReturnResult the Date to marshall
     * @return String representation of the date
     */
    public String marshall(Date getterReturnResult) {
        if (getterReturnResult == null) {
            return null;
        } else {
            return getDateFormat().format(getterReturnResult);
        }
    }

    /**
     * Converts a String back to Date.
     *
     * @param object the String to convert
     * @return Date object
     */
    public Date unconvert(String object) {
        return unmarshall(object);
    }

    /**
     * Unmarshalls a String to Date.
     *
     * @param obj   the String to unmarshall
     * @return Date object
     */
    public Date unmarshall(String obj) {
        if (!StringUtils.hasLength(obj)) {
            return null;
        } else {
            try {
                return getDateFormat().parse(obj);
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
