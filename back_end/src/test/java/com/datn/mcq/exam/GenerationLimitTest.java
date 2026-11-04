package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * D1 (soát backend trước deploy): giới hạn theo từng giáo viên để một người không làm tắc hàng đợi và cạn quota chung.
 * Ngưỡng hạ thấp bằng cấu hình (tối đa 10 câu mỗi ma trận/đề, 6 job đang chờ, 12 câu yêu cầu sinh mỗi ngày) để test nhẹ;
 * giá trị mặc định (60/60/120) kiểm riêng ở {@code GenerationLimitDefaultsTest}.
 */
@SpringBootTest(properties = {
        "app.generation-limits.max-questions-per-exam=10",
        "app.generation-limits.max-pending-jobs=6",
        "app.generation-limits.max-daily-questions=12"
})
@Import(TestSafetyConfiguration.class)
class GenerationLimitTest extends ExamFlowTestBase {

    static final String QUEUE_MESSAGE =
            "Thầy/cô đang có quá nhiều câu chờ soạn. Vui lòng đợi các đề hiện tại soạn xong rồi tạo tiếp.";
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private int newTeacher(String tag) {
        return insert("INSERT INTO users (name, email, role) VALUES (?, ?, 'teacher')", "Cô " + tag,
                tag + ".limit@test");
    }

    private ResultActions postAs(int user, String url, String body) throws Exception {
        return performAs(user, post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private int matrixOf(int user, String... cells) throws Exception {
        ResultActions r = postAs(user, "/api/exam-matrices", "{\"name\": \"Ma trận thử\", \"subjectId\": " + subject
                + ", \"gradeLevel\": 8, \"cells\": [" + String.join(",", cells) + "]}").andExpect(status().isOk());
        return json(r).get("id").asInt();
    }

    private ResultActions buildAs(int user, int matrixId) throws Exception {
        return postAs(user, "/api/exams/build", "{\"matrixId\": " + matrixId + "}");
    }

    private int buildOk(int user, int matrixId) throws Exception {
        return json(buildAs(user, matrixId).andExpect(status().isOk())).get("examId").asInt();
    }

    /** Xử lý mọi job rồi đối soát đề của giáo viên {@code user} cho tới khi các chỗ sinh xong. */
    private void settleAs(int user, int examId) throws Exception {
        for (int round = 0; round < 5; round++) {
            processAll();
            performAs(user, get("/api/exams/" + examId + "/progress")).andExpect(status().isOk());
        }
    }

    private int firstSlot(int examId) {
        return count("SELECT min(id) FROM exam_slot WHERE exam_id = ?", examId);
    }

    private int jobs() {
        return count("SELECT count(*) FROM generation_job WHERE job_type = 'generate'");
    }

    private int exams() {
        return count("SELECT count(*) FROM exam");
    }

    // ---------- trần tổng số câu của ma trận / đề theo bài ----------

    @Test
    void maTranVuotTranTongSoCauBi400() throws Exception {
        postAs(teacher, "/api/exam-matrices", "{\"name\": \"Quá tay\", \"subjectId\": " + subject
                + ", \"gradeLevel\": 8, \"cells\": [" + mcqCell("Đa thức", null, 5) + "," + mcqCell("Phân thức", null, 6)
                + "]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("tối đa 10 câu")))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("11")));

        int ok = matrixOf(teacher, mcqCell("Đa thức", null, 5), mcqCell("Phân thức", null, 5));
        performAs(teacher, put("/api/exam-matrices/" + ok).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Sửa quá tay\", \"subjectId\": " + subject + ", \"gradeLevel\": 8, \"cells\": ["
                        + mcqCell("Đa thức", null, 5) + "," + mcqCell("Phân thức", null, 6) + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("tối đa 10 câu")));
        assertEquals(10, count("SELECT sum(question_count) FROM exam_matrix_cell WHERE matrix_id = ?", ok),
                "lần sửa bị từ chối không đổi ma trận");
    }

    @Test
    void sinhTheoBaiVuotTranTongSoCauBi400VaKhongTaoGiCa() throws Exception {
        postAs(teacher, "/api/exams/build-by-lesson", "{\"subjectId\": " + subject + ", \"lesson\": \"Bài 1\","
                + " \"cells\": [{\"questionType\": \"mcq\", \"difficulty\": \"easy\", \"questionCount\": 6},"
                + " {\"questionType\": \"mcq\", \"difficulty\": \"medium\", \"questionCount\": 5}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("tối đa 10 câu")));
        assertEquals(0, exams());
        assertEquals(0, count("SELECT count(*) FROM exam_matrix"));
        assertEquals(0, jobs());
    }

    // ---------- trần job đang chờ/đang chạy ----------

    @Test
    void vuotTranJobDangChoThi429VaKhongTaoGiCaCuaLanBbiChan() throws Exception {
        int a = newTeacher("a");
        int b = newTeacher("b");
        int sixCells = matrixOf(a, mcqCell("Đa thức", null, 6));
        int oneCell = matrixOf(a, mcqCell("Phân thức", null, 1));
        buildOk(a, sixCells);
        assertEquals(6, jobs());

        buildAs(a, oneCell).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("GENERATION_QUEUE_LIMIT"))
                .andExpect(jsonPath("$.error").value(QUEUE_MESSAGE));
        assertEquals(1, exams(), "lần bị chặn không để lại đề");
        assertEquals(6, jobs(), "lần bị chặn không để lại job");

        int bMatrix = matrixOf(b, mcqCell("Đa thức", null, 1));
        buildOk(b, bMatrix); // trần tính riêng từng giáo viên
        assertEquals(7, jobs());
    }

    @Test
    void doiCauSinhMoiBiChanKhiHangDoiCuaGiaoVienDay() throws Exception {
        int c = newTeacher("c");
        int first = buildOk(c, matrixOf(c, mcqCell("Đa thức", null, 3)));
        settleAs(c, first);
        int slot = firstSlot(first);
        assertEquals("ready", slotState(slot));
        buildOk(c, matrixOf(c, mcqCell("Phân thức", null, 6))); // đầy hàng đợi (6 job chờ)

        postAs(c, "/api/exams/" + first + "/slots/" + slot + "/replace", "{\"source\": \"generate\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("GENERATION_QUEUE_LIMIT"));
        assertEquals("ready", slotState(slot), "Đổi câu bị chặn thì chỗ giữ nguyên");
        assertEquals(0, count("SELECT count(*) FROM exam_slot_action WHERE slot_id = ?", slot));
    }

    // ---------- trần số câu yêu cầu sinh mỗi ngày (giờ Việt Nam) ----------

    @Test
    void vuotTranMoiNgayThi429KemRetryAfterDenNuaDemGioVietNam() throws Exception {
        int d = newTeacher("d");
        int first = buildOk(d, matrixOf(d, mcqCell("Đa thức", null, 6)));
        settleAs(d, first);
        int second = buildOk(d, matrixOf(d, mcqCell("Phân thức", null, 6))); // đủ 12 câu trong ngày
        settleAs(d, second);
        int slot = firstSlot(first);
        assertEquals("ready", slotState(slot));

        long before = secondsToVietnamMidnight();
        MvcResult blocked = postAs(d, "/api/exams/" + first + "/slots/" + slot + "/replace",
                "{\"source\": \"generate\"}").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("DAILY_GENERATION_LIMIT")).andReturn();
        long after = secondsToVietnamMidnight();
        long retryAfter = Long.parseLong(blocked.getResponse().getHeader("Retry-After"));
        assertTrue(retryAfter <= before + 1 && retryAfter >= after - 1,
                "Retry-After " + retryAfter + " phải tới 0h giờ Việt Nam (" + after + ".." + before + ")");
        String message = objectMapper.readTree(body(blocked)).get("error").asText();
        assertTrue(message.contains("12") && message.contains("0 giờ"), message);
        assertEquals("ready", slotState(slot));

        // Đổi bằng câu ngân hàng không tính vào trần ngày (ở đây ngân hàng trống nên 409, không phải 429).
        postAs(d, "/api/exams/" + first + "/slots/" + slot + "/replace", "{\"source\": \"bank\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BANK_EXHAUSTED"));

        // Sang ngày mới (giờ Việt Nam) thì bộ đếm về 0.
        jdbc.update("UPDATE generation_quota SET usage_date = usage_date - 1 WHERE user_id = ?", d);
        postAs(d, "/api/exams/" + first + "/slots/" + slot + "/replace", "{\"source\": \"generate\"}")
                .andExpect(status().isOk());
    }

    @Test
    void ngayTinhTheoGioVietNamKhongPhaiUtc() throws Exception {
        int e = newTeacher("e");
        buildOk(e, matrixOf(e, mcqCell("Đa thức", null, 3)));
        String today = jdbc.queryForObject("SELECT usage_date::text FROM generation_quota WHERE user_id = ?",
                String.class, e);
        assertEquals(ZonedDateTime.now(VN).toLocalDate().toString(), today);
        assertEquals(3, count("SELECT requested FROM generation_quota WHERE user_id = ?", e));
    }

    // ---------- câu từ ngân hàng không tính ----------

    @Test
    void cauLayTuNganHangKhongTinhVaoTranNaoCa() throws Exception {
        int f = newTeacher("f");
        for (int i = 0; i < 8; i++) {
            bankMcq("Đa thức", "easy", "Câu ngân hàng " + i);
        }
        JsonNode fromBank = json(buildAs(f, matrixOf(f, mcqCell("Đa thức", null, 8))).andExpect(status().isOk()));
        assertEquals(8, fromBank.get("fromBank").asInt());
        assertEquals(0, fromBank.get("jobsQueued").asInt());
        assertEquals(0, count("SELECT coalesce(sum(requested), 0) FROM generation_quota WHERE user_id = ?", f));

        // Nếu 8 câu ngân hàng bị tính thì 8 + 6 = 14 vượt trần ngày 12 (và 6 job vẫn nằm trong trần chờ).
        buildOk(f, matrixOf(f, mcqCell("Phân thức", null, 6)));
        assertEquals(6, count("SELECT requested FROM generation_quota WHERE user_id = ?", f));
    }

    // ---------- đồng thời ----------

    @Test
    void haiYeuCauSongSongCungChamTranChiMotCaiQua() throws Exception {
        int g = newTeacher("g");
        int m1 = matrixOf(g, mcqCell("Đa thức", null, 4));
        int m2 = matrixOf(g, mcqCell("Phân thức", null, 4));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int m : new int[] {m1, m2}) {
                tasks.add(() -> {
                    go.await();
                    return buildAs(g, m).andReturn().getResponse().getStatus();
                });
            }
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> t : tasks) {
                futures.add(pool.submit(t));
            }
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get(60, TimeUnit.SECONDS));
            }
            statuses.sort(Integer::compare);
            assertEquals(List.of(200, 429), statuses, "mỗi yêu cầu 4 câu, trần chờ 6: chỉ một yêu cầu được qua");
            assertEquals(4, jobs());
            assertEquals(1, exams());
            assertEquals(4, count("SELECT requested FROM generation_quota WHERE user_id = ?", g));
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- tiện ích ----------

    /** Số giây từ bây giờ tới 0h giờ Việt Nam kế tiếp. */
    private static long secondsToVietnamMidnight() {
        ZonedDateTime now = ZonedDateTime.ofInstant(Instant.now(), VN);
        return java.time.Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(VN)).getSeconds();
    }

    private static String body(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }
}
