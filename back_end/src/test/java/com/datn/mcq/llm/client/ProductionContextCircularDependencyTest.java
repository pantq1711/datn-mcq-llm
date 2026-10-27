package com.datn.mcq.llm.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Kiểm tra nạp ApplicationContext thật của ứng dụng mà KHÔNG thay LlmClient, GeminiRateLimiter, GenerationJobProcessor
 * bằng bean giả. Bắt lỗi vòng phụ thuộc bean trong luồng sản xuất.
 */
@SpringBootTest(
        classes = com.datn.mcq.BackEndApplication.class,
        properties = "app.worker.enabled=false"
)
@org.springframework.context.annotation.Import(ProductionContextCircularDependencyTest.ExcludeFakeLlmClientConfig.class)
class ProductionContextCircularDependencyTest {

    static class ExcludeFakeLlmClientConfig {
        @Bean
        public static BeanDefinitionRegistryPostProcessor removeFakeLlmClient() {
            return registry -> {
                if (registry.containsBeanDefinition("blockingTestLlmClient")) {
                    registry.removeBeanDefinition("blockingTestLlmClient");
                }
            };
        }
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired(required = false)
    private GeminiRateLimiter rateLimiter;

    @Autowired(required = false)
    private LlmClient llmClient;

    @Test
    void contextLoadsWithoutFakeLlmClientAndContainsRateLimiter() {
        assertNotNull(applicationContext, "ApplicationContext phải nạp được thành công");
        assertNotNull(rateLimiter, "Bean GeminiRateLimiter phải tồn tại");
        assertNotNull(llmClient, "Bean LlmClient thật phải tồn tại");
        assertTrue(llmClient.getClass().equals(LlmClient.class), "LlmClient phải là bean thật, không bị thay bằng bean giả");
    }
}

