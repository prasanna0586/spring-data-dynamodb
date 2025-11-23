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
package org.socialsignin.spring.data.dynamodb.mapping.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.data.auditing.IsNewAwareAuditingHandler;
import org.springframework.util.Assert;

/**
 * Event listener to populate auditing related fields on an entity about to be saved.
 * <p>
 * NOTE: This listener is registered for backward compatibility with XML-based configurations,
 * but auditing is handled by {@link AuditingEntityCallback} in modern annotation-based configurations.
 * The callback-based approach is triggered automatically during repository.save() operations.
 *
 * @author Prasanna Kumar Ramachandran
 */
public class AuditingEventListener extends AbstractDynamoDBEventListener<Object> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditingEventListener.class);

    private final ObjectFactory<IsNewAwareAuditingHandler> auditingHandlerFactory;

    /**
     * Creates a new {@link AuditingEventListener} using the given
     * {@link org.springframework.data.mapping.context.MappingContext} and
     * {@link org.springframework.data.auditing.AuditingHandler} provided by the given {@link ObjectFactory}.
     *
     * @param auditingHandlerFactory must not be {@literal null}.
     */
    public AuditingEventListener(ObjectFactory<IsNewAwareAuditingHandler> auditingHandlerFactory) {
        Assert.notNull(auditingHandlerFactory, "IsNewAwareAuditingHandler must not be null!");
        this.auditingHandlerFactory = auditingHandlerFactory;
    }

    // Note: onBeforeSave() is not implemented here.
    // Modern auditing uses AuditingEntityCallback which is triggered during repository.save().
    // For legacy XML-based event publishing, extend this class and override onBeforeSave().
}
