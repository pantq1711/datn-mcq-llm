package com.datn.mcq.exam.service;

import com.datn.mcq.common.exception.ApiErrorException;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.enums.TeacherRequest;
import com.datn.mcq.exam.dto.ExamBuildDtos.CellContextInput;
import com.datn.mcq.exam.entity.ExamGenerationContext;
import com.datn.mcq.generation.entity.GenerationJob;
import com.datn.mcq.generation.service.GenerationService;
import com.datn.mcq.exam.repository.ExamGenerationContextRepository;
import com.datn.mcq.matrix.entity.ExamMatrixCell;
import com.datn.mcq.material.service.MaterialService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ngữ cảnh sinh câu của một đề (W2-3): tài liệu bài học đính kèm và "yêu cầu thêm cho AI". Gắn theo TỪNG ĐỀ (quyết định
 * F), phạm vi cả đề (sinh theo bài, hoặc "cả ma trận") hoặc một ô. Tài liệu hiệu lực của một ô là hợp của tài liệu cả đề
 * và của ô; yêu cầu thêm hiệu lực là của ô nếu có, không thì của cả đề.
 *
 * <p>Kiểm tra đầu vào trước mọi thay đổi: tài liệu phải của người tạo đề (404, như không tồn tại), tối đa
 * {@value #MAX_MATERIALS} tài liệu khác nhau mỗi lần sinh, yêu cầu thêm tối đa {@value #MAX_INSTRUCTION_CHARS} ký tự, ô
 * phải thuộc ma trận và không lặp. Lưu và đọc chạy trong transaction của nơi tạo đề.
 */
@Service
public class ExamContextService {

    public static final int MAX_MATERIALS = 5;
    public static final int MAX_INSTRUCTION_CHARS = 1000;
    public static final String TOO_MANY_MATERIALS = "TOO_MANY_MATERIALS";
    public static final String INSTRUCTION_TOO_LONG = "INSTRUCTION_TOO_LONG";
    public static final String INVALID_REQUEST = "INVALID_REQUEST";

    /**
     * Một phạm vi đã chuẩn hóa: tài liệu (không trùng), yêu cầu thêm (null khi không có) và (PR B) mã chip yêu cầu (không
     * trùng, theo thứ tự danh mục; rỗng khi không có).
     */
    public record Scope(List<Integer> materialIds, String instruction, List<String> requestCodes) {
        public Scope {
            requestCodes = requestCodes == null ? List.of() : List.copyOf(requestCodes);
        }

        public Scope(List<Integer> materialIds, String instruction) {
            this(materialIds, instruction, List.of());
        }

        boolean isEmpty() {
            return materialIds.isEmpty() && !hasRequest();
        }

        /** PR B: phạm vi có yêu cầu khi có ít nhất một mã hoặc có văn bản tự do. */
        boolean hasRequest() {
            return instruction != null || !requestCodes.isEmpty();
        }
    }

    /**
     * Yêu cầu của MỘT mức (12/11, PR B): mã và văn bản luôn lấy từ cùng một mức, theo quy tắc thay hẳn (ô có yêu cầu thì cả
     * bộ của ô, không thì của đề).
     *
     * @param codes mã chip của mức (chưa lọc theo dạng câu)
     * @param text  văn bản tự do đã lưu của mức; null khi mức chỉ có mã
     * @param scope {@code cell} hoặc {@code exam}
     */
    public record RequestLevel(List<String> codes, String text, String scope) {
        public RequestLevel {
            codes = codes == null ? List.of() : List.copyOf(codes);
        }

        /** Mã áp dụng cho dạng câu {@code type}, theo thứ tự danh mục. */
        public List<String> codesFor(QuestionType type) {
            return TeacherRequest.applicable(codes, type);
        }

        /** Yêu cầu hiệu lực cho một câu dạng {@code type}; null khi sau khi lọc không còn mã và không có văn bản. */
        public GenerationService.JobRequest forType(QuestionType type) {
            List<String> applicable = codesFor(type);
            return applicable.isEmpty() && text == null ? null : new GenerationService.JobRequest(applicable, text, scope);
        }
    }

    /** Ngữ cảnh đã kiểm tra của một lần tạo đề: phạm vi cả đề và phạm vi theo id ô. */
    public record Resolved(Scope examScope, Map<Integer, Scope> cellScopes) {

        public static final Resolved NONE = new Resolved(new Scope(List.of(), null), Map.of());

        /**
         * PR B: yêu cầu thừa hưởng của ô: của ô nếu ô có yêu cầu (mã hoặc văn bản), không thì của cả đề; null khi cả hai không
         * có. Mức cụ thể hơn THAY HẲN mức dưới, cho cả mã lẫn văn bản.
         */
        public RequestLevel requestFor(Integer cellId) {
            Scope own = cellScopes.get(cellId);
            if (own != null && own.hasRequest()) {
                return new RequestLevel(own.requestCodes(), own.instruction(), GenerationJob.REQUEST_SCOPE_CELL);
            }
            return examScope.hasRequest()
                    ? new RequestLevel(examScope.requestCodes(), examScope.instruction(), GenerationJob.REQUEST_SCOPE_EXAM)
                    : null;
        }

        /** Tài liệu hiệu lực của ô: hợp của cả đề và của ô. */
        public Set<Integer> materialsFor(Integer cellId) {
            Set<Integer> out = new LinkedHashSet<>(examScope.materialIds());
            Scope own = cellScopes.get(cellId);
            if (own != null) {
                out.addAll(own.materialIds());
            }
            return out;
        }

        /**
         * Yêu cầu thêm hiệu lực của ô: văn bản của mức {@link #requestFor} chọn; null khi không có. PR B: ô chỉ có mã (không
         * văn bản) thì vẫn thay hẳn đề, nên kết quả là null dù đề có văn bản.
         */
        public String instructionFor(Integer cellId) {
            RequestLevel level = requestFor(cellId);
            return level == null ? null : level.text();
        }

        public boolean isEmpty() {
            return examScope.isEmpty() && cellScopes.isEmpty();
        }
    }

    private final ExamGenerationContextRepository repository;
    private final MaterialService materialService;

    public ExamContextService(ExamGenerationContextRepository repository, MaterialService materialService) {
        this.repository = repository;
        this.materialService = materialService;
    }

    /**
     * Kiểm tra và chuẩn hóa đầu vào của một lần tạo đề.
     *
     * @param cells         các ô của ma trận (để kiểm {@code cellInputs}); với sinh theo bài là danh sách ô ẩn
     * @param cellInputs    ngữ cảnh theo ô (chỉ tạo đề theo ma trận); null hoặc rỗng là không có
     * @throws ApiErrorException 400 TOO_MANY_MATERIALS, INSTRUCTION_TOO_LONG, INVALID_REQUEST; 404 nếu có tài liệu không
     *                           phải của {@code ownerId}
     */
    @Transactional
    public Resolved resolve(Integer ownerId, List<ExamMatrixCell> cells, List<Integer> examMaterialIds,
                            String examInstruction, List<CellContextInput> cellInputs) {
        return resolve(ownerId, cells, examMaterialIds, examInstruction, null, cellInputs);
    }

    /** Như trên, kèm (PR B) mã chip yêu cầu của cả đề; mã lạ là 400 INVALID_REQUEST, kiểm trước mọi ghi. */
    @Transactional
    public Resolved resolve(Integer ownerId, List<ExamMatrixCell> cells, List<Integer> examMaterialIds,
                            String examInstruction, List<String> examRequestCodes, List<CellContextInput> cellInputs) {
        Scope examScope = new Scope(distinctIds(examMaterialIds), normalizeInstruction(examInstruction),
                normalizeRequestCodes(examRequestCodes));
        Map<Integer, Scope> cellScopes = new LinkedHashMap<>();
        Set<Integer> validCells = new HashSet<>();
        cells.forEach(c -> validCells.add(c.getId()));
        for (CellContextInput in : cellInputs == null ? List.<CellContextInput>of() : cellInputs) {
            if (in == null || in.cellId() == null) {
                throw ApiErrorException.badRequest(INVALID_REQUEST, "Mỗi phần tử của cells phải có cellId.");
            }
            if (!validCells.contains(in.cellId())) {
                throw ApiErrorException.badRequest(INVALID_REQUEST, "Ô id=" + in.cellId() + " không thuộc ma trận.");
            }
            if (cellScopes.containsKey(in.cellId())) {
                throw ApiErrorException.badRequest(INVALID_REQUEST, "Ô id=" + in.cellId() + " xuất hiện nhiều lần.");
            }
            Scope scope = new Scope(distinctIds(in.materialIds()), normalizeInstruction(in.extraInstruction()),
                    normalizeRequestCodes(in.requestCodes()));
            if (!scope.isEmpty()) {
                cellScopes.put(in.cellId(), scope);
            }
        }
        Set<Integer> all = new LinkedHashSet<>(examScope.materialIds());
        cellScopes.values().forEach(s -> all.addAll(s.materialIds()));
        if (all.size() > MAX_MATERIALS) {
            throw ApiErrorException.badRequest(TOO_MANY_MATERIALS, "Mỗi lần sinh tối đa " + MAX_MATERIALS
                    + " tài liệu khác nhau (đang chọn " + all.size() + ").");
        }
        materialService.requireOwnedShared(all, ownerId);
        return examScope.isEmpty() && cellScopes.isEmpty() ? Resolved.NONE : new Resolved(examScope, cellScopes);
    }

    /** Lưu ngữ cảnh đã kiểm tra của đề; không có gì để lưu thì không tạo dòng nào. */
    @Transactional
    public void persist(Integer examId, Resolved resolved) {
        if (!resolved.examScope().isEmpty()) {
            save(examId, null, resolved.examScope());
        }
        resolved.cellScopes().forEach((cellId, scope) -> save(examId, cellId, scope));
    }

    private void save(Integer examId, Integer cellId, Scope scope) {
        ExamGenerationContext c = new ExamGenerationContext();
        c.setExamId(examId);
        c.setCellId(cellId);
        c.setExtraInstruction(scope.instruction());
        c.setRequestCodes(scope.requestCodes());
        c.setMaterialIds(new LinkedHashSet<>(scope.materialIds()));
        repository.save(c);
    }

    /** PR B: yêu cầu thừa hưởng của một ô trong đề đã tạo (đọc từ DB: Đổi câu, sinh lại); null khi không có. */
    @Transactional(readOnly = true)
    public RequestLevel requestFor(Integer examId, Integer cellId) {
        return load(examId).requestFor(cellId);
    }

    /**
     * PR B: yêu cầu (không kèm tài liệu) của nhiều đề trong MỘT truy vấn, cho xem đề và danh sách đề. Đề không có yêu cầu nào
     * thì vắng mặt.
     */
    @Transactional(readOnly = true)
    public Map<Integer, Resolved> requestsOf(java.util.Collection<Integer> examIds) {
        Map<Integer, Resolved> out = new LinkedHashMap<>();
        if (examIds.isEmpty()) {
            return out;
        }
        for (Object[] row : repository.findRequestsByExamIds(examIds)) {
            Integer examId = ((Number) row[0]).intValue();
            Integer cellId = row[1] == null ? null : ((Number) row[1]).intValue();
            String codes = row[3] == null ? null : String.valueOf(row[3]);
            Scope scope = new Scope(List.of(), (String) row[2],
                    codes == null || codes.isEmpty() ? List.of() : List.of(codes.split(",")));
            Resolved current = out.getOrDefault(examId, new Resolved(new Scope(List.of(), null), new LinkedHashMap<>()));
            if (cellId == null) {
                current = new Resolved(scope, current.cellScopes());
            } else {
                current.cellScopes().put(cellId, scope);
            }
            out.put(examId, current);
        }
        return out;
    }

    /**
     * PR B (12/11): chuẩn hóa mã chip yêu cầu client gửi: không phân biệt hoa thường, bỏ khoảng trắng hai đầu, gộp mã trùng,
     * theo thứ tự danh mục ({@link TeacherRequest}). null là không gửi (trả null); mảng rỗng trả rỗng. Mã lạ hoặc phần tử rỗng:
     * 400 INVALID_REQUEST (nơi gọi kiểm trước mọi ghi).
     */
    public static List<String> normalizeRequestCodes(List<String> raw) {
        if (raw == null) {
            return null;
        }
        for (String code : raw) {
            if (TeacherRequest.parseOrNull(code) == null) {
                throw ApiErrorException.badRequest(INVALID_REQUEST, "requestCodes có mã không hợp lệ: "
                        + (code == null ? "null" : "\"" + code.strip() + "\"") + ". Mã hợp lệ: "
                        + String.join(", ", java.util.Arrays.stream(TeacherRequest.values())
                                .map(TeacherRequest::getDbValue).toList()) + ".");
            }
        }
        return TeacherRequest.applicable(raw, null);
    }

    /** Tài liệu hiệu lực của một ô trong đề đã tạo (đọc từ DB: dùng khi sinh lại, Giữ câu). */
    @Transactional(readOnly = true)
    public Set<Integer> materialsFor(Integer examId, Integer cellId) {
        return load(examId).materialsFor(cellId);
    }

    /** Yêu cầu thêm hiệu lực của một ô trong đề đã tạo; null khi không có. */
    @Transactional(readOnly = true)
    public String instructionFor(Integer examId, Integer cellId) {
        return load(examId).instructionFor(cellId);
    }

    private Resolved load(Integer examId) {
        Scope examScope = new Scope(List.of(), null);
        Map<Integer, Scope> cellScopes = new LinkedHashMap<>();
        for (ExamGenerationContext c : repository.findByExamId(examId)) {
            Scope scope = new Scope(new ArrayList<>(c.getMaterialIds()), c.getExtraInstruction(), c.getRequestCodes());
            if (c.getCellId() == null) {
                examScope = scope;
            } else {
                cellScopes.put(c.getCellId(), scope);
            }
        }
        return new Resolved(examScope, cellScopes);
    }

    private static List<Integer> distinctIds(List<Integer> ids) {
        Set<Integer> out = new LinkedHashSet<>();
        for (Integer id : ids == null ? List.<Integer>of() : ids) {
            if (id == null) {
                throw ApiErrorException.badRequest(INVALID_REQUEST, "materialIds không được chứa giá trị rỗng.");
            }
            out.add(id);
        }
        return new ArrayList<>(out);
    }

    /**
     * Chuẩn hóa yêu cầu thêm: Unicode NFC, xuống dòng \n, bỏ ký tự điều khiển và ký tự định dạng ẩn (bidi, độ rộng bằng
     * không), trim; trống thì null; dài quá {@value #MAX_INSTRUCTION_CHARS} ký tự (điểm mã Unicode) thì 400.
     */
    static String normalizeInstruction(String raw) {
        if (raw == null) {
            return null;
        }
        String s = Normalizer.normalize(raw.replace("\r\n", "\n").replace('\r', '\n'), Normalizer.Form.NFC);
        StringBuilder out = new StringBuilder(s.length());
        s.codePoints().forEach(cp -> {
            int type = Character.getType(cp);
            if (cp == '\n' || cp == '\t' || (type != Character.CONTROL && type != Character.FORMAT
                    && type != Character.SURROGATE && type != Character.PRIVATE_USE)) {
                out.appendCodePoint(cp);
            }
        });
        String clean = out.toString().strip();
        if (clean.isEmpty()) {
            return null;
        }
        if (clean.codePointCount(0, clean.length()) > MAX_INSTRUCTION_CHARS) {
            throw ApiErrorException.badRequest(INSTRUCTION_TOO_LONG, "Yêu cầu thêm cho AI tối đa "
                    + MAX_INSTRUCTION_CHARS + " ký tự.");
        }
        return clean;
    }
}
