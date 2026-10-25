package com.datn.mcq.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * W1-4 (K-5): lỗi toàn vẹn dữ liệu do tranh chấp (khóa ngoại, trùng khóa duy nhất) trả 409 kèm mã máy đọc được thay vì
 * 500, để hai thao tác đồng thời (ví dụ tạo đề và xóa ma trận) không làm client thấy lỗi máy chủ. Lỗi toàn vẹn khác (CHECK,
 * NOT NULL) là lỗi của chương trình nên vẫn là 500, kèm mã để dễ tìm.
 */
class ApiExceptionHandlerDataIntegrityTest {

    @RestController
    static class ThrowingController {
        @GetMapping("/test/fk")
        public String fk() {
            throw new DataIntegrityViolationException("insert or update on table \"exam\" violates foreign key",
                    new SQLException("violates foreign key constraint \"fk_exam_matrix\"", "23503"));
        }

        @GetMapping("/test/unique")
        public String unique() {
            throw new DuplicateKeyException("duplicate key value violates unique constraint",
                    new SQLException("duplicate key value violates unique constraint \"exam_exam_code_key\"", "23505"));
        }

        @GetMapping("/test/check")
        public String check() {
            throw new DataIntegrityViolationException("violates check constraint",
                    new SQLException("new row violates check constraint \"chk_es_ready\"", "23514"));
        }

        @GetMapping("/test/no-cause")
        public String noCause() {
            throw new DataIntegrityViolationException("không rõ nguyên nhân");
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();

    @Test
    void viPhamKhoaNgoaiTra409FkConflictKhongLoMoTaCsdl() throws Exception {
        mockMvc.perform(get("/test/fk"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FOREIGN_KEY_CONFLICT"))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("fk_exam_matrix"))));
    }

    @Test
    void trungKhoaDuyNhatTra409UniqueConflict() throws Exception {
        mockMvc.perform(get("/test/unique"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UNIQUE_CONFLICT"))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("exam_exam_code_key"))));
    }

    @Test
    void loiToanVenKhacVanLa500CoMa() throws Exception {
        mockMvc.perform(get("/test/check"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("DATA_INTEGRITY"));
        mockMvc.perform(get("/test/no-cause"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("DATA_INTEGRITY"));
    }
}
