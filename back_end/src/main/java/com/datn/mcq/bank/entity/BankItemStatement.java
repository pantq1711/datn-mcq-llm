package com.datn.mcq.bank.entity;

import com.datn.mcq.enums.DbEnum;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.StatementKey;
import com.datn.mcq.enums.TruthValue;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/**
 * Một ý a-d của câu Đúng/Sai trong ngân hàng: bản cuối giáo viên chốt (V4).
 * Mỗi dòng là một "lệnh hỏi" khi đếm theo ma trận đề. DB xóa dây chuyền theo bank_item.
 */
@Entity
@Table(name = "bank_item_statement")
@IdClass(BankItemStatement.Key.class)
public class BankItemStatement {

    public static class Key implements Serializable {
        private Integer bankItemId;
        private String position;

        public Key() {}

        public Key(Integer bankItemId, String position) {
            this.bankItemId = bankItemId;
            this.position = position;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key that)) return false;
            return Objects.equals(bankItemId, that.bankItemId) && Objects.equals(position, that.position);
        }

        @Override
        public int hashCode() {
            return Objects.hash(bankItemId, position);
        }
    }

    @Id
    @Column(name = "bank_item_id", nullable = false)
    private Integer bankItemId;

    @Id
    @Column(name = "position", nullable = false, length = 1)
    private String position;

    /** Hằng 'tf', chỉ dùng cho khóa ngoại kép tới bank_item(id, question_type). */
    @Column(name = "question_type", nullable = false, length = 10, updatable = false)
    private QuestionType questionType = QuestionType.TF;

    @Column(name = "statement_text", nullable = false, columnDefinition = "TEXT")
    private String statementText;

    @Column(name = "label", nullable = false, length = 1)
    private TruthValue label;

    @Column(name = "difficulty", nullable = false, length = 10)
    private Difficulty difficulty;

    public BankItemStatement() {}

    public BankItemStatement(Integer bankItemId, StatementKey key) {
        this.bankItemId = bankItemId;
        setStatementKey(key);
    }

    public Integer getBankItemId() {
        return bankItemId;
    }

    public void setBankItemId(Integer bankItemId) {
        this.bankItemId = bankItemId;
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

    public String getStatementText() {
        return statementText;
    }

    public void setStatementText(String statementText) {
        this.statementText = statementText;
    }

    public TruthValue getLabel() {
        return label;
    }

    public void setLabel(TruthValue label) {
        this.label = label;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }
}
