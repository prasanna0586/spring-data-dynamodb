package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.repository.CrudRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for DocumentMetadata entity.
 * Demonstrates query method derivation, pagination, and custom queries.
 */
public interface DocumentMetadataRepository extends CrudRepository<DocumentMetadata, String>, DocumentMetadataRepositoryCustom {

    /**
     * Find by uniqueDocumentId (primary key) - efficient query.
     *
     * @param uniqueDocumentId the unique document identifier
     * @return Optional containing the document if found
     */
    Optional<DocumentMetadata> findByUniqueDocumentId(String uniqueDocumentId);

    /**
     * Find by memberId with pagination - uses memberId-createdAt-index GSI (hash key only, efficient).
     * Returns a Slice for memory efficiency and better handling of large result sets.
     *
     * @param memberId the member ID to search for
     * @param pageable pagination information (page number, size, sort)
     * @return Slice of documents for the member
     */
    Slice<DocumentMetadata> findByMemberId(Integer memberId, Pageable pageable);

    /**
     * Find by memberId and createdAt between - uses memberId-createdAt-index GSI efficiently.
     *
     * @param memberId the member ID to search for
     * @param startDate start of the date range
     * @param endDate end of the date range
     * @return list of documents within the date range
     */
    List<DocumentMetadata> findByMemberIdAndCreatedAtBetween(Integer memberId, Instant startDate, Instant endDate);

    // Note: The following are implemented in DocumentMetadataRepositoryImpl using custom queries:
    // - findByMemberIdAndDocumentCategoryIn (uses memberId-documentCategory-index GSI with range key condition, parallel queries)
    // - findByMemberIdAndDocumentSubCategoryIn (uses memberId-documentSubCategory-index GSI with range key condition, parallel queries)
}
