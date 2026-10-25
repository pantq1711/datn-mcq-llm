package com.datn.mcq.matrix.entity;

import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.QuestionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Một ô của ma trận (V6): phần, dạng câu, chủ đề, nội dung, yêu cầu cần đạt theo mức, mức độ (tf: 4 mức của 4 ý),
 * số câu, và question_template tạo tự động cho ô.
 */
@Entity
@Table(name = "exam_matrix_cell")
public class ExamMatrixCell {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "matrix_id", nullable = false)
    private Integer matrixId;

    @Column(name = "position", nullable = false)
    private Integer position;

    @Column(name = "part", nullable = false, length = 3)
    private String part;

    @Column(name = "question_type", nullable = false, length = 10)
    private QuestionType questionType;

    @Column(name = "topic", nullable = false, length = 255)
    private String topic;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "learning_outcome_easy", columnDefinition = "TEXT")
    private String learningOutcomeEasy;

    @Column(name = "learning_outcome_medium", columnDefinition = "TEXT")
    private String learningOutcomeMedium;

    @Column(name = "learning_outcome_hard", columnDefinition = "TEXT")
    private String learningOutcomeHard;

    @Column(name = "difficulty", length = 10)
    private Difficulty difficulty;

    /** tf: "easy,easy,medium,hard" theo thứ tự ý a-d; null với mcq/sa. */
    @Column(name = "statement_difficulties", length = 40)
    private String statementDifficulties;

    @Column(name = "question_count", nullable = false)
    private Integer questionCount;

    @Column(name = "template_id", nullable = false)
    private Integer templateId;

    public ExamMatrixCell() {}

    /** Yêu cầu cần đạt của một mức; null khi ô không có mức đó. */
    public String learningOutcome(Difficulty level) {
        return switch (level) {
            case EASY -> learningOutcomeEasy;
            case MEDIUM -> learningOutcomeMedium;
            case HARD -> learningOutcomeHard;
        };
    }

    public Integer getId() {
        return id;
    }

    public Integer getMatrixId() {
        return matrixId;
    }

    public void setMatrixId(Integer matrixId) {
        this.matrixId = matrixId;
    }

    public Integer getPosition() {
        return position;
    }

    public void setPosition(Integer position) {
        this.position = position;
    }

    public String getPart() {
        return part;
    }

    public void setPart(String part) {
        this.part = part;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public void setQuestionType(QuestionType questionType) {
        this.questionType = questionType;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getLearningOutcomeEasy() {
        return learningOutcomeEasy;
    }

    public void setLearningOutcomeEasy(String learningOutcomeEasy) {
        this.learningOutcomeEasy = learningOutcomeEasy;
    }

    public String getLearningOutcomeMedium() {
        return learningOutcomeMedium;
    }

    public void setLearningOutcomeMedium(String learningOutcomeMedium) {
        this.learningOutcomeMedium = learningOutcomeMedium;
    }

    public String getLearningOutcomeHard() {
        return learningOutcomeHard;
    }

    public void setLearningOutcomeHard(String learningOutcomeHard) {
        this.learningOutcomeHard = learningOutcomeHard;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public String getStatementDifficulties() {
        return statementDifficulties;
    }

    public void setStatementDifficulties(String statementDifficulties) {
        this.statementDifficulties = statementDifficulties;
    }

    public Integer getQuestionCount() {
        return questionCount;
    }

    public void setQuestionCount(Integer questionCount) {
        this.questionCount = questionCount;
    }

    public Integer getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Integer templateId) {
        this.templateId = templateId;
    }
}
