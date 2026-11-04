package com.datn.mcq.auth.service;

import com.datn.mcq.auth.entity.RefreshToken;
import com.datn.mcq.auth.repository.RefreshTokenRepository;
import com.datn.mcq.config.SecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntPredicate;

/**
 * Refresh token xoay vòng (W1-1). Mỗi phương thức là một transaction riêng; {@link #rotate} trả kết quả thay vì ném lỗi
 * để việc thu hồi cả chuỗi khi phát hiện dùng lại vẫn được commit.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    /** {@link Rotation#rejection()} khi token vừa bị thay trong cửa sổ ân hạn (D6). */
    public static final String RACE = "race";

    private final RefreshTokenRepository repository;
    private final SecurityProperties properties;

    public RefreshTokenService(RefreshTokenRepository repository, SecurityProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    /** Token vừa cấp: giá trị gốc (chỉ gửi trong cookie) và thời gian sống còn lại. */
    public record Issued(Integer userId, String rawToken, Duration maxAge) {}

    /**
     * Kết quả refresh: {@code issued} khác null khi thành công.
     *
     * @param rejection lý do từ chối (để log): not_found, reused, expired, user_inactive, race (D6: token vừa bị thay trong
     *                  cửa sổ ân hạn, không thu hồi gì)
     */
    public record Rotation(Issued issued, String rejection) {
        static Rotation rejected(String reason) {
            return new Rotation(null, reason);
        }
    }

    /** Đăng nhập mới: một chuỗi (family) mới. */
    @Transactional
    public Issued issueNewFamily(Integer userId, String userAgent) {
        LocalDateTime now = now();
        LocalDateTime familyExpires = now.plus(properties.refresh().absoluteTtl());
        return save(userId, UUID.randomUUID(), now, familyExpires, userAgent).issued();
    }

    /**
     * Đổi token: hợp lệ thì thu hồi token cũ ('rotated') và cấp token mới cùng chuỗi, hạn không vượt hạn tuyệt đối của
     * chuỗi. Token đã thu hồi mà vẫn được gửi lên thì thu hồi cả chuỗi ('reuse_detected').
     *
     * @param userActive kiểm tra tài khoản còn hoạt động (theo id)
     */
    @Transactional
    public Rotation rotate(String rawToken, String userAgent, IntPredicate userActive) {
        if (rawToken == null || rawToken.isBlank()) {
            return Rotation.rejected("not_found");
        }
        Optional<RefreshToken> found = repository.lockByTokenHash(sha256Hex(rawToken));
        if (found.isEmpty()) {
            return Rotation.rejected("not_found");
        }
        RefreshToken current = found.get();
        LocalDateTime now = now();
        if (current.isRevoked() && isRecentRotation(current, now)) {
            // D6: tab khác vừa xoay vòng token này; không thu hồi, không cấp token mới.
            log.debug("Refresh token vừa được thay ở lần xoay vòng trước (user {}, family {}): bỏ qua, chờ cookie mới",
                    current.getUserId(), current.getFamilyId());
            return Rotation.rejected(RACE);
        }
        if (current.isRevoked()) {
            int revoked = repository.revokeFamily(current.getFamilyId(), RefreshToken.REUSE_DETECTED, now);
            log.warn("Refresh token đã thu hồi ({}) được dùng lại: user {}, family {}; thu hồi {} token còn hiệu lực",
                    current.getRevokeReason(), current.getUserId(), current.getFamilyId(), revoked);
            return Rotation.rejected("reused");
        }
        if (!current.getExpiresAt().isAfter(now) || !current.getFamilyExpiresAt().isAfter(now)) {
            return Rotation.rejected("expired");
        }
        if (!userActive.test(current.getUserId())) {
            repository.revokeFamily(current.getFamilyId(), RefreshToken.ADMIN_DISABLED, now);
            return Rotation.rejected("user_inactive");
        }
        Saved next = save(current.getUserId(), current.getFamilyId(), now, current.getFamilyExpiresAt(), userAgent);
        current.revoke(now, RefreshToken.ROTATED);
        current.setReplacedById(next.entity().getId());
        repository.save(current);
        return new Rotation(next.issued(), null);
    }

    /**
     * Token đã bị thay bởi chính một lần xoay vòng hợp lệ trong cửa sổ ân hạn, và chuỗi chưa bị thu hồi vì lý do nào khác
     * (đăng xuất, đổi mật khẩu, khóa tài khoản, đã phát hiện dùng lại): lúc đó là tab khác refresh trước, không phải trộm token.
     */
    private boolean isRecentRotation(RefreshToken token, LocalDateTime now) {
        Duration grace = properties.refresh().reuseGrace();
        if (grace.isZero() || !RefreshToken.ROTATED.equals(token.getRevokeReason()) || token.getReplacedById() == null
                || token.getRevokedAt().plus(grace).isBefore(now)) {
            return false;
        }
        return repository.countRevokedForOtherReason(token.getFamilyId(), RefreshToken.ROTATED) == 0;
    }

    /** Đăng xuất: thu hồi chuỗi của token (nếu có); gọi lại hoặc token lạ thì không làm gì. */
    @Transactional
    public void revokeFamilyOf(String rawToken, String reason) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.lockByTokenHash(sha256Hex(rawToken))
                .ifPresent(t -> repository.revokeFamily(t.getFamilyId(), reason, now()));
    }

    @Transactional
    public int revokeAllForUser(Integer userId, String reason) {
        return repository.revokeAllForUser(userId, reason, now());
    }

    /** Dọn token đã hết hạn quá một ngày của một người. */
    @Transactional
    public void purgeExpired(Integer userId) {
        repository.deleteExpired(userId, now().minusDays(1));
    }

    private record Saved(Issued issued, RefreshToken entity) {}

    private Saved save(Integer userId, UUID family, LocalDateTime now, LocalDateTime familyExpires, String userAgent) {
        LocalDateTime candidate = now.plus(properties.refresh().ttl());
        LocalDateTime expires = candidate.isBefore(familyExpires) ? candidate : familyExpires;
        String raw = newRawToken();
        RefreshToken entity = repository.save(new RefreshToken(userId, sha256Hex(raw), family, now, expires,
                familyExpires, truncate(userAgent)));
        return new Saved(new Issued(userId, raw, Duration.between(now, expires)), entity);
    }

    private static LocalDateTime now() {
        // Postgres lưu tới micro giây.
        return LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String truncate(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        return userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent;
    }
}
