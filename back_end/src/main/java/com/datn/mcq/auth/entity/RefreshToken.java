package com.datn.mcq.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/** Refresh token (V11). Chỉ lưu SHA-256 hex của token gốc. */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    public static final String ROTATED = "rotated";
    public static final String LOGOUT = "logout";
    public static final String REUSE_DETECTED = "reuse_detected";
    public static final String ADMIN_DISABLED = "admin_disabled";
    public static final String PASSWORD_CHANGED = "password_changed";
    /** V12: admin đổi email của tài khoản. */
    public static final String EMAIL_CHANGED = "email_changed";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "family_expires_at", nullable = false)
    private LocalDateTime familyExpiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoke_reason", length = 20)
    private String revokeReason;

    @Column(name = "replaced_by_id")
    private Long replacedById;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    protected RefreshToken() {}

    public RefreshToken(Integer userId, String tokenHash, UUID familyId, LocalDateTime issuedAt,
                        LocalDateTime expiresAt, LocalDateTime familyExpiresAt, String userAgent) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.familyExpiresAt = familyExpiresAt;
        this.userAgent = userAgent;
    }

    public void revoke(LocalDateTime at, String reason) {
        this.revokedAt = at;
        this.revokeReason = reason;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public Long getId() {
        return id;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getFamilyExpiresAt() {
        return familyExpiresAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public String getRevokeReason() {
        return revokeReason;
    }

    public Long getReplacedById() {
        return replacedById;
    }

    public void setReplacedById(Long replacedById) {
        this.replacedById = replacedById;
    }

    public String getUserAgent() {
        return userAgent;
    }
}
