package org.socialsignin.spring.data.dynamodb.domain.sample;

import org.socialsignin.spring.data.dynamodb.repository.EnableScan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

/**
 * Repository for FileMetadata demonstrating binary and Set type operations.
 */
@EnableScan
public interface FileMetadataRepository extends CrudRepository<FileMetadata, String> {

    /**
     * Find files by content type.
     * Scan operation.
     */
    @EnableScan
    List<FileMetadata> findByContentType(String contentType);

    /**
     * Find files by uploader with pagination.
     * Scan operation.
     */
    @EnableScan
    @org.socialsignin.spring.data.dynamodb.repository.EnableScanCount
    Page<FileMetadata> findByUploadedBy(String uploadedBy, Pageable pageable);

    /**
     * Find files by file name.
     * Scan operation.
     */
    @EnableScan
    List<FileMetadata> findByFileName(String fileName);
}
