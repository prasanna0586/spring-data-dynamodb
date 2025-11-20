package org.socialsignin.spring.data.dynamodb.callback;

import org.springframework.data.mapping.callback.EntityCallbacks;

/**
 * Implementation of DynamoDBCallbackInvoker that actually invokes the callbacks.
 * This is responsible for calling the right callback at the appropriate time (before or after conversion).
 *
 * NOTES:
 * - `entityCallbacks.callback` is used to trigger the appropriate callback (Before or After) for the entity.
 * - The callback invocations ensure that the entities can be modified before or after conversion.
 */
public class DynamoDBCallbackInvokerImpl implements DynamoDBCallbackInvoker {

    private final EntityCallbacks entityCallbacks;

    public DynamoDBCallbackInvokerImpl(EntityCallbacks entityCallbacks) {
        this.entityCallbacks = entityCallbacks;
    }

    /**
     * Trigger the BeforeConvert callback for the given entity.
     *
     * @param entity The entity to be processed.
     * @param type The type of the entity.
     * @return The processed entity after the BeforeConvert callback.
     */
    @Override
    public <T> T triggerBeforeConvert(T entity, Class<T> type) {
        // Triggers the BeforeConvertCallback for the entity.
        return entityCallbacks.callback(BeforeConvertCallback.class, entity);
    }

    /**
     * Trigger the AfterConvert callback for the given entity.
     *
     * @param entity The entity to be processed.
     * @param type The type of the entity.
     * @return The processed entity after the AfterConvert callback.
     */
    @Override
    public <T> T triggerAfterConvert(T entity, Class<T> type) {
        // Triggers the AfterConvertCallback for the entity.
        return entityCallbacks.callback(AfterConvertCallback.class, entity);
    }
}
