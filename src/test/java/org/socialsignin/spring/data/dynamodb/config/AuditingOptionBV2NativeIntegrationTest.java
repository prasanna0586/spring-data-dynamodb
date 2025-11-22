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
import org.socialsignin.spring.data.dynamodb.domain.sample.AuditableUser;
import org.socialsignin.spring.data.dynamodb.domain.sample.AuditableUserRepository;
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
import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for auditing with SDK_V2_NATIVE marshalling mode.
 *
 * Configuration Option B: Using marshallingMode parameter on @EnableDynamoDBRepositories
 * with SDK_V2_NATIVE mode (no auto-generation, IDs must be set manually).
 *
 * @author Prasanna Kumar Ramachandran
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { DynamoDBLocalResource.class,
        AuditingOptionBV2NativeIntegrationTest.TestAppConfig.class })
@TestPropertySource(properties = { "spring.data.dynamodb.entity2ddl.auto=create" })
public class AuditingOptionBV2NativeIntegrationTest {

    /**
     * Simple AuditorAware implementation for testing.
     * Returns a configurable auditor ID.
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
    @EnableDynamoDBAuditing(auditorAwareRef = "auditorProvider", setDates = true, modifyOnCreate = true)
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample",
                                 marshallingMode = org.socialsignin.spring.data.dynamodb.core.MarshallingMode.SDK_V2_NATIVE)
    public static class TestAppConfig {

        @Bean(name = "auditorProvider")
        public AuditorAware<String> auditorProvider() {
            return new TestAuditorAware();
        }
    }

    @Autowired
    AuditableUserRepository auditableUserRepository;

    @Autowired
    TestAuditorAware auditorAware;

    AuditableUser auditor;

    @BeforeEach
    public void setUp() {
        // Set the current auditor
        auditorAware.setCurrentAuditor("system-auditor");

        // Create and save an auditor (ID must be set manually in SDK_V2_NATIVE)
        AuditableUser auditor = new AuditableUser("auditor");
        auditor.setId(UUID.randomUUID().toString());
        this.auditor = auditableUserRepository.save(auditor);
        assertThat(this.auditor, is(notNullValue()));
        assertThat(this.auditor.getId(), is(notNullValue()));

        Optional<AuditableUser> auditorUser = auditableUserRepository.findById(this.auditor.getId());
        assertTrue(auditorUser.isPresent());
    }

    @Test
    public void basicAuditingWithManualId() {
        // Set the current auditor to the saved auditor's ID
        auditorAware.setCurrentAuditor(this.auditor.getId());

        // Create and save user (ID must be set manually in SDK_V2_NATIVE mode)
        AuditableUser user = new AuditableUser("user");
        user.setId(UUID.randomUUID().toString());
        AuditableUser savedUser = auditableUserRepository.save(user);

        // Verify auditing fields are populated
        assertThat(savedUser.getId(), is(notNullValue()));
        assertThat(savedUser.getCreatedAt(), is(notNullValue()));
        assertThat(savedUser.getCreatedBy(), is(this.auditor.getId()));
        assertThat(savedUser.getLastModifiedAt(), is(notNullValue()));
        assertThat(savedUser.getLastModifiedBy(), is(this.auditor.getId()));
    }

}
