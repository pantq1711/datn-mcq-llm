package com.datn.mcq.exam.dto;

import com.datn.mcq.enums.QuestionType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** DTO của luồng tạo đề theo ma trận (V7, phần C mục 2). */
public final class ExamBuildDtos {

    private ExamBuildDtos() {}

    /**
     * Người tạo đề là người đăng nhập (W1-1, đã bỏ teacherId; client cũ gửi lên thì bị bỏ qua).
     *
     * @param title       bỏ trống thì lấy tên ma trận
     * @param materialIds (W2-3) tài liệu bài học áp cho cả ma trận (của chính người gọi); tùy chọn
     * @param cells       (W2-3) ngữ cảnh riêng của từng ô (tài liệu, yêu cầu thêm cho AI); tùy chọn. Tối đa 5 tài liệu
     *                    khác nhau trong cả lần tạo đề
     */
    public record BuildExamRequest(Integer matrixId, String title, List<Integer> materialIds,
                                   List<CellContextInput> cells) {}

    /**
     * Ngữ cảnh sinh câu riêng của một ô ma trận (W2-3). Ô có tài liệu hoặc yêu cầu thêm thì bỏ qua ngân hàng khi tạo đề
     * (câu lấy từ ngân hàng không theo được tài liệu hay yêu cầu).
     *
     * @param cellId           ô thuộc ma trận của đề; mỗi ô tối đa một phần tử
     * @param materialIds      tài liệu của ô (hợp với tài liệu của cả ma trận)
     * @param extraInstruction "yêu cầu thêm cho AI" cho ô, tối đa 1000 ký tự; là DỮ LIỆU cho prompt, không đổi định dạng,
     *                         số lượng, mức độ hay QC
     */
    public record CellContextInput(Integer cellId, List<Integer> materialIds, String extraInstruction) {}

    /**
     * @param fromBank   số chỗ lấy câu từ ngân hàng
     * @param jobsQueued số job sinh câu đã xếp hàng cho phần còn thiếu
     * @param matrixId   null với đề sinh theo bài (ma trận ẩn, không dùng được theo id)
     * @param kind       "matrix" (tạo từ ma trận) hoặc "lesson" (sinh theo bài), W1-4
     */
    public record BuildResultDto(Integer examId, String examCode, String title, Integer matrixId, int slots,
                                 int fromBank, int jobsQueued, String kind) {}

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
