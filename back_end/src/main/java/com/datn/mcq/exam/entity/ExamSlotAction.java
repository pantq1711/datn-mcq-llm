package com.datn.mcq.exam.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** Nhật ký thao tác Giữ / Sửa / Đổi trên một chỗ của đề (V7), để thống kê riêng với chấm mù. */
@Entity
@Table(name = "exam_slot_action")
public class ExamSlotAction {

    public static final String KEEP = "keep";
    public static final String EDIT = "edit";
    public static final String REPLACE = "replace";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "slot_id", nullable = false)
    private Integer slotId;

    @Column(name = "action", nullable = false, length = 10)
    private String action;

    /** Câu sinh ra mà thao tác áp lên (null khi câu lấy từ ngân hàng). */
    @Column(name = "generated_question_id")
    private Integer generatedQuestionId;

    /** Câu ngân hàng mà thao tác áp lên, hoặc câu ngân hàng tạo ra khi Giữ/Sửa. */
    @Column(name = "bank_item_id")
    private Integer bankItemId;

    @Column(name = "edit_distance")
    private Integer editDistance;

    @Column(name = "actor_id")
    private Integer actorId;

    /** Yêu cầu bổ sung của giáo viên khi Đổi câu (V9); null khi không có. */
    @Column(name = "instruction")
    private String instruction;

    /** Các trường đã đổi khi Sửa (V13, mảng JSON tên trường); null với dòng khác edit. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changed_fields", columnDefinition = "jsonb")
    private String changedFields;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public ExamSlotAction() {}

    public ExamSlotAction(Integer slotId, String action, Integer generatedQuestionId, Integer bankItemId,
                          Integer editDistance, Integer actorId) {
        this.slotId = slotId;
        this.action = action;
        this.generatedQuestionId = generatedQuestionId;
        this.bankItemId = bankItemId;
        this.editDistance = editDistance;
        this.actorId = actorId;
    }

    public Integer getId() {
        return id;
    }

    public Integer getSlotId() {
        return slotId;
    }

    public String getAction() {
        return action;
    }

    public Integer getGeneratedQuestionId() {
        return generatedQuestionId;
    }

    public Integer getBankItemId() {
        return bankItemId;
    }

    public Integer getEditDistance() {
        return editDistance;
    }

    public Integer getActorId() {
        return actorId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getInstruction() {
        return instruction;
    }

    public void setInstruction(String instruction) {
        this.instruction = instruction;
    }

    public String getChangedFields() {
        return changedFields;
    }

    public void setChangedFields(String changedFields) {
        this.changedFields = changedFields;
    }
}
