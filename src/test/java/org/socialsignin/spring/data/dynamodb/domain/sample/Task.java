package org.socialsignin.spring.data.dynamodb.domain.sample;

import com.amazonaws.services.dynamodbv2.datamodeling.*;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain model for testing enum type handling.
 * Demonstrates various enum storage strategies.
 */
@DynamoDBTable(tableName = "Task")
public class Task {

    private String taskId;
    private String title;
    private String description;
    private TaskStatus status;
    private Priority priority;
    private String assignedTo;
    private Instant createdAt;
    private Instant dueDate;

    public Task() {
    }

    public Task(String taskId, String title, TaskStatus status, Priority priority) {
        this.taskId = taskId;
        this.title = title;
        this.status = status;
        this.priority = priority;
        this.createdAt = Instant.now();
    }

    @DynamoDBHashKey(attributeName = "taskId")
    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    @DynamoDBAttribute(attributeName = "title")
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @DynamoDBAttribute(attributeName = "description")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @DynamoDBAttribute(attributeName = "status")
    @DynamoDBTypeConvertedEnum
    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    @DynamoDBAttribute(attributeName = "priority")
    @DynamoDBTypeConvertedEnum
    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    @DynamoDBAttribute(attributeName = "assignedTo")
    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }

    @DynamoDBAttribute(attributeName = "createdAt")
    @DynamoDBTypeConverted(converter = InstantConverter.class)
    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @DynamoDBAttribute(attributeName = "dueDate")
    @DynamoDBTypeConverted(converter = InstantConverter.class)
    public Instant getDueDate() {
        return dueDate;
    }

    public void setDueDate(Instant dueDate) {
        this.dueDate = dueDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Task task = (Task) o;
        return Objects.equals(taskId, task.taskId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId);
    }

    @Override
    public String toString() {
        return "Task{" +
                "taskId='" + taskId + '\'' +
                ", title='" + title + '\'' +
                ", status=" + status +
                ", priority=" + priority +
                ", assignedTo='" + assignedTo + '\'' +
                ", createdAt=" + createdAt +
                ", dueDate=" + dueDate +
                '}';
    }
}
