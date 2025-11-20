package org.socialsignin.spring.data.dynamodb.callback;

import org.springframework.data.mapping.callback.EntityCallback;

/**
 * Callback interface that gets triggered before the entity is converted.
 * This allows for custom actions to be taken before the entity is mapped or persisted.
 *
 * NOTES:
 * - This is a functional interface extending EntityCallback.
 * - It provides a single method `onBeforeConvert` that allows pre-processing on the entity.
 */
@FunctionalInterface
public interface BeforeConvertCallback<T> extends EntityCallback<T> {
    T onBeforeConvert(T entity);
}
