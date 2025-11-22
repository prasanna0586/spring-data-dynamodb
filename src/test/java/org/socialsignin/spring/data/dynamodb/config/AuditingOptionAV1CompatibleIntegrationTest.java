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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.socialsignin.spring.data.dynamodb.core.MarshallingMode;
import org.socialsignin.spring.data.dynamodb.domain.sample.AuditableUser;
import org.socialsignin.spring.data.dynamodb.domain.sample.AuditableUserRepository;
import org.socialsignin.spring.data.dynamodb.mapping.DynamoDBMappingContext;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.socialsignin.spring.data.dynamodb.utils.DynamoDBLocalResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for auditing with explicit DynamoDBMappingContext bean (Option A).
 *
 * Configuration Option A: Explicit @Bean for dynamoDBMappingContext
 * with SDK_V1_COMPATIBLE mode (auto-generation enabled).
 *
 * @author Prasanna Kumar Ramachandran
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { DynamoDBLocalResource.class,
        AuditingOptionAV1CompatibleIntegrationTest.TestAppConfig.class })
@TestPropertySource(properties = { "spring.data.dynamodb.entity2ddl.auto=create" })
public class AuditingOptionAV1CompatibleIntegrationTest {

    /**
     * Simple AuditorAware implementation for testing.
     */
    public static class TestAuditorAware implements AuditorAware<String> {
        private String currentAuditor = "system-auditor";

        public void setCurrentAuditor(String auditor) {
            this.currentAuditor = auditor;
        }

        @Override
        public Optional<String> getCurrentAuditor() {
            return Optional.ofNullable(currentAuditor);
        }
    }

    @Configuration
    @EnableDynamoDBAuditing(auditorAwareRef = "auditorProvider")
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    public static class TestAppConfig {

        @Bean(name = "auditorProvider")
        public AuditorAware<String> auditorProvider() {
            return new TestAuditorAware();
        }

        @Bean
        public DynamoDBMappingContext dynamoDBMappingContext() {
            return new DynamoDBMappingContext(MarshallingMode.SDK_V1_COMPATIBLE);
        }
    }

    @Autowired
    AuditableUserRepository auditableUserRepository;

    @Autowired
    TestAuditorAware auditorAware;

    AuditableUser auditor;

    @BeforeEach
    public void setUp() {
        auditorAware.setCurrentAuditor("system-auditor");

        AuditableUser auditor = new AuditableUser("auditor");
        this.auditor = auditableUserRepository.save(auditor);
        assertThat(this.auditor, is(notNullValue()));
        assertThat(this.auditor.getId(), is(notNullValue()));

        Optional<AuditableUser> auditorUser = auditableUserRepository.findById(this.auditor.getId());
        assertTrue(auditorUser.isPresent());
    }

    @Test
    public void auditingWithExplicitBeanV1Compatible() {
        auditorAware.setCurrentAuditor(this.auditor.getId());

        AuditableUser user = new AuditableUser("user");
        AuditableUser savedUser = auditableUserRepository.save(user);

        assertThat(savedUser.getId(), is(notNullValue()));
        assertThat(savedUser.getCreatedAt(), is(notNullValue()));
        assertThat(savedUser.getCreatedBy(), is(this.auditor.getId()));
        assertThat(savedUser.getLastModifiedAt(), is(notNullValue()));
        assertThat(savedUser.getLastModifiedBy(), is(this.auditor.getId()));
    }

}
