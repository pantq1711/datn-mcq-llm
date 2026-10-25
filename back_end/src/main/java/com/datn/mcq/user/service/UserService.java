package com.datn.mcq.user.service;

import com.datn.mcq.enums.UserRole;
import com.datn.mcq.user.entity.UserAccount;
import com.datn.mcq.user.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class UserService {

    private final UserAccountRepository userAccountRepository;

    public UserService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public UserAccount getById(Integer id) {
        return userAccountRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy người dùng id=" + id));
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> findById(Integer id) {
        return userAccountRepository.findById(id);
    }

    /** Email không phân biệt hoa thường, bỏ khoảng trắng hai đầu. */
    @Transactional(readOnly = true)
    public Optional<UserAccount> findByEmail(String email) {
        return email == null ? Optional.empty() : userAccountRepository.findByNormalizedEmail(email);
    }

    @Transactional(readOnly = true)
    public boolean existsActiveUserWithRole(UserRole role) {
        return userAccountRepository.existsByRoleAndEnabledTrue(role);
    }

    /** Tạo tài khoản có mật khẩu (đã băm). Email lưu dạng chữ thường. */
    @Transactional
    public UserAccount createUser(String name, String email, UserRole role, String passwordHash) {
        UserAccount u = new UserAccount(name, email.trim().toLowerCase(java.util.Locale.ROOT), role);
        u.setPasswordHash(passwordHash);
        return userAccountRepository.save(u);
    }

    /** Mọi tài khoản (hoặc theo vai trò), id tăng dần. */
    @Transactional(readOnly = true)
    public List<UserAccount> list(UserRole role) {
        return role == null ? userAccountRepository.findAllByOrderByIdAsc()
                : userAccountRepository.findAllByRoleOrderByIdAsc(role);
    }

    /** Khóa hàng các tài khoản đang hoạt động của vai trò; gọi trong transaction của nơi gọi. */
    @Transactional
    public List<UserAccount> lockActiveUsersWithRole(UserRole role) {
        return userAccountRepository.lockActiveByRole(role);
    }

    @Transactional
    public UserAccount rename(Integer id, String name) {
        UserAccount u = getById(id);
        u.setName(name);
        return userAccountRepository.save(u);
    }

    /** Đổi email (nơi gọi đã chuẩn hóa và kiểm tra trùng; unique index V11 chặn trường hợp đua). */
    @Transactional
    public UserAccount changeEmail(Integer id, String email) {
        UserAccount u = getById(id);
        u.setEmail(email);
        return userAccountRepository.saveAndFlush(u);
    }

    @Transactional
    public UserAccount setEnabled(Integer id, boolean enabled) {
        UserAccount u = getById(id);
        u.setEnabled(enabled);
        return userAccountRepository.save(u);
    }

    /** Ghi lúc đăng nhập thành công; trả bản đã cập nhật. */
    @Transactional
    public UserAccount recordLogin(Integer id) {
        UserAccount u = getById(id);
        u.setLastLoginAt(now());
        return userAccountRepository.save(u);
    }

    @Transactional
    public void updatePasswordHash(Integer id, String passwordHash) {
        UserAccount u = getById(id);
        u.setPasswordHash(passwordHash);
        u.setPasswordChangedAt(now());
        userAccountRepository.save(u);
    }

    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }
}
