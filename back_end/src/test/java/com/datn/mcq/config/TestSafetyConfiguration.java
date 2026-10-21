package com.datn.mcq.config;

import com.datn.mcq.enums.Strategy;
import com.datn.mcq.llm.client.LlmClient;
import com.datn.mcq.llm.dto.LlmCallResult;
import org.flywaydb.core.Flyway;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Cấu hình bảo vệ môi trường test (Fail-Closed):
 * 1. Đặt @Configuration trong package com.datn.mcq.config (thuộc phạm vi @ComponentScan của BackEndApplication)
 *    để tự động nạp cho mọi @SpringBootTest ngay cả khi người viết test mới quên gắn @Import.
 * 2. Chặn mọi kết nối tới database khác 'datn_mcq_test' trên cả DataSource chính và DataSource riêng của Flyway
 *    (kể cả khi cấu hình spring.flyway.url riêng) trước khi Flyway chạy migrate() hoặc Hibernate validate.
 * 3. Đăng ký @Primary BlockingTestLlmClient để chặn toàn bộ đường gọi mạng tới Gemini API qua DI.
 */
@Configuration
public class TestSafetyConfiguration {

    public static final String REQUIRED_TEST_DB = "datn_mcq_test";

    @Bean
    public static TestDatabaseGuard testDatabaseGuard() {
        return new TestDatabaseGuard();
    }

    @Bean
    public FlywayMigrationStrategy safeFlywayMigrationStrategy() {
        return flyway -> {
            if (flyway == null || flyway.getConfiguration() == null) {
                throw new IllegalStateException("FATAL [FlywayMigrationStrategy]: Flyway hoặc Configuration bị null!");
            }
            DataSource flywayDataSource = flyway.getConfiguration().getDataSource();
            TestDatabaseGuard.validateTestDatabase(flywayDataSource, "FlywayMigrationStrategy Pre-Migrate Guard");
            flyway.migrate();
        };
    }

    @Bean
    @Primary
    public LlmClient blockingTestLlmClient() {
        return new BlockingTestLlmClient();
    }

    public static class TestDatabaseGuard implements BeanPostProcessor, PriorityOrdered {

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof Flyway flyway) {
                DataSource flywayDs = flyway.getConfiguration() != null
                        ? flyway.getConfiguration().getDataSource()
                        : null;
                validateTestDatabase(flywayDs, "Flyway Bean Pre-Initialization [" + beanName + "]");
            }
            return bean;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
            if (bean instanceof DataSource dataSource) {
                validateTestDatabase(dataSource, "DataSource BeanPostProcessor [" + beanName + "]");
            } else if (bean instanceof Flyway flyway) {
                DataSource flywayDs = flyway.getConfiguration() != null
                        ? flyway.getConfiguration().getDataSource()
                        : null;
                validateTestDatabase(flywayDs, "Flyway Bean Post-Initialization [" + beanName + "]");
            }
            return bean;
        }

        public static void validateTestDatabase(DataSource dataSource, String stage) {
            if (dataSource == null) {
                throw new IllegalStateException("FATAL [" + stage + "]: DataSource is null (Fail-Closed)!");
            }
            try (Connection conn = dataSource.getConnection()) {
                String catalog = conn.getCatalog();
                String url = conn.getMetaData() != null ? conn.getMetaData().getURL() : "unknown";
                if (catalog == null || !REQUIRED_TEST_DB.equalsIgnoreCase(catalog.trim())) {
                    String errorMsg = String.format(
                            "\n========================================================================================\n" +
                            "FATAL DATABASE GUARD FAILURE [%s]:\n" +
                            "Integration test must ONLY connect to test database '%s'!\n" +
                            "Actual runtime connection:\n" +
                            "  - Current Catalog/Database: '%s'\n" +
                            "  - JDBC URL:                 '%s'\n" +
                            "ABORTING execution immediately to protect development database from test mutations.\n" +
                            "========================================================================================",
                            stage, REQUIRED_TEST_DB, catalog, url
                    );
                    throw new IllegalStateException(errorMsg);
                }
            } catch (SQLException e) {
                throw new IllegalStateException(
                        "FATAL [" + stage + "]: Cannot connect or inspect database metadata to verify '"
                                + REQUIRED_TEST_DB + "' (Fail-Closed)!", e);
            }
        }
    }

    public static class BlockingTestLlmClient extends LlmClient {

        public BlockingTestLlmClient() {
            super("BLOCKED_FOR_TESTS", "blocked-test-model");
        }

        @Override
        public boolean isConfigured() {
            return false;
        }

        @Override
        public LlmCallResult generateMcq(String prompt, String modelName, Strategy strategy, Double temperature) {
            throw new UnsupportedOperationException(
                    "BLOCKED: Real Gemini API calls are strictly forbidden in test executions! " +
                    "Mock LlmClient or pass rawLlmOutputOverride instead.");
        }

        @Override
        public LlmCallResult callJsonMode(String prompt, String modelName, Double temperature) {
            throw new UnsupportedOperationException(
                    "BLOCKED: Real Gemini API calls are strictly forbidden in test executions! " +
                    "Mock LlmClient or pass fake response instead.");
        }
    }
}
