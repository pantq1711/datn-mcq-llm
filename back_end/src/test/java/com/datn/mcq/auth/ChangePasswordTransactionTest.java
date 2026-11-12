package com.datn.mcq.auth;

import com.datn.mcq.auth.service.AuthService;
import com.datn.mcq.auth.service.RefreshTokenService;
import com.datn.mcq.config.AuthTestSupport;
import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

/**
 * A-11 (rà soát 11/11): đổi mật khẩu cập nhật hash và thu hồi mọi refresh token trong CÙNG một transaction. Thu hồi lỗi
 * thì hash cũ được giữ (rollback), không để mật khẩu đã đổi mà phiên cũ còn sống.
 */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class ChangePasswordTransactionTest {

    private static final String PASSWORD = "matkhau-cu-1";

    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private AuthService authService;
    @MockitoSpyBean
    private RefreshTokenService refreshTokenService;

    private int teacher;

    @BeforeEach
    void setUp() {
        TestDataCleaner.cleanAll(dataSource, jdbc, "ChangePasswordTransactionTest.setUp");
        Mockito.reset(refreshTokenService);
        teacher = AuthTestSupport.insertUser(jdbc, "GV", "gv.doimk@test.vn", "teacher", PASSWORD, true);
    }

    private String hash() {
        return jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, teacher);
    }

    @Test
    void thuHoiTokenLoiThiHashMoiRollback() {
        String before = hash();
        Mockito.doThrow(new IllegalStateException("thu hồi lỗi giả lập"))
                .when(refreshTokenService).revokeAllForUser(anyInt(), anyString());

        assertThrows(IllegalStateException.class,
                () -> authService.changePassword(teacher, PASSWORD, "matkhau-moi-12345"));

        assertEquals(before, hash(), "bước 2 lỗi thì bước 1 (cập nhật hash) rollback");
    }

    @Test
    void thanhCongThiDoiHash() {
        String before = hash();
        authService.changePassword(teacher, PASSWORD, "matkhau-moi-12345");
        assertNotEquals(before, hash());
    }
}
