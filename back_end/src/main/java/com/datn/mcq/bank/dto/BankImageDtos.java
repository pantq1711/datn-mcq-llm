package com.datn.mcq.bank.dto;

/**
 * Ảnh câu hỏi như module {@code bank} đưa cho module khác (D3 ngày 23/11, docs/design/cau-co-hinh.md mục 4.6): {@code assignment}
 * lấy ảnh qua {@code BankService} bằng các kiểu này, không phụ thuộc thẳng {@code questionimage} (Q-8 giữ nguyên).
 */
public final class BankImageDtos {

    private BankImageDtos() {}

    /** Metadata một ảnh kèm URL đã ký (không có byte ảnh). {@code role}: stem, option_a..d, statement_a..d, solution. */
    public record SignedImage(Long id, String role, Short ordinal, String mime, Integer widthPx, Integer heightPx,
                              Short displayWidthMm, String url) {}

    /** Byte ảnh để trả cho trình duyệt; {@code sha256} dùng làm ETag. */
    public record ImageFile(Long id, String role, String mime, byte[] data, String sha256) {}

    /** Vai của ảnh lời giải. */
    public static final String ROLE_SOLUTION = "solution";
}
