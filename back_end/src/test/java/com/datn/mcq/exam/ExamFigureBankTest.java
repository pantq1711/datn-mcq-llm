package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.enums.QuestionType;
import com.datn.mcq.exam.service.ExamSlotSiblings;
import com.datn.mcq.generation.repository.GeneratedQuestionRepository;
import com.datn.mcq.llm.prompt.MatrixPromptDraftV0;
import com.datn.mcq.qc.dto.DedupCandidate;
import com.datn.mcq.questionimage.RecordingStatementInspector;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * X1 (docs/design/cau-co-hinh.md mục 4.3): câu ngân hàng có hình được chọn khi tạo đề và khi Đổi bằng câu ngân hàng (bỏ
 * điều kiện loại ở {@code ExamSlotRepository.findBankCandidates}); Đổi bằng sinh mới từ chỗ có hình không đưa chữ câu cũ vào
 * prompt nhưng giữ lý do (D4); cờ "giống câu khác trong đề" và dedup trong đề so cả hình (H7, {@code imageKey}); thống kê
 * không đổi. LLM sinh câu và Solver giả, không gọi mạng.
 */
@SpringBootTest(properties = {
        "app.question-images.url-signing-key=dGVzdC1vbmx5LXF1ZXN0aW9uLWltYWdlLXVybC1zaWduaW5nLWtleS1ub3QtcHJvZA==",
        "app.question-images.url-ttl=1h",
        RecordingStatementInspector.PROPERTY
})
@Import(TestSafetyConfiguration.class)
class ExamFigureBankTest extends ExamFlowTestBase {

    private static final String SHA_1 = "1".repeat(64);
    private static final String SHA_2 = "2".repeat(64);

    @Autowired
    ExamSlotSiblings siblings;
    @Autowired
    GeneratedQuestionRepository generatedQuestionRepository;

    private JsonNode view(int examId) throws Exception {
        return json(perform(get("/api/exams/" + examId)).andExpect(status().isOk()));
    }

    private static List<JsonNode> items(JsonNode view) {
        List<JsonNode> out = new ArrayList<>();
        for (JsonNode part : view.get("parts")) {
            part.get("items").forEach(out::add);
        }
        return out;
    }

    private static JsonNode itemOfSlot(JsonNode view, int slotId) {
        return items(view).stream().filter(i -> i.get("slotId").asInt() == slotId).findFirst()
                .orElseThrow(() -> new AssertionError("Đề không có chỗ " + slotId));
    }

    private static List<String> info(JsonNode item) {
        List<String> out = new ArrayList<>();
        item.get("infoReasons").forEach(r -> out.add(r.asText()));
        return out;
    }

    private Integer bankItemOfSlot(int slot) {
        return jdbc.queryForObject("SELECT bank_item_id FROM exam_slot WHERE id = ?", Integer.class, slot);
    }

    private String latestNote(int slotId) {
        return jdbc.queryForObject("SELECT prompt_note FROM generation_job WHERE exam_slot_id = ? AND job_type = 'generate'"
                + " ORDER BY id DESC LIMIT 1", String.class, slotId);
    }

    /** Câu mcq ngân hàng có một hình ở đề dẫn (sha256 cho trước); câu nguồn là câu nhập từ Word của lô vừa tạo. */
    private int figureMcq(String text, String sha256) {
        int item = bankMcq("Đa thức", "easy", text);
        int batch = attachImageToBankItem(item, sha256);
        jdbc.update("UPDATE generated_question SET origin = 'import', import_batch_id = ? WHERE id ="
                + " (SELECT source_question_id FROM bank_item WHERE id = ?)", batch, item);
        return item;
    }

    private int imageIdOf(int bankItemId) {
        return count("SELECT qi.id FROM question_image qi JOIN bank_item b ON b.source_question_id ="
                + " qi.generated_question_id WHERE b.id = ?", bankItemId);
    }

    // ---------- tạo đề ----------

    @Test
    void taoDeTheoMaTranNhanCauCoHinhVaTraImagesCauKhongHinhKhongDoi() throws Exception {
        int plain = bankMcq("Đa thức", "easy", "Câu không hình");
        int figure = figureMcq("Cho hình vẽ sau.\n⟦img:1⟧", SHA_1);
        int matrix = createMatrix(mcqCell("Đa thức", null, 2));

        JsonNode built = build(matrix);
        int examId = built.get("examId").asInt();

        assertEquals(2, built.get("fromBank").asInt(), "câu có hình là ứng viên như mọi câu ngân hàng");
        assertEquals(0, built.get("jobsQueued").asInt());
        int plainSlot = slotId(examId, cellId(matrix, 1), 1);
        int figureSlot = slotId(examId, cellId(matrix, 1), 2);
        assertEquals(plain, bankItemOfSlot(plainSlot));
        assertEquals(figure, bankItemOfSlot(figureSlot));

        JsonNode view = view(examId);
        JsonNode withFigure = itemOfSlot(view, figureSlot);
        assertEquals(1, withFigure.get("images").size());
        JsonNode image = withFigure.get("images").get(0);
        assertEquals(imageIdOf(figure), image.get("id").asInt());
        assertEquals("stem", image.get("role").asText());
        assertTrue(image.get("url").asText().contains("&u=" + teacher + "&sig="), image.toString());
        assertTrue(withFigure.get("questionText").asText().contains("⟦img:"));
        JsonNode withoutFigure = itemOfSlot(view, plainSlot);
        assertFalse(withoutFigure.has("images"), "câu không hình: JSON như cũ, không có khóa images");
        assertEquals("Câu không hình", withoutFigure.get("questionText").asText());
    }

    // ---------- Đổi bằng câu ngân hàng ----------

    @Test
    void doiBangNganHangQuaLaiGiuaCauKhongHinhVaCauCoHinh() throws Exception {
        int plain1 = bankMcq("Đa thức", "easy", "Câu không hình một");
        int figure = figureMcq("Cho hình vẽ sau.\n⟦img:1⟧", SHA_1);
        int plain2 = bankMcq("Đa thức", "easy", "Câu không hình hai");
        int matrix = createMatrix(mcqCell("Đa thức", null, 1));
        int examId = build(matrix).get("examId").asInt();
        int slot = slotId(examId, cellId(matrix, 1), 1);
        assertEquals(plain1, bankItemOfSlot(slot), "cùng số lần dùng: id nhỏ trước");

        // không hình → có hình
        JsonNode first = json(postJson("/api/exams/" + examId + "/slots/" + slot + "/replace", "{\"source\": \"bank\"}")
                .andExpect(status().isOk()));
        assertEquals(figure, bankItemOfSlot(slot));
        assertEquals(1, first.get("images").size(), "kết quả Đổi trả images của câu có hình");
        assertEquals(1, itemOfSlot(view(examId), slot).get("images").size());

        // có hình → không hình
        JsonNode second = json(postJson("/api/exams/" + examId + "/slots/" + slot + "/replace", "{\"source\": \"bank\"}")
                .andExpect(status().isOk()));
        assertEquals(plain2, bankItemOfSlot(slot));
        assertFalse(second.has("images"));
        assertFalse(itemOfSlot(view(examId), slot).has("images"));
        assertEquals(List.of(plain1, figure), jdbc.queryForList("SELECT bank_item_id FROM exam_slot_action WHERE slot_id = ?"
                + " AND action = 'replace' ORDER BY id", Integer.class, slot), "câu có hình vào nhật ký Đổi như thường");
        assertEquals(0, count("SELECT count(*) FROM generation_job WHERE exam_slot_id = ?", slot), "không sinh câu");
    }

    // ---------- Đổi bằng sinh mới từ chỗ có hình (D4) ----------

    @Test
    void doiBangSinhMoiTuChoCoHinhGiuLyDoKhongDuaChuCauCuVaoPrompt() throws Exception {
        figureMcq("Cho hình vẽ sau. Tính số đo góc ABC của tứ giác.\n⟦img:1⟧", SHA_1);
        int matrix = createMatrix(mcqCell("Đa thức", null, 1));
        int examId = build(matrix).get("examId").asInt();
        int slot = slotId(examId, cellId(matrix, 1), 1);
        generator.mcqPrompts.clear();

        postJson("/api/exams/" + examId + "/slots/" + slot + "/replace", "{\"reason\": \"too_easy\"}")
                .andExpect(status().isOk());

        String expected = MatrixPromptDraftV0.replaceReasonBlock("too_easy", null);
        assertTrue(expected.contains("QUÁ DỄ"), expected);
        String note = latestNote(slot);
        assertTrue(note.contains(expected), note);
        assertFalse(note.contains("góc ABC"), "không đưa chữ câu cũ có hình: " + note);
        assertFalse(note.contains("⟦img:"), note);
        assertFalse(note.contains("Câu bị đổi (DỮ LIỆU"), note);
        settle(examId);
        assertFalse(generator.mcqPrompts.isEmpty());
        for (String p : generator.mcqPrompts) {
            assertTrue(p.contains(expected), "giữ lý do trong prompt: " + p);
            assertFalse(p.contains("góc ABC"), p);
            assertFalse(p.contains("⟦img:"), p);
        }
        assertEquals(List.of("too_easy"), jdbc.queryForList("SELECT replace_reason FROM exam_slot_action WHERE slot_id = ?"
                + " AND action = 'replace'", String.class, slot));
    }

    @Test
    void doiBangSinhMoiTuChoNganHangKhongHinhVanKemCauCu() throws Exception {
        bankMcq("Đa thức", "easy", "Rút gọn đa thức không hình");
        int matrix = createMatrix(mcqCell("Đa thức", null, 1));
        int examId = build(matrix).get("examId").asInt();
        int slot = slotId(examId, cellId(matrix, 1), 1);

        postJson("/api/exams/" + examId + "/slots/" + slot + "/replace", "{\"reason\": \"too_easy\"}")
                .andExpect(status().isOk());

        assertTrue(latestNote(slot).contains(MatrixPromptDraftV0.replaceReasonBlock("too_easy",
                "Rút gọn đa thức không hình")), latestNote(slot));
    }

    // ---------- cờ "giống câu khác trong đề" và dedup trong đề (H7) ----------

    private List<JsonNode> twoFigureItemsInOneExam(String sha1, String sha2) throws Exception {
        figureMcq("Bậc của đơn thức $9x^8$ là:\n⟦img:1⟧", sha1);
        figureMcq("Bậc của đơn thức $9x^8$ là:\n⟦img:1⟧", sha2);
        int matrix = createMatrix(mcqCell("Đa thức", null, 2));
        int examId = build(matrix).get("examId").asInt();
        assertEquals(0, count("SELECT count(*) FROM generation_job"), "cả hai chỗ lấy từ ngân hàng");
        return items(view(examId));
    }

    @Test
    void cungChuKhacHinhKhongBaoGiongCauKhacTrongDe() throws Exception {
        List<JsonNode> items = twoFigureItemsInOneExam(SHA_1, SHA_2);
        assertEquals("bank", items.get(1).get("source").asText());
        assertEquals(List.of(), info(items.get(0)));
        assertEquals(List.of(), info(items.get(1)), "khác hình: không là câu cùng khuôn");
    }

    @Test
    void cungChuCungHinhVanBaoGiongCauKhacTrongDe() throws Exception {
        List<JsonNode> items = twoFigureItemsInOneExam(SHA_1, SHA_1);
        assertEquals(List.of(), info(items.get(0)));
        assertEquals(List.of("similar_in_exam: I.1"), info(items.get(1)));
    }

    @Test
    void dedupTrongDeMangImageKeyCuaCauNganHangNapTheoLo() throws Exception {
        int f1 = figureMcq("Câu hình một\n⟦img:1⟧", SHA_1);
        int f2 = figureMcq("Câu hình hai\n⟦img:1⟧", SHA_2);
        int plain = bankMcq("Đa thức", "easy", "Câu không hình");
        int matrix = createMatrix(mcqCell("Đa thức", null, 4));
        int examId = build(matrix).get("examId").asInt();
        int generatedSlot = slotId(examId, cellId(matrix, 1), 4);
        assertNull(bankItemOfSlot(generatedSlot), "chỗ thứ tư chờ sinh câu");

        RecordingStatementInspector.clear();
        List<DedupCandidate> candidates = siblings.candidates(examId, generatedSlot, QuestionType.MCQ);
        assertEquals(1, RecordingStatementInspector.imageQueries().size(),
                "imageKey của mọi câu ngân hàng trong đề: một truy vấn theo lô");
        for (String sql : RecordingStatementInspector.imageQueries()) {
            assertFalse(RecordingStatementInspector.selectsDataColumn(sql), "không nạp cột data: " + sql);
        }

        assertEquals(3, candidates.size());
        assertEquals(SHA_1, keyOf(candidates, f1));
        assertEquals(SHA_2, keyOf(candidates, f2));
        assertEquals("", keyOf(candidates, plain));
    }

    private String keyOf(List<DedupCandidate> candidates, int bankItemId) {
        int source = count("SELECT source_question_id FROM bank_item WHERE id = ?", bankItemId);
        return candidates.stream().filter(c -> c.questionId() == source).findFirst()
                .orElseThrow(() -> new AssertionError("thiếu ứng viên của câu ngân hàng " + bankItemId)).imageKey();
    }

    // ---------- thống kê không đổi (test khóa) ----------

    @Test
    void thongKeSuDungNganHangVaDauRaLlmKhongDoiVoiCauCoHinh() throws Exception {
        int llmBefore = generatedQuestionRepository.countLlmGeneratedSince(LocalDate.now().atStartOfDay().minusDays(1));
        int figure = figureMcq("Cho hình vẽ sau.\n⟦img:1⟧", SHA_1);
        assertEquals(llmBefore, generatedQuestionRepository.countLlmGeneratedSince(
                LocalDate.now().atStartOfDay().minusDays(1)), "câu nhập có hình không tính vào bộ đếm đầu ra LLM");
        int matrix = createMatrix(mcqCell("Đa thức", null, 1));
        build(matrix);

        JsonNode detail = json(perform(get("/api/bank-items/" + figure)).andExpect(status().isOk()));
        assertEquals(1, detail.get("usage").get("usageCount").asInt(), "số đề dùng câu tính theo bank_item_id");
        assertNotNull(detail.get("usage").get("lastUsedAt"));
        assertFalse(detail.get("usage").get("lastUsedAt").isNull());

        JsonNode ai = json(perform(get("/api/bank-items").param("subjectId", String.valueOf(subject))
                .param("origin", "ai")).andExpect(status().isOk()));
        assertEquals(0, ai.get("totalItems").asInt(), "lọc câu AI (origin = 'llm') không lẫn câu nhập có hình");
        JsonNode imported = json(perform(get("/api/bank-items").param("subjectId", String.valueOf(subject))
                .param("origin", "import")).andExpect(status().isOk()));
        assertEquals(1, imported.get("totalItems").asInt());
        assertEquals(1, imported.get("items").get(0).get("usageCount").asInt());
    }
}
