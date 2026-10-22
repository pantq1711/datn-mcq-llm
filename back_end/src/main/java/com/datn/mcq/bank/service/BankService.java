package com.datn.mcq.bank.service;

import com.datn.mcq.bank.dto.BankItemDto;
import com.datn.mcq.bank.entity.BankItem;
import com.datn.mcq.bank.entity.BankItemStatement;
import com.datn.mcq.bank.repository.BankItemRepository;
import com.datn.mcq.bank.repository.BankItemStatementRepository;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.exam.repository.ExamQuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class BankService {

    private final BankItemRepository bankItemRepository;
    private final BankItemStatementRepository bankItemStatementRepository;
    private final ExamQuestionRepository examQuestionRepository;

    public BankService(BankItemRepository bankItemRepository,
                       BankItemStatementRepository bankItemStatementRepository,
                       ExamQuestionRepository examQuestionRepository) {
        this.bankItemRepository = bankItemRepository;
        this.bankItemStatementRepository = bankItemStatementRepository;
        this.examQuestionRepository = examQuestionRepository;
    }

    /** Ngân hàng cho GET /api/bank-items: mới nhất trước; câu tf kèm 4 ý (một truy vấn cho mọi câu tf). */
    @Transactional(readOnly = true)
    public List<BankItemDto> listAll() {
        return listAll(null);
    }

    /** @param questionType lọc theo dạng câu (GĐ2); null là mọi dạng, như trước V4 */
    @Transactional(readOnly = true)
    public List<BankItemDto> listAll(QuestionType questionType) {
        List<BankItem> items = questionType == null
                ? bankItemRepository.findAllByOrderByIdDesc()
                : bankItemRepository.findByQuestionTypeOrderByIdDesc(questionType);
        List<Integer> tfIds = items.stream()
                .filter(i -> i.getQuestionType() == QuestionType.TF)
                .map(BankItem::getId)
                .toList();
        Map<Integer, List<BankItemStatement>> statementsByItem = tfIds.isEmpty()
                ? Map.of()
                : bankItemStatementRepository.findByBankItemIdInOrderByBankItemIdAscPositionAsc(tfIds).stream()
                        .collect(Collectors.groupingBy(BankItemStatement::getBankItemId));
        return items.stream()
                .map(i -> BankItemDto.fromEntity(i, statementsByItem.getOrDefault(i.getId(), List.of())))
                .toList();
    }

    @Transactional
    public BankItem createOrUpdateFromReview(
            Integer sourceQuestionId,
            Integer reviewId,
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            Option correctOption,
            String explanation,
            Difficulty difficulty) {
        BankItem item = bankItemRepository.findBySourceQuestionId(sourceQuestionId)
                .orElseGet(BankItem::new);
        item.setSourceQuestionId(sourceQuestionId);
        item.setReviewId(reviewId);
        item.setQuestionText(questionText);
        item.setOptionA(optionA);
        item.setOptionB(optionB);
        item.setOptionC(optionC);
        item.setOptionD(optionD);
        item.setCorrectOption(correctOption);
        item.setExplanation(explanation);
        item.setDifficulty(difficulty);
        item.setCreatedAt(LocalDateTime.now());
        return bankItemRepository.save(item);
    }

    /**
     * Chép câu Trả lời ngắn vào ngân hàng (V4, GĐ2): không có phương án và correct_option; sa_answer là đáp số cuối
     * giáo viên chốt (đã kiểm ở chế độ STRICT). Duyệt lại thì cập nhật dòng cũ.
     */
    @Transactional
    public BankItem createOrUpdateShortAnswerFromReview(
            Integer sourceQuestionId,
            Integer reviewId,
            String questionText,
            String saAnswer,
            String explanation,
            Difficulty difficulty) {
        BankItem item = bankItemRepository.findBySourceQuestionId(sourceQuestionId)
                .orElseGet(BankItem::new);
        item.setQuestionType(QuestionType.SA);
        item.setSourceQuestionId(sourceQuestionId);
        item.setReviewId(reviewId);
        item.setQuestionText(questionText);
        item.setOptionA(null);
        item.setOptionB(null);
        item.setOptionC(null);
        item.setOptionD(null);
        item.setCorrectOption(null);
        item.setSaAnswer(saAnswer);
        item.setExplanation(explanation);
        item.setDifficulty(difficulty);
        item.setCreatedAt(LocalDateTime.now());
        return bankItemRepository.save(item);
    }

    @Transactional
    public void removeBankItemIfUnused(Integer sourceQuestionId) {
        bankItemRepository.findBySourceQuestionId(sourceQuestionId).ifPresent(item -> {
            if (examQuestionRepository.existsByBankItemId(item.getId())) {
                throw new IllegalStateException(
                        "Câu hỏi đã được đưa vào đề thi (exam), không thể từ chối và gỡ khỏi ngân hàng câu hỏi.");
            }
            bankItemRepository.delete(item);
        });
    }
}

