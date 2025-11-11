package org.socialsignin.spring.data.dynamodb.domain.sample;

import java.util.List;

/**
 * Custom repository interface for DocumentMetadata with parallel query implementations.
 * The implementation uses CompletableFuture to execute multiple GSI queries in parallel.
 */
public interface DocumentMetadataRepositoryCustom {

    /**
     * Find documents by memberId and a list of document categories.
     * Uses parallel queries on the memberId-documentCategory-index GSI.
     *
     * @param memberId the member ID to search for
     * @param documentCategories list of document categories to match
     * @return list of documents matching any of the provided categories
     */
    List<DocumentMetadata> findByMemberIdAndDocumentCategoryIn(Integer memberId, List<Integer> documentCategories);

    /**
     * Find documents by memberId and a list of document sub-categories.
     * Uses parallel queries on the memberId-documentSubCategory-index GSI.
     *
     * @param memberId the member ID to search for
     * @param documentSubCategories list of document sub-categories to match
     * @return list of documents matching any of the provided sub-categories
     */
    List<DocumentMetadata> findByMemberIdAndDocumentSubCategoryIn(Integer memberId, List<Integer> documentSubCategories);
}
