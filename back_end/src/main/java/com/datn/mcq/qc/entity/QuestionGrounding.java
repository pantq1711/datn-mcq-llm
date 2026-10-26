package com.datn.mcq.qc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Kết quả QC "bám tài liệu" của một câu (V20, W2-3). Chỉ là cảnh báo: không đổi generated_question.status. Mỗi câu (mỗi
 * phiên bản) tối đa một dòng.
 */
@Entity
@Table(name = "question_grounding")
public class QuestionGrounding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "question_id", nullable = false, updatable = false)
    private Integer questionId;

    /** supported, not_supported hoặc unknown (xem GroundingVerdict). */
    @Column(name = "verdict", nullable = false, length = 15)
    private String verdict;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Column(name = "evidence_quote", columnDefinition = "TEXT")
    private String evidenceQuote;

    /** Trích dẫn có thật trong đoạn tài liệu đã đưa vào prompt không; null khi judge không trích dẫn. */
    @Column(name = "evidence_found")
    private Boolean evidenceFound;

    @Column(name = "judge_model", nullable = false, length = 100)
    private String judgeModel;

    /** Mảng JSON các đoạn tài liệu đã dùng (chép từ job). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "material_refs", nullable = false, columnDefinition = "jsonb")
    private String materialRefs;

    /** TRUE: câu sinh CÓ tài liệu trong prompt (luồng thật). FALSE dành cho nhóm đối chứng khi đo thử nghiệm. */
    @Column(name = "generated_with_material", nullable = false)
    private boolean generatedWithMaterial = true;

    @Column(name = "source_job_id")
    private Integer sourceJobId;

    @Column(name = "checked_at", nullable = false)
    private LocalDateTime checkedAt = LocalDateTime.now();

    public QuestionGrounding() {}

    public Integer getId() {
        return id;
    }

    public Integer getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Integer questionId) {
        this.questionId = questionId;
    }

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getEvidenceQuote() {
        return evidenceQuote;
    }

    public void setEvidenceQuote(String evidenceQuote) {
        this.evidenceQuote = evidenceQuote;
    }

    public Boolean getEvidenceFound() {
        return evidenceFound;
    }

    public void setEvidenceFound(Boolean evidenceFound) {
        this.evidenceFound = evidenceFound;
    }

    public String getJudgeModel() {
        return judgeModel;
    }

    public void setJudgeModel(String judgeModel) {
        this.judgeModel = judgeModel;
    }

    public String getMaterialRefs() {
        return materialRefs;
    }

    public void setMaterialRefs(String materialRefs) {
        this.materialRefs = materialRefs;
    }

    public boolean isGeneratedWithMaterial() {
        return generatedWithMaterial;
    }

    public void setGeneratedWithMaterial(boolean generatedWithMaterial) {
        this.generatedWithMaterial = generatedWithMaterial;
    }

    public Integer getSourceJobId() {
        return sourceJobId;
    }

    public void setSourceJobId(Integer sourceJobId) {
        this.sourceJobId = sourceJobId;
    }

    public LocalDateTime getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(LocalDateTime checkedAt) {
        this.checkedAt = checkedAt;
    }
}
