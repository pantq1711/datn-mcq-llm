package com.datn.mcq.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Tiện ích xác thực cho test (W1-1). Chỉ dùng thư viện (spring-security-test, Nimbus, crypto), không phụ thuộc lớp
 * main, để test viết trước vẫn biên dịch được.
 *
 * <p>{@link #TEST_JWT_SECRET_BASE64} phải trùng {@code app.security.jwt.secret} trong src/test/resources/application.yaml.
 * Đây là khóa giả chỉ dùng cho test, không phải secret thật.
 */
public final class AuthTestSupport {

    public static final String TEST_JWT_SECRET_BASE64 =
            "dGVzdC1vbmx5LWp3dC1zZWNyZXQtZm9yLWRhdG4tbWNxLW5vdC1mb3ItcHJvZHVjdGlvbg==";
    public static final String ISSUER = "datn-mcq";
    public static final String REFRESH_COOKIE = "refresh_token";

    private static final PasswordEncoder ENCODER = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private AuthTestSupport() {}

    /** JWT giả của spring-security-test (không ký), cho test chỉ cần "đang đăng nhập là giáo viên id". */
    public static RequestPostProcessor teacher(int userId) {
        return jwt().jwt(j -> j.subject(String.valueOf(userId)).claim("role", "TEACHER"))
                .authorities(new SimpleGrantedAuthority("ROLE_TEACHER"));
    }

    public static RequestPostProcessor admin(int userId) {
        return jwt().jwt(j -> j.subject(String.valueOf(userId)).claim("role", "ADMIN"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    /** Thêm user có mật khẩu (băm {bcrypt}); trả id. */
    public static int insertUser(JdbcTemplate jdbc, String name, String email, String role, String rawPassword,
                                 boolean enabled) {
        return jdbc.queryForObject("INSERT INTO users (name, email, role, password_hash, enabled) VALUES (?, ?, ?, ?, ?)"
                        + " RETURNING id", Integer.class, name, email, role,
                rawPassword == null ? null : ENCODER.encode(rawPassword), enabled);
    }

    public static String encode(String rawPassword) {
        return ENCODER.encode(rawPassword);
    }

    /** Access token HS256 tự ký (dùng để thử token hết hạn, sai khóa, sai issuer). */
    public static String signedToken(byte[] key, String subject, String role, String issuer, Instant issuedAt,
                                     Instant expiresAt) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .issuer(issuer)
                    .claim("role", role)
                    .issueTime(Date.from(issuedAt))
                    .expirationTime(Date.from(expiresAt))
                    .jwtID(UUID.randomUUID().toString())
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(key));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] testKey() {
        return Base64.getDecoder().decode(TEST_JWT_SECRET_BASE64);
    }

    public static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
