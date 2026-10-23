package com.datn.mcq.exam.entity;

import com.datn.mcq.enums.QuestionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Một chỗ trong đề tạo theo ma trận (V7): câu thứ {@code slotNo} của ô {@code cellId}. Câu lấy từ ngân hàng
 * ({@code bankItemId}) hoặc do hệ thống sinh ({@code generatedQuestionId}); Giữ câu sinh ra thì có cả hai.
 */
@Entity
@Table(name = "exam_slot")
public class ExamSlot {

    public static final String PENDING = "pending";
    public static final String GENERATING = "generating";
    public static final String READY = "ready";
    public static final String FAILED = "failed";
    public static final String KEPT = "kept";
    public static final String EDITED = "edited";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "exam_id", nullable = false)
    private Integer examId;

    @Column(name = "cell_id", nullable = false)
    private Integer cellId;

    @Column(name = "slot_no", nullable = false)
    private Integer slotNo;

    @Column(name = "question_type", nullable = false, length = 10)
    private QuestionType questionType;

    /** Run sinh câu của ô (dùng lại khi sinh lại); null khi câu lấy từ ngân hàng. */
    @Column(name = "run_id")
    private Integer runId;

    @Column(name = "state", nullable = false, length = 20)
    private String state = PENDING;

    @Column(name = "bank_item_id")
    private Integer bankItemId;

    @Column(name = "generated_question_id")
    private Integer generatedQuestionId;

    /** Câu lệch Solver giữ lại để dùng (kèm cờ) nếu các lần sinh sau không ra câu dùng được. */
    @Column(name = "fallback_question_id")
    private Integer fallbackQuestionId;

    /** Số job sinh câu đã tạo cho câu hiện tại của chỗ này. */
    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "solver_retries", nullable = false)
    private Integer solverRetries = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    public ExamSlot() {}

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

    public Integer getSlotNo() {
        return slotNo;
    }

    public void setSlotNo(Integer slotNo) {
        this.slotNo = slotNo;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public void setQuestionType(QuestionType questionType) {
        this.questionType = questionType;
    }

    public Integer getRunId() {
        return runId;
    }

    public void setRunId(Integer runId) {
        this.runId = runId;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
        this.updatedAt = LocalDateTime.now();
    }

    public Integer getBankItemId() {
        return bankItemId;
    }

    public void setBankItemId(Integer bankItemId) {
        this.bankItemId = bankItemId;
    }

    public Integer getGeneratedQuestionId() {
        return generatedQuestionId;
    }

    public void setGeneratedQuestionId(Integer generatedQuestionId) {
        this.generatedQuestionId = generatedQuestionId;
    }

    public Integer getFallbackQuestionId() {
        return fallbackQuestionId;
    }

    public void setFallbackQuestionId(Integer fallbackQuestionId) {
        this.fallbackQuestionId = fallbackQuestionId;
    }

    public Integer getAttempts() {
        return attempts;
    }

    public void setAttempts(Integer attempts) {
        this.attempts = attempts;
    }

    public Integer getSolverRetries() {
        return solverRetries;
    }

    public void setSolverRetries(Integer solverRetries) {
        this.solverRetries = solverRetries;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
