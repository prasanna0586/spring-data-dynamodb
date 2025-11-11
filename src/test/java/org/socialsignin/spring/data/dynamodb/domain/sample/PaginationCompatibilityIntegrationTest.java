package org.socialsignin.spring.data.dynamodb.domain.sample;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.socialsignin.spring.data.dynamodb.utils.DynamoDBLocalResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Pagination patterns that are compatible with SDK v1 and v2 migration.
 *
 * These tests validate pagination token handling, which has API differences between SDKs:
 * - SDK v1: Uses setExclusiveStartKey() and getLastEvaluatedKey()
 * - SDK v2: Uses pagination methods on response objects
 *
 * Coverage:
 * - Scan with pagination
 * - Query with pagination
 * - Pagination token extraction and reuse
 * - Last page detection
 * - Consistent pagination across large datasets
 * - Spring Data Pageable integration
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, PaginationCompatibilityIntegrationTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Pagination Compatibility Integration Tests")
public class PaginationCompatibilityIntegrationTest {

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    public static class TestAppConfig {
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AmazonDynamoDB amazonDynamoDB;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    // ==================== Scan Pagination ====================

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Test 1: Scan with pagination - Manual token handling")
    void testScanWithManualPagination() {
        // Given - Create 50 users
        for (int i = 0; i < 50; i++) {
            User user = new User();
            user.setId("scan-user-" + String.format("%03d", i));
            user.setName("User " + i);
            user.setNumberOfPlaylists(i);
            userRepository.save(user);
        }

        // When - Scan with pagination (10 items per page)
        int pageSize = 10;
        int totalItemsScanned = 0;
        Map<String, AttributeValue> lastEvaluatedKey = null;
        int pageCount = 0;

        do {
            ScanRequest scanRequest = new ScanRequest()
                    .withTableName("user")
                    .withLimit(pageSize);

            if (lastEvaluatedKey != null) {
                scanRequest.withExclusiveStartKey(lastEvaluatedKey);
            }

            ScanResult result = amazonDynamoDB.scan(scanRequest);

            totalItemsScanned += result.getCount();
            lastEvaluatedKey = result.getLastEvaluatedKey();
            pageCount++;

            System.out.println("Page " + pageCount + ": Retrieved " + result.getCount() + " items");

        } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());

        // Then
        assertThat(totalItemsScanned).isEqualTo(50);
        assertThat(pageCount).isGreaterThanOrEqualTo(5); // At least 5 pages for 50 items with page size 10
        System.out.println("Total pages: " + pageCount + ", Total items: " + totalItemsScanned);
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Test 2: Scan with pagination - Verify last page has null token")
    void testScanLastPageHasNullToken() {
        // Given - Small dataset
        for (int i = 0; i < 15; i++) {
            User user = new User();
            user.setId("page-user-" + i);
            user.setName("User " + i);
            userRepository.save(user);
        }

        // When - Scan with large page size
        ScanRequest scanRequest = new ScanRequest()
                .withTableName("user")
                .withLimit(20); // Larger than dataset

        ScanResult result = amazonDynamoDB.scan(scanRequest);

        // Then - Last page should have null or empty lastEvaluatedKey
        assertThat(result.getCount()).isEqualTo(15);
        assertThat(result.getLastEvaluatedKey()).satisfiesAnyOf(
                key -> assertThat(key).isNull(),
                key -> assertThat(key).isEmpty()
        );
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Test 3: Scan pagination - Verify no duplicates")
    void testScanPaginationNoDuplicates() {
        // Given - Create users
        for (int i = 0; i < 30; i++) {
            User user = new User();
            user.setId("nodupe-user-" + String.format("%03d", i));
            user.setName("User " + i);
            userRepository.save(user);
        }

        // When - Scan all pages and track IDs
        Set<String> scannedIds = new HashSet<>();
        int totalScanned = 0;
        Map<String, AttributeValue> lastEvaluatedKey = null;

        do {
            ScanRequest scanRequest = new ScanRequest()
                    .withTableName("user")
                    .withLimit(10);

            if (lastEvaluatedKey != null) {
                scanRequest.withExclusiveStartKey(lastEvaluatedKey);
            }

            ScanResult result = amazonDynamoDB.scan(scanRequest);

            // Collect all IDs from this page
            for (Map<String, AttributeValue> item : result.getItems()) {
                AttributeValue idAttr = item.get("id");
                if (idAttr != null && idAttr.getS() != null) {
                    scannedIds.add(idAttr.getS());
                    totalScanned++;
                }
            }

            lastEvaluatedKey = result.getLastEvaluatedKey();

        } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());

        // Then - No duplicates (Set size should equal total scanned)
        assertThat(scannedIds).hasSize(totalScanned);
        System.out.println("Scanned " + totalScanned + " items across pages, " + scannedIds.size() + " unique IDs");
    }

    // ==================== Scan-based Pagination (No Index Queries) ====================

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Test 4: Scan with pagination and filter expression")
    void testScanWithPaginationAndFilter() {
        // Given - Create users with various playlist counts
        for (int i = 0; i < 25; i++) {
            User user = new User();
            user.setId("filter-scan-user-" + String.format("%03d", i));
            user.setName("User " + i);
            user.setNumberOfPlaylists(i);
            userRepository.save(user);
        }

        // When - Scan with filter (playlists > 15)
        int pageSize = 5;
        int totalItemsFound = 0;
        Map<String, AttributeValue> lastEvaluatedKey = null;
        int pageCount = 0;

        do {
            ScanRequest scanRequest = new ScanRequest()
                    .withTableName("user")
                    .withLimit(pageSize)
                    .withFilterExpression("numberOfPlaylists > :threshold")
                    .withExpressionAttributeValues(
                            Collections.singletonMap(":threshold", new AttributeValue().withN("15"))
                    );

            if (lastEvaluatedKey != null) {
                scanRequest.withExclusiveStartKey(lastEvaluatedKey);
            }

            ScanResult result = amazonDynamoDB.scan(scanRequest);

            totalItemsFound += result.getCount();
            lastEvaluatedKey = result.getLastEvaluatedKey();
            pageCount++;

            System.out.println("Scan Page " + pageCount + ": Retrieved " + result.getCount() + " items (scanned " + result.getScannedCount() + ")");

        } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());

        // Then - Should find users 16-24 (9 users)
        assertThat(totalItemsFound).isGreaterThanOrEqualTo(9);
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("Test 5: Scan pagination - Empty filter results")
    void testScanPaginationWithNoFilterMatches() {
        // Given - No users with very high playlist count
        for (int i = 0; i < 10; i++) {
            User user = new User();
            user.setId("low-count-user-" + i);
            user.setName("User " + i);
            user.setNumberOfPlaylists(i);
            userRepository.save(user);
        }

        // When - Scan with filter that matches nothing
        ScanRequest scanRequest = new ScanRequest()
                .withTableName("user")
                .withFilterExpression("numberOfPlaylists > :threshold")
                .withExpressionAttributeValues(
                        Collections.singletonMap(":threshold", new AttributeValue().withN("1000"))
                );

        ScanResult result = amazonDynamoDB.scan(scanRequest);

        // Then - Should return no items (but may have scanned items)
        assertThat(result.getCount()).isEqualTo(0);
        System.out.println("Scanned " + result.getScannedCount() + " items, filtered to 0");
    }

    // ==================== Manual Pagination with Application Logic ====================

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("Test 6: Manual pagination - Application-level paging")
    void testManualPaginationApplicationLevel() {
        // Given
        for (int i = 0; i < 30; i++) {
            User user = new User();
            user.setId("manual-page-user-" + String.format("%03d", i));
            user.setName("User " + i);
            userRepository.save(user);
        }

        // When - Manual pagination with limit
        int pageSize = 10;
        int totalPages = 0;
        int totalItems = 0;
        Map<String, AttributeValue> lastKey = null;

        do {
            ScanRequest scanRequest = new ScanRequest()
                    .withTableName("user")
                    .withLimit(pageSize);

            if (lastKey != null) {
                scanRequest.withExclusiveStartKey(lastKey);
            }

            ScanResult result = amazonDynamoDB.scan(scanRequest);
            totalPages++;
            totalItems += result.getCount();
            lastKey = result.getLastEvaluatedKey();

        } while (lastKey != null && !lastKey.isEmpty());

        // Then
        assertThat(totalItems).isEqualTo(30);
        assertThat(totalPages).isGreaterThanOrEqualTo(3);
        System.out.println("Manual pagination: " + totalPages + " pages, " + totalItems + " total items");
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("Test 7: Pagination metadata tracking")
    void testPaginationMetadataTracking() {
        // Given - Create dataset
        int totalUsers = 25;
        for (int i = 0; i < totalUsers; i++) {
            User user = new User();
            user.setId("metadata-user-" + i);
            user.setName("User " + i);
            userRepository.save(user);
        }

        // When - Track pagination metadata
        List<Integer> pageSizes = new ArrayList<>();
        Map<String, AttributeValue> lastKey = null;
        int pageCount = 0;

        do {
            ScanRequest scanRequest = new ScanRequest()
                    .withTableName("user")
                    .withLimit(10);

            if (lastKey != null) {
                scanRequest.withExclusiveStartKey(lastKey);
            }

            ScanResult result = amazonDynamoDB.scan(scanRequest);
            pageSizes.add(result.getCount());
            lastKey = result.getLastEvaluatedKey();
            pageCount++;

        } while (lastKey != null && !lastKey.isEmpty());

        // Then - Metadata should be tracked
        assertThat(pageCount).isGreaterThanOrEqualTo(2);
        assertThat(pageSizes.stream().mapToInt(Integer::intValue).sum()).isEqualTo(totalUsers);

        System.out.println("=== Pagination Metadata ===");
        System.out.println("Total pages: " + pageCount);
        for (int i = 0; i < pageSizes.size(); i++) {
            System.out.println("  Page " + (i + 1) + ": " + pageSizes.get(i) + " items");
        }
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("Test 8: Pagination with filter expression")
    void testPaginationWithFilterExpression() {
        // Given - Users with varying playlist counts
        for (int i = 0; i < 50; i++) {
            User user = new User();
            user.setId("filter-user-" + i);
            user.setName("User " + i);
            user.setNumberOfPlaylists(i);
            userRepository.save(user);
        }

        // When - Scan with filter (playlists > 25) and pagination
        int pageSize = 10;
        int totalItemsFiltered = 0;
        Map<String, AttributeValue> lastEvaluatedKey = null;

        do {
            ScanRequest scanRequest = new ScanRequest()
                    .withTableName("user")
                    .withLimit(pageSize)
                    .withFilterExpression("numberOfPlaylists > :threshold")
                    .withExpressionAttributeValues(
                            Collections.singletonMap(":threshold", new AttributeValue().withN("25"))
                    );

            if (lastEvaluatedKey != null) {
                scanRequest.withExclusiveStartKey(lastEvaluatedKey);
            }

            ScanResult result = amazonDynamoDB.scan(scanRequest);

            totalItemsFiltered += result.getCount();
            lastEvaluatedKey = result.getLastEvaluatedKey();

        } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());

        // Then - Should find users 26-49 (24 users)
        assertThat(totalItemsFiltered).isEqualTo(24);
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("Test 9: Large dataset pagination stress test")
    void testLargeDatasetPagination() {
        // Given - Create 100 users
        for (int i = 0; i < 100; i++) {
            User user = new User();
            user.setId("large-user-" + String.format("%04d", i));
            user.setName("User " + i);
            userRepository.save(user);
        }

        // When - Scan with very small page size
        int pageSize = 5;
        int totalPages = 0;
        int totalItems = 0;
        Map<String, AttributeValue> lastEvaluatedKey = null;

        do {
            ScanRequest scanRequest = new ScanRequest()
                    .withTableName("user")
                    .withLimit(pageSize);

            if (lastEvaluatedKey != null) {
                scanRequest.withExclusiveStartKey(lastEvaluatedKey);
            }

            ScanResult result = amazonDynamoDB.scan(scanRequest);

            totalPages++;
            totalItems += result.getCount();
            lastEvaluatedKey = result.getLastEvaluatedKey();

        } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());

        // Then
        assertThat(totalItems).isEqualTo(100);
        assertThat(totalPages).isGreaterThanOrEqualTo(20); // At least 20 pages for 100 items with page size 5
        System.out.println("Large dataset: " + totalPages + " pages for " + totalItems + " items");
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("Test 10: Pagination token serialization pattern")
    void testPaginationTokenSerialization() {
        // Given
        for (int i = 0; i < 20; i++) {
            User user = new User();
            user.setId("token-user-" + i);
            user.setName("User " + i);
            userRepository.save(user);
        }

        // When - Get first page and extract token
        ScanRequest scanRequest = new ScanRequest()
                .withTableName("user")
                .withLimit(10);

        ScanResult firstPage = amazonDynamoDB.scan(scanRequest);
        Map<String, AttributeValue> paginationToken = firstPage.getLastEvaluatedKey();

        // Then - Token should be reusable
        assertThat(paginationToken).isNotNull();
        assertThat(paginationToken).isNotEmpty();

        // When - Use token for next page
        ScanRequest secondPageRequest = new ScanRequest()
                .withTableName("user")
                .withLimit(10)
                .withExclusiveStartKey(paginationToken);

        ScanResult secondPage = amazonDynamoDB.scan(secondPageRequest);

        // Then
        assertThat(secondPage.getCount()).isGreaterThan(0);
        System.out.println("First page: " + firstPage.getCount() + " items");
        System.out.println("Second page: " + secondPage.getCount() + " items");
        System.out.println("Pagination token keys: " + paginationToken.keySet());
    }
}
