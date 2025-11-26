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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.socialsignin.spring.data.dynamodb.domain.sample.Playlist;
import org.socialsignin.spring.data.dynamodb.domain.sample.PlaylistId;
import org.socialsignin.spring.data.dynamodb.domain.sample.User;
import software.amazon.awssdk.enhanced.dynamodb.AttributeConverter;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unused")
public class DynamoDBIdIsHashKeyEntityInformationImplUnitTest {

    private DynamoDBIdIsHashKeyEntityInformationImpl<Playlist, PlaylistId> dynamoDBPlaylistEntityInformation;

    private DynamoDBIdIsHashKeyEntityInformationImpl<User, String> dynamoDBUserEntityInformation;

    @Mock
    private DynamoDBHashAndRangeKeyExtractingEntityMetadata<Playlist, String> mockPlaylistEntityMetadata;

    @Mock
    private DynamoDBHashKeyExtractingEntityMetadata<User> mockUserEntityMetadata;

    @Mock
    private Object mockHashKey;

    @Mock
    private User mockUserPrototype;

    @Mock
    private Playlist mockPlaylistPrototype;

    @Mock
    private AttributeConverter<Object> mockPropertyMarshaller;

    @BeforeEach
    public void setup() {
        dynamoDBPlaylistEntityInformation = new DynamoDBIdIsHashKeyEntityInformationImpl<>(Playlist.class,
                mockPlaylistEntityMetadata);
        dynamoDBUserEntityInformation = new DynamoDBIdIsHashKeyEntityInformationImpl<>(User.class,
                mockUserEntityMetadata);
    }

    @Test
    public void testGetId_WhenHashKeyTypeSameAsIdType_InvokesHashKeyMethod_AndReturnedIdIsAssignableToIdType_AndIsValueExpected() {
        User user = new User();
        user.setId("someUserId");
        String id = dynamoDBUserEntityInformation.getId(user);
        assertEquals("someUserId", id);

    }

    @Test
    public void testGetId_WhenHashKeyMethodNotSameAsIdType_InvokesHashKeyMethod_AndReturnedIdIsNotAssignableToIdType() {
        assertThrows(ClassCastException.class, () -> {
            Playlist playlist = new Playlist();
            playlist.setUserName("someUserName");
            playlist.setPlaylistName("somePlaylistName");
            PlaylistId id = dynamoDBPlaylistEntityInformation.getId(playlist);
        });
    }

    @Test
    public void testGetHashKeyGivenId_WhenHashKeyTypeSameAsIdType_ReturnsId() {
        Object hashKey = dynamoDBUserEntityInformation.getHashKey("someUserId");
        assertNotNull(hashKey);
        assertEquals("someUserId", hashKey);
    }

    @Test
    public void testGetHashKeyGivenId_WhenHashKeyTypeNotSameAsIdType_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            PlaylistId id = new PlaylistId();
            Object hashKey = dynamoDBPlaylistEntityInformation.getHashKey(id);
        });
    }

    @Test
    public void testGetJavaType_WhenEntityIsInstanceWithHashAndRangeKey_ReturnsEntityClass() {
        assertEquals(Playlist.class, dynamoDBPlaylistEntityInformation.getJavaType());
    }

    @Test
    public void testGetJavaType_WhenEntityIsInstanceWithHashKeyOnly_ReturnsEntityClass() {
        assertEquals(User.class, dynamoDBUserEntityInformation.getJavaType());
    }

    @Test
    public void testGetIdType_WhenEntityIsInstanceWithHashAndRangeKey_ReturnsReturnTypeOfHashKeyMethod() {
        assertEquals(String.class, dynamoDBPlaylistEntityInformation.getIdType());
    }

    @Test
    public void testGetIdType_WhenEntityIsInstanceWithHashKeyOnly_ReturnsReturnTypeOfHashKeyMethod() {
        assertEquals(String.class, dynamoDBUserEntityInformation.getIdType());
    }

    // The following tests ensure that invarient methods such as those always
    // retuning constants, or
    // that delegate to metadata, behave the same irrespective of the setup of the
    // EntityInformation

    @Test
    public void testGetRangeKey_ReturnsNull_IrrespectiveOfEntityInformationSetup() {
        Object userRangeKey = dynamoDBUserEntityInformation.getRangeKey("someUserId");
        assertNull(userRangeKey);

        Object playlistRangeKey = dynamoDBPlaylistEntityInformation.getRangeKey(new PlaylistId());
        assertNull(playlistRangeKey);
    }

    @Test
    public void testIsRangeKeyAware_ReturnsFalse_IrrespectiveOfEntityInformationSetup() {
        assertFalse(dynamoDBUserEntityInformation.isRangeKeyAware());

        assertFalse(dynamoDBPlaylistEntityInformation.isRangeKeyAware());
    }

    @Test
    public void testGetHashKeyPropertyName_DelegatesToEntityMetadata_IrrespectiveOfEntityInformationSetup() {
        Mockito.when(mockUserEntityMetadata.getHashKeyPropertyName()).thenReturn("userHashKeyPropertyName");
        Mockito.when(mockPlaylistEntityMetadata.getHashKeyPropertyName()).thenReturn("playlistHashKeyPropertyName");

        assertEquals("userHashKeyPropertyName", dynamoDBUserEntityInformation.getHashKeyPropertyName());
        assertEquals("playlistHashKeyPropertyName", dynamoDBPlaylistEntityInformation.getHashKeyPropertyName());

    }

    @Test
    public void testGetMarshallerForProperty_DelegatesToEntityMetadata_IrrespectiveOfEntityInformationSetup() {
        Mockito.<AttributeConverter<?>>when(mockUserEntityMetadata.getAttributeConverterForProperty("marshalledProperty"))
                .thenReturn(mockPropertyMarshaller);
        Mockito.<AttributeConverter<?>>when(mockPlaylistEntityMetadata.getAttributeConverterForProperty("marshalledProperty"))
                .thenReturn(mockPropertyMarshaller);

        AttributeConverter<?> marshaller1 = dynamoDBPlaylistEntityInformation
                .getAttributeConverterForProperty("marshalledProperty");
        assertEquals(mockPropertyMarshaller, marshaller1);

        AttributeConverter<?> marshaller2 = dynamoDBUserEntityInformation
                .getAttributeConverterForProperty("marshalledProperty");
        assertEquals(mockPropertyMarshaller, marshaller2);
    }

    @Test
    public void testGetIsHashKeyProperty_DelegatesToEntityMetadata_IrrespectiveOfEntityInformationSetup() {
        Mockito.when(mockUserEntityMetadata.isHashKeyProperty("hashKeyProperty")).thenReturn(true);
        Mockito.when(mockPlaylistEntityMetadata.isHashKeyProperty("nonHashKeyProperty")).thenReturn(false);

        assertTrue(dynamoDBUserEntityInformation.isHashKeyProperty("hashKeyProperty"));
        assertTrue(dynamoDBUserEntityInformation.isHashKeyProperty("hashKeyProperty"));

        assertFalse(dynamoDBPlaylistEntityInformation.isHashKeyProperty("nonHashKeyProperty"));
        assertFalse(dynamoDBPlaylistEntityInformation.isHashKeyProperty("nonHashKeyProperty"));
    }

    @Test
    public void testGetIsCompositeIdProperty_ReturnsFalse_IrrespectiveOfEntityInformationSetup() {
        assertFalse(dynamoDBUserEntityInformation.isCompositeHashAndRangeKeyProperty("compositeIdProperty"));
        assertFalse(dynamoDBUserEntityInformation.isCompositeHashAndRangeKeyProperty("compositeIdProperty"));

        assertFalse(
                dynamoDBPlaylistEntityInformation.isCompositeHashAndRangeKeyProperty("nonCompositeIdProperty"));
        assertFalse(
                dynamoDBPlaylistEntityInformation.isCompositeHashAndRangeKeyProperty("nonCompositeIdProperty"));
    }

    @Test
    public void testGetOverriddenAttributeName_DelegatesToEntityMetadata_IrrespectiveOfEntityInformationSetup() {
        Mockito.when(mockUserEntityMetadata.getOverriddenAttributeName("overriddenProperty"))
                .thenReturn(Optional.of("modifiedPropertyName"));
        Mockito.when(mockPlaylistEntityMetadata.getOverriddenAttributeName("overriddenProperty"))
                .thenReturn(Optional.of("modifiedPropertyName"));

        Optional<String> propertyName1 = dynamoDBUserEntityInformation.getOverriddenAttributeName("overriddenProperty");
        assertEquals(Optional.of("modifiedPropertyName"), propertyName1);

        Optional<String> propertyName2 = dynamoDBPlaylistEntityInformation
                .getOverriddenAttributeName("overriddenProperty");
        assertEquals(Optional.of("modifiedPropertyName"), propertyName2);
    }

}
