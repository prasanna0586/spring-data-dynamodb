package org.socialsignin.spring.data.dynamodb.callback;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mapping.callback.EntityCallbacks;

/**
 * Configuration class that sets up the DynamoDB callbacks and callback invoker.
 * This configuration ensures that the callbacks are properly registered and invoked during the entity lifecycle.
 *
 * NOTES:
 * - `EntityCallbacks.create(applicationContext)` creates a new `EntityCallbacks` instance that will be used
 *   to automatically pick up the registered callbacks.
 * - `DynamoDBCallbackInvokerImpl` is used to invoke the registered callbacks for the entities.
 */
@Configuration
public class DynamoDBCallbacksConfig {

    /**
     * Bean definition for EntityCallbacks which registers and manages entity callbacks.
     *
     * @param applicationContext The Spring ApplicationContext used to load the callback beans.
     * @return An instance of EntityCallbacks.
     */
    @Bean
    public EntityCallbacks entityCallbacks(ApplicationContext applicationContext) {
        // Creates and returns an instance of EntityCallbacks that is tied to the Spring context.
        return EntityCallbacks.create(applicationContext);
    }

    /**
     * Bean definition for DynamoDBCallbackInvoker which invokes the registered callbacks.
     *
     * @param callbacks The EntityCallbacks instance.
     * @return An instance of DynamoDBCallbackInvoker.
     */
    @Bean
    public DynamoDBCallbackInvoker dynamoDBCallbackInvoker(EntityCallbacks callbacks) {
        // Creates and returns an instance of DynamoDBCallbackInvoker to trigger callbacks during entity lifecycle.
        return new DynamoDBCallbackInvokerImpl(callbacks);
    }
}
