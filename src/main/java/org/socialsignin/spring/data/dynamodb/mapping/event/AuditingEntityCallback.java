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
package org.socialsignin.spring.data.dynamodb.mapping.event;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.core.Ordered;
import org.springframework.data.auditing.IsNewAwareAuditingHandler;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;

/**
 * Entity callback to populate auditing related fields on an entity about to be saved.
 * This callback-based approach ensures the modified entity is properly returned and saved.
 * @author Prasanna Kumar Ramachandran
 */
public class AuditingEntityCallback implements BeforeConvertCallback<Object>, Ordered {

    @NonNull
    private final ObjectFactory<IsNewAwareAuditingHandler> auditingHandlerFactory;

    /**
     * Creates a new {@link AuditingEntityCallback} using the given {@link IsNewAwareAuditingHandler}
     * provided by the given {@link ObjectFactory}.
     * @param auditingHandlerFactory must not be {@literal null}.
     */
    public AuditingEntityCallback(@NonNull ObjectFactory<IsNewAwareAuditingHandler> auditingHandlerFactory) {
        Assert.notNull(auditingHandlerFactory, "IsNewAwareAuditingHandler must not be null!");
        this.auditingHandlerFactory = auditingHandlerFactory;
    }

    @NonNull
    @Override
    public Object onBeforeConvert(@NonNull Object entity, String tableName) {
        IsNewAwareAuditingHandler handler = auditingHandlerFactory.getObject();
        // markAudited should never return null, but return original entity as fallback
        return handler.markAudited(entity);
    }

    @Override
    public int getOrder() {
        return 100;  // Run before other callbacks
    }
}
