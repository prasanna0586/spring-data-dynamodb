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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BatchWriteRetryConfigTest {

    @Test
    void defaultConfigShouldUseAWSRecommendedValues() {
        BatchWriteRetryConfig config = new BatchWriteRetryConfig();

        assertEquals(8, config.getMaxRetries());
        assertEquals(100L, config.getBaseDelayMs());
        assertEquals(20000L, config.getMaxDelayMs());
        assertTrue(config.isUseJitter());
    }

    @Test
    void exponentialBackoffShouldDoubleDelay() {
        BatchWriteRetryConfig config = new BatchWriteRetryConfig.Builder()
                .useJitter(false) // Disable jitter for predictable testing
                .build();

        // First retry: 100ms
        assertEquals(100L, config.getDelayBeforeRetry(0));

        // Second retry: 200ms
        assertEquals(200L, config.getDelayBeforeRetry(1));

        // Third retry: 400ms
        assertEquals(400L, config.getDelayBeforeRetry(2));

        // Fourth retry: 800ms
        assertEquals(800L, config.getDelayBeforeRetry(3));

        // Fifth retry: 1600ms
        assertEquals(1600L, config.getDelayBeforeRetry(4));
    }

    @Test
    void delayShouldNotExceedMaximum() {
        BatchWriteRetryConfig config = new BatchWriteRetryConfig.Builder()
                .maxDelayMs(5000L)
                .useJitter(false)
                .build();

        // Keep retrying until we hit the cap
        long delay1 = config.getDelayBeforeRetry(5); // 100 * 2^5 = 3200ms
        assertEquals(3200L, delay1);

        long delay2 = config.getDelayBeforeRetry(6); // 100 * 2^6 = 6400ms, capped at 5000ms
        assertEquals(5000L, delay2);

        long delay3 = config.getDelayBeforeRetry(10); // Should still be capped
        assertEquals(5000L, delay3);
    }

    @Test
    void jitterShouldRandomizeDelay() {
        BatchWriteRetryConfig config = new BatchWriteRetryConfig.Builder()
                .baseDelayMs(100L)
                .useJitter(true)
                .build();

        long baseDelay = 100L;
        long delay = config.getDelayBeforeRetry(0);

        // With jitter, delay should be between 50% and 100% of base delay
        assertTrue(delay >= baseDelay / 2, "Delay should be at least 50% of base: " + delay);
        assertTrue(delay <= baseDelay, "Delay should not exceed base: " + delay);
    }

    @Test
    void builderShouldAllowCustomConfiguration() {
        BatchWriteRetryConfig config = new BatchWriteRetryConfig.Builder()
                .maxRetries(5)
                .baseDelayMs(100L)
                .maxDelayMs(10000L)
                .useJitter(false)
                .build();

        assertEquals(5, config.getMaxRetries());
        assertEquals(100L, config.getBaseDelayMs());
        assertEquals(10000L, config.getMaxDelayMs());
        assertFalse(config.isUseJitter());
    }

    @Test
    void disableRetriesShouldSetMaxRetriesToZero() {
        BatchWriteRetryConfig config = new BatchWriteRetryConfig.Builder()
                .disableRetries()
                .build();

        assertEquals(0, config.getMaxRetries());
    }

    @Test
    void shouldRejectInvalidMaxRetries() {
        assertThrows(IllegalArgumentException.class, () ->
                new BatchWriteRetryConfig(-1, 50L, 30000L, true));
    }

    @Test
    void shouldRejectInvalidBaseDelay() {
        assertThrows(IllegalArgumentException.class, () ->
                new BatchWriteRetryConfig(10, 0L, 30000L, true));

        assertThrows(IllegalArgumentException.class, () ->
                new BatchWriteRetryConfig(10, -50L, 30000L, true));
    }

    @Test
    void shouldRejectInvalidMaxDelay() {
        assertThrows(IllegalArgumentException.class, () ->
                new BatchWriteRetryConfig(10, 50L, 25L, true)); // maxDelay < baseDelay
    }

    @Test
    void shouldRejectNegativeRetryCount() {
        BatchWriteRetryConfig config = new BatchWriteRetryConfig();

        assertThrows(IllegalArgumentException.class, () ->
                config.getDelayBeforeRetry(-1));
    }

    @Test
    void toStringShouldIncludeAllSettings() {
        BatchWriteRetryConfig config = new BatchWriteRetryConfig(5, 100L, 5000L, false);
        String str = config.toString();

        assertTrue(str.contains("maxRetries=5"));
        assertTrue(str.contains("baseDelayMs=100"));
        assertTrue(str.contains("maxDelayMs=5000"));
        assertTrue(str.contains("useJitter=false"));
    }

    @Test
    void awsRecommendedExponentialSequence() {
        // Verify the sequence matches AWS SDK for Java 2.x: 100, 200, 400, 800, 1600...
        // With max delay of 20 seconds
        BatchWriteRetryConfig config = new BatchWriteRetryConfig.Builder()
                .useJitter(false)
                .build();

        long[] expectedDelays = {100L, 200L, 400L, 800L, 1600L, 3200L, 6400L, 12800L, 20000L, 20000L};

        for (int i = 0; i < expectedDelays.length; i++) {
            long actual = config.getDelayBeforeRetry(i);
            long expected = Math.min(expectedDelays[i], config.getMaxDelayMs());
            assertEquals(expected, actual,
                    String.format("Retry %d should have delay %dms but got %dms", i, expected, actual));
        }
    }
}
