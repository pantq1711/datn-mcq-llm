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

    private List<Map<String, Object>> run(String file) throws IOException {
        return jdbc.queryForList(Files.readString(DIR.resolve(file), StandardCharsets.UTF_8));
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

    @Test
    void loaiDeCuaTaiKhoanNhomPhatTrienBangDanhSachEmail() throws Exception {
        sampleData();
        String sql = Files.readString(DIR.resolve("01-ket-cuc-cau-llm.sql"), StandardCharsets.UTF_8);
        String email = jdbc.queryForObject("SELECT email FROM users WHERE id = ?", String.class, teacher);
        String filtered = sql.replace("ARRAY[]::text[]", "ARRAY['" + email + "']::text[]");
        assertTrue(!filtered.equals(sql), "file phải có chỗ điền email như hướng dẫn trong README");
        assertEquals(List.of(), jdbc.queryForList(filtered), "toàn bộ đề của tài khoản nhóm bị loại");
    }

    @Test
    void coMotCauLenhSelectDocLapMoiFile() throws IOException {
        try (var files = Files.list(DIR)) {
            List<Path> sqlFiles = files.filter(p -> p.toString().endsWith(".sql")).sorted().toList();
            assertEquals(5, sqlFiles.size());
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
