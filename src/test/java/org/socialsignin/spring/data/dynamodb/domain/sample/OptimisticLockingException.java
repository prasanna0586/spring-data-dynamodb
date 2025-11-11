package org.socialsignin.spring.data.dynamodb.domain.sample;

/**
 * Exception thrown when an optimistic locking conflict occurs during a save operation.
 * This typically happens when two concurrent updates attempt to modify the same document,
 * and the version has changed between read and write operations.
 */
public class OptimisticLockingException extends RuntimeException {

    private final String documentId;
    private final Long attemptedVersion;

    public OptimisticLockingException(String message, String documentId, Long attemptedVersion) {
        super(message);
        this.documentId = documentId;
        this.attemptedVersion = attemptedVersion;
    }

    public OptimisticLockingException(String message, String documentId, Long attemptedVersion, Throwable cause) {
        super(message, cause);
        this.documentId = documentId;
        this.attemptedVersion = attemptedVersion;
    }

    public String getDocumentId() {
        return documentId;
    }

    public Long getAttemptedVersion() {
        return attemptedVersion;
    }

    @Override
    public String toString() {
        return String.format("%s [documentId=%s, attemptedVersion=%s]",
                super.toString(), documentId, attemptedVersion);
    }
}
