package com.datn.mcq.generation.tool;

import com.datn.mcq.generation.entity.GeneratedQuestion;
import com.datn.mcq.generation.service.GenerationService;
import com.datn.mcq.generation.service.StoredQcInput;
import com.datn.mcq.llm.client.QuotaExhaustedException;
import com.datn.mcq.llm.prompt.PromptBuilder;
import com.datn.mcq.qc.dto.QuestionQcInput;
import com.datn.mcq.qc.dto.SolverVerdict;
import com.datn.mcq.qc.orchestrator.QcOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Công cụ đo Solver CHẠY THỦ CÔNG (thẩm định 31/10): gọi Solver cho một danh sách câu đã lưu và ghi CSV ra thư mục ngoài
 * repo. Chỉ tồn tại khi có {@code app.solver-probe.ids-file}; test và chạy app bình thường không có bean này.
 *
 * <ul>
 *   <li>Gọi Solver qua {@link QcOrchestrator#callSolver} (đúng LlmClient, model Solver và bộ điều tiết GeminiRateLimiter
 *       của app). GỌI GEMINI TÍNH PHÍ: mỗi câu một lượt.</li>
 *   <li>KHÔNG ghi DB: không lưu dòng answer_check, không đổi trạng thái câu. Lượt gọi của công cụ vì thế không được
 *       bộ điều tiết đếm lại khi app khởi động lần sau (đếm theo dòng answer_check).</li>
 *   <li>Chỉ đọc câu đã lưu (generated_question và 4 ý tf); Solver chỉ thấy đề và phương án/ý như trong pipeline.</li>
 *   <li>{@code app.solver-probe.prompt-version} chọn phiên bản prompt Solver mcq cần đo (một giá trị trong
 *       {@link PromptBuilder#SOLVER_PROMPT_VERSIONS}, ví dụ {@code solver-v2-delimited} cho số "trước",
 *       {@code solver-v3-option-check} hoặc {@code solver-v4-literal-reading} cho số "sau"); Solver của lần chạy lấy đúng
 *       phiên bản này. Không có trong code hoặc khác phiên bản Solver đang chạy thì dừng trước khi gọi lượt nào.</li>
 * </ul>
 *
 * Chạy xong thì đóng app. Chạy kèm {@code --server.port=0 --app.worker.enabled=false}; KHÔNG dùng
 * {@code --spring.main.web-application-type=none} (SecurityConfig cần ngữ cảnh web, app không khởi động được).
 */
@Component
@ConditionalOnProperty(prefix = "app.solver-probe", name = "ids-file")
public class SolverProbeRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SolverProbeRunner.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final GenerationService generationService;
    private final QcOrchestrator qcOrchestrator;
    private final ConfigurableApplicationContext context;
    private final String idsFile;
    private final String promptVersion;
    private final String outDir;

    public SolverProbeRunner(
            GenerationService generationService,
            QcOrchestrator qcOrchestrator,
            ConfigurableApplicationContext context,
            @Value("${app.solver-probe.ids-file}") String idsFile,
            @Value("${app.solver-probe.prompt-version:}") String promptVersion,
            @Value("${app.solver-probe.out-dir:}") String outDir) {
        this.generationService = generationService;
        this.qcOrchestrator = qcOrchestrator;
        this.context = context;
        this.idsFile = idsFile;
        this.promptVersion = promptVersion == null ? "" : promptVersion.trim();
        this.outDir = outDir == null ? "" : outDir.trim();
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        // Kiểm tra mọi tham số trước lượt gọi đầu tiên.
        checkPromptVersion(promptVersion, qcOrchestrator.solverPromptVersion());
        Path out = resolveOutDir(outDir, Path.of("").toAbsolutePath());
        List<Integer> ids = parseIds(Files.readString(Path.of(idsFile.trim()), StandardCharsets.UTF_8));
        if (ids.isEmpty()) {
            throw new IllegalStateException("Danh sách id câu rỗng: " + idsFile);
        }
        if (!qcOrchestrator.solverAvailable()) {
            throw new IllegalStateException(QcOrchestrator.SOLVER_NOT_CONFIGURED);
        }
        Files.createDirectories(out);
        Path csv = out.resolve("solver-probe_" + promptVersion + "_" + LocalDateTime.now().format(STAMP) + ".csv");
        log.info("Công cụ đo Solver: {} câu, prompt {}, ghi {}", ids.size(), promptVersion, csv);

        int consistent = 0;
        int inconsistent = 0;
        int errors = 0;
        try (BufferedWriter w = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
            w.write('﻿'); // BOM để Excel đọc đúng tiếng Việt
            w.write(SolverProbeRow.HEADER);
            w.newLine();
            for (int i = 0; i < ids.size(); i++) {
                Integer id = ids.get(i);
                SolverProbeRow row;
                QuestionQcInput input = null;
                try {
                    GeneratedQuestion gq = generationService.getQuestionById(id);
                    input = StoredQcInput.of(gq, generationService.getStatements(id), false, true);
                    SolverVerdict verdict = qcOrchestrator.callSolver(input);
                    row = SolverProbeRow.of(promptVersion, input, verdict);
                } catch (QuotaExhaustedException ex) {
                    row = SolverProbeRow.error(promptVersion, id, input, "hết quota Gemini trong ngày: " + ex.getMessage());
                    writeRow(w, row);
                    errors++;
                    log.warn("Dừng ở câu {} ({}/{}): hết quota Gemini trong ngày.", id, i + 1, ids.size());
                    break;
                } catch (RuntimeException ex) {
                    row = SolverProbeRow.error(promptVersion, id, input, ex.getClass().getSimpleName() + ": "
                            + ex.getMessage());
                }
                writeRow(w, row);
                switch (row.verdict()) {
                    case "consistent" -> consistent++;
                    case "inconsistent" -> inconsistent++;
                    default -> errors++;
                }
                log.info("[{}/{}] câu {}: {} (đáp án {}, Solver {})", i + 1, ids.size(), id, row.verdict(),
                        row.questionAnswer(), row.solverAnswer());
            }
        }
        log.info("Công cụ đo Solver xong: consistent={}, inconsistent={}, error={}. File: {}",
                consistent, inconsistent, errors, csv);
        System.exit(SpringApplication.exit(context, () -> 0));
    }

    private static void writeRow(BufferedWriter w, SolverProbeRow row) throws IOException {
        w.write(row.toCsvLine());
        w.newLine();
        w.flush(); // giữ được kết quả đã có nếu dừng giữa chừng
    }

    /**
     * Phiên bản yêu cầu phải có trong code và bằng phiên bản Solver đang chạy ({@code active}). Bình thường hai giá trị
     * trùng nhau vì Solver lấy mặc định từ {@code app.solver-probe.prompt-version}; khác nhau khi đặt thêm
     * {@code app.llm.solver-prompt-version} khác đi.
     */
    static void checkPromptVersion(String requested, String active) {
        String known = String.join(", ", PromptBuilder.SOLVER_PROMPT_VERSIONS);
        if (requested == null || requested.isBlank()) {
            throw new IllegalStateException("Thiếu app.solver-probe.prompt-version (có: " + known + ")");
        }
        if (!PromptBuilder.SOLVER_PROMPT_VERSIONS.contains(requested.trim())) {
            throw new IllegalStateException("Phiên bản prompt Solver '" + requested + "' không có trong code (có: "
                    + known + ")");
        }
        if (!requested.trim().equals(active)) {
            throw new IllegalStateException("Solver đang chạy prompt '" + active + "', khác phiên bản cần đo '"
                    + requested + "' (bỏ hoặc sửa app.llm.solver-prompt-version)");
        }
    }

    /**
     * Thư mục ghi CSV, bắt buộc nằm NGOÀI repo (repo là thư mục cha gần nhất của {@code workingDir} có ".git"; không tìm
     * thấy thì coi {@code workingDir} là repo).
     */
    static Path resolveOutDir(String outDir, Path workingDir) {
        if (outDir == null || outDir.isBlank()) {
            throw new IllegalStateException("Thiếu app.solver-probe.out-dir (thư mục ngoài repo để ghi CSV)");
        }
        Path out = Path.of(outDir.trim()).toAbsolutePath().normalize();
        Path wd = workingDir.toAbsolutePath().normalize();
        Path repo = wd;
        for (Path p = wd; p != null; p = p.getParent()) {
            if (Files.exists(p.resolve(".git"))) {
                repo = p;
                break;
            }
        }
        if (out.startsWith(repo)) {
            throw new IllegalStateException("app.solver-probe.out-dir phải nằm ngoài repo (" + repo + "): " + out);
        }
        return out;
    }

    /**
     * Đọc id câu: phân tách bằng dấu phẩy, khoảng trắng hoặc xuống dòng; bỏ dòng trống và phần sau "#"; bỏ id trùng, giữ
     * thứ tự. Giá trị không phải số nguyên dương thì ném lỗi.
     */
    static List<Integer> parseIds(String text) {
        Set<Integer> ids = new LinkedHashSet<>();
        for (String line : text.replace("﻿", "").split("\\R")) {
            int hash = line.indexOf('#');
            String content = hash >= 0 ? line.substring(0, hash) : line;
            for (String token : content.split("[,;\\s]+")) {
                if (token.isBlank()) {
                    continue;
                }
                int id;
                try {
                    id = Integer.parseInt(token.trim());
                } catch (NumberFormatException ex) {
                    throw new IllegalStateException("Id câu không hợp lệ: '" + token + "'");
                }
                if (id <= 0) {
                    throw new IllegalStateException("Id câu không hợp lệ: '" + token + "'");
                }
                ids.add(id);
            }
        }
        return new ArrayList<>(ids);
    }
}
