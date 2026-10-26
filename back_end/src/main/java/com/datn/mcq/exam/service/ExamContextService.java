package com.datn.mcq.exam.service;

import com.datn.mcq.common.exception.ApiErrorException;
import com.datn.mcq.exam.dto.ExamBuildDtos.CellContextInput;
import com.datn.mcq.exam.entity.ExamGenerationContext;
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

    /** Một phạm vi đã chuẩn hóa: tài liệu (không trùng) và yêu cầu thêm (null khi không có). */
    public record Scope(List<Integer> materialIds, String instruction) {
        boolean isEmpty() {
            return materialIds.isEmpty() && instruction == null;
        }
    }

    /** Ngữ cảnh đã kiểm tra của một lần tạo đề: phạm vi cả đề và phạm vi theo id ô. */
    public record Resolved(Scope examScope, Map<Integer, Scope> cellScopes) {

        public static final Resolved NONE = new Resolved(new Scope(List.of(), null), Map.of());

        /** Tài liệu hiệu lực của ô: hợp của cả đề và của ô. */
        public Set<Integer> materialsFor(Integer cellId) {
            Set<Integer> out = new LinkedHashSet<>(examScope.materialIds());
            Scope own = cellScopes.get(cellId);
            if (own != null) {
                out.addAll(own.materialIds());
            }
            return out;
        }

        /** Yêu cầu thêm hiệu lực của ô: của ô nếu có, không thì của cả đề; null khi không có. */
        public String instructionFor(Integer cellId) {
            Scope own = cellScopes.get(cellId);
            return own != null && own.instruction() != null ? own.instruction() : examScope.instruction();
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
        Scope examScope = new Scope(distinctIds(examMaterialIds), normalizeInstruction(examInstruction));
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
            Scope scope = new Scope(distinctIds(in.materialIds()), normalizeInstruction(in.extraInstruction()));
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
        c.setMaterialIds(new LinkedHashSet<>(scope.materialIds()));
        repository.save(c);
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
            Scope scope = new Scope(new ArrayList<>(c.getMaterialIds()), c.getExtraInstruction());
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
