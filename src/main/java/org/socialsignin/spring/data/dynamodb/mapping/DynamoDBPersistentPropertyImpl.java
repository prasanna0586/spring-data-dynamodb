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
package org.socialsignin.spring.data.dynamodb.mapping;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Reference;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mapping.Association;
import org.springframework.data.mapping.model.AnnotationBasedPersistentProperty;
import org.springframework.data.mapping.model.Property;
import org.springframework.data.mapping.model.SimpleTypeHolder;
import org.springframework.lang.NonNull;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * {@link DynamoDBPersistentProperty} implementation
 * @author Prasanna Kumar Ramachandran
 */
class DynamoDBPersistentPropertyImpl extends AnnotationBasedPersistentProperty<DynamoDBPersistentProperty>
        implements DynamoDBPersistentProperty {

    @NonNull
    private static final Collection<Class<? extends Annotation>> ASSOCIATION_ANNOTATIONS;
    @NonNull
    private static final Collection<Class<? extends Annotation>> ID_ANNOTATIONS;

    static {

        Set<Class<? extends Annotation>> annotations;

        // Reference not yet supported
        ASSOCIATION_ANNOTATIONS = Set.of(Reference.class);

        annotations = new HashSet<>();
        annotations.add(Id.class);
        annotations.add(DynamoDbPartitionKey.class);
        ID_ANNOTATIONS = annotations;
    }

    /**
     * Creates a new {@link DynamoDBPersistentPropertyImpl}
     *
     * @param property
     *            must not be {@literal null}.
     * @param owner
     *            must not be {@literal null}.
     * @param simpleTypeHolder
     *            must not be {@literal null}.
     */

    public DynamoDBPersistentPropertyImpl(@NonNull Property property, @NonNull DynamoDBPersistentEntityImpl<?> owner,
                                          @NonNull SimpleTypeHolder simpleTypeHolder) {
        super(property, owner, simpleTypeHolder);
    }

    @Override
    public boolean isWritable() {
        return !isAnnotationPresent(DynamoDbIgnore.class);
    }

    public boolean isHashKeyProperty() {
        return isAnnotationPresent(DynamoDbPartitionKey.class);
    }

    public boolean isCompositeIdProperty() {
        return isAnnotationPresent(Id.class);
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.mapping.model.AnnotationBasedPersistentProperty #isIdProperty()
     */
    @Override
    public boolean isIdProperty() {

        for (Class<? extends Annotation> annotation : ID_ANNOTATIONS) {
            if (isAnnotationPresent(annotation)) {
                return true;
            }
        }

        return false;
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.mapping.model.AbstractPersistentProperty#isEntity ()
     */
    // @Override

    public boolean isEntity() {

        return isAnnotationPresent(Reference.class);

    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.mapping.model.AnnotationBasedPersistentProperty #isAssociation()
     */
    @Override
    public boolean isAssociation() {

        for (Class<? extends Annotation> annotationType : ASSOCIATION_ANNOTATIONS) {
            if (findAnnotation(annotationType) != null) {
                // No query lookup yet supported ( see
                // Repositories.getPersistentEntity(..) )
                // return !information.isCollectionLike();
                return true;
            }
        }

        return false;
    }

    /*
     * (non-Javadoc)
     * @see org.springframework.data.mapping.model.AnnotationBasedPersistentProperty #isTransient()
     */
    @Override
    public boolean isTransient() {
        return isAnnotationPresent(Transient.class) || super.isTransient() || isAnnotationPresent(DynamoDbIgnore.class);
    }

    // SDK v2 does not have a direct equivalent to DynamoDBVersionAttribute
    // Version tracking is handled via Spring Data's @Version annotation
    // The default implementation from the superclass is used

    /*
     * (non-Javadoc)
     * @see org.springframework.data.mapping.model.AbstractPersistentProperty# createAssociation()
     */
    @NonNull
    @Override
    protected Association<DynamoDBPersistentProperty> createAssociation() {
        return new Association<>(this, null);
    }
}
