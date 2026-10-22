package com.datn.mcq.generation.entity;

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
 * Một ý a-d của câu Đúng/Sai do LLM sinh (V4). Cột position lưu chuỗi "a".."d"; JPA không áp converter cho
 * thuộc tính @Id nên dùng {@link #getStatementKey()} để đọc dưới dạng enum.
 */
@Entity
@Table(name = "generated_question_statement")
@IdClass(GeneratedQuestionStatement.Key.class)
public class GeneratedQuestionStatement {

    public static class Key implements Serializable {
        private Integer questionId;
        private String position;

        public Key() {}

        public Key(Integer questionId, String position) {
            this.questionId = questionId;
            this.position = position;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key that)) return false;
            return Objects.equals(questionId, that.questionId) && Objects.equals(position, that.position);
        }

        @Override
        public int hashCode() {
            return Objects.hash(questionId, position);
        }
    }

    @Id
    @Column(name = "question_id", nullable = false)
    private Integer questionId;

    @Id
    @Column(name = "position", nullable = false, length = 1)
    private String position;

    /** Hằng 'tf', chỉ dùng cho khóa ngoại kép tới generated_question(id, question_type). */
    @Column(name = "question_type", nullable = false, length = 10, updatable = false)
    private QuestionType questionType = QuestionType.TF;

    @Column(name = "statement_text", columnDefinition = "TEXT")
    private String statementText;

    @Column(name = "llm_label", length = 1)
    private TruthValue llmLabel;

    /** Mức mục tiêu khi sinh (quyết định 1 ngày 22/10). */
    @Column(name = "llm_difficulty", length = 10)
    private Difficulty llmDifficulty;

    @Column(name = "final_difficulty", length = 10)
    private Difficulty finalDifficulty;

    public GeneratedQuestionStatement() {}

    public GeneratedQuestionStatement(Integer questionId, StatementKey key) {
        this.questionId = questionId;
        setStatementKey(key);
    }

    public Integer getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Integer questionId) {
        this.questionId = questionId;
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

    public TruthValue getLlmLabel() {
        return llmLabel;
    }

    public void setLlmLabel(TruthValue llmLabel) {
        this.llmLabel = llmLabel;
    }

    public Difficulty getLlmDifficulty() {
        return llmDifficulty;
    }

    public void setLlmDifficulty(Difficulty llmDifficulty) {
        this.llmDifficulty = llmDifficulty;
    }

    public Difficulty getFinalDifficulty() {
        return finalDifficulty;
    }

    public void setFinalDifficulty(Difficulty finalDifficulty) {
        this.finalDifficulty = finalDifficulty;
    }
}
