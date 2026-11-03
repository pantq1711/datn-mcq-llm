package com.datn.mcq.bankimport.service;

import com.datn.mcq.bank.dto.BankStatementInput;
import com.datn.mcq.bank.service.BankService;
import com.datn.mcq.bankimport.docx.DocxReader;
import com.datn.mcq.bankimport.dto.BankImportDtos.BatchDto;
import com.datn.mcq.bankimport.dto.BankImportDtos.ConfirmResult;
import com.datn.mcq.bankimport.dto.BankImportDtos.ImportItem;
import com.datn.mcq.bankimport.dto.BankImportDtos.ItemOption;
import com.datn.mcq.bankimport.dto.BankImportDtos.ItemStatement;
import com.datn.mcq.bankimport.dto.BankImportDtos.PreviewData;
import com.datn.mcq.bankimport.dto.BankImportDtos.SkippedItem;
import com.datn.mcq.bankimport.entity.ImportBatch;
import com.datn.mcq.bankimport.parse.DocParser;
import com.datn.mcq.bankimport.parse.LessonName;
import com.datn.mcq.bankimport.parse.ParsedDoc;
import com.datn.mcq.bankimport.repository.ImportBatchRepository;
import com.datn.mcq.bankimport.repository.ImportDedupRepository;
import com.datn.mcq.catalog.dto.CreateTemplateRequest;
import com.datn.mcq.catalog.entity.QuestionTemplate;
import com.datn.mcq.catalog.service.CatalogService;
import com.datn.mcq.common.exception.ApiErrorException;
import com.datn.mcq.config.PandocLimiter;
import com.datn.mcq.enums.AnswerChoice;
import com.datn.mcq.enums.DbEnum;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.Purpose;
import com.datn.mcq.enums.QuestionOrigin;
import com.datn.mcq.enums.QuestionStatus;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.ReviewDecision;
import com.datn.mcq.enums.SaAnswerKind;
import com.datn.mcq.enums.SourceType;
import com.datn.mcq.enums.StatementKey;
import com.datn.mcq.enums.TruthChoice;
import com.datn.mcq.enums.TruthValue;
import com.datn.mcq.experiment.entity.ExperimentRun;
import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.entity.GeneratedQuestionStatement;
import com.datn.mcq.generation.repository.GeneratedQuestionRepository;
import com.datn.mcq.generation.repository.GeneratedQuestionStatementRepository;
import com.datn.mcq.generation.service.GenerationService;
import com.datn.mcq.material.extract.DocxInputGuard;
import com.datn.mcq.review.entity.TeacherReview;
import com.datn.mcq.review.entity.TeacherReviewStatement;
import com.datn.mcq.review.repository.TeacherReviewRepository;
import com.datn.mcq.review.repository.TeacherReviewStatementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Nhập ngân hàng câu từ Word (V26, quyết định ngày 3/11, mô hình B1). Luồng: tải file → bản xem trước (không ghi câu nào)
 * → giáo viên chọn câu → xác nhận: trong MỘT transaction tạo template của bài, run "import-docx" theo dạng, generated_question
 * ({@code origin = 'import'}, {@code status = 'in_bank'}, {@code llm_difficulty} NULL, {@code final_difficulty} theo file),
 * teacher_review production {@code assisted}/{@code approved_as_is} với người duyệt là người tải, và bank_item riêng tư
 * ({@code visible_to_user_id} = người tải). Đây là đường thứ hai được phép tạo bank_item (M-9, M-10 bản sửa 3/11).
 *
 * <p>KHÔNG gọi LlmClient, không xếp job Solver, không trừ hạn mức sinh câu của giáo viên (D1). Không lưu file gốc, tên file
 * gốc hay phần "GV …" của tên file; log chỉ có số liệu và id, không có nội dung câu.
 */
@Service
public class BankImportService {

    private static final Logger LOG = LoggerFactory.getLogger(BankImportService.class);
    private static final long HOUR_MS = 3_600_000L;
    private static final String SUBJECT_NAME = "Toán";
    private static final int MAX_TOPIC = 255;
    private static final int MAX_BLOCKED_KEYS_IN_MESSAGE = 10;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final BankImportProperties props;
    private final DocxInputGuard inputGuard;
    private final PandocLimiter limiter;
    private final ImportBatchRepository batches;
    private final ImportDedupRepository dedup;
    private final CatalogService catalogService;
    private final GenerationService generationService;
    private final GeneratedQuestionRepository questions;
    private final GeneratedQuestionStatementRepository questionStatements;
    private final TeacherReviewRepository reviews;
    private final TeacherReviewStatementRepository reviewStatements;
    private final BankService bankService;
    private final ImportBatchWriter batchWriter;
    private final Map<Integer, Deque<Long>> uploadTimes = new ConcurrentHashMap<>();

    @Autowired
    public BankImportService(BankImportProperties props, DocxInputGuard inputGuard, PandocLimiter limiter,
                             ImportBatchRepository batches, ImportDedupRepository dedup, CatalogService catalogService,
                             GenerationService generationService, GeneratedQuestionRepository questions,
                             GeneratedQuestionStatementRepository questionStatements, TeacherReviewRepository reviews,
                             TeacherReviewStatementRepository reviewStatements, BankService bankService,
                             ImportBatchWriter batchWriter) {
        this.batchWriter = batchWriter;
        this.props = props;
        this.inputGuard = inputGuard;
        this.limiter = limiter;
        this.batches = batches;
        this.dedup = dedup;
        this.catalogService = catalogService;
        this.generationService = generationService;
        this.questions = questions;
        this.questionStatements = questionStatements;
        this.reviews = reviews;
        this.reviewStatements = reviewStatements;
        this.bankService = bankService;
    }

    // ---------- tải lên → bản xem trước ----------

    /**
     * Phân tích file và lưu bản xem trước (chưa lưu câu nào vào ngân hàng).
     *
     * @param fileName   tên file client gửi: CHỈ dùng để tách khối, chương, bài, tên bài; không lưu
     * @param topic      chủ đề do giáo viên nhập (tùy chọn); mặc định là tên bài trong tên file
     * @param gradeLevel khối do giáo viên nhập (tùy chọn); mặc định là khối trong tên file
     */
    public BatchDto upload(Integer ownerId, String fileName, byte[] content, String topic, Integer gradeLevel) {
        checkRate(ownerId);
        LocalDateTime now = LocalDateTime.now();
        // Kiểm sớm để khỏi phân tích file vô ích; kiểm chính thức (có khóa theo người tải) ở ImportBatchWriter.savePreview.
        if (batches.countByOwnerIdAndStatusAndExpiresAtAfter(ownerId, ImportBatch.STATUS_PREVIEW, now)
                >= props.getMaxPendingBatches()) {
            throw pendingLimit(props.getMaxPendingBatches());
        }
        Path dir;
        try {
            dir = Files.createTempDirectory("bank-import-");
        } catch (IOException e) {
            throw new ApiErrorException(HttpStatus.UNPROCESSABLE_CONTENT, "EXTRACTION_FAILED",
                    "Không tạo được thư mục tạm để đọc file Word.");
        }
        try {
            Path file = inputGuard.writeCheckedDocx(content, dir, props.getMaxFileBytes());
            String sha = sha256(content);
            LessonName.Parsed ln = LessonName.parse(fileName, sha.substring(0, 16));
            int grade = resolveGrade(ln, gradeLevel);
            String resolvedTopic = resolveTopic(ln, topic);
            String displayName = displayName(ln, resolvedTopic);
            ParsedDoc doc = parseLimited(file, ln);
            if (doc.questions().isEmpty()) {
                throw new ApiErrorException(HttpStatus.UNPROCESSABLE_CONTENT, "IMPORT_NO_QUESTIONS",
                        "Không tìm thấy câu hỏi nào. File cần có các phần \"PHẦN I/II/III\" và đầu câu \"Câu N.\" hoặc "
                                + "nhãn mức như [NB].");
            }
            if (doc.questions().size() > props.getMaxQuestions()) {
                throw new ApiErrorException(HttpStatus.UNPROCESSABLE_CONTENT, "IMPORT_TOO_MANY_QUESTIONS",
                        "File có " + doc.questions().size() + " câu, quá giới hạn " + props.getMaxQuestions()
                                + " câu mỗi lần nhập. Hãy tách thành nhiều file.");
            }
            Integer subjectId = catalogService.getOrCreateSubject(SUBJECT_NAME).getId();
            PreviewData preview = ImportPreviewBuilder.build(doc, dedup.bankCandidates(subjectId, ownerId));

            ImportBatch b = new ImportBatch();
            b.setOwnerId(ownerId);
            b.setDisplayName(displayName);
            b.setFileSha256(sha);
            b.setSubjectId(subjectId);
            b.setGradeLevel(grade);
            b.setTopic(resolvedTopic);
            b.setStatus(ImportBatch.STATUS_PREVIEW);
            b.setQuestionCount(doc.questions().size());
            b.setSkippedCounts(JSON.writeValueAsString(skippedCounts(preview.skipped())));
            b.setPreview(JSON.writeValueAsString(preview));
            LocalDateTime createdAt = LocalDateTime.now();
            b.setCreatedAt(createdAt);
            b.setExpiresAt(createdAt.plus(props.getPreviewTtl()));
            ImportBatch saved = batchWriter.savePreview(b);
            LOG.info("Nhập từ Word: lô {} (user {}) đọc {} câu, xem trước {} câu ({} có cờ chặn), bỏ {} câu",
                    saved.getId(), ownerId, doc.questions().size(), preview.items().size(),
                    preview.items().stream().filter(ImportItem::blocking).count(), preview.skipped().size());
            return toDto(saved, preview);
        } finally {
            deleteQuietly(dir);
        }
    }

    private ParsedDoc parseLimited(Path file, LessonName.Parsed ln) {
        boolean acquired;
        try {
            acquired = limiter.tryAcquire(props.getQueueWait());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw DocxInputGuard.busy();
        }
        if (!acquired) {
            throw DocxInputGuard.busy();
        }
        try {
            DocxReader.Result pkg = DocxReader.read(file, new DocxReader.Limits(props.getMaxXmlBytes(),
                    props.getMaxEmbeddingBytes(), props.getMaxEmbeddingTotalBytes()));
            return DocParser.parse(pkg, ln);
        } catch (IOException e) {
            throw DocxInputGuard.invalid("Không đọc được cấu trúc của file Word (file hỏng, quá lớn hoặc có nội dung "
                    + "không hợp lệ).");
        } finally {
            limiter.release();
        }
    }

    private static int resolveGrade(LessonName.Parsed ln, Integer gradeLevel) {
        Integer grade = gradeLevel != null ? gradeLevel : ln.source().grade();
        if (grade == null) {
            throw lessonUnknown();
        }
        if (grade < 6 || grade > 9) {
            throw new ApiErrorException(HttpStatus.UNPROCESSABLE_CONTENT, "IMPORT_GRADE_INVALID",
                    "Khối " + grade + " nằm ngoài khối 6 đến 9 của hệ thống.");
        }
        return grade;
    }

    private static String resolveTopic(LessonName.Parsed ln, String topic) {
        String t = topic != null && !topic.isBlank() ? topic.strip() : ln.source().lessonTitle();
        if (t == null || t.isBlank()) {
            throw lessonUnknown();
        }
        if (t.length() > MAX_TOPIC) {
            throw new ApiErrorException(HttpStatus.BAD_REQUEST, "IMPORT_TOPIC_INVALID",
                    "Chủ đề dài tối đa " + MAX_TOPIC + " ký tự.");
        }
        return t;
    }

    private static ApiErrorException lessonUnknown() {
        return new ApiErrorException(HttpStatus.UNPROCESSABLE_CONTENT, "IMPORT_LESSON_UNKNOWN",
                "Không nhận ra khối và bài từ tên file. Hãy đặt tên file theo mẫu \"KHỐI 8 - CHƯƠNG 1 - BÀI 2 - Tên bài\" "
                        + "hoặc nhập khối và chủ đề.");
    }

    /** Tên hiển thị đã làm sạch; tên file lạ thì dựng từ chủ đề (không bao giờ dùng nguyên tên file). */
    private static String displayName(LessonName.Parsed ln, String topic) {
        String name = LessonName.displayName(ln);
        if (name == null) {
            name = "Bộ câu hỏi - " + topic;
        }
        return name.length() > 255 ? name.substring(0, 255).strip() : name;
    }

    private static Map<String, Integer> skippedCounts(List<SkippedItem> skipped) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (SkippedItem s : skipped) {
            out.merge(s.reason(), 1, Integer::sum);
        }
        return out;
    }

    // ---------- xem lại, hủy ----------

    @Transactional(readOnly = true)
    public BatchDto get(Integer id, Integer ownerId) {
        ImportBatch b = owned(id, ownerId);
        if (isExpired(b)) {
            throw expired();
        }
        return toDto(b, ImportBatch.STATUS_PREVIEW.equals(b.getStatus()) ? readPreview(b) : null);
    }

    /** Hủy lô chưa xác nhận (xóa hẳn dòng và bản xem trước). Lô đã xác nhận thì 409. */
    @Transactional
    public void delete(Integer id, Integer ownerId) {
        ImportBatch b = batches.lockByIdAndOwnerId(id, ownerId).orElseThrow(BankImportService::notFound);
        if (ImportBatch.STATUS_CONFIRMED.equals(b.getStatus())) {
            throw notPreview();
        }
        batches.delete(b);
    }

    // ---------- xác nhận ----------

    /**
     * Đưa các câu {@code keys} của lô vào ngân hàng riêng tư của người tải, trong một transaction (lỗi giữa chừng thì không
     * ghi gì và lô vẫn ở preview). Câu có cờ chặn không chọn được (422 IMPORT_ITEM_BLOCKED).
     */
    @Transactional
    public ConfirmResult confirm(Integer id, Integer ownerId, List<String> keys) {
        try (ImportLogGuard.Scope ignored = ImportLogGuard.open(id)) {
            return doConfirm(id, ownerId, keys);
        } catch (DataIntegrityViolationException | org.hibernate.exception.ConstraintViolationException e) {
            ApiErrorException safe = ImportDbErrors.translate(e, "xác nhận", id);
            if (safe == null) {
                throw e;
            }
            throw safe;
        }
    }

    private ConfirmResult doConfirm(Integer id, Integer ownerId, List<String> keys) {
        ImportBatch b = batches.lockByIdAndOwnerId(id, ownerId).orElseThrow(BankImportService::notFound);
        if (isExpired(b)) {
            throw expired();
        }
        if (!ImportBatch.STATUS_PREVIEW.equals(b.getStatus())) {
            throw notPreview();
        }
        PreviewData preview = readPreview(b);
        List<ImportItem> chosen = chosen(preview, keys);

        QuestionTemplate tpl = catalogService.createTemplate(new CreateTemplateRequest(b.getSubjectId(),
                b.getGradeLevel(), b.getTopic(), null, "Nhập từ Word: " + b.getDisplayName(), SourceType.TEXT));
        Map<QuestionType, ExperimentRun> runs = new EnumMap<>(QuestionType.class);
        Map<QuestionType, Integer> counts = new EnumMap<>(QuestionType.class);
        for (ImportItem i : chosen) {
            counts.merge(type(i), 1, Integer::sum);
        }
        counts.forEach((type, n) -> runs.put(type, generationService.createImportRun(b.getSubjectId(), type, n)));

        LocalDateTime now = LocalDateTime.now();
        List<Integer> bankItemIds = new ArrayList<>();
        for (ImportItem i : chosen) {
            bankItemIds.add(writeItem(i, b, tpl.getId(), runs.get(type(i)).getId(), ownerId, now));
        }
        b.setStatus(ImportBatch.STATUS_CONFIRMED);
        b.setConfirmedAt(now);
        b.setImportedCount(bankItemIds.size());
        b.setPreview(null);
        // flush ngay để mọi lỗi ràng buộc nổ ra trong phạm vi chặn log và được dịch (không chờ tới lúc commit)
        batches.saveAndFlush(b);
        LOG.info("Nhập từ Word: lô {} (user {}) đã xác nhận {} câu vào ngân hàng riêng tư", b.getId(), ownerId,
                bankItemIds.size());
        return new ConfirmResult(b.getId(), bankItemIds.size(), List.copyOf(bankItemIds));
    }

    /** Câu được chọn theo thứ tự trong file; kiểm mã câu (400) và cờ chặn (422). */
    private static List<ImportItem> chosen(PreviewData preview, List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            throw keysInvalid("Chưa chọn câu nào để đưa vào ngân hàng.");
        }
        Set<String> wanted = new HashSet<>();
        for (String k : keys) {
            if (k == null || !wanted.add(k)) {
                throw keysInvalid("Danh sách câu có mã trống hoặc lặp lại.");
            }
        }
        Map<String, ImportItem> byKey = new LinkedHashMap<>();
        preview.items().forEach(i -> byKey.put(i.key(), i));
        List<String> unknown = wanted.stream().filter(k -> !byKey.containsKey(k)).sorted().toList();
        if (!unknown.isEmpty()) {
            throw keysInvalid("Không có câu " + String.join(", ", unknown.subList(0,
                    Math.min(unknown.size(), MAX_BLOCKED_KEYS_IN_MESSAGE))) + " trong lần nhập này.");
        }
        List<ImportItem> out = byKey.values().stream().filter(i -> wanted.contains(i.key())).toList();
        List<String> blocked = out.stream().filter(ImportItem::blocking).map(ImportItem::key).toList();
        if (!blocked.isEmpty()) {
            throw new ApiErrorException(HttpStatus.UNPROCESSABLE_CONTENT, "IMPORT_ITEM_BLOCKED",
                    "Các câu sau còn lỗi cần sửa trong file Word nên chưa đưa vào ngân hàng được: "
                            + String.join(", ", blocked.subList(0, Math.min(blocked.size(), MAX_BLOCKED_KEYS_IN_MESSAGE)))
                            + (blocked.size() > MAX_BLOCKED_KEYS_IN_MESSAGE ? "…" : "") + ".");
        }
        return out;
    }

    /** Ghi một câu: generated_question (+ ý tf), teacher_review assisted (+ ý tf), bank_item riêng tư. Trả id bank_item. */
    private Integer writeItem(ImportItem i, ImportBatch b, Integer templateId, Integer runId, Integer ownerId,
                              LocalDateTime now) {
        QuestionType type = type(i);
        Difficulty level = DbEnum.fromDbValue(Difficulty.class, i.level());
        GeneratedQuestion gq = new GeneratedQuestion();
        gq.setTemplateId(templateId);
        gq.setRunId(runId);
        gq.setQuestionType(type);
        gq.setQuestionText(i.stem());
        gq.setExplanation(i.explanation());
        gq.setRawLlmOutput("");
        gq.setLlmDifficulty(null);
        gq.setFinalDifficulty(type == QuestionType.TF ? null : level);
        gq.setParseSuccess(Boolean.TRUE);
        gq.setAttemptCount(1);
        gq.setStatus(QuestionStatus.IN_BANK);
        gq.setCreatedAt(now);
        gq.setOrigin(QuestionOrigin.IMPORT);
        gq.setImportBatchId(b.getId());
        Option correct = null;
        if (type == QuestionType.MCQ) {
            List<ItemOption> o = i.options();
            gq.setOptionA(o.get(0).text());
            gq.setOptionB(o.get(1).text());
            gq.setOptionC(o.get(2).text());
            gq.setOptionD(o.get(3).text());
            correct = DbEnum.fromDbValue(Option.class, i.correctOption());
            gq.setCorrectOption(correct);
        } else if (type == QuestionType.SA) {
            gq.setSaAnswer(i.saAnswer());
        }
        gq = questions.saveAndFlush(gq);

        List<BankStatementInput> bankStatements = new ArrayList<>();
        if (type == QuestionType.TF) {
            for (ItemStatement s : i.statements()) {
                StatementKey key = DbEnum.fromDbValue(StatementKey.class, s.key());
                TruthValue label = Boolean.TRUE.equals(s.label()) ? TruthValue.TRUE : TruthValue.FALSE;
                GeneratedQuestionStatement row = new GeneratedQuestionStatement(gq.getId(), key);
                row.setStatementText(s.text());
                row.setLlmLabel(label);
                row.setLlmDifficulty(null);
                row.setFinalDifficulty(level);
                questionStatements.save(row);
                bankStatements.add(new BankStatementInput(key, s.text(), label, level));
            }
            questionStatements.flush();
        }

        TeacherReview review = new TeacherReview();
        review.setQuestionType(type);
        review.setQuestionId(gq.getId());
        review.setReviewerId(ownerId);
        review.setReviewPurpose(Purpose.PRODUCTION);
        review.setReviewMode(TeacherReview.MODE_ASSISTED);
        review.setFormatOk(Boolean.TRUE);
        review.setDecision(ReviewDecision.APPROVED_AS_IS);
        review.setEdited(false);
        review.setReviewedAt(now);
        switch (type) {
            case MCQ -> {
                review.setReviewerAnswer(DbEnum.fromDbValue(AnswerChoice.class, correct.getDbValue()));
                review.setAnswerCorrect(Boolean.TRUE);
                review.setDifficultyLabel(level);
                review.setFinalOption(correct);
            }
            case SA -> {
                review.setReviewerSaKind(SaAnswerKind.NUMBER);
                review.setReviewerSaAnswer(i.saAnswer());
                review.setFinalSaAnswer(i.saAnswer());
                review.setAnswerCorrect(Boolean.TRUE);
                review.setDifficultyLabel(level);
            }
            case TF -> {
                review.setAnswerCorrect(null);
                review.setDifficultyLabel(null);
            }
        }
        TeacherReview saved = reviews.saveAndFlush(review);
        if (type == QuestionType.TF) {
            for (BankStatementInput s : bankStatements) {
                TeacherReviewStatement row = new TeacherReviewStatement(saved.getId(), s.key());
                row.setReviewerLabel(s.label() == TruthValue.TRUE ? TruthChoice.TRUE : TruthChoice.FALSE);
                row.setDifficultyLabel(level);
                row.setLabelCorrect(Boolean.TRUE);
                row.setFinalLabel(s.label());
                reviewStatements.save(row);
            }
            reviewStatements.flush();
        }

        Integer bankItemId = switch (type) {
            case MCQ -> bankService.createOrUpdateFromReview(gq.getId(), saved.getId(), i.stem(), gq.getOptionA(),
                    gq.getOptionB(), gq.getOptionC(), gq.getOptionD(), correct, i.explanation(), level).getId();
            case SA -> bankService.createOrUpdateShortAnswerFromReview(gq.getId(), saved.getId(), i.stem(),
                    i.saAnswer(), i.explanation(), level).getId();
            case TF -> bankService.createOrUpdateTrueFalseFromReview(gq.getId(), saved.getId(), i.stem(),
                    i.explanation(), bankStatements).getId();
        };
        bankService.restrictToUser(bankItemId, ownerId);
        return bankItemId;
    }

    private static QuestionType type(ImportItem i) {
        return DbEnum.fromDbValue(QuestionType.class, i.type());
    }

    // ---------- hết hạn ----------

    /**
     * Lô chờ quá hạn thành expired và bị xóa nội dung bản xem trước (để không giữ nội dung bộ đề quá thời hạn). Chạy định
     * kỳ; idempotent.
     */
    @Scheduled(initialDelayString = "${app.bank-import.purge-initial-delay-ms:60000}",
            fixedDelayString = "${app.bank-import.purge-interval-ms:3600000}")
    public int purgeExpired() {
        int n = batches.expirePreviews(LocalDateTime.now());
        if (n > 0) {
            LOG.info("Nhập từ Word: {} lô xem trước quá hạn đã bị xóa nội dung", n);
        }
        return n;
    }

    private static boolean isExpired(ImportBatch b) {
        return ImportBatch.STATUS_EXPIRED.equals(b.getStatus())
                || (ImportBatch.STATUS_PREVIEW.equals(b.getStatus()) && !b.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    // ---------- tiện ích ----------

    private ImportBatch owned(Integer id, Integer ownerId) {
        return batches.findByIdAndOwnerId(id, ownerId).orElseThrow(BankImportService::notFound);
    }

    private static PreviewData readPreview(ImportBatch b) {
        return JSON.readValue(b.getPreview(), PreviewData.class);
    }

    private static BatchDto toDto(ImportBatch b, PreviewData preview) {
        Map<String, Integer> skipped = JSON.readValue(b.getSkippedCounts(), new TypeReference<LinkedHashMap<String, Integer>>() {});
        List<ImportItem> items = preview == null ? List.of() : preview.items();
        int blocked = (int) items.stream().filter(ImportItem::blocking).count();
        return new BatchDto(b.getId(), b.getDisplayName(), b.getStatus(), b.getSubjectId(), b.getGradeLevel(),
                b.getTopic(), b.getCreatedAt(), b.getExpiresAt(), b.getConfirmedAt(), b.getQuestionCount(),
                items.size() - blocked, blocked, b.getImportedCount(), skipped,
                preview == null ? List.of() : preview.skipped(), items);
    }

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
                        "Tải file quá nhiều lần (tối đa " + props.getMaxUploadsPerHour() + " lần mỗi giờ). Hãy thử lại sau.");
            }
            times.addLast(now);
        }
    }

    /** Chỉ cho test: xóa bộ đếm số lần tải lên (bộ đếm nằm trong bộ nhớ, dùng chung giữa các test cùng context). */
    public void resetRateLimitForTests() {
        uploadTimes.clear();
    }

    static ApiErrorException pendingLimit(int max) {
        return new ApiErrorException(HttpStatus.CONFLICT, "IMPORT_PENDING_LIMIT", "Thầy/cô đang có " + max
                + " lần nhập chờ xác nhận. Hãy xác nhận hoặc hủy bớt rồi tải tiếp.");
    }

    private static ApiErrorException notFound() {
        return new ApiErrorException(HttpStatus.NOT_FOUND, "IMPORT_NOT_FOUND", "Không tìm thấy lần nhập này.");
    }

    private static ApiErrorException notPreview() {
        return new ApiErrorException(HttpStatus.CONFLICT, "IMPORT_NOT_PREVIEW",
                "Lần nhập này đã được xác nhận, không đổi được nữa.");
    }

    private static ApiErrorException expired() {
        return new ApiErrorException(HttpStatus.GONE, "IMPORT_EXPIRED",
                "Bản xem trước đã hết hạn và bị xóa. Hãy tải lại file.");
    }

    private static ApiErrorException keysInvalid(String message) {
        return new ApiErrorException(HttpStatus.BAD_REQUEST, "IMPORT_KEYS_INVALID", message);
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteQuietly(Path dir) {
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // để hệ điều hành dọn thư mục tạm
                }
            });
        } catch (IOException ignored) {
            // thư mục đã bị xóa hoặc không đọc được
        }
    }
}
