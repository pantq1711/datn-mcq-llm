package com.datn.mcq.bank.entity;

import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "bank_item")
public class BankItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "source_question_id", nullable = false, unique = true)
    private Integer sourceQuestionId;

    @Column(name = "review_id", nullable = false, unique = true)
    private Integer reviewId;

    /** Dạng câu (V4); khóa ngoại kép tới generated_question và teacher_review theo (id, question_type). */
    @Column(name = "question_type", nullable = false, length = 10, updatable = false)
    private QuestionType questionType = QuestionType.MCQ;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    // option_a..d, correct_option: bắt buộc với mcq, NULL với tf/sa (CHECK chk_bi_type_content, V4).
    @Column(name = "option_a", columnDefinition = "TEXT")
    private String optionA;

    @Column(name = "option_b", columnDefinition = "TEXT")
    private String optionB;

    @Column(name = "option_c", columnDefinition = "TEXT")
    private String optionC;

    @Column(name = "option_d", columnDefinition = "TEXT")
    private String optionD;

    @Column(name = "correct_option", length = 1)
    private Option correctOption;

    /** Đáp số cuối của câu sa (bản giáo viên chốt); NULL với mcq và tf. */
    @Column(name = "sa_answer", columnDefinition = "TEXT")
    private String saAnswer;

    @Column(name = "explanation", columnDefinition = "TEXT")
    private String explanation;

    /** Bắt buộc với mcq và sa; tf để NULL (mức độ ở bank_item_statement). */
    @Column(name = "difficulty", length = 10)
    private Difficulty difficulty;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public BankItem() {}

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getSourceQuestionId() {
        return sourceQuestionId;
    }

    public void setSourceQuestionId(Integer sourceQuestionId) {
        this.sourceQuestionId = sourceQuestionId;
    }

    public Integer getReviewId() {
        return reviewId;
    }

    public void setReviewId(Integer reviewId) {
        this.reviewId = reviewId;
    }

    public String getQuestionText() {
        return questionText;
    }

    public void setQuestionText(String questionText) {
        this.questionText = questionText;
    }

    public String getOptionA() {
        return optionA;
    }

    public void setOptionA(String optionA) {
        this.optionA = optionA;
    }

    public String getOptionB() {
        return optionB;
    }

    public void setOptionB(String optionB) {
        this.optionB = optionB;
    }

    public String getOptionC() {
        return optionC;
    }

    public void setOptionC(String optionC) {
        this.optionC = optionC;
    }

    public String getOptionD() {
        return optionD;
    }

    public void setOptionD(String optionD) {
        this.optionD = optionD;
    }

    public Option getCorrectOption() {
        return correctOption;
    }

    public void setCorrectOption(Option correctOption) {
        this.correctOption = correctOption;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public Difficulty getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    /**
     * V19 (W2-3): null là câu dùng chung; khác null thì chỉ người dùng này thấy và dùng lại (câu sinh từ tài liệu của họ).
     */
    @Column(name = "visible_to_user_id")
    private Integer visibleToUserId;

    public Integer getVisibleToUserId() {
        return visibleToUserId;
    }

    public void setVisibleToUserId(Integer visibleToUserId) {
        this.visibleToUserId = visibleToUserId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public void setQuestionType(QuestionType questionType) {
        this.questionType = questionType;
    }

    public String getSaAnswer() {
        return saAnswer;
    }

    public void setSaAnswer(String saAnswer) {
        this.saAnswer = saAnswer;
    }
}

