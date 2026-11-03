package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * B7 tối thiểu: {@code POST /api/exams/{id}/slots/{slot}/replace} nhận trường tùy chọn {@code reason}, lưu vào cột
 * {@code exam_slot_action.replace_reason} (V25, có CHECK). Giá trị ngoài danh sách là 400 INVALID_REQUEST trước mọi thay
 * đổi. Không bắt buộc, trống thì NULL.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ExamReplaceReasonTest extends ExamFlowTestBase {

    private static final List<String> REASONS = List.of("wrong_answer", "ambiguous_options", "off_matrix",
            "duplicate_pattern", "latex_format", "too_hard", "too_easy", "other");

    private int examId;
    private int slot;

    @BeforeEach
    void setUp() throws Exception {
        int matrixId = createMatrix(mcqCell("Đa thức", null, 1));
        examId = build(matrixId).get("examId").asInt();
        settle(examId);
        slot = slotId(examId, cellId(matrixId, 1), 1);
        assertEquals("ready", slotState(slot));
        for (int i = 1; i <= REASONS.size() + 1; i++) {
            bankMcq("Đa thức", "easy", "Câu ngân hàng số " + i);
        }
    }

    private String url() {
        return "/api/exams/" + examId + "/slots/" + slot + "/replace";
    }

    private List<String> reasonsLogged() {
        return jdbc.queryForList("SELECT replace_reason FROM exam_slot_action WHERE slot_id = ? AND action = 'replace'"
                + " ORDER BY id", String.class, slot);
    }

    @Test
    void moiLyDoHopLeDuocLuuVaoNhatKyDoi() throws Exception {
        for (String reason : REASONS) {
            postJson(url(), "{\"source\": \"bank\", \"reason\": \"" + reason + "\"}").andExpect(status().isOk());
        }
        assertEquals(REASONS, reasonsLogged());
    }

    @Test
    void khongGuiHoacTrongLyDoThiLuuNull() throws Exception {
        postJson(url(), "{\"source\": \"bank\"}").andExpect(status().isOk());
        postJson(url(), "{\"source\": \"bank\", \"reason\": \"  \"}").andExpect(status().isOk());
        postJson(url(), "{\"source\": \"bank\", \"reason\": null}").andExpect(status().isOk());
        List<String> logged = reasonsLogged();
        assertEquals(3, logged.size());
        logged.forEach(r -> assertNull(r));
    }

    @Test
    void lyDoDoiBangSinhMoiCungDuocLuu() throws Exception {
        postJson(url(), "{\"reason\": \"too_hard\", \"instruction\": \"Dễ hơn một chút\"}").andExpect(status().isOk());
        assertEquals(List.of("too_hard"), reasonsLogged());
    }

    @Test
    void lyDoLaTra400TruocMoiThayDoi() throws Exception {
        int questionBefore = count("SELECT generated_question_id FROM exam_slot WHERE id = ?", slot);
        for (String bad : List.of("XYZ", "wrong answer", "WRONG_ANSWER_2", "'; DROP TABLE exam_slot_action; --")) {
            JsonNode body = json(postJson(url(), "{\"source\": \"bank\", \"reason\": \"" + bad.replace("'", "\\u0027")
                    + "\"}").andExpect(status().isBadRequest()));
            assertEquals("INVALID_REQUEST", body.get("code").asText(), bad);
        }
        assertEquals(0, count("SELECT count(*) FROM exam_slot_action WHERE slot_id = ?", slot), "không ghi nhật ký");
        assertEquals(questionBefore, count("SELECT generated_question_id FROM exam_slot WHERE id = ?", slot),
                "chỗ giữ nguyên");
    }

    @Test
    void cotReplaceReasonCoCheckOMucDatabase() {
        jdbc.update("INSERT INTO exam_slot_action (slot_id, action, replace_source, replace_reason) VALUES (?, 'replace',"
                + " 'bank', 'wrong_answer')", slot);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO exam_slot_action (slot_id, action, replace_source, replace_reason) VALUES (?, 'replace',"
                        + " 'bank', 'ly_do_la')", slot), "giá trị ngoài danh sách");
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "INSERT INTO exam_slot_action (slot_id, action, replace_reason) VALUES (?, 'keep', 'other')", slot),
                "chỉ dòng replace mới có lý do");
        assertEquals(40, count("SELECT character_maximum_length FROM information_schema.columns"
                + " WHERE table_name = 'exam_slot_action' AND column_name = 'replace_reason'"));
    }
}
