package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W1-3 phần 2: chọn câu ngân hàng khi tạo đề. (a) một câu ngân hàng không vào hai chỗ của cùng đề; (b) Đổi không lấy
 * câu ngân hàng (sinh mới, câu cũ không quay lại); giữa các đề chỉ ưu tiên mềm câu ít dùng, dùng lâu rồi hơn.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ExamBankSelectionTest extends ExamFlowTestBase {

    private List<Integer> bankIdsOf(int examId) {
        return jdbc.queryForList("SELECT bank_item_id FROM exam_slot WHERE exam_id = ? AND bank_item_id IS NOT NULL"
                + " ORDER BY id", Integer.class, examId);
    }

    // ---------- (a) không chọn cùng một câu cho hai chỗ ----------

    @Test
    void haiOCungChuDeNhanHaiCauNganHangKhacNhau() throws Exception {
        int a = bankMcq("Đa thức", "easy", "Câu ngân hàng A");
        int b = bankMcq("Đa thức", "easy", "Câu ngân hàng B");
        bankMcq("Đa thức", "easy", "Câu ngân hàng C");
        int matrix = createMatrix(mcqCell("Đa thức", "Đơn thức", 1), mcqCell("Đa thức", "Đơn thức", 1));
        JsonNode built = build(matrix);
        assertEquals(2, built.get("fromBank").asInt());
        List<Integer> used = bankIdsOf(built.get("examId").asInt());
        assertEquals(2, used.size());
        assertEquals(2, used.stream().distinct().count(), "hai chỗ không cùng một câu ngân hàng: " + used);
        assertEquals(List.of(a, b), used, "chưa dùng, cùng mức dùng: theo id");
    }

    @Test
    void chiConMotCauNganHangThiChoSauSinhMoi() throws Exception {
        bankMcq("Đa thức", "easy", "Câu ngân hàng duy nhất");
        int matrix = createMatrix(mcqCell("Đa thức", null, 1), mcqCell("Đa thức", null, 1));
        JsonNode built = build(matrix);
        assertEquals(1, built.get("fromBank").asInt());
        assertEquals(1, built.get("jobsQueued").asInt());
        assertEquals(1, bankIdsOf(built.get("examId").asInt()).size());
    }

    /**
     * Hai ô dùng chung một template (DB không cấm) và ô đầu cần 2 câu: truy vấn không được trả cùng một câu ngân hàng
     * hai lần (LEFT JOIN exam_matrix_cell nhân dòng).
     */
    @Test
    void haiOChungMotTemplateKhongNhanDoiMotCauNganHang() throws Exception {
        int matrix = createMatrix(mcqCell("Đa thức", "Đơn thức", 2), mcqCell("Đa thức", "Đơn thức", 1));
        int shared = count("SELECT template_id FROM exam_matrix_cell WHERE id = ?", cellId(matrix, 1));
        jdbc.update("UPDATE exam_matrix_cell SET template_id = ? WHERE id = ?", shared, cellId(matrix, 2));
        int item = bankMcq(shared, "Đa thức", "easy", "Câu nguồn", "Câu ngân hàng dùng chung", new String[] {"$1$",
                "$2$", "$3$", "$4$"});
        JsonNode built = build(matrix);
        int examId = built.get("examId").asInt();
        assertEquals(List.of(item), bankIdsOf(examId), "một câu ngân hàng chỉ vào một chỗ");
        assertEquals(1, built.get("fromBank").asInt());
        assertEquals(2, built.get("jobsQueued").asInt());
    }

    // ---------- (b) Đổi không lấy câu ngân hàng ----------

    @Test
    void doiCauSinhMoiKhongLayCauNganHangVaCauCuKhongQuayLai() throws Exception {
        int item = bankMcq("Đa thức", "easy", "Câu ngân hàng cho chỗ này");
        int matrix = createMatrix(mcqCell("Đa thức", null, 1));
        int examId = build(matrix).get("examId").asInt();
        int slot = slotId(examId, cellId(matrix, 1), 1);
        assertEquals(item, count("SELECT bank_item_id FROM exam_slot WHERE id = ?", slot));

        perform(post("/api/exams/" + examId + "/slots/" + slot + "/replace")).andExpect(status().isOk());
        assertNull(jdbc.queryForObject("SELECT bank_item_id FROM exam_slot WHERE id = ?", Integer.class, slot),
                "chỗ bỏ câu ngân hàng cũ");
        assertEquals(1, count("SELECT count(*) FROM generation_job WHERE exam_slot_id = ?", slot),
                "Đổi xếp một job sinh mới thay vì lấy câu khác trong ngân hàng");
        settle(examId);
        assertEquals("ready", slotState(slot));
        assertNull(jdbc.queryForObject("SELECT bank_item_id FROM exam_slot WHERE id = ?", Integer.class, slot));
        assertNotNull(jdbc.queryForObject("SELECT generated_question_id FROM exam_slot WHERE id = ?", Integer.class,
                slot));
        assertEquals(List.of(), bankIdsOf(examId), "không còn chỗ nào mang câu ngân hàng");
        JsonNode view = json(perform(get("/api/exams/" + examId)).andExpect(status().isOk()));
        assertEquals("generated", view.get("parts").get(0).get("items").get(0).get("source").asText());
    }

    // ---------- ưu tiên mềm giữa các đề ----------

    @Test
    void uuTienCauItDungRoiCauDungLauHon() throws Exception {
        int b1 = bankMcq("Đa thức", "easy", "Câu một");
        int b2 = bankMcq("Đa thức", "easy", "Câu hai");
        int b3 = bankMcq("Đa thức", "easy", "Câu ba");
        // b1 dùng ở 3 đề, b2 dùng 1 lần từ lâu, b3 dùng 1 lần gần đây; đề của cả hai giáo viên đều tính.
        manualExamUsing(otherTeacher, "X1", "2026-03-01 08:00:00", b1);
        manualExamUsing(otherTeacher, "X2", "2026-03-02 08:00:00", b1);
        manualExamUsing(teacher, "X3", "2026-03-03 08:00:00", b1);
        manualExamUsing(otherTeacher, "X4", "2026-01-01 08:00:00", b2);
        manualExamUsing(teacher, "X5", "2026-06-01 08:00:00", b3);
        int matrix = createMatrix(mcqCell("Đa thức", null, 1));

        assertEquals(List.of(b2), bankIdsOf(build(matrix).get("examId").asInt()), "ít dùng nhất, dùng lâu rồi nhất");
        assertEquals(List.of(b3), bankIdsOf(build(matrix).get("examId").asInt()),
                "b2 đã có thêm một đề nên b3 (1 lần) lên trước b1 (3 lần)");
    }

    @Test
    void cauChuaDungLuonDuocUuTienHonCauDaDung() throws Exception {
        int used = bankMcq("Đa thức", "easy", "Câu đã dùng");
        manualExamUsing(otherTeacher, "Y1", "2020-01-01 08:00:00", used);
        int fresh = bankMcq("Đa thức", "easy", "Câu chưa dùng (id lớn hơn)");
        int matrix = createMatrix(mcqCell("Đa thức", null, 1));
        assertEquals(List.of(fresh), bankIdsOf(build(matrix).get("examId").asInt()));
    }

    @Test
    void khongCamCungCauDaDungKhiNganHangChiConCauDaDung() throws Exception {
        int only = bankMcq("Đa thức", "easy", "Câu duy nhất");
        manualExamUsing(otherTeacher, "Z1", "2026-02-01 08:00:00", only);
        int matrix = createMatrix(mcqCell("Đa thức", null, 1));
        assertEquals(List.of(only), bankIdsOf(build(matrix).get("examId").asInt()),
                "ưu tiên mềm: vẫn dùng lại khi không còn câu khác");
    }
}
