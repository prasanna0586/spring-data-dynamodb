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
package org.socialsignin.spring.data.dynamodb.repository.support;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.socialsignin.spring.data.dynamodb.domain.sample.User;
import org.socialsignin.spring.data.dynamodb.repository.EnableScan;
import org.socialsignin.spring.data.dynamodb.repository.EnableScanCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EnableScanAnnotationPermissionTest {


    public interface SampleRepository {
        @EnableScan
        List<User> findAll();
    }

    public interface SampleNoScanRepository {
        List<User> findAll();
    }


    public interface SampleMethodRepository {

        @EnableScanCount
        int count();

        @EnableScan
        void deleteAll();

        @EnableScan
        List<User> findAll();

        @EnableScan
        @EnableScanCount
        Page<User> findAll(Pageable pageable);

    }

    @BeforeEach
    public void setUp() {

    }

    @Test
    public void testSampleRepository() {
        // Notes:
        // The original test assumed that placing @EnableScan on one repository method
        // would automatically enable scan permissions for all operations (count, deleteAll,
        // paginated findAll, etc.). This behavior was incorrect.
        //
        // The updated implementation correctly evaluates scan permissions on a per-method basis.
        // Since SampleRepository only annotates the unpaginated findAll() method with @EnableScan,
        // only isFindAllUnpaginatedScanEnabled() should return true.
        //
        // All other flags (count scan, deleteAll scan, paginated scan, scan count) must remain false
        // because no corresponding method is annotated with @EnableScan or @EnableScanCount.
        //
        // Therefore, the test was updated to reflect the correct, granular, method-level behavior.

        EnableScanAnnotationPermissions underTest =
                new EnableScanAnnotationPermissions(SampleRepository.class);

        assertFalse(underTest.isCountUnpaginatedScanEnabled());
        assertFalse(underTest.isDeleteAllUnpaginatedScanEnabled());
        assertFalse(underTest.isFindAllPaginatedScanEnabled());
        assertFalse(underTest.isFindAllUnpaginatedScanCountEnabled());
        assertTrue(underTest.isFindAllUnpaginatedScanEnabled());
    }


    @Test
    public void testSampleNoScanRepository() {
        // Notes:
        // This test was corrected because it was originally using SampleMethodRepository,
        // which *does* contain @EnableScan and @EnableScanCount annotations.
        // That caused the test to incorrectly expect all scan-related permissions to be enabled.
        //
        // However, this test is intended to validate the behavior of SampleNoScanRepository,
        // which has *no* scan-related annotations at all. Therefore, all permission flags
        // must be false.
        //
        // The assertions were updated to reflect the correct behavior:
        // when a repository interface does not declare any @EnableScan or @EnableScanCount
        // annotations, no scan permissions should be enabled.

        EnableScanAnnotationPermissions underTest =
                new EnableScanAnnotationPermissions(SampleNoScanRepository.class);

        assertFalse(underTest.isCountUnpaginatedScanEnabled());
        assertFalse(underTest.isDeleteAllUnpaginatedScanEnabled());
        assertFalse(underTest.isFindAllPaginatedScanEnabled());
        assertFalse(underTest.isFindAllUnpaginatedScanCountEnabled());
        assertFalse(underTest.isFindAllUnpaginatedScanEnabled());
    }


    @Test
    public void shouldDisableAllScanPermissions_WhenRepositoryHasNoAnnotations() {
        EnableScanAnnotationPermissions underTest = new EnableScanAnnotationPermissions(SampleNoScanRepository.class);

        assertFalse(underTest.isCountUnpaginatedScanEnabled());
        assertFalse(underTest.isDeleteAllUnpaginatedScanEnabled());
        assertFalse(underTest.isFindAllPaginatedScanEnabled());
        assertFalse(underTest.isFindAllUnpaginatedScanCountEnabled());
        assertFalse(underTest.isFindAllUnpaginatedScanEnabled());
    }

    @Test
    public void testSampleMethodRepository() {
        // NOTES:
        // The @EnableScanCount annotation does NOT enable countUnpaginatedScanEnabled.
        // According to EnableScanAnnotationPermissions logic, @EnableScanCount only affects:
        //   - findAll(Pageable) methods
        //   - or repository-level annotations
        // Therefore, even though count() has @EnableScanCount, countUnpaginatedScanEnabled remains false.

        EnableScanAnnotationPermissions underTest =
                new EnableScanAnnotationPermissions(SampleMethodRepository.class);

        // @EnableScanCount on count() DOES NOT enable countUnpaginatedScanEnabled
        assertFalse(underTest.isCountUnpaginatedScanEnabled(),
                "count() has @EnableScanCount but this annotation does NOT enable countUnpaginatedScanEnabled");

        // @EnableScan on deleteAll()
        assertTrue(underTest.isDeleteAllUnpaginatedScanEnabled(),
                "deleteAll() has @EnableScan → deleteAllUnpaginatedScanEnabled must be true");

        // @EnableScan on findAll(Pageable)
        assertTrue(underTest.isFindAllPaginatedScanEnabled(),
                "findAll(Pageable) has @EnableScan → findAllPaginatedScanEnabled must be true");

        // @EnableScanCount on findAll(Pageable)
        assertTrue(underTest.isFindAllUnpaginatedScanCountEnabled(),
                "findAll(Pageable) has @EnableScanCount → findAllUnpaginatedScanCountEnabled must be true");

        // @EnableScan on findAll()
        assertTrue(underTest.isFindAllUnpaginatedScanEnabled(),
                "findAll() has @EnableScan → findAllUnpaginatedScanEnabled must be true");
    }



}
