package com.datn.mcq.assignment;

import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API công khai cho học sinh (docs/design/lam-bai-truc-tuyen.md mục 3.2, 4; quyết định 16/11): không đăng nhập, không lộ
 * đáp án khi bài còn mở, token lưu dạng băm, hạn nộp tính ở server với ân hạn 2 phút, một lượt nộp một lần; điểm, đáp án
 * và lời giải chỉ có sau khi bài đóng và hết ân hạn.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class PublicAttemptApiTest extends AssignmentTestBase {

    /** Tên trường không bao giờ được có trong dữ liệu gửi cho học sinh khi bài còn mở. */
    private static final Set<String> FORBIDDEN_KEYS = Set.of("correctOption", "label", "saAnswer", "explanation",
            "difficulty", "bankItemId", "questionId", "sourceQuestionId", "reviewReasons", "scoring", "tokenHash",
            "result", "review", "score", "score10", "correctCount", "yourChoice");

    private static void collectKeys(JsonNode node, List<String> out) {
        if (node.isObject()) {
            node.fieldNames().forEachRemaining(name -> {
                out.add(name);
                collectKeys(node.get(name), out);
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectKeys(child, out));
        }
    }

    private static void assertNoAnswerLeak(JsonNode payload) {
        List<String> keys = new ArrayList<>();
        collectKeys(payload, keys);
        for (String key : keys) {
            assertFalse(FORBIDDEN_KEYS.contains(key), "Lộ trường " + key + ": " + payload);
        }
        assertFalse(payload.toString().contains("LOI-GIAI-BI-MAT"), "Lộ lời giải: " + payload);
    }

    // ---------- Trang chào ----------

    @Test
    void infoIsPublicAndHasNoQuestions() throws Exception {
        String code = assign(true).get("publicCode").asText();
        JsonNode info = json(mockMvc.perform(get("/api/public/assignments/" + code))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store"))));
        assertEquals("Đề giữa kì I", info.get("title").asText());
        assertEquals(4, info.get("questionCount").asInt());
        assertEquals(45, info.get("durationMinutes").asInt());
        assertEquals("open", info.get("status").asText());
        assertTrue(info.get("showScore").asBoolean());
        assertFalse(info.get("showSolutions").asBoolean());
        assertNotNull(info.get("closesAt"));
        // Học sinh chọn lớp trong danh sách giáo viên khai (quyết định 16/11).
        assertEquals("8A1", info.get("classes").get(0).get("classLabel").asText());
        assertEquals(40, info.get("classes").get(0).get("size").asInt());
        assertEquals("8A2", info.get("classes").get(1).get("classLabel").asText());
        assertFalse(info.has("parts"));
        assertFalse(info.has("assignmentId"));
        assertFalse(info.has("examId"));
        assertNoAnswerLeak(info);

        mockMvc.perform(get("/api/public/assignments/ZZZZZZZZZZ")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/assignments/" + code.toLowerCase())).andExpect(status().isNotFound());
    }

    // ---------- Bắt đầu ----------

    @Test
    void startReturnsQuestionsWithoutAnswersAndStoresOnlyTheTokenHash() throws Exception {
        JsonNode a = assign(true, true);
        JsonNode payload = json(start(a.get("publicCode").asText(), " 8a 1 ", 12).andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store"))));
        String token = payload.get("attemptToken").asText();
        assertTrue(token.length() >= 40, token);
        assertEquals("8A1", payload.get("classLabel").asText());   // chuẩn hóa: bỏ khoảng trắng, viết hoa
        assertEquals(12, payload.get("seatNo").asInt());
        assertFalse(payload.get("submitted").asBoolean());
        assertFalse(payload.get("duplicateSeat").asBoolean());
        assertFalse(payload.get("resultsReleased").asBoolean());
        long remaining = payload.get("remainingSeconds").asLong();
        assertTrue(remaining > 44 * 60 && remaining <= 45 * 60, "remainingSeconds = " + remaining);
        assertNoAnswerLeak(payload);

        JsonNode parts = payload.get("parts");
        assertEquals(3, parts.size());
        JsonNode mcq = parts.get(0).get("items").get(0);
        assertEquals("I", parts.get(0).get("part").asText());
        assertEquals(mcqSlot1, mcq.get("slotId").asInt());
        assertEquals(1, mcq.get("number").asInt());
        assertEquals("mcq", mcq.get("questionType").asText());
        assertEquals("$2$", mcq.get("options").get("B").asText());
        assertEquals(4, mcq.get("options").size());
        assertEquals(2, parts.get(0).get("items").get(1).get("number").asInt());
        JsonNode tf = parts.get(1).get("items").get(0);
        assertEquals(4, tf.get("statements").size());
        assertEquals("a", tf.get("statements").get(0).get("key").asText());
        assertEquals("Ý a của câu", tf.get("statements").get(0).get("text").asText());
        assertEquals(2, tf.get("statements").get(0).size(), "ý chỉ có key và text");
        JsonNode sa = parts.get(2).get("items").get(0);
        assertEquals("sa", sa.get("questionType").asText());
        assertFalse(sa.has("options"));
        assertFalse(sa.has("statements"));

        Map<String, Object> row = jdbc.queryForMap("SELECT class_label, seat_no, token_hash, submitted_at"
                + " FROM assignment_attempt");
        assertEquals("8A1", row.get("class_label"));
        assertEquals(AuthTestSupport.sha256Hex(token), row.get("token_hash"));
        assertEquals(0, count("SELECT count(*) FROM assignment_attempt WHERE token_hash = ?", token));
        assertEquals(1, count("SELECT count(*) FROM assignment_attempt a JOIN exam_assignment e ON e.id ="
                + " a.assignment_id WHERE a.deadline_at = a.started_at + make_interval(mins => e.duration_minutes)"));
    }

    @Test
    void deadlineNeverPassesTheClosingTime() throws Exception {
        JsonNode a = assign(true);
        jdbc.update("UPDATE exam_assignment SET closes_at = now() + interval '10 minutes' WHERE id = ?",
                a.get("assignmentId").asInt());
        JsonNode payload = json(start(a.get("publicCode").asText(), "8A1", 1).andExpect(status().isCreated()));
        long remaining = payload.get("remainingSeconds").asLong();
        assertTrue(remaining > 9 * 60 && remaining <= 10 * 60, "remainingSeconds = " + remaining);
        assertEquals(1, count("SELECT count(*) FROM assignment_attempt a JOIN exam_assignment e ON e.id ="
                + " a.assignment_id WHERE a.deadline_at = e.closes_at"));
    }

    /**
     * Quyết định 16/11 (2): lớp phải là một lớp của bài giao, số thứ tự trong 1..sĩ số. Trùng lớp + số thứ tự vẫn vào được
     * (lượt mới, không ghi đè lượt cũ) và cả hai lượt bị đánh dấu. Quyết định 25/11: lần đầu báo trùng (409
     * ATTEMPT_IDENTITY_TAKEN, không tạo lượt); học sinh xác nhận ({@code confirmDuplicate}) thì mới tạo.
     */
    @Test
    void startAcceptsDuplicateSeatAndRejectsClassOrSeatOutsideTheRoster() throws Exception {
        String code = assign(true).get("publicCode").asText();
        String first = startToken(code, "8A1", 12);
        start(code, "8a1", 12).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTEMPT_IDENTITY_TAKEN"));
        assertEquals(1, count("SELECT count(*) FROM assignment_attempt WHERE class_label = '8A1' AND seat_no = 12"));
        JsonNode again = json(startConfirmed(code, "8a1", 12).andExpect(status().isCreated()));
        assertTrue(again.get("duplicateSeat").asBoolean());
        assertTrue(json(current(first).andExpect(status().isOk())).get("duplicateSeat").asBoolean());
        assertEquals(2, count("SELECT count(*) FROM assignment_attempt WHERE class_label = '8A1' AND seat_no = 12"));
        // Khác lớp hoặc khác số thứ tự thì không trùng.
        assertFalse(json(start(code, "8A2", 12).andExpect(status().isCreated())).get("duplicateSeat").asBoolean());
        start(code, "8A1", 40).andExpect(status().isCreated());

        for (String badClass : new String[] {"", "   ", "Nguyễn Văn An", "8A1<script>", "12345678901", "8A9", "9A1"}) {
            start(code, badClass, 1).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        for (int badSeat : new int[] {0, -1, 41, 100}) {
            start(code, "8A1", badSeat).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        mockMvc.perform(post("/api/public/assignments/" + code + "/attempts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"classLabel\": \"8A1\"}")).andExpect(status().isBadRequest());
        start("ZZZZZZZZZZ", "8A1", 1).andExpect(status().isNotFound());
        assertEquals(4, count("SELECT count(*) FROM assignment_attempt"));
    }

    /** Quyết định 16/11 (2): trần lượt = 2 × tổng sĩ số (2 lớp × 40 em = 160). */
    @Test
    void startStopsAtTwiceTheTotalRoster() throws Exception {
        JsonNode a = assign(true);
        jdbc.update("INSERT INTO assignment_attempt (assignment_id, class_label, seat_no, token_hash, started_at,"
                + " deadline_at) SELECT ?, '8A1', 1 + (g % 40), md5(g::text) || md5((g + 1)::text), now(),"
                + " now() + interval '45 minutes' FROM generate_series(0, 158) g", a.get("assignmentId").asInt());
        start(a.get("publicCode").asText(), "8A2", 1).andExpect(status().isCreated());
        start(a.get("publicCode").asText(), "8A2", 2).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_FULL"));
        assertEquals(160, count("SELECT count(*) FROM assignment_attempt"));
    }

    // ---------- Mở lại ----------

    @Test
    void currentNeedsAValidToken() throws Exception {
        String code = assign(true).get("publicCode").asText();
        String token = startToken(code, "8A1", 1);
        JsonNode payload = json(current(token).andExpect(status().isOk()));
        assertFalse(payload.has("attemptToken"), "token chỉ trả một lần lúc bắt đầu");
        assertEquals(3, payload.get("parts").size());
        assertNoAnswerLeak(payload);

        current(null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ATTEMPT_TOKEN_INVALID"));
        current("khong-phai-token").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ATTEMPT_TOKEN_INVALID"));
        // Băm của token không dùng thay token được.
        current(AuthTestSupport.sha256Hex(token)).andExpect(status().isUnauthorized());
    }

    // ---------- Nộp và chấm ----------

    /**
     * Quyết định 16/11 (1): khi bài còn mở, nộp bài chỉ trả "đã nộp" và mở lại cũng không có điểm; điểm chỉ có sau khi bài
     * đóng và hết ân hạn nộp.
     */
    @Test
    void submitGradesAllThreeTypesOnceAndShowsTheScoreOnlyAfterClosing() throws Exception {
        JsonNode a = assign(true);
        int id = a.get("assignmentId").asInt();
        String token = startToken(a.get("publicCode").asText(), "8A1", 1);

        JsonNode done = json(submit(token, sampleAnswers()).andExpect(status().isOk()));
        assertNotNull(done.get("submittedAt"));
        assertNotNull(done.get("resultsAt"));
        assertFalse(done.has("result"));
        assertNoAnswerLeak(done);

        assertEquals(4, count("SELECT count(*) FROM attempt_answer"));
        assertEquals("B|1|0.25", answerRow(mcqSlot1, "mcq_choice"));
        assertEquals("C|0|0.00", answerRow(mcqSlot2, "mcq_choice"));
        assertEquals("TTF-|2|0.25", answerRow(tfSlot, "tf_choices"));
        assertEquals("2.5|1|0.50", answerRow(saSlot, "sa_text"));
        assertEquals(1, count("SELECT count(*) FROM assignment_attempt WHERE submitted_at IS NOT NULL"
                + " AND score = 1.00 AND max_score = 2.00"));

        // Nộp lần hai: 409, dữ liệu giữ nguyên.
        submit(token, "[{\"slotId\": " + mcqSlot2 + ", \"choice\": \"B\"}]").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTEMPT_ALREADY_SUBMITTED"));
        assertEquals("C|0|0.00", answerRow(mcqSlot2, "mcq_choice"));

        // Mở lại khi bài còn mở: không còn câu hỏi, chưa có điểm.
        JsonNode again = json(current(token).andExpect(status().isOk()));
        assertTrue(again.get("submitted").asBoolean());
        assertFalse(again.has("parts"));
        assertFalse(again.get("resultsReleased").asBoolean());
        assertNoAnswerLeak(again);

        // Đóng tay nhưng còn trong ân hạn: vẫn chưa có điểm.
        asTeacher(post(base() + "/" + id + "/close")).andExpect(status().isOk());
        assertNoAnswerLeak(json(current(token).andExpect(status().isOk())));

        // Hết ân hạn: có điểm (bật showScore), không có đáp án (tắt showSolutions).
        jdbc.update("UPDATE exam_assignment SET closed_at = closed_at - interval '3 minutes' WHERE id = ?", id);
        JsonNode released = json(current(token).andExpect(status().isOk()));
        assertTrue(released.get("resultsReleased").asBoolean());
        JsonNode result = released.get("result");
        assertEquals(0, new BigDecimal("1.0").compareTo(result.get("score").decimalValue()));
        assertEquals(0, new BigDecimal("2.0").compareTo(result.get("maxScore").decimalValue()));
        assertEquals(0, new BigDecimal("5.0").compareTo(result.get("score10").decimalValue()));
        assertEquals(3, result.get("parts").size());
        assertFalse(released.has("review"));
        assertFalse(released.toString().contains("LOI-GIAI-BI-MAT"));
        assertFalse(released.toString().contains("correctOption"));
    }

    private String answerRow(int slot, String column) {
        return jdbc.queryForObject("SELECT " + column + " || '|' || correct_count || '|' || score FROM attempt_answer"
                + " WHERE slot_id = ?", String.class, slot);
    }

    @Test
    void unansweredQuestionsAreStoredAsBlank() throws Exception {
        String token = startToken(assign(true).get("publicCode").asText(), "8A1", 1);
        submit(token, "[]").andExpect(status().isOk());
        assertEquals(1, count("SELECT count(*) FROM assignment_attempt WHERE score = 0"));
        assertEquals(4, count("SELECT count(*) FROM attempt_answer WHERE correct_count = 0 AND score = 0"));
        assertEquals(1, count("SELECT count(*) FROM attempt_answer WHERE slot_id = ? AND tf_choices = '----'", tfSlot));
        assertEquals(3, count("SELECT count(*) FROM attempt_answer WHERE mcq_choice IS NULL AND sa_text IS NULL"
                + " AND tf_choices IS NULL"));
    }

    @Test
    void hiddenScoreIsNeverSentToTheStudent() throws Exception {
        JsonNode a = assign(false);
        String token = startToken(a.get("publicCode").asText(), "8A1", 1);
        JsonNode done = json(submit(token, sampleAnswers()).andExpect(status().isOk()));
        assertFalse(done.has("result"));
        closeAndPassGrace(a.get("assignmentId").asInt());
        JsonNode again = json(current(token).andExpect(status().isOk()));
        assertTrue(again.get("submitted").asBoolean());
        assertTrue(again.get("resultsReleased").asBoolean());
        assertFalse(again.has("result"));
        assertFalse(again.has("review"));
        assertFalse(again.toString().contains("score10"));
    }

    /**
     * Quyết định 16/11 (6): bật showSolutions thì SAU KHI bài đóng và hết ân hạn, học sinh mở lại bằng token của mình thấy
     * đáp án đúng và lời giải từng câu cạnh lựa chọn của mình. Khi bài còn mở (kể cả đã nộp) và trong ân hạn sau khi đóng:
     * tuyệt đối không có đáp án hay lời giải.
     */
    @Test
    void solutionsAreShownOnlyAfterTheAssignmentClosed() throws Exception {
        JsonNode a = assign(false, true);
        int id = a.get("assignmentId").asInt();
        String code = a.get("publicCode").asText();
        assertTrue(a.get("showSolutions").asBoolean());
        String token = startToken(code, "8A1", 1);
        String other = startToken(code, "8A2", 1);   // còn đang làm

        JsonNode done = json(submit(token, sampleAnswers()).andExpect(status().isOk()));
        assertNoAnswerLeak(done);
        assertNoAnswerLeak(json(current(token).andExpect(status().isOk())));
        assertNoAnswerLeak(json(current(other).andExpect(status().isOk())));

        // Giáo viên đóng; trong 2 phút ân hạn vẫn không lộ.
        asTeacher(post(base() + "/" + id + "/close")).andExpect(status().isOk());
        JsonNode inGrace = json(current(token).andExpect(status().isOk()));
        assertFalse(inGrace.get("resultsReleased").asBoolean());
        assertNoAnswerLeak(inGrace);

        jdbc.update("UPDATE exam_assignment SET closed_at = closed_at - interval '3 minutes' WHERE id = ?", id);
        JsonNode released = json(current(token).andExpect(status().isOk()));
        assertTrue(released.get("resultsReleased").asBoolean());
        assertFalse(released.has("result"), "showScore tắt: không có điểm tổng");
        assertFalse(released.has("parts"));
        JsonNode review = released.get("review");
        assertEquals(3, review.size());
        JsonNode mcq1 = review.get(0).get("items").get(0);
        assertEquals(mcqSlot1, mcq1.get("slotId").asInt());
        assertEquals("B", mcq1.get("yourChoice").asText());
        assertEquals("B", mcq1.get("correctOption").asText());
        assertEquals("LOI-GIAI-BI-MAT", mcq1.get("explanation").asText());
        assertEquals("$1$", mcq1.get("options").get("A").asText());
        JsonNode mcq2 = review.get(0).get("items").get(1);
        assertEquals("C", mcq2.get("yourChoice").asText());
        assertEquals("B", mcq2.get("correctOption").asText());
        assertEquals(0, mcq2.get("correctCount").asInt());
        JsonNode tf = review.get(1).get("items").get(0);
        assertEquals(2, tf.get("correctCount").asInt());
        JsonNode st = tf.get("statements");
        assertEquals("T", st.get(0).get("label").asText());
        assertEquals("T", st.get(0).get("yourAnswer").asText());
        assertTrue(st.get(0).get("correct").asBoolean());
        assertEquals("F", st.get(1).get("label").asText());
        assertFalse(st.get(1).get("correct").asBoolean());
        assertFalse(st.get(3).has("yourAnswer"), "ý d bỏ trống");
        JsonNode sa = review.get(2).get("items").get(0);
        assertEquals("2.5", sa.get("yourText").asText());
        assertEquals("2,5", sa.get("saAnswer").asText());
        assertEquals(1, sa.get("correctCount").asInt());

        // Lượt chưa nộp: vẫn xem được đáp án, phần của em để trống.
        JsonNode otherReview = json(current(other).andExpect(status().isOk())).get("review");
        assertFalse(otherReview.get(0).get("items").get(0).has("yourChoice"));
        assertEquals("B", otherReview.get(0).get("items").get(0).get("correctOption").asText());
    }

    @Test
    void invalidAnswersAreRejectedWithoutSavingAnything() throws Exception {
        String token = startToken(assign(true).get("publicCode").asText(), "8A1", 1);
        String[] bad = {
                "[{\"slotId\": 999999, \"choice\": \"A\"}]",                                        // chỗ lạ
                "[{\"slotId\": " + mcqSlot1 + ", \"choice\": \"E\"}]",                             // ngoài A-D
                "[{\"slotId\": " + mcqSlot1 + ", \"text\": \"5\"}]",                               // sai dạng câu
                "[{\"slotId\": " + tfSlot + ", \"tf\": {\"e\": \"T\"}}]",                          // ý lạ
                "[{\"slotId\": " + tfSlot + ", \"tf\": {\"a\": \"X\"}}]",                          // giá trị lạ
                "[{\"slotId\": " + saSlot + ", \"text\": \"123456789012345678901\"}]",             // dài quá 20
                "[{\"slotId\": " + mcqSlot1 + ", \"choice\": \"A\"}, {\"slotId\": " + mcqSlot1 + ", \"choice\": \"B\"}]",
                "[{\"choice\": \"A\"}]",                                                             // thiếu slotId
                // AN-07: nhiều phần tử hơn số câu của đề.
                "[{\"slotId\": " + mcqSlot1 + "}, {\"slotId\": " + mcqSlot2 + "}, {\"slotId\": " + tfSlot + "},"
                        + " {\"slotId\": " + saSlot + "}, {\"slotId\": 999999}]",
        };
        for (String answers : bad) {
            submit(token, answers).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        mockMvc.perform(post("/api/public/attempts/current/submit").header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        assertEquals(0, count("SELECT count(*) FROM attempt_answer"));
        assertEquals(1, count("SELECT count(*) FROM assignment_attempt WHERE submitted_at IS NULL"));
        // Sau các lần bị từ chối vẫn nộp được.
        submit(token, sampleAnswers()).andExpect(status().isOk());

        mockMvc.perform(post("/api/public/attempts/current/submit").contentType(MediaType.APPLICATION_JSON)
                .content("{\"answers\": []}")).andExpect(status().isUnauthorized());
    }

    /** Quyết định 16/11 (5): ân hạn nộp 2 phút sau hạn của lượt. */
    @Test
    void deadlineIsEnforcedOnTheServerWithATwoMinuteGrace() throws Exception {
        String code = assign(true).get("publicCode").asText();
        String late = startToken(code, "8A1", 1);
        String tooLate = startToken(code, "8A1", 2);
        jdbc.update("UPDATE assignment_attempt SET deadline_at = now() - interval '90 seconds' WHERE token_hash = ?",
                AuthTestSupport.sha256Hex(late));
        jdbc.update("UPDATE assignment_attempt SET deadline_at = now() - interval '150 seconds' WHERE token_hash = ?",
                AuthTestSupport.sha256Hex(tooLate));

        // Trễ 90 giây: còn trong 2 phút ân hạn (thiết kế 14/11 chỉ cho 30 giây).
        submit(late, sampleAnswers()).andExpect(status().isOk());
        submit(tooLate, sampleAnswers()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ATTEMPT_EXPIRED"));
        assertEquals(1, count("SELECT count(*) FROM assignment_attempt WHERE submitted_at IS NOT NULL"));
        assertEquals(0, json(current(tooLate).andExpect(status().isOk())).get("remainingSeconds").asLong());
    }

    /** Hết hạn đóng (đóng lười): lượt có hạn đúng bằng hạn đóng vẫn nộp được trong ân hạn. */
    @Test
    void lazyCloseKeepsTheGraceForAttemptsEndingAtTheClosingTime() throws Exception {
        JsonNode a = assign(true);
        String token = startToken(a.get("publicCode").asText(), "8A1", 1);
        jdbc.update("UPDATE exam_assignment SET closes_at = now() - interval '60 seconds' WHERE id = ?",
                a.get("assignmentId").asInt());
        jdbc.update("UPDATE assignment_attempt t SET deadline_at = e.closes_at FROM exam_assignment e"
                + " WHERE e.id = t.assignment_id");
        submit(token, sampleAnswers()).andExpect(status().isOk());
        assertEquals(1, count("SELECT count(*) FROM exam_assignment WHERE status = 'closed'"));
        // Kết quả chưa mở vì chưa hết ân hạn tính từ lúc đóng.
        assertFalse(json(current(token).andExpect(status().isOk())).get("resultsReleased").asBoolean());
    }

    @Test
    void concurrentSubmitsSucceedExactlyOnce() throws Exception {
        String token = startToken(assign(true).get("publicCode").asText(), "8A1", 1);
        int threads = 6;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Integer> call = () -> {
                    go.await();
                    MvcResult r = submit(token, sampleAnswers()).andReturn();
                    return r.getResponse().getStatus();
                };
                results.add(pool.submit(call));
            }
            go.countDown();
            int ok = 0;
            int conflict = 0;
            for (Future<Integer> f : results) {
                int s = f.get();
                if (s == 200) {
                    ok++;
                } else if (s == 409) {
                    conflict++;
                }
            }
            assertEquals(1, ok, "đúng một lần nộp thành công");
            assertEquals(threads - 1, conflict);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(4, count("SELECT count(*) FROM attempt_answer"));
    }

    /**
     * AN-03, quyết định 16/11 (5): hai lớp chung một IP (wifi trường) với hạn mức mặc định: 80 em cùng bắt đầu, tải lại
     * và nộp trong một phút, không em nào nhận 429.
     */
    @Test
    void twoClassesBehindOneIpCanStartReloadAndSubmit() throws Exception {
        String code = assign(true).get("publicCode").asText();
        List<String> tokens = new ArrayList<>();
        for (String cls : new String[] {"8A1", "8A2"}) {
            for (int seat = 1; seat <= 40; seat++) {
                MvcResult r = mockMvc.perform(post("/api/public/assignments/" + code + "/attempts")
                        .with(req -> {
                            req.setRemoteAddr("10.40.0.1");
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classLabel\": \"" + cls + "\", \"seatNo\": " + seat + "}"))
                        .andExpect(status().isCreated()).andReturn();
                tokens.add(objectMapper.readTree(r.getResponse().getContentAsString()).get("attemptToken").asText());
            }
        }
        for (String token : tokens) {
            current(token).andExpect(status().isOk());
            submit(token, sampleAnswers()).andExpect(status().isOk());
        }
        assertEquals(80, count("SELECT count(*) FROM assignment_attempt WHERE submitted_at IS NOT NULL"));
    }

    // ---------- Ranh giới quyền ----------

    @Test
    void publicPathsDoNotOpenAnythingElse() throws Exception {
        assign(true);
        mockMvc.perform(get("/api/public/exams")).andExpect(status().is4xxClientError());
        mockMvc.perform(get("/api/exams")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/bank-items")).andExpect(status().isUnauthorized());
    }
}
