package com.datn.mcq.exam.dto;

import com.datn.mcq.enums.QuestionType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** DTO của luồng tạo đề theo ma trận (V7, phần C mục 2). */
public final class ExamBuildDtos {

    private ExamBuildDtos() {}

    /**
     * @param title     bỏ trống thì lấy tên ma trận
     * @param teacherId bỏ trống thì dùng giáo viên mặc định
     */
    public record BuildExamRequest(Integer matrixId, String title, Integer teacherId) {}

    /**
     * @param fromBank   số chỗ lấy câu từ ngân hàng
     * @param jobsQueued số job sinh câu đã xếp hàng cho phần còn thiếu
     */
    public record BuildResultDto(Integer examId, String examCode, String title, Integer matrixId, int slots,
                                 int fromBank, int jobsQueued) {}

    /**
     * Tiến độ của một ô.
     *
     * @param needed     số câu ô cần
     * @param fromBank   số chỗ dùng câu ngân hàng
     * @param ready      số chỗ đã có câu (kể cả câu ngân hàng, câu đã Giữ/Sửa)
     * @param generating số chỗ đang chờ sinh
     * @param failed     số chỗ không sinh được câu dùng được
     */
    public record CellProgressDto(Integer cellId, Integer position, String part, QuestionType questionType,
                                  String topic, String content, int needed, int fromBank, int ready, int generating,
                                  int failed) {}

    /**
     * @param status "generating" (còn chỗ đang sinh), "ready" (đủ câu) hoặc "partial" (hết việc nhưng có chỗ hỏng)
     */
    public record ProgressDto(Integer examId, String examCode, String title, String status, int total, int ready,
                              int generating, int failed, int fromBank, List<CellProgressDto> cells) {}

    /** Một chỗ trong đề (tóm tắt, không kèm nội dung câu). */
    public record SlotDto(
            Integer slotId,
            Integer cellId,
            Integer slotNo,
            QuestionType questionType,
            String state,
            @JsonInclude(JsonInclude.Include.NON_NULL) String source,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer bankItemId,
            @JsonInclude(JsonInclude.Include.NON_NULL) Integer questionId,
            int attempts
    ) {}
}
