package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.exam.export.PandocTestSupport;
import com.datn.mcq.llm.prompt.DistractorCheckPrompt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phương án nhiễu kèm lỗi sai của học sinh (13/11, PA1, docs/design/phuong-an-nhieu-loi-sai.md): lưu lỗi khi sinh câu, Solver
 * ghép mù trong cùng lượt gọi, {@code distractorErrors} và cờ thông tin {@code distractor_mismatch} trên API xem đề (không
 * bật "cần xem kỹ"), Giữ câu chép sang ngân hàng, bản sửa (H-4) trước và sau job kiểm lại, xuất Word, CHECK của V29. HTTP
 * thật trên DB test; LLM sinh câu và Solver giả (không gọi mạng).
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ExamDistractorErrorsTest extends ExamFlowTestBase {

    /** Lỗi của câu đáp án B; chữ ở B (phương án đúng) phải bị parser bỏ và không bao giờ gửi Solver. */
    private static final String ERRORS = "{\"A\": \"Nhầm số mũ thành $1$\", \"B\": \"CHỮ Ở ĐÁP ÁN ĐÚNG\","
            + " \"C\": \"Quên hệ số\", \"D\": \"Cộng thêm $2$ vào số mũ\"}";

    /** Lỗi 1 (A) khớp, lỗi 2 (C) không ra phương án nào, lỗi 3 (D) không rõ. */
    private static final String CHECKS = "[{\"error\": 1, \"result\": \"A\", \"reason\": \"ra A\"},"
            + " {\"error\": 2, \"result\": \"none\", \"reason\": \"không ra\"},"
            + " {\"error\": 3, \"result\": \"unclear\", \"reason\": \"mơ hồ\"}]";

    private int examId;
    private int slot;

    private void readyExam() throws Exception {
        generator.distractorErrors = ERRORS;
        SOLVER.mcqOptions = "B";
        SOLVER.errorChecks = CHECKS;
        int matrixId = createMatrix(mcqCell("Đa thức", null, 1));
        examId = build(matrixId).get("examId").asInt();
        settle(examId);
        slot = slotId(examId, cellId(matrixId, 1), 1);
    }

    private int questionOf(int slotId) {
        return count("SELECT generated_question_id FROM exam_slot WHERE id = ?", slotId);
    }

    private JsonNode item() throws Exception {
        JsonNode view = json(perform(get("/api/exams/" + examId)).andExpect(status().isOk()));
        for (JsonNode part : view.get("parts")) {
            for (JsonNode it : part.get("items")) {
                if (it.get("slotId").asInt() == slot) {
                    return it;
                }
            }
        }
        throw new AssertionError("không có chỗ " + slot);
    }

    private static String checks(JsonNode item) {
        List<String> out = new ArrayList<>();
        item.get("distractorErrors").fields().forEachRemaining(e -> out.add(e.getKey() + ":"
                + e.getValue().get("check").asText()));
        return String.join("|", out);
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }

    private String stored(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }

    private ObjectNode editBody(int base) {
        Map<String, Object> q = jdbc.queryForMap("SELECT * FROM generated_question WHERE id = ?", base);
        ObjectNode n = objectMapper.createObjectNode();
        n.put("baseQuestionId", base);
        n.put("questionText", (String) q.get("question_text"));
        n.put("optionA", (String) q.get("option_a"));
        n.put("optionB", (String) q.get("option_b"));
        n.put("optionC", (String) q.get("option_c"));
        n.put("optionD", (String) q.get("option_d"));
        n.put("correctOption", String.valueOf(q.get("correct_option")).strip());
        n.put("explanation", (String) q.get("explanation"));
        return n;
    }

    private void edit(ObjectNode body) throws Exception {
        perform(put("/api/exams/" + examId + "/slots/" + slot + "/question").contentType(MediaType.APPLICATION_JSON)
                .content(body.toString())).andExpect(status().isOk());
    }

    @Test
    void sinhCauLuuLoiSolverGhepMuCungLuotGoiVaApiTraKetQua() throws Exception {
        readyExam();
        int q = questionOf(slot);
        assertEquals("{\"A\": \"Nhầm số mũ thành $1$\", \"B\": null, \"C\": \"Quên hệ số\", \"D\": \"Cộng thêm $2$ vào số mũ\"}",
                stored("SELECT distractor_errors::text FROM generated_question WHERE id = ?", q));
        assertEquals("{\"A\": \"match\", \"C\": \"mismatch\", \"D\": \"unknown\"}",
                stored("SELECT distractor_checks::text FROM quality_check_result WHERE question_id = ?"
                        + " AND check_type = 'answer_check'", q));
        // v7: lượt giải (đúng v4, không thấy lỗi) rồi lượt 2 ghép lỗi.
        assertEquals(2, SOLVER.calls, "lượt giải + lượt ghép lỗi");
        assertFalse(SOLVER.prompts.get(0).contains("LỖI_HỌC_SINH"), "lượt giải không thấy lỗi");
        assertEquals(com.datn.mcq.qc.checker.AnswerSolverChecker.SOLVER_RESPONSE_SCHEMA_JSON, SOLVER.schemas.get(0));
        String prompt = SOLVER.prompts.get(1);
        assertTrue(prompt.contains("<<<LỖI_HỌC_SINH\n1. Nhầm số mũ thành $1$\n2. Quên hệ số\n3. Cộng thêm $2$ vào số mũ\n"
                + "HẾT_LỖI>>>"), prompt);
        assertFalse(prompt.contains("CHỮ Ở ĐÁP ÁN ĐÚNG"));
        assertEquals(DistractorCheckPrompt.SECOND_PASS_SCHEMA_ERRORS, SOLVER.schemas.get(1));
        assertTrue(stored("SELECT solver_reasoning FROM quality_check_result WHERE question_id = ?"
                + " AND check_type = 'answer_check'", q).contains(DistractorCheckPrompt.NOTES_PREFIX));

        JsonNode it = item();
        assertEquals("A:match|C:mismatch|D:unknown", checks(it));
        assertEquals("Nhầm số mũ thành $1$", it.get("distractorErrors").get("A").get("text").asText());
        assertFalse(it.get("distractorErrors").has("B"), "không có khóa của phương án đúng");
        assertEquals(List.of("distractor_mismatch: C"), texts(it.get("infoReasons")));
        assertFalse(it.get("needsReview").asBoolean(), "không khớp chỉ là thông tin");
        assertEquals(List.of(), texts(it.get("reviewReasons")));
        assertEquals("passed", it.get("qcState").asText());
        assertEquals("qc_passed", stored("SELECT status FROM generated_question WHERE id = ?", q));
        JsonNode view = json(perform(get("/api/exams/" + examId)).andExpect(status().isOk()));
        assertEquals(0, view.get("needsReviewCount").asInt());
    }

    @Test
    void cauKhongCoLoiKhongCoTruongVaSolverNhuCu() throws Exception {
        SOLVER.mcqOptions = "B";
        int matrixId = createMatrix(mcqCell("Đa thức", null, 1));
        examId = build(matrixId).get("examId").asInt();
        settle(examId);
        slot = slotId(examId, cellId(matrixId, 1), 1);
        JsonNode it = item();
        assertFalse(it.has("distractorErrors"), "vắng khi không có lỗi");
        assertEquals(List.of(), texts(it.get("infoReasons")));
        assertFalse(SOLVER.prompts.get(0).contains(DistractorCheckPrompt.SOLVER_HEADER));
        assertNull(stored("SELECT distractor_errors::text FROM generated_question WHERE id = ?", questionOf(slot)));
        assertTrue(stored("SELECT warnings::text FROM quality_check_result WHERE question_id = ? AND check_type = 'format'",
                questionOf(slot)).contains("thiếu distractor_errors"), "thiếu trường: cảnh báo tự sửa");
        assertFalse(it.get("needsReview").asBoolean(), "cảnh báo tự sửa không bật cờ");
    }

    @Test
    void giuCauChepLoiVaKetQuaSangNganHangKhongLamCauRiengTu() throws Exception {
        readyExam();
        int q = questionOf(slot);
        perform(post("/api/exams/" + examId + "/slots/" + slot + "/keep")).andExpect(status().isOk());
        Map<String, Object> bank = jdbc.queryForMap("SELECT distractor_errors::text AS e, distractor_checks::text AS c,"
                + " visible_to_user_id FROM bank_item WHERE source_question_id = ?", q);
        assertEquals("{\"A\": \"Nhầm số mũ thành $1$\", \"C\": \"Quên hệ số\", \"D\": \"Cộng thêm $2$ vào số mũ\"}",
                bank.get("e"));
        assertEquals("{\"A\": \"match\", \"C\": \"mismatch\", \"D\": \"unknown\"}", bank.get("c"));
        assertNull(bank.get("visible_to_user_id"), "không khớp không làm câu vào ngân hàng riêng");
        JsonNode it = item();
        assertEquals("A:match|C:mismatch|D:unknown", checks(it), "câu đã Giữ đọc từ bank_item");
        // API ngân hàng chưa trả trường mới
        String bankJson = perform(get("/api/bank-items")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertFalse(bankJson.contains("distractor"), bankJson);
    }

    @Test
    void banSuaMotPhuongAnTrongLucChoChiLoiDoChuaKiemSauJobTinhLai() throws Exception {
        readyExam();
        int base = questionOf(slot);
        ObjectNode body = editBody(base);
        body.put("optionC", "$7$");
        edit(body);
        int version = questionOf(slot);
        assertTrue(version != base);
        assertEquals(stored("SELECT distractor_errors::text FROM generated_question WHERE id = ?", base),
                stored("SELECT distractor_errors::text FROM generated_question WHERE id = ?", version), "chép chữ lỗi");
        JsonNode pending = item();
        assertEquals("checking", pending.get("qcState").asText());
        assertEquals("A:match|C:unchecked|D:unknown", checks(pending), "trong lúc chờ: chỉ lỗi của C chưa kiểm");
        assertEquals(List.of(), texts(pending.get("infoReasons")), "C không còn kết quả không khớp");
        assertEquals(409, perform(post("/api/exams/" + examId + "/slots/" + slot + "/keep")).andReturn().getResponse()
                .getStatus(), "Giữ khi đang kiểm vẫn bị chặn");

        SOLVER.errorChecks = "[{\"error\": 1, \"result\": \"C\", \"reason\": \"r\"}, {\"error\": 2, \"result\": \"C\","
                + " \"reason\": \"r\"}, {\"error\": 3, \"result\": \"D\", \"reason\": \"r\"}]";
        int before = SOLVER.calls;
        processAll();
        assertEquals(before + 2, SOLVER.calls, "job kiểm lại: lượt giải + lượt ghép lỗi");
        assertTrue(SOLVER.prompts.get(SOLVER.prompts.size() - 1).contains("1. Nhầm số mũ thành $1$\n2. Quên hệ số\n"
                + "3. Cộng thêm $2$ vào số mũ\n"), "job gửi lại MỌI lỗi còn chữ");
        JsonNode done = item();
        assertEquals("A:mismatch|C:match|D:match", checks(done), "kết quả mới thay hẳn kết quả tạm");
        assertEquals(List.of("distractor_mismatch: A"), texts(done.get("infoReasons")));
    }

    @Test
    void banSuaCauDanThiMoiLoiChuaKiemJobThatBaiGiuKetQuaTamVaGiuChepDungTrangThai() throws Exception {
        readyExam();
        ObjectNode body = editBody(questionOf(slot));
        body.put("questionText", "Bậc của đơn thức $5x^7$ là bao nhiêu?");
        SOLVER.fail = true;
        edit(body);
        assertEquals("A:unchecked|C:unchecked|D:unchecked", checks(item()));
        processAll();
        assertEquals("failed", stored("SELECT status FROM generation_job WHERE question_id = ? AND job_type = 'answer_check'"
                + " ORDER BY id DESC LIMIT 1", questionOf(slot)));
        JsonNode failed = item();
        assertEquals("A:unchecked|C:unchecked|D:unchecked", checks(failed), "job thất bại: giữ kết quả tạm");
        SOLVER.fail = false;
        perform(post("/api/exams/" + examId + "/slots/" + slot + "/keep")).andExpect(status().isOk());
        Map<String, Object> bank = jdbc.queryForMap("SELECT distractor_errors::text AS e, distractor_checks::text AS c"
                + " FROM bank_item WHERE source_question_id = ?", questionOf(slot));
        assertTrue(String.valueOf(bank.get("e")).contains("Quên hệ số"));
        assertNull(bank.get("c"), "chép đúng trạng thái đang hiển thị: mọi lỗi chưa kiểm");
        assertEquals("A:unchecked|C:unchecked|D:unchecked", checks(item()));
    }

    @Test
    void banSuaDoiDapAnThiLoiOPhuongAnDungMoiKhongHienVaChiSuaLoiGiaiChepKetQua() throws Exception {
        readyExam();
        int base = questionOf(slot);
        ObjectNode explanationOnly = editBody(base);
        explanationOnly.put("explanation", "Lời giải đã sửa.");
        edit(explanationOnly);
        int v1 = questionOf(slot);
        assertEquals("{\"A\": \"match\", \"C\": \"mismatch\", \"D\": \"unknown\"}",
                stored("SELECT distractor_checks::text FROM quality_check_result WHERE question_id = ?"
                        + " AND check_type = 'answer_check'", v1), "chỉ sửa lời giải: chép kết quả");
        assertEquals("A:match|C:mismatch|D:unknown", checks(item()));

        ObjectNode answer = editBody(v1);
        answer.put("optionB", "$99$");
        answer.put("optionC", (String) jdbc.queryForMap("SELECT option_b FROM generated_question WHERE id = ?", v1)
                .get("option_b"));
        answer.put("correctOption", "C");
        edit(answer);
        assertEquals("A:unchecked|D:unchecked", checks(item()), "lỗi ghi ở C (nay là đáp án đúng) không hiển thị");
        SOLVER.mcqOptions = "C";
        SOLVER.errorChecks = "[{\"error\": 1, \"result\": \"A\", \"reason\": \"r\"}, {\"error\": 2, \"result\": \"D\","
                + " \"reason\": \"r\"}]";
        processAll();
        String last = SOLVER.prompts.get(SOLVER.prompts.size() - 1);
        assertFalse(last.contains("Quên hệ số"), "lỗi ở đáp án đúng mới không gửi Solver");
        assertEquals("A:match|D:match", checks(item()));
    }

    @Test
    void xuatWordCoDongPhanTichEquationVaMathType() throws Exception {
        readyExam();
        String base = "/api/exams/" + examId + "/export/docx";
        assertEquals(400, perform(get(base + "?includeDistractorNotes=yes")).andReturn().getResponse().getStatus());
        if (PandocTestSupport.find().isEmpty()) {
            return;
        }
        for (String eq : List.of("omml", "mathtype")) {
            var on = perform(get(base + "?equation=" + eq)).andReturn().getResponse();
            assertEquals(200, on.getStatus(), on.getContentAsString(StandardCharsets.UTF_8));
            String xml = PandocTestSupport.documentXml(on.getContentAsByteArray());
            assertTrue(xml.contains("Phân tích phương án nhiễu:"), eq);
            assertTrue(xml.indexOf("Phân tích phương án nhiễu:") > xml.indexOf("LỜI GIẢI CHI TIẾT"), "chỉ ở phần lời giải");
            assertFalse(xml.contains("Quên hệ số"), "không in lỗi không khớp");
            if (eq.equals("mathtype")) {
                assertEquals("0", on.getHeader("X-Equation-Kept-Omml"), "công thức trong lỗi cũng thành MathType");
                assertEquals("0", on.getHeader("X-Equation-Failed"));
            }
            var off = perform(get(base + "?equation=" + eq + "&includeDistractorNotes=false")).andReturn().getResponse();
            assertEquals(200, off.getStatus());
            assertFalse(PandocTestSupport.documentXml(off.getContentAsByteArray()).contains("Phân tích phương án nhiễu"));
        }
    }

    @Test
    void checkCuaMigrationV29() throws Exception {
        readyExam();
        int q = questionOf(slot);
        DataIntegrityViolationException notObject = assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE generated_question SET distractor_errors = '[1]'::jsonb WHERE id = ?", q), "phải là đối tượng");
        assertTrue(notObject.getMessage().contains("chk_gq_distractor_errors"), notObject.getMessage());
        DataIntegrityViolationException formatRow = assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE quality_check_result SET distractor_checks = '{}'::jsonb WHERE question_id = ?"
                        + " AND check_type = 'format'", q), "chỉ dòng answer_check");
        assertTrue(formatRow.getMessage().contains("chk_qcr_distractor_checks"), formatRow.getMessage());
        DataIntegrityViolationException bankArray = assertThrows(DataIntegrityViolationException.class, () -> {
            perform(post("/api/exams/" + examId + "/slots/" + slot + "/keep")).andExpect(status().isOk());
            jdbc.update("UPDATE bank_item SET distractor_checks = '\"x\"'::jsonb WHERE source_question_id = ?", q);
        });
        assertTrue(bankArray.getMessage().contains("chk_bi_distractor_checks"), bankArray.getMessage());
        // chỉ câu mcq: điều kiện có trong định nghĩa ràng buộc của cả ba bảng
        for (String c : List.of("chk_gq_distractor_errors", "chk_bi_distractor_errors", "chk_bi_distractor_checks",
                "chk_qcr_distractor_checks")) {
            assertTrue(stored("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?", c)
                    .contains("'mcq'"), c);
        }
        // dòng cũ là NULL
        assertEquals(0, count("SELECT count(*) FROM bank_item WHERE distractor_errors IS NULL AND distractor_checks"
                + " IS NOT NULL"));
    }
}
