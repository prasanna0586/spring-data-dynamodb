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
package org.socialsignin.spring.data.dynamodb.config;

import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * Annotation to enable auditing in DynamoDB via annotation configuration.
 * @author Prasanna Kumar Ramachandran
 */
@Inherited
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(DynamoDBAuditingRegistrar.class)
public @interface EnableDynamoDBAuditing {

    /**
     * Configures the {@link org.springframework.data.domain.AuditorAware} bean to be used to look up the current principal.
     * @return the bean name of the AuditorAware instance to be used
     */
    String auditorAwareRef() default "";

    /**
     * Configures whether the creation and modification dates are set. Defaults to {@literal true}.
     * @return true if dates should be set, false otherwise
     */
    boolean setDates() default true;

    /**
     * Configures whether the entity shall be marked as modified on creation. Defaults to {@literal true}.
     * @return true if entity should be marked as modified on creation, false otherwise
     */
    boolean modifyOnCreate() default true;

    /**
     * Configures a {@link org.springframework.data.auditing.DateTimeProvider} bean name to be used to look up the {@link java.time.temporal.TemporalAccessor} used for setting the current date and time.
     * @return the bean name of the DateTimeProvider instance to be used
     */
    String dateTimeProviderRef() default "";

}
