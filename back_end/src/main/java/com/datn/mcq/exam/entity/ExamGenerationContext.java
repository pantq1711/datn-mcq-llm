package com.datn.mcq.exam.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Ngữ cảnh sinh câu của một đề (V19, W2-3): tài liệu bài học đính kèm và "yêu cầu thêm cho AI". {@code cellId} null là
 * phạm vi cả đề (sinh theo bài, hoặc "cả ma trận"); khác null là phạm vi một ô. Mỗi đề có tối đa một dòng cho mỗi phạm vi.
 */
@Entity
@Table(name = "exam_generation_context")
public class ExamGenerationContext {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "exam_id", nullable = false)
    private Integer examId;

    @Column(name = "cell_id")
    private Integer cellId;

    /** Yêu cầu thêm đã chuẩn hóa (1-1000 ký tự); null khi không có. */
    @Column(name = "extra_instruction", columnDefinition = "TEXT")
    private String extraInstruction;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Tài liệu của phạm vi này (bảng exam_context_material). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "exam_context_material", joinColumns = @JoinColumn(name = "context_id"))
    @Column(name = "material_id", nullable = false)
    private Set<Integer> materialIds = new LinkedHashSet<>();

    public ExamGenerationContext() {}

    public Integer getId() {
        return id;
    }

    public Integer getExamId() {
        return examId;
    }

    public void setExamId(Integer examId) {
        this.examId = examId;
    }

    public Integer getCellId() {
        return cellId;
    }

    public void setCellId(Integer cellId) {
        this.cellId = cellId;
    }

    public String getExtraInstruction() {
        return extraInstruction;
    }

    public void setExtraInstruction(String extraInstruction) {
        this.extraInstruction = extraInstruction;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Set<Integer> getMaterialIds() {
        return materialIds;
    }

    public void setMaterialIds(Set<Integer> materialIds) {
        this.materialIds = materialIds;
    }
}
