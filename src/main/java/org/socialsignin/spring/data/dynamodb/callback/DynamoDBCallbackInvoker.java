package org.socialsignin.spring.data.dynamodb.callback;

/**
 * Interface that defines the methods for triggering DynamoDB callbacks.
 * This acts as an invoker to trigger both BeforeConvert and AfterConvert callbacks.
 *
 * NOTES:
 * - This interface defines methods to trigger callbacks before and after conversion.
 * - It abstracts the callback invocation process, making it easier to integrate with the entity lifecycle.
 */
public interface DynamoDBCallbackInvoker {

    <T> T triggerBeforeConvert(T entity, Class<T> type);

    <T> T triggerAfterConvert(T entity, Class<T> type);

}
