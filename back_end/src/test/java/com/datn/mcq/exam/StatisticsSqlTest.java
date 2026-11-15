package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W1-3 phần 4: chạy các file SQL thống kê ở {@code docs/thong-ke/} trên dữ liệu mẫu (đề giáo viên có lần sinh lỗi định
 * dạng, câu được sửa rồi Đổi, câu Giữ nguyên, câu Solver lệch) và kiểm số đếm. Bảo đảm file SQL chạy được và đúng bộ lọc.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class StatisticsSqlTest extends ExamFlowTestBase {

    private static final Path DIR = Path.of("..", "docs", "thong-ke");

    /** Chạy file như psql với biến mặc định của file (N-10: dev_emails rỗng, không loại ai). */
    private List<Map<String, Object>> run(String file) throws IOException {
        return jdbc.queryForList(PsqlFile.load(DIR.resolve(file)));
    }

    private static Map<String, Object> row(List<Map<String, Object>> rows, String key, String value) {
        return rows.stream().filter(r -> value.equals(r.get(key))).findFirst()
                .orElseThrow(() -> new AssertionError("Không có dòng " + key + " = " + value + " trong " + rows));
    }

    private int count(Map<String, Object> row, String column) {
        return ((Number) row.get(column)).intValue();
    }

    /** Đề 1: lỗi định dạng, câu LLM được sửa rồi Đổi, câu sau Đổi được Giữ. Đề 2: Solver lệch, giáo viên Giữ nguyên. */
    private void sampleData() throws Exception {
        int matrix = createMatrix(mcqCell("Đa thức", "Đơn thức", 1));
        int cell = cellId(matrix, 1);
        generator.sameMcq = false;
        generator.brokenMcq = 1;
        generator.unclosedMcq = 1;
        int examId = build(matrix).get("examId").asInt();
        int slot = slotId(examId, cell, 1);
        processAll();
        progress(examId);
        processAll();
        progress(examId);
        int q2 = count("SELECT generated_question_id FROM exam_slot WHERE id = ?", slot);
        Map<String, Object> q = jdbc.queryForMap("SELECT * FROM generated_question WHERE id = ?", q2);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("baseQuestionId", q2);
        body.put("questionText", (String) q.get("question_text"));
        body.put("optionA", (String) q.get("option_a"));
        body.put("optionB", (String) q.get("option_b"));
        body.put("optionC", "$9$");
        body.put("optionD", (String) q.get("option_d"));
        body.put("correctOption", "C");
        body.put("explanation", (String) q.get("explanation"));
        perform(put("/api/exams/" + examId + "/slots/" + slot + "/question").contentType(MediaType.APPLICATION_JSON)
                .content(body.toString())).andExpect(status().isOk());
        processAll();
        perform(post("/api/exams/" + examId + "/slots/" + slot + "/replace")).andExpect(status().isOk());
        settle(examId);
        perform(post("/api/exams/" + examId + "/slots/" + slot + "/keep")).andExpect(status().isOk());

        // Đề 2 dùng chủ đề khác để không lấy câu vừa Giữ từ ngân hàng; Solver luôn chọn C nên lệch đáp án B của LLM.
        SOLVER.mcqJudge = "C";
        int matrix2 = createMatrix(mcqCell("Phân thức", "Rút gọn", 1));
        int exam2 = build(matrix2).get("examId").asInt();
        settle(exam2);
        int slot2 = slotId(exam2, cellId(matrix2, 1), 1);
        perform(post("/api/exams/" + exam2 + "/slots/" + slot2 + "/keep")).andExpect(status().isOk());
    }

    @Test
    void cacFileSqlChayDuocVaDemDungTrenDuLieuMau() throws Exception {
        sampleData();

        // 01: q (sửa rồi Đổi), câu sau Đổi (Giữ nguyên), câu Solver lệch ở đề 2 (Giữ nguyên).
        List<Map<String, Object>> outcomes = run("01-ket-cuc-cau-llm.sql");
        assertEquals(2, count(row(outcomes, "ket_cuc", "giu_nguyen"), "so_cau_llm"), outcomes.toString());
        assertEquals(1, count(row(outcomes, "ket_cuc", "sua_roi_doi"), "so_cau_llm"));
        assertEquals(3, outcomes.stream().mapToInt(r -> count(r, "so_cau_llm")).sum(),
                "câu format_failed chưa từng nằm trong chỗ nên không tính; bản sửa gộp vào câu gốc");
        assertEquals(66.7, ((Number) row(outcomes, "ket_cuc", "giu_nguyen").get("ti_le_phan_tram")).doubleValue(), 0.05);

        // 02: câu LLM gốc của hai đề: lần sinh lỗi định dạng, hai câu qua cả ba bước, các lần sinh của đề 2 lệch Solver.
        Map<String, Object> qc = run("02-buoc-qc-bat-loi.sql").get(0);
        assertEquals("mcq", qc.get("question_type"));
        assertEquals(1, count(qc, "bi_chan_o_format"));
        assertTrue(count(qc, "solver_lech") >= 1, qc.toString());
        assertEquals(5, count(qc, "tong_cau_llm"), "5 lần sinh: 3 của đề 1 và 2 của đề 2 (bản sửa không tính)");
        assertEquals(1, count(qc, "tu_dong_dau_dola"), "câu quên đóng $ được hệ thống tự đóng");
        assertEquals(count(qc, "tong_cau_llm"), count(qc, "bi_chan_o_format") + count(qc, "bi_chan_o_trung")
                + count(qc, "dedup_chua_ket_luan") + count(qc, "solver_lech") + count(qc, "solver_loi")
                + count(qc, "solver_khong_chay_hoac_loi") + count(qc, "qua_ca_ba_buoc"),
                "mỗi câu thuộc đúng một nhóm kết cục");

        // 03: bản sửa đổi phương án C và đáp án.
        List<Map<String, Object>> fields = run("03-truong-giao-vien-hay-sua.sql");
        assertEquals(1, count(row(fields, "truong", "option_c"), "so_lan_sua"));
        assertEquals(1, count(row(fields, "truong", "correct_option"), "so_cho"));
        assertEquals(2, fields.size(), fields.toString());

        // 04: câu LLM Solver báo lệch và nằm trong chỗ: câu của đề 2 được Giữ nguyên.
        List<Map<String, Object>> solver = run("04-solver-lech-giao-vien-xu-ly.sql");
        assertEquals(1, count(row(solver, "ket_cuc", "giu_nguyen"), "so_cau_llm_solver_lech"), solver.toString());
    }

    /**
     * C-03 (rà soát 11/11): mỗi cột tự sửa của file 02 chỉ đếm đúng loại cảnh báo của nó. Trước đây tu_sua_escape_latex đếm
     * mọi cảnh báo "json: đã sửa ..." nên câu chỉ được thêm dấu "?" cuối câu dẫn cũng bị tính là sửa escape LaTeX.
     */
    @Test
    void cotTuSuaTachTheoLoaiCauChiCoCanhBaoDauCauKhongTinhLaEscapeLatex() throws Exception {
        sampleData();
        List<Integer> ids = jdbc.queryForList("SELECT g.id FROM generated_question g JOIN quality_check_result f"
                + " ON f.question_id = g.id AND f.check_type = 'format' WHERE g.edited_from_question_id IS NULL"
                + " AND g.id IN (SELECT question_id FROM generation_job WHERE job_type = 'generate'"
                + " AND exam_slot_id IS NOT NULL) ORDER BY g.id", Integer.class);
        assertTrue(ids.size() >= 3, ids.toString());
        jdbc.update("UPDATE quality_check_result SET warnings = '[]'::jsonb WHERE check_type = 'format'");
        setWarnings(ids.get(0), "[\"json: đã sửa dấu câu cuối stem (thêm ?)\"]");
        setWarnings(ids.get(1), "[\"json: đã sửa 2 escape LaTeX (\\\\frac, \\\\circ)\","
                + " \"json: đã sửa 1 dấu & căn lề ngoài môi trường \\\\begin ở explanation\"]");
        setWarnings(ids.get(2), "[\"json: đã sửa 1 lệnh LaTeX thiếu \\\\ (frac) ở stem\","
                + " \"json: đã sửa 1 ký tự \\\\n lọt vào công thức hoặc cặp $ thừa ở explanation\","
                + " \"json: đã sửa 1 ký hiệu độ (^\\\\bullet, ^\\\\triangle thành ^{\\\\circ}) ở option_a\"]");

        Map<String, Object> qc = run("02-buoc-qc-bat-loi.sql").get(0);
        assertEquals(1, count(qc, "tu_sua_escape_latex"), "chỉ câu có cảnh báo escape LaTeX: " + qc);
        assertEquals(1, count(qc, "tu_sua_dau_cau_cuoi"), qc.toString());
        assertEquals(1, count(qc, "tu_sua_lenh_latex_thieu_gach"), qc.toString());
        assertEquals(1, count(qc, "tu_sua_xuong_dong_lot"), qc.toString());
        assertEquals(1, count(qc, "tu_sua_ky_hieu_do"), qc.toString());
        assertEquals(1, count(qc, "tu_sua_dau_can_le"), qc.toString());
        assertEquals(0, count(qc, "tu_dong_dau_dola"), qc.toString());
    }

    private void setWarnings(int questionId, String json) {
        jdbc.update("UPDATE quality_check_result SET warnings = ?::jsonb WHERE question_id = ? AND check_type = 'format'",
                json, questionId);
    }

    @Test
    void vanHanhChayDuocVaDemJobKetThucTheoNgayVaLoai() throws Exception {
        sampleData();

        // 07 (giám sát 08/11): job đã kết thúc theo ngày và loại; dữ liệu mẫu có job sinh câu done và Solver.
        List<Map<String, Object>> ops = run("07-van-hanh.sql");
        assertTrue(ops.stream().anyMatch(r -> "generate".equals(r.get("job_type"))), ops.toString());
        int total = ops.stream().mapToInt(r -> count(r, "so_job_ket_thuc")).sum();
        assertEquals(count("SELECT count(*) FROM generation_job WHERE status IN ('done', 'failed')"
                + " AND started_at IS NOT NULL"), total, "mọi job đã kết thúc và đã được nhận đều được đếm: " + ops);
        for (Map<String, Object> r : ops) {
            assertTrue(count(r, "so_that_bai") <= count(r, "so_job_ket_thuc"), r.toString());
        }
    }

    /** PR B (12/11): 10-yeu-cau-giao-vien.sql đếm kết luận request_check theo mã, theo mức và lượt Đổi sau not_met. */
    @Test
    void yeuCauGiaoVienDemTheoMaTheoMucVaLuotDoiSauCanhBao() throws Exception {
        SOLVER.requestCheck = "{\"verdict\": \"not_met\", \"reason\": \"thiếu\"}";
        int matrix = createMatrix(mcqCell("Đa thức", "Đơn thức", 1));
        int examId = json(postJson("/api/exams/build", "{\"matrixId\": " + matrix
                + ", \"requestCodes\": [\"real_context\", \"multi_step\"]}").andExpect(status().isOk()))
                .get("examId").asInt();
        settle(examId);
        int slot = slotId(examId, cellId(matrix, 1), 1);
        SOLVER.requestCheck = "{\"verdict\": \"met\", \"reason\": \"ok\"}";
        perform(post("/api/exams/" + examId + "/slots/" + slot + "/replace")).andExpect(status().isOk());
        settle(examId);
        // câu không có yêu cầu không được tính
        int plain = createMatrix(mcqCell("Phân thức", "Rút gọn", 1));
        settle(build(plain).get("examId").asInt());

        List<Map<String, Object>> rows = run("10-yeu-cau-giao-vien.sql");
        Map<String, Object> real = rows.stream().filter(r -> "ma".equals(r.get("bang")) && "real_context".equals(r.get("nhom")))
                .findFirst().orElseThrow(() -> new AssertionError(rows.toString()));
        assertEquals(2, count(real, "so_cau"), rows.toString());
        assertEquals(1, count(real, "met"));
        assertEquals(1, count(real, "not_met"));
        assertEquals(0, count(real, "partial"));
        Map<String, Object> multi = rows.stream().filter(r -> "ma".equals(r.get("bang")) && "multi_step".equals(r.get("nhom")))
                .findFirst().orElseThrow();
        assertEquals(2, count(multi, "so_cau"), "câu nhiều mã được tính cho mỗi mã");
        Map<String, Object> scope = rows.stream().filter(r -> "scope".equals(r.get("bang")) && "exam".equals(r.get("nhom")))
                .findFirst().orElseThrow(() -> new AssertionError(rows.toString()));
        assertEquals(2, count(scope, "so_cau"));
        Map<String, Object> after = rows.stream().filter(r -> "doi_sau".equals(r.get("bang"))).findFirst()
                .orElseThrow(() -> new AssertionError(rows.toString()));
        assertEquals("not_met", after.get("nhom"));
        assertEquals(1, count(after, "so_cau"));
        assertEquals(4, rows.size(), rows.toString());
    }

    /**
     * PA1 (13/11): 08-phuong-an-nhieu.sql. Đề 1: câu có 3 lỗi (2 khớp, 1 không khớp), Giữ. Đề 2: câu có 3 lỗi đều khớp, Đổi vì
     * "phương án mơ hồ"; câu sinh lại không có lỗi (tính vào mẫu số "đủ lỗi").
     */
    @Test
    void phuongAnNhieuDemDuLoiKetQuaVaGiuDoiTheoNhom() throws Exception {
        generator.distractorErrors = "{\"A\": \"Lỗi A\", \"B\": null, \"C\": \"Lỗi C\", \"D\": \"Lỗi D\"}";
        SOLVER.mcqOptions = "B";
        SOLVER.errorChecks = "[{\"error\": 1, \"result\": \"A\", \"reason\": \"r\"}, {\"error\": 2, \"result\": \"none\","
                + " \"reason\": \"r\"}, {\"error\": 3, \"result\": \"D\", \"reason\": \"r\"}]";
        int m1 = createMatrix(mcqCell("Đa thức", "Đơn thức", 1));
        int e1 = build(m1).get("examId").asInt();
        settle(e1);
        int s1 = slotId(e1, cellId(m1, 1), 1);
        perform(post("/api/exams/" + e1 + "/slots/" + s1 + "/keep")).andExpect(status().isOk());

        SOLVER.errorChecks = "[{\"error\": 1, \"result\": \"A\", \"reason\": \"r\"}, {\"error\": 2, \"result\": \"C\","
                + " \"reason\": \"r\"}, {\"error\": 3, \"result\": \"D\", \"reason\": \"r\"}]";
        int m2 = createMatrix(mcqCell("Phân thức", "Rút gọn", 1));
        int e2 = build(m2).get("examId").asInt();
        settle(e2);
        int s2 = slotId(e2, cellId(m2, 1), 1);
        generator.distractorErrors = null;
        postJson("/api/exams/" + e2 + "/slots/" + s2 + "/replace", "{\"reason\": \"ambiguous_options\"}")
                .andExpect(status().isOk());
        settle(e2);

        List<Map<String, Object>> rows = run("08-phuong-an-nhieu.sql");
        java.util.function.BiFunction<String, String, Map<String, Object>> get = (chiSo, nhom) -> rows.stream()
                .filter(r -> chiSo.equals(r.get("chi_so")) && nhom.equals(r.get("nhom"))).findFirst()
                .orElseThrow(() -> new AssertionError(chiSo + "/" + nhom + " không có trong " + rows));
        java.util.function.BiConsumer<Map<String, Object>, int[]> check = (r, expected) -> {
            assertEquals(expected[0], count(r, "so_cau"), r.toString());
            assertEquals(expected[1], count(r, "mau_so"), r.toString());
        };
        check.accept(get.apply("du_loi", "mcq"), new int[] {2, 3});
        check.accept(get.apply("ket_qua_loi", "match"), new int[] {5, 6});
        check.accept(get.apply("ket_qua_loi", "mismatch"), new int[] {1, 6});
        check.accept(get.apply("ket_qua_loi", "unknown"), new int[] {0, 6});
        check.accept(get.apply("ket_qua_loi", "unchecked"), new int[] {0, 6});
        check.accept(get.apply("ca_3_khop", "mcq"), new int[] {1, 2});
        check.accept(get.apply("giu", "co_mismatch"), new int[] {1, 1});
        check.accept(get.apply("giu", "khong_mismatch"), new int[] {0, 1});
        check.accept(get.apply("doi", "co_mismatch"), new int[] {0, 1});
        check.accept(get.apply("doi", "khong_mismatch"), new int[] {1, 1});
        check.accept(get.apply("doi_mo_ho", "khong_mismatch"), new int[] {1, 1});
        check.accept(get.apply("doi_mo_ho", "co_mismatch"), new int[] {0, 0});
        assertEquals(null, get.apply("doi_mo_ho", "co_mismatch").get("ti_le_phan_tram"), "mẫu số 0 thì không có tỉ lệ");
        assertEquals(12, rows.size(), rows.toString());
    }

    @Test
    void loaiDeCuaTaiKhoanNhomPhatTrienBangDanhSachEmail() throws Exception {
        sampleData();
        String email = jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, teacher);
        assertTrue(!run("01-ket-cuc-cau-llm.sql").isEmpty(), "mặc định (dev_emails rỗng) không loại ai");
        // N-10: như psql -v dev_emails='khac@x.vn, <email>' (danh sách cách nhau bằng dấu phẩy, không phân biệt hoa thường)
        String sql = PsqlFile.load(DIR.resolve("01-ket-cuc-cau-llm.sql"),
                Map.of("dev_emails", "khac@x.vn, " + email.toUpperCase()));
        assertEquals(List.of(), jdbc.queryForList(sql), "toàn bộ đề của tài khoản nhóm bị loại");
    }

    /** N-10: mọi file thống kê dùng cùng biến psql dev_emails (mặc định rỗng), không còn mảng email chép tay. */
    @Test
    void moiFileDungBienPsqlDevEmailsMacDinhRong() throws IOException {
        List<Path> files = new java.util.ArrayList<>();
        try (var list = Files.list(DIR)) {
            list.filter(p -> p.toString().endsWith(".sql")).forEach(files::add);
        }
        files.add(Path.of("..", "docs", "experiments", "trial", "queries.sql"));
        assertEquals(10, files.size());
        for (Path p : files) {
            String raw = Files.readString(p, StandardCharsets.UTF_8);
            assertTrue(raw.contains("\\set dev_emails ''"), p + ": khai báo mặc định rỗng");
            assertTrue(raw.contains(":'dev_emails'"), p + ": dùng biến");
            assertTrue(!raw.contains("ARRAY[]::text[]"), p + ": không còn mảng email chép tay");
        }
    }

    @Test
    void coMotCauLenhSelectDocLapMoiFile() throws IOException {
        try (var files = Files.list(DIR)) {
            List<Path> sqlFiles = files.filter(p -> p.toString().endsWith(".sql")).sorted().toList();
            assertEquals(9, sqlFiles.size());
            for (Path p : sqlFiles) {
                // bỏ phần chú thích (-- ...) trước khi kiểm
                String sql = Files.readString(p, StandardCharsets.UTF_8).lines()
                        .map(l -> l.replaceAll("--.*$", "")).collect(java.util.stream.Collectors.joining("\n"))
                        .toLowerCase();
                assertTrue(sql.contains("select") && !sql.matches("(?s).*\\b(insert|update|delete|drop|alter)\\b.*"),
                        p + " chỉ được là truy vấn đọc");
                assertEquals(1, sql.chars().filter(c -> c == ';').count(), p + " phải đúng một câu lệnh");
            }
        }
    }
}
