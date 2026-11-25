package com.datn.mcq.assignment.controller;

import com.datn.mcq.assignment.dto.PublicAttemptDtos.AttemptViewDto;
import com.datn.mcq.assignment.dto.PublicAttemptDtos.InfoDto;
import com.datn.mcq.assignment.dto.PublicAttemptDtos.StartRequest;
import com.datn.mcq.assignment.dto.PublicAttemptDtos.SubmitRequest;
import com.datn.mcq.assignment.dto.PublicAttemptDtos.SubmitResultDto;
import com.datn.mcq.assignment.service.PublicAttemptImageService;
import com.datn.mcq.assignment.service.PublicAttemptImageService.ImageResponse;
import com.datn.mcq.assignment.service.PublicAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API công khai cho học sinh (docs/design/lam-bai-truc-tuyen.md mục 3.2): không đăng nhập ({@code permitAll} cho
 * {@code /api/public/**}); token lượt làm ở header {@value #TOKEN_HEADER}, không bao giờ trong URL. Mọi phản hồi thành
 * công có {@code Cache-Control: no-store}, trừ tải hình ({@code private}, S1).
 */
@RestController
@RequestMapping("/api/public")
public class PublicAttemptController {

    public static final String TOKEN_HEADER = "X-Attempt-Token";

    private final PublicAttemptService service;
    private final PublicAttemptImageService images;

    public PublicAttemptController(PublicAttemptService service, PublicAttemptImageService images) {
        this.service = service;
        this.images = images;
    }

    /** P1: trang chào. 404 khi mã sai. */
    @GetMapping("/assignments/{code}")
    public ResponseEntity<InfoDto> info(@PathVariable String code) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.info(code));
    }

    /**
     * P2: bắt đầu. 201 kèm token (chỉ trả một lần); 400, 404, 409 ASSIGNMENT_CLOSED / ASSIGNMENT_FULL /
     * ATTEMPT_IDENTITY_TAKEN (25/11: trùng lớp + tên hoặc số mà chưa xác nhận), 429.
     */
    @PostMapping("/assignments/{code}/attempts")
    public ResponseEntity<AttemptViewDto> start(@PathVariable String code,
                                                @RequestBody(required = false) StartRequest request,
                                                HttpServletRequest http) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(service.start(code, request, http.getRemoteAddr()));
    }

    /** P3: mở lại. 401 ATTEMPT_TOKEN_INVALID, 429. */
    @GetMapping("/attempts/current")
    public ResponseEntity<AttemptViewDto> current(@RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.current(token));
    }

    /** P4: nộp. 400, 401, 409 ATTEMPT_ALREADY_SUBMITTED / ATTEMPT_EXPIRED / ASSIGNMENT_CLOSED, 429. */
    @PostMapping("/attempts/current/submit")
    public ResponseEntity<SubmitResultDto> submit(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                                  @RequestBody(required = false) SubmitRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.submit(token, request));
    }

    /**
     * Tải một hình của câu (S1, docs/design/cau-co-hinh.md mục 4.1): URL ký theo lượt làm, không token, không JWT (thẻ
     * {@code <img>} không gửi header). Mọi lỗi (thiếu hoặc sai kiểu tham số, chữ ký sai, hết hạn, lượt đã xóa, ảnh không được
     * hiển thị cho lượt này) là 404 IMAGE_NOT_FOUND; 429 khi vượt hạn mức theo lượt. Riêng phản hồi này có
     * {@code Cache-Control: private, max-age} (D6) thay cho {@code no-store}.
     */
    @GetMapping("/question-images/{id}")
    public ResponseEntity<byte[]> image(@PathVariable("id") String id,
                                        @RequestParam(value = "exp", required = false) String exp,
                                        @RequestParam(value = "a", required = false) String a,
                                        @RequestParam(value = "sig", required = false) String sig,
                                        @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
                                        String ifNoneMatch) {
        long imageId;
        long expEpoch;
        int attemptId;
        try {
            imageId = Long.parseLong(id);
            expEpoch = Long.parseLong(exp);
            attemptId = Integer.parseInt(a);
        } catch (NumberFormatException e) {
            throw PublicAttemptImageService.notFound();
        }
        ImageResponse payload = images.load(imageId, expEpoch, attemptId, sig, ifNoneMatch);

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CACHE_CONTROL, "private, max-age=" + payload.maxAgeSeconds());
        headers.set(HttpHeaders.ETAG, payload.etag());
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");
        if (payload.notModified()) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).headers(headers).build();
        }
        headers.setContentType(MediaType.parseMediaType(payload.mime()));
        return ResponseEntity.ok().headers(headers).body(payload.data());
    }
}
