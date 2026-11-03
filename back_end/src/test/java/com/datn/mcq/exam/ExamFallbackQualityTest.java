package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * F-01 (soát ngoài, trước deploy): câu dự phòng của chỗ ({@code exam_slot.fallback_question_id}) chỉ bị ghi đè khi câu mới
 * TỐT HƠN (thứ hạng: QC_PASSED có cảnh báo cùng nhãn > GENERATED lệch Solver); hết lượt thì dùng câu tốt nhất giữa câu cuối
 * và câu dự phòng; câu lệch Solver không đi đường "sinh lại vì cùng nhãn". Mức tích hợp, LLM và Solver giả.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ExamFallbackQualityTest extends ExamFlowTestBase {

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

    /** Nhãn sinh ra {@code labels}; Solver đồng ý (null) hoặc lệch ({@code judges}); rồi xử lý job và đối soát một vòng. */
    private void round(String labels, String judges) throws Exception {
        generator.tfLabels = labels.split(",");
        SOLVER.tfJudges = judges;
        processAll();
        progress(examId);
    }

    private List<Integer> questionIds() {
        return jdbc.queryForList("SELECT id FROM generated_question ORDER BY id", Integer.class);
    }

    private Map<String, Object> slotRow() {
        return jdbc.queryForMap("SELECT * FROM exam_slot WHERE id = ?", slot);
    }

    @Test
    void cauQcPassedCoCanhBaoKhongBiCauLechSolverGhiDe() throws Exception {
        round("T,T,T,T", null);
        List<Integer> afterFirst = questionIds();
        assertEquals("qc_passed", jdbc.queryForObject("SELECT status FROM generated_question WHERE id = ?", String.class,
                afterFirst.get(0)));
        assertEquals(afterFirst.get(0), slotRow().get("fallback_question_id"), "q1 (QC_PASSED, TTTT) là câu dự phòng");

        round("T,F,T,F", "F,T,F,T");
        assertEquals(afterFirst.get(0), slotRow().get("fallback_question_id"),
                "q2 lệch Solver kém hơn q1: không được ghi đè câu dự phòng");

        round("F,T,T,F", "T,T,T,T");
        List<Integer> ids = questionIds();
        assertEquals(3, ids.size());
        Map<String, Object> row = slotRow();
        assertEquals("ready", row.get("state"));
        assertEquals(ids.get(0), row.get("generated_question_id"), "hết lượt: dùng q1, không dùng q3 lệch Solver");
        assertEquals(3, count("SELECT count(*) FROM generation_job WHERE exam_slot_id = ? AND job_type = 'generate'",
                slot), "tổng job generate của chỗ không vượt MAX_ATTEMPTS");
    }

    @Test
    void cauLechSolverRoiCauQcPassedCoCanhBaoHetLuotThiDungCauQcPassed() throws Exception {
        round("T,F,T,F", "F,T,F,T");
        round("T,T,T,T", null);
        round("F,T,T,F", "T,T,T,T");
        List<Integer> ids = questionIds();
        assertEquals(3, ids.size());
        assertEquals("generated", jdbc.queryForObject("SELECT status FROM generated_question WHERE id = ?", String.class,
                ids.get(0)));
        assertEquals("qc_passed", jdbc.queryForObject("SELECT status FROM generated_question WHERE id = ?", String.class,
                ids.get(1)));
        Map<String, Object> row = slotRow();
        assertEquals("ready", row.get("state"));
        assertEquals(ids.get(1), row.get("generated_question_id"), "hết lượt: dùng q2 (QC_PASSED), tốt hơn q1 và q3 lệch");
        int jobs = count("SELECT count(*) FROM generation_job WHERE exam_slot_id = ? AND job_type = 'generate'", slot);
        assertTrue(jobs <= 3, "tổng job generate = " + jobs);
    }
}
