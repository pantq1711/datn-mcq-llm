package com.datn.mcq.questionimage.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "question_image")
public class QuestionImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false)
    private Integer ownerId;

    @Column(name = "import_batch_id", nullable = false)
    private Integer importBatchId;

    @Column(name = "item_key", nullable = false, length = 20)
    private String itemKey;

    @Column(name = "generated_question_id")
    private Integer generatedQuestionId;

    @Column(name = "role", nullable = false, length = 12)
    private String role;

    @Column(name = "ordinal", nullable = false)
    private Short ordinal;

    @Column(name = "mime", nullable = false, length = 10)
    private String mime;

    @Column(name = "data", nullable = false)
    private byte[] data;

    @Column(name = "byte_size", nullable = false)
    private Integer byteSize;

    @Column(name = "width_px", nullable = false)
    private Integer widthPx;

    @Column(name = "height_px", nullable = false)
    private Integer heightPx;

    @Column(name = "display_width_mm", nullable = false)
    private Short displayWidthMm;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "source_format", nullable = false, length = 5)
    private String sourceFormat;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public QuestionImage() {}

    public QuestionImage(Integer ownerId, Integer importBatchId, String itemKey,
                         Integer generatedQuestionId, String role, Short ordinal,
                         String mime, byte[] data, Integer byteSize,
                         Integer widthPx, Integer heightPx, Short displayWidthMm,
                         String sha256, String sourceFormat) {
        this.ownerId = ownerId;
        this.importBatchId = importBatchId;
        this.itemKey = itemKey;
        this.generatedQuestionId = generatedQuestionId;
        this.role = role;
        this.ordinal = ordinal;
        this.mime = mime;
        this.data = data;
        this.byteSize = byteSize;
        this.widthPx = widthPx;
        this.heightPx = heightPx;
        this.displayWidthMm = displayWidthMm;
        this.sha256 = sha256;
        this.sourceFormat = sourceFormat;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Integer ownerId) {
        this.ownerId = ownerId;
    }

    public Integer getImportBatchId() {
        return importBatchId;
    }

    public void setImportBatchId(Integer importBatchId) {
        this.importBatchId = importBatchId;
    }

    public String getItemKey() {
        return itemKey;
    }

    public void setItemKey(String itemKey) {
        this.itemKey = itemKey;
    }

    public Integer getGeneratedQuestionId() {
        return generatedQuestionId;
    }

    public void setGeneratedQuestionId(Integer generatedQuestionId) {
        this.generatedQuestionId = generatedQuestionId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Short getOrdinal() {
        return ordinal;
    }

    public void setOrdinal(Short ordinal) {
        this.ordinal = ordinal;
    }

    public String getMime() {
        return mime;
    }

    public void setMime(String mime) {
        this.mime = mime;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public Integer getByteSize() {
        return byteSize;
    }

    public void setByteSize(Integer byteSize) {
        this.byteSize = byteSize;
    }

    public Integer getWidthPx() {
        return widthPx;
    }

    public void setWidthPx(Integer widthPx) {
        this.widthPx = widthPx;
    }

    public Integer getHeightPx() {
        return heightPx;
    }

    public void setHeightPx(Integer heightPx) {
        this.heightPx = heightPx;
    }

    public Short getDisplayWidthMm() {
        return displayWidthMm;
    }

    public void setDisplayWidthMm(Short displayWidthMm) {
        this.displayWidthMm = displayWidthMm;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getSourceFormat() {
        return sourceFormat;
    }

    public void setSourceFormat(String sourceFormat) {
        this.sourceFormat = sourceFormat;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
