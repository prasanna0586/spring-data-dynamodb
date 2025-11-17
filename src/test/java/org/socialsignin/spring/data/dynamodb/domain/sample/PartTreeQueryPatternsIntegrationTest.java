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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests covering query patterns that were compromised during SDK v1 to v2 migration
 * in PartTreeDynamoDBQueryUnitTest. These tests validate:
 * 1. Range-key-only scan queries
 * 2. Scan-based exists queries
 * 3. Custom marshaller integration in queries
 * 4. NOT operator queries
 * 5. Attribute name override in queries
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { PartTreeQueryPatternsIntegrationTest.TestAppConfig.class, DynamoDBLocalResource.class })
@TestPropertySource(properties = { "spring.data.dynamodb.entity2ddl.auto=create" })
public class PartTreeQueryPatternsIntegrationTest {

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample", marshallingMode = org.socialsignin.spring.data.dynamodb.core.MarshallingMode.SDK_V1_COMPATIBLE)
    public static class TestAppConfig {
    }

    @Autowired
    private PlaylistRepository playlistRepository;

    @Autowired
    private UserRepository userRepository;

    private String testUserName;
    private PlaylistId playlist1Id;
    private PlaylistId playlist2Id;
    private PlaylistId playlist3Id;

    private User user1;
    private User user2;
    private User user3;

    @BeforeEach
    public void setUp() {
        // Clean up
        playlistRepository.deleteAll();
        userRepository.deleteAll();

        // Setup test data for Playlist (composite key) tests
        testUserName = "testUser-" + UUID.randomUUID().toString();
        String playlistName1 = "Rock Classics";
        String playlistName2 = "Jazz Favorites";
        String playlistName3 = "Pop Hits";

        playlist1Id = new PlaylistId(testUserName, playlistName1);
        playlist2Id = new PlaylistId(testUserName, playlistName2);
        playlist3Id = new PlaylistId(testUserName, playlistName3);

        Playlist playlist1 = new Playlist(playlist1Id);
        playlist1.setDisplayName("Best Rock Music");
        playlistRepository.save(playlist1);

        Playlist playlist2 = new Playlist(playlist2Id);
        playlist2.setDisplayName("Smooth Jazz");
        playlistRepository.save(playlist2);

        Playlist playlist3 = new Playlist(playlist3Id);
        playlist3.setDisplayName("Top 40 Pop");
        playlistRepository.save(playlist3);

        // Setup test data for User tests
        user1 = new User();
        user1.setName("Alice");
        user1.setPostCode("12345");
        user1.setNumberOfPlaylists(5);
        Date year2020 = new GregorianCalendar(2020, Calendar.JANUARY, 1).getTime();
        user1.setJoinYear(year2020);
        user1 = userRepository.save(user1);

        user2 = new User();
        user2.setName("Bob");
        user2.setPostCode("67890");
        user2.setNumberOfPlaylists(10);
        Date year2021 = new GregorianCalendar(2021, Calendar.JANUARY, 1).getTime();
        user2.setJoinYear(year2021);
        user2 = userRepository.save(user2);

        user3 = new User();
        user3.setName("Charlie");
        user3.setPostCode("12345");
        user3.setNumberOfPlaylists(3);
        Date year2022 = new GregorianCalendar(2022, Calendar.JANUARY, 1).getTime();
        user3.setJoinYear(year2022);
        user3 = userRepository.save(user3);
    }

    /**
     * Test 1: Range-Key-Only Scan Queries
     * Covers: testExecute_WhenFinderMethodIsFindingEntityWithCompositeIdList_WhenFindingByRangeKeyOnly
     * This validates that queries can filter by range key alone (requiring a scan)
     */
    @Test
    public void testFindByRangeKeyOnly_PlaylistName() {
        // Query by range key only (PlaylistName) - this requires a scan
        List<Playlist> foundPlaylists = playlistRepository.findByPlaylistName("Rock Classics");

        assertNotNull(foundPlaylists);
        assertEquals(1, foundPlaylists.size());
        assertEquals(playlist1Id.getPlaylistName(), foundPlaylists.get(0).getPlaylistName());
        assertEquals("Best Rock Music", foundPlaylists.get(0).getDisplayName());
    }

    @Test
    public void testFindByRangeKeyOnly_MultipleResults() {
        // Create another playlist with same name but different hash key
        String anotherUser = "anotherUser-" + UUID.randomUUID().toString();
        PlaylistId duplicateNameId = new PlaylistId(anotherUser, "Rock Classics");
        Playlist duplicatePlaylist = new Playlist(duplicateNameId);
        duplicatePlaylist.setDisplayName("Another Rock Collection");
        playlistRepository.save(duplicatePlaylist);

        // Query by range key only should find both
        List<Playlist> foundPlaylists = playlistRepository.findByPlaylistName("Rock Classics");

        assertNotNull(foundPlaylists);
        assertEquals(2, foundPlaylists.size());

        Set<String> userNames = new HashSet<>();
        for (Playlist p : foundPlaylists) {
            userNames.add(p.getUserName());
            assertEquals("Rock Classics", p.getPlaylistName());
        }
        assertTrue(userNames.contains(testUserName));
        assertTrue(userNames.contains(anotherUser));
    }

    /**
     * Test 2: Scan-Based Exists Queries
     * Covers: testExecute_WhenExistsQueryFindsNoEntity, testExecute_WhenExistsQueryFindsOneEntity,
     *         testExecute_WhenExistsQueryFindsMultipleEntities
     */
    @Test
    public void testExistsByProperty_NotFound() {
        boolean exists = playlistRepository.existsByDisplayName("Nonexistent Display Name");
        assertFalse(exists, "Should return false when no entity matches");
    }

    @Test
    public void testExistsByProperty_FoundOne() {
        boolean exists = playlistRepository.existsByDisplayName("Best Rock Music");
        assertTrue(exists, "Should return true when one entity matches");
    }

    @Test
    public void testExistsByProperty_FoundMultiple() {
        // Create another playlist with the same display name
        String anotherUser = "user2-" + UUID.randomUUID().toString();
        PlaylistId anotherId = new PlaylistId(anotherUser, "Another Playlist");
        Playlist anotherPlaylist = new Playlist(anotherId);
        anotherPlaylist.setDisplayName("Best Rock Music"); // Same display name
        playlistRepository.save(anotherPlaylist);

        boolean exists = playlistRepository.existsByDisplayName("Best Rock Music");
        assertTrue(exists, "Should return true when multiple entities match");
    }

    /**
     * Test 3: Custom Marshaller in Queries
     * Covers: testExecute_WhenFinderMethodIsFindingEntityList_WithSingleDateParameter_WithCustomMarshaller_WhenNotFindingByHashKey
     * The User entity has joinYear field with @DynamoDBTypeConverted(converter = DynamoDBYearMarshaller.class)
     */
    @Test
    public void testFindByCustomMarshalledProperty_JoinYear() {
        Date year2021 = new GregorianCalendar(2021, Calendar.JANUARY, 1).getTime();

        // Query by joinYear which uses custom marshaller (Year2StringAttributeConverter)
        List<User> users = userRepository.findByJoinYear(year2021);

        assertNotNull(users);
        assertEquals(1, users.size());
        assertEquals("Bob", users.get(0).getName());
        assertEquals(year2021, users.get(0).getJoinYear());
    }

    @Test
    public void testFindByCustomMarshalledProperty_MultipleResults() {
        // Create another user with same join year
        User user4 = new User();
        user4.setName("David");
        user4.setPostCode("11111");
        Date year2021 = new GregorianCalendar(2021, Calendar.JANUARY, 1).getTime();
        user4.setJoinYear(year2021);
        userRepository.save(user4);

        List<User> users = userRepository.findByJoinYear(year2021);

        assertNotNull(users);
        assertEquals(2, users.size());

        Set<String> names = new HashSet<>();
        for (User u : users) {
            names.add(u.getName());
            assertEquals(year2021, u.getJoinYear());
        }
        assertTrue(names.contains("Bob"));
        assertTrue(names.contains("David"));
    }

    /**
     * Test 4: NOT Operator Queries
     * Covers: testExecute_WhenFinderMethodIsFindingEntityList_WithSingleStringParameter_WhenNotFindingByNotHashKey,
     *         testExecute_WhenFinderMethodIsFindingEntityList_WithSingleStringParameter_WhenNotFindingByNotAProperty
     */
    @Test
    public void testFindByPropertyNot_Name() {
        // Find all users where name is NOT "Alice"
        List<User> users = userRepository.findByNameNot("Alice");

        assertNotNull(users);
        assertEquals(2, users.size());

        for (User u : users) {
            assertNotEquals("Alice", u.getName());
        }

        Set<String> names = new HashSet<>();
        for (User u : users) {
            names.add(u.getName());
        }
        assertTrue(names.contains("Bob"));
        assertTrue(names.contains("Charlie"));
    }

    @Test
    public void testFindByPropertyNot_PostCode() {
        // Find all users where postCode is NOT "12345"
        List<User> users = userRepository.findByPostCodeNot("12345");

        assertNotNull(users);
        assertEquals(1, users.size());
        assertEquals("Bob", users.get(0).getName());
        assertEquals("67890", users.get(0).getPostCode());
    }

    @Test
    public void testFindByPropertyNot_NoResults() {
        // Find users where name is NOT something that matches all
        List<User> users = userRepository.findByNameNot("NonexistentName");

        assertNotNull(users);
        assertEquals(3, users.size()); // All users should be returned
    }

    /**
     * Test 5: Attribute Name Override in Queries
     * Covers: testExecute_WhenFinderMethodIsFindingEntityWithCompositeIdList_WhenFindingByCompositeIdWithHashKeyOnlyAndByAnotherPropertyWithOverriddenAttributeName
     * The Playlist entity has DisplayName with @DynamoDBAttribute("DisplayName")
     * The User entity has Id with @DynamoDBAttribute("Id")
     */
    @Test
    public void testFindByOverriddenAttributeName_DisplayName() {
        // DisplayName is mapped to "DisplayName" in DynamoDB via @DynamoDBAttribute
        List<Playlist> playlists = playlistRepository.findByDisplayName("Smooth Jazz");

        assertNotNull(playlists);
        assertEquals(1, playlists.size());
        assertEquals("Smooth Jazz", playlists.get(0).getDisplayName());
        assertEquals(playlist2Id.getPlaylistName(), playlists.get(0).getPlaylistName());
    }

    @Test
    public void testFindByHashKeyAndOverriddenAttribute() {
        // Query by hash key (UserName is overridden) and DisplayName (also overridden)
        List<Playlist> playlists = playlistRepository.findByUserNameAndDisplayName(testUserName, "Top 40 Pop");

        assertNotNull(playlists);
        assertEquals(1, playlists.size());
        assertEquals("Top 40 Pop", playlists.get(0).getDisplayName());
        assertEquals(testUserName, playlists.get(0).getUserName());
        assertEquals(playlist3Id.getPlaylistName(), playlists.get(0).getPlaylistName());
    }

    @Test
    public void testFindByOverriddenAttributeName_NoResults() {
        List<Playlist> playlists = playlistRepository.findByDisplayName("Nonexistent Display");

        assertNotNull(playlists);
        assertTrue(playlists.isEmpty());
    }

    /**
     * Test 6: Combined Scenarios - Range Key with Additional Filter
     * Covers: testExecute_WhenFinderMethodIsFindingEntityWithCompositeIdList_WhenFindingByCompositeIdWithRangeKeyOnlyAndByAnotherPropertyWithOverriddenAttributeName
     */
    @Test
    public void testFindByRangeKeyAndOverriddenAttribute() {
        // Query by range key (PlaylistName) and DisplayName (overridden attribute)
        List<Playlist> playlists = playlistRepository.findByPlaylistNameAndDisplayName("Jazz Favorites", "Smooth Jazz");

        assertNotNull(playlists);
        assertEquals(1, playlists.size());
        assertEquals("Jazz Favorites", playlists.get(0).getPlaylistName());
        assertEquals("Smooth Jazz", playlists.get(0).getDisplayName());
    }

    @Test
    public void testFindByRangeKeyAndOverriddenAttribute_NoMatch() {
        // Correct range key but wrong display name
        List<Playlist> playlists = playlistRepository.findByPlaylistNameAndDisplayName("Jazz Favorites", "Wrong Display Name");

        assertNotNull(playlists);
        assertTrue(playlists.isEmpty());
    }
}
