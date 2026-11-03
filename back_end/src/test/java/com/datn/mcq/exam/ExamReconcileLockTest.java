package com.datn.mcq.exam;

import com.datn.mcq.config.TestSafetyConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F-07 (phần khóa): {@code ExamBuildService.reconcile} chỉ khóa các chỗ của đề (PESSIMISTIC_WRITE) khi đề còn chỗ
 * {@code generating}; kiểm bằng một truy vấn không khóa trước. Cách kiểm: một transaction khác giữ khóa
 * {@code SELECT ... FOR UPDATE} trên các chỗ của đề; xem đề đã xong KHÔNG được chờ khóa đó, còn đề đang sinh vẫn khóa (chờ)
 * rồi đối soát đúng như cũ.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ExamReconcileLockTest extends ExamFlowTestBase {

    private int matrixId;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    @BeforeEach
    void setUp() throws Exception {
        matrixId = createMatrix(mcqCell("Đa thức", "Đơn thức", 2));
    }

    /** Mở một transaction riêng và giữ khóa ghi trên mọi chỗ của đề cho tới khi đóng kết nối trả về. */
    private Connection holdSlotLocks(int examId) throws Exception {
        Connection c = dataSource.getConnection();
        c.setAutoCommit(false);
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM exam_slot WHERE exam_id = ? FOR UPDATE")) {
            ps.setInt(1, examId);
            ps.executeQuery().close();
        }
        return c;
    }

    private Future<Integer> viewStatus(int examId) {
        return pool.submit(() -> perform(get("/api/exams/" + examId)).andReturn().getResponse().getStatus());
    }

    @Test
    void deDaXongKhongKhoaCacCho() throws Exception {
        int examId = build(matrixId).get("examId").asInt();
        settle(examId);
        assertEquals(0, count("SELECT count(*) FROM exam_slot WHERE exam_id = ? AND state = 'generating'", examId));

        Connection lock = holdSlotLocks(examId);
        try {
            assertEquals(200, viewStatus(examId).get(5, TimeUnit.SECONDS),
                    "đề không còn chỗ generating thì xem đề không chờ khóa của giao dịch khác");
            perform(get("/api/exams/" + examId + "/progress")).andExpect(status().isOk());
        } finally {
            lock.rollback();
            lock.close();
        }
    }

    @Test
    void deConChoDangSinhVanKhoaRoiDoiSoatDungNhuCu() throws Exception {
        int examId = build(matrixId).get("examId").asInt();
        processAll();
        assertEquals(2, count("SELECT count(*) FROM exam_slot WHERE exam_id = ? AND state = 'generating'", examId),
                "job đã xong nhưng chưa đối soát");

        Connection lock = holdSlotLocks(examId);
        Future<Integer> view = viewStatus(examId);
        try {
            assertThrows(TimeoutException.class, () -> view.get(1500, TimeUnit.MILLISECONDS),
                    "đề còn chỗ generating thì vẫn khóa các chỗ: phải chờ giao dịch đang giữ khóa");
        } finally {
            lock.rollback();
            lock.close();
        }
        assertEquals(200, view.get(15, TimeUnit.SECONDS));
        assertEquals(0, count("SELECT count(*) FROM exam_slot WHERE exam_id = ? AND state = 'generating'", examId),
                "sau khi có khóa, đối soát chạy như cũ: các chỗ đã có câu");
        assertEquals(2, count("SELECT count(*) FROM exam_slot WHERE exam_id = ? AND state = 'ready'", examId));
    }
}
