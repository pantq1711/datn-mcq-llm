package com.datn.mcq.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Đăng ký {@link BankProperties} (NV-31). */
@Configuration
@EnableConfigurationProperties(BankProperties.class)
public class BankConfig {
}
