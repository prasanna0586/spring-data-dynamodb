/**
 * Copyright © 2018 spring-data-dynamodb (https://github.com/prasanna0586/spring-data-dynamodb)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *     http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.socialsignin.spring.data.dynamodb.repository.config;

import org.springframework.data.repository.config.RepositoryBeanDefinitionRegistrarSupport;
import org.springframework.data.repository.config.RepositoryConfigurationExtension;
import org.springframework.lang.NonNull;

import java.lang.annotation.Annotation;

/**
 * Bean definition registrar for DynamoDB repositories.
 * <p>
 * Handles the registration of DynamoDB repository beans when the
 * {@link EnableDynamoDBRepositories} annotation is detected.
 * @author Prasanna Kumar Ramachandran
 */
public class DynamoDBRepositoriesRegistrar extends RepositoryBeanDefinitionRegistrarSupport {

    @NonNull
    @Override
    protected Class<? extends Annotation> getAnnotation() {
        return EnableDynamoDBRepositories.class;
    }

    @NonNull
    @Override
    protected RepositoryConfigurationExtension getExtension() {
        return new DynamoDBRepositoryConfigExtension();
    }

}
