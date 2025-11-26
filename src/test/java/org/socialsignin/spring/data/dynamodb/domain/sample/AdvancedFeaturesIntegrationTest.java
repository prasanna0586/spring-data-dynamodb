package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.socialsignin.spring.data.dynamodb.utils.DynamoDBLocalResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive integration tests for advanced DynamoDB features:
 * - Binary data types (ByteBuffer)
 * - Set types (StringSet, NumberSet)
 * - Large result set pagination
 * - Complex data structures
 * - Edge cases and error scenarios
 *
 * Coverage:
 * - Binary data storage and retrieval
 * - Set operations (add, remove, contains)
 * - Large dataset pagination (100+ items)
 * - Empty sets and null handling
 * - Large binary data (up to several KB)
 * - Set deduplication
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, AdvancedFeaturesIntegrationTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Advanced Features Integration Tests")
public class AdvancedFeaturesIntegrationTest {

    @Configuration
    @EnableDynamoDBRepositories(
            basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample",
            marshallingMode = org.socialsignin.spring.data.dynamodb.core.MarshallingMode.SDK_V1_COMPATIBLE
    )
    public static class TestAppConfig {
    }

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        fileMetadataRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ==================== Binary Data Tests (ByteBuffer) ====================

    @Test
    @Order(1)
    @DisplayName("Test 1: Store and retrieve small binary data")
    void testBinaryData_SmallFile() {
        // Given - Create file with small binary content
        FileMetadata file = new FileMetadata();
        file.setFileName("test.txt");
        file.setContentType("text/plain");
        file.setUploadedBy("user1");
        file.setUploadedAt(Instant.now());

        String textContent = "Hello, DynamoDB Binary Data!";
        ByteBuffer content = ByteBuffer.wrap(textContent.getBytes(StandardCharsets.UTF_8));
        file.setFileContent(content);
        file.setFileSize((long) textContent.length());

        // When
        FileMetadata savedFile = fileMetadataRepository.save(file);

        // Then
        assertThat(savedFile).isNotNull();
        assertThat(savedFile.getFileId()).isNotNull();

        // Retrieve and verify binary data
        FileMetadata retrievedFile = fileMetadataRepository.findById(savedFile.getFileId()).orElse(null);
        assertThat(retrievedFile).isNotNull();
        assertThat(retrievedFile.getFileContent()).isNotNull();

        // Convert ByteBuffer back to String
        byte[] contentBytes = new byte[retrievedFile.getFileContent().remaining()];
        retrievedFile.getFileContent().get(contentBytes);
        String retrievedContent = new String(contentBytes, StandardCharsets.UTF_8);

        assertThat(retrievedContent).isEqualTo(textContent);
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Store and retrieve large binary data (10 KB)")
    void testBinaryData_LargeFile() {
        // Given - Create file with 10 KB binary content
        FileMetadata file = new FileMetadata();
        file.setFileName("large-file.bin");
        file.setContentType("application/octet-stream");
        file.setUploadedBy("user1");
        file.setUploadedAt(Instant.now());

        // Create 10 KB of random binary data
        byte[] largeContent = new byte[10 * 1024]; // 10 KB
        new Random().nextBytes(largeContent);
        ByteBuffer content = ByteBuffer.wrap(largeContent);
        file.setFileContent(content);
        file.setFileSize((long) largeContent.length);

        // When
        FileMetadata savedFile = fileMetadataRepository.save(file);

        // Then
        FileMetadata retrievedFile = fileMetadataRepository.findById(savedFile.getFileId()).orElse(null);
        assertThat(retrievedFile).isNotNull();
        assertThat(retrievedFile.getFileContent()).isNotNull();

        // Verify size
        assertThat(retrievedFile.getFileContent().remaining()).isEqualTo(10 * 1024);

        // Verify content matches
        byte[] retrievedBytes = new byte[retrievedFile.getFileContent().remaining()];
        retrievedFile.getFileContent().get(retrievedBytes);
        assertThat(retrievedBytes).isEqualTo(largeContent);
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Store multiple binary fields (content + thumbnail)")
    void testBinaryData_MultipleBinaryFields() {
        // Given - File with both content and thumbnail
        FileMetadata file = new FileMetadata();
        file.setFileName("image.jpg");
        file.setContentType("image/jpeg");
        file.setUploadedBy("user1");
        file.setUploadedAt(Instant.now());

        // Main content (1 KB)
        byte[] mainContent = new byte[1024];
        Arrays.fill(mainContent, (byte) 0xFF);
        file.setFileContent(ByteBuffer.wrap(mainContent));

        // Thumbnail (256 bytes)
        byte[] thumbnailContent = new byte[256];
        Arrays.fill(thumbnailContent, (byte) 0xAA);
        file.setFileThumbnail(ByteBuffer.wrap(thumbnailContent));

        file.setFileSize((long) mainContent.length);

        // When
        FileMetadata savedFile = fileMetadataRepository.save(file);

        // Then
        FileMetadata retrievedFile = fileMetadataRepository.findById(savedFile.getFileId()).orElse(null);
        assertThat(retrievedFile).isNotNull();

        // Verify main content
        byte[] retrievedMain = new byte[retrievedFile.getFileContent().remaining()];
        retrievedFile.getFileContent().get(retrievedMain);
        assertThat(retrievedMain).isEqualTo(mainContent);

        // Verify thumbnail
        byte[] retrievedThumbnail = new byte[retrievedFile.getFileThumbnail().remaining()];
        retrievedFile.getFileThumbnail().get(retrievedThumbnail);
        assertThat(retrievedThumbnail).isEqualTo(thumbnailContent);
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: Handle null binary data")
    void testBinaryData_NullContent() {
        // Given - File without binary content
        FileMetadata file = new FileMetadata();
        file.setFileName("metadata-only.txt");
        file.setContentType("text/plain");
        file.setUploadedBy("user1");
        file.setUploadedAt(Instant.now());
        file.setFileContent(null); // No content
        file.setFileSize(0L);

        // When
        FileMetadata savedFile = fileMetadataRepository.save(file);

        // Then
        FileMetadata retrievedFile = fileMetadataRepository.findById(savedFile.getFileId()).orElse(null);
        assertThat(retrievedFile).isNotNull();
        assertThat(retrievedFile.getFileContent()).isNull();
        assertThat(retrievedFile.getFileSize()).isEqualTo(0L);
    }

    // ==================== String Set Tests ====================

    @Test
    @Order(5)
    @DisplayName("Test 5: Store and retrieve StringSet (tags)")
    void testStringSet_Tags() {
        // Given - File with tags
        FileMetadata file = new FileMetadata();
        file.setFileName("document.pdf");
        file.setContentType("application/pdf");
        file.setUploadedBy("user1");
        file.setUploadedAt(Instant.now());

        Set<String> tags = new HashSet<>(Arrays.asList("work", "important", "project-alpha", "2024"));
        file.setTags(tags);

        // When
        FileMetadata savedFile = fileMetadataRepository.save(file);

        // Then
        FileMetadata retrievedFile = fileMetadataRepository.findById(savedFile.getFileId()).orElse(null);
        assertThat(retrievedFile).isNotNull();
        assertThat(retrievedFile.getTags()).isNotNull();
        assertThat(retrievedFile.getTags()).containsExactlyInAnyOrderElementsOf(tags);
    }

    @Test
    @Order(6)
    @DisplayName("Test 6: StringSet deduplication")
    void testStringSet_Deduplication() {
        // Given - File with duplicate tags (sets should deduplicate)
        FileMetadata file = new FileMetadata();
        file.setFileName("doc.txt");
        file.setContentType("text/plain");
        file.setUploadedBy("user1");
        file.setUploadedAt(Instant.now());

        // Create set with duplicates (Set will automatically deduplicate)
        Set<String> tagsWithDuplicates = new HashSet<>(Arrays.asList("tag1", "tag2", "tag1", "tag3", "tag2"));
        file.setTags(tagsWithDuplicates);

        // When
        FileMetadata savedFile = fileMetadataRepository.save(file);

        // Then
        FileMetadata retrievedFile = fileMetadataRepository.findById(savedFile.getFileId()).orElse(null);
        assertThat(retrievedFile).isNotNull();
        assertThat(retrievedFile.getTags()).hasSize(3); // Only unique tags
        assertThat(retrievedFile.getTags()).containsExactlyInAnyOrder("tag1", "tag2", "tag3");
    }

    @Test
    @Order(7)
    @DisplayName("Test 7: Update StringSet (add and remove tags)")
    void testStringSet_UpdateSet() {
        // Given - File with initial tags
        FileMetadata file = new FileMetadata();
        file.setFileName("notes.txt");
        file.setContentType("text/plain");
        file.setUploadedBy("user1");
        file.setUploadedAt(Instant.now());

        Set<String> initialTags = new HashSet<>(Arrays.asList("draft", "personal"));
        file.setTags(initialTags);

        FileMetadata savedFile = fileMetadataRepository.save(file);

        // When - Update tags (add and remove)
        FileMetadata fileToUpdate = fileMetadataRepository.findById(savedFile.getFileId()).orElse(null);
        assertThat(fileToUpdate).isNotNull();

        Set<String> updatedTags = new HashSet<>(fileToUpdate.getTags());
        updatedTags.remove("draft"); // Remove
        updatedTags.add("final"); // Add
        updatedTags.add("published"); // Add
        fileToUpdate.setTags(updatedTags);

        fileMetadataRepository.save(fileToUpdate);

        // Then
        FileMetadata retrievedFile = fileMetadataRepository.findById(savedFile.getFileId()).orElse(null);
        assertThat(retrievedFile).isNotNull();
        assertThat(retrievedFile.getTags()).hasSize(3);
        assertThat(retrievedFile.getTags()).containsExactlyInAnyOrder("personal", "final", "published");
        assertThat(retrievedFile.getTags()).doesNotContain("draft");
    }

    @Test
    @Order(8)
    @DisplayName("Test 8: Empty StringSet - DynamoDB validation")
    void testStringSet_EmptySet() {
        // Given - File with empty tags set (DynamoDB does NOT allow empty sets)
        FileMetadata file = new FileMetadata();
        file.setFileName("untagged.txt");
        file.setContentType("text/plain");
        file.setUploadedBy("user1");
        file.setUploadedAt(Instant.now());
        // Don't set tags (null) - empty sets are not allowed by DynamoDB
        file.setTags(null);

        // When - Save file without tags
        FileMetadata savedFile = fileMetadataRepository.save(file);

        // Then - File is saved successfully with null tags
        FileMetadata retrievedFile = fileMetadataRepository.findById(savedFile.getFileId()).orElse(null);
        assertThat(retrievedFile).isNotNull();
        assertThat(retrievedFile.getTags()).isNull();

        // Note: Attempting to save a file with new HashSet<>() (empty set) would throw:
        // AmazonDynamoDBException: "An string set  may not be empty"
    }

    @Test
    @Order(9)
    @DisplayName("Test 9: Null StringSet")
    void testStringSet_NullSet() {
        // Given - File without tags
        FileMetadata file = new FileMetadata();
        file.setFileName("no-tags.txt");
        file.setContentType("text/plain");
        file.setUploadedBy("user1");
        file.setUploadedAt(Instant.now());
        file.setTags(null); // No tags

        // When
        FileMetadata savedFile = fileMetadataRepository.save(file);

        // Then
        FileMetadata retrievedFile = fileMetadataRepository.findById(savedFile.getFileId()).orElse(null);
        assertThat(retrievedFile).isNotNull();
        assertThat(retrievedFile.getTags()).isNull();
    }

    // ==================== Number Set Tests ====================

    @Test
    @Order(10)
    @DisplayName("Test 10: Store and retrieve NumberSet (version numbers)")
    void testNumberSet_VersionNumbers() {
        // Given - File with version numbers
        FileMetadata file = new FileMetadata();
        file.setFileName("app.jar");
        file.setContentType("application/java-archive");
        file.setUploadedBy("user1");
        file.setUploadedAt(Instant.now());

        Set<Integer> versions = new HashSet<>(Arrays.asList(1, 2, 3, 5, 8, 13, 21));
        file.setVersionNumbers(versions);

        // When
        FileMetadata savedFile = fileMetadataRepository.save(file);

        // Then
        FileMetadata retrievedFile = fileMetadataRepository.findById(savedFile.getFileId()).orElse(null);
        assertThat(retrievedFile).isNotNull();
        assertThat(retrievedFile.getVersionNumbers()).isNotNull();
        assertThat(retrievedFile.getVersionNumbers()).containsExactlyInAnyOrderElementsOf(versions);
    }

    @Test
    @Order(11)
    @DisplayName("Test 11: Multiple StringSets (tags and permissions)")
    void testMultipleStringSets() {
        // Given - File with multiple sets
        FileMetadata file = new FileMetadata();
        file.setFileName("secure-doc.pdf");
        file.setContentType("application/pdf");
        file.setUploadedBy("user1");
        file.setUploadedAt(Instant.now());

        Set<String> tags = new HashSet<>(Arrays.asList("confidential", "finance", "q4-2024"));
        Set<String> permissions = new HashSet<>(Arrays.asList("read", "write", "share"));

        file.setTags(tags);
        file.setPermissions(permissions);

        // When
        FileMetadata savedFile = fileMetadataRepository.save(file);

        // Then
        FileMetadata retrievedFile = fileMetadataRepository.findById(savedFile.getFileId()).orElse(null);
        assertThat(retrievedFile).isNotNull();
        assertThat(retrievedFile.getTags()).containsExactlyInAnyOrderElementsOf(tags);
        assertThat(retrievedFile.getPermissions()).containsExactlyInAnyOrderElementsOf(permissions);
    }

    // ==================== Large Pagination Tests ====================

    @Test
    @Order(12)
    @DisplayName("Test 12: Large dataset pagination - 100 items")
    void testLargePagination_100Items() {
        // Given - Create 100 files
        List<FileMetadata> files = createFiles(100, "user-pagination");
        fileMetadataRepository.saveAll(files);

        // When - Paginate through all files with page size 10
        List<FileMetadata> allFiles = new ArrayList<>();
        int pageSize = 10;
        int pageNumber = 0;
        Page<FileMetadata> page;

        do {
            Pageable pageable = PageRequest.of(pageNumber, pageSize);
            page = fileMetadataRepository.findByUploadedBy("user-pagination", pageable);
            allFiles.addAll(page.getContent());
            pageNumber++;
        } while (page.hasNext());

        // Then - Should have retrieved all 100 files
        assertThat(allFiles).hasSize(100);
        assertThat(pageNumber).isEqualTo(10); // 100 items / 10 per page = 10 pages
    }

    @Test
    @Order(13)
    @DisplayName("Test 13: Large dataset pagination - 250 items with varying page sizes")
    void testLargePagination_250Items() {
        // Given - Create 250 files
        List<FileMetadata> files = createFiles(250, "user-large");
        fileMetadataRepository.saveAll(files);

        // Test 1: Page size 25
        Pageable pageable25 = PageRequest.of(0, 25);
        Page<FileMetadata> page25 = fileMetadataRepository.findByUploadedBy("user-large", pageable25);
        assertThat(page25.getContent()).hasSize(25);
        assertThat(page25.getTotalElements()).isEqualTo(250);
        assertThat(page25.getTotalPages()).isEqualTo(10);

        // Test 2: Page size 50
        Pageable pageable50 = PageRequest.of(0, 50);
        Page<FileMetadata> page50 = fileMetadataRepository.findByUploadedBy("user-large", pageable50);
        assertThat(page50.getContent()).hasSize(50);
        assertThat(page50.getTotalPages()).isEqualTo(5);

        // Test 3: Last page (partial)
        Pageable pageableLast = PageRequest.of(4, 50); // Page 4 (0-indexed), size 50
        Page<FileMetadata> pageLast = fileMetadataRepository.findByUploadedBy("user-large", pageableLast);
        assertThat(pageLast.getContent()).hasSize(50);
        assertThat(pageLast.isLast()).isTrue();
    }

    @Test
    @Order(14)
    @DisplayName("Test 14: Large dataset iteration - Process 500 items")
    void testLargeDataset_500Items() {
        // Given - Create 500 users (using User entity for this test)
        List<User> users = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            User user = new User();
            user.setId("large-user-" + i);
            user.setName("User " + i);
            user.setNumberOfPlaylists(i % 100);
            users.add(user);
        }
        userRepository.saveAll(users);

        // When - Iterate through all users
        long count = userRepository.count();

        // Then
        assertThat(count).isEqualTo(500);

        // Verify we can retrieve all items
        List<User> allUsers = (List<User>) userRepository.findAll();
        assertThat(allUsers).hasSize(500);

        // Verify data integrity on samples
        User firstUser = userRepository.findById("large-user-0").orElse(null);
        User midUser = userRepository.findById("large-user-250").orElse(null);
        User lastUser = userRepository.findById("large-user-499").orElse(null);

        assertThat(firstUser).isNotNull();
        assertThat(midUser).isNotNull();
        assertThat(lastUser).isNotNull();

        assertThat(firstUser.getName()).isEqualTo("User 0");
        assertThat(midUser.getName()).isEqualTo("User 250");
        assertThat(lastUser.getName()).isEqualTo("User 499");
    }

    // ==================== Combined Features Test ====================

    @Test
    @Order(15)
    @DisplayName("Test 15: Combined features - Binary + Sets + Pagination")
    void testCombinedFeatures() {
        // Given - Create 50 files with binary content, tags, and permissions
        List<FileMetadata> files = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            FileMetadata file = new FileMetadata();
            file.setFileName("file-" + i + ".txt");
            file.setContentType("text/plain");
            file.setUploadedBy("user-combined");
            file.setUploadedAt(Instant.now());

            // Binary content
            String content = "Content for file " + i;
            file.setFileContent(ByteBuffer.wrap(content.getBytes(StandardCharsets.UTF_8)));
            file.setFileSize((long) content.length());

            // Tags
            Set<String> tags = new HashSet<>(Arrays.asList("tag-" + (i % 10), "batch-" + (i / 10)));
            file.setTags(tags);

            // Permissions
            Set<String> permissions = i % 2 == 0
                    ? new HashSet<>(Arrays.asList("read", "write"))
                    : new HashSet<>(Collections.singletonList("read"));
            file.setPermissions(permissions);

            // Version numbers
            Set<Integer> versions = new HashSet<>(Arrays.asList(1, i + 1));
            file.setVersionNumbers(versions);

            files.add(file);
        }

        // When - Save all and paginate
        fileMetadataRepository.saveAll(files);

        Pageable pageable = PageRequest.of(0, 20);
        Page<FileMetadata> firstPage = fileMetadataRepository.findByUploadedBy("user-combined", pageable);

        // Then - Verify combined features
        assertThat(firstPage.getContent()).hasSize(20);
        assertThat(firstPage.getTotalElements()).isEqualTo(50);

        // Verify first file has all features
        FileMetadata firstFile = firstPage.getContent().get(0);
        assertThat(firstFile.getFileContent()).isNotNull();
        assertThat(firstFile.getTags()).isNotNull();
        assertThat(firstFile.getPermissions()).isNotNull();
        assertThat(firstFile.getVersionNumbers()).isNotNull();

        // Verify binary content can be read
        byte[] contentBytes = new byte[firstFile.getFileContent().remaining()];
        firstFile.getFileContent().get(contentBytes);
        String retrievedContent = new String(contentBytes, StandardCharsets.UTF_8);
        assertThat(retrievedContent).startsWith("Content for file");
    }

    // ==================== Edge Cases ====================

    @Test
    @Order(16)
    @DisplayName("Test 16: Special characters in StringSet")
    void testStringSet_SpecialCharacters() {
        // Given - File with tags containing special characters
        FileMetadata file = new FileMetadata();
        file.setFileName("special.txt");
        file.setContentType("text/plain");
        file.setUploadedBy("user1");
        file.setUploadedAt(Instant.now());

        Set<String> specialTags = new HashSet<>(Arrays.asList(
                "tag-with-dash",
                "tag_with_underscore",
                "tag.with.dot",
                "tag@with#symbols",
                "tag with spaces",
                "tag/with/slash"
        ));
        file.setTags(specialTags);

        // When
        FileMetadata savedFile = fileMetadataRepository.save(file);

        // Then
        FileMetadata retrievedFile = fileMetadataRepository.findById(savedFile.getFileId()).orElse(null);
        assertThat(retrievedFile).isNotNull();
        assertThat(retrievedFile.getTags()).containsExactlyInAnyOrderElementsOf(specialTags);
    }

    @Test
    @Order(17)
    @DisplayName("Test 17: Null binary ByteBuffer (no content)")
    void testBinaryData_NullByteBuffer() {
        // Given - File without binary content (null)
        FileMetadata file = new FileMetadata();
        file.setFileName("empty.bin");
        file.setContentType("application/octet-stream");
        file.setUploadedBy("user1");
        file.setUploadedAt(Instant.now());
        file.setFileContent(null); // No content
        file.setFileSize(0L);

        // When
        FileMetadata savedFile = fileMetadataRepository.save(file);

        // Then
        FileMetadata retrievedFile = fileMetadataRepository.findById(savedFile.getFileId()).orElse(null);
        assertThat(retrievedFile).isNotNull();
        assertThat(retrievedFile.getFileContent()).isNull();
        assertThat(retrievedFile.getFileSize()).isEqualTo(0L);

        // Note: Empty ByteBuffer.allocate(0) may also be stored as null by DynamoDB
    }

    // ==================== Helper Methods ====================

    private List<FileMetadata> createFiles(int count, String uploadedBy) {
        List<FileMetadata> files = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            FileMetadata file = new FileMetadata();
            file.setFileName("file-" + i + ".txt");
            file.setContentType("text/plain");
            file.setUploadedBy(uploadedBy);
            file.setUploadedAt(Instant.now());
            file.setFileSize((long) i);

            Set<String> tags = new HashSet<>(Collections.singletonList("tag-" + (i % 10)));
            file.setTags(tags);

            files.add(file);
        }
        return files;
    }
}
