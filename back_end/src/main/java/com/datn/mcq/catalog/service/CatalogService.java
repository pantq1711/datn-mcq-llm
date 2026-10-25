package com.datn.mcq.catalog.service;

import com.datn.mcq.catalog.dto.CreateTemplateRequest;
import com.datn.mcq.catalog.entity.QuestionTemplate;
import com.datn.mcq.catalog.entity.Subject;
import com.datn.mcq.catalog.repository.QuestionTemplateRepository;
import com.datn.mcq.catalog.repository.SubjectRepository;
import com.datn.mcq.enums.SourceType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class CatalogService {

    private final SubjectRepository subjectRepository;
    private final QuestionTemplateRepository questionTemplateRepository;

    public CatalogService(SubjectRepository subjectRepository,
                          QuestionTemplateRepository questionTemplateRepository) {
        this.subjectRepository = subjectRepository;
        this.questionTemplateRepository = questionTemplateRepository;
    }

    @Transactional(readOnly = true)
    public List<Subject> listSubjects() {
        return subjectRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Subject getSubjectById(Integer subjectId) {
        return subjectRepository.findById(subjectId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy môn học id=" + subjectId));
    }

    @Transactional
    public Subject getOrCreateSubject(String name) {
        return subjectRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> subjectRepository.save(new Subject(name)));
    }

    @Transactional(readOnly = true)
    public QuestionTemplate getTemplateById(Integer templateId) {
        return questionTemplateRepository.findById(templateId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy question_template id=" + templateId));
    }

    @Transactional(readOnly = true)
    public List<QuestionTemplate> listTemplates(Integer subjectId) {
        if (subjectId != null) {
            return questionTemplateRepository.findBySubjectIdOrderByIdAsc(subjectId);
        }
        return questionTemplateRepository.findAll();
    }

    /**
     * Tạo question_template mới cho subject đã tồn tại. Không tạo subject.
     * Kiểm tra trước các ràng buộc của bảng (V1, V2) để trả 400 thay vì lỗi DB.
     */
    @Transactional
    public QuestionTemplate createTemplate(CreateTemplateRequest req) {
        if (req == null || req.subjectId() == null) {
            throw new IllegalArgumentException("Phải truyền subjectId");
        }
        String sourceQuestion = req.sourceQuestion() != null ? req.sourceQuestion().trim() : "";
        if (sourceQuestion.isEmpty()) {
            throw new IllegalArgumentException("sourceQuestion không được để trống");
        }
        if (req.gradeLevel() != null && (req.gradeLevel() < 6 || req.gradeLevel() > 9)) {
            throw new IllegalArgumentException("gradeLevel phải nằm trong khoảng 6 đến 9");
        }
        String topic = blankToNull(req.topic());
        if (topic != null && topic.length() > 255) {
            throw new IllegalArgumentException("topic dài tối đa 255 ký tự");
        }
        Subject subject = getSubjectById(req.subjectId());

        QuestionTemplate tpl = new QuestionTemplate();
        tpl.setSubjectId(subject.getId());
        tpl.setGradeLevel(req.gradeLevel());
        tpl.setTopic(topic);
        tpl.setLearningOutcome(blankToNull(req.learningOutcome()));
        tpl.setSourceType(req.sourceType() != null ? req.sourceType() : SourceType.TEXT);
        tpl.setSourceQuestion(sourceQuestion);
        tpl.setVersion(1);
        tpl.setIsActive(Boolean.TRUE);
        return questionTemplateRepository.save(tpl);
    }

    /**
     * W1-4: dọn các template không còn ô ma trận, câu sinh ra hay job nào tham chiếu (khi sửa/xóa ma trận). Template còn
     * được tham chiếu giữ nguyên. Trả số template đã xóa.
     */
    @Transactional
    public int deleteUnusedTemplates(java.util.Collection<Integer> templateIds) {
        if (templateIds == null || templateIds.isEmpty()) {
            return 0;
        }
        return questionTemplateRepository.deleteUnreferenced(templateIds);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

