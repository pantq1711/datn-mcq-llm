package com.datn.mcq.auth.controller;

import com.datn.mcq.auth.dto.AuthDtos.ChangePasswordRequest;
import com.datn.mcq.auth.dto.AuthDtos.LoginRequest;
import com.datn.mcq.auth.dto.AuthDtos.TokenResponse;
import com.datn.mcq.auth.dto.AuthDtos.UserDto;
import com.datn.mcq.auth.service.AuthService;
import com.datn.mcq.auth.service.AuthService.Session;
import com.datn.mcq.common.exception.AuthFailureException;
import com.datn.mcq.common.security.CurrentUser;
import com.datn.mcq.config.SecurityProperties;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

/**
 * Đăng nhập (W1-1). Access token trả trong JSON (frontend giữ trong bộ nhớ, gửi header Authorization: Bearer);
 * refresh token đặt trong cookie HttpOnly, Secure (cấu hình), SameSite=Strict, Path=/api/auth.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public static final String REFRESH_COOKIE = "refresh_token";
    static final String COOKIE_PATH = "/api/auth";

    private final AuthService authService;
    private final SecurityProperties properties;

    public AuthController(AuthService authService, SecurityProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    /**
     * Đăng nhập bằng email và mật khẩu. Sai thông tin: 401 INVALID_CREDENTIALS. Sai quá nhiều lần (theo email + IP):
     * 429 TOO_MANY_ATTEMPTS kèm Retry-After.
     */
    @PostMapping("/login")
    @SecurityRequirements
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request,
                                               @RequestHeader(value = HttpHeaders.USER_AGENT, required = false)
                                               String userAgent,
                                               HttpServletRequest httpRequest) {
        Session s = authService.login(request.email(), request.password(), userAgent, httpRequest.getRemoteAddr());
        return withCookie(s);
    }

    /** Đổi refresh token trong cookie lấy cặp token mới (xoay vòng). Không hợp lệ: 401 REFRESH_INVALID, xóa cookie. */
    @PostMapping("/refresh")
    @SecurityRequirements
    public ResponseEntity<?> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
                                     @RequestHeader(value = HttpHeaders.USER_AGENT, required = false)
                                     String userAgent) {
        try {
            return withCookie(authService.refresh(refreshToken, userAgent));
        } catch (AuthFailureException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, clearCookie().toString())
                    .body(Map.of("error", e.getMessage(), "code", e.getCode()));
        }
    }

    /** Đăng xuất: thu hồi phiên của cookie (nếu có) và xóa cookie. Luôn 204. */
    @PostMapping("/logout")
    @SecurityRequirements
    public ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearCookie().toString()).build();
    }

    @GetMapping("/me")
    public UserDto me(CurrentUser user) {
        return authService.me(user.id());
    }

    /** Đổi mật khẩu; thành công thì mọi phiên bị thu hồi (204, xóa cookie), phải đăng nhập lại. */
    @PostMapping("/password")
    public ResponseEntity<Void> changePassword(CurrentUser user, @RequestBody ChangePasswordRequest request) {
        authService.changePassword(user.id(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clearCookie().toString()).build();
    }

    private ResponseEntity<TokenResponse> withCookie(Session s) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(s.refreshToken(), s.refreshMaxAge()).toString())
                .body(s.body());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(Boolean.TRUE.equals(properties.cookieSecure()))
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie clearCookie() {
        return cookie("", Duration.ZERO);
    }
}
