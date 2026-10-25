package com.datn.mcq.auth.service;

import com.datn.mcq.auth.dto.AuthDtos.TokenResponse;
import com.datn.mcq.auth.dto.AuthDtos.UserDto;
import com.datn.mcq.auth.entity.RefreshToken;
import com.datn.mcq.auth.service.AccessTokenService.AccessToken;
import com.datn.mcq.auth.service.RefreshTokenService.Issued;
import com.datn.mcq.auth.service.RefreshTokenService.Rotation;
import com.datn.mcq.common.exception.AuthFailureException;
import com.datn.mcq.common.exception.LoginRateLimitedException;
import com.datn.mcq.enums.UserRole;
import com.datn.mcq.user.entity.UserAccount;
import com.datn.mcq.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * Đăng nhập, refresh, đăng xuất, đổi mật khẩu (W1-1). Chỉ giáo viên và admin có mật khẩu, đang hoạt động mới đăng nhập
 * được. Mọi trường hợp đăng nhập sai (email không có, sai mật khẩu, tài khoản khóa) trả cùng một lỗi;
 * {@link DaoAuthenticationProvider} còn so mật khẩu giả khi không có email để thời gian phản hồi không lộ email.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    public static final String BAD_CREDENTIALS_MESSAGE = "Email hoặc mật khẩu không đúng";
    public static final String REFRESH_INVALID_MESSAGE =
            "Phiên đăng nhập không hợp lệ hoặc đã hết hạn, hãy đăng nhập lại";
    public static final String WRONG_CURRENT_PASSWORD = "Mật khẩu hiện tại không đúng";
    static final int MIN_PASSWORD_LENGTH = 8;
    static final int MAX_PASSWORD_BYTES = 72;
    private static final Set<UserRole> LOGIN_ROLES = Set.of(UserRole.TEACHER, UserRole.ADMIN);

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final AccessTokenService accessTokenService;
    private final PasswordEncoder passwordEncoder;
    private final DaoAuthenticationProvider authenticationProvider;
    private final LoginAttemptLimiter loginAttemptLimiter;

    public AuthService(UserService userService, RefreshTokenService refreshTokenService,
                       AccessTokenService accessTokenService, PasswordEncoder passwordEncoder,
                       LoginAttemptLimiter loginAttemptLimiter) {
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
        this.accessTokenService = accessTokenService;
        this.passwordEncoder = passwordEncoder;
        this.loginAttemptLimiter = loginAttemptLimiter;
        this.authenticationProvider = new DaoAuthenticationProvider(this::loadForLogin);
        this.authenticationProvider.setPasswordEncoder(passwordEncoder);
    }

    /** Kết quả đăng nhập/refresh: JSON trả về và refresh token để đặt cookie. */
    public record Session(TokenResponse body, String refreshToken, Duration refreshMaxAge) {}

    /**
     * @param clientIp IP người gọi, cùng email làm khóa giới hạn đăng nhập sai (sai quá nhiều lần: 429, kể cả khi đúng
     *                 mật khẩu, tới khi hết thời gian khóa)
     */
    public Session login(String email, String password, String userAgent, String clientIp) {
        if (email == null || email.isBlank() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("email và password không được để trống");
        }
        String normalized = normalizeEmail(email);
        String limitKey = LoginAttemptLimiter.key(normalized, clientIp);
        Duration locked = loginAttemptLimiter.lockedFor(limitKey);
        if (!locked.isZero()) {
            throw new LoginRateLimitedException(Math.max(1, (locked.toMillis() + 999) / 1000));
        }
        Authentication result;
        try {
            result = authenticationProvider.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(normalized, password));
        } catch (AuthenticationException e) {
            loginAttemptLimiter.recordFailure(limitKey);
            throw new AuthFailureException(AuthFailureException.INVALID_CREDENTIALS, BAD_CREDENTIALS_MESSAGE);
        }
        loginAttemptLimiter.recordSuccess(limitKey);
        Integer userId = Integer.valueOf(((UserDetails) result.getPrincipal()).getUsername());
        UserAccount user = userService.recordLogin(userId);
        refreshTokenService.purgeExpired(userId);
        return session(user, refreshTokenService.issueNewFamily(userId, userAgent));
    }

    public Session refresh(String refreshToken, String userAgent) {
        Rotation rotation = refreshTokenService.rotate(refreshToken, userAgent, this::canLogin);
        if (rotation.issued() == null) {
            log.debug("Refresh bị từ chối: {}", rotation.rejection());
            throw new AuthFailureException(AuthFailureException.REFRESH_INVALID, REFRESH_INVALID_MESSAGE);
        }
        return session(userService.getById(rotation.issued().userId()), rotation.issued());
    }

    public void logout(String refreshToken) {
        refreshTokenService.revokeFamilyOf(refreshToken, RefreshToken.LOGOUT);
    }

    public UserDto me(Integer userId) {
        return UserDto.of(userService.getById(userId));
    }

    /** Sai mật khẩu hiện tại hoặc mật khẩu mới không hợp lệ: 400. Thành công: thu hồi mọi phiên, phải đăng nhập lại. */
    public void changePassword(Integer userId, String currentPassword, String newPassword) {
        UserAccount user = userService.getById(userId);
        if (currentPassword == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException(WRONG_CURRENT_PASSWORD);
        }
        validateNewPassword(newPassword, MIN_PASSWORD_LENGTH);
        userService.updatePasswordHash(userId, passwordEncoder.encode(newPassword));
        refreshTokenService.revokeAllForUser(userId, RefreshToken.PASSWORD_CHANGED);
    }

    /** Mật khẩu từ {@code minLength} ký tự, tối đa 72 byte UTF-8 (giới hạn của BCrypt). */
    public static void validateNewPassword(String password, int minLength) {
        if (password == null || password.codePointCount(0, password.length()) < minLength
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new IllegalArgumentException("Mật khẩu mới phải từ " + minLength + " ký tự và không quá "
                    + MAX_PASSWORD_BYTES + " byte");
        }
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private Session session(UserAccount user, Issued refresh) {
        AccessToken access = accessTokenService.issue(user);
        TokenResponse body = new TokenResponse(access.value(), "Bearer", access.expiresInSeconds(), UserDto.of(user));
        return new Session(body, refresh.rawToken(), refresh.maxAge());
    }

    private boolean canLogin(int userId) {
        return userService.findById(userId).map(this::loginAllowed).orElse(false);
    }

    private boolean loginAllowed(UserAccount u) {
        return Boolean.TRUE.equals(u.getEnabled()) && u.getPasswordHash() != null && LOGIN_ROLES.contains(u.getRole());
    }

    /** UserDetails cho đăng nhập: username là id; user không đủ điều kiện coi như không tồn tại. */
    private UserDetails loadForLogin(String email) {
        UserAccount u = userService.findByEmail(email)
                .filter(a -> a.getPasswordHash() != null && LOGIN_ROLES.contains(a.getRole()))
                .orElseThrow(() -> new UsernameNotFoundException("not found"));
        return User.withUsername(String.valueOf(u.getId()))
                .password(u.getPasswordHash())
                .disabled(!Boolean.TRUE.equals(u.getEnabled()))
                .roles(u.getRole().name())
                .build();
    }
}
