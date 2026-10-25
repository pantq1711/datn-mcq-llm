package com.datn.mcq.config;

import com.datn.mcq.common.util.SimpleJson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Lỗi 401/403 phát sinh trong filter chain (không đi qua {@code ApiExceptionHandler}) trả JSON cùng format của dự án:
 * {@code {"error": "...", "code": "..."}} (H9). Mã: UNAUTHENTICATED (không có token), TOKEN_EXPIRED, TOKEN_INVALID,
 * FORBIDDEN (sai vai trò). Giữ header WWW-Authenticate theo RFC 6750.
 */
@Component
public class JsonSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    public static final String UNAUTHENTICATED_MESSAGE = "Chưa đăng nhập";
    public static final String EXPIRED_MESSAGE = "Phiên đăng nhập đã hết hạn";
    public static final String INVALID_MESSAGE = "Token không hợp lệ";
    public static final String FORBIDDEN_MESSAGE = "Bạn không có quyền thực hiện thao tác này";

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        if (ex instanceof OAuth2AuthenticationException oauth) {
            String description = oauth.getError() != null && oauth.getError().getDescription() != null
                    ? oauth.getError().getDescription() : String.valueOf(ex.getMessage());
            boolean expired = description.toLowerCase(Locale.ROOT).contains("expired");
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
            write(response, HttpServletResponse.SC_UNAUTHORIZED, expired ? EXPIRED_MESSAGE : INVALID_MESSAGE,
                    expired ? "TOKEN_EXPIRED" : "TOKEN_INVALID");
            return;
        }
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(response, HttpServletResponse.SC_UNAUTHORIZED, UNAUTHENTICATED_MESSAGE, "UNAUTHENTICATED");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        write(response, HttpServletResponse.SC_FORBIDDEN, FORBIDDEN_MESSAGE, "FORBIDDEN");
    }

    static void write(HttpServletResponse response, int status, String message, String code) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\": " + SimpleJson.quote(message) + ", \"code\": "
                + SimpleJson.quote(code) + "}");
    }
}
