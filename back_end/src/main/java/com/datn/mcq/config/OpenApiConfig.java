package com.datn.mcq.config;

import com.datn.mcq.common.security.CurrentUser;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tài liệu OpenAPI (springdoc): Swagger UI có nút Authorize (bearer JWT). Mặc định mọi API cần token; API đăng nhập
 * đánh dấu {@code @SecurityRequirements()} để công khai. {@link CurrentUser} lấy từ token nên không hiện thành tham số.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";

    static {
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(CurrentUser.class);
    }

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("DATN MCQ API").version("v1")
                        .description("Đăng nhập POST /api/auth/login lấy accessToken, bấm Authorize và dán token."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
