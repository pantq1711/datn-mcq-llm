package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W1-4 bước 2 (D5-B): Đổi bằng câu ngân hàng ({@code {"source": "bank"}}). Lấy câu ngân hàng khớp ô (cùng tiêu chí khi tạo
 * đề, xếp hạng mềm), loại câu đã có trong đề và câu đã bị Đổi khỏi đề; hết câu thì 409 BANK_EXHAUSTED, chỗ giữ nguyên
 * (không tự sinh ngầm). Mặc định (không source) vẫn sinh mới.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ReplaceFromBankTest extends ExamFlowTestBase {

    private static final String TOPIC = "Đa thức";

    private String replaceUrl(int examId, int slot) {
        return "/api/exams/" + examId + "/slots/" + slot + "/replace";
    }

    private com.fasterxml.jackson.databind.JsonNode replaceBank(int examId, int slot) throws Exception {
        return json(postJson(replaceUrl(examId, slot), "{\"source\": \"bank\"}").andExpect(status().isOk()));
    }

    private void assertExhausted(int examId, int slot) throws Exception {
        JsonNode body = json(postJson(replaceUrl(examId, slot), "{\"source\": \"bank\"}")
                .andExpect(status().isConflict()));
        assertEquals("BANK_EXHAUSTED", body.get("code").asText());
    }

    /** Đề một ô mcq dễ {@code count} câu, không có câu ngân hàng nào lúc tạo: mọi chỗ được sinh rồi `ready`. */
    private int[] generatedExam(int count) throws Exception {
        int matrix = createMatrix(mcqCell(TOPIC, null, count));
        int examId = build(matrix).get("examId").asInt();
        settle(examId);
        int[] out = new int[count + 1];
        out[0] = examId;
        for (int i = 1; i <= count; i++) {
            out[i] = slotId(examId, cellId(matrix, 1), i);
            assertEquals("ready", slotState(out[i]));
        }
        return out;
    }

    private int jobs(int slot) {
        return count("SELECT count(*) FROM generation_job WHERE exam_slot_id = ?", slot);
    }

    private int actions(int slot) {
        return count("SELECT count(*) FROM exam_slot_action WHERE slot_id = ?", slot);
    }

    /** Một bank_item sao chép từ câu sinh ra {@code questionId} (như duyệt chấm mù), cùng đề và phương án. */
    private int bankCopyOf(int questionId) {
        int review = insert("INSERT INTO teacher_review (question_id, reviewer_id, review_purpose, reviewer_answer,"
                + " answer_correct, format_ok, difficulty_label, decision, final_option) VALUES (?, ?, 'production',"
                + " 'B', TRUE, TRUE, 'easy', 'approved_as_is', 'B')", questionId, teacher);
        return insert("INSERT INTO bank_item (source_question_id, review_id, question_text, option_a, option_b,"
                + " option_c, option_d, correct_option, difficulty) SELECT id, ?, question_text, option_a, option_b,"
                + " option_c, option_d, 'B', 'easy' FROM generated_question WHERE id = ?", review, questionId);
    }

    // ---------- lấy được câu ngân hàng ----------

    @Test
    void doiBangNganHangLayCauKhopOKhongTaoJob() throws Exception {
        int[] e = generatedExam(1);
        int oldQuestion = count("SELECT generated_question_id FROM exam_slot WHERE id = ?", e[1]);
        int item = bankMcq(TOPIC, "easy", "Câu ngân hàng thay thế");
        int jobsBefore = jobs(e[1]);

        JsonNode item1 = replaceBank(e[0], e[1]);
        assertEquals("ready", item1.get("state").asText());
        assertEquals("bank", item1.get("source").asText());
        assertEquals(item, item1.get("bankItemId").asInt());
        assertEquals("Câu ngân hàng thay thế", item1.get("questionText").asText());
        assertEquals(false, item1.get("needsReview").asBoolean());

        assertEquals("ready", slotState(e[1]));
        assertEquals(item, count("SELECT bank_item_id FROM exam_slot WHERE id = ?", e[1]));
        assertNull(jdbc.queryForObject("SELECT generated_question_id FROM exam_slot WHERE id = ?", Integer.class, e[1]));
        assertEquals(0, count("SELECT attempts FROM exam_slot WHERE id = ?", e[1]));
        assertEquals(jobsBefore, jobs(e[1]), "không sinh ngầm");
        JsonNode view = json(perform(get("/api/exams/" + e[0])).andExpect(status().isOk()));
        assertEquals("bank", view.get("parts").get(0).get("items").get(0).get("source").asText());

        assertEquals("bank", jdbc.queryForObject("SELECT replace_source FROM exam_slot_action WHERE slot_id = ? AND"
                + " action = 'replace'", String.class, e[1]));
        assertEquals(oldQuestion, count("SELECT generated_question_id FROM exam_slot_action WHERE slot_id = ?", e[1]),
                "nhật ký ghi câu bị đổi ra");
    }

    @Test
    void chiLayCauKhopTieuChiOMonDangMucChuDe() throws Exception {
        int[] e = generatedExam(1);
        bankMcq("Chủ đề khác", "easy", "Sai chủ đề");
        bankMcq(TOPIC, "hard", "Sai mức");
        assertExhausted(e[0], e[1]);
        int good = bankMcq(TOPIC, "easy", "Đúng ô");
        assertEquals(good, replaceBank(e[0], e[1]).get("bankItemId").asInt());
    }

    @Test
    void xepHangMemChonCauItDungTruoc() throws Exception {
        int[] e = generatedExam(1);
        int used = bankMcq(TOPIC, "easy", "Câu đã dùng ở đề khác");
        int fresh = bankMcq(TOPIC, "easy", "Câu chưa dùng");
        manualExamUsing(otherTeacher, "R1", "2026-03-01 08:00:00", used);
        assertEquals(fresh, replaceBank(e[0], e[1]).get("bankItemId").asInt());
    }

    @Test
    void doiBangNganHangTrenChoDaGiuChoCauKhac() throws Exception {
        int[] e = generatedExam(1);
        perform(post("/api/exams/" + e[0] + "/slots/" + e[1] + "/keep")).andExpect(status().isOk());
        int keptBank = count("SELECT bank_item_id FROM exam_slot WHERE id = ?", e[1]);
        int other = bankMcq(TOPIC, "easy", "Câu khác");
        JsonNode r = replaceBank(e[0], e[1]);
        assertEquals(other, r.get("bankItemId").asInt());
        assertEquals("ready", r.get("state").asText(), "chỗ đã Giữ rồi đổi: về ready, chưa Giữ");
        assertEquals(keptBank, count("SELECT bank_item_id FROM exam_slot_action WHERE slot_id = ? AND action ="
                + " 'replace'", e[1]), "câu đã Giữ vẫn ở ngân hàng và được ghi là câu bị đổi");
        assertEquals(1, count("SELECT count(*) FROM bank_item WHERE id = ?", keptBank));
    }

    // ---------- loại trừ ----------

    @Test
    void khongLayCauDaCoTrongDeVaKhongLayLaiCauDaBiDoi() throws Exception {
        int a = bankMcq(TOPIC, "easy", "Câu A");
        int b = bankMcq(TOPIC, "easy", "Câu B");
        int c = bankMcq(TOPIC, "easy", "Câu C");
        int matrix = createMatrix(mcqCell(TOPIC, null, 2));
        JsonNode built = build(matrix);
        int examId = built.get("examId").asInt();
        assertEquals(2, built.get("fromBank").asInt());
        int slot1 = slotId(examId, cellId(matrix, 1), 1);
        int slot2 = slotId(examId, cellId(matrix, 1), 2);
        assertEquals(a, count("SELECT bank_item_id FROM exam_slot WHERE id = ?", slot1));
        assertEquals(b, count("SELECT bank_item_id FROM exam_slot WHERE id = ?", slot2));

        // A và B đang trong đề nên chỉ còn C.
        assertEquals(c, replaceBank(examId, slot1).get("bankItemId").asInt());
        // Lần nữa: A bị đổi ra, B và C đang trong đề, nên hết câu dù A vẫn còn trong ngân hàng.
        assertExhausted(examId, slot1);
    }

    @Test
    void cauBiDoiKhoiChoNayKhongQuayLaiOChoKhacCuaDe() throws Exception {
        int a = bankMcq(TOPIC, "easy", "Câu A");
        int b = bankMcq(TOPIC, "easy", "Câu B");
        int matrix = createMatrix(mcqCell(TOPIC, null, 1), mcqCell(TOPIC, null, 1));
        int examId = build(matrix).get("examId").asInt();
        int slot1 = slotId(examId, cellId(matrix, 1), 1);
        int slot2 = slotId(examId, cellId(matrix, 2), 1);
        assertEquals(a, count("SELECT bank_item_id FROM exam_slot WHERE id = ?", slot1));
        assertEquals(b, count("SELECT bank_item_id FROM exam_slot WHERE id = ?", slot2));

        perform(post(replaceUrl(examId, slot1))).andExpect(status().isOk()); // A bị đổi ra (sinh mới)
        assertExhausted(examId, slot2); // A không được dùng lại ở chỗ khác của đề
        assertEquals(b, count("SELECT bank_item_id FROM exam_slot WHERE id = ?", slot2), "chỗ 2 giữ nguyên");
    }

    @Test
    void khongLayBanSaoNganHangCuaCauDangNamTrongDe() throws Exception {
        int[] e = generatedExam(1);
        int question = count("SELECT generated_question_id FROM exam_slot WHERE id = ?", e[1]);
        bankCopyOf(question);
        assertExhausted(e[0], e[1]);
    }

    @Test
    void khongLayBanSaoNganHangCuaCauDaBiDoiRa() throws Exception {
        int[] e = generatedExam(1);
        int question = count("SELECT generated_question_id FROM exam_slot WHERE id = ?", e[1]);
        bankCopyOf(question);
        perform(post(replaceUrl(e[0], e[1]))).andExpect(status().isOk());
        settle(e[0]);
        assertEquals("ready", slotState(e[1]));
        assertExhausted(e[0], e[1]);
    }

    @Test
    void khongLayBanSaoNganHangCuaCauGocKhiCauHienTaiLaBanGiaoVienSua() throws Exception {
        int[] e = generatedExam(1);
        int original = count("SELECT generated_question_id FROM exam_slot WHERE id = ?", e[1]);
        bankCopyOf(original);
        perform(put("/api/exams/" + e[0] + "/slots/" + e[1] + "/question").contentType(MediaType.APPLICATION_JSON)
                .content("{\"baseQuestionId\": " + original + ", \"questionText\": \"Bậc của đơn thức $5x^2$ là:\","
                        + " \"optionA\": \"$1$\", \"optionB\": \"$2$\", \"optionC\": \"$3$\", \"optionD\": \"$4$\","
                        + " \"correctOption\": \"B\", \"explanation\": \"Số mũ.\"}")).andExpect(status().isOk());
        assertTrue(count("SELECT generated_question_id FROM exam_slot WHERE id = ?", e[1]) != original);
        assertExhausted(e[0], e[1]);
    }

    // ---------- hết câu: lỗi rõ ràng, không đổi gì ----------

    @Test
    void hetCauNganHangTra409ChoNguyenTrangKhongSinhNgam() throws Exception {
        int[] e = generatedExam(1);
        int question = count("SELECT generated_question_id FROM exam_slot WHERE id = ?", e[1]);
        int jobsBefore = jobs(e[1]);
        int actionsBefore = actions(e[1]);

        JsonNode body = json(postJson(replaceUrl(e[0], e[1]), "{\"source\": \"bank\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BANK_EXHAUSTED")));
        assertTrue(body.get("error").asText().toLowerCase().contains("ngân hàng"), body.get("error").asText());

        assertEquals("ready", slotState(e[1]));
        assertEquals(question, count("SELECT generated_question_id FROM exam_slot WHERE id = ?", e[1]));
        assertEquals(jobsBefore, jobs(e[1]), "không sinh ngầm");
        assertEquals(actionsBefore, actions(e[1]), "không ghi nhật ký khi lỗi");
        assertEquals(1, count("SELECT attempts FROM exam_slot WHERE id = ?", e[1]));

        // Sau 409, giáo viên chọn sinh mới và đi tiếp bình thường.
        postJson(replaceUrl(e[0], e[1]), "{\"source\": \"generate\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("generating"));
        assertEquals(jobsBefore + 1, jobs(e[1]));
    }

    // ---------- mặc định và kiểm tra tham số ----------

    @Test
    void khongCoSourceHoacSourceGenerateVanSinhMoiVaGhiNguon() throws Exception {
        int[] e = generatedExam(1);
        bankMcq(TOPIC, "easy", "Có câu ngân hàng nhưng không được lấy");
        postJson(replaceUrl(e[0], e[1]), "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("generating"));
        assertNull(jdbc.queryForObject("SELECT bank_item_id FROM exam_slot WHERE id = ?", Integer.class, e[1]));
        settle(e[0]);
        postJson(replaceUrl(e[0], e[1]), "{\"source\": \"generate\", \"instruction\": \"Dùng ngữ cảnh mua vở\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("generating"));
        assertEquals(List.of("generate", "generate"), jdbc.queryForList("SELECT replace_source FROM exam_slot_action"
                + " WHERE slot_id = ? ORDER BY id", String.class, e[1]));
        assertEquals("Dùng ngữ cảnh mua vở", jdbc.queryForObject("SELECT instruction FROM exam_slot_action WHERE"
                + " slot_id = ? ORDER BY id DESC LIMIT 1", String.class, e[1]));
    }

    @Test
    void sourceSaiHoacBankKemInstructionTra400KhongDoiGi() throws Exception {
        int[] e = generatedExam(1);
        bankMcq(TOPIC, "easy", "Câu ngân hàng");
        int question = count("SELECT generated_question_id FROM exam_slot WHERE id = ?", e[1]);
        postJson(replaceUrl(e[0], e[1]), "{\"source\": \"xyz\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        postJson(replaceUrl(e[0], e[1]), "{\"source\": \"bank\", \"instruction\": \"Dùng ngữ cảnh mua vở\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertEquals("ready", slotState(e[1]));
        assertEquals(question, count("SELECT generated_question_id FROM exam_slot WHERE id = ?", e[1]));
        assertEquals(0, actions(e[1]));
    }

    @Test
    void choDangSinhTra409KhiDoiBangNganHang() throws Exception {
        int matrix = createMatrix(mcqCell(TOPIC, null, 1));
        int examId = build(matrix).get("examId").asInt();
        int slot = slotId(examId, cellId(matrix, 1), 1);
        bankMcq(TOPIC, "easy", "Câu ngân hàng");
        // build đã xếp job (chưa chạy): chỗ đang sinh
        postJson(replaceUrl(examId, slot), "{\"source\": \"bank\"}").andExpect(status().isConflict());
        assertEquals("generating", slotState(slot));
        assertEquals(0, actions(slot));
    }

    // ---------- quyền sở hữu ----------

    @Test
    void giaoVienKhacDoiBangNganHangTra404KhongDoiGi() throws Exception {
        int[] e = generatedExam(1);
        bankMcq(TOPIC, "easy", "Câu ngân hàng");
        performAs(otherTeacher, post(replaceUrl(e[0], e[1])).contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\": \"bank\"}")).andExpect(status().isNotFound());
        assertEquals("ready", slotState(e[1]));
        assertEquals(0, actions(e[1]));
    }

    // ---------- vết QC ----------

    @Test
    void vetQcCoDongGocNganHangSauKhiDoiBangNganHang() throws Exception {
        int[] e = generatedExam(1);
        int item = bankMcq(TOPIC, "easy", "Câu ngân hàng thay thế");
        replaceBank(e[0], e[1]);

        JsonNode trace = json(perform(get("/api/exams/" + e[0] + "/slots/" + e[1] + "/qc-trace"))
                .andExpect(status().isOk()));
        assertEquals("bank", trace.get("source").asText());
        assertEquals(item, trace.get("bankItemId").asInt());
        JsonNode lineages = trace.get("lineages");
        assertEquals(2, lineages.size(), "lần sinh trước và câu ngân hàng hiện tại");
        assertEquals("generated", lineages.get(0).get("origin").asText());
        assertEquals("bank", lineages.get(1).get("origin").asText());
        assertTrue(lineages.get(1).get("versions").get(0).get("current").asBoolean(),
                "câu ngân hàng là phiên bản hiện tại của chỗ");
        assertEquals("replace", trace.get("actions").get(trace.get("actions").size() - 1).get("action").asText());
    }

    // ---------- đề theo bài ----------

    @Test
    void doiBangNganHangTrenDeTheoBaiKhongLocNoiDung() throws Exception {
        int matrix = createMatrix(mcqCell(TOPIC, "Đơn thức, đa thức", 1));
        int template = count("SELECT template_id FROM exam_matrix_cell WHERE id = ?", cellId(matrix, 1));
        int item = bankMcq(template, TOPIC, "easy", "Câu nguồn", "Câu từ ô ma trận",
                new String[] {"$1$", "$2$", "$3$", "$4$"});
        int examId = json(postJson("/api/exams/build-by-lesson", "{\"subjectId\": " + subject + ", \"lesson\": \""
                + TOPIC + "\", \"cells\": [{\"questionType\": \"mcq\", \"difficulty\": \"easy\", \"questionCount\": 1,"
                + " \"statementDifficulties\": null}]}").andExpect(status().isOk())).get("examId").asInt();
        // Ngân hàng đã có câu nên chỗ lấy luôn từ lúc tạo; đổi bằng ngân hàng thì hết câu (câu duy nhất đang trong đề).
        int slot = count("SELECT id FROM exam_slot WHERE exam_id = ?", examId);
        assertEquals(item, count("SELECT bank_item_id FROM exam_slot WHERE id = ?", slot));
        assertExhausted(examId, slot);
        int second = bankMcq(template, TOPIC, "easy", "Câu nguồn hai", "Câu thứ hai từ ô ma trận",
                new String[] {"$1$", "$2$", "$3$", "$4$"});
        assertEquals(second, replaceBank(examId, slot).get("bankItemId").asInt());
        assertNotNull(second);
    }
}
