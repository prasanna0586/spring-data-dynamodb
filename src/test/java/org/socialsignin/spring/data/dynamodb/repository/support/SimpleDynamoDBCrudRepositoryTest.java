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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.socialsignin.spring.data.dynamodb.core.DynamoDBOperations;
import org.socialsignin.spring.data.dynamodb.domain.sample.Playlist;
import org.socialsignin.spring.data.dynamodb.domain.sample.PlaylistId;
import org.socialsignin.spring.data.dynamodb.domain.sample.User;
import org.socialsignin.spring.data.dynamodb.exception.BatchWriteException;
import org.springframework.dao.EmptyResultDataAccessException;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteResult;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SimpleDynamoDBCrudRepository}.
 * @author Prasanna Kumar Ramachandran
 */
@ExtendWith(MockitoExtension.class)
public class SimpleDynamoDBCrudRepositoryTest {

    @Mock
    private PageIterable<User> findAllResultMock;
    @Mock
    private DynamoDBOperations dynamoDBOperations;
    @Mock
    private EnableScanPermissions mockEnableScanPermissions;
    @Mock
    private DynamoDBEntityInformation<User, Long> entityWithSimpleIdInformation;
    @Mock
    private DynamoDBEntityInformation<Playlist, PlaylistId> entityWithCompositeIdInformation;

    private User testUser;
    private Playlist testPlaylist;
    private PlaylistId testPlaylistId;

    private SimpleDynamoDBCrudRepository<User, Long> repoForEntityWithOnlyHashKey;
    private SimpleDynamoDBCrudRepository<Playlist, PlaylistId> repoForEntityWithHashAndRangeKey;

    @BeforeEach
    public void setUp() {

        testUser = new User();

        testPlaylistId = new PlaylistId();
        testPlaylistId.setUserName("michael");
        testPlaylistId.setPlaylistName("playlist1");

        testPlaylist = new Playlist(testPlaylistId);

        when(entityWithSimpleIdInformation.getJavaType()).thenReturn(User.class);

        when(entityWithCompositeIdInformation.getJavaType()).thenReturn(Playlist.class);

        repoForEntityWithOnlyHashKey = new SimpleDynamoDBCrudRepository<>(entityWithSimpleIdInformation,
                dynamoDBOperations, mockEnableScanPermissions);
        repoForEntityWithHashAndRangeKey = new SimpleDynamoDBCrudRepository<>(entityWithCompositeIdInformation,
                dynamoDBOperations, mockEnableScanPermissions);

    }

    @Test
    public void deleteById() {
        final long id = ThreadLocalRandom.current().nextLong();
        User testResult = new User();
        testResult.setId(Long.toString(id));

        when(entityWithSimpleIdInformation.getHashKey(id)).thenReturn(id);
        when(dynamoDBOperations.load(User.class, id)).thenReturn(testResult);

        repoForEntityWithOnlyHashKey.deleteById(id);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        Mockito.verify(dynamoDBOperations).delete(captor.capture());
        assertEquals(Long.toString(id), captor.getValue().getId());
    }

    @Test
    public void deleteEntity() {
        repoForEntityWithOnlyHashKey.delete(testUser);

        verify(dynamoDBOperations).delete(testUser);
    }

    @Test
    public void deleteIterable() {
        List<User> entitiesToDelete = new ArrayList<>();
        entitiesToDelete.add(testUser);

        repoForEntityWithOnlyHashKey.deleteAll(entitiesToDelete);

        verify(dynamoDBOperations).batchDelete(entitiesToDelete);
    }

    @Test
    public void deleteAll() {
        // SDK v2: deleteAll() calls findAll() which calls .items().stream().toList()
        List<User> usersToDelete = new ArrayList<>();
        usersToDelete.add(testUser);

        software.amazon.awssdk.core.pagination.sync.SdkIterable<User> sdkIterable =
            () -> usersToDelete.iterator();

        when(mockEnableScanPermissions.isDeleteAllUnpaginatedScanEnabled()).thenReturn(true);
        when(mockEnableScanPermissions.isFindAllUnpaginatedScanEnabled()).thenReturn(true);
        when(dynamoDBOperations.scan(eq(User.class), any(ScanEnhancedRequest.class))).thenReturn(findAllResultMock);
        when(findAllResultMock.items()).thenReturn(sdkIterable);

        repoForEntityWithOnlyHashKey.deleteAll();

        // Verify batchDelete was called with the list of users (not PageIterable)
        ArgumentCaptor<List<User>> captor = ArgumentCaptor.forClass(List.class);
        verify(dynamoDBOperations).batchDelete(captor.capture());
        assertEquals(usersToDelete.size(), captor.getValue().size());
    }

    @Test
    public void testFindAll() {
        // SDK v2: findAll() calls .items().stream().toList() on the PageIterable
        List<User> expectedUsers = new ArrayList<>();
        expectedUsers.add(testUser);

        software.amazon.awssdk.core.pagination.sync.SdkIterable<User> sdkIterable =
            () -> expectedUsers.iterator();

        when(mockEnableScanPermissions.isFindAllUnpaginatedScanEnabled()).thenReturn(true);
        when(dynamoDBOperations.scan(eq(User.class), any(ScanEnhancedRequest.class))).thenReturn(findAllResultMock);
        when(findAllResultMock.items()).thenReturn(sdkIterable);

        List<User> actual = repoForEntityWithOnlyHashKey.findAll();

        assertEquals(expectedUsers.size(), actual.size());
        assertEquals(testUser, actual.get(0));
    }

    /**
     * /**
     *
     * @see <a href="https://jira.spring.io/browse/DATAJPA-177">DATAJPA-177</a>
     */
    @Test
    public void throwsExceptionIfEntityOnlyHashKeyToDeleteDoesNotExist() {
        assertThrows(EmptyResultDataAccessException.class, () -> {
            repoForEntityWithOnlyHashKey.deleteById(4711L);
        });
    }

    @Test
    public void testEntityDelete() {
        final long id = ThreadLocalRandom.current().nextLong();
        User entity = new User();
        entity.setId(Long.toString(id));

        repoForEntityWithOnlyHashKey.delete(entity);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        Mockito.verify(dynamoDBOperations).delete(captor.capture());
        assertEquals(Long.toString(id), captor.getValue().getId());
    }

    @Test
    public void existsEntityWithOnlyHashKey() {
        when(entityWithSimpleIdInformation.getHashKey(1l)).thenReturn(1l);
        when(dynamoDBOperations.load(User.class, 1l)).thenReturn(null);

        boolean actual = repoForEntityWithOnlyHashKey.existsById(1l);

        assertFalse(actual);
    }

    @Test
    public void testCount() {
        when(mockEnableScanPermissions.isCountUnpaginatedScanEnabled()).thenReturn(true);

        repoForEntityWithOnlyHashKey.count();

        verify(dynamoDBOperations).count(eq(User.class), any(ScanEnhancedRequest.class));
    }

    @Test
    public void findOneEntityWithOnlyHashKey() {
        when(entityWithSimpleIdInformation.getHashKey(1l)).thenReturn(1l);
        when(dynamoDBOperations.load(User.class, 1l)).thenReturn(testUser);

        Optional<User> user = repoForEntityWithOnlyHashKey.findById(1l);
        Mockito.verify(dynamoDBOperations).load(User.class, 1l);
        assertEquals(testUser, user.get());
    }

    @Test
    public void findOneEntityWithHashAndRangeKey() {
        when(entityWithCompositeIdInformation.isRangeKeyAware()).thenReturn(true);
        when(entityWithCompositeIdInformation.getHashKey(testPlaylistId)).thenReturn("michael");
        when(entityWithCompositeIdInformation.getRangeKey(testPlaylistId)).thenReturn("playlist1");
        when(dynamoDBOperations.load(Playlist.class, "michael", "playlist1")).thenReturn(testPlaylist);

        Optional<Playlist> playlist = repoForEntityWithHashAndRangeKey.findById(testPlaylistId);
        assertEquals(testPlaylist, playlist.get());
    }

    @Test
    public void testSave() {
        final long id = ThreadLocalRandom.current().nextLong();
        User entity = new User();
        entity.setId(Long.toString(id));

        repoForEntityWithOnlyHashKey.save(entity);

        verify(dynamoDBOperations).save(entity);
    }

    /**
     * @see <a href="https://jira.spring.io/browse/DATAJPA-177">DATAJPA-177</a>
     */
    @Test
    public void throwsExceptionIfEntityWithHashAndRangeKeyToDeleteDoesNotExist() {
        when(entityWithCompositeIdInformation.isRangeKeyAware()).thenReturn(true);

        assertThrows(EmptyResultDataAccessException.class, () -> {
            PlaylistId playlistId = new PlaylistId();
            playlistId.setUserName("someUser");
            playlistId.setPlaylistName("somePlaylistName");

            repoForEntityWithHashAndRangeKey.deleteById(playlistId);
        });
    }

    @Test
    public void testBatchSave() {

        List<User> entities = new ArrayList<>();
        entities.add(new User());
        entities.add(new User());
        when(dynamoDBOperations.batchSave(anyIterable())).thenReturn(Collections.emptyList());

        repoForEntityWithOnlyHashKey.saveAll(entities);

        verify(dynamoDBOperations).batchSave(anyIterable());
    }

    @Test
    public void testBatchSaveFailure() {
        // SDK v2: BatchWriteResult represents unprocessed items
        // Create mock BatchWriteResult objects to simulate failures
        List<BatchWriteResult> failures = new ArrayList<>();
        BatchWriteResult result1 = Mockito.mock(BatchWriteResult.class);
        failures.add(result1);
        BatchWriteResult result2 = Mockito.mock(BatchWriteResult.class);
        failures.add(result2);

        List<User> entities = new ArrayList<>();
        User user1 = new User();
        User user2 = new User();
        entities.add(user1);
        entities.add(user2);

        // Mock batchSave to return failures
        when(dynamoDBOperations.batchSave(anyIterable())).thenReturn(failures);

        // SDK v2: Must also mock extractUnprocessedPutItems to return the unprocessed entities
        // In SDK v1, batchSave() directly returned FailedBatch objects with exceptions
        // In SDK v2, we must extract unprocessed entities from BatchWriteResult
        when(dynamoDBOperations.extractUnprocessedPutItems(anyList(), anyMap()))
                .thenReturn(Arrays.asList(user1, user2));

        BatchWriteException exception = assertThrows(BatchWriteException.class, () -> {
            repoForEntityWithOnlyHashKey.saveAll(entities);
        });

        assertTrue(exception.getMessage().contains("Batch write operation failed"));
    }
}
