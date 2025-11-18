package org.socialsignin.spring.data.dynamodb.domain.sample;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.socialsignin.spring.data.dynamodb.utils.DynamoDBLocalResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for DocumentMetadataService demonstrating:
 * - Service layer operations
 * - Pagination through service
 * - Date range queries
 * - Category and sub-category queries with parallel execution
 * - CRUD operations
 * - Optimistic locking behavior (version management and conflict detection)
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, DocumentMetadataServiceIntegrationTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("DocumentMetadataService Integration Tests")
public class DocumentMetadataServiceIntegrationTest {

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    public static class TestAppConfig {
        @Bean
        public DocumentMetadataService documentMetadataService(DocumentMetadataRepository repository) {
            return new DocumentMetadataService(repository);
        }
    }

    @Autowired
    private DocumentMetadataService documentMetadataService;

    @Autowired
    private DocumentMetadataRepository repository;

    private static final List<String> testDocumentIds = new ArrayList<>();

    @AfterAll
    static void cleanup(@Autowired DocumentMetadataRepository repository) {
        // Clean up all test documents
        for (String docId : testDocumentIds) {
            try {
                repository.deleteById(docId);
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    private DocumentMetadata createTestDocument(String id, Integer memberId, Integer category,
                                                  Integer subCategory, String createdBy) {
        DocumentMetadata doc = new DocumentMetadata();
        doc.setUniqueDocumentId(id);
        doc.setMemberId(memberId);
        doc.setDocumentCategory(category);
        doc.setDocumentSubCategory(subCategory);
        doc.setCreatedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        doc.setCreatedBy(createdBy);
        doc.setUpdatedBy(createdBy);
        doc.setNotes("Test document");

        testDocumentIds.add(id);
        return doc;
    }

    // ==================== Save and Retrieve Document Tests ====================

    @Test
    @Order(1)
    @DisplayName("Test 1: Save document and retrieve by ID")
    void testSaveAndGetDocumentById() {
        // Given
        DocumentMetadata doc = createTestDocument("service-test1-doc1", 100, 1001, 2001, "serviceUser1");

        // When - Save
        DocumentMetadata savedDoc = documentMetadataService.saveDocument(doc);

        // Then - Verify save
        assertThat(savedDoc).isNotNull();
        assertThat(savedDoc.getUniqueDocumentId()).isEqualTo("service-test1-doc1");

        // When - Retrieve
        Optional<DocumentMetadata> retrieved = documentMetadataService.getDocumentById("service-test1-doc1");

        // Then - Verify retrieve
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getMemberId()).isEqualTo(100);
        assertThat(retrieved.get().getDocumentCategory()).isEqualTo(1001);
        assertThat(retrieved.get().getCreatedBy()).isEqualTo("serviceUser1");
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Get document by ID - Not found")
    void testGetDocumentById_NotFound() {
        // When
        Optional<DocumentMetadata> result = documentMetadataService.getDocumentById("nonexistent-doc");

        // Then
        assertThat(result).isEmpty();
    }

    // ==================== Paginated Query Tests ====================

    @Test
    @Order(3)
    @DisplayName("Test 3: Get documents by memberId with pagination - Single page")
    void testGetDocumentsByMemberId_SinglePage() {
        // Given
        documentMetadataService.saveDocument(createTestDocument("service-test3-doc1", 101, 1001, 2001, "user1"));
        documentMetadataService.saveDocument(createTestDocument("service-test3-doc2", 101, 1002, 2002, "user1"));
        documentMetadataService.saveDocument(createTestDocument("service-test3-doc3", 101, 1003, 2003, "user1"));

        // When
        Slice<DocumentMetadata> result = documentMetadataService.getDocumentsByMemberId(101, PageRequest.of(0, 10));

        // Then
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.getContent()).allMatch(doc -> doc.getMemberId().equals(101));
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: Get documents by memberId with pagination - Multiple pages")
    void testGetDocumentsByMemberId_MultiplePages() {
        // Given - Create 5 documents but use page size of 2
        for (int i = 1; i <= 5; i++) {
            documentMetadataService.saveDocument(
                    createTestDocument("service-test4-doc" + i, 102, 1001, 2001, "user1"));
        }

        // When - Get first page
        Slice<DocumentMetadata> firstPage = documentMetadataService.getDocumentsByMemberId(102, PageRequest.of(0, 2));

        // Then - Verify first page
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.hasNext()).isTrue();

        // When - Get next page
        Slice<DocumentMetadata> secondPage = documentMetadataService.getDocumentsByMemberId(102, firstPage.nextPageable());

        // Then - Verify second page
        assertThat(secondPage.getContent()).hasSize(2);
        assertThat(secondPage.hasNext()).isTrue();
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: Get documents by memberId - Empty result")
    void testGetDocumentsByMemberId_EmptyResult() {
        // When
        Slice<DocumentMetadata> result = documentMetadataService.getDocumentsByMemberId(999, PageRequest.of(0, 10));

        // Then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.hasNext()).isFalse();
    }

    // ==================== Date Range Query Tests ====================

    @Test
    @Order(6)
    @DisplayName("Test 6: Get documents by memberId and date range - Within range")
    void testGetDocumentsByMemberIdAndDateRange_WithinRange() {
        // Given
        Instant now = Instant.now();
        DocumentMetadata doc1 = createTestDocument("service-test6-doc1", 103, 1001, 2001, "user1");
        doc1.setCreatedAt(now.minus(2, ChronoUnit.HOURS));
        documentMetadataService.saveDocument(doc1);

        DocumentMetadata doc2 = createTestDocument("service-test6-doc2", 103, 1002, 2002, "user1");
        doc2.setCreatedAt(now.minus(1, ChronoUnit.HOURS));
        documentMetadataService.saveDocument(doc2);

        DocumentMetadata doc3 = createTestDocument("service-test6-doc3", 103, 1003, 2003, "user1");
        doc3.setCreatedAt(now.minus(5, ChronoUnit.HOURS));
        documentMetadataService.saveDocument(doc3);

        // When - Query for docs within last 3 hours
        Instant startDate = now.minus(3, ChronoUnit.HOURS);
        Instant endDate = now.plus(1, ChronoUnit.HOURS);
        List<DocumentMetadata> results = documentMetadataService.getDocumentsByMemberIdAndDateRange(103, startDate, endDate);

        // Then - Should get 2 documents (doc1 and doc2, not doc3)
        assertThat(results).hasSize(2);
        assertThat(results).extracting(DocumentMetadata::getUniqueDocumentId)
                .containsExactlyInAnyOrder("service-test6-doc1", "service-test6-doc2");
    }

    @Test
    @Order(7)
    @DisplayName("Test 7: Get documents by memberId and date range - No results in range")
    void testGetDocumentsByMemberIdAndDateRange_NoResults() {
        // Given - Document created now
        documentMetadataService.saveDocument(createTestDocument("service-test7-doc1", 104, 1001, 2001, "user1"));

        // When - Query for old dates
        Instant startDate = Instant.now().minus(10, ChronoUnit.DAYS);
        Instant endDate = Instant.now().minus(5, ChronoUnit.DAYS);
        List<DocumentMetadata> results = documentMetadataService.getDocumentsByMemberIdAndDateRange(104, startDate, endDate);

        // Then
        assertThat(results).isEmpty();
    }

    // ==================== Category Query Tests (Custom Implementation) ====================

    @Test
    @Order(8)
    @DisplayName("Test 8: Get documents by memberId and categories - Single category")
    void testGetDocumentsByMemberIdAndCategoriesIn_SingleCategory() {
        // Given
        documentMetadataService.saveDocument(createTestDocument("service-test8-doc1", 105, 1001, 2001, "user1"));
        documentMetadataService.saveDocument(createTestDocument("service-test8-doc2", 105, 1002, 2002, "user1"));
        documentMetadataService.saveDocument(createTestDocument("service-test8-doc3", 105, 1003, 2003, "user1"));

        // When
        List<DocumentMetadata> results = documentMetadataService.getDocumentsByMemberIdAndCategoriesIn(
                105, List.of(1001));

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getDocumentCategory()).isEqualTo(1001);
    }

    @Test
    @Order(9)
    @DisplayName("Test 9: Get documents by memberId and categories - Multiple categories")
    void testGetDocumentsByMemberIdAndCategoriesIn_MultipleCategories() {
        // Given
        documentMetadataService.saveDocument(createTestDocument("service-test9-doc1", 106, 1001, 2001, "user1"));
        documentMetadataService.saveDocument(createTestDocument("service-test9-doc2", 106, 1002, 2002, "user1"));
        documentMetadataService.saveDocument(createTestDocument("service-test9-doc3", 106, 1003, 2003, "user1"));
        documentMetadataService.saveDocument(createTestDocument("service-test9-doc4", 106, 1004, 2004, "user1"));

        // When - Query for categories 1001 and 1003
        List<DocumentMetadata> results = documentMetadataService.getDocumentsByMemberIdAndCategoriesIn(
                106, Arrays.asList(1001, 1003));

        // Then - Should get 2 documents
        assertThat(results).hasSize(2);
        assertThat(results).extracting(DocumentMetadata::getDocumentCategory)
                .containsExactlyInAnyOrder(1001, 1003);
    }

    @Test
    @Order(10)
    @DisplayName("Test 10: Get documents by memberId and categories - Empty result")
    void testGetDocumentsByMemberIdAndCategoriesIn_EmptyResult() {
        // Given
        documentMetadataService.saveDocument(createTestDocument("service-test10-doc1", 107, 1001, 2001, "user1"));

        // When - Query for non-existent category
        List<DocumentMetadata> results = documentMetadataService.getDocumentsByMemberIdAndCategoriesIn(
                107, List.of(9999));

        // Then
        assertThat(results).isEmpty();
    }

    // ==================== SubCategory Query Tests (Custom Implementation) ====================

    @Test
    @Order(11)
    @DisplayName("Test 11: Get documents by memberId and sub-categories - Single sub-category")
    void testGetDocumentsByMemberIdAndSubCategoriesIn_SingleSubCategory() {
        // Given
        documentMetadataService.saveDocument(createTestDocument("service-test11-doc1", 108, 1001, 2001, "user1"));
        documentMetadataService.saveDocument(createTestDocument("service-test11-doc2", 108, 1002, 2002, "user1"));
        documentMetadataService.saveDocument(createTestDocument("service-test11-doc3", 108, 1003, 2003, "user1"));

        // When
        List<DocumentMetadata> results = documentMetadataService.getDocumentsByMemberIdAndSubCategoriesIn(
                108, List.of(2001));

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getDocumentSubCategory()).isEqualTo(2001);
    }

    @Test
    @Order(12)
    @DisplayName("Test 12: Get documents by memberId and sub-categories - Multiple sub-categories")
    void testGetDocumentsByMemberIdAndSubCategoriesIn_MultipleSubCategories() {
        // Given
        documentMetadataService.saveDocument(createTestDocument("service-test12-doc1", 109, 1001, 2001, "user1"));
        documentMetadataService.saveDocument(createTestDocument("service-test12-doc2", 109, 1002, 2002, "user1"));
        documentMetadataService.saveDocument(createTestDocument("service-test12-doc3", 109, 1003, 2003, "user1"));
        documentMetadataService.saveDocument(createTestDocument("service-test12-doc4", 109, 1004, 2004, "user1"));

        // When - Query for sub-categories 2002 and 2004
        List<DocumentMetadata> results = documentMetadataService.getDocumentsByMemberIdAndSubCategoriesIn(
                109, Arrays.asList(2002, 2004));

        // Then - Should get 2 documents
        assertThat(results).hasSize(2);
        assertThat(results).extracting(DocumentMetadata::getDocumentSubCategory)
                .containsExactlyInAnyOrder(2002, 2004);
    }

    @Test
    @Order(13)
    @DisplayName("Test 13: Get documents by memberId and sub-categories - Empty result")
    void testGetDocumentsByMemberIdAndSubCategoriesIn_EmptyResult() {
        // Given
        documentMetadataService.saveDocument(createTestDocument("service-test13-doc1", 110, 1001, 2001, "user1"));

        // When - Query for non-existent sub-category
        List<DocumentMetadata> results = documentMetadataService.getDocumentsByMemberIdAndSubCategoriesIn(
                110, List.of(9999));

        // Then
        assertThat(results).isEmpty();
    }

    // ==================== Delete Document Tests ====================

    @Test
    @Order(14)
    @DisplayName("Test 14: Delete document")
    void testDeleteDocument() {
        // Given
        DocumentMetadata doc = createTestDocument("service-test14-doc1", 111, 1001, 2001, "user1");
        documentMetadataService.saveDocument(doc);

        // Verify document exists
        Optional<DocumentMetadata> beforeDelete = documentMetadataService.getDocumentById("service-test14-doc1");
        assertThat(beforeDelete).isPresent();

        // When
        documentMetadataService.deleteDocument("service-test14-doc1");
        testDocumentIds.remove("service-test14-doc1"); // Remove from cleanup list

        // Then
        Optional<DocumentMetadata> afterDelete = documentMetadataService.getDocumentById("service-test14-doc1");
        assertThat(afterDelete).isEmpty();
    }

    // ==================== Parallel Query Performance Test ====================

    @Test
    @Order(15)
    @DisplayName("Test 15: Parallel queries for multiple categories - Performance test")
    void testGetDocumentsByMemberIdAndCategoriesIn_ParallelPerformance() {
        // Given - Create documents across 5 different categories
        for (int category = 1; category <= 5; category++) {
            for (int i = 1; i <= 3; i++) {
                documentMetadataService.saveDocument(
                        createTestDocument("service-test15-cat" + category + "-doc" + i,
                                112, 1000 + category, 2001, "user1"));
            }
        }

        // When - Query for all 5 categories (should run in parallel)
        long startTime = System.currentTimeMillis();
        List<DocumentMetadata> results = documentMetadataService.getDocumentsByMemberIdAndCategoriesIn(
                112, Arrays.asList(1001, 1002, 1003, 1004, 1005));
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(results).hasSize(15); // 5 categories × 3 documents each
        assertThat(duration).isLessThan(5000); // Should complete reasonably fast with parallel execution

        // Verify all categories are present
        assertThat(results).extracting(DocumentMetadata::getDocumentCategory)
                .containsExactlyInAnyOrder(
                        1001, 1001, 1001,
                        1002, 1002, 1002,
                        1003, 1003, 1003,
                        1004, 1004, 1004,
                        1005, 1005, 1005);
    }

    // ==================== Update Document Test ====================

    @Test
    @Order(16)
    @DisplayName("Test 16: Update document")
    void testUpdateDocument() {
        // Given - Create and save document
        DocumentMetadata doc = createTestDocument("service-test16-doc1", 113, 1001, 2001, "user1");
        documentMetadataService.saveDocument(doc);

        // When - Update the document
        doc.setNotes("Updated notes");
        doc.setUpdatedBy("user2");
        doc.setUpdatedAt(Instant.now());
        DocumentMetadata updatedDoc = documentMetadataService.saveDocument(doc);

        // Then - Verify update
        assertThat(updatedDoc.getNotes()).isEqualTo("Updated notes");
        assertThat(updatedDoc.getUpdatedBy()).isEqualTo("user2");

        // Retrieve and verify
        Optional<DocumentMetadata> retrieved = documentMetadataService.getDocumentById("service-test16-doc1");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().getNotes()).isEqualTo("Updated notes");
        assertThat(retrieved.get().getUpdatedBy()).isEqualTo("user2");
    }

    // ==================== Optimistic Locking Tests ====================

    @Test
    @Order(17)
    @DisplayName("Test 17: Optimistic locking - Version is set on initial save")
    void testOptimisticLocking_VersionSetOnInitialSave() {
        // Given
        DocumentMetadata doc = createTestDocument("service-test17-doc1", 114, 1001, 2001, "user1");
        assertThat(doc.getVersion()).isNull(); // Version is null before save

        // When
        DocumentMetadata savedDoc = documentMetadataService.saveDocument(doc);

        // Then
        assertThat(savedDoc.getVersion()).isNotNull();
        assertThat(savedDoc.getVersion()).isEqualTo(1L); // First version should be 1
    }

    @Test
    @Order(18)
    @DisplayName("Test 18: Optimistic locking - Version is incremented on update")
    void testOptimisticLocking_VersionIncrementedOnUpdate() {
        // Given - Create and save document
        DocumentMetadata doc = createTestDocument("service-test18-doc1", 115, 1001, 2001, "user1");
        DocumentMetadata savedDoc = documentMetadataService.saveDocument(doc);
        Long initialVersion = savedDoc.getVersion();
        assertThat(initialVersion).isEqualTo(1L);

        // When - Update the document
        savedDoc.setNotes("Updated notes");
        DocumentMetadata updatedDoc = documentMetadataService.saveDocument(savedDoc);

        // Then - Version should be incremented
        assertThat(updatedDoc.getVersion()).isEqualTo(2L);
        assertThat(updatedDoc.getVersion()).isGreaterThan(initialVersion);

        // When - Update again
        updatedDoc.setNotes("Updated notes again");
        DocumentMetadata updatedDoc2 = documentMetadataService.saveDocument(updatedDoc);

        // Then - Version should be incremented again
        assertThat(updatedDoc2.getVersion()).isEqualTo(3L);
    }

    @Test
    @Order(19)
    @DisplayName("Test 19: Optimistic locking - Concurrent update throws OptimisticLockingException")
    void testOptimisticLocking_ConcurrentUpdateThrowsException() {
        // Given - Create and save document
        DocumentMetadata doc = createTestDocument("service-test19-doc1", 116, 1001, 2001, "user1");
        DocumentMetadata savedDoc = documentMetadataService.saveDocument(doc);
        assertThat(savedDoc.getVersion()).isEqualTo(1L);

        // Simulate two users retrieving the same document
        Optional<DocumentMetadata> user1Doc = documentMetadataService.getDocumentById("service-test19-doc1");
        Optional<DocumentMetadata> user2Doc = documentMetadataService.getDocumentById("service-test19-doc1");

        assertThat(user1Doc).isPresent();
        assertThat(user2Doc).isPresent();
        assertThat(user1Doc.get().getVersion()).isEqualTo(1L);
        assertThat(user2Doc.get().getVersion()).isEqualTo(1L);

        // When - User 1 updates successfully
        user1Doc.get().setNotes("User 1 update");
        DocumentMetadata user1Updated = documentMetadataService.saveDocument(user1Doc.get());
        assertThat(user1Updated.getVersion()).isEqualTo(2L);

        // Then - User 2 update should fail due to stale version
        user2Doc.get().setNotes("User 2 update");
        assertThatThrownBy(() -> documentMetadataService.saveDocument(user2Doc.get()))
                .isInstanceOf(OptimisticLockingException.class)
                .hasMessageContaining("Document was modified by another user")
                .satisfies(exception -> {
                    OptimisticLockingException ole = (OptimisticLockingException) exception;
                    assertThat(ole.getDocumentId()).isEqualTo("service-test19-doc1");
                    assertThat(ole.getAttemptedVersion()).isEqualTo(1L);
                });

        // Verify the document has user1's changes, not user2's
        Optional<DocumentMetadata> finalDoc = documentMetadataService.getDocumentById("service-test19-doc1");
        assertThat(finalDoc).isPresent();
        assertThat(finalDoc.get().getNotes()).isEqualTo("User 1 update");
        assertThat(finalDoc.get().getVersion()).isEqualTo(2L);
    }

    @Test
    @Order(20)
    @DisplayName("Test 20: Optimistic locking - Update with correct version succeeds")
    void testOptimisticLocking_UpdateWithCorrectVersionSucceeds() {
        // Given - Create and save document
        DocumentMetadata doc = createTestDocument("service-test20-doc1", 117, 1001, 2001, "user1");
        DocumentMetadata savedDoc = documentMetadataService.saveDocument(doc);

        // Simulate user retrieving the document after an update
        Optional<DocumentMetadata> userDoc = documentMetadataService.getDocumentById("service-test20-doc1");
        assertThat(userDoc).isPresent();

        // When - User updates with the current version
        userDoc.get().setNotes("Updated with correct version");
        DocumentMetadata updated = documentMetadataService.saveDocument(userDoc.get());

        // Then - Update should succeed
        assertThat(updated.getVersion()).isEqualTo(2L);
        assertThat(updated.getNotes()).isEqualTo("Updated with correct version");
    }
}
