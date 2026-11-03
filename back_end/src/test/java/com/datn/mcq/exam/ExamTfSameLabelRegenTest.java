package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Câu Đúng/Sai có 4 ý cùng nhãn (2/11, thẩm định UX đề 10): trước đây chỉ là cảnh báo; nay đi đường sinh lại sẵn có của
 * chỗ (như lệch Solver: câu cũ giữ làm dự phòng, lần sinh lại được dặn lý do), hết lượt thì giữ câu kèm cờ như cũ. Mức
 * tích hợp, LLM và Solver giả.
 *
 * <p>Chọn đường sinh lại ở đối soát (không biến thành lỗi chặn ở bước format) vì lỗi chặn đưa câu về format_failed: câu đó
 * không Giữ được vào ngân hàng và hết lượt thì chỗ hỏng, trái với "hết lượt thì giữ câu kèm cờ".
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ExamTfSameLabelRegenTest extends ExamFlowTestBase {

    private static final String FLAG = "warning: label: 4 ý cùng nhãn T";

    private int examId;
    private int slot;

    @BeforeEach
    void setUp() throws Exception {
        int matrixId = createMatrix("{\"part\": \"II\", \"questionType\": \"tf\", \"statementDifficulties\": [\"easy\","
                + " \"easy\", \"medium\", \"hard\"], \"questionCount\": 1, \"topic\": \"Hằng đẳng thức\","
                + " \"learningOutcomes\": {\"easy\": \"Nhận biết.\", \"medium\": \"Khai triển.\", \"hard\": \"Vận dụng.\"}}");
        examId = build(matrixId).get("examId").asInt();
        slot = slotId(examId, cellId(matrixId, 1), 1);
    }

    private JsonNode item() throws Exception {
        return json(perform(get("/api/exams/" + examId)).andExpect(status().isOk())).get("parts").get(1).get("items").get(0);
    }

    private static List<String> reasons(JsonNode item) {
        List<String> out = new ArrayList<>();
        item.get("reviewReasons").forEach(r -> out.add(r.asText()));
        return out;
    }

    private List<String> notes() {
        return jdbc.queryForList("SELECT coalesce(prompt_note, '') FROM generation_job WHERE exam_slot_id = ? ORDER BY id",
                String.class, slot);
    }

    @Test
    void bonYCungNhanThiSinhLaiMoiLanDuocDanLyDo() throws Exception {
        generator.tfLabels = new String[] {"T", "T", "T", "T"};
        processAll();
        progress(examId);
        assertEquals("generating", slotState(slot), "đối soát thấy 4 ý cùng nhãn: chỗ được sinh lại, chưa dùng câu đó");
        assertEquals(2, notes().size());
        assertTrue(notes().get(1).contains("4 ý cùng nhãn T"), "lần sinh lại được dặn lý do: " + notes().get(1));
        assertFalse(notes().get(0).contains("4 ý cùng nhãn"));

        generator.tfLabels = new String[] {"T", "F", "T", "F"};
        settle(examId);
        assertEquals("ready", slotState(slot));
        assertEquals(2, count("SELECT count(*) FROM generation_job WHERE exam_slot_id = ?", slot), "chỉ sinh lại một lần");
        JsonNode item = item();
        assertEquals("TFTF", item.get("statements").findValuesAsText("label").stream().reduce("", String::concat));
        assertEquals(List.of(), reasons(item), "câu mới sạch: không còn cờ");
        assertEquals(2, count("SELECT count(*) FROM generated_question"), "câu TTTT bị bỏ, câu TFTF được dùng");
    }

    @Test
    void hetLuotSinhLaiThiGiuCauKemCoNhuCu() throws Exception {
        generator.tfLabels = new String[] {"T", "T", "T", "T"};
        settle(examId);
        assertEquals("ready", slotState(slot), "không để chỗ hỏng chỉ vì 4 ý cùng nhãn");
        assertEquals(3, count("SELECT count(*) FROM generation_job WHERE exam_slot_id = ?", slot), "tối đa 3 lượt mỗi chỗ");
        assertEquals(0, count("SELECT count(*) FROM generated_question WHERE status = 'format_failed'"),
                "không phải lỗi chặn: câu không bị format_failed nên vẫn Giữ được");
        JsonNode item = item();
        assertTrue(item.get("needsReview").asBoolean());
        assertEquals(List.of(FLAG), reasons(item));
        assertEquals(1, count("SELECT count(*) FROM exam_slot WHERE id = ? AND generated_question_id IS NOT NULL", slot));

        int before = count("SELECT count(*) FROM generation_job");
        progress(examId);
        progress(examId);
        assertEquals(before, count("SELECT count(*) FROM generation_job"), "đã hết lượt: đối soát lại không sinh thêm");
    }

    @Test
    void cauNhanDaDangKhongBiSinhLai() throws Exception {
        generator.tfLabels = new String[] {"T", "F", "F", "T"};
        settle(examId);
        assertEquals("ready", slotState(slot));
        assertEquals(1, count("SELECT count(*) FROM generation_job WHERE exam_slot_id = ?", slot));
        assertEquals(List.of(), reasons(item()));
    }
}
