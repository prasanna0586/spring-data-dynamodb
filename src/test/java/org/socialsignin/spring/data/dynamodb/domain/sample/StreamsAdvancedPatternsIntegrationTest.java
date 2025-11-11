package org.socialsignin.spring.data.dynamodb.domain.sample;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Advanced integration tests for DynamoDB Streams patterns.
 *
 * These tests extend the basic Streams functionality with production patterns:
 * - Change Data Capture (CDC) patterns
 * - Event sourcing patterns
 * - Cross-region replication simulation
 * - Aggregation and materialized views
 * - Audit logging from streams
 * - Stream processing resilience
 *
 * NOTE: DynamoDB Local has limited stream support. These tests demonstrate
 * the patterns and processing logic that would be used with real streams.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, StreamsAdvancedPatternsIntegrationTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("DynamoDB Streams Advanced Patterns Integration Tests")
public class StreamsAdvancedPatternsIntegrationTest {

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    public static class TestAppConfig {
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BankAccountRepository accountRepository;

    @Autowired
    private DynamoDbClient amazonDynamoDB;

    // Simulated stream event store
    private Map<String, List<StreamEvent>> streamEvents = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        accountRepository.deleteAll();
        streamEvents.clear();
    }

    // ==================== Change Data Capture (CDC) Patterns ====================

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Test 1: CDC pattern - Capture all changes to entity")
    void testCDCCaptureAllChanges() {
        // Given - Track changes to a user
        String userId = "cdc-user-1";

        // When - Create user (INSERT event)
        User user = new User();
        user.setId(userId);
        user.setName("Original Name");
        user.setNumberOfPlaylists(10);
        userRepository.save(user);
        recordStreamEvent(userId, "INSERT", null, user);

        // Update 1 (MODIFY event)
        user.setName("First Update");
        user.setNumberOfPlaylists(15);
        User oldState1 = cloneUser(user);
        oldState1.setName("Original Name");
        oldState1.setNumberOfPlaylists(10);
        userRepository.save(user);
        recordStreamEvent(userId, "MODIFY", oldState1, user);

        // Update 2 (MODIFY event)
        User oldState2 = cloneUser(user);
        user.setName("Second Update");
        user.setNumberOfPlaylists(20);
        userRepository.save(user);
        recordStreamEvent(userId, "MODIFY", oldState2, user);

        // Delete (REMOVE event)
        User finalState = cloneUser(user);
        userRepository.deleteById(userId);
        recordStreamEvent(userId, "REMOVE", finalState, null);

        // Then - Should have complete change history
        List<StreamEvent> events = streamEvents.get(userId);
        assertThat(events).hasSize(4);
        assertThat(events.get(0).eventType).isEqualTo("INSERT");
        assertThat(events.get(1).eventType).isEqualTo("MODIFY");
        assertThat(events.get(2).eventType).isEqualTo("MODIFY");
        assertThat(events.get(3).eventType).isEqualTo("REMOVE");

        System.out.println("=== CDC Pattern: Complete Change History ===");
        for (int i = 0; i < events.size(); i++) {
            StreamEvent event = events.get(i);
            System.out.println("Event " + (i + 1) + ": " + event.eventType);
            if (event.oldImage != null) {
                System.out.println("  Old: " + event.oldImage.getName() + " (playlists=" + event.oldImage.getNumberOfPlaylists() + ")");
            }
            if (event.newImage != null) {
                System.out.println("  New: " + event.newImage.getName() + " (playlists=" + event.newImage.getNumberOfPlaylists() + ")");
            }
        }
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Test 2: CDC pattern - Detect specific field changes")
    void testCDCDetectSpecificFieldChanges() {
        // Given
        String userId = "field-change-user";
        User user = new User();
        user.setId(userId);
        user.setName("User Name");
        user.setNumberOfPlaylists(10);
        userRepository.save(user);

        // When - Update only numberOfPlaylists
        User oldState = cloneUser(user);
        user.setNumberOfPlaylists(50);
        userRepository.save(user);

        // Process stream event
        Map<String, Object> changes = detectFieldChanges(oldState, user);

        // Then - Should detect only numberOfPlaylists changed
        assertThat(changes).hasSize(1);
        assertThat(changes).containsKey("numberOfPlaylists");
        assertThat(changes.get("numberOfPlaylists")).isEqualTo("10 -> 50");

        System.out.println("=== CDC Pattern: Field-Level Change Detection ===");
        System.out.println("Changed fields:");
        changes.forEach((field, change) -> System.out.println("  " + field + ": " + change));
    }

    // ==================== Event Sourcing Patterns ====================

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Test 3: Event sourcing - Rebuild state from stream events")
    void testEventSourcingRebuildState() {
        // Given - Series of account transactions tracked in event store
        String accountId = "event-sourced-account";
        List<AccountEvent> accountEvents = new ArrayList<>();

        BankAccount account = new BankAccount(accountId, "John Doe", 1000.0);
        accountRepository.save(account);
        accountEvents.add(new AccountEvent("INSERT", null, cloneAccount(account)));

        // Transaction 1: Deposit 500
        BankAccount old1 = cloneAccount(account);
        account.setBalance(1500.0);
        accountRepository.save(account);
        accountEvents.add(new AccountEvent("MODIFY", old1, cloneAccount(account)));

        // Transaction 2: Withdrawal 200
        BankAccount old2 = cloneAccount(account);
        account.setBalance(1300.0);
        accountRepository.save(account);
        accountEvents.add(new AccountEvent("MODIFY", old2, cloneAccount(account)));

        // Transaction 3: Deposit 300
        BankAccount old3 = cloneAccount(account);
        account.setBalance(1600.0);
        accountRepository.save(account);
        accountEvents.add(new AccountEvent("MODIFY", old3, cloneAccount(account)));

        // When - Rebuild state from events
        BankAccount rebuiltState = rebuildAccountStateFromAccountEvents(accountEvents);

        // Then - Rebuilt state should match current state
        assertThat(rebuiltState.getBalance()).isEqualTo(1600.0);
        assertThat(rebuiltState.getAccountHolder()).isEqualTo("John Doe");

        System.out.println("=== Event Sourcing Pattern ===");
        System.out.println("Initial balance: 1000.0");
        System.out.println("After event replay: " + rebuiltState.getBalance());
        System.out.println("Transactions in stream: " + (accountEvents.size() - 1));
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Test 4: Event sourcing - Point-in-time reconstruction")
    void testEventSourcingPointInTimeReconstruction() {
        // Given - Account with multiple transactions tracked in event store
        String accountId = "pit-account";
        List<Double> balanceHistory = new ArrayList<>();
        List<AccountEvent> accountEvents = new ArrayList<>();

        BankAccount account = new BankAccount(accountId, "Jane Doe", 1000.0);
        accountRepository.save(account);
        accountEvents.add(new AccountEvent("INSERT", null, cloneAccount(account)));
        balanceHistory.add(1000.0);

        // 5 transactions
        for (int i = 1; i <= 5; i++) {
            BankAccount oldState = cloneAccount(account);
            double newBalance = 1000.0 + (i * 100);
            account.setBalance(newBalance);
            accountRepository.save(account);
            accountEvents.add(new AccountEvent("MODIFY", oldState, cloneAccount(account)));
            balanceHistory.add(newBalance);
        }

        // When - Reconstruct state at different points in time
        for (int eventIndex = 0; eventIndex < 6; eventIndex++) {
            BankAccount stateAtPoint = rebuildAccountStateFromAccountEvents(accountEvents, eventIndex);
            double expectedBalance = balanceHistory.get(eventIndex);

            System.out.println("State after event " + eventIndex + ": balance=" + stateAtPoint.getBalance());
            assertThat(stateAtPoint.getBalance()).isEqualTo(expectedBalance);
        }

        System.out.println("=== Event Sourcing: Point-in-Time Reconstruction ===");
        System.out.println("Successfully reconstructed state at 6 different points in time");
    }

    // ==================== Aggregation and Materialized Views ====================

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("Test 5: Materialized view - User statistics aggregation")
    void testMaterializedViewUserStatistics() {
        // Given - Multiple users with different playlist counts
        Map<String, Integer> userStats = new HashMap<>();

        for (int i = 0; i < 10; i++) {
            User user = new User();
            user.setId("stats-user-" + i);
            user.setName("User " + i);
            user.setNumberOfPlaylists(i * 5);
            userRepository.save(user);

            // Stream event triggers materialized view update
            updateMaterializedView("user_statistics", "total_playlists", user.getNumberOfPlaylists());
            updateMaterializedView("user_statistics", "user_count", 1);
        }

        // When - Calculate aggregates from materialized view
        int totalUsers = getMaterializedViewValue("user_statistics", "user_count");
        int totalPlaylists = getMaterializedViewValue("user_statistics", "total_playlists");
        double avgPlaylists = totalPlaylists / (double) totalUsers;

        // Then
        assertThat(totalUsers).isEqualTo(10);
        assertThat(totalPlaylists).isEqualTo(225); // 0+5+10+15+20+25+30+35+40+45
        assertThat(avgPlaylists).isEqualTo(22.5);

        System.out.println("=== Materialized View Pattern ===");
        System.out.println("Total users: " + totalUsers);
        System.out.println("Total playlists: " + totalPlaylists);
        System.out.println("Average playlists: " + avgPlaylists);
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("Test 6: Materialized view - Real-time counters")
    void testMaterializedViewRealTimeCounters() {
        // Given - Track operations in real-time
        Map<String, Integer> counters = new HashMap<>();
        counters.put("inserts", 0);
        counters.put("updates", 0);
        counters.put("deletes", 0);

        // When - Simulate stream events
        for (int i = 0; i < 5; i++) {
            User user = new User();
            user.setId("counter-user-" + i);
            user.setName("User " + i);
            userRepository.save(user);
            counters.put("inserts", counters.get("inserts") + 1);
        }

        for (int i = 0; i < 3; i++) {
            Optional<User> userOpt = userRepository.findById("counter-user-" + i);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                user.setName("Updated " + i);
                userRepository.save(user);
                counters.put("updates", counters.get("updates") + 1);
            }
        }

        for (int i = 0; i < 2; i++) {
            userRepository.deleteById("counter-user-" + i);
            counters.put("deletes", counters.get("deletes") + 1);
        }

        // Then
        assertThat(counters.get("inserts")).isEqualTo(5);
        assertThat(counters.get("updates")).isEqualTo(3);
        assertThat(counters.get("deletes")).isEqualTo(2);

        System.out.println("=== Real-Time Counters from Streams ===");
        System.out.println("INSERT events: " + counters.get("inserts"));
        System.out.println("MODIFY events: " + counters.get("updates"));
        System.out.println("REMOVE events: " + counters.get("deletes"));
    }

    // ==================== Audit Logging from Streams ====================

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("Test 7: Audit log - Complete audit trail")
    void testAuditLogCompleteTrail() {
        // Given - Operations that need auditing
        List<AuditEntry> auditLog = new ArrayList<>();

        String accountId = "audited-account";
        BankAccount account = new BankAccount(accountId, "Audit User", 1000.0);
        accountRepository.save(account);
        auditLog.add(new AuditEntry("INSERT", accountId, "Created account with balance 1000.0", Instant.now()));

        // Transaction 1
        account.setBalance(1500.0);
        accountRepository.save(account);
        auditLog.add(new AuditEntry("MODIFY", accountId, "Balance changed: 1000.0 -> 1500.0", Instant.now()));

        // Transaction 2
        account.setBalance(1300.0);
        accountRepository.save(account);
        auditLog.add(new AuditEntry("MODIFY", accountId, "Balance changed: 1500.0 -> 1300.0", Instant.now()));

        // Then - Audit log should have complete history
        assertThat(auditLog).hasSize(3);

        System.out.println("=== Audit Log from Streams ===");
        for (int i = 0; i < auditLog.size(); i++) {
            AuditEntry entry = auditLog.get(i);
            System.out.println((i + 1) + ". [" + entry.eventType + "] " + entry.description +
                    " (" + entry.timestamp + ")");
        }
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("Test 8: Audit log - Compliance reporting")
    void testAuditLogComplianceReporting() {
        // Given - Regulated operations
        List<AuditEntry> auditLog = new ArrayList<>();
        String accountId = "compliance-account";

        BankAccount account = new BankAccount(accountId, "Compliance User", 10000.0);
        accountRepository.save(account);
        auditLog.add(new AuditEntry("INSERT", accountId, "Account created", Instant.now()));

        // Large transaction (needs compliance tracking)
        account.setBalance(5000.0);
        accountRepository.save(account);
        auditLog.add(new AuditEntry("MODIFY", accountId, "Large withdrawal: -5000.0 (Compliance review required)", Instant.now()));

        // When - Generate compliance report
        List<AuditEntry> complianceEvents = auditLog.stream()
                .filter(entry -> entry.description.contains("Compliance") || entry.description.contains("Large"))
                .toList();

        // Then
        assertThat(complianceEvents).hasSize(1);

        System.out.println("=== Compliance Report from Stream Events ===");
        System.out.println("Total events: " + auditLog.size());
        System.out.println("Compliance-flagged events: " + complianceEvents.size());
        complianceEvents.forEach(event ->
                System.out.println("  - " + event.description + " at " + event.timestamp)
        );
    }

    // ==================== Stream Processing Resilience ====================

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("Test 9: Resilience - Duplicate event handling (idempotency)")
    void testResilienceDuplicateEventHandling() {
        // Given - Track processed events
        Set<String> processedEventIds = new HashSet<>();
        int actualProcessingCount = 0;

        // When - Process events (including duplicates)
        List<String> eventIds = Arrays.asList("event-1", "event-2", "event-1", "event-3", "event-2", "event-4");

        for (String eventId : eventIds) {
            if (!processedEventIds.contains(eventId)) {
                // Process event
                processedEventIds.add(eventId);
                actualProcessingCount++;
                System.out.println("Processing event: " + eventId);
            } else {
                System.out.println("Skipping duplicate event: " + eventId);
            }
        }

        // Then - Should process each unique event only once
        assertThat(actualProcessingCount).isEqualTo(4);
        assertThat(processedEventIds).hasSize(4);

        System.out.println("=== Stream Processing Resilience: Idempotency ===");
        System.out.println("Total events received: " + eventIds.size());
        System.out.println("Unique events processed: " + actualProcessingCount);
        System.out.println("Duplicates skipped: " + (eventIds.size() - actualProcessingCount));
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("Test 10: Resilience - Out-of-order event handling")
    void testResilienceOutOfOrderEventHandling() {
        // Given - Events with sequence numbers (received out of order)
        List<StreamEventWithSequence> events = Arrays.asList(
                new StreamEventWithSequence("seq-001", "user-1", 1),
                new StreamEventWithSequence("seq-003", "user-1", 3),  // Out of order
                new StreamEventWithSequence("seq-002", "user-1", 2),  // Gap filler
                new StreamEventWithSequence("seq-004", "user-1", 4)
        );

        // When - Process with sequence number validation (skip events that are out of order)
        Map<String, Long> lastProcessedSequence = new HashMap<>();
        List<StreamEventWithSequence> processedInOrder = new ArrayList<>();

        for (StreamEventWithSequence event : events) {
            Long lastSeq = lastProcessedSequence.getOrDefault(event.partitionKey, 0L);

            if (event.sequenceNumber > lastSeq) {
                processedInOrder.add(event);
                lastProcessedSequence.put(event.partitionKey, event.sequenceNumber);
                System.out.println("Processed event: " + event.eventId + " (seq=" + event.sequenceNumber + ")");
            } else {
                System.out.println("Skipped out-of-order event: " + event.eventId +
                        " (seq=" + event.sequenceNumber + ", expected>" + lastSeq + ")");
            }
        }

        // Then - In strict ordering mode, we process seq-001, seq-003, seq-004
        // seq-002 is skipped because seq-003 was already processed
        assertThat(processedInOrder).hasSize(3);

        System.out.println("=== Stream Processing Resilience: Sequence Handling ===");
        System.out.println("Events processed: " + processedInOrder.size());
        System.out.println("Note: Event seq-002 skipped because seq-003 was already processed (strict ordering)");
    }

    // ==================== Helper Methods and Classes ====================

    private static class StreamEvent {
        String eventType;
        User oldImage;
        User newImage;

        StreamEvent(String eventType, User oldImage, User newImage) {
            this.eventType = eventType;
            this.oldImage = oldImage;
            this.newImage = newImage;
        }
    }

    private static class AuditEntry {
        String eventType;
        String entityId;
        String description;
        Instant timestamp;

        AuditEntry(String eventType, String entityId, String description, Instant timestamp) {
            this.eventType = eventType;
            this.entityId = entityId;
            this.description = description;
            this.timestamp = timestamp;
        }
    }

    private static class StreamEventWithSequence {
        String eventId;
        String partitionKey;
        long sequenceNumber;

        StreamEventWithSequence(String eventId, String partitionKey, long sequenceNumber) {
            this.eventId = eventId;
            this.partitionKey = partitionKey;
            this.sequenceNumber = sequenceNumber;
        }
    }

    private static class AccountEvent {
        String eventType;
        BankAccount oldImage;
        BankAccount newImage;

        AccountEvent(String eventType, BankAccount oldImage, BankAccount newImage) {
            this.eventType = eventType;
            this.oldImage = oldImage;
            this.newImage = newImage;
        }
    }

    private void recordStreamEvent(String entityId, String eventType, Object oldImage, Object newImage) {
        streamEvents.computeIfAbsent(entityId, k -> new ArrayList<>())
                .add(new StreamEvent(eventType, (User) oldImage, (User) newImage));
    }

    private User cloneUser(User user) {
        User clone = new User();
        clone.setId(user.getId());
        clone.setName(user.getName());
        clone.setNumberOfPlaylists(user.getNumberOfPlaylists());
        clone.setPostCode(user.getPostCode());
        return clone;
    }

    private BankAccount cloneAccount(BankAccount account) {
        return new BankAccount(account.getAccountId(), account.getAccountHolder(), account.getBalance());
    }

    private Map<String, Object> detectFieldChanges(User oldImage, User newImage) {
        Map<String, Object> changes = new HashMap<>();

        if (!Objects.equals(oldImage.getName(), newImage.getName())) {
            changes.put("name", oldImage.getName() + " -> " + newImage.getName());
        }
        if (!Objects.equals(oldImage.getNumberOfPlaylists(), newImage.getNumberOfPlaylists())) {
            changes.put("numberOfPlaylists", oldImage.getNumberOfPlaylists() + " -> " + newImage.getNumberOfPlaylists());
        }

        return changes;
    }

    private BankAccount rebuildAccountStateFromEvents(String accountId) {
        return rebuildAccountStateFromEvents(accountId, Integer.MAX_VALUE);
    }

    private BankAccount rebuildAccountStateFromEvents(String accountId, int upToEventIndex) {
        List<StreamEvent> events = streamEvents.get(accountId);
        if (events == null || events.isEmpty()) {
            return null;
        }

        // Start with INSERT event
        BankAccount state = null;
        for (int i = 0; i <= Math.min(upToEventIndex, events.size() - 1); i++) {
            StreamEvent event = events.get(i);
            if ("INSERT".equals(event.eventType)) {
                // Create initial state (using User as proxy for BankAccount in this example)
                state = new BankAccount(accountId, "Rebuilt", 0.0);
            } else if ("MODIFY".equals(event.eventType) && event.newImage != null) {
                // Apply changes (simplified for example)
            }
        }

        return state;
    }

    private BankAccount rebuildAccountStateFromAccountEvents(List<AccountEvent> events) {
        return rebuildAccountStateFromAccountEvents(events, Integer.MAX_VALUE);
    }

    private BankAccount rebuildAccountStateFromAccountEvents(List<AccountEvent> events, int upToEventIndex) {
        if (events == null || events.isEmpty()) {
            return null;
        }

        BankAccount state = null;
        for (int i = 0; i <= Math.min(upToEventIndex, events.size() - 1); i++) {
            AccountEvent event = events.get(i);
            if ("INSERT".equals(event.eventType) && event.newImage != null) {
                // Create initial state from INSERT event
                state = cloneAccount(event.newImage);
            } else if ("MODIFY".equals(event.eventType) && event.newImage != null) {
                // Apply changes from MODIFY event
                state = cloneAccount(event.newImage);
            }
        }

        return state;
    }

    // Simulated materialized view storage
    private Map<String, Map<String, Integer>> materializedViews = new ConcurrentHashMap<>();

    private void updateMaterializedView(String viewName, String metric, int value) {
        materializedViews.computeIfAbsent(viewName, k -> new ConcurrentHashMap<>())
                .merge(metric, value, Integer::sum);
    }

    private int getMaterializedViewValue(String viewName, String metric) {
        return materializedViews.getOrDefault(viewName, Collections.emptyMap())
                .getOrDefault(metric, 0);
    }
}
