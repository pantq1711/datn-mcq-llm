package com.datn.mcq.material.service;

import com.datn.mcq.common.exception.ApiErrorException;
import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.material.dto.MaterialDtos.MaterialDto;
import com.datn.mcq.material.dto.MaterialDtos.MaterialPageDto;
import com.datn.mcq.material.dto.MaterialDtos.WarningDto;
import com.datn.mcq.material.entity.LessonMaterial;
import com.datn.mcq.material.extract.ExtractedText;
import com.datn.mcq.material.extract.ExtractionWarning;
import com.datn.mcq.material.extract.MaterialProperties;
import com.datn.mcq.material.extract.MaterialTextExtractor;
import com.datn.mcq.material.repository.LessonMaterialRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tài liệu bài học của giáo viên (W2-3): tải lên (trích văn bản, lưu văn bản và sha256, không lưu file gốc), danh sách,
 * chi tiết, xóa. Mọi thao tác theo chủ sở hữu; tài liệu của người khác trả NoSuchElementException (404) như không tồn tại.
 *
 * <p>Tải lên KHÔNG chạy trong một transaction lớn: trích văn bản (pandoc, PDFBox) chạy ngoài transaction, chỉ bước lưu
 * là một transaction ngắn. Hai lần tải đồng thời cùng một file của cùng chủ sở hữu: UNIQUE (owner_id, sha256) chặn lần
 * thứ hai, lần đó đọc lại và dùng bản đã lưu.
 */
@Service
public class MaterialService {

    /** Số ký tự đầu của văn bản trả ở chi tiết. */
    public static final int PREVIEW_CHARS = 500;

    private static final long HOUR_MS = 3_600_000L;

    /** @param created true khi vừa tạo mới (201); false khi dùng lại bản đã có cùng sha256 (200) */
    public record UploadResult(MaterialDto material, boolean created) {}

    private final LessonMaterialRepository repository;
    private final MaterialTextExtractor extractor;
    private final MaterialProperties props;
    private final MaterialUsageLookup usage;
    private final Map<Integer, Deque<Long>> uploadTimes = new ConcurrentHashMap<>();

    public MaterialService(LessonMaterialRepository repository, MaterialTextExtractor extractor,
                           MaterialProperties props, MaterialUsageLookup usage) {
        this.repository = repository;
        this.extractor = extractor;
        this.props = props;
        this.usage = usage;
    }

    public UploadResult upload(Integer ownerId, String originalFileName, byte[] content) {
        checkRate(ownerId);
        String sha256 = sha256(content);
        Optional<LessonMaterial> existing = repository.findByOwnerIdAndSha256(ownerId, sha256);
        if (existing.isPresent()) {
            return new UploadResult(toDto(existing.get(), false), false);
        }
        if (repository.countByOwnerId(ownerId) >= props.getMaxPerOwner()) {
            throw ApiErrorException.conflict("MATERIAL_QUOTA_EXCEEDED", "Mỗi giáo viên lưu tối đa "
                    + props.getMaxPerOwner() + " tài liệu. Hãy xóa bớt tài liệu không dùng rồi tải lại.");
        }
        ExtractedText text = extractor.extract(content);
        LessonMaterial m = new LessonMaterial();
        m.setOwnerId(ownerId);
        m.setFileName(sanitizeFileName(originalFileName, text.mimeType()));
        m.setMimeType(text.mimeType());
        m.setSizeBytes((long) content.length);
        m.setSha256(sha256);
        m.setExtractMethod(text.method());
        m.setPageCount(text.pageCount());
        m.setExtractedText(text.text());
        m.setCharCount(text.text().codePointCount(0, text.text().length()));
        m.setWarnings(ExtractionWarning.toJson(text.warnings()));
        // Postgres lưu tới micro giây: cắt trước để phản hồi lúc tạo giống phản hồi đọc lại.
        m.setCreatedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MICROS));
        try {
            return new UploadResult(toDto(repository.saveAndFlush(m), false), true);
        } catch (DataIntegrityViolationException race) {
            Optional<LessonMaterial> winner = repository.findByOwnerIdAndSha256(ownerId, sha256);
            if (winner.isPresent()) {
                return new UploadResult(toDto(winner.get(), false), false);
            }
            throw race;
        }
    }

    @Transactional(readOnly = true)
    public MaterialPageDto list(Integer ownerId, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page phải từ 0");
        }
        if (size < 1 || size > 50) {
            throw new IllegalArgumentException("size phải từ 1 đến 50");
        }
        List<MaterialDto> items = repository.findByOwnerIdOrderByIdDesc(ownerId, PageRequest.of(page, size)).stream()
                .map(m -> toDto(m, false)).toList();
        long total = repository.countByOwnerId(ownerId);
        return new MaterialPageDto(items, page, size, total, (int) ((total + size - 1) / size));
    }

    @Transactional(readOnly = true)
    public MaterialDto get(Integer id, Integer ownerId) {
        return toDto(owned(id, ownerId), true);
    }

    /**
     * Xóa tài liệu của chính chủ sở hữu; đã gắn vào đề thì 409 {@value #MATERIAL_IN_USE} (quyết định G). Của người khác
     * hoặc không có: 404.
     */
    @Transactional
    public void delete(Integer id, Integer ownerId) {
        LessonMaterial material = owned(id, ownerId);
        int exams = usage.examCount(id);
        if (exams > 0) {
            throw ApiErrorException.conflict(MATERIAL_IN_USE, "Tài liệu đã dùng cho " + exams
                    + " đề nên không xóa được.");
        }
        repository.delete(material);
    }

    /** Mã 409 khi xóa tài liệu đã gắn vào đề. */
    public static final String MATERIAL_IN_USE = "MATERIAL_IN_USE";

    /**
     * Kiểm tra mọi {@code ids} là tài liệu của {@code ownerId} (không thì 404, thông báo như không tồn tại) và giữ khóa
     * chia sẻ trên các hàng tới hết transaction của nơi gọi (tạo đề), để lần xóa đồng thời phải chờ.
     */
    @Transactional
    public void requireOwnedShared(Collection<Integer> ids, Integer ownerId) {
        if (ids.isEmpty()) {
            return;
        }
        Set<Integer> found = new HashSet<>(repository.lockOwnedIds(ids, ownerId));
        for (Integer id : ids) {
            if (!found.contains(id)) {
                throw new NoSuchElementException("Không tìm thấy tài liệu id=" + id);
            }
        }
    }

    private LessonMaterial owned(Integer id, Integer ownerId) {
        return repository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy tài liệu id=" + id));
    }

    // ---------- giới hạn tần suất ----------

    /** Tính mọi lần thử trong một giờ gần nhất (kể cả lần lỗi); quá hạn thì 429. Trong bộ nhớ, theo từng giáo viên. */
    private void checkRate(Integer ownerId) {
        long now = System.currentTimeMillis();
        Deque<Long> times = uploadTimes.computeIfAbsent(ownerId, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && now - times.peekFirst() >= HOUR_MS) {
                times.pollFirst();
            }
            if (times.size() >= props.getMaxUploadsPerHour()) {
                throw new ApiErrorException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_UPLOADS",
                        "Tải tài liệu quá nhiều lần (tối đa " + props.getMaxUploadsPerHour()
                                + " lần mỗi giờ). Hãy thử lại sau.");
            }
            times.addLast(now);
        }
    }

    // ---------- chuyển đổi ----------

    private static MaterialDto toDto(LessonMaterial m, boolean withPreview) {
        String preview = null;
        if (withPreview) {
            String t = m.getExtractedText();
            int end = t.offsetByCodePoints(0, Math.min(PREVIEW_CHARS, t.codePointCount(0, t.length())));
            preview = t.substring(0, end);
        }
        return new MaterialDto(m.getId(), m.getFileName(), kindOf(m.getMimeType()), m.getSizeBytes(), m.getCharCount(),
                m.getPageCount(), m.getSha256(), m.getExtractMethod(), warningsOf(m.getWarnings()), m.getCreatedAt(),
                preview);
    }

    static String kindOf(String mime) {
        return switch (mime) {
            case LessonMaterial.MIME_DOCX -> "docx";
            case LessonMaterial.MIME_PDF -> "pdf";
            default -> "txt";
        };
    }

    @SuppressWarnings("unchecked")
    private static List<WarningDto> warningsOf(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        Object parsed = SimpleJson.parseObject("{\"w\":" + json + "}").get("w");
        List<WarningDto> out = new ArrayList<>();
        if (parsed instanceof List<?> list) {
            for (Object o : list) {
                Map<String, Object> w = (Map<String, Object>) o;
                out.add(new WarningDto(String.valueOf(w.get("code")),
                        w.get("count") instanceof Number c ? c.intValue() : null,
                        w.get("total") instanceof Number t ? t.intValue() : null,
                        w.get("message") == null ? "" : String.valueOf(w.get("message"))));
            }
        }
        return out;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM không có SHA-256", e);
        }
    }

    /**
     * Tên hiển thị từ tên file client gửi: bỏ đường dẫn (cả / và \), ký tự điều khiển, ký tự định dạng ẩn (bidi), gộp
     * khoảng trắng, NFC, tối đa 255 ký tự (giữ đuôi). Chỉ để hiển thị: không bao giờ dùng làm đường dẫn file.
     */
    public static String sanitizeFileName(String raw, String mime) {
        String fallback = "tai-lieu" + switch (kindOf(mime)) {
            case "docx" -> ".docx";
            case "pdf" -> ".pdf";
            default -> ".txt";
        };
        if (raw == null) {
            return fallback;
        }
        String name = raw.substring(Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\')) + 1);
        name = Normalizer.normalize(name, Normalizer.Form.NFC);
        StringBuilder sb = new StringBuilder();
        name.codePoints().forEach(cp -> {
            if (Character.isWhitespace(cp)) {
                sb.append(' ');
            } else {
                int type = Character.getType(cp);
                if (type != Character.CONTROL && type != Character.FORMAT && type != Character.SURROGATE
                        && type != Character.PRIVATE_USE) {
                    sb.appendCodePoint(cp);
                }
            }
        });
        name = sb.toString().replaceAll(" {2,}", " ").strip();
        if (name.isEmpty()) {
            return fallback;
        }
        int length = name.codePointCount(0, name.length());
        if (length > 255) {
            int dot = name.lastIndexOf('.');
            String ext = dot > 0 && name.length() - dot <= 10 ? name.substring(dot) : "";
            int extLength = ext.codePointCount(0, ext.length());
            String base = name.substring(0, name.offsetByCodePoints(0, 255 - extLength));
            name = base + ext;
        }
        return name;
    }
}
