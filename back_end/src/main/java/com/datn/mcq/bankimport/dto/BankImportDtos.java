package com.datn.mcq.bankimport.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Hợp đồng JSON của {@code /api/bank-imports} (nhập ngân hàng câu từ Word, V26). {@link PreviewData} cũng là dạng lưu ở
 * cột {@code import_batch.preview}.
 */
public final class BankImportDtos {

    private BankImportDtos() {}

    /** Một phương án của câu mcq. */
    public record ItemOption(String key, String text) {}

    /** Một ý của câu tf; {@code label} true = Đúng, false = Sai, null = chưa xác định (kèm cờ chặn). */
    public record ItemStatement(String key, String text, Boolean label) {}

    /**
     * Một câu trong bản xem trước.
     *
     * @param key           mã câu trong lô, ví dụ {@code mcq-007} (dạng + thứ tự trong phần của file)
     * @param type          mcq, tf hoặc sa
     * @param locator       vị trí trong file Word ("Phần I, câu thứ 7"), để giáo viên tìm lại
     * @param levelRaw      nhãn mức trong file: NB, TH, VD, VDC hoặc null
     * @param level         easy (NB), medium (TH), hard (VD, VDC) hoặc null (không có nhãn: cờ chặn level_missing)
     * @param correctOption mcq: A-D hoặc null
     * @param saAnswer      sa: đáp số đã chuẩn hóa STRICT; khi không đạt STRICT là nguyên văn (kèm cờ chặn)
     * @param explanation   lời giải trong file; null khi không có (cờ thông tin no_solution)
     * @param flags         cờ của câu (cờ chặn và cờ thông tin), xem {@code ImportFlags}
     * @param blocking      có cờ chặn: không chọn được khi xác nhận
     */
    public record ImportItem(String key, String type, String locator, String levelRaw, String level, String stem,
                             List<ItemOption> options, String correctOption, List<ItemStatement> statements,
                             String saAnswer, String explanation, List<String> flags, boolean blocking) {}

    /** Câu bị bỏ (không nhập được ở bản đầu): chỉ vị trí và lý do, không có nội dung. */
    public record SkippedItem(String locator, String type, String reason) {}

    /** Bản xem trước đã lưu (cột import_batch.preview). */
    public record PreviewData(List<ImportItem> items, List<SkippedItem> skipped) {}

    /**
     * Một lô nhập. {@code items} và {@code skippedItems} chỉ có khi lô đang chờ xác nhận (status preview); đã xác nhận thì
     * rỗng (nội dung đã vào ngân hàng, bản xem trước bị xóa).
     *
     * @param questionCount   số câu đọc được trong file (kể cả câu bị bỏ)
     * @param importableCount số câu chọn được (không có cờ chặn)
     * @param blockedCount    số câu có cờ chặn
     * @param importedCount   số câu đã vào ngân hàng; null khi chưa xác nhận
     * @param skipped         số câu bị bỏ theo lý do (has_image, formula_unsupported, has_table)
     */
    public record BatchDto(Integer id, String displayName, String status, Integer subjectId, Integer gradeLevel,
                           String topic, LocalDateTime createdAt, LocalDateTime expiresAt, LocalDateTime confirmedAt,
                           int questionCount, int importableCount, int blockedCount, Integer importedCount,
                           Map<String, Integer> skipped, List<SkippedItem> skippedItems, List<ImportItem> items) {}

    /** Thân của POST /api/bank-imports/{id}/confirm: mã các câu chọn đưa vào ngân hàng. */
    public record ConfirmRequest(List<String> keys) {}

    /** Kết quả xác nhận. */
    public record ConfirmResult(Integer batchId, int imported, List<Integer> bankItemIds) {}
}
