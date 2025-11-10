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

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMarshaller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.socialsignin.spring.data.dynamodb.domain.sample.Playlist;
import org.socialsignin.spring.data.dynamodb.domain.sample.PlaylistId;
import org.socialsignin.spring.data.dynamodb.domain.sample.User;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class DynamoDBIdIsHashAndRangeKeyEntityInformationImplUnitTest {

    private DynamoDBIdIsHashAndRangeKeyEntityInformationImpl<Playlist, PlaylistId> dynamoDBPlaylistEntityInformation;

    @Mock
    private DynamoDBHashAndRangeKeyExtractingEntityMetadata<Playlist, PlaylistId> mockPlaylistEntityMetadata;

    @Mock
    private DynamoDBHashAndRangeKeyExtractingEntityMetadata<User, String> mockUserEntityMetadata;

    @Mock
    private Object mockHashKey;

    @Mock
    private Object mockRangeKey;

    @SuppressWarnings("rawtypes")
    @Mock
    private HashAndRangeKeyExtractor mockHashAndRangeKeyExtractor;

    @Mock
    private User mockUserPrototype;

    @Mock
    private Playlist mockPlaylistPrototype;

    @Mock
    private PlaylistId mockPlaylistId;

    @SuppressWarnings("deprecation")
    @Mock
    private DynamoDBMarshaller<Object> mockPropertyMarshaller;

    @SuppressWarnings("unchecked")
    @BeforeEach
    public void setup() {
        dynamoDBPlaylistEntityInformation = new DynamoDBIdIsHashAndRangeKeyEntityInformationImpl<>(Playlist.class,
                mockPlaylistEntityMetadata);
    }

    @Test
    public void testConstruct_WhenEntityDoesNotHaveFieldAnnotatedWithId_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new DynamoDBIdIsHashAndRangeKeyEntityInformationImpl<User, String>(User.class, mockUserEntityMetadata);
        });
    }

    @Test
    public void testGetId_WhenHashKeyMethodSameAsIdType_InvokesHashKeyMethod_AndReturnedIdIsAssignableToIdType_AndIsValueExpected() {
        Playlist playlist = new Playlist();
        playlist.setUserName("someUserName");
        playlist.setPlaylistName("somePlaylistName");
        PlaylistId id = dynamoDBPlaylistEntityInformation.getId(playlist);
        assertNotNull(id);
        assertEquals("someUserName", id.getUserName());
        assertEquals("somePlaylistName", id.getPlaylistName());
    }

    @Test
    public void testGetJavaType_WhenEntityIsInstanceWithHashAndRangeKey_ReturnsEntityClass() {
        assertEquals(Playlist.class, dynamoDBPlaylistEntityInformation.getJavaType());
    }

    @Test
    public void testGetIdType_WhenEntityIsInstanceWithHashAndRangeKey_ReturnsReturnTypeOfIdMethod() {
        assertEquals(PlaylistId.class, dynamoDBPlaylistEntityInformation.getIdType());
    }

    // The following tests ensure that invarient methods such as those always
    // retuning constants, or
    // that delegate to metadata, behave the same irrespective of the setup of the
    // EntityInformation

    @Test
    public void testIsRangeKeyAware_ReturnsTrue() {
        assertTrue(dynamoDBPlaylistEntityInformation.isRangeKeyAware());
    }

    @Test
    public void testGetHashKeyGivenId_WhenIdMethodFoundOnEntity_DelegatesToHashAndRangeKeyExtractorWithGivenIdValue() {
        Mockito.when(mockPlaylistEntityMetadata.getHashAndRangeKeyExtractor(PlaylistId.class))
                .thenReturn(mockHashAndRangeKeyExtractor);
        Mockito.when(mockHashAndRangeKeyExtractor.getHashKey(mockPlaylistId)).thenReturn(mockHashKey);

        // Recreate entity information after stubbing to ensure extractor is properly initialized
        dynamoDBPlaylistEntityInformation = new DynamoDBIdIsHashAndRangeKeyEntityInformationImpl<>(Playlist.class,
                mockPlaylistEntityMetadata);

        Object hashKey = dynamoDBPlaylistEntityInformation.getHashKey(mockPlaylistId);
        assertNotNull(hashKey);
        assertEquals(mockHashKey, hashKey);
    }

    @Test
    public void testGetRangeKeyGivenId_WhenIdMethodFoundOnEntity_DelegatesToHashAndRangeKeyExtractorWithGivenIdValue() {
        Mockito.when(mockPlaylistEntityMetadata.getHashAndRangeKeyExtractor(PlaylistId.class))
                .thenReturn(mockHashAndRangeKeyExtractor);
        Mockito.when(mockHashAndRangeKeyExtractor.getRangeKey(mockPlaylistId)).thenReturn(mockRangeKey);

        // Recreate entity information after stubbing to ensure extractor is properly initialized
        dynamoDBPlaylistEntityInformation = new DynamoDBIdIsHashAndRangeKeyEntityInformationImpl<>(Playlist.class,
                mockPlaylistEntityMetadata);

        Object rangeKey = dynamoDBPlaylistEntityInformation.getRangeKey(mockPlaylistId);
        assertNotNull(rangeKey);
        assertEquals(mockRangeKey, rangeKey);
    }

    @Test
    public void testGetPrototypeEntityForHashKey_DelegatesToDynamoDBEntityMetadata_IrrespectiveOfEntityInformationSetup() {
        Playlist playlistPrototypeEntity = new Playlist();
        Mockito.when(mockPlaylistEntityMetadata.getHashKeyPropotypeEntityForHashKey("someHashKey"))
                .thenReturn(playlistPrototypeEntity);

        Object returnedPlaylistEntity = dynamoDBPlaylistEntityInformation
                .getHashKeyPropotypeEntityForHashKey("someHashKey");

        assertEquals(playlistPrototypeEntity, returnedPlaylistEntity);
        Mockito.verify(mockPlaylistEntityMetadata).getHashKeyPropotypeEntityForHashKey("someHashKey");

    }

    @Test
    public void testGetHashKeyPropertyName_DelegatesToEntityMetadata_IrrespectiveOfEntityInformationSetup() {
        Mockito.when(mockPlaylistEntityMetadata.getHashKeyPropertyName()).thenReturn("playlistHashKeyPropertyName");

        assertEquals("playlistHashKeyPropertyName", dynamoDBPlaylistEntityInformation.getHashKeyPropertyName());

    }

    @Test
    public void testGetHashKeyPrototypeEntityForHashKey_DelegatesToEntityMetadata_IrrespectiveOfEntityInformationSetup() {
        Mockito.when(mockPlaylistEntityMetadata.getHashKeyPropotypeEntityForHashKey("somePlaylistHashKey"))
                .thenReturn(mockPlaylistPrototype);

        Object hashKeyPrototype2 = dynamoDBPlaylistEntityInformation
                .getHashKeyPropotypeEntityForHashKey("somePlaylistHashKey");
        assertEquals(mockPlaylistPrototype, hashKeyPrototype2);
    }

    @Test
    public void testGetMarshallerForProperty_DelegatesToEntityMetadata_IrrespectiveOfEntityInformationSetup() {
        Mockito.when(mockPlaylistEntityMetadata.getMarshallerForProperty("marshalledProperty"))
                .thenReturn(mockPropertyMarshaller);

        @SuppressWarnings("deprecation")
        DynamoDBMarshaller<?> marshaller1 = dynamoDBPlaylistEntityInformation
                .getMarshallerForProperty("marshalledProperty");
        assertEquals(mockPropertyMarshaller, marshaller1);

    }

    @Test
    public void testGetOverriddenAttributeName_DelegatesToEntityMetadata_IrrespectiveOfEntityInformationSetup() {
        Mockito.when(mockPlaylistEntityMetadata.getOverriddenAttributeName("overriddenProperty"))
                .thenReturn(Optional.of("modifiedPropertyName"));

        Optional<String> propertyName2 = dynamoDBPlaylistEntityInformation
                .getOverriddenAttributeName("overriddenProperty");
        assertEquals(Optional.of("modifiedPropertyName"), propertyName2);
    }

    @Test
    public void testGetIsHashKeyProperty_DelegatesToEntityMetadata_IrrespectiveOfEntityInformationSetup() {
        Mockito.when(mockPlaylistEntityMetadata.isHashKeyProperty("nonHashKeyProperty")).thenReturn(false);

        assertFalse(dynamoDBPlaylistEntityInformation.isHashKeyProperty("nonHashKeyProperty"));
        assertFalse(dynamoDBPlaylistEntityInformation.isHashKeyProperty("nonHashKeyProperty"));
    }

    @Test
    public void testGetIsCompositeIdProperty_DelegatesToEntityMetadata_IrrespectiveOfEntityInformationSetup() {
        Mockito.when(mockPlaylistEntityMetadata.isCompositeHashAndRangeKeyProperty("compositeIdProperty"))
                .thenReturn(true);
        Mockito.when(mockPlaylistEntityMetadata.isCompositeHashAndRangeKeyProperty("nonCompositeIdProperty"))
                .thenReturn(false);

        assertTrue(dynamoDBPlaylistEntityInformation.isCompositeHashAndRangeKeyProperty("compositeIdProperty"));
        assertFalse(
                dynamoDBPlaylistEntityInformation.isCompositeHashAndRangeKeyProperty("nonCompositeIdProperty"));
    }

}
