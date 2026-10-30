package com.datn.mcq.auth.repository;

import com.datn.mcq.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** Khóa hàng (SELECT ... FOR UPDATE) khi refresh: hai lần refresh đồng thời cùng token không cùng thành công. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RefreshToken t WHERE t.tokenHash = :hash")
    Optional<RefreshToken> lockByTokenHash(@Param("hash") String hash);

    /**
     * D6: số token của chuỗi đã bị thu hồi vì lý do KHÁC {@code rotatedReason} (đăng xuất, đổi mật khẩu, khóa, đã phát hiện
     * dùng lại...). Lớn hơn 0 nghĩa là chuỗi đã chết, không phải chỉ vừa xoay vòng.
     */
    @Query("SELECT count(t) FROM RefreshToken t WHERE t.familyId = :family AND t.revokedAt IS NOT NULL"
            + " AND t.revokeReason <> :rotatedReason")
    long countRevokedForOtherReason(@Param("family") UUID family, @Param("rotatedReason") String rotatedReason);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = :at, t.revokeReason = :reason"
            + " WHERE t.familyId = :family AND t.revokedAt IS NULL")
    int revokeFamily(@Param("family") UUID family, @Param("reason") String reason, @Param("at") LocalDateTime at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = :at, t.revokeReason = :reason"
            + " WHERE t.userId = :userId AND t.revokedAt IS NULL")
    int revokeAllForUser(@Param("userId") Integer userId, @Param("reason") String reason,
                         @Param("at") LocalDateTime at);

    /** Dọn token đã hết hạn từ trước {@code before} của một người (gọi khi người đó đăng nhập). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM RefreshToken t WHERE t.userId = :userId AND t.expiresAt < :before")
    int deleteExpired(@Param("userId") Integer userId, @Param("before") LocalDateTime before);
}
