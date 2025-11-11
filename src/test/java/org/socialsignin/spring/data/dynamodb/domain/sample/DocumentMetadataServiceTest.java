package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DocumentMetadataService using Mockito.
 * Tests service layer logic without actual database interaction.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DocumentMetadataService Unit Tests")
class DocumentMetadataServiceTest {

    @Mock
    private DocumentMetadataRepository documentMetadataRepository;

    @InjectMocks
    private DocumentMetadataService documentMetadataService;

    private DocumentMetadata testDocument1;
    private DocumentMetadata testDocument2;
    private DocumentMetadata testDocument3;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();

        testDocument1 = new DocumentMetadata();
        testDocument1.setUniqueDocumentId("doc1");
        testDocument1.setMemberId(1);
        testDocument1.setDocumentCategory(100);
        testDocument1.setDocumentSubCategory(200);
        testDocument1.setCreatedAt(now);
        testDocument1.setCreatedBy("user1");

        testDocument2 = new DocumentMetadata();
        testDocument2.setUniqueDocumentId("doc2");
        testDocument2.setMemberId(1);
        testDocument2.setDocumentCategory(101);
        testDocument2.setDocumentSubCategory(201);
        testDocument2.setCreatedAt(now.minusSeconds(3600));
        testDocument2.setCreatedBy("user1");

        testDocument3 = new DocumentMetadata();
        testDocument3.setUniqueDocumentId("doc3");
        testDocument3.setMemberId(2);
        testDocument3.setDocumentCategory(100);
        testDocument3.setDocumentSubCategory(200);
        testDocument3.setCreatedAt(now.minusSeconds(7200));
        testDocument3.setCreatedBy("user2");
    }

    // ==================== getDocumentsByMemberId Tests ====================

    @Test
    @DisplayName("Should return slice of documents for valid memberId")
    void testGetDocumentsByMemberId_Success() {
        // Given
        Integer memberId = 1;
        Pageable pageable = PageRequest.of(0, 10);
        List<DocumentMetadata> documents = Arrays.asList(testDocument1, testDocument2);
        Slice<DocumentMetadata> expectedSlice = new SliceImpl<>(documents, pageable, false);

        when(documentMetadataRepository.findByMemberId(memberId, pageable)).thenReturn(expectedSlice);

        // When
        Slice<DocumentMetadata> result = documentMetadataService.getDocumentsByMemberId(memberId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.getContent()).containsExactly(testDocument1, testDocument2);
        verify(documentMetadataRepository, times(1)).findByMemberId(memberId, pageable);
    }

    @Test
    @DisplayName("Should indicate hasNext when more pages available")
    void testGetDocumentsByMemberId_HasNextPage() {
        // Given
        Integer memberId = 1;
        Pageable pageable = PageRequest.of(0, 10);
        List<DocumentMetadata> documents = Arrays.asList(testDocument1, testDocument2);
        Slice<DocumentMetadata> expectedSlice = new SliceImpl<>(documents, pageable, true); // hasNext = true

        when(documentMetadataRepository.findByMemberId(memberId, pageable)).thenReturn(expectedSlice);

        // When
        Slice<DocumentMetadata> result = documentMetadataService.getDocumentsByMemberId(memberId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.hasNext()).isTrue();
        assertThat(result.nextPageable().getPageNumber()).isEqualTo(1);
        verify(documentMetadataRepository, times(1)).findByMemberId(memberId, pageable);
    }

    @Test
    @DisplayName("Should return empty slice when no documents found")
    void testGetDocumentsByMemberId_EmptyResult() {
        // Given
        Integer memberId = 999;
        Pageable pageable = PageRequest.of(0, 10);
        Slice<DocumentMetadata> expectedSlice = new SliceImpl<>(Collections.emptyList(), pageable, false);

        when(documentMetadataRepository.findByMemberId(memberId, pageable)).thenReturn(expectedSlice);

        // When
        Slice<DocumentMetadata> result = documentMetadataService.getDocumentsByMemberId(memberId, pageable);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        verify(documentMetadataRepository, times(1)).findByMemberId(memberId, pageable);
    }

    // ==================== getDocumentsByMemberIdAndDateRange Tests ====================

    @Test
    @DisplayName("Should return documents within date range")
    void testGetDocumentsByMemberIdAndDateRange_Success() {
        // Given
        Integer memberId = 1;
        Instant startDate = Instant.now().minusSeconds(86400); // 1 day ago
        Instant endDate = Instant.now();
        List<DocumentMetadata> expectedDocuments = Arrays.asList(testDocument1, testDocument2);

        when(documentMetadataRepository.findByMemberIdAndCreatedAtBetween(memberId, startDate, endDate))
                .thenReturn(expectedDocuments);

        // When
        List<DocumentMetadata> result = documentMetadataService.getDocumentsByMemberIdAndDateRange(memberId, startDate, endDate);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(testDocument1, testDocument2);
        verify(documentMetadataRepository, times(1)).findByMemberIdAndCreatedAtBetween(memberId, startDate, endDate);
    }

    @Test
    @DisplayName("Should return empty list when no documents in date range")
    void testGetDocumentsByMemberIdAndDateRange_EmptyResult() {
        // Given
        Integer memberId = 1;
        Instant startDate = Instant.now().minusSeconds(172800); // 2 days ago
        Instant endDate = Instant.now().minusSeconds(86400); // 1 day ago

        when(documentMetadataRepository.findByMemberIdAndCreatedAtBetween(memberId, startDate, endDate))
                .thenReturn(Collections.emptyList());

        // When
        List<DocumentMetadata> result = documentMetadataService.getDocumentsByMemberIdAndDateRange(memberId, startDate, endDate);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
        verify(documentMetadataRepository, times(1)).findByMemberIdAndCreatedAtBetween(memberId, startDate, endDate);
    }

    // ==================== getDocumentsByMemberIdAndCategoriesIn Tests ====================

    @Test
    @DisplayName("Should return documents matching categories")
    void testGetDocumentsByMemberIdAndCategoriesIn_Success() {
        // Given
        Integer memberId = 1;
        List<Integer> categories = Arrays.asList(100, 101);
        List<DocumentMetadata> expectedDocuments = Arrays.asList(testDocument1, testDocument2);

        when(documentMetadataRepository.findByMemberIdAndDocumentCategoryIn(memberId, categories))
                .thenReturn(expectedDocuments);

        // When
        List<DocumentMetadata> result = documentMetadataService.getDocumentsByMemberIdAndCategoriesIn(memberId, categories);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(testDocument1, testDocument2);
        verify(documentMetadataRepository, times(1)).findByMemberIdAndDocumentCategoryIn(memberId, categories);
    }

    @Test
    @DisplayName("Should return empty list when no documents match categories")
    void testGetDocumentsByMemberIdAndCategoriesIn_EmptyResult() {
        // Given
        Integer memberId = 1;
        List<Integer> categories = List.of(999);

        when(documentMetadataRepository.findByMemberIdAndDocumentCategoryIn(memberId, categories))
                .thenReturn(Collections.emptyList());

        // When
        List<DocumentMetadata> result = documentMetadataService.getDocumentsByMemberIdAndCategoriesIn(memberId, categories);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
        verify(documentMetadataRepository, times(1)).findByMemberIdAndDocumentCategoryIn(memberId, categories);
    }

    // ==================== getDocumentsByMemberIdAndSubCategoriesIn Tests ====================

    @Test
    @DisplayName("Should return documents matching sub-categories")
    void testGetDocumentsByMemberIdAndSubCategoriesIn_Success() {
        // Given
        Integer memberId = 1;
        List<Integer> subCategories = Arrays.asList(200, 201);
        List<DocumentMetadata> expectedDocuments = Arrays.asList(testDocument1, testDocument2);

        when(documentMetadataRepository.findByMemberIdAndDocumentSubCategoryIn(memberId, subCategories))
                .thenReturn(expectedDocuments);

        // When
        List<DocumentMetadata> result = documentMetadataService.getDocumentsByMemberIdAndSubCategoriesIn(memberId, subCategories);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(testDocument1, testDocument2);
        verify(documentMetadataRepository, times(1)).findByMemberIdAndDocumentSubCategoryIn(memberId, subCategories);
    }

    @Test
    @DisplayName("Should return empty list when no documents match sub-categories")
    void testGetDocumentsByMemberIdAndSubCategoriesIn_EmptyResult() {
        // Given
        Integer memberId = 1;
        List<Integer> subCategories = List.of(999);

        when(documentMetadataRepository.findByMemberIdAndDocumentSubCategoryIn(memberId, subCategories))
                .thenReturn(Collections.emptyList());

        // When
        List<DocumentMetadata> result = documentMetadataService.getDocumentsByMemberIdAndSubCategoriesIn(memberId, subCategories);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
        verify(documentMetadataRepository, times(1)).findByMemberIdAndDocumentSubCategoryIn(memberId, subCategories);
    }

    // ==================== getDocumentById Tests ====================

    @Test
    @DisplayName("Should return document when found by unique ID")
    void testGetDocumentById_Found() {
        // Given
        String uniqueDocumentId = "doc1";
        when(documentMetadataRepository.findByUniqueDocumentId(uniqueDocumentId))
                .thenReturn(Optional.of(testDocument1));

        // When
        Optional<DocumentMetadata> result = documentMetadataService.getDocumentById(uniqueDocumentId);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(testDocument1);
        verify(documentMetadataRepository, times(1)).findByUniqueDocumentId(uniqueDocumentId);
    }

    @Test
    @DisplayName("Should return empty when document not found by unique ID")
    void testGetDocumentById_NotFound() {
        // Given
        String uniqueDocumentId = "nonexistent";
        when(documentMetadataRepository.findByUniqueDocumentId(uniqueDocumentId))
                .thenReturn(Optional.empty());

        // When
        Optional<DocumentMetadata> result = documentMetadataService.getDocumentById(uniqueDocumentId);

        // Then
        assertThat(result).isEmpty();
        verify(documentMetadataRepository, times(1)).findByUniqueDocumentId(uniqueDocumentId);
    }

    // ==================== saveDocument Tests ====================

    @Test
    @DisplayName("Should save document successfully")
    void testSaveDocument_Success() {
        // Given
        DocumentMetadata documentToSave = testDocument1;
        when(documentMetadataRepository.save(documentToSave)).thenReturn(documentToSave);

        // When
        DocumentMetadata result = documentMetadataService.saveDocument(documentToSave);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(documentToSave);
        verify(documentMetadataRepository, times(1)).save(documentToSave);
    }

    // ==================== deleteDocument Tests ====================

    @Test
    @DisplayName("Should delete document by unique ID")
    void testDeleteDocument_Success() {
        // Given
        String uniqueDocumentId = "doc1";
        doNothing().when(documentMetadataRepository).deleteById(uniqueDocumentId);

        // When
        documentMetadataService.deleteDocument(uniqueDocumentId);

        // Then
        verify(documentMetadataRepository, times(1)).deleteById(uniqueDocumentId);
    }
}
