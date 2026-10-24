package com.datn.mcq.user.repository;

import com.datn.mcq.enums.UserRole;
import com.datn.mcq.user.entity.UserAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Integer> {
    Optional<UserAccount> findByEmail(String email);
    Optional<UserAccount> findFirstByRoleOrderByIdAsc(UserRole role);

    /** Tìm theo email không phân biệt hoa thường, bỏ khoảng trắng hai đầu (khớp unique index của V11). */
    @Query("SELECT u FROM UserAccount u WHERE lower(trim(u.email)) = lower(trim(:email))")
    Optional<UserAccount> findByNormalizedEmail(@Param("email") String email);

    boolean existsByRoleAndEnabledTrue(UserRole role);

    List<UserAccount> findAllByOrderByIdAsc();

    List<UserAccount> findAllByRoleOrderByIdAsc(UserRole role);

    /** Khóa (FOR UPDATE) các tài khoản đang hoạt động của một vai trò, để kiểm tra "admin cuối cùng" không bị đua. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserAccount u WHERE u.role = :role AND u.enabled = TRUE ORDER BY u.id")
    List<UserAccount> lockActiveByRole(@Param("role") UserRole role);
}
