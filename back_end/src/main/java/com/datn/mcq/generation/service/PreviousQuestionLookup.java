package com.datn.mcq.generation.service;

import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.entity.GeneratedQuestionStatement;
import com.datn.mcq.generation.repository.GeneratedQuestionRepository;
import com.datn.mcq.generation.repository.GeneratedQuestionStatementRepository;
import com.datn.mcq.llm.prompt.PreviousQuestion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Các câu đã sinh trước đó đưa vào prompt sa/tf (độ đa dạng, quyết định Q1-Q2 ngày 22/10): tối đa 10 câu gần nhất
 * cùng template và cùng dạng câu, ở mọi run, mọi trạng thái (kể cả duplicate, rejected), miễn là giải mã được.
 * Câu sa chỉ lấy đề; câu tf lấy đề dẫn và 4 ý. Không lấy đáp số hay nhãn.
 *
 * <p>Không lọc theo purpose vì DB bảo đảm run sa/tf luôn là production (V4: chk trên experiment_run và khóa ngoại kép
 * từ generated_question). Không áp cho mcq.
 */
@Service
public class PreviousQuestionLookup {

    private final GeneratedQuestionRepository questionRepository;
    private final GeneratedQuestionStatementRepository statementRepository;

    public PreviousQuestionLookup(GeneratedQuestionRepository questionRepository,
                                  GeneratedQuestionStatementRepository statementRepository) {
        this.questionRepository = questionRepository;
        this.statementRepository = statementRepository;
    }

    /** Mới nhất trước; rỗng với mcq. */
    @Transactional(readOnly = true)
    public List<PreviousQuestion> recent(Integer templateId, QuestionType type) {
        if (templateId == null || (type != QuestionType.SA && type != QuestionType.TF)) {
            return List.of();
        }
        List<GeneratedQuestion> questions = questionRepository
                .findTop10ByTemplateIdAndQuestionTypeAndParseSuccessTrueOrderByCreatedAtDescIdDesc(templateId, type);
        Map<Integer, List<String>> statements = type != QuestionType.TF || questions.isEmpty()
                ? Map.of()
                : statementRepository.findByQuestionIdInOrderByQuestionIdAscPositionAsc(
                                questions.stream().map(GeneratedQuestion::getId).toList()).stream()
                        .collect(Collectors.groupingBy(GeneratedQuestionStatement::getQuestionId,
                                Collectors.mapping(GeneratedQuestionStatement::getStatementText, Collectors.toList())));
        return questions.stream()
                .filter(q -> q.getQuestionText() != null && !q.getQuestionText().isBlank())
                .map(q -> new PreviousQuestion(q.getQuestionText(), statements.getOrDefault(q.getId(), List.of())))
                .toList();
    }
}
