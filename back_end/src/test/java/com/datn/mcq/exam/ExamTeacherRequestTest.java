package com.datn.mcq.exam;

import com.datn.mcq.common.util.SimpleJson;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.enums.TeacherRequest;
import com.datn.mcq.generation.service.QcRerunService;
import com.datn.mcq.llm.prompt.TeacherRequestPrompt;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;

import java.sql.Array;
import java.util.ArrayList;
import java.util.Arrays;
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
 * Yêu cầu của giáo viên (12/11, PR B, docs/design/kiem-soat-do-kho.md mục 4.8): chip yêu cầu ở mức đề, ô và Đổi câu (thứ tự
 * ưu tiên Đổi câu &gt; ô &gt; đề, mức cụ thể hơn THAY HẲN mức dưới), kiểm {@code request_check} trong cùng lượt gọi Solver,
 * cờ thông tin {@code infoReasons} (không bật "cần xem kỹ"), {@code requests} và {@code inheritedRequests} của câu, migration
 * V28. HTTP thật trên DB test; LLM sinh câu và Solver giả (không gọi mạng).
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ExamTeacherRequestTest extends ExamFlowTestBase {

    @Autowired
    QcRerunService qcRerunService;

    private static final String TF_CELL = "{\"part\": \"II\", \"questionType\": \"tf\", \"statementDifficulties\": [\"easy\","
            + " \"easy\", \"medium\", \"hard\"], \"questionCount\": 1, \"topic\": \"Hằng đẳng thức\","
            + " \"learningOutcomes\": {\"easy\": \"Nhận biết.\", \"medium\": \"Khai triển.\", \"hard\": \"Vận dụng.\"}}";
    private static final String CELL_TEXT = "Ô: dùng bối cảnh nông trại";
    private static final String EXTRA_PREFIX = "Yêu cầu thêm của giáo viên cho cả bài hoặc ô này";
    private static final String TEACHER_PREFIX = "Yêu cầu bổ sung của giáo viên";

    // ---------- tiện ích ----------

    private static String codes(String... codes) {
        List<String> quoted = new ArrayList<>();
        for (String c : codes) {
            quoted.add(SimpleJson.quote(c));
        }
        return "[" + String.join(", ", quoted) + "]";
    }

    /** Body tạo đề: {@code examCodes} null là không gửi; {@code cells} là mảng JSON (null là không gửi). */
    private String buildBody(int matrixId, String examCodes, String cells) {
        return "{\"matrixId\": " + matrixId + (examCodes == null ? "" : ", \"requestCodes\": " + examCodes)
                + (cells == null ? "" : ", \"cells\": " + cells) + "}";
    }

    private int buildExam(int matrixId, String examCodes, String cells) throws Exception {
        return json(postJson("/api/exams/build", buildBody(matrixId, examCodes, cells)).andExpect(status().isOk()))
                .get("examId").asInt();
    }

    private static String cellInput(int cellId, String codes, String text) {
        return "{\"cellId\": " + cellId + (codes == null ? "" : ", \"requestCodes\": " + codes)
                + (text == null ? "" : ", \"extraInstruction\": " + SimpleJson.quote(text)) + "}";
    }

    private org.springframework.test.web.servlet.ResultActions replace(int examId, int slot, String body) throws Exception {
        return postJson("/api/exams/" + examId + "/slots/" + slot + "/replace", body);
    }

    private List<String> array(String sql, Object... args) {
        Object raw = jdbc.queryForObject(sql, Object.class, args);
        if (raw == null) {
            return null;
        }
        try {
            Object[] values = raw instanceof Array a ? (Object[]) a.getArray() : (Object[]) raw;
            return Arrays.stream(values).map(String::valueOf).toList();
        } catch (java.sql.SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private Map<String, Object> latestJob(int slotId) {
        return jdbc.queryForMap("SELECT id, request_text, request_scope FROM generation_job WHERE exam_slot_id = ?"
                + " AND job_type = 'generate' ORDER BY id DESC LIMIT 1", slotId);
    }

    private List<String> latestJobCodes(int slotId) {
        return array("SELECT request_codes FROM generation_job WHERE exam_slot_id = ? AND job_type = 'generate'"
                + " ORDER BY id DESC LIMIT 1", slotId);
    }

    private int questionOf(int slotId) {
        return count("SELECT generated_question_id FROM exam_slot WHERE id = ?", slotId);
    }

    private JsonNode item(int examId, int slotId) throws Exception {
        JsonNode view = json(perform(get("/api/exams/" + examId)).andExpect(status().isOk()));
        for (JsonNode part : view.get("parts")) {
            for (JsonNode it : part.get("items")) {
                if (it.get("slotId").asInt() == slotId) {
                    return it;
                }
            }
        }
        throw new AssertionError("không có chỗ " + slotId);
    }

    private String lastMcqPrompt() {
        return generator.mcqPrompts.get(generator.mcqPrompts.size() - 1);
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }

    private Map<String, Integer> writeCounts() {
        return Map.of(
                "exam", count("SELECT count(*) FROM exam"),
                "context", count("SELECT count(*) FROM exam_generation_context"),
                "action", count("SELECT count(*) FROM exam_slot_action"),
                "job", count("SELECT count(*) FROM generation_job"),
                "quota", count("SELECT coalesce(sum(requested), 0) FROM generation_quota"),
                "matrix", count("SELECT count(*) FROM exam_matrix"));
    }

    // ---------- mức đề, mức ô: lọc theo dạng câu (test 2, 6, 12) ----------

    @Test
    void maKhongHopDangOMucDeVanNhanVaChiApChoCauHopDang() throws Exception {
        int matrixId = createMatrix(mcqCell("Đa thức", null, 1), TF_CELL);
        int examId = buildExam(matrixId, codes("tf_shared_context", "real_context"), null);
        int mcqSlot = slotId(examId, cellId(matrixId, 1), 1);
        int tfSlot = slotId(examId, cellId(matrixId, 2), 1);
        assertEquals(List.of("real_context"), latestJobCodes(mcqSlot), "mcq bỏ mã chỉ cho tf");
        assertEquals(List.of("real_context", "tf_shared_context"), latestJobCodes(tfSlot));
        assertEquals("exam", latestJob(mcqSlot).get("request_scope"));
        settle(examId);
        assertEquals(List.of("real_context"), array("SELECT request_codes FROM generated_question WHERE id = ?",
                questionOf(mcqSlot)), "câu mcq không lưu mã không hợp dạng");
        assertEquals(List.of("real_context", "tf_shared_context"), array(
                "SELECT request_codes FROM generated_question WHERE id = ?", questionOf(tfSlot)));
        String mcqPrompt = generator.mcqPrompts.get(0);
        assertTrue(mcqPrompt.contains(TeacherRequest.REAL_CONTEXT.promptLine()), mcqPrompt);
        assertFalse(mcqPrompt.contains(TeacherRequest.TF_SHARED_CONTEXT.promptLine()));
        assertTrue(generator.prompts.stream().anyMatch(p -> p.contains(TeacherRequest.TF_SHARED_CONTEXT.promptLine())),
                "prompt tf có mã của tf");
        // ô chỉ có mã không hợp dạng: ô vẫn được chọn (thay hẳn đề) nhưng câu mcq không có yêu cầu
        int exam2 = buildExam(matrixId, codes("extremum"), "[" + cellInput(cellId(matrixId, 1),
                codes("tf_shared_context"), null) + "]");
        int slot2 = slotId(exam2, cellId(matrixId, 1), 1);
        assertNull(latestJobCodes(slot2));
        assertNull(latestJob(slot2).get("request_scope"));
    }

    @Test
    void maHopLeTrungLanHoaThuongVaRong() throws Exception {
        int matrixId = createMatrix(mcqCell("Đa thức", null, 1));
        int examId = buildExam(matrixId, codes(" Real_Context ", "real_context", "MULTI_STEP"), null);
        assertEquals(List.of("real_context", "multi_step"), array("SELECT request_codes FROM exam_generation_context"
                + " WHERE exam_id = ? AND cell_id IS NULL", examId), "gộp trùng, chuẩn hóa, theo thứ tự danh mục");
        int empty = buildExam(matrixId, "[]", "[" + cellInput(cellId(matrixId, 1), "[]", null) + "]");
        assertEquals(0, count("SELECT count(*) FROM exam_generation_context WHERE exam_id = ?", empty),
                "mảng rỗng là không có yêu cầu");
        assertNull(latestJob(slotId(empty, cellId(matrixId, 1), 1)).get("request_scope"));
        int nul = buildExam(matrixId, "null", null);
        assertEquals(0, count("SELECT count(*) FROM exam_generation_context WHERE exam_id = ?", nul));
        // theo bài học
        JsonNode lesson = json(postJson("/api/exams/build-by-lesson", "{\"subjectId\": " + subject + ", \"gradeLevel\": 8,"
                + " \"lesson\": \"Bài 1\", \"requestCodes\": [\"EXTREMUM\"], \"cells\": [{\"questionType\": \"mcq\","
                + " \"difficulty\": \"easy\", \"questionCount\": 1}]}").andExpect(status().isOk()));
        int lessonExam = lesson.get("examId").asInt();
        assertEquals(List.of("extremum"), array("SELECT request_codes FROM exam_generation_context WHERE exam_id = ?"
                + " AND cell_id IS NULL", lessonExam));
        settle(lessonExam);
        assertTrue(generator.mcqPrompts.stream().anyMatch(p -> p.contains(TeacherRequest.EXTREMUM.promptLine())));
    }

    @Test
    void maLaTra400VaKhongGhiGi() throws Exception {
        int matrixId = createMatrix(mcqCell("Đa thức", null, 1));
        int examId = buildExam(matrixId, null, null);
        settle(examId);
        int slot = slotId(examId, cellId(matrixId, 1), 1);
        Map<String, Integer> before = writeCounts();
        for (String body : List.of(buildBody(matrixId, codes("real_context", "funny"), null),
                buildBody(matrixId, "[null]", null),
                buildBody(matrixId, null, "[" + cellInput(cellId(matrixId, 1), codes("bogus"), "x") + "]"))) {
            JsonNode error = json(postJson("/api/exams/build", body).andExpect(status().isBadRequest()));
            assertEquals("INVALID_REQUEST", error.get("code").asText(), body);
        }
        JsonNode lessonError = json(postJson("/api/exams/build-by-lesson", "{\"subjectId\": " + subject
                + ", \"gradeLevel\": 8, \"lesson\": \"Bài 1\", \"requestCodes\": [\"bogus\"], \"cells\": ["
                + "{\"questionType\": \"mcq\", \"difficulty\": \"easy\", \"questionCount\": 1}]}")
                .andExpect(status().isBadRequest()));
        assertEquals("INVALID_REQUEST", lessonError.get("code").asText());
        JsonNode replaceError = json(replace(examId, slot, "{\"requestCodes\": [\"bogus\"]}")
                .andExpect(status().isBadRequest()));
        assertEquals("INVALID_REQUEST", replaceError.get("code").asText());
        assertEquals(before, writeCounts(), "không ghi đề, ngữ cảnh, nhật ký, job, lượt sinh, ma trận ẩn");
        assertEquals("ready", slotState(slot));
    }

    // ---------- Đổi câu (test 3, 5, 8, 9, 11, 14, 15) ----------

    @Test
    void doiCauKhongHopDangCuaChoTra400ThongBaoTiengVietVaKhongGhiGi() throws Exception {
        int matrixId = createMatrix(mcqCell("Đa thức", null, 1));
        int examId = buildExam(matrixId, null, null);
        settle(examId);
        int slot = slotId(examId, cellId(matrixId, 1), 1);
        Map<String, Object> slotBefore = jdbc.queryForMap("SELECT * FROM exam_slot WHERE id = ?", slot);
        Map<String, Integer> before = writeCounts();
        JsonNode error = json(replace(examId, slot, "{\"requestCodes\": [\"real_context\", \"tf_shared_context\"]}")
                .andExpect(status().isBadRequest()));
        assertEquals("INVALID_REQUEST", error.get("code").asText());
        assertEquals("Yêu cầu \"Đúng/Sai cùng một tình huống\" không áp dụng cho câu Trắc nghiệm.",
                error.get("error").asText());
        assertEquals(before, writeCounts(), "không ghi nhật ký, job, không trừ lượt sinh");
        assertEquals(slotBefore, jdbc.queryForMap("SELECT * FROM exam_slot WHERE id = ?", slot), "chỗ giữ nguyên");
    }

    @Test
    void doiCauLayNganHangKemMaTra400() throws Exception {
        int matrixId = createMatrix(mcqCell("Đa thức", null, 1));
        int examId = buildExam(matrixId, null, null);
        settle(examId);
        int slot = slotId(examId, cellId(matrixId, 1), 1);
        Map<String, Integer> before = writeCounts();
        JsonNode error = json(replace(examId, slot, "{\"source\": \"bank\", \"requestCodes\": [\"real_context\"]}")
                .andExpect(status().isBadRequest()));
        assertEquals("INVALID_REQUEST", error.get("code").asText());
        assertEquals(before, writeCounts());
    }

    @Test
    void doiCauCoYeuCauThayHanYeuCauCuaOVaDeVaGhiNhatKy() throws Exception {
        int matrixId = createMatrix(mcqCell("Đa thức", null, 1));
        int cell = cellId(matrixId, 1);
        int examId = buildExam(matrixId, codes("extremum"), "[" + cellInput(cell, codes("real_context"), CELL_TEXT) + "]");
        int slot = slotId(examId, cell, 1);
        // ô có yêu cầu thì không chứa yêu cầu của đề
        settle(examId);
        String cellPrompt = generator.mcqPrompts.get(0);
        assertTrue(cellPrompt.contains(TeacherRequest.REAL_CONTEXT.promptLine()));
        assertTrue(cellPrompt.contains(CELL_TEXT));
        assertFalse(cellPrompt.contains(TeacherRequest.EXTREMUM.promptLine()), "ô thay hẳn đề");
        assertEquals("cell", latestJob(slot).get("request_scope"));
        assertEquals(CELL_TEXT, latestJob(slot).get("request_text"));

        generator.mcqPrompts.clear();
        replace(examId, slot, "{\"requestCodes\": [\"multi_step\"], \"instruction\": \"Dùng tiền Việt Nam\"}")
                .andExpect(status().isOk());
        assertEquals(List.of("multi_step"), array("SELECT request_codes FROM exam_slot_action WHERE slot_id = ?"
                + " ORDER BY id DESC LIMIT 1", slot), "nhật ký lưu mã");
        assertEquals("replace", latestJob(slot).get("request_scope"));
        assertEquals(List.of("multi_step"), latestJobCodes(slot));
        assertEquals("Dùng tiền Việt Nam", latestJob(slot).get("request_text"));
        settle(examId);
        String p = lastMcqPrompt();
        assertTrue(p.contains(TeacherRequest.MULTI_STEP.promptLine()), p);
        assertTrue(p.contains("Dùng tiền Việt Nam"));
        assertFalse(p.contains(CELL_TEXT), "không còn văn bản của ô");
        assertFalse(p.contains(EXTRA_PREFIX), "không còn khối yêu cầu thêm của ô hay đề");
        assertFalse(p.contains(TeacherRequest.REAL_CONTEXT.promptLine()), "không còn mã của ô");
        assertFalse(p.contains(TeacherRequest.EXTREMUM.promptLine()), "không còn mã của đề");
        JsonNode it = item(examId, slot);
        assertEquals("replace", it.get("requests").get("scope").asText());
        assertEquals(List.of("multi_step"), texts(it.get("requests").get("codes")));
        assertEquals("Dùng tiền Việt Nam", it.get("requests").get("text").asText());

        // không gửi requestCodes, không instruction: dùng yêu cầu của ô
        generator.mcqPrompts.clear();
        replace(examId, slot, "{}").andExpect(status().isOk());
        assertNull(array("SELECT request_codes FROM exam_slot_action WHERE slot_id = ? ORDER BY id DESC LIMIT 1", slot),
                "không gửi thì nhật ký NULL");
        assertEquals("cell", latestJob(slot).get("request_scope"));
        settle(examId);
        assertTrue(lastMcqPrompt().contains(CELL_TEXT));
        assertTrue(lastMcqPrompt().contains(TeacherRequest.REAL_CONTEXT.promptLine()));
        assertEquals("cell", item(examId, slot).get("requests").get("scope").asText());

        // gửi [] và không instruction: câu mới không có yêu cầu nào
        generator.mcqPrompts.clear();
        replace(examId, slot, "{\"requestCodes\": []}").andExpect(status().isOk());
        assertEquals(List.of(), array("SELECT request_codes FROM exam_slot_action WHERE slot_id = ? ORDER BY id DESC"
                + " LIMIT 1", slot), "gửi [] thì nhật ký là mảng rỗng");
        assertEquals("replace", latestJob(slot).get("request_scope"));
        assertNull(latestJobCodes(slot));
        assertNull(latestJob(slot).get("request_text"));
        settle(examId);
        p = lastMcqPrompt();
        assertFalse(p.contains(TeacherRequestPrompt.GENERATION_HEADER), p);
        assertFalse(p.contains(CELL_TEXT));
        assertFalse(p.contains(EXTRA_PREFIX));
        JsonNode cleared = item(examId, slot);
        assertTrue(cleared.path("requests").isMissingNode(), "câu không có yêu cầu thì vắng requests");
        assertEquals("cell", cleared.get("inheritedRequests").get("scope").asText(), "hộp Đổi câu vẫn điền sẵn của ô");
        assertNull(array("SELECT request_codes FROM generated_question WHERE id = ?", questionOf(slot)));
    }

    @Test
    void sinhLaiTuDongGiuYeuCauCuaLuotDoi() throws Exception {
        int matrixId = createMatrix(mcqCell("Đa thức", null, 1));
        int cell = cellId(matrixId, 1);
        int examId = buildExam(matrixId, null, "[" + cellInput(cell, codes("extremum"), CELL_TEXT) + "]");
        settle(examId);
        int slot = slotId(examId, cell, 1);
        generator.brokenMcq = 1;
        generator.mcqPrompts.clear();
        replace(examId, slot, "{\"requestCodes\": [\"multi_step\"], \"instruction\": \"Dùng tiền Việt Nam\"}")
                .andExpect(status().isOk());
        settle(examId);
        assertEquals("ready", slotState(slot));
        assertTrue(generator.mcqPrompts.size() >= 2, "có lần sinh lại tự động");
        for (String p : generator.mcqPrompts) {
            assertTrue(p.contains(TeacherRequest.MULTI_STEP.promptLine()), p);
            assertTrue(p.contains("Dùng tiền Việt Nam"));
            assertFalse(p.contains(CELL_TEXT));
            assertFalse(p.contains(TeacherRequest.EXTREMUM.promptLine()));
        }
        assertTrue(count("SELECT count(*) FROM generation_job WHERE exam_slot_id = ? AND job_type = 'generate'"
                + " AND request_scope = 'replace'", slot) >= 2);
    }

    @Test
    void doiCauGuiLaiDungYeuCauThuaHuongKhongLapVanBan() throws Exception {
        int matrixId = createMatrix(mcqCell("Đa thức", null, 1));
        int cell = cellId(matrixId, 1);
        String longText = "a".repeat(990) + " cuối ô";
        assertEquals(997, longText.length());
        String full = longText + "!!!";
        assertEquals(1000, full.codePointCount(0, full.length()));
        int examId = buildExam(matrixId, null, "[" + cellInput(cell, codes("real_context"), full) + "]");
        settle(examId);
        int slot = slotId(examId, cell, 1);
        JsonNode inherited = item(examId, slot).get("inheritedRequests");
        assertEquals("cell", inherited.get("scope").asText());
        assertEquals(full, inherited.get("text").asText(), "nguyên văn, không cắt");
        assertEquals(List.of("real_context"), texts(inherited.get("codes")));

        // mô phỏng hộp Đổi câu điền sẵn, không sửa gì
        generator.mcqPrompts.clear();
        ObjectNode body = objectMapper.createObjectNode();
        body.set("requestCodes", inherited.get("codes"));
        body.put("instruction", inherited.get("text").asText());
        replace(examId, slot, body.toString()).andExpect(status().isOk());
        assertEquals(full, jdbc.queryForObject("SELECT instruction FROM exam_slot_action WHERE slot_id = ? ORDER BY id"
                + " DESC LIMIT 1", String.class, slot));
        assertEquals(List.of("real_context"), array("SELECT request_codes FROM exam_slot_action WHERE slot_id = ?"
                + " ORDER BY id DESC LIMIT 1", slot));
        settle(examId);
        String p = lastMcqPrompt();
        assertEquals(1, p.split("cuối ô!!!", -1).length - 1, "văn bản chỉ có một lần: " + p);
        assertTrue(p.indexOf("cuối ô!!!") > p.indexOf(TEACHER_PREFIX), "trong khối yêu cầu của lượt Đổi");
        assertFalse(p.contains(EXTRA_PREFIX), "không còn khối của ô");
        assertEquals(1, p.split(java.util.regex.Pattern.quote(TeacherRequest.REAL_CONTEXT.promptLine()), -1).length - 1);
        assertEquals("replace", item(examId, slot).get("requests").get("scope").asText());

        // gửi chip thừa hưởng nhưng xóa trống văn bản: câu mới chỉ có mã
        generator.mcqPrompts.clear();
        replace(examId, slot, "{\"requestCodes\": [\"real_context\"]}").andExpect(status().isOk());
        settle(examId);
        p = lastMcqPrompt();
        assertTrue(p.contains(TeacherRequest.REAL_CONTEXT.promptLine()));
        assertFalse(p.contains("cuối ô!!!"), "không còn văn bản của ô");
        JsonNode requests = item(examId, slot).get("requests");
        assertEquals(List.of("real_context"), texts(requests.get("codes")));
        assertTrue(requests.get("text").isNull());
        assertNull(jdbc.queryForObject("SELECT request_text FROM generated_question WHERE id = ?", String.class,
                questionOf(slot)));
    }

    // ---------- requests, inheritedRequests (test 10, 13) ----------

    @Test
    void inheritedRequestsTheoMucVaLocTheoDangCau() throws Exception {
        int matrixId = createMatrix(mcqCell("Đa thức", null, 1), mcqCell("Phân thức", null, 1), TF_CELL);
        int c1 = cellId(matrixId, 1);
        int c2 = cellId(matrixId, 2);
        int c3 = cellId(matrixId, 3);
        // đề chỉ có mã; ô 1 chỉ có văn bản (lấy cả bộ của ô, không trộn mã của đề); ô 2 không có gì (lấy của đề)
        int examId = buildExam(matrixId, codes("tf_shared_context", "extremum"),
                "[" + cellInput(c1, null, CELL_TEXT) + "]");
        settle(examId);
        JsonNode i1 = item(examId, slotId(examId, c1, 1)).get("inheritedRequests");
        assertEquals("cell", i1.get("scope").asText());
        assertEquals(List.of(), texts(i1.get("codes")));
        assertEquals(CELL_TEXT, i1.get("text").asText());
        JsonNode i2 = item(examId, slotId(examId, c2, 1)).get("inheritedRequests");
        assertEquals("exam", i2.get("scope").asText());
        assertEquals(List.of("extremum"), texts(i2.get("codes")), "lọc theo dạng câu của chỗ (mcq)");
        assertTrue(i2.get("text").isNull(), "mức chỉ có mã thì text null");
        JsonNode i3 = item(examId, slotId(examId, c3, 1)).get("inheritedRequests");
        assertEquals(List.of("extremum", "tf_shared_context"), texts(i3.get("codes")));
        JsonNode r2 = item(examId, slotId(examId, c2, 1)).get("requests");
        assertEquals("exam", r2.get("scope").asText());
        assertEquals(List.of("extremum"), texts(r2.get("codes")));
        JsonNode r1 = item(examId, slotId(examId, c1, 1)).get("requests");
        assertEquals("cell", r1.get("scope").asText());
        assertEquals(CELL_TEXT, r1.get("text").asText());

        int plain = buildExam(matrixId, null, null);
        settle(plain);
        JsonNode none = item(plain, slotId(plain, c1, 1));
        assertTrue(none.path("inheritedRequests").isMissingNode(), "ô và đề không có yêu cầu thì vắng trường");
        assertTrue(none.path("requests").isMissingNode());
        assertEquals(0, none.get("infoReasons").size());
    }

    // ---------- Solver: cùng lượt gọi, request_check (test 19, 20, 21, 22, 23, 27) ----------

    @Test
    void motLuotGoiSolverMoiCauCoYeuCauVaNotMetChiLaCoThongTin() throws Exception {
        SOLVER.requestCheck = "{\"verdict\": \"not_met\", \"reason\": \"Không có bối cảnh thực tế\"}";
        int matrixId = createMatrix(mcqCell("Đa thức", null, 1));
        int examId = buildExam(matrixId, codes("real_context"), null);
        settle(examId);
        int slot = slotId(examId, cellId(matrixId, 1), 1);
        int questions = count("SELECT count(*) FROM generated_question");
        assertEquals(1, questions);
        assertEquals(1, SOLVER.calls, "đúng một lượt gọi Solver cho câu có yêu cầu");
        assertTrue(SOLVER.prompts.get(0).contains(TeacherRequestPrompt.SOLVER_HEADER));
        assertTrue(SOLVER.schemas.get(0).contains("\"request_check\""));
        assertEquals(1, count("SELECT count(*) FROM generation_job WHERE exam_slot_id = ? AND job_type = 'generate'",
                slot), "not_met không kích hoạt sinh lại");
        Map<String, Object> row = jdbc.queryForMap("SELECT passed, request_verdict, request_reason FROM"
                + " quality_check_result WHERE question_id = ? AND check_type = 'answer_check'", questionOf(slot));
        assertEquals(Boolean.TRUE, row.get("passed"), "passed không đổi");
        assertEquals("not_met", row.get("request_verdict"));
        assertEquals("qc_passed", jdbc.queryForObject("SELECT status FROM generated_question WHERE id = ?", String.class,
                questionOf(slot)));

        JsonNode view = json(perform(get("/api/exams/" + examId)).andExpect(status().isOk()));
        assertEquals(0, view.get("needsReviewCount").asInt());
        JsonNode it = view.get("parts").get(0).get("items").get(0);
        assertEquals(List.of("request_not_met: Không có bối cảnh thực tế"), texts(it.get("infoReasons")));
        assertFalse(it.get("needsReview").asBoolean());
        assertEquals(0, it.get("reviewReasons").size());
        assertEquals("passed", it.get("qcState").asText());
        JsonNode list = json(perform(get("/api/exams")).andExpect(status().isOk()));
        assertEquals(0, list.get("items").get(0).get("needsReviewSlots").asInt());

        // Giữ câu chỉ có infoReasons: vào ngân hàng dùng chung
        perform(post("/api/exams/" + examId + "/slots/" + slot + "/keep")).andExpect(status().isOk());
        assertNull(jdbc.queryForObject("SELECT b.visible_to_user_id FROM bank_item b JOIN exam_slot s"
                + " ON s.bank_item_id = b.id WHERE s.id = ?", Integer.class, slot), "không thành riêng tư");
    }

    @Test
    void partialChoInfoReasonsVaCauKhongYeuCauDungPromptCu() throws Exception {
        SOLVER.requestCheck = "{\"verdict\": \"partial\", \"reason\": \"Chỉ một bước\"}";
        int matrixId = createMatrix(mcqCell("Đa thức", null, 1), mcqCell("Phân thức", null, 1));
        int examId = buildExam(matrixId, null, "[" + cellInput(cellId(matrixId, 1), codes("multi_step"), null) + "]");
        settle(examId);
        JsonNode withReq = item(examId, slotId(examId, cellId(matrixId, 1), 1));
        assertEquals(List.of("request_partial: Chỉ một bước"), texts(withReq.get("infoReasons")));
        JsonNode plain = item(examId, slotId(examId, cellId(matrixId, 2), 1));
        assertEquals(0, plain.get("infoReasons").size());
        assertEquals(2, SOLVER.calls);
        long plainPrompts = SOLVER.prompts.stream().filter(p -> !p.contains(TeacherRequestPrompt.SOLVER_HEADER)).count();
        assertEquals(1, plainPrompts, "câu không yêu cầu dùng prompt cũ");
        assertTrue(SOLVER.schemas.contains(com.datn.mcq.qc.checker.AnswerSolverChecker.SOLVER_RESPONSE_SCHEMA_JSON));
        assertNull(jdbc.queryForObject("SELECT request_verdict FROM quality_check_result WHERE question_id = ? AND"
                + " check_type = 'answer_check'", String.class, questionOf(slotId(examId, cellId(matrixId, 2), 1))));
    }

    @Test
    void requestCheckThieuThiKetQuaSolverVanLuuVerdictNull() throws Exception {
        SOLVER.requestCheck = null;
        int matrixId = createMatrix(mcqCell("Đa thức", null, 1));
        int examId = buildExam(matrixId, codes("real_context"), null);
        settle(examId);
        int slot = slotId(examId, cellId(matrixId, 1), 1);
        Map<String, Object> row = jdbc.queryForMap("SELECT passed, request_verdict FROM quality_check_result WHERE"
                + " question_id = ? AND check_type = 'answer_check'", questionOf(slot));
        assertEquals(Boolean.TRUE, row.get("passed"));
        assertNull(row.get("request_verdict"));
        assertEquals(0, item(examId, slot).get("infoReasons").size());
        // hỏng
        SOLVER.requestCheck = "\"met\"";
        replace(examId, slot, "{}").andExpect(status().isOk());
        settle(examId);
        assertNull(jdbc.queryForObject("SELECT request_verdict FROM quality_check_result WHERE question_id = ? AND"
                + " check_type = 'answer_check'", String.class, questionOf(slot)));
        assertEquals("ready", slotState(slot));
    }

    @Test
    void solverTfVaSaCungKiemYeuCau() throws Exception {
        SOLVER.requestCheck = "{\"verdict\": \"met\", \"reason\": \"ok\"}";
        SOLVER.saJudge = "7";
        int matrixId = createMatrix(TF_CELL);
        int examId = buildExam(matrixId, codes("tf_shared_context"), null);
        settle(examId);
        int q = questionOf(slotId(examId, cellId(matrixId, 1), 1));
        assertEquals("met", jdbc.queryForObject("SELECT request_verdict FROM quality_check_result WHERE question_id = ?"
                + " AND check_type = 'answer_check'", String.class, q));
        assertTrue(SOLVER.schemas.contains(TeacherRequestPrompt.TF_SOLVER_SCHEMA));
        assertEquals(0, item(examId, slotId(examId, cellId(matrixId, 1), 1)).get("infoReasons").size(), "met không cờ");
    }

    @Test
    void chayLaiQcVanKemYeuCau() throws Exception {
        SOLVER.fail = true;
        int matrixId = createMatrix(mcqCell("Đa thức", null, 1));
        int examId = buildExam(matrixId, codes("extremum"), null);
        processAll();
        int q = count("SELECT id FROM generated_question ORDER BY id DESC LIMIT 1");
        assertEquals(0, count("SELECT count(*) FROM quality_check_result WHERE question_id = ? AND"
                + " check_type = 'answer_check'", q));
        SOLVER.fail = false;
        SOLVER.prompts.clear();
        SOLVER.requestCheck = "{\"verdict\": \"partial\", \"reason\": \"x\"}";
        qcRerunService.rerun(q);
        assertTrue(SOLVER.prompts.get(0).contains(TeacherRequest.EXTREMUM.promptLine()), SOLVER.prompts.get(0));
        assertEquals("partial", jdbc.queryForObject("SELECT request_verdict FROM quality_check_result WHERE"
                + " question_id = ? AND check_type = 'answer_check'", String.class, q));
        assertTrue(examId > 0);
    }

    // ---------- câu sửa (test 26) ----------

    @Test
    void cauSuaChepYeuCauVaKetLuan() throws Exception {
        SOLVER.requestCheck = "{\"verdict\": \"not_met\", \"reason\": \"thiếu\"}";
        int matrixId = createMatrix(mcqCell("Đa thức", null, 1));
        int examId = buildExam(matrixId, null, "[" + cellInput(cellId(matrixId, 1), codes("real_context"), CELL_TEXT)
                + "]");
        settle(examId);
        int slot = slotId(examId, cellId(matrixId, 1), 1);
        int base = questionOf(slot);
        Map<String, Object> q = jdbc.queryForMap("SELECT * FROM generated_question WHERE id = ?", base);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("baseQuestionId", base);
        body.put("questionText", (String) q.get("question_text"));
        body.put("optionA", (String) q.get("option_a"));
        body.put("optionB", (String) q.get("option_b"));
        body.put("optionC", (String) q.get("option_c"));
        body.put("optionD", (String) q.get("option_d"));
        body.put("correctOption", String.valueOf(q.get("correct_option")).strip());
        body.put("explanation", "Lời giải đã sửa.");
        perform(put("/api/exams/" + examId + "/slots/" + slot + "/question").contentType(MediaType.APPLICATION_JSON)
                .content(body.toString())).andExpect(status().isOk());
        int version = questionOf(slot);
        assertTrue(version != base);
        assertEquals(List.of("real_context"), array("SELECT request_codes FROM generated_question WHERE id = ?", version));
        assertEquals(CELL_TEXT, jdbc.queryForObject("SELECT request_text FROM generated_question WHERE id = ?",
                String.class, version));
        assertEquals("not_met", jdbc.queryForObject("SELECT request_verdict FROM quality_check_result WHERE"
                + " question_id = ? AND check_type = 'answer_check'", String.class, version), "chép kết luận khi chỉ đổi lời giải");
        JsonNode it = item(examId, slot);
        assertEquals("cell", it.get("requests").get("scope").asText(), "scope lấy theo bản gốc");
        assertEquals(List.of("request_not_met: thiếu"), texts(it.get("infoReasons")));
    }

    // ---------- migration (test 25) ----------

    @Test
    void checkCuaMigrationTuChoiGiaTriLaVaDongCuLaNull() throws Exception {
        int matrixId = createMatrix(mcqCell("Đa thức", null, 1));
        int examId = buildExam(matrixId, null, null);
        settle(examId);
        int slot = slotId(examId, cellId(matrixId, 1), 1);
        int q = questionOf(slot);
        assertNull(latestJob(slot).get("request_scope"));
        assertNull(latestJobCodes(slot));
        assertNull(array("SELECT request_codes FROM generated_question WHERE id = ?", q));
        assertNull(jdbc.queryForObject("SELECT request_verdict FROM quality_check_result WHERE question_id = ? AND"
                + " check_type = 'answer_check'", String.class, q));
        int job = ((Number) latestJob(slot).get("id")).intValue();
        List<String> bad = List.of(
                "UPDATE generation_job SET request_codes = ARRAY['real_context', 'bogus'] WHERE id = " + job,
                "UPDATE generation_job SET request_scope = 'matrix' WHERE id = " + job,
                "UPDATE generated_question SET request_codes = ARRAY['bogus'] WHERE id = " + q,
                "UPDATE quality_check_result SET request_verdict = 'maybe' WHERE question_id = " + q,
                "INSERT INTO exam_generation_context (exam_id, request_codes) VALUES (" + examId + ", ARRAY['x'])",
                "INSERT INTO exam_slot_action (slot_id, action, request_codes) VALUES (" + slot + ", 'replace',"
                        + " ARRAY['nope'])",
                "INSERT INTO exam_slot_action (slot_id, action, request_codes) VALUES (" + slot + ", 'keep',"
                        + " ARRAY['real_context'])");
        for (String sql : bad) {
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(sql), sql);
        }
        jdbc.update("UPDATE generation_job SET request_codes = ARRAY['real_context', 'tf_shared_context'],"
                + " request_scope = 'exam' WHERE id = " + job);
        jdbc.update("UPDATE quality_check_result SET request_verdict = 'partial' WHERE question_id = " + q
                + " AND check_type = 'answer_check'");
    }
}
