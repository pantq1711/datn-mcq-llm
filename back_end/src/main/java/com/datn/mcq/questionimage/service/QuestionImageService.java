package com.datn.mcq.questionimage.service;

import com.datn.mcq.common.exception.ApiErrorException;
import com.datn.mcq.questionimage.model.QuestionImage;
import com.datn.mcq.questionimage.repository.QuestionImageRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class QuestionImageService {

    private final QuestionImageRepository repository;
    private final QuestionImageSigner signer;
    private final JdbcTemplate jdbcTemplate;

    public QuestionImageService(QuestionImageRepository repository,
                                QuestionImageSigner signer,
                                JdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.signer = signer;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record ImagePayload(
            boolean notModified,
            byte[] data,
            String mime,
            String etag,
            long maxAgeSeconds
    ) {}

    /**
     * Tải ảnh câu hỏi và kiểm tra chữ ký, hạn dùng, trạng thái tài khoản và quyền xem (H3).
     * Mọi trường hợp sai chữ ký, hết hạn, thiếu quyền hoặc không tìm thấy đều trả 404 IMAGE_NOT_FOUND.
     */
    @Transactional(readOnly = true)
    public ImagePayload getImagePayload(long imageId, long exp, int userId, String sig, String ifNoneMatch) {
        Instant now = Instant.now();

        // 1. Kiểm tra chữ ký
        if (!signer.verify(imageId, exp, userId, sig)) {
            throw notFound();
        }

        // 2. Kiểm tra hạn dùng
        if (signer.isExpired(exp, now)) {
            throw notFound();
        }

        // 3. Kiểm tra trạng thái tài khoản người dùng u (phải còn active)
        if (!isUserActive(userId)) {
            throw notFound();
        }

        // 4. Lấy thông tin ảnh
        QuestionImage image = repository.findById(imageId).orElseThrow(this::notFound);

        // 5. Kiểm tra quyền truy cập của userId đối với ảnh
        if (!hasAccessPermission(image, userId)) {
            throw notFound();
        }

        // 6. Xử lý ETag & If-None-Match
        String etag = "\"" + image.getSha256() + "\"";
        long maxAgeSeconds = Math.max(0, exp - now.getEpochSecond());

        if (ifNoneMatch != null && matchesEtag(ifNoneMatch, etag)) {
            return new ImagePayload(true, null, image.getMime(), etag, maxAgeSeconds);
        }

        return new ImagePayload(false, image.getData(), image.getMime(), etag, maxAgeSeconds);
    }

    private boolean isUserActive(int userId) {
        try {
            Boolean enabled = jdbcTemplate.queryForObject(
                    "SELECT enabled FROM users WHERE id = ?",
                    Boolean.class,
                    userId
            );
            return Boolean.TRUE.equals(enabled);
        } catch (EmptyResultDataAccessException e) {
            return false;
        }
    }

    private boolean hasAccessPermission(QuestionImage image, int userId) {
        // Trường hợp A: Chính chủ tải lên
        if (image.getOwnerId() == userId) {
            // Nếu ảnh vẫn đang ở trạng thái xem trước (chưa vào generated_question)
            if (image.getGeneratedQuestionId() == null) {
                // Kiểm tra xem lô nhập có bị hết hạn hay không. Không bắt lỗi DB: lỗi hệ thống phải thành 5xx có log.
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM import_batch WHERE id = ? AND status = 'preview' AND expires_at > CURRENT_TIMESTAMP",
                        Integer.class,
                        image.getImportBatchId()
                );
                return count != null && count > 0;
            }
            return true;
        }

        // Trường hợp B: Người khác xem ảnh (phải đã là câu ngân hàng và được phép xem)
        if (image.getGeneratedQuestionId() == null) {
            return false; // Ảnh preview của người khác thì không được xem
        }

        // Kiểm tra có bank_item nào của câu này mà công khai (visible_to_user_id IS NULL) hoặc thuộc về userId
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bank_item bi " +
                "WHERE bi.source_question_id = ? AND (bi.visible_to_user_id IS NULL OR bi.visible_to_user_id = ?)",
                Integer.class,
                image.getGeneratedQuestionId(),
                userId
        );
        return count != null && count > 0;
    }

    private boolean matchesEtag(String ifNoneMatch, String etag) {
        String clean = ifNoneMatch.trim();
        return clean.equals(etag) || clean.equals("W/" + etag) || clean.equals("*");
    }

    private ApiErrorException notFound() {
        return new ApiErrorException(HttpStatus.NOT_FOUND, "IMAGE_NOT_FOUND", "Không tìm thấy ảnh hoặc liên kết không hợp lệ");
    }
}
