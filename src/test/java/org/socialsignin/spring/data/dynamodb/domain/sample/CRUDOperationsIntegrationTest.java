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
package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.socialsignin.spring.data.dynamodb.utils.DynamoDBLocalResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { DynamoDBLocalResource.class, CRUDOperationsIntegrationTest.TestAppConfig.class })
@TestPropertySource(properties = { "spring.data.dynamodb.entity2ddl.auto=create" })
public class CRUDOperationsIntegrationTest {

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    public static class TestAppConfig {
    }

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserPaginationRepository userPaginationRepository;
    @Autowired
    private PlaylistRepository playlistRepository;

    @BeforeEach
    public void setUp() {
        userRepository.deleteAll();
        userPaginationRepository.deleteAll();
        playlistRepository.deleteAll();
    }

    @Test
    public void testProjection() {
        String postCode = "postCode";
        String user1 = "projection1" + ThreadLocalRandom.current().nextLong();
        String user2 = "projection2" + ThreadLocalRandom.current().nextLong();
        String user3 = "projection2" + ThreadLocalRandom.current().nextLong();

        User u1 = new User();
        u1.setId("Id1" + ThreadLocalRandom.current().nextLong());
        u1.setName(user1);
        u1.setLeaveDate(Instant.now());
        u1.setPostCode(postCode);
        u1.setNumberOfPlaylists(1);

        User u3 = new User();
        u3.setId("Id3" + ThreadLocalRandom.current().nextLong());
        u3.setName(user3);
        u3.setLeaveDate(Instant.now());
        u3.setPostCode(postCode);
        u3.setNumberOfPlaylists(1);

        User u2 = new User();
        u2.setId("Id2" + ThreadLocalRandom.current().nextLong());
        u2.setName(user2);
        u2.setLeaveDate(Instant.now());
        u2.setPostCode(postCode + postCode);
        u2.setNumberOfPlaylists(2);

        userRepository.save(u1);
        userRepository.save(u2);
        userRepository.save(u3);

        List<User> actualList = new ArrayList<>();
        userRepository.findAll().forEach(actualList::add);

        List<User> projectedActuals = userRepository.findByPostCode(postCode, "projection");
        // 2 matches but should be limited to 1 by @Query
        assertEquals(1, projectedActuals.size());
        User projectedActual = projectedActuals.get(0);
        assertNull(projectedActual.getName(), "Attribute not projected");
        assertNull(projectedActual.getPostCode(), "Attribute not projected");
        assertNull(projectedActual.getNumberOfPlaylists(), "Attribute not projected");
        assertNull(projectedActual.getId(), "Key not projected");
        assertNotNull(projectedActual.getLeaveDate(), "LeaveDate is projected");

        List<User> projectedActuals2 = userRepository.findByPostCode(postCode);
        assertEquals(1, projectedActuals2.size());
        User projectedActual2 = projectedActuals2.get(0);
        assertNull(projectedActual2.getName(), "Attribute not projected");
        assertNull(projectedActual2.getPostCode(), "Attribute not projected");
        assertNull(projectedActual2.getNumberOfPlaylists(), "Attribute not projected");
        assertNull(projectedActual2.getId(), "Key not projected");
        assertNotNull(projectedActual2.getLeaveDate(), "LeaveDate is projected");

        List<User> fullActuals = userRepository.findByNameIn(Arrays.asList(user1, user2, user3));
        assertEquals(3, fullActuals.size());
        User fullActual = fullActuals.get(0);
        assertThat(Arrays.asList(user1, user2, user3), hasItems(fullActual.getName()));
        assertThat(Arrays.asList(user1, user2, user3), hasItems(fullActuals.get(1).getName()));
        assertNotNull(fullActual.getPostCode());
        assertNotNull(fullActual.getNumberOfPlaylists());
        assertNotNull(fullActual.getId());
        assertNotNull(fullActual.getLeaveDate());
    }

    @Test
    public void testEmptyResult() throws InterruptedException, ExecutionException {

        Future<User> actual1 = userRepository.findByNameAndPostCode("does not", "exist");
        assertNull(actual1.get());

        User actual2 = userRepository.findByNameAndLeaveDate("does not exist", Instant.now());
        assertNull(actual2);
    }

    @Test
    public void testDelete() {
        // Prepare
        User u1 = new User();
        String name1 = "name1" + ThreadLocalRandom.current().nextLong();
        u1.setName(name1);
        u1.setId("u1");

        User u2 = new User();
        String name2 = "name1" + ThreadLocalRandom.current().nextLong();
        u2.setId("u2");
        u2.setName(name2);

        User u3 = new User();
        String name3 = "name1" + ThreadLocalRandom.current().nextLong();
        u3.setId("u3");
        u3.setName(name3);

        userRepository.save(u1);
        userRepository.save(u2);
        userRepository.save(u3);

        List<User> actualList = new ArrayList<>();
        userRepository.findAll().forEach(actualList::add);
        assertEquals(3, actualList.size(), "Unexpected List: " + actualList);
        actualList.clear();

        userRepository.findByNameIn(Arrays.asList(name1, name2)).forEach(actualList::add);
        assertEquals(2, actualList.size(), "Unexpected List: " + actualList);
        actualList.clear();

        // Delete specific
        userRepository.deleteById("u2");
        userRepository.findAll().forEach(actualList::add);
        assertEquals("u1", actualList.get(0).getId());
        assertEquals("u3", actualList.get(1).getId());

        // Delete conditional
        userRepository.deleteByIdAndName("u1", name1);
        Optional<User> actualUser = userRepository.findById("u1");
        assertFalse(actualUser.isPresent(), "User should have been deleted!");
    }

    @Test
    public void testDeleteNonExistent() {
        // Delete specific - should throw exception
        assertThrows(EmptyResultDataAccessException.class, () -> {
            userRepository.deleteById("non-existent");
        });
    }

    @Test
    public void testDeleteHashRangeKey() {
        // setup
        long rnd = ThreadLocalRandom.current().nextLong();
        Playlist p = new Playlist();
        p.setPlaylistName("playlistName-" + rnd);
        p.setUserName("userName-" + rnd);

        playlistRepository.save(p);

        PlaylistId id = new PlaylistId("userName-" + rnd, "playlistName-" + rnd);
        assertTrue(playlistRepository.findById(id).isPresent(), "Entity with id not found: " + id);

        playlistRepository.deleteById(id);
        assertFalse(playlistRepository.findById(id).isPresent(), "Entity with id not deleted: " + id);
    }

    @Test
    public void testFilterAndPagination() {

        Supplier<User> userSupplier = () -> {
            User u = new User();
            u.setName("test");
            return u;
        };

        for (int i = 0; i < 22; i++) {
            User u = userSupplier.get();
            userPaginationRepository.save(u);
        }
        User u = userSupplier.get();
        u.setName("not-test");
        userPaginationRepository.save(u);

        List<User> allUsers = userPaginationRepository.findAll();
        assertEquals(23, allUsers.size());

        List<User> allTestUsers = userPaginationRepository.findAllByName("test");
        assertEquals(22, allTestUsers.size());

        Pageable firstPage = PageRequest.of(0, 10);
        Page<User> firstResults = userPaginationRepository.findAllByName("test", firstPage);
        assertEquals(10, firstResults.getNumberOfElements());

        Pageable secondPage = PageRequest.of(1, 10);
        Page<User> secondResults = userPaginationRepository.findAllByName("test", secondPage);
        assertEquals(10, secondResults.getNumberOfElements());

        Pageable thirdPage = PageRequest.of(2, 10);
        Page<User> thirdResults = userPaginationRepository.findAllByName("test", thirdPage);
        assertEquals(2, thirdResults.getNumberOfElements());

        Pageable wholePage = Pageable.unpaged();
        Page<User> wholeResults = userPaginationRepository.findAllByName("test", wholePage);
        assertEquals(22, wholeResults.getNumberOfElements());
    }

    @Test
    public void testDeleteNonExistentIdWithCondition() {
        // Delete conditional
        userRepository.deleteByIdAndName("non-existent", "non-existent");
    }

    @Test
    public void testDeleteNonExistingGsiWithCondition() {
        // Delete via GSI
        userRepository.deleteByPostCodeAndNumberOfPlaylists("non-existing", 23);

    }

    @Test
    public void testFilterWithCollections() {
        // Prepare
        User u1 = new User();
        String name1 = "name1" + ThreadLocalRandom.current().nextLong();
        u1.setId("u1");
        u1.setName(name1);
        u1.setPostCode("1234");
        Set<String> u1Tags = new HashSet<>();
        u1Tags.add("tag-a");
        u1Tags.add("tag-b");
        u1Tags.add("tag-c");
        u1.setTags(u1Tags);

        User u2 = new User();
        String name2 = "name1" + ThreadLocalRandom.current().nextLong();
        u2.setId("u2");
        u2.setName(name2);
        u2.setPostCode("1234");
        Set<String> u2Tags = new HashSet<>();
        u2Tags.add("tag-a");
        u2Tags.add("tag-b");
        u2.setTags(u2Tags);

        User u3 = new User();
        String name3 = "name1" + ThreadLocalRandom.current().nextLong();
        u3.setId("u3");
        u3.setName(name3);
        u3.setPostCode("1234");
        Set<String> u3Tags = new HashSet<>();
        u3Tags.add("tag-a");
        u3Tags.add("tag-c");
        u3.setTags(u3Tags);

        u1 = userRepository.save(u1);
        u2 = userRepository.save(u2);
        u3 = userRepository.save(u3);

        Set<User> tagA = setOf(u1, u2, u3);
        Set<User> tagB = setOf(u1, u2);
        Set<User> tagC = setOf(u1, u3);

        Set<User> notTagA = setOf();
        Set<User> notTagB = setOf(u3);
        Set<User> notTagC = setOf(u2);

        // Single value
        assertEquals(tagA, new HashSet<>(userRepository.findAllByTagsContaining("tag-a")));
        assertEquals(tagB, new HashSet<>(userRepository.findAllByTagsContaining("tag-b")));
        assertEquals(tagC, new HashSet<>(userRepository.findAllByTagsContaining("tag-c")));

        assertEquals(tagA, new HashSet<>(userRepository.findAllByTagsContains("tag-a")));
        assertEquals(tagB, new HashSet<>(userRepository.findAllByTagsContains("tag-b")));
        assertEquals(tagC, new HashSet<>(userRepository.findAllByTagsContains("tag-c")));

        assertEquals(tagA, new HashSet<>(userRepository.findAllByTagsIsContaining("tag-a")));
        assertEquals(tagB, new HashSet<>(userRepository.findAllByTagsIsContaining("tag-b")));
        assertEquals(tagC, new HashSet<>(userRepository.findAllByTagsIsContaining("tag-c")));

        assertEquals(notTagA, new HashSet<>(userRepository.findAllByTagsNotContaining("tag-a")));
        assertEquals(notTagB, new HashSet<>(userRepository.findAllByTagsNotContaining("tag-b")));
        assertEquals(notTagC, new HashSet<>(userRepository.findAllByTagsNotContaining("tag-c")));

        assertEquals(notTagA, new HashSet<>(userRepository.findAllByTagsNotContains("tag-a")));
        assertEquals(notTagB, new HashSet<>(userRepository.findAllByTagsNotContains("tag-b")));
        assertEquals(notTagC, new HashSet<>(userRepository.findAllByTagsNotContains("tag-c")));

        assertEquals(notTagA, new HashSet<>(userRepository.findAllByTagsIsNotContaining("tag-a")));
        assertEquals(notTagB, new HashSet<>(userRepository.findAllByTagsIsNotContaining("tag-b")));
        assertEquals(notTagC, new HashSet<>(userRepository.findAllByTagsIsNotContaining("tag-c")));

        // Sets
        assertEquals(tagA, new HashSet<>(userRepository.findAllByTagsContaining(setOf("tag-a"))));
        assertEquals(tagB, new HashSet<>(userRepository.findAllByTagsContaining(setOf("tag-b"))));
        assertEquals(tagC, new HashSet<>(userRepository.findAllByTagsContaining(setOf("tag-c"))));

        assertEquals(tagA, new HashSet<>(userRepository.findAllByTagsContains(setOf("tag-a"))));
        assertEquals(tagB, new HashSet<>(userRepository.findAllByTagsContains(setOf("tag-b"))));
        assertEquals(tagC, new HashSet<>(userRepository.findAllByTagsContains(setOf("tag-c"))));

        assertEquals(tagA, new HashSet<>(userRepository.findAllByTagsIsContaining(setOf("tag-a"))));
        assertEquals(tagB, new HashSet<>(userRepository.findAllByTagsIsContaining(setOf("tag-b"))));
        assertEquals(tagC, new HashSet<>(userRepository.findAllByTagsIsContaining(setOf("tag-c"))));

        assertEquals(notTagA, new HashSet<>(userRepository.findAllByTagsNotContaining(setOf("tag-a"))));
        assertEquals(notTagB, new HashSet<>(userRepository.findAllByTagsNotContaining(setOf("tag-b"))));
        assertEquals(notTagC, new HashSet<>(userRepository.findAllByTagsNotContaining(setOf("tag-c"))));

        assertEquals(notTagA, new HashSet<>(userRepository.findAllByTagsNotContains(setOf("tag-a"))));
        assertEquals(notTagB, new HashSet<>(userRepository.findAllByTagsNotContains(setOf("tag-b"))));
        assertEquals(notTagC, new HashSet<>(userRepository.findAllByTagsNotContains(setOf("tag-c"))));

        assertEquals(notTagA, new HashSet<>(userRepository.findAllByTagsIsNotContaining(setOf("tag-a"))));
        assertEquals(notTagB, new HashSet<>(userRepository.findAllByTagsIsNotContaining(setOf("tag-b"))));
        assertEquals(notTagC, new HashSet<>(userRepository.findAllByTagsIsNotContaining(setOf("tag-c"))));

        // Lists
        assertEquals(tagA, new HashSet<>(userRepository.findAllByTagsContaining(listOf("tag-a"))));
        assertEquals(tagB, new HashSet<>(userRepository.findAllByTagsContaining(listOf("tag-b"))));
        assertEquals(tagC, new HashSet<>(userRepository.findAllByTagsContaining(listOf("tag-c"))));

        assertEquals(tagA, new HashSet<>(userRepository.findAllByTagsContains(listOf("tag-a"))));
        assertEquals(tagB, new HashSet<>(userRepository.findAllByTagsContains(listOf("tag-b"))));
        assertEquals(tagC, new HashSet<>(userRepository.findAllByTagsContains(listOf("tag-c"))));

        assertEquals(tagA, new HashSet<>(userRepository.findAllByTagsIsContaining(listOf("tag-a"))));
        assertEquals(tagB, new HashSet<>(userRepository.findAllByTagsIsContaining(listOf("tag-b"))));
        assertEquals(tagC, new HashSet<>(userRepository.findAllByTagsIsContaining(listOf("tag-c"))));

        assertEquals(notTagA, new HashSet<>(userRepository.findAllByTagsNotContaining(listOf("tag-a"))));
        assertEquals(notTagB, new HashSet<>(userRepository.findAllByTagsNotContaining(listOf("tag-b"))));
        assertEquals(notTagC, new HashSet<>(userRepository.findAllByTagsNotContaining(listOf("tag-c"))));

        assertEquals(notTagA, new HashSet<>(userRepository.findAllByTagsNotContains(listOf("tag-a"))));
        assertEquals(notTagB, new HashSet<>(userRepository.findAllByTagsNotContains(listOf("tag-b"))));
        assertEquals(notTagC, new HashSet<>(userRepository.findAllByTagsNotContains(listOf("tag-c"))));

        assertEquals(notTagA, new HashSet<>(userRepository.findAllByTagsIsNotContaining(listOf("tag-a"))));
        assertEquals(notTagB, new HashSet<>(userRepository.findAllByTagsIsNotContaining(listOf("tag-b"))));
        assertEquals(notTagC, new HashSet<>(userRepository.findAllByTagsIsNotContaining(listOf("tag-c"))));

    }

    @SafeVarargs
    private final <E> Set<E> setOf(E... values) {
        Set<E> result = new HashSet<>();

        if (values != null) {
            result.addAll(Arrays.asList(values));
        }
        return result;
    }

    @SafeVarargs
    private final <E> List<E> listOf(E... values) {
        List<E> result = new ArrayList<>();

        if (values != null) {
            result.addAll(Arrays.asList(values));
        }
        return result;
    }
}
