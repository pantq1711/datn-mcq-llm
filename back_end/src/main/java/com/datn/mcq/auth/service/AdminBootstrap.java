package com.datn.mcq.auth.service;

import com.datn.mcq.config.SecurityProperties;
import com.datn.mcq.config.SecurityProperties.BootstrapAdmin;
import com.datn.mcq.enums.UserRole;
import com.datn.mcq.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Tạo tài khoản ADMIN đầu tiên khi khởi động từ ADMIN_EMAIL, ADMIN_PASSWORD, ADMIN_NAME (W1-1). Chỉ tạo khi chưa có admin
 * nào đang hoạt động; không bao giờ ghi đè mật khẩu; không chiếm email đã thuộc tài khoản khác. Mật khẩu ngắn hơn 12
 * ký tự thì dừng khởi động. Log chỉ ghi email. Tạo xong nên xóa ADMIN_PASSWORD khỏi môi trường.
 */
@Component("adminBootstrap")
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    static final int MIN_ADMIN_PASSWORD_LENGTH = 12;

    private final SecurityProperties properties;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrap(SecurityProperties properties, UserService userService, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        BootstrapAdmin cfg = properties.bootstrapAdmin();
        String email = AuthService.normalizeEmail(cfg.email());
        String password = cfg.password();
        boolean hasAdmin = userService.existsActiveUserWithRole(UserRole.ADMIN);
        if (email == null || email.isEmpty() || password == null || password.isEmpty()) {
            if (!hasAdmin) {
                log.warn("Chưa có tài khoản admin. Đặt ADMIN_EMAIL và ADMIN_PASSWORD rồi khởi động lại để tạo.");
            }
            return;
        }
        if (hasAdmin) {
            log.info("Đã có tài khoản admin; bỏ qua ADMIN_EMAIL/ADMIN_PASSWORD.");
            return;
        }
        if (userService.findByEmail(email).isPresent()) {
            log.warn("Email {} đã thuộc một tài khoản khác; không tạo admin.", email);
            return;
        }
        try {
            AuthService.validateNewPassword(password, MIN_ADMIN_PASSWORD_LENGTH);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("ADMIN_PASSWORD không hợp lệ: cần từ " + MIN_ADMIN_PASSWORD_LENGTH
                    + " ký tự và không quá 72 byte.");
        }
        userService.createUser(cfg.name(), email, UserRole.ADMIN, passwordEncoder.encode(password));
        log.info("Đã tạo tài khoản admin đầu tiên: {}", email);
    }
}
