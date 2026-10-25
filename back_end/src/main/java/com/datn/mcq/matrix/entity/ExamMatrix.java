package com.datn.mcq.matrix.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** Ma trận đề kiểm tra (V6). */
@Entity
@Table(name = "exam_matrix")
public class ExamMatrix {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "subject_id", nullable = false)
    private Integer subjectId;

    @Column(name = "grade_level")
    private Integer gradeLevel;

    @Column(name = "source", columnDefinition = "TEXT")
    private String source;

    /** Mảng JSON các ghi chú (giữ nguyên từ file mẫu). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notes", columnDefinition = "jsonb")
    private String notes;

    /** Khóa của file mẫu khi ma trận tạo từ mẫu có sẵn; null khi tạo bằng API. */
    @Column(name = "sample_key", length = 50)
    private String sampleKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** V11: chủ sở hữu (users.id); chỉ người này xem và tạo đề từ ma trận. */
    @Column(name = "created_by", nullable = false)
    private Integer createdBy;

    /** V15: 'matrix' (giáo viên tạo) hoặc 'lesson' (ma trận ẩn của một lần sinh theo bài). */
    @Column(name = "kind", nullable = false, length = 10)
    private String kind = KIND_MATRIX;

    public static final String KIND_MATRIX = "matrix";
    public static final String KIND_LESSON = "lesson";

    public ExamMatrix() {}

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public boolean isLesson() {
        return KIND_LESSON.equals(kind);
    }

    public Integer getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Integer createdBy) {
        this.createdBy = createdBy;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getSampleKey() {
        return sampleKey;
    }

    public void setSampleKey(String sampleKey) {
        this.sampleKey = sampleKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
