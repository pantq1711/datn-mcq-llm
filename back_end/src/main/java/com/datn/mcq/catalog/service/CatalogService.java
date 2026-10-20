package com.datn.mcq.catalog.service;

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

    @Transactional
    public QuestionTemplate getOrCreateTemplate(
            Integer subjectId,
            Integer gradeLevel,
            String topic,
            String learningOutcome,
            String sourceQuestion) {
        return questionTemplateRepository.findFirstBySubjectIdAndTopicOrderByIdAsc(subjectId, topic)
                .orElseGet(() -> {
                    QuestionTemplate tpl = new QuestionTemplate();
                    tpl.setSubjectId(subjectId);
                    tpl.setGradeLevel(gradeLevel);
                    tpl.setTopic(topic);
                    tpl.setLearningOutcome(learningOutcome);
                    tpl.setSourceType(SourceType.TEXT);
                    tpl.setSourceQuestion(sourceQuestion);
                    tpl.setVersion(1);
                    tpl.setIsActive(Boolean.TRUE);
                    return questionTemplateRepository.save(tpl);
                });
    }
}

