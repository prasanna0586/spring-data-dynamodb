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

import org.socialsignin.spring.data.dynamodb.core.MarshallingMode;
import org.socialsignin.spring.data.dynamodb.repository.support.DynamoDBRepositoryFactoryBean;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Import;
import org.springframework.data.repository.query.QueryLookupStrategy.Key;

import java.lang.annotation.*;

/**
 * Annotation to enable DynamoDB repositories. Will scan the package of the annotated configuration class for Spring
 * Data repositories by default.
 * @author Prasanna Kumar Ramachandran
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import(DynamoDBRepositoriesRegistrar.class)
public @interface EnableDynamoDBRepositories {

    /**
     * Alias for the {@link #basePackages()} attribute. Allows for more concise annotation declarations e.g.:
     * {@code @EnableDynamoDBRepositories("org.my.pkg")} instead of
     * {@code @EnableDynamoDBRepositories(basePackages="org.my.pkg")}.
     * @return The package name for scanning
     */
    String[] value() default {};

    /**
     * Base packages to scan for annotated components. {@link #value()} is an alias for (and mutually exclusive with)
     * this attribute. Use {@link #basePackageClasses()} for a type-safe alternative to String-based package names.
     * @return The package name for scanning
     */
    String[] basePackages() default {};

    /**
     * Type-safe alternative to {@link #basePackages()} for specifying the packages to scan for annotated components.
     * The package of each class specified will be scanned. Consider creating a special no-op marker class or interface
     * in each package that serves no purpose other than being referenced by this attribute.
     * @return The class to figure out the base package for scanning
     */
    Class<?>[] basePackageClasses() default {};

    /**
     * Specifies which types are eligible for component scanning. Further narrows the set of candidate components from
     * everything in {@link #basePackages()} to everything in the base packages that matches the given filter or
     * filters.
     * @return All the include filters
     */
    Filter[] includeFilters() default {};

    /**
     * Specifies which types are not eligible for component scanning.
     * @return All the exclude filters
     */
    Filter[] excludeFilters() default {};

    /**
     * Returns the postfix to be used when looking up custom repository implementations. Defaults to {@literal Impl}. So
     * for a repository named {@code PersonRepository} the corresponding implementation class will be looked up scanning
     * for {@code PersonRepositoryImpl}. Defaults to 'Impl'.
     * @return The implementation postfix that's used
     */
    String repositoryImplementationPostfix() default "Impl";

    /**
     * Configures the location of where to find the Spring Data named queries properties file. Will default to
     * {@code META-INFO/jpa-named-queries.properties}.
     * @return The location itself
     */
    String namedQueriesLocation() default "";

    /**
     * Returns the key of the {@link org.springframework.data.repository.query.QueryLookupStrategy} to be used for
     * lookup queries for query methods. Defaults to {@link Key#CREATE_IF_NOT_FOUND}.
     * @return The lookup strategy
     */
    Key queryLookupStrategy() default Key.CREATE_IF_NOT_FOUND;

    /**
     * Returns the {@link org.springframework.beans.factory.FactoryBean} class to be used for each repository instance.
     * Defaults to {@link DynamoDBRepositoryFactoryBean}.
     * @return The repository factory bean cleass
     */
    Class<?> repositoryFactoryBeanClass() default DynamoDBRepositoryFactoryBean.class;

    // DynamoDB specific configuration

    /**
     * Returns the DynamoDB client reference to be used for each repository
     * instance
     * @return The DynamoDB client bean name
     */
    String amazonDynamoDBRef() default "";

    /**
     * Returns the DynamoDB mapper configuration reference to be used
     * to configure DynamoDB operations
     * @return The DynamoDB mapper configuration bean name
     */
    String dynamoDBMapperConfigRef() default "";

    /**
     * Returns the DynamoDB mapper reference to be used
     * @return The DynamoDB mapper bean name
     */
    String dynamoDBMapperRef() default "";

    /**
     * Returns the {@link jakarta.validation.Validator } reference to be used for to validate DynamoDB entities
     * @return The {@link org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations} bean name
     */
    String dynamoDBOperationsRef() default "";

    /**
     * Returns the {@link org.socialsignin.spring.data.dynamodb.mapping.DynamoDBMappingContext} reference for the
     * {@link org.springframework.data.mapping.context.MappingContext} for AmazonDynamoDB.
     * @return The {@link org.socialsignin.spring.data.dynamodb.mapping.DynamoDBMappingContext} bean name
     */
    String mappingContextRef() default "";

    /**
     * Configures whether nested repository-interfaces (e.g. defined as inner classes) should be discovered by the
     * repositories' infrastructure.
     */
    boolean considerNestedRepositories() default false;

    /**
     * Configures the marshalling mode for type conversion between Java objects and DynamoDB AttributeValues.
     * <p>
     * Defaults to {@link MarshallingMode#SDK_V2_NATIVE} which uses AWS SDK v2's native type mappings
     * (Boolean → BOOL, Date/Instant → Number/Converter).
     * <p>
     * Use {@link MarshallingMode#SDK_V1_COMPATIBLE} if you are migrating from spring-data-dynamodb
     * with AWS SDK v1 and have existing data that uses the legacy type mappings (Boolean → Number "1"/"0",
     * Date/Instant → String ISO-8601).
     * @return The marshalling mode to use
     * @since 7.0.0
     */
    MarshallingMode marshallingMode() default MarshallingMode.SDK_V2_NATIVE;
}
