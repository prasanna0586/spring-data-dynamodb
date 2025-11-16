package org.socialsignin.spring.data.dynamodb.domain.sample;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbConvertedBy;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain model for testing Time To Live (TTL) functionality.
 * TTL allows automatic deletion of items after a specified timestamp.
 *
 * Note: TTL is configured programmatically using UpdateTimeToLiveRequest,
 * not via annotations. This applies to both SDK v1 and SDK v2.
 */
@DynamoDbBean
public class SessionData {

    private String sessionId;
    private String userId;
    private Long expirationTime;  // Unix timestamp in seconds (TTL attribute)
    private Instant createdAt;
    private String data;

    public SessionData() {
    }

    public SessionData(String sessionId, String userId, Long expirationTime,
                       Instant createdAt, String data) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.expirationTime = expirationTime;
        this.createdAt = createdAt;
        this.data = data;
    }

    @DynamoDbPartitionKey
    @DynamoDbAttribute("sessionId")
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    @DynamoDbAttribute("userId")
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * TTL attribute - Unix timestamp in seconds.
     * Items will be automatically deleted after this time.
     * TTL must be enabled on this attribute using UpdateTimeToLiveRequest.
     */
    @DynamoDbAttribute("expirationTime")
    public Long getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(Long expirationTime) {
        this.expirationTime = expirationTime;
    }

    @DynamoDbAttribute("createdAt")
    @DynamoDbConvertedBy(InstantConverter.class)
    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @DynamoDbAttribute("data")
    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SessionData that = (SessionData) o;
        return Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId);
    }

    @Override
    public String toString() {
        return "SessionData{" +
                "sessionId='" + sessionId + '\'' +
                ", userId='" + userId + '\'' +
                ", expirationTime=" + expirationTime +
                ", createdAt=" + createdAt +
                ", data='" + data + '\'' +
                '}';
    }
}
