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
package org.socialsignin.spring.data.dynamodb.repository.support;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.socialsignin.spring.data.dynamodb.domain.sample.PlaylistId;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SDK v2 Migration Notes:
 * - SDK v1: @DynamoDBHashKey → SDK v2: @DynamoDbPartitionKey
 * - SDK v1: @DynamoDBRangeKey → SDK v2: @DynamoDbSortKey
 * - The DynamoDBHashAndRangeKeyMethodExtractor interface remains unchanged
 * - Test inner classes updated to use SDK v2 annotations
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unused")
public class DynamoDBHashAndRangeKeyMethodExtractorImplUnitTest {

    private DynamoDBHashAndRangeKeyMethodExtractor<PlaylistId> playlistIdMetadata;
    private DynamoDBHashAndRangeKeyMethodExtractor<IdClassWithNoAnnotatedMethods> idClassWithNoHashOrRangeKeyMethodMetadata;
    private DynamoDBHashAndRangeKeyMethodExtractor<IdClassWithOnlyAnnotatedHashKeyMethod> idClassWithOnlyHashKeyMethodMetadata;
    private DynamoDBHashAndRangeKeyMethodExtractor<IdClassWithOnlyAnnotatedRangeKeyMethod> idClassWithOnlyRangeKeyMethodMetadata;
    private DynamoDBHashAndRangeKeyMethodExtractor<IdClassWithMulitpleAnnotatedHashKeyMethods> idClassWitMultipleAnnotatedHashKeysMetadata;
    private DynamoDBHashAndRangeKeyMethodExtractor<IdClassWithMulitpleAnnotatedRangeKeyMethods> idClassWitMultipleAnnotatedRangeKeysMetadata;

    @Test
    public void testConstruct_WhenHashKeyMethodExists_WhenRangeKeyMethodExists() {
        playlistIdMetadata = new DynamoDBHashAndRangeKeyMethodExtractorImpl<PlaylistId>(PlaylistId.class);
        Method hashKeyMethod = playlistIdMetadata.getHashKeyMethod();
        assertNotNull(hashKeyMethod);
        assertEquals("getUserName", hashKeyMethod.getName());
        Method rangeKeyMethod = playlistIdMetadata.getRangeKeyMethod();
        assertNotNull(rangeKeyMethod);
        assertEquals("getPlaylistName", rangeKeyMethod.getName());

        assertEquals(PlaylistId.class, playlistIdMetadata.getJavaType());

    }

    @Test
    public void testConstruct_WhenHashKeyMethodExists_WhenRangeKeyMethodDoesNotExist() {
        assertThrows(IllegalArgumentException.class, () -> {
            idClassWithOnlyHashKeyMethodMetadata = new DynamoDBHashAndRangeKeyMethodExtractorImpl<IdClassWithOnlyAnnotatedHashKeyMethod>(
                    IdClassWithOnlyAnnotatedHashKeyMethod.class);
        });
    }

    @Test
    public void testConstruct_WhenHashKeyMethodDoesNotExist_WhenRangeKeyMethodExists() {
        assertThrows(IllegalArgumentException.class, () -> {
            idClassWithOnlyRangeKeyMethodMetadata = new DynamoDBHashAndRangeKeyMethodExtractorImpl<IdClassWithOnlyAnnotatedRangeKeyMethod>(
                    IdClassWithOnlyAnnotatedRangeKeyMethod.class);
        });
    }

    @Test
    public void testConstruct_WhenMultipleHashKeyMethodsExist() {
        assertThrows(IllegalArgumentException.class, () -> {
            idClassWitMultipleAnnotatedHashKeysMetadata = new DynamoDBHashAndRangeKeyMethodExtractorImpl<IdClassWithMulitpleAnnotatedHashKeyMethods>(
                    IdClassWithMulitpleAnnotatedHashKeyMethods.class);
        });
    }

    @Test
    public void testGetConstruct_WhenMultipleRangeKeyMethodsExist() {
        assertThrows(IllegalArgumentException.class, () -> {
            idClassWitMultipleAnnotatedRangeKeysMetadata = new DynamoDBHashAndRangeKeyMethodExtractorImpl<IdClassWithMulitpleAnnotatedRangeKeyMethods>(
                    IdClassWithMulitpleAnnotatedRangeKeyMethods.class);
        });
    }

    @Test
    public void testConstruct_WhenNeitherHashKeyOrRangeKeyMethodExist() {
        assertThrows(IllegalArgumentException.class, () -> {
            idClassWithNoHashOrRangeKeyMethodMetadata = new DynamoDBHashAndRangeKeyMethodExtractorImpl<IdClassWithNoAnnotatedMethods>(
                    IdClassWithNoAnnotatedMethods.class);
        });
    }

    private class IdClassWithNoAnnotatedMethods {

        public String getHashKey() {
            return null;
        }

        public String getRangeKey() {
            return null;
        }

    }

    private class IdClassWithOnlyAnnotatedHashKeyMethod {

        @DynamoDbPartitionKey
        public String getHashKey() {
            return null;
        }

        public String getRangeKey() {
            return null;
        }

    }

    private class IdClassWithOnlyAnnotatedRangeKeyMethod {

        public String getHashKey() {
            return null;
        }

        @DynamoDbSortKey
        public String getRangeKey() {
            return null;
        }

    }

    private class IdClassWithMulitpleAnnotatedHashKeyMethods {

        @DynamoDbPartitionKey
        public String getHashKey() {
            return null;
        }

        @DynamoDbPartitionKey
        public String getOtherHashKey() {
            return null;
        }

        @DynamoDbSortKey
        public String getRangeKey() {
            return null;
        }

    }

    private class IdClassWithMulitpleAnnotatedRangeKeyMethods {

        @DynamoDbPartitionKey
        public String getHashKey() {
            return null;
        }

        @DynamoDbSortKey
        public String getOtherRangeKey() {
            return null;
        }

        @DynamoDbSortKey
        public String getRangeKey() {
            return null;
        }

    }

}
