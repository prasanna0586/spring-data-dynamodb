package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.socialsignin.spring.data.dynamodb.utils.DynamoDBLocalResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for DocumentMetadataRepository demonstrating:
 * - Primary key queries
 * - GSI queries with hash and range keys
 * - Custom parallel query implementations
 * - CRUD operations
 * - Pagination
 * - Batch operations
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, DocumentMetadataRepositoryIntegrationTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DocumentMetadataRepositoryIntegrationTest {

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    public static class TestAppConfig {
    }

    @Autowired
    private DocumentMetadataRepository repository;

    @Autowired
    private DynamoDbClient amazonDynamoDB;

    private static boolean tableCreated = false;
    private final List<String> testDocumentIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        if (!tableCreated) {
            createTableIfNotExists();
            tableCreated = true;
        }
    }

    @AfterEach
    void tearDown() {
        // Clean up documents created in this test
        for (String docId : testDocumentIds) {
            try {
                repository.deleteById(docId);
            } catch (Exception e) {
                // Ignore if document doesn't exist
            }
        }
        testDocumentIds.clear();
    }

    private void createTableIfNotExists() {
        try {
            amazonDynamoDB.describeTable(DescribeTableRequest.builder()
                    .tableName("DocumentMetadata")
                    .build());
        } catch (ResourceNotFoundException e) {
            // SDK v2: Build CreateTableRequest manually
            // Primary key: uniqueDocumentId (String)
            // GSI: idx_memberId_createdAt with memberId (hash) and createdAt (range)
            CreateTableRequest createTableRequest = CreateTableRequest.builder()
                    .tableName("DocumentMetadata")
                    .keySchema(
                            KeySchemaElement.builder()
                                    .attributeName("uniqueDocumentId")
                                    .keyType(KeyType.HASH)
                                    .build()
                    )
                    .attributeDefinitions(
                            AttributeDefinition.builder()
                                    .attributeName("uniqueDocumentId")
                                    .attributeType(ScalarAttributeType.S)
                                    .build(),
                            AttributeDefinition.builder()
                                    .attributeName("memberId")
                                    .attributeType(ScalarAttributeType.N)
                                    .build(),
                            AttributeDefinition.builder()
                                    .attributeName("createdAt")
                                    .attributeType(ScalarAttributeType.S)
                                    .build()
                    )
                    .provisionedThroughput(ProvisionedThroughput.builder()
                            .readCapacityUnits(5L)
                            .writeCapacityUnits(5L)
                            .build())
                    .globalSecondaryIndexes(
                            GlobalSecondaryIndex.builder()
                                    .indexName("idx_memberId_createdAt")
                                    .keySchema(
                                            KeySchemaElement.builder()
                                                    .attributeName("memberId")
                                                    .keyType(KeyType.HASH)
                                                    .build(),
                                            KeySchemaElement.builder()
                                                    .attributeName("createdAt")
                                                    .keyType(KeyType.RANGE)
                                                    .build()
                                    )
                                    .projection(Projection.builder()
                                            .projectionType(ProjectionType.ALL)
                                            .build())
                                    .provisionedThroughput(ProvisionedThroughput.builder()
                                            .readCapacityUnits(5L)
                                            .writeCapacityUnits(5L)
                                            .build())
                                    .build()
                    )
                    .build();

            amazonDynamoDB.createTable(createTableRequest);

            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ==================== PRIMARY KEY QUERIES ====================

    @Test
    @Order(1)
    @DisplayName("Test 1: Create and find by uniqueDocumentId (Primary Key)")
    void testFindByUniqueDocumentId_Success() {
        // Given
        DocumentMetadata doc = createTestDocument("test1-doc1", 1, 101, 201, "user1");
        repository.save(doc);

        // When
        Optional<DocumentMetadata> found = repository.findByUniqueDocumentId("test1-doc1");

        // Then
        assertThat(found).isPresent();
        assertThat(found.get().getUniqueDocumentId()).isEqualTo("test1-doc1");
        assertThat(found.get().getMemberId()).isEqualTo(1);
        assertThat(found.get().getDocumentCategory()).isEqualTo(101);
        assertThat(found.get().getDocumentSubCategory()).isEqualTo(201);
        assertThat(found.get().getCreatedBy()).isEqualTo("user1");
        assertThat(found.get().getNotes()).isEqualTo("Test document");
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Find by uniqueDocumentId - Not Found")
    void testFindByUniqueDocumentId_NotFound() {
        // When
        Optional<DocumentMetadata> found = repository.findByUniqueDocumentId("non-existent-id");

        // Then
        assertThat(found).isEmpty();
    }

    // ==================== GSI: memberId (Hash Key Only) ====================

    @Test
    @Order(3)
    @DisplayName("Test 3: Find by memberId - Single document")
    void testFindByMemberId_SingleDocument() {
        // Given
        DocumentMetadata doc = createTestDocument("test3-doc1", 3, 101, 201, "user1");
        repository.save(doc);

        // When
        Slice<DocumentMetadata> results = repository.findByMemberId(3, PageRequest.of(0, 10));

        // Then
        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getMemberId()).isEqualTo(3);
        assertThat(results.getContent().get(0).getUniqueDocumentId()).isEqualTo("test3-doc1");
        assertThat(results.hasNext()).isFalse();
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: Find by memberId - Multiple documents")
    void testFindByMemberId_MultipleDocuments() {
        // Given
        repository.save(createTestDocument("test4-doc1", 4, 101, 201, "user1"));
        repository.save(createTestDocument("test4-doc2", 4, 102, 202, "user1"));
        repository.save(createTestDocument("test4-doc3", 4, 103, 203, "user2"));

        // When
        Slice<DocumentMetadata> results = repository.findByMemberId(4, PageRequest.of(0, 10));

        // Then
        assertThat(results.getContent()).hasSize(3);
        assertThat(results.getContent()).allMatch(doc -> doc.getMemberId().equals(4));
        assertThat(results.hasNext()).isFalse();
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: Find by memberId - No results")
    void testFindByMemberId_NoResults() {
        // When
        Slice<DocumentMetadata> results = repository.findByMemberId(999, PageRequest.of(0, 10));

        // Then
        assertThat(results.getContent()).isEmpty();
        assertThat(results.hasNext()).isFalse();
    }

    // ==================== GSI: memberId + createdAt (Hash + Range) ====================

    @Test
    @Order(6)
    @DisplayName("Test 6: Find by memberId and createdAt between - Within range")
    void testFindByMemberIdAndCreatedAtBetween_WithinRange() {
        // Given
        Instant now = Instant.now();
        Instant fiveDaysAgo = now.minus(5, ChronoUnit.DAYS);
        Instant threeDaysAgo = now.minus(3, ChronoUnit.DAYS);
        Instant oneDayAgo = now.minus(1, ChronoUnit.DAYS);

        DocumentMetadata doc1 = createTestDocument("test6-doc1", 6, 101, 201, "user1");
        doc1.setCreatedAt(fiveDaysAgo);
        repository.save(doc1);

        DocumentMetadata doc2 = createTestDocument("test6-doc2", 6, 101, 201, "user1");
        doc2.setCreatedAt(threeDaysAgo);
        repository.save(doc2);

        DocumentMetadata doc3 = createTestDocument("test6-doc3", 6, 102, 202, "user1");
        doc3.setCreatedAt(oneDayAgo);
        repository.save(doc3);

        DocumentMetadata doc4 = createTestDocument("test6-doc4", 6, 103, 203, "user1");
        doc4.setCreatedAt(now);
        repository.save(doc4);

        // When - Query for documents between 4 days ago and 2 days ago
        Instant startDate = now.minus(4, ChronoUnit.DAYS);
        Instant endDate = now.minus(2, ChronoUnit.DAYS);
        List<DocumentMetadata> results = repository.findByMemberIdAndCreatedAtBetween(6, startDate, endDate);

        // Then - Should only get doc2 (created 3 days ago)
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getUniqueDocumentId()).isEqualTo("test6-doc2");
        assertThat(results.get(0).getCreatedAt()).isBetween(startDate, endDate);
    }

    @Test
    @Order(7)
    @DisplayName("Test 7: Find by memberId and createdAt between - No results in range")
    void testFindByMemberIdAndCreatedAtBetween_NoResults() {
        // Given
        Instant now = Instant.now();
        DocumentMetadata doc = createTestDocument("test7-doc1", 7, 101, 201, "user1");
        doc.setCreatedAt(now);
        repository.save(doc);

        // When - Query for a range in the past
        Instant startDate = now.minus(10, ChronoUnit.DAYS);
        Instant endDate = now.minus(5, ChronoUnit.DAYS);
        List<DocumentMetadata> results = repository.findByMemberIdAndCreatedAtBetween(7, startDate, endDate);

        // Then
        assertThat(results).isEmpty();
    }

    @Test
    @Order(8)
    @DisplayName("Test 8: Find by memberId and createdAt between - Multiple in range")
    void testFindByMemberIdAndCreatedAtBetween_MultipleResults() {
        // Given
        Instant now = Instant.now();
        Instant threeDaysAgo = now.minus(3, ChronoUnit.DAYS);
        Instant twoDaysAgo = now.minus(2, ChronoUnit.DAYS);
        Instant oneDayAgo = now.minus(1, ChronoUnit.DAYS);

        DocumentMetadata doc1 = createTestDocument("test8-doc1", 8, 101, 201, "user1");
        doc1.setCreatedAt(threeDaysAgo);
        repository.save(doc1);

        DocumentMetadata doc2 = createTestDocument("test8-doc2", 8, 102, 202, "user1");
        doc2.setCreatedAt(twoDaysAgo);
        repository.save(doc2);

        DocumentMetadata doc3 = createTestDocument("test8-doc3", 8, 103, 203, "user1");
        doc3.setCreatedAt(oneDayAgo);
        repository.save(doc3);

        // When
        Instant startDate = now.minus(4, ChronoUnit.DAYS);
        Instant endDate = now;
        List<DocumentMetadata> results = repository.findByMemberIdAndCreatedAtBetween(8, startDate, endDate);

        // Then
        assertThat(results).hasSize(3);
        assertThat(results).allMatch(doc -> doc.getMemberId().equals(8));
        assertThat(results).allMatch(doc ->
            doc.getCreatedAt().isAfter(startDate) && doc.getCreatedAt().isBefore(endDate.plus(1, ChronoUnit.SECONDS)));
    }

    // ==================== CUSTOM: memberId + documentCategoryIn ====================

    @Test
    @Order(9)
    @DisplayName("Test 9: Find by memberId and documentCategoryIn - Multiple categories")
    void testFindByMemberIdAndDocumentCategoryIn_MultipleCategories() {
        // Given
        repository.save(createTestDocument("test9-doc1", 9, 101, 201, "user1"));
        repository.save(createTestDocument("test9-doc2", 9, 102, 202, "user1"));
        repository.save(createTestDocument("test9-doc3", 9, 103, 203, "user1"));
        repository.save(createTestDocument("test9-doc4", 9, 104, 201, "user1"));
        repository.save(createTestDocument("test9-doc5", 10, 101, 201, "user1")); // Different member

        // When
        List<DocumentMetadata> results = repository.findByMemberIdAndDocumentCategoryIn(
                9, Arrays.asList(101, 102));

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(doc -> doc.getMemberId().equals(9));
        assertThat(results).allMatch(doc ->
            doc.getDocumentCategory().equals(101) || doc.getDocumentCategory().equals(102));
    }

    @Test
    @Order(10)
    @DisplayName("Test 10: Find by memberId and documentCategoryIn - Single category")
    void testFindByMemberIdAndDocumentCategoryIn_SingleCategory() {
        // Given
        repository.save(createTestDocument("test10-doc1", 10, 101, 201, "user1"));
        repository.save(createTestDocument("test10-doc2", 10, 101, 202, "user1"));
        repository.save(createTestDocument("test10-doc3", 10, 102, 203, "user1"));

        // When
        List<DocumentMetadata> results = repository.findByMemberIdAndDocumentCategoryIn(
                10, List.of(101));

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).allMatch(doc -> doc.getDocumentCategory().equals(101));
    }

    @Test
    @Order(11)
    @DisplayName("Test 11: Find by memberId and documentCategoryIn - No matches")
    void testFindByMemberIdAndDocumentCategoryIn_NoMatches() {
        // Given
        repository.save(createTestDocument("test11-doc1", 11, 101, 201, "user1"));

        // When
        List<DocumentMetadata> results = repository.findByMemberIdAndDocumentCategoryIn(
                11, Arrays.asList(102, 103));

        // Then
        assertThat(results).isEmpty();
    }

    @Test
    @Order(12)
    @DisplayName("Test 12: Find by memberId and documentCategoryIn - Empty list")
    void testFindByMemberIdAndDocumentCategoryIn_EmptyList() {
        // When
        List<DocumentMetadata> results = repository.findByMemberIdAndDocumentCategoryIn(
                12, new ArrayList<>());

        // Then
        assertThat(results).isEmpty();
    }

    // ==================== CUSTOM: memberId + documentSubCategoryIn ====================

    @Test
    @Order(13)
    @DisplayName("Test 13: Find by memberId and documentSubCategoryIn - Multiple subcategories")
    void testFindByMemberIdAndDocumentSubCategoryIn_MultipleSubCategories() {
        // Given
        repository.save(createTestDocument("test13-doc1", 13, 101, 201, "user1"));
        repository.save(createTestDocument("test13-doc2", 13, 101, 202, "user1"));
        repository.save(createTestDocument("test13-doc3", 13, 101, 203, "user1"));
        repository.save(createTestDocument("test13-doc4", 13, 101, 204, "user1"));

        // When
        List<DocumentMetadata> results = repository.findByMemberIdAndDocumentSubCategoryIn(
                13, Arrays.asList(201, 202, 203));

        // Then
        assertThat(results).hasSize(3);
        assertThat(results).allMatch(doc -> doc.getMemberId().equals(13));
        assertThat(results).allMatch(doc ->
            doc.getDocumentSubCategory().equals(201) ||
            doc.getDocumentSubCategory().equals(202) ||
            doc.getDocumentSubCategory().equals(203));
    }

    @Test
    @Order(14)
    @DisplayName("Test 14: Find by memberId and documentSubCategoryIn - No matches")
    void testFindByMemberIdAndDocumentSubCategoryIn_NoMatches() {
        // Given
        repository.save(createTestDocument("test14-doc1", 14, 101, 201, "user1"));

        // When
        List<DocumentMetadata> results = repository.findByMemberIdAndDocumentSubCategoryIn(
                14, Arrays.asList(202, 203));

        // Then
        assertThat(results).isEmpty();
    }

    // ==================== CRUD OPERATIONS ====================

    @Test
    @Order(15)
    @DisplayName("Test 15: Create document - All fields populated")
    void testCreateDocument_AllFields() {
        // Given
        Instant createdTime = Instant.now();
        DocumentMetadata doc = new DocumentMetadata();
        doc.setUniqueDocumentId("test15-doc1");
        doc.setMemberId(15);
        doc.setDocumentCategory(101);
        doc.setDocumentSubCategory(201);
        doc.setCreatedAt(createdTime);
        doc.setUpdatedAt(createdTime);
        doc.setCreatedBy("user1");
        doc.setUpdatedBy("user1");
        doc.setNotes("Comprehensive test document with all fields");
        testDocumentIds.add("test15-doc1");

        // When
        repository.save(doc);
        Optional<DocumentMetadata> saved = repository.findByUniqueDocumentId("test15-doc1");

        // Then
        assertThat(saved).isPresent();
        DocumentMetadata savedDoc = saved.get();
        assertThat(savedDoc.getUniqueDocumentId()).isEqualTo("test15-doc1");
        assertThat(savedDoc.getMemberId()).isEqualTo(15);
        assertThat(savedDoc.getDocumentCategory()).isEqualTo(101);
        assertThat(savedDoc.getDocumentSubCategory()).isEqualTo(201);
        assertThat(savedDoc.getCreatedBy()).isEqualTo("user1");
        assertThat(savedDoc.getUpdatedBy()).isEqualTo("user1");
        assertThat(savedDoc.getNotes()).isEqualTo("Comprehensive test document with all fields");
    }

    @Test
    @Order(16)
    @DisplayName("Test 16: Update document - Modify multiple fields")
    void testUpdateDocument_MultipleFields() {
        // Given
        DocumentMetadata doc = createTestDocument("test16-doc1", 16, 101, 201, "user1");
        DocumentMetadata savedDoc = repository.save(doc);

        // When - Update using the saved document (which has version populated)
        savedDoc.setNotes("Updated notes for test");
        savedDoc.setUpdatedAt(Instant.now().plus(1, ChronoUnit.HOURS));
        savedDoc.setUpdatedBy("user2");
        savedDoc.setDocumentSubCategory(202);
        repository.save(savedDoc);

        Optional<DocumentMetadata> updated = repository.findByUniqueDocumentId("test16-doc1");

        // Then
        assertThat(updated).isPresent();
        DocumentMetadata updatedDoc = updated.get();
        assertThat(updatedDoc.getNotes()).isEqualTo("Updated notes for test");
        assertThat(updatedDoc.getUpdatedBy()).isEqualTo("user2");
        assertThat(updatedDoc.getDocumentSubCategory()).isEqualTo(202);
        assertThat(updatedDoc.getCreatedBy()).isEqualTo("user1"); // Should remain unchanged
    }

    @Test
    @Order(17)
    @DisplayName("Test 17: Delete document - Verify deletion")
    void testDeleteDocument_Success() {
        // Given
        DocumentMetadata doc = createTestDocument("test17-doc1", 17, 101, 201, "user1");
        repository.save(doc);

        Optional<DocumentMetadata> beforeDelete = repository.findByUniqueDocumentId("test17-doc1");
        assertThat(beforeDelete).isPresent();

        // When
        repository.deleteById("test17-doc1");
        testDocumentIds.remove("test17-doc1"); // Remove from cleanup list

        // Then
        Optional<DocumentMetadata> afterDelete = repository.findByUniqueDocumentId("test17-doc1");
        assertThat(afterDelete).isEmpty();
    }

    @Test
    @Order(18)
    @DisplayName("Test 18: Batch create and query - 10 documents")
    void testBatchOperations_MultipleDocuments() {
        // Given - Create 10 documents for the same member
        for (int i = 1; i <= 10; i++) {
            DocumentMetadata doc = createTestDocument("test18-doc" + i, 18, 101, 201, "user1");
            doc.setCreatedAt(Instant.now().minus(i, ChronoUnit.DAYS));
            repository.save(doc);
        }

        // When
        Slice<DocumentMetadata> allForMember = repository.findByMemberId(18, PageRequest.of(0, 20));

        // Then
        assertThat(allForMember.getContent()).hasSize(10);
        assertThat(allForMember.getContent()).allMatch(doc -> doc.getMemberId().equals(18));
        assertThat(allForMember.hasNext()).isFalse();
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

        // Track for cleanup
        testDocumentIds.add(id);

        return doc;
    }
}
