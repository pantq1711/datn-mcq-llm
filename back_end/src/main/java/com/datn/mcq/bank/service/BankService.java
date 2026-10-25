package com.datn.mcq.bank.service;

import com.datn.mcq.bank.dto.BankItemDto;
import com.datn.mcq.bank.dto.BankSearchDtos.BankItemDetailDto;
import com.datn.mcq.bank.dto.BankSearchDtos.BankListItemDto;
import com.datn.mcq.bank.dto.BankSearchDtos.BankPageDto;
import com.datn.mcq.bank.dto.BankSearchDtos.OriginDto;
import com.datn.mcq.bank.dto.BankSearchDtos.ProvenanceDto;
import com.datn.mcq.bank.dto.BankSearchDtos.TopicDto;
import com.datn.mcq.bank.dto.BankSearchDtos.UsageDto;
import com.datn.mcq.bank.dto.BankSearchDtos.VersionDto;
import com.datn.mcq.bank.dto.BankStatementInput;
import com.datn.mcq.bank.entity.BankItem;
import com.datn.mcq.bank.entity.BankItemStatement;
import com.datn.mcq.bank.repository.BankItemRepository;
import com.datn.mcq.bank.repository.BankItemStatementRepository;
import com.datn.mcq.bank.repository.BankSearchRepository;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.Option;
import com.datn.mcq.enums.QuestionType;
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

    public BankService(BankItemRepository bankItemRepository,
                       BankItemStatementRepository bankItemStatementRepository,
                       BankSearchRepository searchRepository) {
        this.searchRepository = searchRepository;
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
     * @param origin {@code ai} (mặc định) hoặc {@code import} (để dành cho nhập từ Word, hiện không có câu nào)
     * @param q      tìm trong đề, phương án, đáp số và các ý (không phân biệt hoa thường, tối đa 100 ký tự)
     * @param usage  {@code used} hoặc {@code unused}
     * @param sort   {@code newest} (mặc định), {@code oldest}, {@code least_used}, {@code recently_used}
     */
    @Transactional(readOnly = true)
    public BankPageDto search(Integer subjectId, Integer gradeLevel, QuestionType questionType, String topic,
                              String content, Difficulty difficulty, String levels, String origin, String q,
                              String usage, String sort, int page, int size) {
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
                blankToNull(topic), blankToNull(content), difficulty, lv, org, text == null ? null : text.strip(), use);
        long total = searchRepository.count(filter);
        List<BankSearchRepository.Row> rows = searchRepository.search(filter, order, page * size, size);
        Map<Integer, List<BankItemStatement>> statements = statementsOf(rows);
        List<BankListItemDto> items = rows.stream()
                .map(r -> listItem(r, statements.getOrDefault(r.id(), List.of()))).toList();
        return new BankPageDto(items, page, size, total, (int) ((total + size - 1) / size));
    }

    /**
     * W1-3: chi tiết một câu ngân hàng: nội dung, đáp án, lời giải, nguồn gốc (câu LLM gốc, các bản sửa, người duyệt)
     * và mức dùng; {@code myExams} chỉ gồm đề của {@code actorId}.
     */
    @Transactional(readOnly = true)
    public BankItemDetailDto detail(Integer id, Integer actorId) {
        BankSearchRepository.Row row = searchRepository.findRow(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy câu ngân hàng id=" + id));
        BankListItemDto item = listItem(row, row.questionType() == QuestionType.TF
                ? bankItemStatementRepository.findByBankItemIdInOrderByBankItemIdAscPositionAsc(List.of(id))
                : List.of());
        List<VersionDto> versions = searchRepository.versions(row.sourceQuestionId());
        Integer llm = versions.isEmpty() ? row.sourceQuestionId() : versions.get(0).questionId();
        Optional<String[]> run = searchRepository.runOf(llm);
        ProvenanceDto provenance = new ProvenanceDto(llm, run.map(r -> Integer.valueOf(r[0])).orElse(null),
                run.map(r -> r[1]).orElse(null), run.map(r -> r[2]).orElse(null), versions,
                searchRepository.review(row.reviewId()).orElse(null));
        UsageDto usage = new UsageDto(row.usageCount(), row.lastUsedAt(),
                searchRepository.examsOf(id, actorId, MAX_MY_EXAMS));
        return BankItemDetailDto.of(item, provenance, usage);
    }

    /** W1-3: chủ đề có câu trong ngân hàng kèm số câu, theo môn và khối (để dựng ô chọn bộ lọc). */
    @Transactional(readOnly = true)
    public List<TopicDto> topics(Integer subjectId) {
        return searchRepository.topics(subjectId);
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
                new OriginDto("ai", r.reviewMode(), r.edited()), r.usageCount(), r.lastUsedAt());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    /** Một câu ngân hàng kèm ý (tf); dùng cho xem đề ở chế độ giáo viên (V7). */
    @Transactional(readOnly = true)
    public BankItemDto getItem(Integer id) {
        BankItem item = bankItemRepository.findById(id)
                .orElseThrow(() -> new java.util.NoSuchElementException("Không tìm thấy câu ngân hàng id=" + id));
        List<BankItemStatement> statements = item.getQuestionType() == QuestionType.TF
                ? bankItemStatementRepository.findByBankItemIdInOrderByBankItemIdAscPositionAsc(List.of(id))
                : List.of();
        return BankItemDto.fromEntity(item, statements);
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
     * Chép câu Trả lời ngắn vào ngân hàng (V4, GĐ2): không có phương án và correct_option; sa_answer là đáp số cuối
     * giáo viên chốt (đã kiểm ở chế độ STRICT). Duyệt lại thì cập nhật dòng cũ.
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

