package com.datn.mcq.generation.service;

import com.datn.mcq.qc.dto.DedupCandidate;

import java.util.List;

/**
 * W1-3: nguồn ứng viên so trùng thêm cho câu sinh cho một chỗ trong đề. {@code generation} không biết {@code exam}
 * (backend-structure quy tắc 10): module {@code exam} cài interface này, {@code generation} chỉ gọi khi job có
 * {@code exam_slot_id}.
 */
public interface SlotDedupContext {

    /**
     * Các câu đang nằm trong cùng đề với chỗ {@code examSlotId} (trừ chính chỗ đó), cùng dạng câu với chỗ: câu hiện tại
     * của các chỗ khác kể cả chưa qua Solver, văn bản câu ngân hàng đang dùng (có thể đã sửa lúc duyệt), câu vừa sinh
     * của chỗ đang sinh. Chỉ đọc; không có thì rỗng.
     */
    List<DedupCandidate> candidatesForSlot(Integer examSlotId);

    /**
     * Thẩm định 31/10 (lớp 1): câu dẫn (đề, hoặc đề dẫn với câu tf) của các câu khác đang có trong CÙNG ô, cùng đề với chỗ
     * {@code examSlotId}, theo thứ tự chỗ, để prompt sinh câu yêu cầu chọn dạng bài khác. Cùng định nghĩa "câu đang có" với
     * {@link #candidatesForSlot}: câu hiện tại của chỗ khác (kể cả chưa qua Solver), văn bản câu ngân hàng đang dùng, câu mới
     * nhất và câu fallback của chỗ đang sinh. KHÔNG gồm chính chỗ {@code examSlotId} nên câu đang bị Đổi không có trong danh
     * sách. Chỉ đọc; không có thì rỗng.
     */
    default List<String> sameCellQuestionTexts(Integer examSlotId) {
        return List.of();
    }

    /**
     * W2-4a: ID người tạo đề chứa chỗ {@code examSlotId}, dùng để lọc ứng viên ngân hàng riêng tư. Null khi không tìm thấy.
     */
    default Integer creatorUserIdForSlot(Integer examSlotId) {
        return null;
    }
}
