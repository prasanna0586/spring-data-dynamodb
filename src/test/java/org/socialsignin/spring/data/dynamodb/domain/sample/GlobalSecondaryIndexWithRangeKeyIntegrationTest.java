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
package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.socialsignin.spring.data.dynamodb.utils.DynamoDBLocalResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Shows the usage of Hash+Range key combinations with global secondary indexes.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { DynamoDBLocalResource.class, GlobalSecondaryIndexWithRangeKeyIntegrationTest.TestAppConfig.class })
@TestPropertySource(properties = { "spring.data.dynamodb.entity2ddl.auto=create" })
public class GlobalSecondaryIndexWithRangeKeyIntegrationTest {

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample", marshallingMode = org.socialsignin.spring.data.dynamodb.core.MarshallingMode.SDK_V1_COMPATIBLE)
    public static class TestAppConfig {
    }

    @Autowired
    private InstallationRepository installationRepository;

    @Test
    public void testFindBySystemIdOrderByUpdatedAtDesc() {
        installationRepository.save(new Installation("systemId", createDate(10, 5, 1995)));
        installationRepository.save(new Installation("systemId", createDate(20, 10, 2001)));
        installationRepository.save(new Installation("systemId", createDate(28, 10, 2016)));

        final List<Installation> actual = installationRepository.findBySystemIdOrderByUpdatedAtDesc("systemId");
        assertNotNull(actual);
        assertFalse(actual.isEmpty());

        Date previousDate = null;
        for (final Installation installation : actual) {
            assertEquals(installation.getSystemId(), "systemId");
            if (previousDate != null && installation.getUpdatedAt().compareTo(previousDate) != -1) {
                fail("Results were not returned in descending order of updated date!");
            } else {
                previousDate = installation.getUpdatedAt();
            }
        }
    }

    private Date createDate(final int dayOfMonth, final int month, final int year) {
        final Calendar calendar = Calendar.getInstance();
        calendar.set(year, month, dayOfMonth, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }
}
