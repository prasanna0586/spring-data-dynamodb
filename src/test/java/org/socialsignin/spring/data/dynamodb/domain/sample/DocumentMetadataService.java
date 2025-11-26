package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for DocumentMetadata operations.
 * Demonstrates optimistic locking exception handling and business logic.
 */
@SuppressWarnings("unused") // Public API methods may not be used yet but are part of the service contract
public class DocumentMetadataService {

    private static final Logger log = LoggerFactory.getLogger(DocumentMetadataService.class);

    private final DocumentMetadataRepository documentMetadataRepository;

    public DocumentMetadataService(DocumentMetadataRepository documentMetadataRepository) {
        this.documentMetadataRepository = documentMetadataRepository;
    }

    /**
     * Find documents by memberId with pagination support.
     * The returned Slice contains pagination information for fetching next pages.
     *
     * @param memberId the member ID to search for
     * @param pageable pagination information (page number, size, sort)
     * @return Slice of DocumentMetadata with pagination info
     */
    public Slice<DocumentMetadata> getDocumentsByMemberId(Integer memberId, Pageable pageable) {
        log.info("Entering getDocumentsByMemberId - memberId: {}, page: {}, size: {}",
                memberId, pageable.getPageNumber(), pageable.getPageSize());

        Slice<DocumentMetadata> documentSlice = documentMetadataRepository.findByMemberId(memberId, pageable);

        // Check if there are more pages available
        if (documentSlice.hasNext()) {
            Pageable nextPageable = documentSlice.nextPageable();
            log.info("More documents available for memberId: {}. Next page: {}",
                    memberId, nextPageable.getPageNumber());
        }

        log.info("Exiting getDocumentsByMemberId - memberId: {}, resultCount: {}, hasNext: {}",
                memberId, documentSlice.getNumberOfElements(), documentSlice.hasNext());

        return documentSlice;
    }

    /**
     * Find documents by memberId within a date range.
     *
     * @param memberId the member ID to search for
     * @param startDate start of the date range
     * @param endDate end of the date range
     * @return List of DocumentMetadata within the date range
     */
    public List<DocumentMetadata> getDocumentsByMemberIdAndDateRange(Integer memberId, Instant startDate, Instant endDate) {
        log.info("Entering getDocumentsByMemberIdAndDateRange - memberId: {}, startDate: {}, endDate: {}",
                memberId, startDate, endDate);

        List<DocumentMetadata> documents = documentMetadataRepository.findByMemberIdAndCreatedAtBetween(memberId, startDate, endDate);

        log.info("Exiting getDocumentsByMemberIdAndDateRange - memberId: {}, resultCount: {}",
                memberId, documents.size());

        return documents;
    }

    /**
     * Find documents by memberId and multiple document categories.
     * Uses parallel queries for efficiency with custom repository implementation.
     *
     * @param memberId the member ID to search for
     * @param documentCategories list of document categories
     * @return List of DocumentMetadata matching any of the categories
     */
    public List<DocumentMetadata> getDocumentsByMemberIdAndCategoriesIn(Integer memberId, List<Integer> documentCategories) {
        log.info("Entering getDocumentsByMemberIdAndCategoriesIn - memberId: {}, categories: {}",
                memberId, documentCategories);

        List<DocumentMetadata> documents = documentMetadataRepository.findByMemberIdAndDocumentCategoryIn(memberId, documentCategories);

        log.info("Exiting getDocumentsByMemberIdAndCategoriesIn - memberId: {}, resultCount: {}",
                memberId, documents.size());

        return documents;
    }

    /**
     * Find documents by memberId and multiple document sub-categories.
     * Uses parallel queries for efficiency with custom repository implementation.
     *
     * @param memberId the member ID to search for
     * @param documentSubCategories list of document sub-categories
     * @return List of DocumentMetadata matching any of the sub-categories
     */
    public List<DocumentMetadata> getDocumentsByMemberIdAndSubCategoriesIn(Integer memberId, List<Integer> documentSubCategories) {
        log.info("Entering getDocumentsByMemberIdAndSubCategoriesIn - memberId: {}, subCategories: {}",
                memberId, documentSubCategories);

        List<DocumentMetadata> documents = documentMetadataRepository.findByMemberIdAndDocumentSubCategoryIn(memberId, documentSubCategories);

        log.info("Exiting getDocumentsByMemberIdAndSubCategoriesIn - memberId: {}, resultCount: {}",
                memberId, documents.size());

        return documents;
    }

    /**
     * Find a document by its unique document ID.
     *
     * @param uniqueDocumentId the unique document identifier
     * @return Optional containing the DocumentMetadata if found
     */
    public Optional<DocumentMetadata> getDocumentById(String uniqueDocumentId) {
        log.info("Entering getDocumentById - uniqueDocumentId: {}", uniqueDocumentId);

        Optional<DocumentMetadata> document = documentMetadataRepository.findByUniqueDocumentId(uniqueDocumentId);

        log.info("Exiting getDocumentById - uniqueDocumentId: {}, found: {}",
                uniqueDocumentId, document.isPresent());

        return document;
    }

    /**
     * Save a document metadata.
     * Handles optimistic locking conflicts by catching ConditionalCheckFailedException.
     *
     * @param documentMetadata the document metadata to save
     * @return the saved DocumentMetadata
     * @throws OptimisticLockingException if the document was modified by another user
     */
    public DocumentMetadata saveDocument(DocumentMetadata documentMetadata) {
        log.info("Entering saveDocument - uniqueDocumentId: {}, memberId: {}, version: {}",
                documentMetadata.getUniqueDocumentId(), documentMetadata.getMemberId(),
                documentMetadata.getVersion());

        try {
            DocumentMetadata savedDocument = documentMetadataRepository.save(documentMetadata);
            log.info("Exiting saveDocument - uniqueDocumentId: {}, new version: {}",
                    savedDocument.getUniqueDocumentId(), savedDocument.getVersion());
            return savedDocument;
        } catch (ConditionalCheckFailedException e) {
            log.warn("Optimistic locking conflict for document: {}, attempted version: {}",
                    documentMetadata.getUniqueDocumentId(), documentMetadata.getVersion());
            throw new OptimisticLockingException(
                    "Document was modified by another user. Please refresh and try again.",
                    documentMetadata.getUniqueDocumentId(),
                    documentMetadata.getVersion(),
                    e);
        }
    }

    /**
     * Delete a document by its unique document ID.
     *
     * @param uniqueDocumentId the unique document identifier
     */
    public void deleteDocument(String uniqueDocumentId) {
        log.info("Entering deleteDocument - uniqueDocumentId: {}", uniqueDocumentId);

        documentMetadataRepository.deleteById(uniqueDocumentId);

        log.info("Exiting deleteDocument - uniqueDocumentId: {}", uniqueDocumentId);
    }
}
