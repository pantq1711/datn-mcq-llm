package com.datn.mcq.bank.service;

import com.datn.mcq.bank.dto.BankImageDtos;
import com.datn.mcq.bank.dto.BankItemDto;
import com.datn.mcq.bank.dto.BankSearchDtos;
import com.datn.mcq.bank.dto.BankSearchDtos.BankItemDetailDto;
import com.datn.mcq.bank.dto.BankSearchDtos.BankListItemDto;
import com.datn.mcq.bank.dto.BankSearchDtos.BankPageDto;
import com.datn.mcq.bank.dto.BankSearchDtos.OriginDto;
import com.datn.mcq.bank.dto.BankSearchDtos.ProvenanceDto;
import com.datn.mcq.bank.dto.BankSearchDtos.TopicDto;
import com.datn.mcq.bank.dto.BankSearchDtos.UsageDto;
import com.datn.mcq.bank.dto.BankSearchDtos.ReviewDto;
import com.datn.mcq.bank.dto.BankSearchDtos.UserRef;
import com.datn.mcq.bank.dto.BankSearchDtos.VersionDto;
import com.datn.mcq.bank.dto.BankStatementInput;
import com.datn.mcq.bank.entity.BankItem;
import com.datn.mcq.bank.entity.BankItemStatement;
import com.datn.mcq.bank.repository.BankItemRepository;
import com.datn.mcq.bank.repository.BankItemStatementRepository;
import com.datn.mcq.bank.repository.BankSearchRepository;
import com.datn.mcq.common.exception.ApiErrorException;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionOrigin;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.questionimage.dto.QuestionImageDto;
import com.datn.mcq.questionimage.service.QuestionImageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class BankService {

    private final BankItemRepository bankItemRepository;
    private final BankItemStatementRepository bankItemStatementRepository;
    private final BankSearchRepository searchRepository;
    private final QuestionImageService questionImageService;

    public BankService(BankItemRepository bankItemRepository,
                       BankItemStatementRepository bankItemStatementRepository,
                       BankSearchRepository searchRepository,
                       QuestionImageService questionImageService) {
        this.searchRepository = searchRepository;
        this.questionImageService = questionImageService;
        this.bankItemRepository = bankItemRepository;
        this.bankItemStatementRepository = bankItemStatementRepository;
    }

    public static final int MAX_PAGE_SIZE = 50;
    public static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_QUERY_LENGTH = 100;
    static final int MAX_MY_EXAMS = 50;
    private static final Pattern LEVELS = Pattern.compile("^(easy|medium|hard)(,(easy|medium|hard)){3}$");

    /**
     * W1-3 (D1): ngân hàng có lọc, phân trang và sắp xếp cho GET /api/bank-items; thay danh sách không phân trang.
     * Tham số null hoặc rỗng là không lọc; giá trị sai thì IllegalArgumentException (400).
     *
     * @param levels (chỉ tf) đúng chuỗi 4 mức a-d, ví dụ {@code easy,easy,medium,hard}
     * @param origin không truyền là mọi nguồn; {@code ai} (câu AI đã duyệt) hoặc {@code import} (câu nhập từ Word, V26)
     * @param q      tìm trong đề, phương án, đáp số và các ý (không phân biệt hoa thường, tối đa 100 ký tự)
     * @param usage  {@code used} hoặc {@code unused}
     * @param sort   {@code newest} (mặc định), {@code oldest}, {@code least_used}, {@code recently_used}
     * @param viewerId người xem: câu riêng tư (sinh từ tài liệu của một giáo viên, V19) chỉ hiện với chính người đó
     */
    @Transactional(readOnly = true)
    public BankPageDto search(Integer subjectId, Integer gradeLevel, QuestionType questionType, String topic,
                              String content, Difficulty difficulty, String levels, String origin, String q,
                              String usage, String sort, int page, int size, Integer viewerId) {
        if (page < 0) {
            throw new IllegalArgumentException("page phải từ 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size phải từ 1 đến " + MAX_PAGE_SIZE);
        }
        if (gradeLevel != null && (gradeLevel < 6 || gradeLevel > 9)) {
            throw new IllegalArgumentException("gradeLevel phải từ 6 đến 9");
        }
        String lv = blankToNull(levels);
        if (lv != null) {
            if (!LEVELS.matcher(lv).matches()) {
                throw new IllegalArgumentException("levels phải gồm đúng 4 mức easy, medium, hard cách nhau bởi dấu "
                        + "phẩy, ví dụ easy,easy,medium,hard");
            }
            if (questionType != null && questionType != QuestionType.TF) {
                throw new IllegalArgumentException("levels chỉ áp dụng cho câu tf");
            }
        }
        String org = blankToNull(origin);
        if (org != null && !org.equals("ai") && !org.equals("import")) {
            throw new IllegalArgumentException("origin phải là ai hoặc import");
        }
        String text = blankToNull(q);
        if (text != null && text.strip().length() > MAX_QUERY_LENGTH) {
            throw new IllegalArgumentException("q dài tối đa " + MAX_QUERY_LENGTH + " ký tự");
        }
        String use = blankToNull(usage);
        if (use != null && !use.equals("used") && !use.equals("unused")) {
            throw new IllegalArgumentException("usage phải là used hoặc unused");
        }
        BankSearchRepository.Sort order = switch (sort == null || sort.isBlank() ? "newest" : sort.strip()) {
            case "newest" -> BankSearchRepository.Sort.NEWEST;
            case "oldest" -> BankSearchRepository.Sort.OLDEST;
            case "least_used" -> BankSearchRepository.Sort.LEAST_USED;
            case "recently_used" -> BankSearchRepository.Sort.RECENTLY_USED;
            default -> throw new IllegalArgumentException(
                    "sort phải là newest, oldest, least_used hoặc recently_used");
        };
        BankSearchRepository.Filter filter = new BankSearchRepository.Filter(subjectId, gradeLevel, questionType,
                blankToNull(topic), blankToNull(content), difficulty, lv, org, text == null ? null : text.strip(), use,
                viewerId);
        long total = searchRepository.count(filter);
        List<BankSearchRepository.Row> rows = searchRepository.search(filter, order, page * size, size);
        Map<Integer, List<BankItemStatement>> statements = statementsOf(rows);
        Map<Integer, List<QuestionImageDto>> images = imagesOf(rows, viewerId);
        List<BankListItemDto> items = rows.stream()
                .map(r -> listItem(r, statements.getOrDefault(r.id(), List.of()))
                        .withImages(images.get(r.sourceQuestionId()))).toList();
        return new BankPageDto(items, page, size, total, (int) ((total + size - 1) / size));
    }

    /**
     * G0 H3 (O1): ảnh của các câu trong trang, một truy vấn theo lô (không theo số câu), URL ký cho người xem. Các dòng đã
     * được lọc theo quyền xem câu (câu riêng của người khác không có mặt); không biết người xem thì không có URL.
     */
    private Map<Integer, List<QuestionImageDto>> imagesOf(List<BankSearchRepository.Row> rows, Integer viewerId) {
        if (viewerId == null || rows.isEmpty()) {
            return Map.of();
        }
        return questionImageService.signedImagesBySourceQuestion(
                rows.stream().map(BankSearchRepository.Row::sourceQuestionId).toList(), viewerId);
    }

    /**
     * W1-3: chi tiết một câu ngân hàng: nội dung, đáp án, lời giải, nguồn gốc (câu LLM gốc, các bản sửa, người duyệt)
     * và mức dùng; {@code myExams} chỉ gồm đề của {@code actorId}.
     */
    @Transactional(readOnly = true)
    public BankItemDetailDto detail(Integer id, Integer actorId) {
        return detail(id, actorId, false);
    }

    /**
     * W2-4a (Mục 2): Chi tiết một câu ngân hàng. Bảo vệ quyền riêng tư: không trả họ tên giáo viên khác trong
     * versions và review. Chỉ trả tên thật khi chính người xem là người đó, hoặc người xem là admin.
     */
    @Transactional(readOnly = true)
    public BankItemDetailDto detail(Integer id, Integer actorId, boolean isAdmin) {
        BankSearchRepository.Row row = searchRepository.findRow(id, actorId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy câu ngân hàng id=" + id));
        BankListItemDto item = listItem(row, row.questionType() == QuestionType.TF
                ? bankItemStatementRepository.findByBankItemIdInOrderByBankItemIdAscPositionAsc(List.of(id))
                : List.of())
                .withImages(imagesOf(List.of(row), actorId).get(row.sourceQuestionId()));
        List<VersionDto> rawVersions = searchRepository.versions(row.sourceQuestionId());
        List<VersionDto> versions = rawVersions.stream()
                .map(v -> new VersionDto(v.questionId(), v.kind(), v.createdAt(),
                        anonymize(v.editedBy(), actorId, isAdmin),
                        v.changedFields(), v.editDistance()))
                .toList();
        Integer llm = versions.isEmpty() ? row.sourceQuestionId() : versions.get(0).questionId();
        Optional<String[]> run = searchRepository.runOf(llm);
        ReviewDto review = searchRepository.review(row.reviewId())
                .map(r -> new ReviewDto(r.reviewId(), r.mode(), r.decision(),
                        anonymize(r.reviewer(), actorId, isAdmin),
                        r.reviewedAt()))
                .orElse(null);
        ProvenanceDto provenance = new ProvenanceDto(llm, run.map(r -> Integer.valueOf(r[0])).orElse(null),
                run.map(r -> r[1]).orElse(null), run.map(r -> r[2]).orElse(null), versions, review);
        UsageDto usage = new UsageDto(row.usageCount(), row.lastUsedAt(),
                searchRepository.examsOf(id, actorId, MAX_MY_EXAMS));
        return BankItemDetailDto.of(item, provenance, usage, canRemove(row.reviewerId(), actorId, isAdmin));
    }

    /** NV-21: người gỡ được câu: người Giữ câu (teacher_review.reviewer_id, câu nhập là người tải) hoặc admin. */
    private static boolean canRemove(Integer reviewerId, Integer actorId, boolean isAdmin) {
        return isAdmin || (actorId != null && actorId.equals(reviewerId));
    }

    /** Mã 403 khi gỡ câu không phải của mình (NV-21). */
    public static final String BANK_ITEM_NOT_OWNER = "BANK_ITEM_NOT_OWNER";

    /**
     * NV-21 (quyết định 16/11, mục 3b): gỡ câu khỏi ngân hàng, ẩn mềm ({@code removed_at}, {@code removed_by}, V30). Câu đã gỡ
     * không hiện trong ngân hàng và không được chọn cho đề mới; dòng bank_item giữ nguyên nên đề cũ vẫn hiển thị câu. Chỉ người
     * Giữ câu (teacher_review.reviewer_id; với câu nhập từ Word là người tải) hoặc admin được gỡ.
     *
     * <p>404 (NoSuchElementException) khi câu không có, đã gỡ, hoặc là câu riêng của người khác với người không phải admin
     * (như không tồn tại); 403 {@value #BANK_ITEM_NOT_OWNER} khi câu chung không phải của người gọi.
     */
    @Transactional
    public void remove(Integer id, Integer actorId, boolean isAdmin) {
        BankItem item = bankItemRepository.findById(id)
                .filter(i -> i.getRemovedAt() == null)
                .filter(i -> isAdmin || i.getVisibleToUserId() == null || i.getVisibleToUserId().equals(actorId))
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy câu ngân hàng id=" + id));
        Integer reviewerId = searchRepository.review(item.getReviewId())
                .map(r -> r.reviewer() == null ? null : r.reviewer().id()).orElse(null);
        if (!canRemove(reviewerId, actorId, isAdmin)) {
            throw new ApiErrorException(HttpStatus.FORBIDDEN, BANK_ITEM_NOT_OWNER,
                    "Chỉ người đã lưu câu này vào ngân hàng (hoặc quản trị viên) mới gỡ được câu.");
        }
        item.markRemoved(LocalDateTime.now(), actorId);
        bankItemRepository.save(item);
    }

    /**
     * Câu ngân hàng {@code bankItemId} đang dùng chung ({@code shared}) hay riêng của một người ({@code private}); dùng cho
     * kết quả Giữ câu (NV-28).
     */
    @Transactional(readOnly = true)
    public String visibility(Integer bankItemId) {
        BankItem item = bankItemRepository.findById(bankItemId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy câu ngân hàng id=" + bankItemId));
        return item.getVisibleToUserId() == null ? BankSearchDtos.VISIBILITY_SHARED : BankSearchDtos.VISIBILITY_PRIVATE;
    }

    private static UserRef anonymize(UserRef userRef, Integer viewerId, boolean isAdmin) {
        if (userRef == null) {
            return null;
        }
        if (isAdmin || (viewerId != null && viewerId.equals(userRef.id()))) {
            return userRef;
        }
        return new UserRef(null, "Giáo viên khác");
    }

    /** W1-3: chủ đề có câu trong ngân hàng kèm số câu, theo môn và khối (để dựng ô chọn bộ lọc). */
    @Transactional(readOnly = true)
    public List<TopicDto> topics(Integer subjectId, Integer viewerId) {
        return searchRepository.topics(subjectId, viewerId);
    }

    /**
     * W2-3 (quyết định E): câu ngân hàng sinh từ tài liệu của giáo viên chỉ chính họ thấy và dùng lại. Gọi trong transaction
     * của nơi Giữ câu, ngay sau khi câu vào ngân hàng.
     */
    @Transactional
    public void restrictToUser(Integer bankItemId, Integer userId) {
        BankItem item = bankItemRepository.findById(bankItemId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy câu ngân hàng id=" + bankItemId));
        item.setVisibleToUserId(userId);
        bankItemRepository.save(item);
    }

    /** Câu nguồn {@code questionId} (generated_question) đã có trong ngân hàng. */
    @Transactional(readOnly = true)
    public boolean hasBankItemForQuestion(Integer questionId) {
        return bankItemRepository.findBySourceQuestionId(questionId).isPresent();
    }

    private Map<Integer, List<BankItemStatement>> statementsOf(List<BankSearchRepository.Row> rows) {
        List<Integer> tfIds = rows.stream().filter(r -> r.questionType() == QuestionType.TF)
                .map(BankSearchRepository.Row::id).toList();
        return tfIds.isEmpty() ? Map.of()
                : bankItemStatementRepository.findByBankItemIdInOrderByBankItemIdAscPositionAsc(tfIds).stream()
                        .collect(Collectors.groupingBy(BankItemStatement::getBankItemId));
    }

    private static BankListItemDto listItem(BankSearchRepository.Row r, List<BankItemStatement> statements) {
        List<BankItemDto.StatementDto> statementDtos = r.questionType() == QuestionType.TF
                ? statements.stream().map(BankItemDto.StatementDto::fromEntity).toList() : null;
        return new BankListItemDto(r.id(), r.sourceQuestionId(), r.reviewId(), r.questionText(), r.optionA(),
                r.optionB(), r.optionC(), r.optionD(), r.correctOption(), r.explanation(), r.difficulty(),
                r.createdAt(), r.questionType(), r.questionType() == QuestionType.SA ? r.saAnswer() : null,
                statementDtos, r.subjectId(), r.subjectName(), r.gradeLevel(), r.topic(), r.content(),
                new OriginDto(r.origin() == QuestionOrigin.IMPORT ? "import" : "ai", r.reviewMode(), r.edited()),
                r.usageCount(), r.lastUsedAt(), r.visibleToUserId() == null ? BankSearchDtos.VISIBILITY_SHARED
                        : BankSearchDtos.VISIBILITY_PRIVATE, null);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** Một câu ngân hàng kèm ý (tf); dùng cho xem đề ở chế độ giáo viên (V7). */
    @Transactional(readOnly = true)
    public BankItemDto getItem(Integer id) {
        return getItems(List.of(id)).get(id);
    }

    /**
     * F-07: nhiều câu ngân hàng kèm ý (tf) bằng số truy vấn cố định: một cho {@code bank_item}, một cho ý của các câu tf
     * (bỏ qua nếu không có câu tf). Id không có thì NoSuchElementException như {@link #getItem}.
     */
    @Transactional(readOnly = true)
    public java.util.Map<Integer, BankItemDto> getItems(java.util.Collection<Integer> ids) {
        java.util.Set<Integer> distinct = new java.util.LinkedHashSet<>(ids);
        java.util.Map<Integer, BankItemDto> out = new java.util.LinkedHashMap<>();
        if (distinct.isEmpty()) {
            return out;
        }
        java.util.Map<Integer, BankItem> items = new java.util.HashMap<>();
        for (BankItem item : bankItemRepository.findAllById(distinct)) {
            items.put(item.getId(), item);
        }
        for (Integer id : distinct) {
            if (!items.containsKey(id)) {
                throw new java.util.NoSuchElementException("Không tìm thấy câu ngân hàng id=" + id);
            }
        }
        List<Integer> tfIds = items.values().stream().filter(i -> i.getQuestionType() == QuestionType.TF)
                .map(BankItem::getId).toList();
        java.util.Map<Integer, List<BankItemStatement>> statements = new java.util.HashMap<>();
        if (!tfIds.isEmpty()) {
            for (BankItemStatement s : bankItemStatementRepository
                    .findByBankItemIdInOrderByBankItemIdAscPositionAsc(tfIds)) {
                statements.computeIfAbsent(s.getBankItemId(), k -> new java.util.ArrayList<>()).add(s);
            }
        }
        for (Integer id : distinct) {
            out.put(id, BankItemDto.fromEntity(items.get(id), statements.getOrDefault(id, List.of())));
        }
        return out;
    }

    /**
     * Trong {@code sourceQuestionIds} (câu nguồn của câu ngân hàng), những câu có ít nhất một ảnh, mọi role, kể cả câu đã gỡ
     * khỏi ngân hàng. Cửa cho module {@code assignment} (D3 ngày 23/11: không thêm phụ thuộc vào {@code questionimage}).
     */
    @Transactional(readOnly = true)
    public java.util.Set<Integer> sourceQuestionsWithImages(java.util.Collection<Integer> sourceQuestionIds) {
        return questionImageService.sourceQuestionsWithImages(sourceQuestionIds);
    }

    /**
     * S1 (docs/design/cau-co-hinh.md mục 4.1, D3): ảnh của các câu nguồn theo lô (một truy vấn, không nạp byte), URL ký cho
     * lượt làm {@code attemptId} của học sinh. {@code exp}: epoch giây; null là hạn cuốn theo giờ như H3. Trả mọi role, kể cả
     * câu đã gỡ khỏi ngân hàng (bài đã giao vẫn hiện câu đó, quyết định 16/11); nơi gọi tự lọc role {@code solution}.
     */
    @Transactional(readOnly = true)
    public Map<Integer, List<BankImageDtos.SignedImage>> attemptImagesBySourceQuestion(
            java.util.Collection<Integer> sourceQuestionIds, int attemptId, Long exp) {
        Map<Integer, List<BankImageDtos.SignedImage>> out = new java.util.LinkedHashMap<>();
        questionImageService.attemptSignedImagesBySourceQuestion(sourceQuestionIds, attemptId, exp)
                .forEach((questionId, images) -> out.put(questionId, images.stream()
                        .map(i -> new BankImageDtos.SignedImage(i.id(), i.role(), i.ordinal(), i.mime(), i.widthPx(),
                                i.heightPx(), i.displayWidthMm(), i.url()))
                        .toList()));
        return out;
    }

    /** S1: chữ ký URL ảnh của học sinh đúng và chưa hết hạn. Không truy cập DB. */
    public boolean verifyAttemptImageUrl(long imageId, long exp, int attemptId, String sig) {
        return questionImageService.verifyAttemptUrl(imageId, exp, attemptId, sig);
    }

    /**
     * S1: byte ảnh {@code imageId} nếu ảnh thuộc câu nguồn của một trong các câu ngân hàng {@code bankItemIds}; không lọc
     * {@code removed_at} (bài đã giao đọc câu theo id, quyết định 16/11). Rỗng khi ảnh không có hoặc thuộc câu khác.
     */
    @Transactional(readOnly = true)
    public Optional<BankImageDtos.ImageFile> imageOfBankItems(long imageId, java.util.Collection<Integer> bankItemIds) {
        if (bankItemIds == null || bankItemIds.isEmpty()) {
            return Optional.empty();
        }
        List<Integer> sourceQuestionIds = bankItemRepository.findAllById(new java.util.LinkedHashSet<>(bankItemIds))
                .stream().map(BankItem::getSourceQuestionId).toList();
        return questionImageService.imageOfSourceQuestions(imageId, sourceQuestionIds)
                .map(i -> new BankImageDtos.ImageFile(i.getId(), i.getRole(), i.getMime(), i.getData(), i.getSha256()));
    }

    @Transactional
    public BankItem createOrUpdateFromReview(
            Integer sourceQuestionId,
            Integer reviewId,
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            Option correctOption,
            String explanation,
            Difficulty difficulty) {
        BankItem item = bankItemRepository.findBySourceQuestionId(sourceQuestionId)
                .orElseGet(BankItem::new);
        item.setSourceQuestionId(sourceQuestionId);
        item.setReviewId(reviewId);
        item.setQuestionText(questionText);
        item.setOptionA(optionA);
        item.setOptionB(optionB);
        item.setOptionC(optionC);
        item.setOptionD(optionD);
        item.setCorrectOption(correctOption);
        item.setExplanation(explanation);
        item.setDifficulty(difficulty);
        item.setCreatedAt(LocalDateTime.now());
        return bankItemRepository.save(item);
    }

    /**
     * Chép câu Trả lời ngắn vào ngân hàng (V4, GĐ2): không có phương án và correct_option; sa_answer là đáp số của câu
     * được Giữ. Chép lại thì cập nhật dòng cũ.
     */
    @Transactional
    public BankItem createOrUpdateShortAnswerFromReview(
            Integer sourceQuestionId,
            Integer reviewId,
            String questionText,
            String saAnswer,
            String explanation,
            Difficulty difficulty) {
        BankItem item = bankItemRepository.findBySourceQuestionId(sourceQuestionId)
                .orElseGet(BankItem::new);
        item.setQuestionType(QuestionType.SA);
        item.setSourceQuestionId(sourceQuestionId);
        item.setReviewId(reviewId);
        item.setQuestionText(questionText);
        item.setOptionA(null);
        item.setOptionB(null);
        item.setOptionC(null);
        item.setOptionD(null);
        item.setCorrectOption(null);
        item.setSaAnswer(saAnswer);
        item.setExplanation(explanation);
        item.setDifficulty(difficulty);
        item.setCreatedAt(LocalDateTime.now());
        return bankItemRepository.save(item);
    }

    /**
     * Chép câu Đúng/Sai vào ngân hàng (GĐ3): bank_item (không có phương án, đáp số và mức độ cấp câu) cùng 4 dòng
     * bank_item_statement là bản cuối giáo viên chốt (nội dung, nhãn cuối, mức độ của từng ý). Duyệt lại thì cập nhật
     * dòng cũ và thay 4 ý. Chạy trong transaction của nơi gọi nên rollback cùng teacher_review.
     */
    @Transactional
    public BankItem createOrUpdateTrueFalseFromReview(
            Integer sourceQuestionId,
            Integer reviewId,
            String questionText,
            String explanation,
            List<BankStatementInput> statements) {
        if (statements == null || statements.size() != 4) {
            throw new IllegalArgumentException("Câu đúng/sai vào ngân hàng phải có đủ 4 ý");
        }
        BankItem item = bankItemRepository.findBySourceQuestionId(sourceQuestionId)
                .orElseGet(BankItem::new);
        item.setQuestionType(QuestionType.TF);
        item.setSourceQuestionId(sourceQuestionId);
        item.setReviewId(reviewId);
        item.setQuestionText(questionText);
        item.setOptionA(null);
        item.setOptionB(null);
        item.setOptionC(null);
        item.setOptionD(null);
        item.setCorrectOption(null);
        item.setSaAnswer(null);
        item.setExplanation(explanation);
        item.setDifficulty(null);
        item.setCreatedAt(LocalDateTime.now());
        BankItem saved = bankItemRepository.saveAndFlush(item);

        bankItemStatementRepository.deleteByBankItemId(saved.getId());
        for (BankStatementInput s : statements) {
            BankItemStatement row = new BankItemStatement(saved.getId(), s.key());
            row.setStatementText(s.text());
            row.setLabel(s.label());
            row.setDifficulty(s.difficulty());
            bankItemStatementRepository.save(row);
        }
        bankItemStatementRepository.flush();
        return saved;
    }
}

