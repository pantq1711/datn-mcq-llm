package com.datn.mcq.auth.service;

import com.datn.mcq.config.SecurityProperties;
import com.datn.mcq.user.entity.UserAccount;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/** Cấp access token JWT HS256: sub = id người dùng, role = TEACHER/ADMIN, kèm iss, iat, exp, jti; không chứa email. */
@Service
public class AccessTokenService {

    private final JwtEncoder encoder;
    private final SecurityProperties properties;

    public AccessTokenService(JwtEncoder encoder, SecurityProperties properties) {
        this.encoder = encoder;
        this.properties = properties;
    }

    public record AccessToken(String value, long expiresInSeconds) {}

    public AccessToken issue(UserAccount user) {
        Instant now = Instant.now();
        long ttl = properties.jwt().accessTokenTtl().toSeconds();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttl))
                .id(UUID.randomUUID().toString())
                .claim("role", user.getRole().name())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return new AccessToken(encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue(), ttl);
    }
}
