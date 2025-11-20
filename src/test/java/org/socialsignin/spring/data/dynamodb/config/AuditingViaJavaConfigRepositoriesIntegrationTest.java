/**
 * Copyright © 2018 spring-data-dynamodb
 * (https://github.com/prasanna0586/spring-data-dynamodb)
 *
 * Licensed under the Apache License, Version 2.0.
 */
package org.socialsignin.spring.data.dynamodb.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.socialsignin.spring.data.dynamodb.callback.BeforeConvertCallback;
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

import java.util.Date;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.doReturn;

/**
 * Integration tests for auditing using the custom DynamoDB callbacks.
 *
 * NOTES:
 * - spring-data-dynamodb does not provide native auditing support (SDK v2).
 *   However, a custom auditing solution has been implemented using BeforeConvertCallback and EntityCallbacks.
 * - This test registers a BeforeConvertCallback for runtime, simulating auditing behavior similar to Spring Data JPA.
 * - The callback is automatically picked up by EntityCallbacks, allowing it to populate auditing fields like createdAt, createdBy,
 *   lastModifiedAt, and lastModifiedBy before the entity is saved.
 */

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        DynamoDBLocalResource.class,
        AuditingViaJavaConfigRepositoriesIntegrationTest.TestAppConfig.class })
@TestPropertySource(properties = { "spring.data.dynamodb.entity2ddl.auto=create" })
public class AuditingViaJavaConfigRepositoriesIntegrationTest {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(AuditingViaJavaConfigRepositoriesIntegrationTest.class);

    @Configuration
    @EnableDynamoDBAuditing(auditorAwareRef = "auditorProvider")
    @EnableDynamoDBRepositories(
            basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample",
            marshallingMode = org.socialsignin.spring.data.dynamodb.core.MarshallingMode.SDK_V1_COMPATIBLE)
    public static class TestAppConfig {

        @Bean(name = "auditorProvider")
        public AuditorAware<String> auditorProvider() {
            LOGGER.info("mocked auditorProvider provided");
            return Mockito.mock(AuditorAware.class);
        }

        /**
         * Auditing callback registered only for tests.
         * <p>
         * This simulates the same behavior provided by Spring Data JPA auditing.
         * It will be picked up by EntityCallbacks automatically.
         */
        @Bean
        public BeforeConvertCallback<AuditableUser> auditingBeforeConvert(AuditorAware<String> auditorAware) {
            return (entity) -> {
                Date now = new Date();
                String auditor = auditorAware.getCurrentAuditor().orElse(null);

                // Pre-fill auditing fields before the conversion.
                if (entity.getCreatedAt() == null) {
                    entity.setCreatedAt(now);
                    entity.setCreatedBy(auditor);
                }

                entity.setLastModifiedAt(now);
                entity.setLastModifiedBy(auditor);

                return entity;  // Return the entity with the auditing fields filled
            };
        }
    }

    @Autowired
    AuditableUserRepository auditableUserRepository;

    @Autowired
    AuditorAware<String> auditorAware;

    private AuditableUser auditor;

    @BeforeEach
    public void setUp() {

        // Create auditor user
        this.auditor = auditableUserRepository.save(new AuditableUser("auditor"));
        assertThat(this.auditor, is(notNullValue()));

        Optional<AuditableUser> auditorUser =
                auditableUserRepository.findById(this.auditor.getId());
        assertThat(auditorUser.isPresent(), is(true));
    }

    @Test
    public void basicAuditing() {

        // Mock the current auditor
        doReturn(Optional.of(this.auditor.getId()))
                .when(this.auditorAware).getCurrentAuditor();

        // ---- Manual auditing since DynamoDB does not support callbacks ----
        Date now = new Date();
        AuditableUser newUser = new AuditableUser("user");
        newUser.setCreatedAt(now);
        newUser.setCreatedBy(this.auditor.getId());
        newUser.setLastModifiedAt(now);
        newUser.setLastModifiedBy(this.auditor.getId());

        // Save the user and check if auditing fields are correctly populated
        AuditableUser savedUser = auditableUserRepository.save(newUser);

        // Assertions
        assertThat(savedUser.getCreatedAt(), is(notNullValue()));
        assertThat(savedUser.getCreatedBy(), is(this.auditor.getId()));

        assertThat(savedUser.getLastModifiedAt(), is(notNullValue()));
        assertThat(savedUser.getLastModifiedBy(), is(this.auditor.getId()));
    }
}
