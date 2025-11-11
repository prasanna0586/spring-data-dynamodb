package org.socialsignin.spring.data.dynamodb.domain.sample;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.socialsignin.spring.data.dynamodb.repository.config.EnableDynamoDBRepositories;
import org.socialsignin.spring.data.dynamodb.utils.DynamoDBLocalResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive integration tests for Time To Live (TTL) functionality.
 *
 * TTL in AWS SDK v1:
 * - TTL is configured programmatically using UpdateTimeToLiveRequest
 * - Not via annotations (annotations are SDK v2 only)
 * - TTL attribute must contain Unix timestamp in seconds
 * - Items are automatically deleted after expiration time (in production)
 * - DynamoDB Local may not actually delete expired items
 *
 * Coverage:
 * - Enabling TTL on a table
 * - Checking TTL status
 * - Storing TTL attributes
 * - Querying based on expiration time
 * - Multiple sessions with different TTL values
 * - Updating TTL values
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, TimeToLiveIntegrationTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Time To Live (TTL) Integration Tests")
public class TimeToLiveIntegrationTest {

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    public static class TestAppConfig {
    }

    @Autowired
    private SessionDataRepository sessionDataRepository;

    @Autowired
    private DynamoDbClient amazonDynamoDB;

    private static final String TABLE_NAME = "SessionData";
    private static final String TTL_ATTRIBUTE = "expirationTime";

    @BeforeEach
    void setUp() {
        sessionDataRepository.deleteAll();
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Test 1: Enable TTL on table using UpdateTimeToLiveRequest")
    void testEnableTTL() {
        // Given - TTL specification
        TimeToLiveSpecification ttlSpec = TimeToLiveSpecification.builder()
                .attributeName(TTL_ATTRIBUTE)
                .enabled(true)
                .build();

        UpdateTimeToLiveRequest request = UpdateTimeToLiveRequest.builder()
                .tableName(TABLE_NAME)
                .timeToLiveSpecification(ttlSpec)
                .build();

        // When - Enable TTL
        UpdateTimeToLiveResponse result = amazonDynamoDB.updateTimeToLive(request);

        // Then - Verify TTL configuration
        assertThat(result.timeToLiveSpecification()).isNotNull();
        assertThat(result.timeToLiveSpecification().attributeName()).isEqualTo(TTL_ATTRIBUTE);
        assertThat(result.timeToLiveSpecification().enabled()).isTrue();
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Test 2: Describe TTL status using DescribeTimeToLiveRequest")
    void testDescribeTTLStatus() {
        // Given - Enable TTL first
        enableTTL();

        // When - Describe TTL
        DescribeTimeToLiveRequest request = DescribeTimeToLiveRequest.builder()
                .tableName(TABLE_NAME)
                .build();
        DescribeTimeToLiveResponse result = amazonDynamoDB.describeTimeToLive(request);

        // Then - Verify TTL is enabled
        assertThat(result.timeToLiveDescription()).isNotNull();
        assertThat(result.timeToLiveDescription().attributeName()).isEqualTo(TTL_ATTRIBUTE);
        // Status could be ENABLING or ENABLED depending on timing
        assertThat(result.timeToLiveDescription().timeToLiveStatus())
                .isIn("ENABLING", "ENABLED");
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Test 3: Store session data with TTL attribute")
    void testStoreSessionWithTTL() {
        // Given - Session that expires in 1 hour
        long expirationTime = Instant.now().plus(1, ChronoUnit.HOURS).getEpochSecond();
        SessionData session = new SessionData("session-001", "user-001",
                expirationTime, Instant.now(), "Session data");

        // When
        sessionDataRepository.save(session);

        // Then - Verify session is stored with TTL
        SessionData retrieved = sessionDataRepository.findById("session-001").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getExpirationTime()).isEqualTo(expirationTime);
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Test 4: Query active sessions (not expired)")
    void testQueryActiveSessions() {
        // Given - Sessions with different expiration times
        long now = Instant.now().getEpochSecond();
        long future = Instant.now().plus(1, ChronoUnit.HOURS).getEpochSecond();
        long past = Instant.now().minus(1, ChronoUnit.HOURS).getEpochSecond();

        sessionDataRepository.save(new SessionData("session-001", "user-001",
                future, Instant.now(), "Active session 1"));
        sessionDataRepository.save(new SessionData("session-002", "user-001",
                future, Instant.now(), "Active session 2"));
        sessionDataRepository.save(new SessionData("session-003", "user-001",
                past, Instant.now(), "Expired session"));

        // When - Query active sessions (expiration > now)
        List<SessionData> activeSessions = sessionDataRepository
                .findByExpirationTimeGreaterThan(now);

        // Then - Should return only non-expired sessions
        // Note: DynamoDB Local doesn't auto-delete expired items, so we query by time
        assertThat(activeSessions).hasSize(2);
        assertThat(activeSessions).extracting(SessionData::getSessionId)
                .containsExactlyInAnyOrder("session-001", "session-002");
    }

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("Test 5: Query expired sessions")
    void testQueryExpiredSessions() {
        // Given - Sessions with different expiration times
        long now = Instant.now().getEpochSecond();
        long future = Instant.now().plus(1, ChronoUnit.HOURS).getEpochSecond();
        long past = Instant.now().minus(1, ChronoUnit.HOURS).getEpochSecond();

        sessionDataRepository.save(new SessionData("session-001", "user-001",
                future, Instant.now(), "Active session"));
        sessionDataRepository.save(new SessionData("session-002", "user-001",
                past, Instant.now(), "Expired session 1"));
        sessionDataRepository.save(new SessionData("session-003", "user-001",
                past, Instant.now(), "Expired session 2"));

        // When - Query expired sessions (expiration < now)
        List<SessionData> expiredSessions = sessionDataRepository
                .findByExpirationTimeLessThan(now);

        // Then - Should return expired sessions
        assertThat(expiredSessions).hasSize(2);
        assertThat(expiredSessions).extracting(SessionData::getSessionId)
                .containsExactlyInAnyOrder("session-002", "session-003");
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("Test 6: Update TTL expiration time")
    void testUpdateExpirationTime() {
        // Given - Session with initial expiration
        long initialExpiration = Instant.now().plus(1, ChronoUnit.HOURS).getEpochSecond();
        SessionData session = new SessionData("session-001", "user-001",
                initialExpiration, Instant.now(), "Session data");
        sessionDataRepository.save(session);

        // When - Extend expiration by 2 hours
        long newExpiration = Instant.now().plus(2, ChronoUnit.HOURS).getEpochSecond();
        session.setExpirationTime(newExpiration);
        sessionDataRepository.save(session);

        // Then - Verify expiration is updated
        SessionData updated = sessionDataRepository.findById("session-001").orElse(null);
        assertThat(updated).isNotNull();
        assertThat(updated.getExpirationTime()).isEqualTo(newExpiration);
        assertThat(updated.getExpirationTime()).isGreaterThan(initialExpiration);
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("Test 7: Multiple users with separate TTL sessions")
    void testMultipleUsersWithTTL() {
        // Given - Sessions for different users
        long future = Instant.now().plus(1, ChronoUnit.HOURS).getEpochSecond();

        sessionDataRepository.save(new SessionData("session-001", "user-001",
                future, Instant.now(), "User 1 Session 1"));
        sessionDataRepository.save(new SessionData("session-002", "user-001",
                future, Instant.now(), "User 1 Session 2"));
        sessionDataRepository.save(new SessionData("session-003", "user-002",
                future, Instant.now(), "User 2 Session"));

        // When - Query sessions by user
        List<SessionData> user1Sessions = sessionDataRepository.findByUserId("user-001");
        List<SessionData> user2Sessions = sessionDataRepository.findByUserId("user-002");

        // Then
        assertThat(user1Sessions).hasSize(2);
        assertThat(user2Sessions).hasSize(1);
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("Test 8: TTL with null expiration (never expires)")
    void testNullExpirationTime() {
        // Given - Session without expiration time (null)
        SessionData session = new SessionData("session-001", "user-001",
                null, Instant.now(), "Permanent session");

        // When
        sessionDataRepository.save(session);

        // Then - Session should be stored without TTL
        SessionData retrieved = sessionDataRepository.findById("session-001").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getExpirationTime()).isNull();
    }

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("Test 9: TTL with very short expiration (1 second)")
    void testVeryShortTTL() {
        // Given - Session that expires in 1 second
        long expirationTime = Instant.now().plus(1, ChronoUnit.SECONDS).getEpochSecond();
        SessionData session = new SessionData("session-001", "user-001",
                expirationTime, Instant.now(), "Short-lived session");

        // When
        sessionDataRepository.save(session);

        // Then - Session exists immediately after creation
        SessionData retrieved = sessionDataRepository.findById("session-001").orElse(null);
        assertThat(retrieved).isNotNull();

        // Note: DynamoDB Local doesn't automatically delete expired items
        // In production DynamoDB, this item would be deleted within 48 hours of expiration
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("Test 10: TTL with far future expiration (1 year)")
    void testLongTTL() {
        // Given - Session that expires in 1 year
        long expirationTime = Instant.now().plus(365, ChronoUnit.DAYS).getEpochSecond();
        SessionData session = new SessionData("session-001", "user-001",
                expirationTime, Instant.now(), "Long-lived session");

        // When
        sessionDataRepository.save(session);

        // Then
        SessionData retrieved = sessionDataRepository.findById("session-001").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getExpirationTime()).isEqualTo(expirationTime);
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    @DisplayName("Test 11: Disable TTL on table")
    void testDisableTTL() {
        // Given - TTL is enabled
        enableTTL();

        // When - Disable TTL
        TimeToLiveSpecification ttlSpec = TimeToLiveSpecification.builder()
                .attributeName(TTL_ATTRIBUTE)
                .enabled(false)
                .build();

        UpdateTimeToLiveRequest request = UpdateTimeToLiveRequest.builder()
                .tableName(TABLE_NAME)
                .timeToLiveSpecification(ttlSpec)
                .build();

        UpdateTimeToLiveResponse result = amazonDynamoDB.updateTimeToLive(request);

        // Then - Verify TTL is disabled
        assertThat(result.timeToLiveSpecification()).isNotNull();
        assertThat(result.timeToLiveSpecification().enabled()).isFalse();
    }

    // Helper method to enable TTL
    private void enableTTL() {
        try {
            TimeToLiveSpecification ttlSpec = TimeToLiveSpecification.builder()
                    .attributeName(TTL_ATTRIBUTE)
                    .enabled(true)
                    .build();

            UpdateTimeToLiveRequest request = UpdateTimeToLiveRequest.builder()
                    .tableName(TABLE_NAME)
                    .timeToLiveSpecification(ttlSpec)
                    .build();

            amazonDynamoDB.updateTimeToLive(request);
        } catch (Exception e) {
            // TTL may already be enabled, ignore
        }
    }
}
