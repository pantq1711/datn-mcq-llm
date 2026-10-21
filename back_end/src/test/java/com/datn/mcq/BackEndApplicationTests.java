package com.datn.mcq;

import com.datn.mcq.config.TestSafetyConfiguration;
import com.datn.mcq.llm.client.LlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest
@Import(TestSafetyConfiguration.class)
class BackEndApplicationTests {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private LlmClient llmClient;

	@Test
	void contextLoads() {
		TestSafetyConfiguration.TestDatabaseGuard.validateTestDatabase(
				dataSource, "BackEndApplicationTests.contextLoads");
		assertInstanceOf(TestSafetyConfiguration.BlockingTestLlmClient.class, llmClient);
		assertFalse(llmClient.isConfigured());
	}

}
