package com.datn.mcq.config;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guard DB của test (TestSafetyConfiguration) sau khi nhận thêm DB tạm theo nhánh ({@code datn_mcq_<tên>_test}, đặt qua
 * {@code back_end/.test-db.properties}): vẫn từ chối DB dev, e2e, d2 và mọi tên khác; chỉ nhận datn_mcq_test và DB tạm
 * đúng mẫu. Không kết nối DB thật (DataSource giả).
 */
class TestDatabaseGuardTest {

    private static DataSource dataSourceOn(String catalog) throws SQLException {
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        DatabaseMetaData meta = mock(DatabaseMetaData.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.getCatalog()).thenReturn(catalog);
        when(conn.getMetaData()).thenReturn(meta);
        when(meta.getURL()).thenReturn("jdbc:postgresql://localhost/" + catalog);
        return ds;
    }

    @ParameterizedTest(name = "từ chối [{0}]")
    @NullSource
    @ValueSource(strings = {"datn_mcq", "datn_mcq_e2e", "datn_mcq_d2", "DATN_MCQ_E2E", "datn_mcq_feature", "postgres",
            "datn_mcq_test_e2e", "datn_mcq__test", "datn_mcq_online-test", "x_datn_mcq_test", "datn_mcq_test2", ""})
    void rejectsEveryOtherDatabase(String catalog) throws SQLException {
        DataSource ds = dataSourceOn(catalog);
        assertThrows(IllegalStateException.class,
                () -> TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(ds, "TestDatabaseGuardTest"));
    }

    @ParameterizedTest(name = "nhận [{0}]")
    @ValueSource(strings = {"datn_mcq_test", "datn_mcq_online_test", " datn_mcq_online_test "})
    void acceptsTheTestDatabaseAndBranchTestDatabases(String catalog) throws SQLException {
        DataSource ds = dataSourceOn(catalog);
        assertDoesNotThrow(() -> TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(ds,
                "TestDatabaseGuardTest"));
    }

    @org.junit.jupiter.api.Test
    void rejectsWhenTheConnectionFails() throws SQLException {
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenThrow(new SQLException("down"));
        assertThrows(IllegalStateException.class,
                () -> TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(ds, "TestDatabaseGuardTest"));
    }
}
