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
package org.socialsignin.spring.data.dynamodb.core;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.util.Random;

/**
 * Configuration for batch write operation retry behavior.
 *
 * AWS recommends using exponential backoff when retrying batch operations with unprocessed items.
 * This configuration allows customization of the retry strategy while providing sensible defaults
 * based on AWS best practices.
 *
 * @see <a href="https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/Programming.Errors.html">
 *      AWS DynamoDB Error Handling</a>
 */
public class BatchWriteRetryConfig {

    /**
     * Default maximum number of retry attempts for batch operations.
     * DynamoDB clients use a default maximum retry count of 8 (AWS SDK for Java 2.x).
     */
    public static final int DEFAULT_MAX_RETRIES = 8;

    /**
     * Default base delay in milliseconds before the first retry.
     * AWS SDK for Java 2.x uses 100ms for non-throttling exceptions.
     * Unprocessed items in batch operations are typically throttling-related,
     * but we use 100ms as a conservative starting point.
     */
    public static final long DEFAULT_BASE_DELAY_MS = 100L;

    /**
     * Default maximum delay in milliseconds between retries.
     * AWS SDK for Java 2.x uses 20 seconds as the maximum delay.
     */
    public static final long DEFAULT_MAX_DELAY_MS = 20000L; // 20 seconds

    /**
     * Default setting for jitter.
     * AWS recommends using jitter to prevent thundering herd problem.
     */
    public static final boolean DEFAULT_USE_JITTER = true;

    private final int maxRetries;
    private final long baseDelayMs;
    private final long maxDelayMs;
    private final boolean useJitter;
    @Nullable
    private final Random random;

    /**
     * Creates a default retry configuration with AWS SDK for Java 2.x settings:
     * - Max retries: 8 (DynamoDB default)
     * - Base delay: 100ms (doubles with each retry: 100, 200, 400, 800, 1600...)
     * - Max delay: 20 seconds
     * - Jitter enabled
     */
    public BatchWriteRetryConfig() {
        this(DEFAULT_MAX_RETRIES, DEFAULT_BASE_DELAY_MS, DEFAULT_MAX_DELAY_MS, DEFAULT_USE_JITTER);
    }

    /**
     * Creates a custom retry configuration.
     *
     * @param maxRetries Maximum number of retry attempts (must be >= 0)
     * @param baseDelayMs Base delay in milliseconds before first retry (must be > 0)
     * @param maxDelayMs Maximum delay in milliseconds between retries (must be >= baseDelayMs)
     * @param useJitter Whether to add random jitter to delays
     */
    public BatchWriteRetryConfig(int maxRetries, long baseDelayMs, long maxDelayMs, boolean useJitter) {
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0");
        }
        if (baseDelayMs <= 0) {
            throw new IllegalArgumentException("baseDelayMs must be > 0");
        }
        if (maxDelayMs < baseDelayMs) {
            throw new IllegalArgumentException("maxDelayMs must be >= baseDelayMs");
        }

        this.maxRetries = maxRetries;
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.useJitter = useJitter;
        this.random = useJitter ? new Random() : null;
    }

    /**
     * Calculates the delay before the next retry using exponential backoff.
     *
     * Formula: min(baseDelay * 2^retryCount, maxDelay)
     * With jitter: delay * (0.5 + random(0, 0.5))
     *
     * @param retryCount The current retry attempt (0-based)
     * @return Delay in milliseconds before the next retry
     */
    public long getDelayBeforeRetry(int retryCount) {
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must be >= 0");
        }

        // Calculate exponential backoff: baseDelay * 2^retryCount
        long delay = baseDelayMs;
        for (int i = 0; i < retryCount; i++) {
            delay *= 2;
            if (delay >= maxDelayMs) {
                delay = maxDelayMs;
                break;
            }
        }

        // Cap at maximum delay
        delay = Math.min(delay, maxDelayMs);

        // Add jitter if enabled (randomize between 50% and 100% of calculated delay)
        if (useJitter && random != null) {
            double jitterFactor = 0.5 + (random.nextDouble() * 0.5);
            delay = (long) (delay * jitterFactor);
        }

        return delay;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public long getBaseDelayMs() {
        return baseDelayMs;
    }

    public long getMaxDelayMs() {
        return maxDelayMs;
    }

    public boolean isUseJitter() {
        return useJitter;
    }

    /**
     * Builder for creating custom BatchWriteRetryConfig instances.
     */
    public static class Builder {
        private int maxRetries = DEFAULT_MAX_RETRIES;
        private long baseDelayMs = DEFAULT_BASE_DELAY_MS;
        private long maxDelayMs = DEFAULT_MAX_DELAY_MS;
        private boolean useJitter = DEFAULT_USE_JITTER;

        @NonNull
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        @NonNull
        public Builder baseDelayMs(long baseDelayMs) {
            this.baseDelayMs = baseDelayMs;
            return this;
        }

        @NonNull
        public Builder maxDelayMs(long maxDelayMs) {
            this.maxDelayMs = maxDelayMs;
            return this;
        }

        @NonNull
        public Builder useJitter(boolean useJitter) {
            this.useJitter = useJitter;
            return this;
        }

        /**
         * Disables retry logic completely (maxRetries = 0).
         */
        @NonNull
        public Builder disableRetries() {
            this.maxRetries = 0;
            return this;
        }

        @NonNull
        public BatchWriteRetryConfig build() {
            return new BatchWriteRetryConfig(maxRetries, baseDelayMs, maxDelayMs, useJitter);
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "BatchWriteRetryConfig{" +
                "maxRetries=" + maxRetries +
                ", baseDelayMs=" + baseDelayMs +
                ", maxDelayMs=" + maxDelayMs +
                ", useJitter=" + useJitter +
                '}';
    }
}
