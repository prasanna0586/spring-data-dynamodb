package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.socialsignin.spring.data.dynamodb.repository.EnableScan;
import org.socialsignin.spring.data.dynamodb.repository.EnableScanCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;

/**
 * Repository for FileMetadata demonstrating binary and Set type operations.
 */

public interface FileMetadataRepository extends CrudRepository<FileMetadata, String> {

    /**
     * Find files by uploader with pagination.
     * Scan operation.
     */
    @EnableScan
    @EnableScanCount
    Page<FileMetadata> findByUploadedBy(String uploadedBy, Pageable pageable);

    @EnableScanCount
    @Override
    long count();

    @EnableScan
    @Override
    void deleteAll();

}
