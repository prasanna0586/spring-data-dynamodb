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
import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;
import org.socialsignin.spring.data.dynamodb.domain.sample.Playlist;
import org.socialsignin.spring.data.dynamodb.domain.sample.PlaylistId;
import org.socialsignin.spring.data.dynamodb.domain.sample.User;
import org.springframework.dao.EmptyResultDataAccessException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DynamoDBSimpleIdRepository}.
 *
 * @author Michael Lavelle
 * @author Sebastian Just
 */
@ExtendWith(MockitoExtension.class)
public class SimpleDynamoDBPagingAndSortingRepositoryUnitTest {

    SimpleDynamoDBPagingAndSortingRepository<User, Long> repoForEntityWithOnlyHashKey;

    SimpleDynamoDBPagingAndSortingRepository<Playlist, PlaylistId> repoForEntityWithHashAndRangeKey;

    @Mock
    DynamoDBOperations dynamoDBOperations;

    private User testUser;

    private Playlist testPlaylist;

    private PlaylistId testPlaylistId;

    @Mock
    EnableScanPermissions mockEnableScanPermissions;

    @Mock
    DynamoDBEntityInformation<User, Long> entityWithOnlyHashKeyInformation;

    @Mock
    DynamoDBEntityInformation<Playlist, PlaylistId> entityWithHashAndRangeKeyInformation;

    @BeforeEach
    public void setUp() {

        testUser = new User();

        testPlaylistId = new PlaylistId();
        testPlaylistId.setUserName("michael");
        testPlaylistId.setPlaylistName("playlist1");

        testPlaylist = new Playlist(testPlaylistId);

        when(entityWithOnlyHashKeyInformation.getJavaType()).thenReturn(User.class);

        when(entityWithHashAndRangeKeyInformation.getJavaType()).thenReturn(Playlist.class);

        repoForEntityWithOnlyHashKey = new SimpleDynamoDBPagingAndSortingRepository<>(entityWithOnlyHashKeyInformation,
                dynamoDBOperations, mockEnableScanPermissions);
        repoForEntityWithHashAndRangeKey = new SimpleDynamoDBPagingAndSortingRepository<>(
                entityWithHashAndRangeKeyInformation, dynamoDBOperations, mockEnableScanPermissions);

    }

    /**
     * @see DATAJPA-177
     */
    @Test
    public void throwsExceptionIfEntityWithOnlyHashKeyToDeleteDoesNotExist() {
        assertThrows(EmptyResultDataAccessException.class, () -> {
            repoForEntityWithOnlyHashKey.deleteById(4711L);
        });
    }

    @Test
    public void findOneEntityWithOnlyHashKey() {
        when(entityWithOnlyHashKeyInformation.getHashKey(1l)).thenReturn(1l);
        when(dynamoDBOperations.load(User.class, 1l)).thenReturn(testUser);

        Optional<User> user = repoForEntityWithOnlyHashKey.findById(1l);
        Mockito.verify(dynamoDBOperations).load(User.class, 1l);
        assertEquals(testUser, user.get());
    }

    @Test
    public void findOneEntityWithHashAndRangeKey() {
        when(entityWithHashAndRangeKeyInformation.isRangeKeyAware()).thenReturn(true);
        when(entityWithHashAndRangeKeyInformation.getHashKey(testPlaylistId)).thenReturn("michael");
        when(entityWithHashAndRangeKeyInformation.getRangeKey(testPlaylistId)).thenReturn("playlist1");
        when(dynamoDBOperations.load(Playlist.class, "michael", "playlist1")).thenReturn(testPlaylist);

        Optional<Playlist> playlist = repoForEntityWithHashAndRangeKey.findById(testPlaylistId);
        assertEquals(testPlaylist, playlist.get());
    }

    /**
     * @see DATAJPA-177
     */
    @Test
    public void throwsExceptionIfEntityWithHashAndRangeKeyToDeleteDoesNotExist() {
        when(entityWithHashAndRangeKeyInformation.isRangeKeyAware()).thenReturn(true);

        assertThrows(EmptyResultDataAccessException.class, () -> {
            PlaylistId playlistId = new PlaylistId();
            playlistId.setUserName("someUser");
            playlistId.setPlaylistName("somePlaylistName");

            repoForEntityWithHashAndRangeKey.deleteById(playlistId);
        });
    }
}
