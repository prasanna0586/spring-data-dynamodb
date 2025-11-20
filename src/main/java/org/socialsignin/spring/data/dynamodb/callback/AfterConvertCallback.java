package org.socialsignin.spring.data.dynamodb.callback;

import org.springframework.data.mapping.callback.EntityCallback;

/**
 * Callback interface that gets triggered after the entity has been converted.
 * This allows for custom actions to be taken after the entity is mapped to its final form.
 *
 * NOTES:
 * - This is a functional interface extending EntityCallback.
 * - It provides a single method `onAfterConvert` that allows post-processing on the entity.
 */
@FunctionalInterface
public interface AfterConvertCallback<T> extends EntityCallback<T> {
    T onAfterConvert(T entity);
}