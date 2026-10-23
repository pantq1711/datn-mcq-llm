package com.datn.mcq.exam.entity;

import com.datn.mcq.enums.ExamStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "exam")
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "parent_exam_id")
    private Integer parentExamId;

    @Column(name = "exam_code", nullable = false, unique = true, length = 20)
    private String examCode;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "subject_id", nullable = false)
    private Integer subjectId;

    @Column(name = "created_by", nullable = false)
    private Integer createdBy;

    @Column(name = "is_backup", nullable = false)
    private Boolean isBackup = Boolean.FALSE;

    @Column(name = "status", nullable = false, length = 20)
    private ExamStatus status = ExamStatus.DRAFT;

    public Exam() {}

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getParentExamId() {
        return parentExamId;
    }

    public void setParentExamId(Integer parentExamId) {
        this.parentExamId = parentExamId;
    }

    public String getExamCode() {
        return examCode;
    }

    public void setExamCode(String examCode) {
        this.examCode = examCode;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Integer subjectId) {
        this.subjectId = subjectId;
    }

    public Integer getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Integer createdBy) {
        this.createdBy = createdBy;
    }

    public Boolean getIsBackup() {
        return isBackup;
    }

    public void setIsBackup(Boolean isBackup) {
        this.isBackup = isBackup;
    }

    public ExamStatus getStatus() {
        return status;
    }

    public void setStatus(ExamStatus status) {
        this.status = status;
    }
}

