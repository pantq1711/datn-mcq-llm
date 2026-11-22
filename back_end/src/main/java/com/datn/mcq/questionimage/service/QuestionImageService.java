package com.datn.mcq.questionimage.service;

import com.datn.mcq.common.exception.ApiErrorException;
import com.datn.mcq.questionimage.dto.QuestionImageDto;
import com.datn.mcq.questionimage.model.QuestionImage;
import com.datn.mcq.questionimage.repository.QuestionImageRepository;
import com.datn.mcq.questionimage.repository.QuestionImageRepository.MetadataProjection;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    /**
     * H3 (O1): ảnh của các câu nguồn (generated_question) theo lô, mỗi ảnh kèm URL ký cho {@code viewerId}. Đúng một truy
     * vấn metadata (không nạp cột bytea data) bất kể số câu; không có câu thì không truy vấn. Câu không có hình vắng khỏi kết
     * quả; ảnh của một câu theo thứ tự {@code ordinal}.
     *
     * <p>Không kiểm quyền xem câu: nơi gọi chỉ đưa vào câu mà người xem được thấy (ngân hàng theo visible_to_user_id, xem đề
     * theo chủ đề). Quyền thật được kiểm lại mỗi lần tải ảnh ({@link #getImagePayload}), nên URL lộ ra ngoài vẫn không mở được
     * ảnh của câu người khác.</p>
     */
    @Transactional(readOnly = true)
    public Map<Integer, List<QuestionImageDto>> signedImagesBySourceQuestion(Collection<Integer> questionIds,
                                                                              int viewerId) {
        Set<Integer> ids = new LinkedHashSet<>();
        if (questionIds != null) {
            questionIds.stream().filter(Objects::nonNull).forEach(ids::add);
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Instant now = Instant.now();
        Map<Integer, List<QuestionImageDto>> out = new LinkedHashMap<>();
        for (MetadataProjection m : repository.findMetadataByGeneratedQuestionIdIn(ids)) {
            out.computeIfAbsent(m.getGeneratedQuestionId(), k -> new ArrayList<>())
                    .add(new QuestionImageDto(m.getId(), m.getRole(), m.getOrdinal(), m.getMime(), m.getWidthPx(),
                            m.getHeightPx(), m.getDisplayWidthMm(), signer.buildSignedUrl(m.getId(), viewerId, now)));
        }
        return out;
    }

    /**
     * Trong {@code questionIds} (câu nguồn, {@code generated_question.id}), những câu có ít nhất một ảnh (mọi role). Một
     * truy vấn, không nạp bytes; không có câu thì không truy vấn. Không kiểm quyền xem: chỉ để biết câu "có hình hay không".
     */
    @Transactional(readOnly = true)
    public Set<Integer> sourceQuestionsWithImages(Collection<Integer> questionIds) {
        Set<Integer> ids = new LinkedHashSet<>();
        if (questionIds != null) {
            questionIds.stream().filter(Objects::nonNull).forEach(ids::add);
        }
        if (ids.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(repository.findGeneratedQuestionIdsWithImages(ids));
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

        // Kiểm tra có bank_item nào của câu này mà công khai (visible_to_user_id IS NULL) hoặc thuộc về userId,
        // và chưa bị gỡ khỏi ngân hàng (removed_at, V30)
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bank_item bi " +
                "WHERE bi.source_question_id = ? AND (bi.visible_to_user_id IS NULL OR bi.visible_to_user_id = ?) " +
                "AND bi.removed_at IS NULL",
                Integer.class,
                image.getGeneratedQuestionId(),
                userId
        );
        if (count != null && count > 0) {
            return true;
        }

        // Trường hợp C (D2, quyết định 16/11): câu đã gỡ khỏi ngân hàng vẫn hiển thị ở đề cũ, nên người là chủ một đề
        // chưa xóa có chỗ dùng câu đó vẫn xem được ảnh. Cố ý không đòi removed_at IS NULL; đề đã xóa không tính.
        Integer slotCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM exam_slot es " +
                "JOIN exam e ON e.id = es.exam_id AND e.deleted_at IS NULL AND e.created_by = ? " +
                "JOIN bank_item bi ON bi.id = es.bank_item_id " +
                "WHERE bi.source_question_id = ?",
                Integer.class,
                userId,
                image.getGeneratedQuestionId()
        );
        return slotCount != null && slotCount > 0;
    }

    private boolean matchesEtag(String ifNoneMatch, String etag) {
        String clean = ifNoneMatch.trim();
        return clean.equals(etag) || clean.equals("W/" + etag) || clean.equals("*");
    }

    private ApiErrorException notFound() {
        return new ApiErrorException(HttpStatus.NOT_FOUND, "IMAGE_NOT_FOUND", "Không tìm thấy ảnh hoặc liên kết không hợp lệ");
    }
}
