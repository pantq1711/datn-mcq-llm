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
            TotalsDto totals
    ) {}

    public record MatrixSummaryDto(Integer id, String name, Integer subjectId, Integer gradeLevel,
                                   LocalDateTime createdAt, int cellCount) {}
}
