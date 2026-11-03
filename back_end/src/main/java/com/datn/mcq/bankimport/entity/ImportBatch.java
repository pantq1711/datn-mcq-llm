package com.datn.mcq.bankimport.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** Một lần tải bộ câu hỏi .docx để nhập vào ngân hàng (V26, bảng import_batch). */
@Entity
@Table(name = "import_batch")
public class ImportBatch {

    public static final String STATUS_PREVIEW = "preview";
    public static final String STATUS_CONFIRMED = "confirmed";
    public static final String STATUS_EXPIRED = "expired";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Integer ownerId;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "file_sha256", nullable = false, length = 64)
    private String fileSha256;

    @Column(name = "subject_id", nullable = false)
    private Integer subjectId;

    @Column(name = "grade_level", nullable = false)
    private Integer gradeLevel;

    @Column(name = "topic", nullable = false, length = 255)
    private String topic;

    @Column(name = "status", nullable = false, length = 10)
    private String status = STATUS_PREVIEW;

    @Column(name = "question_count", nullable = false)
    private Integer questionCount;

    /** Đối tượng JSON: lý do → số câu bị bỏ. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skipped_counts", nullable = false, columnDefinition = "jsonb")
    private String skippedCounts = "{}";

    @Column(name = "imported_count")
    private Integer importedCount;

    /** Bản xem trước (JSON của BankImportDtos.PreviewData); NULL sau khi xác nhận hoặc hết hạn. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preview", columnDefinition = "jsonb")
    private String preview;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    public Integer getId() {
        return id;
    }

    public Integer getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Integer ownerId) {
        this.ownerId = ownerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getFileSha256() {
        return fileSha256;
    }

    public void setFileSha256(String fileSha256) {
        this.fileSha256 = fileSha256;
    }

    public Integer getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Integer subjectId) {
        this.subjectId = subjectId;
    }

    public Integer getGradeLevel() {
        return gradeLevel;
    }

    public void setGradeLevel(Integer gradeLevel) {
        this.gradeLevel = gradeLevel;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(Integer questionCount) {
        this.questionCount = questionCount;
    }

    public String getSkippedCounts() {
        return skippedCounts;
    }

    public void setSkippedCounts(String skippedCounts) {
        this.skippedCounts = skippedCounts;
    }

    public Integer getImportedCount() {
        return importedCount;
    }

    public void setImportedCount(Integer importedCount) {
        this.importedCount = importedCount;
    }

    public String getPreview() {
        return preview;
    }

    public void setPreview(String preview) {
        this.preview = preview;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }
}
