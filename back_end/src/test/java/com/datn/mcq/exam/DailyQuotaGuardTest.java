package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.llm.client.GeminiRateLimiter;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-18 (soát ngoài): hết hạn mức NGÀY của Gemini ({@link GeminiRateLimiter}) thì tạo đề / Đổi bằng sinh mới trả 429
 * {@code LLM_DAILY_QUOTA_EXHAUSTED} kèm Retry-After NGAY, TRƯỚC khi trừ hạn mức của giáo viên (generation_quota) và trước
 * khi xếp job; đề đủ câu từ ngân hàng (không cần sinh) vẫn tạo được.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class DailyQuotaGuardTest extends ExamFlowTestBase {

    /** Số lượt Gemini đã dùng trong ngày mà bộ điều tiết giả "đọc từ DB" khi nạp lại; trần ngày của bộ điều tiết là 5. */
    private static final AtomicInteger USED = new AtomicInteger();

    @TestBean
    private GeminiRateLimiter geminiRateLimiter;

    static GeminiRateLimiter geminiRateLimiter() {
        return new GeminiRateLimiter(0L, 5, 60_000L, Clock.systemDefaultZone(), d -> { }, s -> USED.get());
    }

    @Autowired
    private GeminiRateLimiter limiter;

    private int matrixId;

    @BeforeEach
    void setUp() throws Exception {
        setGeminiUsed(0);
        matrixId = createMatrix(mcqCell("Đa thức", "Đơn thức", 2));
    }

    private void setGeminiUsed(int used) {
        USED.set(used);
        limiter.resetFromDatabase();
    }

    private int teacherRequested() {
        return count("SELECT coalesce(sum(requested), 0) FROM generation_quota WHERE user_id = ?", teacher);
    }

    @Test
    void hetHanMucNgayThiTaoDeBi429KhongTruHanMucGiaoVienKhongXepJob() throws Exception {
        setGeminiUsed(5);

        MvcResult result = postJson("/api/exams/build", "{\"matrixId\": " + matrixId + "}")
                .andExpect(status().isTooManyRequests()).andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("LLM_DAILY_QUOTA_EXHAUSTED", body.get("code").asText());
        assertNotNull(result.getResponse().getHeader("Retry-After"), "kèm Retry-After để giao diện tính giờ mở lại");
        assertTrue(Long.parseLong(result.getResponse().getHeader("Retry-After")) > 0);
        assertEquals(0, teacherRequested(), "không trừ hạn mức giáo viên");
        assertEquals(0, count("SELECT count(*) FROM exam"), "không để lại đề");
        assertEquals(0, count("SELECT count(*) FROM generation_job"), "không xếp job");
    }

    /** C-01: thông báo cho giáo viên nêu giờ chạy tiếp (giờ Việt Nam, từ mốc đặt lại), không nhắc tên nhà cung cấp. */
    @Test
    void hetHanMucNgayThongBaoGioChayTiepTheoGioVietNam() throws Exception {
        setGeminiUsed(5);

        MvcResult result = postJson("/api/exams/build", "{\"matrixId\": " + matrixId + "}")
                .andExpect(status().isTooManyRequests()).andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(body.get("error").asText().matches("Hết lượt AI hôm nay, hệ thống chạy tiếp lúc \\d{2}:\\d{2}"),
                body.toString());
        assertEquals("LLM_DAILY_QUOTA_EXHAUSTED", body.get("code").asText());
    }

    @Test
    void deDuCauTuNganHangVanTaoDuocKhiHetHanMucNgay() throws Exception {
        bankMcq("Đa thức", "easy", "Câu ngân hàng một");
        bankMcq("Đa thức", "easy", "Câu ngân hàng hai");
        setGeminiUsed(5);

        JsonNode built = json(postJson("/api/exams/build", "{\"matrixId\": " + matrixId + "}").andExpect(status().isOk()));

        assertEquals(2, built.get("fromBank").asInt());
        assertEquals(0, built.get("jobsQueued").asInt());
        assertEquals(0, teacherRequested());
    }

    @Test
    void doiCauBangSinhMoiBi429KhiHetHanMucNgayVaChoGiuNguyen() throws Exception {
        int examId = build(matrixId).get("examId").asInt();
        processAll();
        progress(examId);
        int slot = slotId(examId, cellId(matrixId, 1), 1);
        int questionBefore = count("SELECT generated_question_id FROM exam_slot WHERE id = ?", slot);
        int requestedBefore = teacherRequested();
        assertEquals(2, requestedBefore);

        setGeminiUsed(5);
        MvcResult result = postJson("/api/exams/" + examId + "/slots/" + slot + "/replace", "{}")
                .andExpect(status().isTooManyRequests()).andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals("LLM_DAILY_QUOTA_EXHAUSTED", body.get("code").asText());
        assertNotNull(result.getResponse().getHeader("Retry-After"));
        assertEquals(requestedBefore, teacherRequested(), "không trừ hạn mức giáo viên");
        assertEquals(questionBefore, count("SELECT generated_question_id FROM exam_slot WHERE id = ?", slot),
                "chỗ giữ nguyên câu");
        assertEquals(0, count("SELECT count(*) FROM exam_slot_action WHERE slot_id = ?", slot), "không ghi nhật ký Đổi");
    }

    @Test
    void doiCauBangNganHangKhongBiChanKhiHetHanMucNgay() throws Exception {
        int examId = build(matrixId).get("examId").asInt();
        processAll();
        progress(examId);
        int slot = slotId(examId, cellId(matrixId, 1), 1);
        setGeminiUsed(5);

        // Ngân hàng không có câu: 409 BANK_EXHAUSTED như thường lệ (không phải 429 của hạn mức Gemini).
        JsonNode body = json(postJson("/api/exams/" + examId + "/slots/" + slot + "/replace", "{\"source\": \"bank\"}")
                .andExpect(status().isConflict()));
        assertEquals("BANK_EXHAUSTED", body.get("code").asText());
    }
}
