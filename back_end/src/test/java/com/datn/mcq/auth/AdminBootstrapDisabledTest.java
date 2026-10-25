package com.datn.mcq.auth;

import com.datn.mcq.config.TestDataCleaner;
import com.datn.mcq.config.TestSafetyConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** W1-1: không đặt ADMIN_EMAIL/ADMIN_PASSWORD (cấu hình test mặc định) thì bootstrap không tạo tài khoản nào. */
@SpringBootTest
@Import(TestSafetyConfiguration.class)
class AdminBootstrapDisabledTest {

    @Autowired
    private ApplicationContext context;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void thieuBienMoiTruongThiKhongTaoAdmin() throws Exception {
        TestDataCleaner.cleanAll(dataSource, jdbc, "AdminBootstrapDisabledTest");
        context.getBean("adminBootstrap", ApplicationRunner.class).run(new DefaultApplicationArguments());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM users", Integer.class));
    }
}
