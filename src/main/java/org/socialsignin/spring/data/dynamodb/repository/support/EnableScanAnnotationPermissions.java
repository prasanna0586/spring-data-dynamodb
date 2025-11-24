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

import org.socialsignin.spring.data.dynamodb.repository.EnableScan;
import org.socialsignin.spring.data.dynamodb.repository.EnableScanCount;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;

/**
 * @author Prasanna Kumar Ramachandran
 */
public class EnableScanAnnotationPermissions implements EnableScanPermissions {

    private boolean findAllUnpaginatedScanEnabled = false;
    private boolean findAllPaginatedScanEnabled = false;

    private boolean findAllUnpaginatedScanCountEnabled = false;

    private boolean countUnpaginatedScanEnabled = false;
    private boolean deleteAllUnpaginatedScanEnabled = false;

    public EnableScanAnnotationPermissions(@NonNull Class<?> repositoryInterface) {
        // Check to see if global EnableScan is declared at interface level
        if (repositoryInterface.isAnnotationPresent(EnableScan.class)) {
            this.findAllUnpaginatedScanEnabled = true;
            this.countUnpaginatedScanEnabled = true;
            this.deleteAllUnpaginatedScanEnabled = true;
            this.findAllPaginatedScanEnabled = true;
        } else {
            // Process all method annotations in a single iteration for efficiency
            // This consolidates three separate loops into one, reducing iteration overhead from O(3n) to O(n)
            Method[] methods = ReflectionUtils.getAllDeclaredMethods(repositoryInterface);
            for (Method method : methods) {
                String methodName = method.getName();
                Class<?>[] paramTypes = method.getParameterTypes();
                int paramCount = paramTypes.length;

                // Check for @EnableScan annotation
                if (method.isAnnotationPresent(EnableScan.class)) {
                    if (paramCount == 0) {
                        // Methods with no parameters: findAll(), deleteAll(), count()
                        switch (methodName) {
                            case "findAll" -> findAllUnpaginatedScanEnabled = true;
                            case "deleteAll" -> deleteAllUnpaginatedScanEnabled = true;
                            case "count" -> countUnpaginatedScanEnabled = true;
                        }
                    } else if (paramCount == 1 && "findAll".equals(methodName)) {
                        // Methods with single Pageable parameter: findAll(Pageable)
                        // Cache paramTypes array to avoid multiple getParameterTypes() calls
                        // Check array bounds defensively before accessing [0]
                        if (Pageable.class.isAssignableFrom(paramTypes[0])) {
                            findAllPaginatedScanEnabled = true;
                        }
                    }
                }

                // Check for @EnableScanCount annotation
                if (method.isAnnotationPresent(EnableScanCount.class)) {
                    if (paramCount == 1 && "findAll".equals(methodName)) {
                        // Use cached paramTypes array from above
                        // paramCount == 1 guarantees paramTypes[0] exists
                        if (Pageable.class.isAssignableFrom(paramTypes[0])) {
                            findAllUnpaginatedScanCountEnabled = true;
                        }
                    }
                }
            }
        }

        // Check for class-level @EnableScanCount annotation
        if (!findAllUnpaginatedScanCountEnabled && repositoryInterface.isAnnotationPresent(EnableScanCount.class)) {
            findAllUnpaginatedScanCountEnabled = true;
        }
    }

    @Override
    public boolean isFindAllUnpaginatedScanEnabled() {
        return findAllUnpaginatedScanEnabled;

    }

    @Override
    public boolean isDeleteAllUnpaginatedScanEnabled() {
        return deleteAllUnpaginatedScanEnabled;
    }

    @Override
    public boolean isCountUnpaginatedScanEnabled() {
        return countUnpaginatedScanEnabled;
    }

    @Override
    public boolean isFindAllUnpaginatedScanCountEnabled() {
        return findAllUnpaginatedScanCountEnabled;
    }

    @Override
    public boolean isFindAllPaginatedScanEnabled() {
        return findAllPaginatedScanEnabled;
    }

}
