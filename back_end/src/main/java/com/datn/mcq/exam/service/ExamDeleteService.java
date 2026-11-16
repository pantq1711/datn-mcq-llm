package com.datn.mcq.exam.service;

import com.datn.mcq.common.exception.ApiErrorException;
import com.datn.mcq.exam.entity.Exam;
import com.datn.mcq.exam.entity.ExamSlot;
import com.datn.mcq.exam.repository.ExamRepository;
import com.datn.mcq.exam.repository.ExamSlotRepository;
import com.datn.mcq.generation.service.GenerationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * NV-01 (rà soát 15/11, V31): giáo viên xóa đề của mình. Xóa MỀM ({@code exam.deleted_at}): không xóa vật lý dòng nào
 * (exam_slot, exam_slot_action, generated_question là số liệu chương 4); đề đã xóa không hiện ở đâu nữa (404 như không tồn
 * tại), không tính vào MATRIX_IN_USE (trừ sửa cấu trúc ma trận), MATERIAL_IN_USE và số "đã dùng" của câu ngân hàng. Câu đã
 * Giữ vẫn ở ngân hàng; hạn mức ngày đã trừ không hoàn lại.
 */
@Service
public class ExamDeleteService {

    /** Mã 409 khi đề còn chỗ đang soạn. */
    public static final String EXAM_GENERATING = "EXAM_GENERATING";

    private final ExamRepository examRepository;
    private final ExamSlotRepository slotRepository;
    private final GenerationService generationService;

    public ExamDeleteService(ExamRepository examRepository, ExamSlotRepository slotRepository,
                             GenerationService generationService) {
        this.examRepository = examRepository;
        this.slotRepository = slotRepository;
        this.generationService = generationService;
    }

    /**
     * Xóa đề {@code examId} của {@code actorId}. Không có, của người khác hoặc đã xóa: 404. Còn job sinh câu đang chạy hoặc
     * chờ chạy thường: 409 {@value #EXAM_GENERATING}, không đổi gì. Job đang hoãn vì hết lượt AI: cho xóa, job chuyển
     * failed ("exam_deleted"). Job answer_check/grounding_check để chạy nốt (chỉ ghi kết quả QC của câu).
     *
     * <p>Khóa hàng đề (FOR UPDATE), rồi các chỗ (cùng khóa của đối soát), rồi các job sinh câu còn mở, trong một transaction.
     */
    @Transactional
    public void delete(Integer examId, Integer actorId) {
        examRepository.lockLiveForUpdate(examId, actorId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đề id=" + examId));
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đề id=" + examId));
        List<Integer> slotIds = slotRepository.lockByExamId(examId).stream().map(ExamSlot::getId).toList();
        LocalDateTime now = LocalDateTime.now();
        if (!generationService.failDeferredGenerateJobsIfIdle(slotIds, now)) {
            throw ApiErrorException.conflict(EXAM_GENERATING, "Đề đang được soạn, chờ xong rồi xóa.");
        }
        exam.setDeletedAt(now);
        examRepository.save(exam);
    }
}
