package com.datn.mcq.matrix.service;

import com.datn.mcq.catalog.dto.CreateTemplateRequest;
import com.datn.mcq.catalog.entity.QuestionTemplate;
import com.datn.mcq.catalog.entity.Subject;
import com.datn.mcq.catalog.service.CatalogService;
import com.datn.mcq.common.exception.ApiErrorException;
import com.datn.mcq.common.util.JsonStringArray;
import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.enums.DbEnum;
import com.datn.mcq.enums.Difficulty;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.SourceType;
import com.datn.mcq.matrix.dto.MatrixDtos.CellDto;
import com.datn.mcq.matrix.dto.MatrixDtos.CellInput;
import com.datn.mcq.matrix.dto.MatrixDtos.CreateFromSampleRequest;
import com.datn.mcq.matrix.dto.MatrixDtos.CreateLessonRequest;
import com.datn.mcq.matrix.dto.MatrixDtos.LessonCellInput;
import com.datn.mcq.matrix.dto.MatrixDtos.CreateMatrixRequest;
import com.datn.mcq.matrix.dto.MatrixDtos.MatrixDto;
import com.datn.mcq.matrix.dto.MatrixDtos.MatrixSummaryDto;
import com.datn.mcq.matrix.dto.MatrixDtos.SampleDto;
import com.datn.mcq.matrix.dto.MatrixDtos.TotalsDto;
import com.datn.mcq.matrix.entity.ExamMatrix;
import com.datn.mcq.matrix.entity.ExamMatrixCell;
import com.datn.mcq.matrix.repository.ExamMatrixCellRepository;
import com.datn.mcq.matrix.repository.ExamMatrixRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Ma trận đề (V6, phần C mục 1): tạo qua API JSON hoặc từ mẫu có sẵn; mỗi ô tạo một question_template (dùng lại pipeline
 * sinh câu, phạm vi dedup và danh sách "câu đã sinh" theo template). Cấu trúc đề: phần I mcq, II tf, III sa.
 */
@Service
public class MatrixService {

    /** Mẫu có sẵn: khóa → file trong classpath (bản chép của docs/sample, có test so khớp). */
    static final Map<String, String> SAMPLES = Map.of("giuaki1-toan8", "/samples/matrix_giuaki1_toan8.json");
    static final String DEFAULT_SUBJECT = "Toán";
    private static final Map<String, QuestionType> PART_TYPES =
            Map.of("I", QuestionType.MCQ, "II", QuestionType.TF, "III", QuestionType.SA);
    private static final Set<String> LEVEL_KEYS = Set.of("easy", "medium", "hard");
    /** W1-4: tổng số câu tối đa của một lần sinh theo bài (mỗi câu thiếu tốn một job, giãn cách 13 giây). */
    public static final int MAX_LESSON_QUESTIONS = 50;
    /** Câu đúng/sai sinh theo bài không nêu mức: ý a-d lần lượt dễ, dễ, vừa, khó. */
    private static final List<Difficulty> DEFAULT_TF_LEVELS =
            List.of(Difficulty.EASY, Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD);

    private final ExamMatrixRepository matrixRepository;
    private final ExamMatrixCellRepository cellRepository;
    private final CatalogService catalogService;
    private final MatrixUsageLookup usage;

    public MatrixService(ExamMatrixRepository matrixRepository, ExamMatrixCellRepository cellRepository,
                         CatalogService catalogService, MatrixUsageLookup usage) {
        this.matrixRepository = matrixRepository;
        this.cellRepository = cellRepository;
        this.catalogService = catalogService;
        this.usage = usage;
    }

    /** Tạo ma trận; {@code ownerId} là người đăng nhập (W1-1). */
    @Transactional
    public MatrixDto create(CreateMatrixRequest req, Integer ownerId) {
        return create(req, null, ownerId);
    }

    private MatrixDto create(CreateMatrixRequest req, String sampleKey, Integer ownerId) {
        return toDto(persist(req, sampleKey, ExamMatrix.KIND_MATRIX, ownerId));
    }

    /**
     * Chủ đề (bài) của một lần sinh theo bài. Hiện là chuỗi {@code lesson}. Điểm mở rộng: khi có danh mục bài
     * (lesson_catalog), request nhận thêm {@code lessonId} tùy chọn và hàm này lấy tên bài từ danh mục khi có lessonId;
     * các trường hiện có giữ nguyên nghĩa nên API cũ không vỡ.
     */
    private static String resolveLesson(CreateLessonRequest req) {
        if (req.lesson() == null || req.lesson().isBlank()) {
            throw new IllegalArgumentException("lesson (tên bài) không được để trống");
        }
        String lesson = req.lesson().trim();
        if (lesson.length() > 255) {
            throw new IllegalArgumentException("lesson dài tối đa 255 ký tự");
        }
        return lesson;
    }

    /**
     * W1-4: ma trận ẩn của một lần sinh theo bài (kind = 'lesson'): mọi ô cùng chủ đề là bài, phần I/II/III suy từ dạng
     * câu, yêu cầu cần đạt mặc định theo mức (ghi đè được). Không hiện trong danh sách, không xem/sửa/xóa theo id. Gọi
     * trong transaction của nơi tạo đề (lỗi sau đó thì ma trận cũng rollback).
     */
    @Transactional
    public ExamMatrix createLesson(CreateLessonRequest req, Integer ownerId) {
        if (ownerId == null) {
            throw new IllegalArgumentException("Thiếu người tạo ma trận");
        }
        if (req == null) {
            throw new IllegalArgumentException("Thiếu dữ liệu sinh theo bài");
        }
        String lesson = resolveLesson(req);
        String title = req.title() == null || req.title().isBlank() ? lesson : req.title().trim();
        if (title.length() > 255) {
            throw new IllegalArgumentException("title dài tối đa 255 ký tự");
        }
        Map<String, String> overrides = req.learningOutcomes() == null ? Map.of() : req.learningOutcomes();
        if (!LEVEL_KEYS.containsAll(overrides.keySet())) {
            throw new IllegalArgumentException("learningOutcomes chỉ nhận khóa easy, medium, hard");
        }
        List<CellInput> cells = new ArrayList<>();
        int total = 0;
        List<LessonCellInput> in = req.cells() == null ? List.of() : req.cells();
        for (int i = 0; i < in.size(); i++) {
            LessonCellInput c = in.get(i);
            if (c == null) {
                throw new IllegalArgumentException("Ô " + (i + 1) + ": ô trống");
            }
            if (c.questionType() == null) {
                throw new IllegalArgumentException("Ô " + (i + 1) + ": thiếu questionType");
            }
            boolean tf = c.questionType() == QuestionType.TF;
            List<Difficulty> levels = tf && c.statementDifficulties() == null ? DEFAULT_TF_LEVELS
                    : c.statementDifficulties();
            List<Difficulty> used = tf ? levels : c.difficulty() == null ? List.of() : List.of(c.difficulty());
            Map<String, String> outcomes = new LinkedHashMap<>();
            for (Difficulty level : used == null ? List.<Difficulty>of() : used) {
                if (level != null) {
                    String text = overrides.get(level.getDbValue());
                    outcomes.put(level.getDbValue(), text != null && !text.isBlank() ? text.trim()
                            : defaultOutcome(level, lesson));
                }
            }
            cells.add(new CellInput(partOf(c.questionType()), c.questionType(), lesson, req.content(), outcomes,
                    c.difficulty(), tf ? levels : c.statementDifficulties(), c.questionCount()));
            total += c.questionCount() == null ? 0 : c.questionCount();
        }
        CreateMatrixRequest matrixRequest = new CreateMatrixRequest(title, req.subjectId(), req.gradeLevel(), null,
                List.of(), cells);
        validate(matrixRequest);
        if (total > MAX_LESSON_QUESTIONS) {
            throw new IllegalArgumentException("Một lần sinh theo bài tối đa " + MAX_LESSON_QUESTIONS + " câu (hiện "
                    + total + ")");
        }
        return persist(matrixRequest, null, ExamMatrix.KIND_LESSON, ownerId);
    }

    private static String partOf(QuestionType type) {
        return switch (type) {
            case MCQ -> "I";
            case TF -> "II";
            case SA -> "III";
        };
    }

    /** Yêu cầu cần đạt mặc định của một mức khi giáo viên sinh theo bài và không nhập (nhận biết, thông hiểu, vận dụng). */
    private static String defaultOutcome(Difficulty level, String lesson) {
        return switch (level) {
            case EASY -> "Nhận biết được kiến thức cơ bản của bài: " + lesson + ".";
            case MEDIUM -> "Hiểu và vận dụng trực tiếp kiến thức của bài: " + lesson + ".";
            case HARD -> "Vận dụng kiến thức của bài \"" + lesson + "\" để giải quyết bài toán nhiều bước hoặc tình huống mới.";
        };
    }

    private ExamMatrix persist(CreateMatrixRequest req, String sampleKey, String kind, Integer ownerId) {
        if (ownerId == null) {
            throw new IllegalArgumentException("Thiếu người tạo ma trận");
        }
        validate(req);
        Subject subject = catalogService.getSubjectById(req.subjectId());
        ExamMatrix matrix = new ExamMatrix();
        matrix.setKind(kind);
        matrix.setCreatedBy(ownerId);
        matrix.setName(req.name().trim());
        matrix.setSubjectId(subject.getId());
        matrix.setGradeLevel(req.gradeLevel());
        matrix.setSource(blankToNull(req.source()));
        matrix.setNotes(JsonStringArray.write(req.notes()));
        matrix.setSampleKey(sampleKey);
        // Postgres lưu tới micro giây: cắt trước để phản hồi lúc tạo giống phản hồi đọc lại.
        matrix.setCreatedAt(LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        matrix = matrixRepository.save(matrix);
        addCells(matrix, req.cells());
        return matrix;
    }

    /** Tạo các ô của ma trận theo thứ tự (vị trí từ 1), mỗi ô một question_template mới. */
    private void addCells(ExamMatrix matrix, List<CellInput> inputs) {
        int position = 1;
        for (CellInput in : inputs) {
            ExamMatrixCell cell = new ExamMatrixCell();
            cell.setMatrixId(matrix.getId());
            cell.setPosition(position++);
            cell.setPart(in.part().trim());
            cell.setQuestionType(in.questionType());
            cell.setTopic(in.topic().trim());
            cell.setContent(blankToNull(in.content()));
            Map<String, String> outcomes = in.learningOutcomes();
            cell.setLearningOutcomeEasy(blankToNull(outcomes.get("easy")));
            cell.setLearningOutcomeMedium(blankToNull(outcomes.get("medium")));
            cell.setLearningOutcomeHard(blankToNull(outcomes.get("hard")));
            if (in.questionType() == QuestionType.TF) {
                cell.setStatementDifficulties(String.join(",",
                        in.statementDifficulties().stream().map(Difficulty::getDbValue).toList()));
            } else {
                cell.setDifficulty(in.difficulty());
            }
            cell.setQuestionCount(in.questionCount());
            cell.setTemplateId(createTemplate(matrix, cell).getId());
            cellRepository.save(cell);
        }
    }

    // ---------- sửa, xóa, nhân bản (W1-4 bước 3) ----------

    /** Mã 409 khi sửa cấu trúc hoặc xóa ma trận đã có đề. */
    public static final String MATRIX_IN_USE = "MATRIX_IN_USE";

    /**
     * Sửa ma trận của chính người gọi (body như tạo, thay toàn bộ). Chưa có đề nào: thay cả môn, khối và các ô (id ô đổi;
     * template cũ không còn ai tham chiếu bị dọn). Đã có đề: chỉ đổi được tên, nguồn, ghi chú; môn, khối hoặc ô khác bản
     * đang lưu (so sau chuẩn hóa) thì 409 {@value #MATRIX_IN_USE}, vì đề đã tạo đọc ô sống để Đổi/sinh lại. Khóa hàng ma
     * trận trước khi đếm đề, nên tạo đề đang dở (đã ghi đề, chưa commit) làm lần sửa chờ rồi thấy đề đó.
     */
    @Transactional
    public MatrixDto update(Integer id, CreateMatrixRequest req, Integer ownerId) {
        ExamMatrix matrix = lockOwnedForUpdate(id, ownerId);
        validate(req);
        Subject subject = catalogService.getSubjectById(req.subjectId());
        List<ExamMatrixCell> oldCells = cellRepository.findByMatrixIdOrderByPositionAsc(id);
        int exams = usage.examCount(id);
        matrix.setName(req.name().trim());
        matrix.setSource(blankToNull(req.source()));
        matrix.setNotes(JsonStringArray.write(req.notes()));
        if (exams > 0) {
            if (!sameStructure(matrix, oldCells, subject.getId(), req)) {
                throw ApiErrorException.conflict(MATRIX_IN_USE, "Ma trận đã được dùng cho " + exams + " đề nên không "
                        + "sửa được môn, khối và các ô (đề đã tạo dùng lại ô để sinh câu). Tên, nguồn và ghi chú vẫn "
                        + "sửa được; muốn đổi ô hãy nhân bản ma trận hoặc tạo ma trận mới.");
            }
            return toDto(matrixRepository.save(matrix));
        }
        matrix.setSubjectId(subject.getId());
        matrix.setGradeLevel(req.gradeLevel());
        matrix = matrixRepository.save(matrix);
        List<Integer> oldTemplates = oldCells.stream().map(ExamMatrixCell::getTemplateId).toList();
        cellRepository.deleteAll(oldCells);
        cellRepository.flush();
        addCells(matrix, req.cells());
        catalogService.deleteUnusedTemplates(oldTemplates);
        return toDto(matrix);
    }

    /** Khóa ghi hàng ma trận của người gọi (kind = 'matrix') rồi đọc; không có hoặc của người khác: 404. */
    private ExamMatrix lockOwnedForUpdate(Integer id, Integer ownerId) {
        matrixRepository.lockRowForUpdate(id, ownerId, ExamMatrix.KIND_MATRIX)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy ma trận id=" + id));
        return matrixRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy ma trận id=" + id));
    }

    /** Môn, khối và danh sách ô của yêu cầu giống bản đang lưu (cùng cách chuẩn hóa như lúc lưu). */
    private static boolean sameStructure(ExamMatrix matrix, List<ExamMatrixCell> cells, Integer subjectId,
                                         CreateMatrixRequest req) {
        if (!matrix.getSubjectId().equals(subjectId) || !java.util.Objects.equals(matrix.getGradeLevel(),
                req.gradeLevel()) || cells.size() != req.cells().size()) {
            return false;
        }
        for (int i = 0; i < cells.size(); i++) {
            ExamMatrixCell c = cells.get(i);
            CellInput in = req.cells().get(i);
            Map<String, String> outcomes = in.learningOutcomes();
            boolean tf = in.questionType() == QuestionType.TF;
            boolean same = c.getPart().equals(in.part().trim())
                    && c.getQuestionType() == in.questionType()
                    && c.getTopic().equals(in.topic().trim())
                    && java.util.Objects.equals(c.getContent(), blankToNull(in.content()))
                    && java.util.Objects.equals(c.getLearningOutcomeEasy(), blankToNull(outcomes.get("easy")))
                    && java.util.Objects.equals(c.getLearningOutcomeMedium(), blankToNull(outcomes.get("medium")))
                    && java.util.Objects.equals(c.getLearningOutcomeHard(), blankToNull(outcomes.get("hard")))
                    && java.util.Objects.equals(c.getDifficulty(), tf ? null : in.difficulty())
                    && java.util.Objects.equals(c.getStatementDifficulties(), tf ? String.join(",",
                            in.statementDifficulties().stream().map(Difficulty::getDbValue).toList()) : null)
                    && c.getQuestionCount().equals(in.questionCount());
            if (!same) {
                return false;
            }
        }
        return true;
    }

    /**
     * Xóa ma trận của chính người gọi. Có đề thì 409 {@value #MATRIX_IN_USE} (đề giữ khóa ngoại tới ma trận và ô). Không
     * có đề: xóa ô và ma trận, dọn template của các ô nếu không còn câu hay job tham chiếu (câu đã sinh giữ vết nguồn gốc).
     */
    @Transactional
    public void delete(Integer id, Integer ownerId) {
        ExamMatrix matrix = lockOwnedForUpdate(id, ownerId);
        int exams = usage.examCount(id);
        if (exams > 0) {
            throw ApiErrorException.conflict(MATRIX_IN_USE, "Ma trận đã được dùng cho " + exams + " đề nên không xóa "
                    + "được.");
        }
        List<ExamMatrixCell> cells = cellRepository.findByMatrixIdOrderByPositionAsc(id);
        List<Integer> templates = cells.stream().map(ExamMatrixCell::getTemplateId).toList();
        cellRepository.deleteAll(cells);
        cellRepository.flush();
        matrixRepository.delete(matrix);
        matrixRepository.flush();
        catalogService.deleteUnusedTemplates(templates);
    }

    /**
     * Nhân bản ma trận của chính người gọi (kể cả ma trận đã có đề): ma trận mới độc lập, cùng nội dung ô, mỗi ô có
     * template riêng, không giữ khóa mẫu (sampleKey), người sở hữu là người gọi. {@code name} bỏ trống thì
     * "&lt;tên&gt; (bản sao)" (cắt phần tên cho vừa 255 ký tự).
     */
    @Transactional
    public MatrixDto duplicate(Integer id, String name, Integer ownerId) {
        ExamMatrix source = getOwnedMatrix(id, ownerId);
        String copyName;
        if (name == null || name.isBlank()) {
            String suffix = " (bản sao)";
            String base = source.getName();
            copyName = (base.length() + suffix.length() > 255 ? base.substring(0, 255 - suffix.length()) : base)
                    + suffix;
        } else {
            copyName = name.trim();
        }
        List<CellInput> cells = new ArrayList<>();
        for (ExamMatrixCell c : cellRepository.findByMatrixIdOrderByPositionAsc(id)) {
            Map<String, String> outcomes = new LinkedHashMap<>();
            for (Difficulty level : Difficulty.values()) {
                if (c.learningOutcome(level) != null) {
                    outcomes.put(level.getDbValue(), c.learningOutcome(level));
                }
            }
            cells.add(new CellInput(c.getPart(), c.getQuestionType(), c.getTopic(), c.getContent(), outcomes,
                    c.getDifficulty(), statementLevels(c), c.getQuestionCount()));
        }
        return toDto(persist(new CreateMatrixRequest(copyName, source.getSubjectId(), source.getGradeLevel(),
                source.getSource(), JsonStringArray.read(source.getNotes()), cells), null, ExamMatrix.KIND_MATRIX,
                ownerId));
    }

    /** question_template của ô: source_question ghi ma trận, phần, chủ đề, nội dung và yêu cầu cần đạt. */
    private QuestionTemplate createTemplate(ExamMatrix matrix, ExamMatrixCell cell) {
        StringBuilder outcomes = new StringBuilder();
        for (Difficulty level : Difficulty.values()) {
            String text = cell.learningOutcome(level);
            if (text != null) {
                outcomes.append(outcomes.isEmpty() ? "" : " ").append("[").append(level.getDbValue()).append("] ")
                        .append(text);
            }
        }
        String content = cell.getContent() != null ? "; nội dung: " + cell.getContent() : "";
        String source = matrix.isLesson()
                ? "Sinh theo bài \"" + cell.getTopic() + "\"" + content + "; yêu cầu cần đạt: " + outcomes
                : "Ma trận \"" + matrix.getName() + "\", phần " + cell.getPart() + ", chủ đề: " + cell.getTopic()
                        + content + "; yêu cầu cần đạt: " + outcomes;
        return catalogService.createTemplate(new CreateTemplateRequest(matrix.getSubjectId(), matrix.getGradeLevel(),
                cell.getTopic(), outcomes.toString(), source, SourceType.TEXT));
    }

    /** Chi tiết ma trận của chính người gọi; của người khác thì 404 như không tồn tại (W1-1). */
    @Transactional(readOnly = true)
    public MatrixDto get(Integer id, Integer ownerId) {
        return toDto(getOwnedMatrix(id, ownerId));
    }

    private MatrixDto toDto(ExamMatrix m) {
        List<ExamMatrixCell> cells = cellRepository.findByMatrixIdOrderByPositionAsc(m.getId());
        return new MatrixDto(m.getId(), m.getName(), m.getSubjectId(), m.getGradeLevel(), m.getSource(),
                JsonStringArray.read(m.getNotes()), m.getSampleKey(), m.getCreatedAt(),
                cells.stream().map(MatrixService::toDto).toList(), totals(cells), usage.examCount(m.getId()));
    }

    /**
     * Ma trận (kind = 'matrix') thuộc {@code ownerId}; không có, của người khác hoặc là ma trận ẩn của sinh theo bài:
     * NoSuchElementException (404, cùng thông báo).
     */
    @Transactional(readOnly = true)
    public ExamMatrix getOwnedMatrix(Integer matrixId, Integer ownerId) {
        return matrixRepository.findByIdAndCreatedByAndKind(matrixId, ownerId, ExamMatrix.KIND_MATRIX)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy ma trận id=" + matrixId));
    }

    /**
     * Như {@link #getOwnedMatrix} nhưng giữ khóa chia sẻ (FOR SHARE) trên hàng ma trận tới hết transaction: tạo đề dùng
     * để một lần xóa/sửa ma trận đồng thời phải chờ (rồi thấy đề mới và trả 409), còn nếu ma trận vừa bị xóa thì 404 thay vì
     * lỗi khóa ngoại. Gọi trong transaction của nơi tạo đề.
     */
    @Transactional
    public ExamMatrix getOwnedMatrixShared(Integer matrixId, Integer ownerId) {
        return matrixRepository.lockOwnedForShare(matrixId, ownerId, ExamMatrix.KIND_MATRIX)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy ma trận id=" + matrixId));
    }

    /** Loại (matrix | lesson) của các ma trận theo id (danh sách đề, xem đề). Id không có thì bỏ qua. */
    @Transactional(readOnly = true)
    public Map<Integer, String> kindsByIds(java.util.Collection<Integer> ids) {
        Map<Integer, String> out = new LinkedHashMap<>();
        for (ExamMatrix m : matrixRepository.findAllById(ids)) {
            out.put(m.getId(), m.getKind());
        }
        return out;
    }

    /** Các ô của một ma trận theo thứ tự (dùng cho module exam). */
    @Transactional(readOnly = true)
    public List<ExamMatrixCell> cells(Integer matrixId) {
        if (!matrixRepository.existsById(matrixId)) {
            throw new NoSuchElementException("Không tìm thấy ma trận id=" + matrixId);
        }
        return cellRepository.findByMatrixIdOrderByPositionAsc(matrixId);
    }

    @Transactional(readOnly = true)
    public ExamMatrix getMatrix(Integer matrixId) {
        return matrixRepository.findById(matrixId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy ma trận id=" + matrixId));
    }

    @Transactional(readOnly = true)
    public ExamMatrixCell getCell(Integer cellId) {
        return cellRepository.findById(cellId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy ô ma trận id=" + cellId));
    }

    /** Ma trận của người gọi, mới nhất trước (W1-1). */
    @Transactional(readOnly = true)
    public List<MatrixSummaryDto> list(Integer ownerId) {
        List<ExamMatrix> matrices = matrixRepository.findAllByCreatedByAndKindOrderByIdDesc(ownerId,
                ExamMatrix.KIND_MATRIX);
        Map<Integer, Integer> exams = usage.examCounts(matrices.stream().map(ExamMatrix::getId).toList());
        return matrices.stream()
                .map(m -> new MatrixSummaryDto(m.getId(), m.getName(), m.getSubjectId(), m.getGradeLevel(),
                        m.getCreatedAt(), cellRepository.findByMatrixIdOrderByPositionAsc(m.getId()).size(),
                        exams.getOrDefault(m.getId(), 0)))
                .toList();
    }

    public List<SampleDto> samples() {
        List<SampleDto> out = new ArrayList<>();
        for (Map.Entry<String, String> e : new java.util.TreeMap<>(SAMPLES).entrySet()) {
            Map<String, Object> raw = readSample(e.getKey());
            out.add(new SampleDto(e.getKey(), (String) raw.get("name"), toInteger(raw.get("gradeLevel")),
                    (String) raw.get("source"), toStrings(raw.get("notes"))));
        }
        return out;
    }

    /** Tạo ma trận từ mẫu; bỏ trống subjectId thì dùng môn "Toán" (tạo nếu chưa có). Giữ nguyên notes của mẫu. */
    @Transactional
    public MatrixDto createFromSample(String key, CreateFromSampleRequest body, Integer ownerId) {
        Map<String, Object> raw = readSample(key);
        Integer subjectId = body != null && body.subjectId() != null
                ? body.subjectId()
                : catalogService.getOrCreateSubject(DEFAULT_SUBJECT).getId();
        List<CellInput> cells = new ArrayList<>();
        Object rawCells = raw.get("cells");
        if (rawCells instanceof List<?> list) {
            for (Object o : list) {
                cells.add(toCell((Map<?, ?>) o));
            }
        }
        return create(new CreateMatrixRequest((String) raw.get("name"), subjectId, toInteger(raw.get("gradeLevel")),
                (String) raw.get("source"), toStrings(raw.get("notes")), cells), key, ownerId);
    }

    private static Map<String, Object> readSample(String key) {
        String path = SAMPLES.get(key);
        if (path == null) {
            throw new NoSuchElementException("Không có mẫu ma trận \"" + key + "\"");
        }
        try (InputStream in = MatrixService.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Thiếu file mẫu " + path);
            }
            return SimpleJson.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static CellInput toCell(Map<?, ?> m) {
        Map<String, String> outcomes = new LinkedHashMap<>();
        if (m.get("learningOutcomes") instanceof Map<?, ?> lo) {
            lo.forEach((k, v) -> outcomes.put(String.valueOf(k), v == null ? null : String.valueOf(v)));
        }
        List<Difficulty> levels = null;
        if (m.get("statementDifficulties") instanceof List<?> l) {
            levels = l.stream().map(v -> DbEnum.fromDbValue(Difficulty.class, String.valueOf(v))).toList();
        }
        return new CellInput(
                (String) m.get("part"),
                m.get("questionType") == null ? null
                        : DbEnum.fromDbValue(QuestionType.class, String.valueOf(m.get("questionType"))),
                (String) m.get("topic"),
                (String) m.get("content"),
                outcomes,
                m.get("difficulty") == null ? null
                        : DbEnum.fromDbValue(Difficulty.class, String.valueOf(m.get("difficulty"))),
                levels,
                toInteger(m.get("questionCount")));
    }

    private static Integer toInteger(Object o) {
        return o instanceof Number n ? n.intValue() : null;
    }

    private static List<String> toStrings(Object o) {
        if (!(o instanceof List<?> l)) {
            return List.of();
        }
        return l.stream().map(String::valueOf).toList();
    }

    // ---------- kiểm tra ----------

    static void validate(CreateMatrixRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()) {
            throw new IllegalArgumentException("name không được để trống");
        }
        if (req.name().trim().length() > 255) {
            throw new IllegalArgumentException("name dài tối đa 255 ký tự");
        }
        if (req.subjectId() == null) {
            throw new IllegalArgumentException("Phải truyền subjectId");
        }
        if (req.gradeLevel() != null && (req.gradeLevel() < 6 || req.gradeLevel() > 9)) {
            throw new IllegalArgumentException("gradeLevel phải nằm trong khoảng 6 đến 9");
        }
        if (req.cells() == null || req.cells().isEmpty()) {
            throw new IllegalArgumentException("Ma trận phải có ít nhất một ô");
        }
        if (req.cells().size() > 100) {
            throw new IllegalArgumentException("Ma trận có tối đa 100 ô");
        }
        for (int i = 0; i < req.cells().size(); i++) {
            validateCell(i + 1, req.cells().get(i));
        }
    }

    private static void validateCell(int index, CellInput c) {
        String p = "Ô " + index + ": ";
        if (c == null) {
            throw new IllegalArgumentException(p + "ô trống");
        }
        String part = c.part() == null ? "" : c.part().trim();
        QuestionType expected = PART_TYPES.get(part);
        if (expected == null) {
            throw new IllegalArgumentException(p + "part phải là I, II hoặc III");
        }
        if (c.questionType() != expected) {
            throw new IllegalArgumentException(p + "phần " + part + " chỉ chứa " + switch (expected) {
                case MCQ -> "câu trắc nghiệm (mcq)";
                case TF -> "câu đúng/sai (tf)";
                case SA -> "câu trả lời ngắn (sa)";
            });
        }
        if (c.topic() == null || c.topic().isBlank()) {
            throw new IllegalArgumentException(p + "topic không được để trống");
        }
        if (c.topic().trim().length() > 255) {
            throw new IllegalArgumentException(p + "topic dài tối đa 255 ký tự");
        }
        if (c.questionCount() == null || c.questionCount() < 1 || c.questionCount() > 50) {
            throw new IllegalArgumentException(p + "questionCount phải từ 1 đến 50");
        }
        List<Difficulty> used;
        if (expected == QuestionType.TF) {
            if (c.difficulty() != null) {
                throw new IllegalArgumentException(p + "câu đúng/sai không dùng difficulty");
            }
            if (c.statementDifficulties() == null || c.statementDifficulties().size() != 4
                    || c.statementDifficulties().stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException(p + "câu đúng/sai phải có statementDifficulties gồm đúng 4 mức");
            }
            used = c.statementDifficulties();
        } else {
            if (c.statementDifficulties() != null) {
                throw new IllegalArgumentException(p + "statementDifficulties chỉ dùng cho câu đúng/sai");
            }
            if (c.difficulty() == null) {
                throw new IllegalArgumentException(p + "câu " + expected.getDbValue() + " phải có difficulty");
            }
            used = List.of(c.difficulty());
        }
        Map<String, String> outcomes = c.learningOutcomes() == null ? Map.of() : c.learningOutcomes();
        if (!LEVEL_KEYS.containsAll(outcomes.keySet())) {
            throw new IllegalArgumentException(p + "learningOutcomes chỉ nhận khóa easy, medium, hard");
        }
        for (Difficulty level : used) {
            String text = outcomes.get(level.getDbValue());
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException(p + "thiếu yêu cầu cần đạt cho mức " + level.getDbValue());
            }
        }
    }

    // ---------- DTO ----------

    static CellDto toDto(ExamMatrixCell c) {
        Map<String, String> outcomes = new LinkedHashMap<>();
        for (Difficulty level : Difficulty.values()) {
            if (c.learningOutcome(level) != null) {
                outcomes.put(level.getDbValue(), c.learningOutcome(level));
            }
        }
        return new CellDto(c.getId(), c.getPosition(), c.getPart(), c.getQuestionType(), c.getTopic(), c.getContent(),
                outcomes, c.getDifficulty(), statementLevels(c), c.getQuestionCount(), c.getTemplateId());
    }

    /** 4 mức của ý a-d (tf); null với mcq/sa. */
    public static List<Difficulty> statementLevels(ExamMatrixCell c) {
        if (c.getStatementDifficulties() == null) {
            return null;
        }
        return java.util.Arrays.stream(c.getStatementDifficulties().split(","))
                .map(v -> DbEnum.fromDbValue(Difficulty.class, v))
                .toList();
    }

    static TotalsDto totals(List<ExamMatrixCell> cells) {
        Map<String, Integer> byPart = new LinkedHashMap<>();
        byPart.put("I", 0);
        byPart.put("II", 0);
        byPart.put("III", 0);
        Map<String, Integer> byLevel = new LinkedHashMap<>();
        for (Difficulty level : Difficulty.values()) {
            byLevel.put(level.getDbValue(), 0);
        }
        int total = 0;
        for (ExamMatrixCell c : cells) {
            byPart.merge(c.getPart(), c.getQuestionCount(), Integer::sum);
            List<Difficulty> levels = c.getQuestionType() == QuestionType.TF
                    ? statementLevels(c)
                    : List.of(c.getDifficulty());
            for (Difficulty level : levels) {
                byLevel.merge(level.getDbValue(), c.getQuestionCount(), Integer::sum);
                total += c.getQuestionCount();
            }
        }
        return new TotalsDto(byPart, byLevel, total);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
