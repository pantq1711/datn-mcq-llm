package com.datn.mcq.assignment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** DTO phân tích câu của bài giao, B1/B2 (docs/design/lam-bai-truc-tuyen.md mục 5.5 và 14). */
public final class ItemStatDtos {

    private ItemStatDtos() {}

    /**
     * Lượt không được tính. {@code duplicate}: lượt ĐÃ NỘP nhưng trùng lớp + số thứ tự với một lượt khác (cùng quy tắc
     * "trùng" của bảng điểm, kể cả trùng với lượt bỏ dở). {@code notSubmitted}: lượt chưa nộp (đang làm hoặc quá hạn).
     */
    public record ExcludedDto(int duplicate, int notSubmitted) {}

    /**
     * Một câu. {@code answerKey}: đáp án (mcq "B"), nhãn bốn ý (tf "TFFT") hoặc đáp số (sa) để giao diện in đậm.
     * {@code d}: null khi groupSize = 0. {@code flags}: mã cờ hậu kiểm (docs/contract/enums.md), {@code
     * ["insufficient_sample"]} khi n &lt; 20. {@code slotChanged}: chỗ này trên đề đã Đổi sang câu khác sau lúc giao.
     */
    public record ItemStatDto(Integer slotId, String part, int number, String questionType, Integer bankItemId,
                              String answerKey, BigDecimal p, BigDecimal d, Map<String, Object> choiceCounts,
                              List<String> flags, boolean slotChanged) {}

    /**
     * Kết quả B1/B2. {@code n}: số lượt được tính; {@code groupSize}: k = round(0,27 × n). {@code sampleSufficient}: n ≥
     * {@code minSampleSize} (20); khi false, D và cờ chưa đáng tin, {@code sampleNote} nói rõ điều đó. {@code items} theo
     * thứ tự trang đề; rỗng khi n = 0.
     */
    public record ItemStatsDto(Integer assignmentId, int n, int groupSize, LocalDateTime computedAt,
                               boolean sampleSufficient, int minSampleSize, String sampleNote, ExcludedDto excluded,
                               List<ItemStatDto> items) {}
}
