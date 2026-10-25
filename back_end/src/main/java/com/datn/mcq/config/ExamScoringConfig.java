package com.datn.mcq.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Đăng ký {@link ExamScoringProperties} (V4). */
@Configuration
@EnableConfigurationProperties(ExamScoringProperties.class)
public class ExamScoringConfig {
}
