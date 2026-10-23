package com.datn.mcq.bank.service;

import com.datn.mcq.bank.entity.BankItem;
import com.datn.mcq.bank.repository.BankItemRepository;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.Option;
import com.datn.mcq.exam.repository.ExamQuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BankService {

    private final BankItemRepository bankItemRepository;
    private final ExamQuestionRepository examQuestionRepository;

    public BankService(BankItemRepository bankItemRepository,
                       ExamQuestionRepository examQuestionRepository) {
        this.bankItemRepository = bankItemRepository;
        this.examQuestionRepository = examQuestionRepository;
    }

    @Transactional(readOnly = true)
    public List<BankItem> listAll() {
        return bankItemRepository.findAllByOrderByIdDesc();
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

