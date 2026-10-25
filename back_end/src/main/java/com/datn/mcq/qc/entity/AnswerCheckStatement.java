package com.datn.mcq.qc.entity;

import com.datn.mcq.enums.DbEnum;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.StatementKey;
import com.datn.mcq.enums.TruthChoice;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/**
 * Nhãn Solver cho một ý a-d của câu Đúng/Sai, gắn với dòng answer_check (V4).
 * DB xóa dây chuyền khi dòng answer_check bị xóa (chạy lại QC).
 */
@Entity
@Table(name = "answer_check_statement")
@IdClass(AnswerCheckStatement.Key.class)
public class AnswerCheckStatement {

    public static class Key implements Serializable {
        private Integer qcResultId;
        private String position;

        public Key() {}

        public Key(Integer qcResultId, String position) {
            this.qcResultId = qcResultId;
            this.position = position;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key that)) return false;
            return Objects.equals(qcResultId, that.qcResultId) && Objects.equals(position, that.position);
        }

        @Override
        public int hashCode() {
            return Objects.hash(qcResultId, position);
        }
    }

    @Id
    @Column(name = "qc_result_id", nullable = false)
    private Integer qcResultId;

    @Id
    @Column(name = "position", nullable = false, length = 1)
    private String position;

    /** Hằng 'tf', chỉ dùng cho khóa ngoại kép tới quality_check_result(id, question_type). */
    @Column(name = "question_type", nullable = false, length = 10, updatable = false)
    private QuestionType questionType = QuestionType.TF;

    @Column(name = "judge_label", nullable = false, length = 20)
    private TruthChoice judgeLabel;

    public AnswerCheckStatement() {}

    public AnswerCheckStatement(Integer qcResultId, StatementKey key, TruthChoice judgeLabel) {
        this.qcResultId = qcResultId;
        setStatementKey(key);
        this.judgeLabel = judgeLabel;
    }

    public Integer getQcResultId() {
        return qcResultId;
    }

    public void setQcResultId(Integer qcResultId) {
        this.qcResultId = qcResultId;
    }

    public StatementKey getStatementKey() {
        return DbEnum.fromDbValue(StatementKey.class, position);
    }

    public void setStatementKey(StatementKey key) {
        this.position = key != null ? key.getDbValue() : null;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public TruthChoice getJudgeLabel() {
        return judgeLabel;
    }

    public void setJudgeLabel(TruthChoice judgeLabel) {
        this.judgeLabel = judgeLabel;
    }
}
