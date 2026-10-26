package com.datn.mcq.matrix.dto;

import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.QuestionType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** DTO của API ma trận đề (V6). */
public final class MatrixDtos {

    private MatrixDtos() {}

    /**
     * Body tạo ma trận. {@code subjectId} bỏ trống khi tạo từ mẫu thì dùng môn "Toán".
     *
     * @param notes ghi chú tự do (giữ nguyên, không xử lý)
     */
    public record CreateMatrixRequest(
            String name,
            Integer subjectId,
            Integer gradeLevel,
            String source,
            List<String> notes,
            List<CellInput> cells
    ) {}

    /**
     * Một ô của ma trận.
     *
     * @param part                  "I" (mcq), "II" (tf) hoặc "III" (sa)
     * @param learningOutcomes      yêu cầu cần đạt theo mức, khóa "easy", "medium", "hard"; bắt buộc có mọi mức ô dùng
     * @param difficulty            mức của câu mcq/sa; không gửi với tf
     * @param statementDifficulties 4 mức của ý a-d (chỉ tf)
     * @param questionCount         số câu, từ 1 đến 50 (tf: mỗi câu 4 lệnh hỏi)
     */
    public record CellInput(
            String part,
            QuestionType questionType,
            String topic,
            String content,
            Map<String, String> learningOutcomes,
            Difficulty difficulty,
            List<Difficulty> statementDifficulties,
            Integer questionCount
    ) {}

    /**
     * Một nhóm câu của lần sinh theo bài (W1-4): cùng dạng và mức. Phần I/II/III suy ra từ dạng câu.
     *
     * @param difficulty            mức của câu mcq/sa; không gửi với tf
     * @param statementDifficulties 4 mức của ý a-d (chỉ tf); bỏ trống thì mặc định dễ, dễ, vừa, khó
     * @param questionCount         số câu, từ 1 đến 50
     */
    public record LessonCellInput(
            QuestionType questionType,
            Difficulty difficulty,
            List<Difficulty> statementDifficulties,
            Integer questionCount
    ) {}

    /**
     * Body sinh theo bài (POST /api/exams/build-by-lesson, W1-4): một bài, các nhóm câu theo dạng và mức. Chủ đề của bài
     * là chuỗi {@code lesson}; về sau có thể thêm {@code lessonId} tùy chọn (danh mục bài) mà không đổi các trường này.
     *
     * @param content          phạm vi, nội dung cụ thể trong bài (tùy chọn)
     * @param title            tên đề; bỏ trống thì lấy tên bài
     * @param learningOutcomes yêu cầu cần đạt theo mức (khóa easy, medium, hard), ghi đè mặc định; tùy chọn
     * @param materialIds      (W2-3) tài liệu bài học đính kèm (của chính người gọi, tối đa 5); tùy chọn
     * @param extraInstruction (W2-3) "yêu cầu thêm cho AI" cho cả lần sinh, tối đa 1000 ký tự; tùy chọn
     */
    public record CreateLessonRequest(
            Integer subjectId,
            Integer gradeLevel,
            String lesson,
            String content,
            String title,
            Map<String, String> learningOutcomes,
            List<LessonCellInput> cells,
            List<Integer> materialIds,
            String extraInstruction
    ) {}

    /** Body tạo ma trận từ mẫu có sẵn. */
    public record CreateFromSampleRequest(Integer subjectId) {}

    public record SampleDto(String key, String name, Integer gradeLevel, String source, List<String> notes) {}

    public record CellDto(
            Integer id,
            Integer position,
            String part,
            QuestionType questionType,
            String topic,
            String content,
            Map<String, String> learningOutcomes,
            @JsonInclude(JsonInclude.Include.NON_NULL) Difficulty difficulty,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<Difficulty> statementDifficulties,
            Integer questionCount,
            Integer templateId
    ) {}

    /**
     * @param questionsByPart số câu theo phần I, II, III
     * @param commandsByLevel số lệnh hỏi theo mức (một ý tf là một lệnh hỏi)
     */
    public record TotalsDto(Map<String, Integer> questionsByPart, Map<String, Integer> commandsByLevel,
                            int totalCommands) {}

    public record MatrixDto(
            Integer id,
            String name,
            Integer subjectId,
            Integer gradeLevel,
            String source,
            List<String> notes,
            @JsonInclude(JsonInclude.Include.NON_NULL) String sampleKey,
            LocalDateTime createdAt,
            List<CellDto> cells,
            TotalsDto totals,
            int examCount
    ) {}

    /** @param examCount số đề đã tạo từ ma trận (W1-4): lớn hơn 0 thì khóa sửa ô và xóa */
    public record MatrixSummaryDto(Integer id, String name, Integer subjectId, Integer gradeLevel,
                                   LocalDateTime createdAt, int cellCount, int examCount) {}

    /** Body nhân bản ma trận (tùy chọn): {@code name} bỏ trống thì "&lt;tên&gt; (bản sao)". */
    public record DuplicateMatrixRequest(String name) {}
}
