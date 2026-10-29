package com.datn.mcq.config;

import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * Xác thực và phân quyền (W1-1, Spring Security 7): stateless, access token JWT HS256 ký bằng khóa từ JWT_SECRET,
 * refresh token xoay vòng nằm trong cookie HttpOnly (module auth). Quyền thô theo URL đặt ở đây; quyền sở hữu
 * (giáo viên chỉ thấy dữ liệu của mình) kiểm tra ở service. Đường chưa khai báo bị chặn (denyAll).
 *
 * <p>Worker, scheduler và job phục hồi không đi qua HTTP nên không chịu ảnh hưởng.
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    static final int MIN_SECRET_BYTES = 32;

    @Bean
    public SecretKey jwtSigningKey(SecurityProperties properties) {
        String secret = properties.jwt().secret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Thiếu khóa ký JWT: đặt biến môi trường JWT_SECRET (base64, ít nhất "
                    + MIN_SECRET_BYTES + " byte, ví dụ tạo bằng `openssl rand -base64 48`).");
        }
        byte[] key;
        try {
            key = Base64.getDecoder().decode(secret.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SECRET phải là chuỗi base64 hợp lệ.");
        }
        if (key.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET quá ngắn: cần ít nhất " + MIN_SECRET_BYTES + " byte sau khi giải"
                    + " mã base64 (hiện " + key.length + ").");
        }
        return new SecretKeySpec(key, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSigningKey).algorithm(MacAlgorithm.HS256).build();
    }

    /** Chỉ nhận HS256 (chặn alg=none và nhầm thuật toán), kiểm tra issuer và thời hạn (lệch đồng hồ tối đa 30 giây). */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSigningKey, SecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(30)),
                new JwtIssuerValidator(properties.jwt().issuer())));
        return decoder;
    }

    /** Mật khẩu băm dạng "{bcrypt}..." (đổi thuật toán sau này không phải băm lại toàn bộ). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** Claim "role" (TEACHER, ADMIN) thành quyền ROLE_TEACHER, ROLE_ADMIN. */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(SecurityProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOriginPatterns(properties.cors().allowedOrigins());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT));
        // X-Equation-Kept-Omml: xuất Word dạng MathType, số công thức giữ Equation (ExamController)
        cors.setExposedHeaders(List.of(HttpHeaders.CONTENT_DISPOSITION, HttpHeaders.WWW_AUTHENTICATE,
                "X-Equation-Kept-Omml"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter,
                                                   JsonSecurityErrorHandler errors) throws Exception {
        http
                // API stateless dùng bearer token; cookie refresh chỉ gửi tới /api/auth với SameSite=Strict.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {})
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(a -> a
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh", "/api/auth/logout")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .permitAll()
                        // Quản trị; sinh câu trực tiếp (gọi Gemini tính phí) và seed demo chỉ admin (H6, H5).
                        .requestMatchers("/api/admin/**", "/api/generation/**", "/api/reviews/demo/**", "/actuator/**")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/catalog/templates").hasRole("ADMIN")
                        // Chức năng giáo viên (H4: admin không dùng). Form chấm chỉ cần giáo viên đăng nhập (H5, Q-6).
                        .requestMatchers("/api/exam-matrices", "/api/exam-matrices/**", "/api/exams", "/api/exams/**",
                                "/api/materials", "/api/materials/**",
                                "/api/reviews/**").hasRole("TEACHER")
                        .requestMatchers(HttpMethod.GET, "/api/bank-items", "/api/bank-items/**", "/api/catalog/**")
                        .hasAnyRole("TEACHER", "ADMIN")
                        .requestMatchers("/api/auth/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(errors)
                        .accessDeniedHandler(errors))
                .exceptionHandling(e -> e.authenticationEntryPoint(errors).accessDeniedHandler(errors));
        return http.build();
    }
}
