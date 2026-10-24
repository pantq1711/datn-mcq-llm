package com.datn.mcq.auth.service;

import com.datn.mcq.auth.dto.AdminUserDtos.AdminUserDto;
import com.datn.mcq.auth.dto.AdminUserDtos.CreateTeacherRequest;
import com.datn.mcq.auth.dto.AdminUserDtos.ResetPasswordRequest;
import com.datn.mcq.auth.dto.AdminUserDtos.UpdateUserRequest;
import com.datn.mcq.auth.entity.RefreshToken;
import com.datn.mcq.enums.UserRole;
import com.datn.mcq.user.entity.UserAccount;
import com.datn.mcq.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Quản trị tài khoản (W1-1 đợt 2, chỉ ADMIN; URL đã chặn ở SecurityConfig). Không có đăng ký tự do: admin tạo giáo viên.
 * Khóa tài khoản hoặc đặt lại mật khẩu thì thu hồi mọi refresh token của người đó (access token cũ còn tối đa 15 phút,
 * quyết định H10). Không tự khóa mình; không khóa admin đang hoạt động cuối cùng (khóa hàng để hai admin khóa nhau đồng
 * thời không làm mất hết admin).
 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    static final int MAX_LENGTH = 255;

    public static final String SELF_DISABLE_MESSAGE = "Không thể tự khóa tài khoản của mình";
    public static final String LAST_ADMIN_MESSAGE = "Không thể khóa admin đang hoạt động cuối cùng";
    public static final String NO_EMAIL_MESSAGE = "Tài khoản chưa có email nên không đặt mật khẩu được";

    private final UserService userService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(UserService userService, RefreshTokenService refreshTokenService,
                            PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<AdminUserDto> list(UserRole role) {
        return userService.list(role).stream().map(AdminUserDto::of).toList();
    }

    @Transactional
    public AdminUserDto createTeacher(CreateTeacherRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("Thiếu dữ liệu tài khoản");
        }
        String name = cleanName(req.name());
        String email = AuthService.normalizeEmail(req.email());
        if (email == null || email.isEmpty() || email.length() > MAX_LENGTH || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("email không hợp lệ");
        }
        AuthService.validateNewPassword(req.password(), AuthService.MIN_PASSWORD_LENGTH);
        if (userService.findByEmail(email).isPresent()) {
            throw new IllegalStateException("Email đã được dùng: " + email);
        }
        try {
            UserAccount created = userService.createUser(name, email, UserRole.TEACHER,
                    passwordEncoder.encode(req.password()));
            log.info("Admin tạo tài khoản giáo viên id={} ({})", created.getId(), email);
            return AdminUserDto.of(created);
        } catch (DataIntegrityViolationException e) {
            // Hai yêu cầu tạo cùng email đồng thời: unique index của V11 chặn.
            throw new IllegalStateException("Email đã được dùng: " + email);
        }
    }

    /**
     * @param actorId admin đang thao tác (không được tự khóa mình)
     */
    @Transactional
    public AdminUserDto update(Integer actorId, Integer userId, UpdateUserRequest req) {
        if (req == null || (req.name() == null && req.enabled() == null)) {
            throw new IllegalArgumentException("Phải gửi name hoặc enabled");
        }
        UserAccount user = userService.getById(userId);
        String name = req.name() != null ? cleanName(req.name()) : null;
        if (Boolean.FALSE.equals(req.enabled()) && Boolean.TRUE.equals(user.getEnabled())) {
            if (Objects.equals(actorId, userId)) {
                throw new IllegalStateException(SELF_DISABLE_MESSAGE);
            }
            if (user.getRole() == UserRole.ADMIN) {
                boolean otherActiveAdmin = userService.lockActiveUsersWithRole(UserRole.ADMIN).stream()
                        .anyMatch(a -> !a.getId().equals(userId));
                if (!otherActiveAdmin) {
                    throw new IllegalStateException(LAST_ADMIN_MESSAGE);
                }
            }
        }
        if (name != null) {
            user = userService.rename(userId, name);
        }
        if (req.enabled() != null && !req.enabled().equals(user.getEnabled())) {
            user = userService.setEnabled(userId, req.enabled());
            if (!req.enabled()) {
                int revoked = refreshTokenService.revokeAllForUser(userId, RefreshToken.ADMIN_DISABLED);
                log.info("Admin {} khóa tài khoản {}; thu hồi {} refresh token", actorId, userId, revoked);
            }
        }
        return AdminUserDto.of(user);
    }

    /** Đặt mật khẩu mới (kể cả tài khoản cũ chưa có mật khẩu) và thu hồi mọi phiên của người đó. */
    @Transactional
    public void resetPassword(Integer userId, ResetPasswordRequest req) {
        UserAccount user = userService.getById(userId);
        AuthService.validateNewPassword(req != null ? req.newPassword() : null, AuthService.MIN_PASSWORD_LENGTH);
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalArgumentException(NO_EMAIL_MESSAGE);
        }
        userService.updatePasswordHash(userId, passwordEncoder.encode(req.newPassword()));
        refreshTokenService.revokeAllForUser(userId, RefreshToken.PASSWORD_CHANGED);
    }

    private static String cleanName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name không được để trống");
        }
        String s = name.strip();
        if (s.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("name dài tối đa " + MAX_LENGTH + " ký tự");
        }
        return s;
    }
}
