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
package org.socialsignin.spring.data.dynamodb.repository.support;

/**
 * Interface for managing scan operation permissions on repositories.
 * @author Prasanna Kumar Ramachandran
 */
public interface EnableScanPermissions {

    /**
     * Checks if unpaginated findAll scan is enabled.
     * @return true if unpaginated findAll scan is enabled
     */
    boolean isFindAllUnpaginatedScanEnabled();

    /**
     * Checks if paginated findAll scan is enabled.
     * @return true if paginated findAll scan is enabled
     */
    boolean isFindAllPaginatedScanEnabled();

    /**
     * Checks if unpaginated findAll scan count is enabled.
     * @return true if unpaginated findAll scan count is enabled
     */
    boolean isFindAllUnpaginatedScanCountEnabled();

    /**
     * Checks if unpaginated deleteAll scan is enabled.
     * @return true if unpaginated deleteAll scan is enabled
     */
    boolean isDeleteAllUnpaginatedScanEnabled();

    /**
     * Checks if unpaginated count scan is enabled.
     * @return true if unpaginated count scan is enabled
     */
    boolean isCountUnpaginatedScanEnabled();

}
