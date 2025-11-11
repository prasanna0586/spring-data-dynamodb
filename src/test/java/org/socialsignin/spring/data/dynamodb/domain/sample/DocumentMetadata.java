package org.socialsignin.spring.data.dynamodb.domain.sample;

import com.amazonaws.services.dynamodbv2.datamodeling.*;

import java.time.Instant;
import java.util.Objects;

@DynamoDBTable(tableName = "DocumentMetadata")
public class DocumentMetadata {

    private String uniqueDocumentId;
    private Integer memberId;
    private Integer documentCategory;
    private Integer documentSubCategory;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
    private String notes;
    private Long version;

    public DocumentMetadata() {
    }

    public DocumentMetadata(String uniqueDocumentId, Integer memberId, Integer documentCategory,
                            Integer documentSubCategory, Instant createdAt, Instant updatedAt,
                            String createdBy, String updatedBy, String notes, Long version) {
        this.uniqueDocumentId = uniqueDocumentId;
        this.memberId = memberId;
        this.documentCategory = documentCategory;
        this.documentSubCategory = documentSubCategory;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.notes = notes;
        this.version = version;
    }

    @DynamoDBHashKey(attributeName = "uniqueDocumentId")
    public String getUniqueDocumentId() {
        return uniqueDocumentId;
    }

    public void setUniqueDocumentId(String uniqueDocumentId) {
        this.uniqueDocumentId = uniqueDocumentId;
    }

    @DynamoDBIndexHashKey(globalSecondaryIndexNames = {
            "memberId-documentCategory-index",
            "memberId-documentSubCategory-index",
            "memberId-createdAt-index"
    }, attributeName = "memberId")
    public Integer getMemberId() {
        return memberId;
    }

    public void setMemberId(Integer memberId) {
        this.memberId = memberId;
    }

    @DynamoDBIndexRangeKey(globalSecondaryIndexName = "memberId-documentCategory-index", attributeName = "documentCategory")
    public Integer getDocumentCategory() {
        return documentCategory;
    }

    public void setDocumentCategory(Integer documentCategory) {
        this.documentCategory = documentCategory;
    }

    @DynamoDBIndexRangeKey(globalSecondaryIndexName = "memberId-documentSubCategory-index", attributeName = "documentSubCategory")
    public Integer getDocumentSubCategory() {
        return documentSubCategory;
    }

    public void setDocumentSubCategory(Integer documentSubCategory) {
        this.documentSubCategory = documentSubCategory;
    }

    @DynamoDBIndexRangeKey(globalSecondaryIndexName = "memberId-createdAt-index", attributeName = "createdAt")
    @DynamoDBTypeConverted(converter = InstantConverter.class)
    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @DynamoDBAttribute(attributeName = "updatedAt")
    @DynamoDBTypeConverted(converter = InstantConverter.class)
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @DynamoDBAttribute(attributeName = "createdBy")
    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    @DynamoDBAttribute(attributeName = "updatedBy")
    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    @DynamoDBAttribute(attributeName = "notes")
    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @DynamoDBVersionAttribute
    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DocumentMetadata that = (DocumentMetadata) o;
        return Objects.equals(uniqueDocumentId, that.uniqueDocumentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uniqueDocumentId);
    }

    @Override
    public String toString() {
        return "DocumentMetadata{" +
                "uniqueDocumentId='" + uniqueDocumentId + '\'' +
                ", memberId=" + memberId +
                ", documentCategory=" + documentCategory +
                ", documentSubCategory=" + documentSubCategory +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", createdBy='" + createdBy + '\'' +
                ", updatedBy='" + updatedBy + '\'' +
                ", notes='" + notes + '\'' +
                ", version=" + version +
                '}';
    }
}
