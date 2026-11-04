package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.generation.worker.GenerationJobPoller;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-07 (3/11): xem đề và danh sách đề nạp dữ liệu theo lô, không còn N+1.
 * <ul>
 *   <li>Ảnh chụp hành vi: JSON của {@code GET /api/exams/{id}} và {@code GET /api/exams} trên đề có đủ loại câu (mcq/tf/sa;
 *       câu sinh, câu ngân hàng, câu đã sửa, câu đã Giữ; "bám tài liệu"; cờ similar_in_exam; chỗ hỏng; chỗ đang sinh) bằng
 *       đúng file {@code exam-view-batch/golden.json}, chụp từ code TRƯỚC khi gộp truy vấn (id, createdAt, examCode được
 *       chuẩn hóa). Riêng {@code needsReviewSlots} của danh sách không so với ảnh chụp: số cũ thiếu cờ similar_in_exam, được
 *       sửa cho khớp trang chi tiết và có test riêng.</li>
 *   <li>Đếm truy vấn bằng Hibernate Statistics: số truy vấn không tăng theo số câu (xem đề) hay số đề (danh sách).</li>
 * </ul>
 * LLM sinh câu, Solver và judge "bám tài liệu" đều giả (không gọi mạng).
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import(TestSafetyConfiguration.class)
class ExamViewBatchTest extends ExamFlowTestBase {

    private static final String LESSON = "Bài 3. Hằng đẳng thức đáng nhớ";
    private static final String TEXT = "Hằng đẳng thức đáng nhớ thứ nhất: bình phương của một tổng $(a+b)^2 = a^2 + 2ab + b^2$."
            + "\n\nVí dụ: tính $(x+1)^2$.";
    private static final String GOLDEN = "/exam-view-batch/golden.json";
    private static final Path ACTUAL = Path.of("target", "exam-view-batch-actual.json");

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private int sha;

    /** Mẫu nhãn tf do server chọn ngẫu nhiên; cố định nhãn LLM giả trả về để ảnh chụp JSON ổn định giữa các lần chạy. */
    @BeforeEach
    void fixTfLabels() {
        generator.tfLabels = new String[] {"T", "F", "T", "F"};
    }

    // ---------- dựng dữ liệu ----------

    private static String tfCell(String topic, int count) {
        return "{\"part\": \"II\", \"questionType\": \"tf\", \"statementDifficulties\": [\"easy\", \"easy\", \"medium\","
                + " \"hard\"], \"questionCount\": " + count + ", \"topic\": \"" + topic + "\","
                + " \"learningOutcomes\": {\"easy\": \"Nhận biết.\", \"medium\": \"Khai triển.\", \"hard\": \"Vận dụng.\"}}";
    }

    private static String saCell(String topic, String content, int count) {
        return "{\"part\": \"III\", \"questionType\": \"sa\", \"difficulty\": \"medium\", \"questionCount\": " + count
                + ", \"topic\": \"" + topic + "\", \"content\": \"" + content + "\","
                + " \"learningOutcomes\": {\"medium\": \"Tính được góc trong tứ giác.\"}}";
    }

    /** Đề {@code factor} lần (18 câu khi factor = 1): mcq 6 + 6, tf 4, sa 2 mỗi lần. */
    private int scaleExam(int factor) throws Exception {
        int matrix = createMatrix(mcqCell("Đa thức", "Đơn thức", 6 * factor), mcqCell("Phân thức", "Rút gọn", 6 * factor),
                tfCell("Hằng đẳng thức", 4 * factor), saCell("Tứ giác", "Hình thang cân", 2 * factor));
        int examId = build(matrix).get("examId").asInt();
        settle(examId);
        assertEquals(18 * factor, count("SELECT count(*) FROM exam_slot WHERE exam_id = ?", examId));
        assertEquals(18 * factor, count("SELECT count(*) FROM exam_slot WHERE exam_id = ? AND state = 'ready'", examId),
                jdbc.queryForList("SELECT state, failure_reason FROM exam_slot WHERE exam_id = ?", examId).toString());
        return examId;
    }

    /** Đề nhỏ 4 câu (mcq 2, tf 1, sa 1) cho các test đếm theo số đề. */
    private int smallExam() throws Exception {
        int matrix = createMatrix(mcqCell("Đa thức", "Đơn thức", 2), tfCell("Hằng đẳng thức", 1),
                saCell("Tứ giác", "Hình thang cân", 1));
        int examId = build(matrix).get("examId").asInt();
        settle(examId);
        return examId;
    }

    private int material() {
        return insert("INSERT INTO lesson_material (owner_id, file_name, mime_type, size_bytes, sha256, extract_method,"
                + " extracted_text, char_count) VALUES (?, 'bai3.txt', 'text/plain', ?, ?, 'plain', ?, ?)", teacher,
                TEXT.getBytes(StandardCharsets.UTF_8).length, String.format("%064x", ++sha), TEXT,
                TEXT.codePointCount(0, TEXT.length()));
    }

    /**
     * Xử lý mọi job đến hạn trừ job "đã đỗ" (next_run_at xa hơn 12 giờ: job sinh câu của chỗ giữ ở generating, job grounding
     * để ở pending); khác {@code processAll}, không kéo job đã đỗ về hiện tại.
     */
    private void processParked() {
        GenerationJobPoller poller = poller();
        for (int i = 0; i < 80; i++) {
            jdbc.update("UPDATE generation_job SET next_run_at = now() - interval '1 minute' WHERE status = 'pending'"
                    + " AND (next_run_at IS NULL OR next_run_at < now() + interval '12 hours')");
            if (!poller.processNextJob(true).processed()) {
                return;
            }
        }
    }

    private void settleParked(int examId) throws Exception {
        for (int round = 0; round < 5; round++) {
            processParked();
            progress(examId);
        }
    }

    private int generatedQuestionOf(int slotId) {
        return count("SELECT generated_question_id FROM exam_slot WHERE id = ?", slotId);
    }

    /** Đề theo bài có tài liệu (3 câu mcq): câu 1 not_supported, câu 2 grounding_pending, câu 3 grounding_failed. */
    private int lessonExam() throws Exception {
        GROUNDING.verdict = "NOT_SUPPORTED";
        GROUNDING.quote = GroundingFake.Quote.NONE;
        int examId = json(postJson("/api/exams/build-by-lesson", "{\"subjectId\": " + subject + ", \"gradeLevel\": 8,"
                + " \"lesson\": \"" + LESSON + "\", \"materialIds\": [" + material() + "], \"cells\": [{\"questionType\":"
                + " \"mcq\", \"difficulty\": \"easy\", \"questionCount\": 3}]}").andExpect(status().isOk()))
                .get("examId").asInt();
        settle(examId);
        List<Integer> questions = jdbc.queryForList("SELECT generated_question_id FROM exam_slot WHERE exam_id = ?"
                + " ORDER BY slot_no", Integer.class, examId);
        assertEquals(3, questions.size());
        jdbc.update("DELETE FROM question_grounding WHERE question_id IN (?, ?)", questions.get(1), questions.get(2));
        jdbc.update("UPDATE generation_job SET status = 'pending', next_run_at = now() + interval '1 day'"
                + " WHERE job_type = 'grounding_check' AND question_id = ?", questions.get(1));
        jdbc.update("UPDATE generation_job SET status = 'failed', last_error = 'het luot thu'"
                + " WHERE job_type = 'grounding_check' AND question_id = ?", questions.get(2));
        return examId;
    }

    /**
     * Đề có đủ loại chỗ: mcq Đa thức x4 (chỗ 4 đang sinh; chỗ 1 đã Giữ; chỗ 2 đã sửa; chỗ 3 nguyên bản), mcq Phân thức x1 từ
     * ngân hàng, tf x2 (chỗ 2 đã Giữ), sa x2 (chỗ 2 hỏng). Hai câu mcq sinh ra cùng khuôn nên có cờ similar_in_exam.
     */
    private int richExam() throws Exception {
        bankMcq("Phân thức", "easy", "Câu ngân hàng về phân thức");
        int matrix = createMatrix(mcqCell("Đa thức", "Đơn thức", 4), mcqCell("Phân thức", "Rút gọn", 1),
                tfCell("Hằng đẳng thức", 2), saCell("Tứ giác", "Hình thang cân", 2));
        int examId = build(matrix).get("examId").asInt();
        int mcqCell = cellId(matrix, 1);
        int held = slotId(examId, mcqCell, 4);
        jdbc.update("UPDATE generation_job SET next_run_at = now() + interval '1 day' WHERE exam_slot_id = ?", held);
        settleParked(examId);

        // chỗ 2 của ô mcq: sửa một phương án rồi để Solver kiểm lại (chưa Giữ)
        int edited = slotId(examId, mcqCell, 2);
        int base = generatedQuestionOf(edited);
        Map<String, Object> q = jdbc.queryForMap("SELECT * FROM generated_question WHERE id = ?", base);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("baseQuestionId", base);
        body.put("questionText", (String) q.get("question_text"));
        body.put("optionA", (String) q.get("option_a"));
        body.put("optionB", (String) q.get("option_b"));
        body.put("optionC", "$99$");
        body.put("optionD", (String) q.get("option_d"));
        body.put("correctOption", "B");
        body.put("explanation", (String) q.get("explanation"));
        perform(put("/api/exams/" + examId + "/slots/" + edited + "/question").contentType(MediaType.APPLICATION_JSON)
                .content(body.toString())).andExpect(status().isOk());
        // chỗ 1 của ô mcq và chỗ 2 của ô tf: Giữ vào ngân hàng
        postJson("/api/exams/" + examId + "/slots/" + slotId(examId, mcqCell, 1) + "/keep", "{}")
                .andExpect(status().isOk());
        postJson("/api/exams/" + examId + "/slots/" + slotId(examId, cellId(matrix, 3), 2) + "/keep", "{}")
                .andExpect(status().isOk());
        settleParked(examId);

        // chỗ 2 của ô sa hỏng
        jdbc.update("UPDATE exam_slot SET state = 'failed', failure_reason = 'llm_error', generated_question_id = NULL"
                + " WHERE id = ?", slotId(examId, cellId(matrix, 4), 2));
        assertEquals("generating", slotState(held));
        return examId;
    }

    // ---------- chuẩn hóa JSON để so ảnh chụp ----------

    private final Map<String, Integer> tokens = new HashMap<>();
    private final Map<String, Integer> counters = new HashMap<>();

    private static String idSpace(String field) {
        return switch (field) {
            case "questionId", "originalQuestionId" -> "question";
            case "examId", "slotId", "cellId", "bankItemId", "matrixId" -> field.substring(0, field.length() - 2);
            default -> null;
        };
    }

    /** id → "<loại>#<thứ tự xuất hiện>", createdAt/examCode → mốc cố định; phần còn lại giữ nguyên. */
    private JsonNode normalize(JsonNode node) {
        if (node.isArray()) {
            ArrayNode out = objectMapper.createArrayNode();
            node.forEach(n -> out.add(normalize(n)));
            return out;
        }
        if (!node.isObject()) {
            return node;
        }
        ObjectNode out = objectMapper.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> e = fields.next();
            String space = idSpace(e.getKey());
            if (space != null && e.getValue().isInt()) {
                String key = space + ":" + e.getValue().asInt();
                Integer ordinal = tokens.get(key);
                if (ordinal == null) {
                    ordinal = counters.merge(space, 1, Integer::sum);
                    tokens.put(key, ordinal);
                }
                out.put(e.getKey(), space + "#" + ordinal);
            } else if (e.getKey().equals("createdAt") || e.getKey().equals("examCode")) {
                out.put(e.getKey(), "<" + e.getKey() + ">");
            } else {
                out.set(e.getKey(), normalize(e.getValue()));
            }
        }
        return out;
    }

    /** Bỏ needsReviewSlots khỏi danh sách: số này được sửa có chủ đích (khớp trang chi tiết), có test riêng. */
    private static JsonNode withoutNeedsReviewSlots(JsonNode list) {
        ObjectNode copy = list.deepCopy();
        copy.get("items").forEach(i -> ((ObjectNode) i).remove("needsReviewSlots"));
        return copy;
    }

    private JsonNode view(int examId) throws Exception {
        return json(perform(get("/api/exams/" + examId)).andExpect(status().isOk()));
    }

    private JsonNode list() throws Exception {
        return json(perform(get("/api/exams")).andExpect(status().isOk()));
    }

    private static JsonNode itemOf(JsonNode list, int examId) {
        for (JsonNode i : list.get("items")) {
            if (i.get("examId").asInt() == examId) {
                return i;
            }
        }
        throw new AssertionError("Danh sách không có đề " + examId);
    }

    // ---------- đếm truy vấn ----------

    private interface Action {
        void run() throws Exception;
    }

    private long queriesOf(Action action) throws Exception {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        assertTrue(stats.isStatisticsEnabled(), "thống kê Hibernate phải bật trong test này");
        stats.clear();
        action.run();
        return stats.getPrepareStatementCount();
    }

    private long viewQueries(int examId) throws Exception {
        return queriesOf(() -> perform(get("/api/exams/" + examId)).andExpect(status().isOk()));
    }

    /**
     * Số truy vấn của một trang danh sách. Spring Data bỏ truy vấn đếm tổng khi trang đầu chưa đầy (ít hơn {@code size}),
     * nên các lần so sánh dùng trang ĐẦY (3 đề với size=3, 10 đề với size=10) để cùng có hoặc cùng không có truy vấn đếm.
     */
    private long listQueries(int size) throws Exception {
        return queriesOf(() -> perform(get("/api/exams?size=" + size)).andExpect(status().isOk()));
    }

    // ---------- test ----------

    @Test
    void anhChupJsonXemDeVaDanhSachKhongDoiSauKhiGopTruyVan() throws Exception {
        int lesson = lessonExam();
        int rich = richExam();

        JsonNode viewLesson = view(lesson);
        JsonNode viewRich = view(rich);
        JsonNode list = list();
        assertEquals(2, list.get("items").size());

        ObjectNode actual = objectMapper.createObjectNode();
        actual.set("viewRich", normalize(viewRich));
        actual.set("viewLesson", normalize(viewLesson));
        actual.set("list", normalize(withoutNeedsReviewSlots(list)));
        Files.createDirectories(ACTUAL.getParent());
        Files.writeString(ACTUAL, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(actual),
                StandardCharsets.UTF_8);

        JsonNode expected = golden();
        assertNotNull(expected, "chưa có " + GOLDEN + " (số liệu thực tế ghi ở " + ACTUAL.toAbsolutePath() + ")");
        assertEquals(expected, actual, "JSON khác ảnh chụp trước khi sửa; số liệu thực tế ghi ở " + ACTUAL.toAbsolutePath());

        // đủ loại câu như đã dựng, để ảnh chụp có ý nghĩa
        List<JsonNode> items = new ArrayList<>();
        viewRich.get("parts").forEach(p -> p.get("items").forEach(items::add));
        assertEquals(9, items.size());
        assertTrue(items.stream().anyMatch(i -> "bank".equals(i.path("source").asText())));
        assertTrue(items.stream().anyMatch(i -> i.path("edited").asBoolean() && !"kept".equals(i.get("state").asText())));
        assertTrue(items.stream().anyMatch(i -> "kept".equals(i.get("state").asText())));
        assertTrue(items.stream().anyMatch(i -> "failed".equals(i.get("state").asText())));
        assertTrue(items.stream().anyMatch(i -> "generating".equals(i.get("state").asText())));
        assertTrue(items.stream().anyMatch(i -> i.get("reviewReasons").toString().contains("similar_in_exam")));
        assertTrue(viewLesson.toString().contains("grounding_not_supported"));
        assertTrue(viewLesson.toString().contains("grounding_pending"));
        assertTrue(viewLesson.toString().contains("grounding_failed"));
    }

    @Test
    void soTruyVanXemDeKhongTangTheoSoCau() throws Exception {
        int small = scaleExam(1);
        int large = scaleExam(2);
        view(small);
        view(large);

        long q18 = viewQueries(small);
        long q36 = viewQueries(large);
        assertEquals(q18, q36, "đề 18 câu: " + q18 + " truy vấn, đề 36 câu: " + q36 + " (phải bằng nhau)");
        assertTrue(q18 <= 25, "đề 18 câu dùng " + q18 + " truy vấn, tối đa 25");
        assertTrue(q36 <= 25, "đề 36 câu dùng " + q36 + " truy vấn, tối đa 25");
    }

    @Test
    void soTruyVanDanhSachKhongTangTheoSoDe() throws Exception {
        for (int i = 0; i < 3; i++) {
            smallExam();
        }
        list();
        long q3 = listQueries(3);
        for (int i = 0; i < 7; i++) {
            smallExam();
        }
        assertEquals(10, list().get("items").size());
        long q10 = listQueries(10);
        assertEquals(q3, q10, "3 đề: " + q3 + " truy vấn, 10 đề: " + q10 + " (phải bằng nhau)");
        assertTrue(q3 <= 20, "danh sách 3 đề dùng " + q3 + " truy vấn, tối đa 20");
        assertTrue(q10 <= 20, "danh sách 10 đề dùng " + q10 + " truy vấn, tối đa 20");
    }

    @Test
    void needsReviewSlotsOGDanhSachBangSoCauCanXemKyOTrangChiTiet() throws Exception {
        // hai câu mcq sinh ra cùng khuôn: trang chi tiết gắn similar_in_exam cho câu sau, danh sách cũng phải đếm
        int matrix = createMatrix(mcqCell("Đa thức", "Đơn thức", 1), mcqCell("Phân thức", "Rút gọn", 1));
        int similar = build(matrix).get("examId").asInt();
        settle(similar);
        int lesson = lessonExam();
        int rich = richExam();

        JsonNode list = list();
        for (int examId : List.of(similar, lesson, rich)) {
            assertEquals(view(examId).get("needsReviewCount").asInt(), itemOf(list, examId).get("needsReviewSlots").asInt(),
                    "đề " + examId);
        }
        assertEquals(1, itemOf(list, similar).get("needsReviewSlots").asInt());
        assertTrue(itemOf(list, rich).get("needsReviewSlots").asInt() > 0);
    }

    private JsonNode golden() throws IOException {
        try (InputStream in = getClass().getResourceAsStream(GOLDEN)) {
            return in == null ? null : objectMapper.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
