package com.datn.mcq.assignment.service;

import com.datn.mcq.assignment.repository.AssignmentRepository;
import com.datn.mcq.assignment.repository.AssignmentRepository.AssignmentRow;
import com.datn.mcq.assignment.repository.AssignmentRepository.ItemRow;
import com.datn.mcq.bank.service.BankService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

/** Dùng chung cho API giáo viên và API công khai: đóng lười và nạp đề của bài giao. Gọi trong transaction của nơi gọi. */
@Component
class AssignmentLoader {

    private final AssignmentRepository repository;
    private final BankService bankService;

    AssignmentLoader(AssignmentRepository repository, BankService bankService) {
        this.repository = repository;
        this.bankService = bankService;
    }

    /**
     * Đóng lười (mục 2.2): bài còn mở mà đã tới {@code closes_at} thì chuyển {@code closed}, {@code closed_at = closes_at}.
     * Trả bản đọc lại khi có đổi.
     */
    AssignmentRow refresh(AssignmentRow a, LocalDateTime now) {
        if (!a.open() || a.closesAt().isAfter(now)) {
            return a;
        }
        // 0 dòng đổi: lần gọi đồng thời vừa đóng. Cả hai trường hợp đều đọc lại để lấy closed_at.
        repository.closeIfDue(a.id(), now);
        return repository.findById(a.id()).orElseThrow(() -> new NoSuchElementException("Không tìm thấy bài giao"));
    }

    AssignmentPaper paper(AssignmentRow a) {
        List<ItemRow> items = repository.items(a.id());
        return new AssignmentPaper(items, bankService.getItems(items.stream().map(ItemRow::bankItemId).toList()),
                Scoring.fromJson(a.scoringJson()));
    }
}
