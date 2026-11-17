package com.datn.mcq.exam.service;

import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.exam.entity.Exam;
import com.datn.mcq.exam.entity.ExamSlot;
import com.datn.mcq.exam.repository.ExamSlotRepository;
import com.datn.mcq.matrix.entity.ExamMatrixCell;
import com.datn.mcq.matrix.service.MatrixService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Cửa của module {@code assignment} vào đề (docs/design/lam-bai-truc-tuyen.md mục 1, quy tắc 2 của backend-structure: module
 * khác chỉ gọi service). {@code exam} không biết {@code assignment}; chiều phụ thuộc là {@code assignment ──► exam}.
 */
@Service
public class ExamAssignmentSource {

    /** Một chỗ của đề theo thứ tự trang đề: Phần I, II, III; trong Phần theo ô rồi số thứ tự chỗ. */
    public record AssignableSlot(Integer slotId, String part, int numberInPart, QuestionType questionType, String state,
                                 Integer bankItemId) {
        /** Chỗ đã chốt bằng Giữ hoặc Sửa (câu hỏi 1 ngày 14/11: chỉ giao đề mà mọi chỗ đã chốt). */
        public boolean settled() {
            return ExamSlot.KEPT.equals(state) || ExamSlot.EDITED.equals(state);
        }
    }

    public record ExamForAssignment(Integer examId, String title, List<AssignableSlot> slots) {}

    private static final List<String> PART_ORDER = List.of("I", "II", "III");

    private final ExamBuildService buildService;
    private final ExamSlotRepository slotRepository;
    private final MatrixService matrixService;

    public ExamAssignmentSource(ExamBuildService buildService, ExamSlotRepository slotRepository,
                                MatrixService matrixService) {
        this.buildService = buildService;
        this.slotRepository = slotRepository;
        this.matrixService = matrixService;
    }

    /** Đề chưa xóa của {@code actorId}; không có hoặc của người khác: NoSuchElementException (404). Chỉ đọc. */
    @Transactional(readOnly = true)
    public void requireOwned(Integer examId, Integer actorId) {
        buildService.getOwnedExam(examId, actorId);
    }

    /**
     * Đề của {@code actorId} cùng các chỗ theo thứ tự trang đề, để giao bài. Khóa CHIA SẺ hàng đề tới hết transaction của
     * nơi gọi (như Giữ, Sửa): lần xóa đề đang dở thì chờ rồi 404; lần xóa đến sau thì chờ giao bài xong rồi thấy bài giao
     * ({@link ExamDeletionGuard}). Phải gọi trong transaction ghi được. Không đối soát chỗ đang sinh (chỗ đó chưa chốt nên
     * đằng nào cũng không giao được).
     */
    @Transactional
    public ExamForAssignment lockForAssignment(Integer examId, Integer actorId) {
        Exam exam = buildService.getOwnedExamForWrite(examId, actorId);
        List<ExamSlot> slots = new ArrayList<>(slotRepository.findByExamIdOrderByCellIdAscSlotNoAsc(examId));
        Map<Integer, ExamMatrixCell> cells = matrixService.cellsByIds(
                slots.stream().map(ExamSlot::getCellId).collect(Collectors.toCollection(LinkedHashSet::new)));
        slots.sort(Comparator
                .comparing((ExamSlot s) -> partRank(cells.get(s.getCellId()).getPart()))
                .thenComparing(s -> cells.get(s.getCellId()).getPosition())
                .thenComparing(ExamSlot::getSlotNo));
        Map<String, Integer> counters = new HashMap<>();
        List<AssignableSlot> out = new ArrayList<>(slots.size());
        for (ExamSlot slot : slots) {
            String part = cells.get(slot.getCellId()).getPart();
            int number = counters.merge(part, 1, Integer::sum);
            out.add(new AssignableSlot(slot.getId(), part, number, slot.getQuestionType(), slot.getState(),
                    slot.getBankItemId()));
        }
        return new ExamForAssignment(exam.getId(), exam.getTitle(), List.copyOf(out));
    }

    private static int partRank(String part) {
        int i = PART_ORDER.indexOf(part);
        return i < 0 ? PART_ORDER.size() : i;
    }
}
