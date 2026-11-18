package com.datn.mcq.questionimage.controller;

import com.datn.mcq.common.exception.ApiErrorException;
import com.datn.mcq.questionimage.service.QuestionImageService;
import com.datn.mcq.questionimage.service.QuestionImageService.ImagePayload;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QuestionImageController {

    private final QuestionImageService imageService;

    public QuestionImageController(QuestionImageService imageService) {
        this.imageService = imageService;
    }

    /**
     * Tải ảnh câu hỏi qua URL có chữ ký HMAC-SHA256 (hợp đồng H3).
     * Endpoint permitAll, không đọc JWT.
     */
    @GetMapping("/api/question-images/{id}")
    public ResponseEntity<byte[]> getQuestionImage(
            @PathVariable("id") String id,
            @RequestParam(value = "exp", required = false) String exp,
            @RequestParam(value = "u", required = false) String u,
            @RequestParam(value = "sig", required = false) String sig,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {

        // Hợp đồng: thiếu tham số, sai kiểu hay tràn số đều là 404 IMAGE_NOT_FOUND (không phân biệt).
        long imageId;
        long expEpoch;
        int userId;
        try {
            imageId = Long.parseLong(id);
            expEpoch = Long.parseLong(exp);
            userId = Integer.parseInt(u);
        } catch (NumberFormatException e) {
            throw new ApiErrorException(HttpStatus.NOT_FOUND, "IMAGE_NOT_FOUND",
                    "Không tìm thấy ảnh hoặc liên kết không hợp lệ");
        }

        ImagePayload payload = imageService.getImagePayload(imageId, expEpoch, userId, sig, ifNoneMatch);

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
