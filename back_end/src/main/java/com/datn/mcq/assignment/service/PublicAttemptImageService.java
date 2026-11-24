package com.datn.mcq.assignment.service;

import com.datn.mcq.assignment.dto.PublicAttemptDtos.PublicImageDto;
import com.datn.mcq.assignment.repository.AssignmentRepository;
import com.datn.mcq.assignment.repository.AssignmentRepository.AssignmentRow;
import com.datn.mcq.assignment.repository.AssignmentRepository.AttemptRow;
import com.datn.mcq.assignment.repository.AssignmentRepository.ItemRow;
import com.datn.mcq.bank.dto.BankImageDtos;
import com.datn.mcq.bank.dto.BankImageDtos.ImageFile;
import com.datn.mcq.bank.dto.BankImageDtos.SignedImage;
import com.datn.mcq.bank.service.BankService;
import com.datn.mcq.common.exception.ApiErrorException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hình của câu cho học sinh (S1, docs/design/cau-co-hinh.md mục 4.1; D1 = A, D3, D6 = private). URL ký theo lượt làm,
 * {@code sig = HMAC(khóa ảnh, "p|id|exp|attemptId")}; ảnh lấy qua {@link BankService} (module này không phụ thuộc
 * {@code questionimage}).
 *
 * <p>Điều kiện hiển thị giống JSON P2/P3: hình câu dẫn, phương án, ý có khi lượt còn nộp được ({@code parts}) hoặc khi kết quả
 * đã mở và giáo viên bật {@code showSolutions} ({@code review}); hình lời giải CHỈ trong trường hợp sau. Không dùng quyền
 * xem ảnh của giáo viên ({@code hasAccessPermission}, lọc {@code removed_at}): bài đã giao đọc câu theo
 * {@code assignment_item.bank_item_id}, câu gỡ khỏi ngân hàng vẫn hiện (quyết định 16/11).
 */
@Service
public class PublicAttemptImageService {

    /** Cùng mã với URL giáo viên (H3): mọi lỗi tải ảnh đều 404 này, không phân biệt lý do. */
    public static final String IMAGE_NOT_FOUND = "IMAGE_NOT_FOUND";
    /** Trần {@code max-age} (giây) của ảnh học sinh. */
    static final long MAX_AGE_CAP_SECONDS = 3600;

    private final AssignmentRepository repository;
    private final AssignmentLoader loader;
    private final PublicRateLimiter limiter;
    private final BankService bankService;

    public PublicAttemptImageService(AssignmentRepository repository, AssignmentLoader loader,
                                     PublicRateLimiter limiter, BankService bankService) {
        this.repository = repository;
        this.loader = loader;
        this.limiter = limiter;
        this.bankService = bankService;
    }

    /** Byte ảnh hoặc 304 ({@code notModified}, {@code data} null) kèm các giá trị header. */
    public record ImageResponse(boolean notModified, byte[] data, String mime, String etag, long maxAgeSeconds) {}

    // ---------- JSON P2/P3 ----------

    /**
     * {@code exp} của URL trong {@code parts}: hạn của lượt + ân hạn, cố định cả lượt nên URL giữ nguyên qua các lần P3 và
     * trình duyệt dùng lại cache.
     */
    static long attemptExp(AttemptRow attempt) {
        return attempt.deadlineAt().plus(AttemptRules.GRACE).atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    /**
     * Hình theo câu nguồn của đề, URL ký cho {@code attemptId}; một truy vấn cho cả đề. {@code withSolution} false thì bỏ hình
     * lời giải. {@code exp} null: hạn cuốn theo giờ như H3 (dùng cho {@code review}, ký lại mỗi lần P3). Câu không có hình
     * vắng khỏi kết quả (JSON bỏ trường {@code images}).
     */
    Map<Integer, List<PublicImageDto>> images(AssignmentPaper paper, int attemptId, boolean withSolution, Long exp) {
        Map<Integer, List<PublicImageDto>> out = new LinkedHashMap<>();
        bankService.attemptImagesBySourceQuestion(paper.sourceQuestionIds(), attemptId, exp)
                .forEach((questionId, images) -> {
                    List<PublicImageDto> list = images.stream()
                            .filter(i -> withSolution || !BankImageDtos.ROLE_SOLUTION.equals(i.role()))
                            .map(PublicAttemptImageService::toDto)
                            .toList();
                    if (!list.isEmpty()) {
                        out.put(questionId, list);
                    }
                });
        return out;
    }

    private static PublicImageDto toDto(SignedImage i) {
        return new PublicImageDto(i.id(), i.mime(), i.widthPx(), i.heightPx(), i.displayWidthMm(), i.url());
    }

    // ---------- tải ảnh ----------

    /**
     * Tải một ảnh. Thứ tự: chữ ký và hạn (không chạm DB); hạn mức theo lượt (429); lượt còn tồn tại; điều kiện hiển thị như
     * JSON; ảnh thuộc một câu của bài giao; hình lời giải chỉ khi được xem lời giải. Mọi trường hợp không đạt: 404
     * {@value #IMAGE_NOT_FOUND}. Đóng lười như P3 nên có thể ghi (transaction thường).
     */
    @Transactional
    public ImageResponse load(long imageId, long exp, int attemptId, String sig, String ifNoneMatch) {
        if (!bankService.verifyAttemptImageUrl(imageId, exp, attemptId, sig)) {
            throw notFound();
        }
        limiter.checkImage(attemptId);
        AttemptRow attempt = repository.attemptById(attemptId).orElseThrow(PublicAttemptImageService::notFound);
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        AssignmentRow a = loader.refresh(repository.findById(attempt.assignmentId())
                .orElseThrow(PublicAttemptImageService::notFound), now);
        boolean solutions = AttemptRules.resultsReleased(a, now) && a.showSolutions();
        if (!solutions && !AttemptRules.canSubmit(a, attempt, now)) {
            throw notFound();
        }
        List<Integer> bankItemIds = repository.items(a.id()).stream().map(ItemRow::bankItemId).toList();
        ImageFile file = bankService.imageOfBankItems(imageId, bankItemIds)
                .orElseThrow(PublicAttemptImageService::notFound);
        if (BankImageDtos.ROLE_SOLUTION.equals(file.role()) && !solutions) {
            throw notFound();
        }
        String etag = "\"" + file.sha256() + "\"";
        long maxAge = Math.min(MAX_AGE_CAP_SECONDS, Math.max(0, exp - Instant.now().getEpochSecond()));
        if (ifNoneMatch != null && matchesEtag(ifNoneMatch, etag)) {
            return new ImageResponse(true, null, file.mime(), etag, maxAge);
        }
        return new ImageResponse(false, file.data(), file.mime(), etag, maxAge);
    }

    private static boolean matchesEtag(String ifNoneMatch, String etag) {
        String clean = ifNoneMatch.trim();
        return clean.equals(etag) || clean.equals("W/" + etag) || clean.equals("*");
    }

    public static ApiErrorException notFound() {
        return new ApiErrorException(HttpStatus.NOT_FOUND, IMAGE_NOT_FOUND, "Không tìm thấy hình hoặc liên kết không hợp lệ");
    }
}
