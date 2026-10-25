package com.datn.mcq.catalog.entity;

import com.datn.mcq.enums.SourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "question_template")
public class QuestionTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "subject_id", nullable = false)
    private Integer subjectId;

    @Column(name = "grade_level")
    private Integer gradeLevel;

    @Column(name = "topic", length = 255)
    private String topic;

    @Column(name = "learning_outcome", columnDefinition = "TEXT")
    private String learningOutcome;

    @Column(name = "source_type", nullable = false, length = 20)
    private SourceType sourceType = SourceType.TEXT;

    @Column(name = "source_question", nullable = false, columnDefinition = "TEXT")
    private String sourceQuestion;

    @Column(name = "version")
    private Integer version = 1;

    @Column(name = "is_active")
    private Boolean isActive = Boolean.TRUE;

    public QuestionTemplate() {}

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getLearningOutcome() {
        return learningOutcome;
    }

    public void setLearningOutcome(String learningOutcome) {
        this.learningOutcome = learningOutcome;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceQuestion() {
        return sourceQuestion;
    }

    public void setSourceQuestion(String sourceQuestion) {
        this.sourceQuestion = sourceQuestion;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}

