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
package org.socialsignin.spring.data.dynamodb.config;

import org.junit.jupiter.api.Test;
import org.socialsignin.spring.data.dynamodb.mapping.DynamoDBMappingContext;
import org.socialsignin.spring.data.dynamodb.mapping.event.AbstractDynamoDBEventListener;
import org.socialsignin.spring.data.dynamodb.mapping.event.BeforeSaveEvent;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.auditing.IsNewAwareAuditingHandler;
import org.springframework.util.Assert;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.time.LocalDateTime;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Integration test for the auditing support.
 *
 * @author Vito Limandibhrata
 */
public class AuditingIntegrationTests {

    /**
     * Test-specific auditing event listener that responds to manually published BeforeSaveEvent.
     * This is used to test the legacy XML-based auditing configuration.
     * Production code should use @EnableDynamoDBAuditing which uses AuditingEntityCallback instead.
     */
    public static class TestAuditingEventListener extends AbstractDynamoDBEventListener<Object> {

        private final ObjectFactory<IsNewAwareAuditingHandler> auditingHandlerFactory;

        public TestAuditingEventListener(ObjectFactory<IsNewAwareAuditingHandler> auditingHandlerFactory) {
            Assert.notNull(auditingHandlerFactory, "IsNewAwareAuditingHandler must not be null!");
            this.auditingHandlerFactory = auditingHandlerFactory;
        }

        @Override
        public void onBeforeSave(Object source) {
            auditingHandlerFactory.getObject().markAudited(source);
        }
    }

    @Test
    public void enablesAuditingAndSetsPropertiesAccordingly() throws Exception {

        AbstractApplicationContext context = new ClassPathXmlApplicationContext("auditing.xml", getClass());

        DynamoDBMappingContext mappingContext = context.getBean(DynamoDBMappingContext.class);
        mappingContext.getPersistentEntity(Entity.class);

        Entity entity = new Entity();
        BeforeSaveEvent<Entity> event = new BeforeSaveEvent<Entity>(entity);
        context.publishEvent(event);

        assertThat(entity.created, is(notNullValue()));
        assertThat(entity.modified, is(entity.created));

        Thread.sleep(10);
        entity.id = 1L;
        event = new BeforeSaveEvent<Entity>(entity);
        context.publishEvent(event);

        assertThat(entity.created, is(notNullValue()));
        assertThat(entity.modified, is(not(entity.created)));
        context.close();
    }

    @DynamoDbBean
    class Entity {

        @Id
        Long id;
        @CreatedDate
        LocalDateTime created;

        @LastModifiedDate
        LocalDateTime modified;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public LocalDateTime getCreated() {
            return created;
        }

        public void setCreated(LocalDateTime created) {
            this.created = created;
        }

        public LocalDateTime getModified() {
            return modified;
        }

        public void setModified(LocalDateTime modified) {
            this.modified = modified;
        }
    }
}
