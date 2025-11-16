package org.socialsignin.spring.data.dynamodb.domain.sample;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive integration tests for Enum type handling in DynamoDB.
 *
 * Coverage:
 * - @DynamoDBTypeConvertedEnum (string representation)
 * - Save/retrieve entities with enum fields
 * - Query by enum values
 * - Update enum values
 * - Null enum handling
 * - Enum in filter expressions
 * - Enum in conditional expressions
 * - Multiple enum fields in same entity
 * - Enum ordinal vs string representation
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {DynamoDBLocalResource.class, EnumTypesIntegrationTest.TestAppConfig.class})
@TestPropertySource(properties = {"spring.data.dynamodb.entity2ddl.auto=create"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Enum Types Integration Tests")
public class EnumTypesIntegrationTest {

    @Configuration
    @EnableDynamoDBRepositories(basePackages = "org.socialsignin.spring.data.dynamodb.domain.sample")
    public static class TestAppConfig {
    }

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private DynamoDbClient amazonDynamoDB;

    @Autowired
    private DynamoDbEnhancedClient enhancedClient;

    private DynamoDbTable<Task> taskTable;

    @BeforeEach
    void setUp() {
        // Initialize the DynamoDB table for Task entity
        taskTable = enhancedClient.table("Task", TableSchema.fromBean(Task.class));

        taskRepository.deleteAll();
    }

    // ==================== Basic Enum Operations ====================

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Test 1: Save and retrieve task with enum fields")
    void testSaveAndRetrieveTaskWithEnums() {
        // Given
        Task task = new Task("task-001", "Implement feature", TaskStatus.IN_PROGRESS, Priority.HIGH);
        task.setDescription("Implement user authentication");
        task.setAssignedTo("Alice");
        task.setDueDate(Instant.now().plusSeconds(86400));

        // When
        taskRepository.save(task);

        // Then
        Task retrieved = taskRepository.findById("task-001").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(retrieved.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(retrieved.getTitle()).isEqualTo("Implement feature");
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("Test 2: Enum stored as string in DynamoDB")
    void testEnumStoredAsString() {
        // Given
        Task task = new Task("task-002", "Bug fix", TaskStatus.COMPLETED, Priority.MEDIUM);
        taskRepository.save(task);

        // When - Read raw item from DynamoDB
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("taskId", AttributeValue.builder().s("task-002").build());

        GetItemRequest request = GetItemRequest.builder()
                .tableName("Task")
                .key(key)
                .build();
        GetItemResponse result = amazonDynamoDB.getItem(request);

        // Then - Enums should be stored as strings
        assertThat(result.item().get("status").s()).isEqualTo("COMPLETED");
        assertThat(result.item().get("priority").s()).isEqualTo("MEDIUM");
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("Test 3: Save task with all enum values")
    void testAllEnumValues() {
        // Given - Tasks with all possible status values
        taskRepository.save(new Task("task-pending", "Task 1", TaskStatus.PENDING, Priority.LOW));
        taskRepository.save(new Task("task-progress", "Task 2", TaskStatus.IN_PROGRESS, Priority.MEDIUM));
        taskRepository.save(new Task("task-completed", "Task 3", TaskStatus.COMPLETED, Priority.HIGH));
        taskRepository.save(new Task("task-cancelled", "Task 4", TaskStatus.CANCELLED, Priority.URGENT));
        taskRepository.save(new Task("task-failed", "Task 5", TaskStatus.FAILED, Priority.LOW));

        // When - Retrieve all tasks
        List<Task> allTasks = (List<Task>) taskRepository.findAll();

        // Then - All enum values should be preserved
        assertThat(allTasks).hasSize(5);

        assertThat(allTasks).extracting(Task::getStatus)
                .containsExactlyInAnyOrder(
                        TaskStatus.PENDING,
                        TaskStatus.IN_PROGRESS,
                        TaskStatus.COMPLETED,
                        TaskStatus.CANCELLED,
                        TaskStatus.FAILED
                );

        assertThat(allTasks).extracting(Task::getPriority)
                .containsExactlyInAnyOrder(
                        Priority.LOW,
                        Priority.MEDIUM,
                        Priority.HIGH,
                        Priority.URGENT,
                        Priority.LOW
                );
    }

    @Test
    @org.junit.jupiter.api.Order(4)
    @DisplayName("Test 4: Null enum handling")
    void testNullEnumHandling() {
        // Given - Task with null enum values
        Task task = new Task();
        task.setTaskId("task-null");
        task.setTitle("Task with nulls");
        task.setStatus(null);
        task.setPriority(null);

        // When
        taskRepository.save(task);

        // Then
        Task retrieved = taskRepository.findById("task-null").orElse(null);
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getStatus()).isNull();
        assertThat(retrieved.getPriority()).isNull();
    }

    // ==================== Query Operations ====================

    @Test
    @org.junit.jupiter.api.Order(5)
    @DisplayName("Test 5: Query by enum value - findByStatus")
    void testQueryByEnumValue() {
        // Given
        taskRepository.save(new Task("task-q1", "Task 1", TaskStatus.IN_PROGRESS, Priority.HIGH));
        taskRepository.save(new Task("task-q2", "Task 2", TaskStatus.COMPLETED, Priority.MEDIUM));
        taskRepository.save(new Task("task-q3", "Task 3", TaskStatus.IN_PROGRESS, Priority.LOW));
        taskRepository.save(new Task("task-q4", "Task 4", TaskStatus.PENDING, Priority.HIGH));

        // When
        List<Task> inProgressTasks = taskRepository.findByStatus(TaskStatus.IN_PROGRESS);

        // Then
        assertThat(inProgressTasks).hasSize(2);
        assertThat(inProgressTasks).allMatch(task -> task.getStatus() == TaskStatus.IN_PROGRESS);
    }

    @Test
    @org.junit.jupiter.api.Order(6)
    @DisplayName("Test 6: Query by priority enum")
    void testQueryByPriority() {
        // Given
        taskRepository.save(new Task("task-p1", "Task 1", TaskStatus.PENDING, Priority.HIGH));
        taskRepository.save(new Task("task-p2", "Task 2", TaskStatus.PENDING, Priority.HIGH));
        taskRepository.save(new Task("task-p3", "Task 3", TaskStatus.PENDING, Priority.LOW));

        // When
        List<Task> highPriorityTasks = taskRepository.findByPriority(Priority.HIGH);

        // Then
        assertThat(highPriorityTasks).hasSize(2);
        assertThat(highPriorityTasks).allMatch(task -> task.getPriority() == Priority.HIGH);
    }

    @Test
    @org.junit.jupiter.api.Order(7)
    @DisplayName("Test 7: Query by multiple enum fields")
    void testQueryByMultipleEnums() {
        // Given
        taskRepository.save(new Task("task-m1", "Task 1", TaskStatus.IN_PROGRESS, Priority.HIGH));
        taskRepository.save(new Task("task-m2", "Task 2", TaskStatus.IN_PROGRESS, Priority.LOW));
        taskRepository.save(new Task("task-m3", "Task 3", TaskStatus.COMPLETED, Priority.HIGH));
        taskRepository.save(new Task("task-m4", "Task 4", TaskStatus.IN_PROGRESS, Priority.HIGH));

        // When
        List<Task> tasks = taskRepository.findByStatusAndPriority(TaskStatus.IN_PROGRESS, Priority.HIGH);

        // Then
        assertThat(tasks).hasSize(2);
        assertThat(tasks).allMatch(task ->
                task.getStatus() == TaskStatus.IN_PROGRESS && task.getPriority() == Priority.HIGH
        );
    }

    @Test
    @org.junit.jupiter.api.Order(8)
    @DisplayName("Test 8: Count by enum value")
    void testCountByEnum() {
        // Given
        taskRepository.save(new Task("task-c1", "Task 1", TaskStatus.COMPLETED, Priority.HIGH));
        taskRepository.save(new Task("task-c2", "Task 2", TaskStatus.COMPLETED, Priority.MEDIUM));
        taskRepository.save(new Task("task-c3", "Task 3", TaskStatus.PENDING, Priority.HIGH));
        taskRepository.save(new Task("task-c4", "Task 4", TaskStatus.COMPLETED, Priority.LOW));

        // When
        long completedCount = taskRepository.countByStatus(TaskStatus.COMPLETED);

        // Then
        assertThat(completedCount).isEqualTo(3);
    }

    // ==================== Update Operations ====================

    @Test
    @org.junit.jupiter.api.Order(9)
    @DisplayName("Test 9: Update enum value")
    void testUpdateEnumValue() {
        // Given
        Task task = new Task("task-update-1", "Task to update", TaskStatus.PENDING, Priority.LOW);
        taskRepository.save(task);

        // When - Change status
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setPriority(Priority.HIGH);
        taskRepository.save(task);

        // Then
        Task updated = taskRepository.findById("task-update-1").get();
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(updated.getPriority()).isEqualTo(Priority.HIGH);
    }

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("Test 10: Update enum using UpdateExpression")
    void testUpdateEnumUsingUpdateExpression() {
        // Given
        Task task = new Task("task-update-2", "Task", TaskStatus.PENDING, Priority.LOW);
        taskRepository.save(task);

        // When - Update status using DynamoDB API
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("taskId", AttributeValue.builder().s("task-update-2").build());

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newStatus", AttributeValue.builder().s("COMPLETED").build());
        expressionAttributeValues.put(":newPriority", AttributeValue.builder().s("URGENT").build());

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName("Task")
                .key(key)
                .updateExpression("SET #status = :newStatus, priority = :newPriority")
                .expressionAttributeNames(Collections.singletonMap("#status", "status"))
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        amazonDynamoDB.updateItem(updateRequest);

        // Then
        Task updated = taskRepository.findById("task-update-2").get();
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(updated.getPriority()).isEqualTo(Priority.URGENT);
    }

    // ==================== Filter Expressions with Enums ====================

    @Test
    @org.junit.jupiter.api.Order(11)
    @DisplayName("Test 11: Scan with enum filter expression")
    void testScanWithEnumFilter() {
        // Given
        taskRepository.save(new Task("task-scan-1", "Task 1", TaskStatus.PENDING, Priority.HIGH));
        taskRepository.save(new Task("task-scan-2", "Task 2", TaskStatus.COMPLETED, Priority.LOW));
        taskRepository.save(new Task("task-scan-3", "Task 3", TaskStatus.IN_PROGRESS, Priority.MEDIUM));
        taskRepository.save(new Task("task-scan-4", "Task 4", TaskStatus.COMPLETED, Priority.HIGH));

        // When - Scan for completed tasks
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":status", AttributeValue.builder().s("COMPLETED").build());

        ScanRequest scanRequest = ScanRequest.builder()
                .tableName("Task")
                .filterExpression("#status = :status")
                .expressionAttributeNames(Collections.singletonMap("#status", "status"))
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        ScanResponse result = amazonDynamoDB.scan(scanRequest);

        // Then
        assertThat(result.items()).hasSize(2);
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    @DisplayName("Test 12: Filter with IN operator on enum")
    void testFilterWithInOperatorOnEnum() {
        // Given
        taskRepository.save(new Task("task-in-1", "Task 1", TaskStatus.PENDING, Priority.HIGH));
        taskRepository.save(new Task("task-in-2", "Task 2", TaskStatus.COMPLETED, Priority.LOW));
        taskRepository.save(new Task("task-in-3", "Task 3", TaskStatus.IN_PROGRESS, Priority.MEDIUM));
        taskRepository.save(new Task("task-in-4", "Task 4", TaskStatus.CANCELLED, Priority.HIGH));

        // When - Find tasks with status IN (PENDING, IN_PROGRESS)
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":status1", AttributeValue.builder().s("PENDING").build());
        expressionAttributeValues.put(":status2", AttributeValue.builder().s("IN_PROGRESS").build());

        ScanRequest scanRequest = ScanRequest.builder()
                .tableName("Task")
                .filterExpression("#status IN (:status1, :status2)")
                .expressionAttributeNames(Collections.singletonMap("#status", "status"))
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        ScanResponse result = amazonDynamoDB.scan(scanRequest);

        // Then
        assertThat(result.items()).hasSize(2);
    }

    // ==================== Conditional Expressions with Enums ====================

    @Test
    @org.junit.jupiter.api.Order(13)
    @DisplayName("Test 13: Conditional update based on enum value")
    void testConditionalUpdateBasedOnEnum() {
        // Given
        Task task = new Task("task-cond-1", "Task", TaskStatus.PENDING, Priority.MEDIUM);
        taskRepository.save(task);

        // When - Only update if status is PENDING
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("taskId", AttributeValue.builder().s("task-cond-1").build());

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newStatus", AttributeValue.builder().s("IN_PROGRESS").build());
        expressionAttributeValues.put(":expectedStatus", AttributeValue.builder().s("PENDING").build());

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName("Task")
                .key(key)
                .updateExpression("SET #status = :newStatus")
                .conditionExpression("#status = :expectedStatus")
                .expressionAttributeNames(Collections.singletonMap("#status", "status"))
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        amazonDynamoDB.updateItem(updateRequest);

        // Then
        Task updated = taskRepository.findById("task-cond-1").get();
        assertThat(updated.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test
    @org.junit.jupiter.api.Order(14)
    @DisplayName("Test 14: Conditional update fails when enum doesn't match")
    void testConditionalUpdateFailsOnEnumMismatch() {
        // Given
        Task task = new Task("task-cond-2", "Task", TaskStatus.COMPLETED, Priority.HIGH);
        taskRepository.save(task);

        // When - Try to update but expect PENDING (actual is COMPLETED)
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("taskId", AttributeValue.builder().s("task-cond-2").build());

        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":newStatus", AttributeValue.builder().s("IN_PROGRESS").build());
        expressionAttributeValues.put(":expectedStatus", AttributeValue.builder().s("PENDING").build());

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName("Task")
                .key(key)
                .updateExpression("SET #status = :newStatus")
                .conditionExpression("#status = :expectedStatus")
                .expressionAttributeNames(Collections.singletonMap("#status", "status"))
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        // Then - Update should fail
        try {
            amazonDynamoDB.updateItem(updateRequest);
            Assertions.fail("Should have thrown ConditionalCheckFailedException");
        } catch (ConditionalCheckFailedException e) {
            // Expected
        }

        // Status should remain unchanged
        Task unchanged = taskRepository.findById("task-cond-2").get();
        assertThat(unchanged.getStatus()).isEqualTo(TaskStatus.COMPLETED);
    }

    // ==================== Edge Cases ====================

    @Test
    @org.junit.jupiter.api.Order(15)
    @DisplayName("Test 15: Batch save with mixed enum values")
    void testBatchSaveWithEnums() {
        // Given
        List<Task> tasks = Arrays.asList(
                new Task("task-batch-1", "Task 1", TaskStatus.PENDING, Priority.LOW),
                new Task("task-batch-2", "Task 2", TaskStatus.IN_PROGRESS, Priority.MEDIUM),
                new Task("task-batch-3", "Task 3", TaskStatus.COMPLETED, Priority.HIGH),
                new Task("task-batch-4", "Task 4", TaskStatus.CANCELLED, Priority.URGENT),
                new Task("task-batch-5", "Task 5", TaskStatus.FAILED, Priority.LOW)
        );

        // When
        taskRepository.saveAll(tasks);

        // Then
        assertThat(taskRepository.count()).isEqualTo(5);

        Task task3 = taskRepository.findById("task-batch-3").get();
        assertThat(task3.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task3.getPriority()).isEqualTo(Priority.HIGH);
    }

    @Test
    @org.junit.jupiter.api.Order(16)
    @DisplayName("Test 16: Enum case sensitivity")
    void testEnumCaseSensitivity() {
        // Given
        Task task = new Task("task-case-1", "Task", TaskStatus.IN_PROGRESS, Priority.HIGH);
        taskRepository.save(task);

        // When - Try to query with wrong case (should not match)
        Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
        expressionAttributeValues.put(":status", AttributeValue.builder().s("in_progress").build()); // lowercase

        ScanRequest scanRequest = ScanRequest.builder()
                .tableName("Task")
                .filterExpression("#status = :status")
                .expressionAttributeNames(Collections.singletonMap("#status", "status"))
                .expressionAttributeValues(expressionAttributeValues)
                .build();

        ScanResponse result = amazonDynamoDB.scan(scanRequest);

        // Then - Should not find any (case sensitive)
        assertThat(result.items()).isEmpty();
    }

    @Test
    @org.junit.jupiter.api.Order(17)
    @DisplayName("Test 17: Query with null enum value")
    void testQueryWithNullEnum() {
        // Given
        Task task1 = new Task();
        task1.setTaskId("task-null-1");
        task1.setTitle("Task with null status");
        task1.setStatus(null);
        task1.setPriority(Priority.HIGH);
        taskRepository.save(task1);

        Task task2 = new Task("task-null-2", "Normal task", TaskStatus.PENDING, Priority.HIGH);
        taskRepository.save(task2);

        // When - Query for non-null status
        List<Task> tasks = taskRepository.findByStatus(TaskStatus.PENDING);

        // Then - Should only find task2
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getTaskId()).isEqualTo("task-null-2");
    }

    @Test
    @org.junit.jupiter.api.Order(18)
    @DisplayName("Test 18: DynamoDbEnhancedClient with enum")
    void testEnhancedClientWithEnum() {
        // Given
        Task task = new Task("task-mapper-1", "Task via Enhanced Client", TaskStatus.IN_PROGRESS, Priority.URGENT);

        // When - Save using DynamoDbEnhancedClient
        taskTable.putItem(task);

        // Then - Load using DynamoDbEnhancedClient
        Key key = Key.builder().partitionValue("task-mapper-1").build();
        Task loaded = taskTable.getItem(key);
        assertThat(loaded).isNotNull();
        assertThat(loaded.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(loaded.getPriority()).isEqualTo(Priority.URGENT);
    }
}
