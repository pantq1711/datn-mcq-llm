package com.datn.mcq.assignment;

import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Giao bài, đóng, xóa và bảng điểm của giáo viên (docs/design/lam-bai-truc-tuyen.md mục 2, 3.1; quyết định 16/11): chỉ
 * giao đề mà mọi chỗ đã Giữ, giáo viên khai lớp + sĩ số, thành phần đề chốt lúc giao, lượt trùng số thứ tự được giữ và
 * đánh dấu, xóa nhiều lượt, đề còn bài giao không xóa được, chỉ chủ đề thao tác được.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class AssignmentTeacherApiTest extends AssignmentTestBase {

    private static Instant inTwoHours() {
        return Instant.now().plus(2, ChronoUnit.HOURS);
    }

    // ---------- Giao bài ----------

    @Test
    void createFreezesItemsInExamPageOrder() throws Exception {
        JsonNode a = assign(true);
        int id = a.get("assignmentId").asInt();
        assertTrue(a.get("publicCode").asText().matches("[A-Z2-9]{10}"), a.get("publicCode").asText());
        assertEquals("open", a.get("status").asText());
        assertEquals("Đề giữa kì I", a.get("title").asText());
        assertEquals(45, a.get("durationMinutes").asInt());
        assertTrue(a.get("showScore").asBoolean());
        assertFalse(a.get("showSolutions").asBoolean());
        assertEquals(4, a.get("questionCount").asInt());
        // 2 trắc nghiệm x 0,25 + 1 Đúng/Sai x 1,0 + 1 Trả lời ngắn x 0,5
        assertEquals(0, new BigDecimal("2.0").compareTo(a.get("maxScore").decimalValue()));
        assertEquals(0, a.get("startedCount").asInt());
        // Lớp chuẩn hóa ("8a 2" -> "8A2"), giữ thứ tự khai; trần lượt = 2 x tổng sĩ số.
        assertEquals("8A1", a.get("classes").get(0).get("classLabel").asText());
        assertEquals("8A2", a.get("classes").get(1).get("classLabel").asText());
        assertEquals(40, a.get("classes").get(1).get("size").asInt());
        assertEquals(160, a.get("maxAttempts").asInt());

        List<Map<String, Object>> items = jdbc.queryForList("SELECT slot_id, bank_item_id, question_type, part,"
                + " number_in_part, position FROM assignment_item WHERE assignment_id = ? ORDER BY position", id);
        assertEquals(4, items.size());
        assertEquals(List.of(mcqSlot1, mcqSlot2, tfSlot, saSlot), items.stream().map(r -> r.get("slot_id")).toList());
        assertEquals(List.of(mcqItem1, mcqItem2, tfItem, saItem),
                items.stream().map(r -> r.get("bank_item_id")).toList());
        assertEquals(List.of("I", "I", "II", "III"), items.stream().map(r -> r.get("part")).toList());
        assertEquals(List.of(1, 2, 1, 1), items.stream().map(r -> r.get("number_in_part")).toList());
        assertEquals(List.of("8A1|40", "8A2|40"), jdbc.queryForList("SELECT class_label || '|' || roster_size FROM"
                + " assignment_class WHERE assignment_id = ? ORDER BY position", String.class, id));

        // Bảng điểm lúc giao được chụp lại trong bài giao.
        assertEquals("0.25", jdbc.queryForObject("SELECT scoring ->> 'mcq' FROM exam_assignment WHERE id = ?",
                String.class, id));
        assertEquals(5, count("SELECT jsonb_array_length(scoring -> 'tf') FROM exam_assignment WHERE id = ?", id));
        assertEquals(1, count("SELECT count(*) FROM exam_assignment WHERE id = ? AND consent_confirmed_at IS NOT NULL",
                id));

        asTeacher(get(base())).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].assignmentId").value(id))
                .andExpect(jsonPath("$[0].attempts").doesNotExist());
    }

    @Test
    void createRejectsExamWithSlotNotKept() throws Exception {
        // Chỗ lấy từ ngân hàng còn 'ready' (có bank_item nhưng chưa bấm Giữ) cũng không được giao.
        jdbc.update("UPDATE exam_slot SET state = 'ready' WHERE id = ?", mcqSlot2);
        asTeacher(post(base()).contentType(MediaType.APPLICATION_JSON)
                .content(createBody(45, inTwoHours(), true, true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGN_NOT_ALL_KEPT"))
                .andExpect(jsonPath("$.errors[0]").value("Phần I, Câu 2"));
        assertEquals(0, count("SELECT count(*) FROM exam_assignment"));
    }

    @Test
    void createRejectsShortAnswerKeyThatCannotBeTypedOnAPhone() throws Exception {
        jdbc.update("UPDATE bank_item SET sa_answer = '\\frac{5}{2}' WHERE id = ?", saItem);
        asTeacher(post(base()).contentType(MediaType.APPLICATION_JSON)
                .content(createBody(45, inTwoHours(), true, true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGN_SA_KEY_UNSUPPORTED"))
                .andExpect(jsonPath("$.errors[0]").value("Phần III, Câu 1"));
        assertEquals(0, count("SELECT count(*) FROM exam_assignment"));
    }

    /** Gắn một ảnh (dữ liệu tự tạo) vào câu nguồn của câu ngân hàng {@code bankItem}, role {@code role}. */
    private void attachImage(int bankItem, String role) {
        int sourceQuestion = jdbc.queryForObject("SELECT source_question_id FROM bank_item WHERE id = ?",
                Integer.class, bankItem);
        int batch = insert("INSERT INTO import_batch (owner_id, display_name, file_sha256, subject_id, grade_level,"
                + " topic, status, question_count, preview, created_at, expires_at) VALUES (?, 'Lô ảnh giả',"
                + " repeat('a', 64), ?, 8, 'Đa thức', 'preview', 1, '{}'::jsonb, CURRENT_TIMESTAMP,"
                + " CURRENT_TIMESTAMP + INTERVAL '24 hours')", teacher, subject);
        jdbc.update("INSERT INTO question_image (owner_id, import_batch_id, item_key, generated_question_id, role,"
                + " ordinal, mime, data, byte_size, width_px, height_px, display_width_mm, sha256, source_format)"
                + " VALUES (?, ?, 'q1', ?, ?, 1, 'image/png', ?, 4, 10, 10, 50, repeat('b', 64), 'png')",
                teacher, batch, sourceQuestion, role, new byte[]{1, 2, 3, 4});
    }

    @Test
    void createRejectsExamWithFigureQuestion() throws Exception {
        // Học sinh chưa có đường xem ảnh (A1, thiết kế cau-co-hinh.md mục 4): đề có một chỗ có ảnh thì không giao.
        attachImage(mcqItem2, "stem");
        asTeacher(post(base()).contentType(MediaType.APPLICATION_JSON)
                .content(createBody(45, inTwoHours(), true, true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGN_HAS_FIGURE"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors[0]").value("Phần I, Câu 2"));
        // Không ghi gì vào DB.
        assertEquals(0, count("SELECT count(*) FROM exam_assignment"));
        assertEquals(0, count("SELECT count(*) FROM assignment_item"));
        assertEquals(0, count("SELECT count(*) FROM assignment_class"));
    }

    @Test
    void createRejectsFigureOnRemovedBankItemAndListsEverySlotWithFigure() throws Exception {
        // Câu đã gỡ khỏi ngân hàng vẫn nằm trong đề (quyết định 16/11) nên vẫn chặn; ảnh role solution cũng tính.
        attachImage(mcqItem1, "solution");
        jdbc.update("UPDATE bank_item SET removed_at = CURRENT_TIMESTAMP, removed_by = ? WHERE id = ?", teacher,
                mcqItem1);
        attachImage(tfItem, "statement_b");
        asTeacher(post(base()).contentType(MediaType.APPLICATION_JSON)
                .content(createBody(45, inTwoHours(), true, true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGN_HAS_FIGURE"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0]").value("Phần I, Câu 1"))
                .andExpect(jsonPath("$.errors[1]").value("Phần II, Câu 1"));
        assertEquals(0, count("SELECT count(*) FROM exam_assignment"));
    }

    @Test
    void createStillAcceptsExamWhoseFigureBelongsToAnotherQuestion() throws Exception {
        // Ảnh của một câu không nằm trong đề này không ảnh hưởng: đề không hình giao được như cũ.
        int otherItem = bankMcq(template("Khác"), "Câu ngoài đề");
        attachImage(otherItem, "stem");
        asTeacher(post(base()).contentType(MediaType.APPLICATION_JSON)
                .content(createBody(45, inTwoHours(), true, true)))
                .andExpect(status().isCreated());
        assertEquals(1, count("SELECT count(*) FROM exam_assignment"));
    }

    @Test
    void createValidatesRequest() throws Exception {
        StringBuilder elevenClasses = new StringBuilder("[");
        for (int i = 1; i <= 11; i++) {
            elevenClasses.append(i > 1 ? ", " : "").append("{\"classLabel\": \"6A").append(i).append("\", \"size\": 30}");
        }
        elevenClasses.append("]");
        String[] bad = {
                createBody(45, inTwoHours(), true, false),                               // chưa tick đồng ý
                createBody(4, inTwoHours(), true, true),                                 // thời gian quá ngắn
                createBody(181, inTwoHours(), true, true),                               // quá dài
                createBody(45, Instant.now().minus(1, ChronoUnit.MINUTES), true, true),  // hạn đóng đã qua
                createBody(45, Instant.now().plus(10, ChronoUnit.MINUTES), true, true),  // hạn đóng sớm hơn thời gian làm
                createBody(45, Instant.now().plus(31, ChronoUnit.DAYS), true, true),     // quá 30 ngày
                "{\"durationMinutes\": 45, \"showScore\": true, \"consentConfirmed\": true, \"classes\": "
                        + DEFAULT_CLASSES + "}",                                         // thiếu hạn đóng
                // Quyết định 16/11: lớp + sĩ số bắt buộc và hợp lệ.
                createBody(45, inTwoHours(), true, false, true, null),                    // không khai lớp
                createBody(45, inTwoHours(), true, false, true, "[]"),
                createBody(45, inTwoHours(), true, false, true, "[{\"classLabel\": \"Lớp Văn\", \"size\": 30}]"),
                createBody(45, inTwoHours(), true, false, true, "[{\"classLabel\": \"8A1\", \"size\": 0}]"),
                createBody(45, inTwoHours(), true, false, true, "[{\"classLabel\": \"8A1\", \"size\": 100}]"),
                createBody(45, inTwoHours(), true, false, true, "[{\"classLabel\": \"8A1\"}]"),
                createBody(45, inTwoHours(), true, false, true,
                        "[{\"classLabel\": \"8A1\", \"size\": 30}, {\"classLabel\": \" 8a1\", \"size\": 30}]"),
                createBody(45, inTwoHours(), true, false, true, elevenClasses.toString()),
        };
        for (String body : bad) {
            asTeacher(post(base()).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        assertEquals(0, count("SELECT count(*) FROM exam_assignment"));
    }

    @Test
    void replacingASlotAfterAssigningDoesNotChangeTheAssignment() throws Exception {
        JsonNode a = assign(true);
        String token = startToken(a.get("publicCode").asText(), "8A1", 1);
        // Giáo viên Đổi câu ở chỗ 1 sau khi giao: exam_slot trỏ sang câu khác.
        int other = bankMcq(template("Khác"), "Câu thay thế sau khi giao");
        jdbc.update("UPDATE exam_slot SET bank_item_id = ? WHERE id = ?", other, mcqSlot1);

        JsonNode payload = json(current(token).andExpect(status().isOk()));
        assertEquals("Giá trị của $x^2$ tại $x = 3$ là",
                payload.get("parts").get(0).get("items").get(0).get("questionText").asText());
        assertEquals(mcqItem1, count("SELECT bank_item_id FROM assignment_item WHERE assignment_id = ? AND slot_id = ?",
                a.get("assignmentId").asInt(), mcqSlot1));
    }

    // ---------- Quyền ----------

    @Test
    void onlyTheOwnerTeacherCanUseTeacherEndpoints() throws Exception {
        int id = assign(true).get("assignmentId").asInt();
        String body = createBody(45, inTwoHours(), true, true);
        String bulk = "{\"attemptIds\": [1]}";

        // Giáo viên khác: 404 như id không tồn tại.
        asUser(otherTeacher, post(base()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        asUser(otherTeacher, get(base())).andExpect(status().isNotFound());
        asUser(otherTeacher, get(base() + "/" + id)).andExpect(status().isNotFound());
        asUser(otherTeacher, post(base() + "/" + id + "/close")).andExpect(status().isNotFound());
        asUser(otherTeacher, post(base() + "/" + id + "/attempts/bulk-delete").contentType(MediaType.APPLICATION_JSON)
                .content(bulk)).andExpect(status().isNotFound());
        asUser(otherTeacher, delete(base() + "/" + id)).andExpect(status().isNotFound());
        // Bài giao không thuộc đề trong đường dẫn.
        asTeacher(get("/api/exams/" + (exam + 999) + "/assignments/" + id)).andExpect(status().isNotFound());
        asTeacher(get(base() + "/" + (id + 999))).andExpect(status().isNotFound());

        // Admin không dùng chức năng giáo viên; không đăng nhập thì 401.
        mockMvc.perform(get(base() + "/" + id).with(AuthTestSupport.admin(admin))).andExpect(status().isForbidden());
        mockMvc.perform(get(base() + "/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(base()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(base() + "/" + id + "/attempts/bulk-delete").contentType(MediaType.APPLICATION_JSON)
                .content(bulk)).andExpect(status().isUnauthorized());

        assertEquals(1, count("SELECT count(*) FROM exam_assignment WHERE status = 'open'"));
    }

    /**
     * GV B gọi TỪNG endpoint giáo viên với bài giao của GV A, theo hai đường: qua đề của A, và qua đề của chính B kèm id bài
     * giao của A. Mọi lần đều 404 như id không tồn tại (quy ước của exam), thân lỗi không chứa dữ liệu của bài giao, và
     * không có gì bị sửa hay xóa.
     */
    @Test
    void anotherTeacherGetsNotFoundOnEveryEndpointAndChangesNothing() throws Exception {
        JsonNode a = assign(true);
        int id = a.get("assignmentId").asInt();
        String code = a.get("publicCode").asText();
        submit(startToken(code, "8A1", 1), sampleAnswers()).andExpect(status().isOk());
        startToken(code, "8A1", 2);
        int attempt = count("SELECT min(id) FROM assignment_attempt WHERE assignment_id = ?", id);
        int matrixOfB = insert("INSERT INTO exam_matrix (name, subject_id, created_by) VALUES ('Ma trận của B', ?, ?)",
                subject, otherTeacher);
        int examOfB = insert("INSERT INTO exam (exam_code, title, subject_id, created_by, mode, matrix_id) VALUES ('GB2',"
                + " 'Đề của thầy Bình', ?, ?, 'teacher', ?)", subject, otherTeacher, matrixOfB);
        String body = createBody(45, inTwoHours(), true, true);
        String bulk = "{\"attemptIds\": [" + attempt + "]}";

        for (String prefix : new String[] {base(), "/api/exams/" + examOfB + "/assignments"}) {
            List<org.springframework.test.web.servlet.ResultActions> calls = new java.util.ArrayList<>();
            if (prefix.equals(base())) {
                calls.add(asUser(otherTeacher, post(prefix).contentType(MediaType.APPLICATION_JSON).content(body)));
                calls.add(asUser(otherTeacher, get(prefix)));
            }
            calls.add(asUser(otherTeacher, get(prefix + "/" + id)));
            calls.add(asUser(otherTeacher, post(prefix + "/" + id + "/close")));
            calls.add(asUser(otherTeacher, delete(prefix + "/" + id + "/attempts/" + attempt)));
            calls.add(asUser(otherTeacher, post(prefix + "/" + id + "/attempts/bulk-delete")
                    .contentType(MediaType.APPLICATION_JSON).content(bulk)));
            calls.add(asUser(otherTeacher, delete(prefix + "/" + id)));
            for (org.springframework.test.web.servlet.ResultActions call : calls) {
                call.andExpect(status().isNotFound());
                String response = call.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
                assertFalse(response.contains(code), response);
                assertFalse(response.contains("8A1"), response);
                assertFalse(response.contains("Đề giữa kì I"), response);
            }
        }
        // B xem danh sách bài giao của đề mình: không thấy bài của A.
        asUser(otherTeacher, get("/api/exams/" + examOfB + "/assignments")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        assertEquals(1, count("SELECT count(*) FROM exam_assignment"));
        assertEquals(1, count("SELECT count(*) FROM exam_assignment WHERE id = ? AND status = 'open'", id));
        assertEquals(2, count("SELECT count(*) FROM assignment_attempt WHERE assignment_id = ?", id));
        assertEquals(4, count("SELECT count(*) FROM attempt_answer"));
        // A vẫn thao tác bình thường.
        asTeacher(get(base() + "/" + id)).andExpect(status().isOk()).andExpect(jsonPath("$.attempts.length()").value(2));
    }

    // ---------- Bảng điểm ----------

    @Test
    void scoreboardShowsStatesAndPartScores() throws Exception {
        JsonNode a = assign(true);
        int id = a.get("assignmentId").asInt();
        String code = a.get("publicCode").asText();
        String submitted = startToken(code, "8A2", 7);
        startToken(code, "8A1", 12);                    // đang làm
        String expired = startToken(code, "8A1", 3);    // quá hạn, không nộp
        submit(submitted, sampleAnswers()).andExpect(status().isOk());
        jdbc.update("UPDATE assignment_attempt SET deadline_at = now() - interval '10 minutes' WHERE token_hash = ?",
                AuthTestSupport.sha256Hex(expired));

        JsonNode board = json(asTeacher(get(base() + "/" + id)).andExpect(status().isOk()));
        assertEquals(3, board.get("startedCount").asInt());
        assertEquals(1, board.get("submittedCount").asInt());
        assertEquals(0, board.get("duplicateCount").asInt());
        JsonNode rows = board.get("attempts");
        // Sắp theo lớp (thứ tự khai lúc giao) rồi số thứ tự.
        assertEquals("8A1", rows.get(0).get("classLabel").asText());
        assertEquals(3, rows.get(0).get("seatNo").asInt());
        assertEquals("expired", rows.get(0).get("state").asText());
        assertFalse(rows.get(0).get("duplicate").asBoolean());
        assertEquals(12, rows.get(1).get("seatNo").asInt());
        assertEquals("in_progress", rows.get(1).get("state").asText());
        assertFalse(rows.get(1).has("score"));

        JsonNode done = rows.get(2);
        assertEquals("8A2", done.get("classLabel").asText());
        assertEquals("submitted", done.get("state").asText());
        // 0,25 (câu 1) + 0 (câu 2) + 0,25 (2 ý đúng) + 0,5 (trả lời ngắn) = 1,0 trên 2,0 → 5,0 thang 10
        assertEquals(0, new BigDecimal("1.0").compareTo(done.get("score").decimalValue()));
        assertEquals(0, new BigDecimal("5.0").compareTo(done.get("score10").decimalValue()));
        JsonNode parts = done.get("parts");
        assertEquals("I", parts.get(0).get("part").asText());
        assertEquals(1, parts.get(0).get("correct").asInt());
        assertEquals(2, parts.get(0).get("total").asInt());
        assertEquals("II", parts.get(1).get("part").asText());
        assertEquals(2, parts.get(1).get("correct").asInt());
        assertEquals(4, parts.get(1).get("total").asInt());   // Phần II đếm theo ý
        assertEquals(0, new BigDecimal("0.25").compareTo(parts.get(1).get("score").decimalValue()));
        assertEquals(1, parts.get(2).get("correct").asInt());
        // Bảng điểm không lộ token.
        assertFalse(board.toString().contains("token"), board.toString());
    }

    /** Quyết định 16/11 (2): trùng lớp + số thứ tự thì giữ cả hai lượt, cả hai bị đánh dấu, không lượt nào bị ghi đè. */
    @Test
    void duplicateSeatKeepsBothAttemptsAndFlagsBoth() throws Exception {
        JsonNode a = assign(true);
        int id = a.get("assignmentId").asInt();
        String code = a.get("publicCode").asText();
        String first = startToken(code, "8A1", 12);
        submit(first, sampleAnswers()).andExpect(status().isOk());
        String second = startToken(code, "8A1", 12);
        startToken(code, "8A1", 13);

        JsonNode rows = json(asTeacher(get(base() + "/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicateCount").value(2))).get("attempts");
        assertEquals(3, rows.size());
        assertEquals(12, rows.get(0).get("seatNo").asInt());
        assertTrue(rows.get(0).get("duplicate").asBoolean());
        assertEquals("submitted", rows.get(0).get("state").asText());   // lượt đầu giữ nguyên điểm
        assertEquals(12, rows.get(1).get("seatNo").asInt());
        assertTrue(rows.get(1).get("duplicate").asBoolean());
        assertEquals("in_progress", rows.get(1).get("state").asText());
        assertFalse(rows.get(2).get("duplicate").asBoolean());
        assertEquals(1, count("SELECT count(*) FROM attempt_answer a JOIN assignment_attempt t ON t.id = a.attempt_id"
                + " WHERE t.token_hash = ? AND a.slot_id = ?", AuthTestSupport.sha256Hex(first), mcqSlot1));

        // Giáo viên xóa lượt sai: lượt còn lại hết bị đánh dấu (cờ tính lúc đọc, không lưu).
        int secondId = count("SELECT id FROM assignment_attempt WHERE token_hash = ?", AuthTestSupport.sha256Hex(second));
        asTeacher(delete(base() + "/" + id + "/attempts/" + secondId)).andExpect(status().isNoContent());
        JsonNode after = json(asTeacher(get(base() + "/" + id)).andExpect(status().isOk()));
        assertEquals(0, after.get("duplicateCount").asInt());
        assertFalse(after.get("attempts").get(0).get("duplicate").asBoolean());
    }

    // ---------- Đóng, xóa ----------

    @Test
    void closeIsIdempotentAndStopsNewAndUnfinishedAttempts() throws Exception {
        JsonNode a = assign(true);
        int id = a.get("assignmentId").asInt();
        String code = a.get("publicCode").asText();
        String unfinished = startToken(code, "8A1", 1);

        asTeacher(post(base() + "/" + id + "/close")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("closed"));
        String closedAt = jdbc.queryForObject("SELECT closed_at::text FROM exam_assignment WHERE id = ?", String.class,
                id);
        asTeacher(post(base() + "/" + id + "/close")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("closed"));
        assertEquals(closedAt, jdbc.queryForObject("SELECT closed_at::text FROM exam_assignment WHERE id = ?",
                String.class, id));

        start(code, "8A1", 2).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_CLOSED"));
        // Lượt đang làm dở lúc giáo viên đóng tay: không nộp được nữa, kể cả trong ân hạn.
        submit(unfinished, sampleAnswers()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_CLOSED"));
        assertEquals(0, count("SELECT count(*) FROM attempt_answer"));
        asTeacher(get(base() + "/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.attempts[0].state").value("expired"));
    }

    @Test
    void assignmentClosesLazilyAfterClosesAt() throws Exception {
        JsonNode a = assign(true);
        int id = a.get("assignmentId").asInt();
        jdbc.update("UPDATE exam_assignment SET closes_at = now() - interval '1 minute' WHERE id = ?", id);

        mockMvc.perform(get("/api/public/assignments/" + a.get("publicCode").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("closed"));
        assertEquals(1, count("SELECT count(*) FROM exam_assignment WHERE id = ? AND status = 'closed'"
                + " AND closed_at = closes_at", id));
        start(a.get("publicCode").asText(), "8A1", 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_CLOSED"));
    }

    /** T5 khi bài mở: học sinh mất máy thì vào lại được; sau khi đóng vẫn xóa được (quyết định 16/11, ý 4). */
    @Test
    void deletingAnAttemptWorksWhileOpenAndAfterClosing() throws Exception {
        JsonNode a = assign(true);
        int id = a.get("assignmentId").asInt();
        String code = a.get("publicCode").asText();
        String token = startToken(code, "8A1", 5);
        submit(token, sampleAnswers()).andExpect(status().isOk());
        int attempt = count("SELECT id FROM assignment_attempt WHERE assignment_id = ?", id);

        asUser(otherTeacher, delete(base() + "/" + id + "/attempts/" + attempt)).andExpect(status().isNotFound());
        asTeacher(delete(base() + "/" + id + "/attempts/" + (attempt + 999))).andExpect(status().isNotFound());
        asTeacher(delete(base() + "/" + id + "/attempts/" + attempt)).andExpect(status().isNoContent());
        assertEquals(0, count("SELECT count(*) FROM assignment_attempt"));
        assertEquals(0, count("SELECT count(*) FROM attempt_answer"));
        current(token).andExpect(status().isUnauthorized());
        // Cùng lớp + số thứ tự vào lại được.
        String again = startToken(code, "8A1", 5);

        submit(again, sampleAnswers()).andExpect(status().isOk());

        // Đã đóng và đã qua ân hạn: vẫn xóa được, dữ liệu mất hẳn.
        closeAndPassGrace(id);
        int second = count("SELECT id FROM assignment_attempt WHERE token_hash = ?", AuthTestSupport.sha256Hex(again));
        asTeacher(delete(base() + "/" + id + "/attempts/" + second)).andExpect(status().isNoContent());
        assertEquals(0, count("SELECT count(*) FROM assignment_attempt"));
        assertEquals(0, count("SELECT count(*) FROM attempt_answer"));
        current(again).andExpect(status().isUnauthorized());
    }

    /** Quyết định 16/11 (2, 4): xóa nhiều lượt một lần; id của bài giao khác bị bỏ qua; làm được cả sau khi đóng. */
    @Test
    void bulkDeleteRemovesOnlyTheChosenAttemptsOfThisAssignment() throws Exception {
        JsonNode a = assign(true);
        int id = a.get("assignmentId").asInt();
        String code = a.get("publicCode").asText();
        submit(startToken(code, "8A1", 1), sampleAnswers()).andExpect(status().isOk());
        startToken(code, "8A1", 2);
        startToken(code, "8A2", 3);
        List<Integer> ids = jdbc.queryForList("SELECT id FROM assignment_attempt WHERE assignment_id = ? ORDER BY seat_no",
                Integer.class, id);
        JsonNode other = assign(true);
        startToken(other.get("publicCode").asText(), "8A1", 1);
        int otherAttempt = count("SELECT id FROM assignment_attempt WHERE assignment_id = ?",
                other.get("assignmentId").asInt());

        String body = "{\"attemptIds\": [" + ids.get(0) + ", " + ids.get(1) + ", " + otherAttempt + ", 999999]}";
        asTeacher(post(base() + "/" + id + "/attempts/bulk-delete").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.deleted").value(2));
        assertEquals(List.of(ids.get(2)), jdbc.queryForList("SELECT id FROM assignment_attempt WHERE assignment_id = ?",
                Integer.class, id));
        assertEquals(1, count("SELECT count(*) FROM assignment_attempt WHERE id = ?", otherAttempt));
        assertEquals(0, count("SELECT count(*) FROM attempt_answer"));

        for (String bad : new String[] {"{}", "{\"attemptIds\": []}", "{\"attemptIds\": [null]}"}) {
            asTeacher(post(base() + "/" + id + "/attempts/bulk-delete").contentType(MediaType.APPLICATION_JSON)
                    .content(bad)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        closeAndPassGrace(id);
        asTeacher(post(base() + "/" + id + "/attempts/bulk-delete").contentType(MediaType.APPLICATION_JSON)
                .content("{\"attemptIds\": [" + ids.get(2) + "]}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(1));
        assertEquals(0, count("SELECT count(*) FROM assignment_attempt WHERE assignment_id = ?", id));
    }

    @Test
    void deletingAnAssignmentRemovesAllStudentData() throws Exception {
        JsonNode a = assign(true);
        int id = a.get("assignmentId").asInt();
        String code = a.get("publicCode").asText();
        submit(startToken(code, "8A1", 5), sampleAnswers()).andExpect(status().isOk());
        startToken(code, "8A1", 6);
        // Quyết định 16/11 (4): xóa được cả khi bài đã đóng.
        closeAndPassGrace(id);

        asTeacher(delete(base() + "/" + id)).andExpect(status().isNoContent());
        for (String table : new String[] {"exam_assignment", "assignment_class", "assignment_item", "assignment_attempt",
                "attempt_answer"}) {
            assertEquals(0, count("SELECT count(*) FROM " + table), table);
        }
        mockMvc.perform(get("/api/public/assignments/" + code)).andExpect(status().isNotFound());
        // Đề và ngân hàng không bị đụng tới.
        assertEquals(4, count("SELECT count(*) FROM exam_slot WHERE exam_id = ?", exam));
    }

    /**
     * Quyết định 16/11 (3), AN-04: đề còn bài giao (mở hay đã đóng) thì xóa đề trả 409 EXAM_HAS_ASSIGNMENTS và không đổi
     * gì; xóa hết bài giao rồi thì xóa đề được.
     */
    @Test
    void examWithAssignmentsCannotBeDeleted() throws Exception {
        JsonNode a = assign(true);
        int id = a.get("assignmentId").asInt();
        String code = a.get("publicCode").asText();
        startToken(code, "8A1", 1);
        asTeacher(post(base() + "/" + id + "/close")).andExpect(status().isOk());

        asTeacher(delete("/api/exams/" + exam)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAM_HAS_ASSIGNMENTS"));
        assertEquals(1, count("SELECT count(*) FROM exam WHERE id = ? AND deleted_at IS NULL", exam));
        asTeacher(get(base() + "/" + id)).andExpect(status().isOk());
        mockMvc.perform(get("/api/public/assignments/" + code)).andExpect(status().isOk());

        asTeacher(delete(base() + "/" + id)).andExpect(status().isNoContent());
        asTeacher(delete("/api/exams/" + exam)).andExpect(status().isNoContent());
        assertEquals(1, count("SELECT count(*) FROM exam WHERE id = ? AND deleted_at IS NOT NULL", exam));
        // Đề đã xóa: giao bài trả 404 như đề không tồn tại.
        asTeacher(post(base()).contentType(MediaType.APPLICATION_JSON).content(createBody(45, inTwoHours(), true, true)))
                .andExpect(status().isNotFound());
    }
}
