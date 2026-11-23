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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongFunction;

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
        Instant now = Instant.now();
        return imagesBySourceQuestion(questionIds, id -> signer.buildSignedUrl(id, viewerId, now));
    }

    /**
     * S1 (docs/design/cau-co-hinh.md mục 4.1): như {@link #signedImagesBySourceQuestion} nhưng URL ký cho lượt làm
     * {@code attemptId} của học sinh ({@code /api/public/question-images/{id}?exp&a&sig}, chữ ký tiền tố {@code p|}).
     * {@code exp} (epoch giây) do nơi gọi chọn theo trạng thái lượt; null là hạn cuốn theo giờ như H3
     * ({@link QuestionImageSigner#calculateExp}). Trả mọi role: nơi gọi tự bỏ role {@code solution} khi chưa được xem lời
     * giải.
     */
    @Transactional(readOnly = true)
    public Map<Integer, List<QuestionImageDto>> attemptSignedImagesBySourceQuestion(Collection<Integer> questionIds,
                                                                                     int attemptId, Long exp) {
        long expiry = exp != null ? exp : signer.calculateExp(Instant.now());
        return imagesBySourceQuestion(questionIds, id -> signer.buildAttemptUrl(id, expiry, attemptId));
    }

    /**
     * Ảnh (URL ký cho người xem) và khóa hình H7 ({@link #imageKeysBySourceQuestion}) của các câu nguồn, lấy từ CÙNG một truy
     * vấn metadata. Câu không có hình vắng khỏi cả hai bản đồ; câu chỉ có ảnh lời giải vắng khỏi {@code imageKeys}.
     */
    public record SignedImages(Map<Integer, List<QuestionImageDto>> images, Map<Integer, String> imageKeys) {}

    /**
     * X1: như {@link #signedImagesBySourceQuestion} và trả thêm khóa hình của từng câu (cờ "giống câu khác trong đề" của xem
     * đề so cả hình), vẫn đúng một truy vấn metadata, không nạp cột data.
     */
    @Transactional(readOnly = true)
    public SignedImages signedImagesAndKeysBySourceQuestion(Collection<Integer> questionIds, int viewerId) {
        Instant now = Instant.now();
        List<MetadataProjection> metadata = metadataOf(questionIds);
        return new SignedImages(toDtos(metadata, id -> signer.buildSignedUrl(id, viewerId, now)), keysOf(metadata));
    }

    /**
     * H7 (dedup và cờ giống câu so cả hình): khóa hình của các câu nguồn ({@code generated_question.id}) theo lô, MỘT truy vấn
     * metadata (không nạp cột data); không có câu thì không truy vấn. Khóa là các {@code sha256} của ảnh khác role
     * {@code solution}, nối bằng dấu phẩy theo thứ tự {@code role} rồi {@code ordinal}; hai câu cùng khóa là cùng hình. Câu
     * không có ảnh nào ngoài lời giải vắng khỏi kết quả (nơi gọi coi như khóa rỗng). Đây là định nghĩa DUY NHẤT của khóa hình
     * cho câu ngân hàng (nhập từ Word so trùng ngân hàng, dedup trong đề, cờ giống câu khi xem đề).
     */
    @Transactional(readOnly = true)
    public Map<Integer, String> imageKeysBySourceQuestion(Collection<Integer> questionIds) {
        return keysOf(metadataOf(questionIds));
    }

    /** Một truy vấn metadata (không nạp cột data) cho mọi câu; ảnh của một câu theo {@code ordinal}. */
    private Map<Integer, List<QuestionImageDto>> imagesBySourceQuestion(Collection<Integer> questionIds,
                                                                        LongFunction<String> url) {
        return toDtos(metadataOf(questionIds), url);
    }

    /** Số id câu tối đa của một truy vấn metadata (so trùng khi nhập Word đưa cả ngân hàng của môn vào). */
    static final int METADATA_CHUNK = 1000;

    /**
     * Metadata ảnh của các câu (bỏ id null, không trùng); rỗng và không truy vấn khi không có câu. Một truy vấn cho tối đa
     * {@value #METADATA_CHUNK} câu (xem đề, trang ngân hàng luôn chỉ một truy vấn).
     */
    private List<MetadataProjection> metadataOf(Collection<Integer> questionIds) {
        List<Integer> ids = new ArrayList<>(new LinkedHashSet<>(questionIds == null ? List.of()
                : questionIds.stream().filter(Objects::nonNull).toList()));
        if (ids.isEmpty()) {
            return List.of();
        }
        if (ids.size() <= METADATA_CHUNK) {
            return repository.findMetadataByGeneratedQuestionIdIn(ids);
        }
        List<MetadataProjection> out = new ArrayList<>();
        for (int from = 0; from < ids.size(); from += METADATA_CHUNK) {
            out.addAll(repository.findMetadataByGeneratedQuestionIdIn(
                    ids.subList(from, Math.min(ids.size(), from + METADATA_CHUNK))));
        }
        return out;
    }

    private static Map<Integer, List<QuestionImageDto>> toDtos(List<MetadataProjection> metadata, LongFunction<String> url) {
        if (metadata.isEmpty()) {
            return Map.of();
        }
        Map<Integer, List<QuestionImageDto>> out = new LinkedHashMap<>();
        for (MetadataProjection m : metadata) {
            out.computeIfAbsent(m.getGeneratedQuestionId(), k -> new ArrayList<>())
                    .add(new QuestionImageDto(m.getId(), m.getRole(), m.getOrdinal(), m.getMime(), m.getWidthPx(),
                            m.getHeightPx(), m.getDisplayWidthMm(), url.apply(m.getId())));
        }
        return out;
    }

    /** Khóa hình H7 theo câu, xem {@link #imageKeysBySourceQuestion}. */
    private static Map<Integer, String> keysOf(List<MetadataProjection> metadata) {
        Map<Integer, List<MetadataProjection>> byQuestion = new LinkedHashMap<>();
        for (MetadataProjection m : metadata) {
            if (!"solution".equals(m.getRole())) {
                byQuestion.computeIfAbsent(m.getGeneratedQuestionId(), k -> new ArrayList<>()).add(m);
            }
        }
        Map<Integer, String> out = new LinkedHashMap<>();
        byQuestion.forEach((questionId, images) -> out.put(questionId, images.stream()
                .sorted(java.util.Comparator.comparing(MetadataProjection::getRole)
                        .thenComparing(MetadataProjection::getOrdinal))
                .map(MetadataProjection::getSha256)
                .collect(java.util.stream.Collectors.joining(","))));
        return out;
    }

    /** S1: chữ ký URL học sinh đúng và chưa hết hạn. Không truy cập DB, nên gọi trước mọi kiểm tra khác. */
    public boolean verifyAttemptUrl(long imageId, long exp, int attemptId, String sig) {
        return signer.verifyForAttempt(imageId, exp, attemptId, sig) && !signer.isExpired(exp, Instant.now());
    }

    /**
     * S1: ảnh {@code imageId} nếu nó thuộc một trong các câu nguồn {@code questionIds}; rỗng nếu không có ảnh đó hoặc ảnh
     * thuộc câu khác (kể cả ảnh xem trước chưa gắn câu). Không kiểm người xem: nơi gọi đã chốt tập câu được xem. Cố ý không
     * dùng {@code hasAccessPermission} (đòi {@code removed_at IS NULL}, sai với bài đã giao, quyết định 16/11).
     */
    @Transactional(readOnly = true)
    public Optional<QuestionImage> imageOfSourceQuestions(long imageId, Collection<Integer> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return Optional.empty();
        }
        Set<Integer> ids = new HashSet<>(questionIds);
        return repository.findById(imageId)
                .filter(image -> image.getGeneratedQuestionId() != null && ids.contains(image.getGeneratedQuestionId()));
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

    /** Byte ảnh để nhúng vào file Word (O2); {@code displayWidthMm} là bề rộng trong Word gốc. */
    public record ExportImage(long id, String mime, byte[] data, int displayWidthMm) {}

    /**
     * O2: dung lượng đã lưu của các ảnh {@code imageIds}, một truy vấn không nạp cột data (để kiểm trần trước khi nạp byte).
     * Id không tồn tại vắng khỏi kết quả; không có id thì không truy vấn. Không kiểm quyền: nơi gọi chỉ đưa id ảnh đã có
     * trong dữ liệu xem đề của người xuất (đã lọc theo quyền, H3(a)).
     */
    @Transactional(readOnly = true)
    public Map<Long, Integer> byteSizes(Collection<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Integer> out = new LinkedHashMap<>();
        repository.findSizesByIdIn(Set.copyOf(imageIds)).forEach(s -> out.put(s.getId(), s.getByteSize()));
        return out;
    }

    /**
     * O2: byte ảnh để nhúng data URI khi xuất Word, một truy vấn (transaction đọc ngắn, đóng trước khi chạy pandoc). Không
     * đi qua URL ký. Không kiểm quyền, như {@link #byteSizes}.
     */
    @Transactional(readOnly = true)
    public Map<Long, ExportImage> exportImages(Collection<Long> imageIds) {
        if (imageIds == null || imageIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ExportImage> out = new LinkedHashMap<>();
        for (QuestionImageRepository.ExportProjection p : repository.findExportDataByIdIn(Set.copyOf(imageIds))) {
            out.put(p.getId(), new ExportImage(p.getId(), p.getMime(), p.getData(), p.getDisplayWidthMm()));
        }
        return out;
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
