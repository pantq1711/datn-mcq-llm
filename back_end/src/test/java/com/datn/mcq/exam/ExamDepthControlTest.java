package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.llm.prompt.MatrixPromptDraftV0;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Độ sâu đề (12/11): mức thử thách của đề ({@code challenge} = standard | advanced, cột exam.challenge) khi tạo đề theo ma
 * trận và theo bài học; khối nâng cao chỉ ở ô Vận dụng; ô Vận dụng của đề nâng cao không lấy câu ngân hàng; Đổi câu trong đề
 * nâng cao giữ mức; Đổi câu lý do quá dễ / quá khó thêm khối lý do kèm câu cũ, giữ qua lần sinh lại tự động.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ExamDepthControlTest extends ExamFlowTestBase {

    private static String mcqHard(String topic, int count) {
        return "{\"part\": \"I\", \"questionType\": \"mcq\", \"difficulty\": \"hard\", \"questionCount\": " + count
                + ", \"topic\": \"" + topic + "\", \"learningOutcomes\": {\"hard\": \"Vận dụng.\"}}";
    }

    private JsonNode buildWith(int matrixId, String challenge) throws Exception {
        return json(postJson("/api/exams/build", "{\"matrixId\": " + matrixId
                + (challenge == null ? "" : ", \"challenge\": \"" + challenge + "\"") + "}").andExpect(status().isOk()));
    }

    private String examChallenge(int examId) {
        return jdbc.queryForObject("SELECT challenge FROM exam WHERE id = ?", String.class, examId);
    }

    private String runPrompt(int examId, int cellId) {
        return jdbc.queryForObject("SELECT r.prompt_text FROM experiment_run r JOIN exam_slot s ON s.run_id = r.id"
                + " WHERE s.exam_id = ? AND s.cell_id = ? LIMIT 1", String.class, examId, cellId);
    }

    private String latestNote(int slotId) {
        return jdbc.queryForObject("SELECT prompt_note FROM generation_job WHERE exam_slot_id = ? AND job_type = 'generate'"
                + " ORDER BY id DESC LIMIT 1", String.class, slotId);
    }

    @Test
    void deNangCaoLuuVaoExamTraVeApiVaChiOVanDungCoKhoiNangCao() throws Exception {
        int matrixId = createMatrix(mcqCell("Đa thức", null, 1), mcqHard("Hằng đẳng thức", 1));
        JsonNode built = buildWith(matrixId, "advanced");
        int examId = built.get("examId").asInt();
        assertEquals("advanced", built.get("challenge").asText());
        assertEquals("advanced", examChallenge(examId));
        assertFalse(runPrompt(examId, cellId(matrixId, 1)).contains(MatrixPromptDraftV0.ADVANCED_RULE), "ô Nhận biết");
        assertTrue(runPrompt(examId, cellId(matrixId, 2)).contains(MatrixPromptDraftV0.ADVANCED_RULE), "ô Vận dụng");
        settle(examId);
        assertTrue(generator.mcqPrompts.stream().anyMatch(p -> p.contains(MatrixPromptDraftV0.ADVANCED_RULE)),
                "prompt gửi model của ô Vận dụng có khối nâng cao");
        JsonNode view = json(perform(get("/api/exams/" + examId)).andExpect(status().isOk()));
        assertEquals("advanced", view.get("challenge").asText());
    }

    @Test
    void khongGuiMucThuThachThiLaChuan() throws Exception {
        int matrixId = createMatrix(mcqHard("Hằng đẳng thức", 1));
        JsonNode built = buildWith(matrixId, null);
        int examId = built.get("examId").asInt();
        assertEquals("standard", built.get("challenge").asText());
        assertEquals("standard", examChallenge(examId));
        assertFalse(runPrompt(examId, cellId(matrixId, 1)).contains(MatrixPromptDraftV0.ADVANCED_RULE));
        JsonNode view = json(perform(get("/api/exams/" + examId)).andExpect(status().isOk()));
        assertEquals("standard", view.get("challenge").asText());
        // không phân biệt hoa thường, có khoảng trắng
        assertEquals("standard", buildWith(matrixId, " Standard ").get("challenge").asText());
    }

    @Test
    void mucThuThachLaTra400VaKhongTaoDe() throws Exception {
        int matrixId = createMatrix(mcqHard("Hằng đẳng thức", 1));
        int before = count("SELECT count(*) FROM exam");
        JsonNode body = json(postJson("/api/exams/build", "{\"matrixId\": " + matrixId + ", \"challenge\": \"expert\"}")
                .andExpect(status().isBadRequest()));
        assertEquals("INVALID_REQUEST", body.get("code").asText());
        assertEquals(before, count("SELECT count(*) FROM exam"));
        int lessons = count("SELECT count(*) FROM exam_matrix");
        postJson("/api/exams/build-by-lesson", "{\"subjectId\": " + subject + ", \"gradeLevel\": 8, \"lesson\": \"Bài 1\","
                + " \"challenge\": \"hard\", \"cells\": [{\"questionType\": \"mcq\", \"difficulty\": \"hard\","
                + " \"questionCount\": 1}]}").andExpect(status().isBadRequest());
        assertEquals(lessons, count("SELECT count(*) FROM exam_matrix"), "không còn ma trận ẩn");
    }

    @Test
    void taoDeTheoBaiHocNhanMucThuThach() throws Exception {
        JsonNode built = json(postJson("/api/exams/build-by-lesson", "{\"subjectId\": " + subject + ", \"gradeLevel\": 8,"
                + " \"lesson\": \"Bài 3. Hằng đẳng thức\", \"challenge\": \"advanced\", \"cells\": ["
                + "{\"questionType\": \"mcq\", \"difficulty\": \"hard\", \"questionCount\": 1}]}")
                .andExpect(status().isOk()));
        int examId = built.get("examId").asInt();
        assertEquals("advanced", built.get("challenge").asText());
        assertEquals("advanced", examChallenge(examId));
        String prompt = jdbc.queryForObject("SELECT r.prompt_text FROM experiment_run r JOIN exam_slot s ON s.run_id = r.id"
                + " WHERE s.exam_id = ? LIMIT 1", String.class, examId);
        assertTrue(prompt.contains(MatrixPromptDraftV0.ADVANCED_RULE), prompt);
    }

    @Test
    void oVanDungCuaDeNangCaoKhongLayCauNganHang() throws Exception {
        bankMcq("Hằng đẳng thức", "hard", "Câu ngân hàng Vận dụng");
        bankMcq("Đa thức", "easy", "Câu ngân hàng Nhận biết");
        int matrixId = createMatrix(mcqCell("Đa thức", null, 1), mcqHard("Hằng đẳng thức", 1));
        JsonNode adv = buildWith(matrixId, "advanced");
        assertEquals(1, adv.get("fromBank").asInt(), "chỉ ô Nhận biết lấy ngân hàng");
        int advExam = adv.get("examId").asInt();
        assertEquals(0, count("SELECT count(*) FROM exam_slot WHERE exam_id = ? AND cell_id = ? AND bank_item_id IS NOT NULL",
                advExam, cellId(matrixId, 2)));
        JsonNode std = buildWith(matrixId, "standard");
        assertEquals(2, std.get("fromBank").asInt(), "đề chuẩn lấy ngân hàng như cũ");
    }

    @Test
    void doiCauTrongDeNangCaoGiuMuc() throws Exception {
        int matrixId = createMatrix(mcqHard("Hằng đẳng thức", 1));
        int examId = buildWith(matrixId, "advanced").get("examId").asInt();
        settle(examId);
        int slot = slotId(examId, cellId(matrixId, 1), 1);
        generator.mcqPrompts.clear();
        postJson("/api/exams/" + examId + "/slots/" + slot + "/replace", "{}").andExpect(status().isOk());
        settle(examId);
        assertFalse(generator.mcqPrompts.isEmpty());
        assertTrue(generator.mcqPrompts.get(0).contains(MatrixPromptDraftV0.ADVANCED_RULE), generator.mcqPrompts.get(0));
    }

    @Test
    void doiCauLyDoQuaDeCoKhoiLyDoKemCauCuVaGiuQuaLanSinhLai() throws Exception {
        int matrixId = createMatrix(mcqCell("Đa thức", null, 1));
        int examId = buildWith(matrixId, null).get("examId").asInt();
        settle(examId);
        int slot = slotId(examId, cellId(matrixId, 1), 1);
        String oldText = jdbc.queryForObject("SELECT q.question_text FROM generated_question q JOIN exam_slot s"
                + " ON s.generated_question_id = q.id WHERE s.id = ?", String.class, slot);
        generator.brokenMcq = 1; // lần sinh đầu của lượt Đổi bị loại → sinh lại tự động
        generator.mcqPrompts.clear();
        postJson("/api/exams/" + examId + "/slots/" + slot + "/replace",
                "{\"reason\": \"too_easy\", \"instruction\": \"Dùng ngữ cảnh mua sắm\"}").andExpect(status().isOk());
        String expected = MatrixPromptDraftV0.replaceReasonBlock("too_easy", oldText);
        assertTrue(latestNote(slot).contains(expected), latestNote(slot));
        settle(examId);
        assertEquals("ready", slotState(slot));
        assertTrue(generator.mcqPrompts.size() >= 2, "có lần sinh lại tự động");
        for (String p : generator.mcqPrompts) {
            assertTrue(p.contains(expected), "mọi lần thử của lượt Đổi đều giữ khối lý do: " + p);
            assertTrue(p.contains("Dùng ngữ cảnh mua sắm"));
            assertTrue(p.indexOf(expected) < p.indexOf("Dùng ngữ cảnh mua sắm"), "khối lý do trước yêu cầu giáo viên");
        }
    }

    @Test
    void doiCauLyDoQuaKhoVaLyDoKhac() throws Exception {
        int matrixId = createMatrix(mcqCell("Đa thức", null, 1));
        int examId = buildWith(matrixId, null).get("examId").asInt();
        settle(examId);
        int slot = slotId(examId, cellId(matrixId, 1), 1);
        postJson("/api/exams/" + examId + "/slots/" + slot + "/replace", "{\"reason\": \"too_hard\"}")
                .andExpect(status().isOk());
        assertTrue(latestNote(slot).contains("QUÁ KHÓ"), latestNote(slot));
        settle(examId);
        for (String reason : List.of("wrong_answer", "off_matrix", "other")) {
            postJson("/api/exams/" + examId + "/slots/" + slot + "/replace", "{\"reason\": \"" + reason + "\"}")
                    .andExpect(status().isOk());
            String note = latestNote(slot);
            assertFalse(note != null && (note.contains("QUÁ KHÓ") || note.contains("QUÁ DỄ")), reason + ": " + note);
            settle(examId);
        }
    }

    private static String saHard(String topic) {
        return "{\"part\": \"III\", \"questionType\": \"sa\", \"difficulty\": \"hard\", \"questionCount\": 1, \"topic\": \""
                + topic + "\", \"learningOutcomes\": {\"hard\": \"Vận dụng.\"}}";
    }

    private static String tfCell(String topic) {
        return "{\"part\": \"II\", \"questionType\": \"tf\", \"statementDifficulties\": [\"easy\", \"medium\", \"medium\","
                + " \"hard\"], \"questionCount\": 1, \"topic\": \"" + topic + "\", \"learningOutcomes\": {\"easy\":"
                + " \"Nhận biết.\", \"medium\": \"Hiểu.\", \"hard\": \"Vận dụng.\"}}";
    }

    @Test
    void deNangCaoCauSaNhacDapSoToiDa4KyTuTrongPromptGuiModel() throws Exception {
        int matrixId = createMatrix(saHard("Hằng đẳng thức"));
        int examId = buildWith(matrixId, "advanced").get("examId").asInt();
        assertTrue(runPrompt(examId, cellId(matrixId, 1)).contains(MatrixPromptDraftV0.SA_ADVANCED_ANSWER_RULE));
        settle(examId);
        assertTrue(generator.prompts.stream().anyMatch(p -> p.contains(MatrixPromptDraftV0.SA_ADVANCED_ANSWER_RULE)),
                "prompt gửi model của ô sa Vận dụng nâng cao có nhắc đáp số");
        int stdMatrix = createMatrix(saHard("Hằng đẳng thức"));
        int stdExam = buildWith(stdMatrix, "standard").get("examId").asInt();
        assertFalse(runPrompt(stdExam, cellId(stdMatrix, 1)).contains(MatrixPromptDraftV0.SA_ADVANCED_ANSWER_RULE),
                "đề chuẩn giữ prompt cũ");
    }

    @Test
    void doiCauSaQuaDeDoiDangBaiKhacDapSoKhacVaDuaDapSoCuVaoPrompt() throws Exception {
        int matrixId = createMatrix(saHard("Hằng đẳng thức"));
        int examId = buildWith(matrixId, "advanced").get("examId").asInt();
        settle(examId);
        int slot = slotId(examId, cellId(matrixId, 1), 1);
        String oldText = jdbc.queryForObject("SELECT q.question_text FROM generated_question q JOIN exam_slot s"
                + " ON s.generated_question_id = q.id WHERE s.id = ?", String.class, slot);
        String oldAnswer = jdbc.queryForObject("SELECT q.sa_answer FROM generated_question q JOIN exam_slot s"
                + " ON s.generated_question_id = q.id WHERE s.id = ?", String.class, slot);
        generator.prompts.clear();
        postJson("/api/exams/" + examId + "/slots/" + slot + "/replace", "{\"reason\": \"too_easy\"}")
                .andExpect(status().isOk());
        String expected = MatrixPromptDraftV0.replaceReasonBlock("too_easy", oldText,
                com.datn.mcq.enums.QuestionType.SA, oldAnswer);
        assertTrue(expected.contains("<<< " + oldAnswer + " >>>"), expected);
        assertTrue(latestNote(slot).contains(expected), latestNote(slot));
        assertTrue(latestNote(slot).contains("DẠNG BÀI hoặc cách giải KHÁC"), latestNote(slot));
        assertTrue(latestNote(slot).contains("tối đa 4 ký tự"), latestNote(slot));
        settle(examId);
        assertFalse(generator.prompts.isEmpty());
        assertTrue(generator.prompts.get(0).contains(expected), generator.prompts.get(0));
        assertTrue(generator.prompts.get(0).contains(MatrixPromptDraftV0.SA_ADVANCED_ANSWER_RULE), "vẫn giữ mức nâng cao");
    }

    @Test
    void doiCauTfQuaKhoDoiNguCanhKhacVaKhongCoDapSo() throws Exception {
        int matrixId = createMatrix(tfCell("Hằng đẳng thức"));
        int examId = buildWith(matrixId, null).get("examId").asInt();
        settle(examId);
        int slot = slotId(examId, cellId(matrixId, 1), 1);
        postJson("/api/exams/" + examId + "/slots/" + slot + "/replace", "{\"reason\": \"too_hard\"}")
                .andExpect(status().isOk());
        String note = latestNote(slot);
        assertTrue(note.contains("QUÁ KHÓ"), note);
        assertTrue(note.contains("DẠNG BÀI hoặc cách giải KHÁC"), note);
        assertTrue(note.contains("Ngữ cảnh và các khẳng định KHÁC câu bị đổi"), note);
        assertFalse(note.contains("Đáp số"), note);
    }

    @Test
    void cotChallengeCoMacDinhVaCheck() {
        int exam = insert("INSERT INTO exam (exam_code, title, subject_id, created_by) VALUES ('CH1', 'Đề', ?, ?)",
                subject, teacher);
        assertEquals("standard", examChallenge(exam), "đề cũ và đề thủ công là chuẩn");
        jdbc.update("UPDATE exam SET challenge = 'advanced' WHERE id = ?", exam);
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE exam SET challenge = 'expert' WHERE id = ?", exam));
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("UPDATE exam SET challenge = NULL WHERE id = ?", exam));
    }
}
