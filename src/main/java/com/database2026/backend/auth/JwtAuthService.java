package com.database2026.backend.auth;

import com.database2026.backend.common.DomainException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class JwtAuthService {

    private static final String TOKEN_PREFIX = "Bearer ";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String LOCAL_DEV_SECRET = "local-development-jwt-secret-change-me-please-32-bytes";

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final SecretKeySpec secretKey;
    private final Duration accessTokenTtl;
    private final Base64.Encoder base64UrlEncoder = Base64.getUrlEncoder().withoutPadding();
    private final Base64.Decoder base64UrlDecoder = Base64.getUrlDecoder();

    public JwtAuthService(
            JdbcTemplate jdbcTemplate,
            @Value("${app.auth.jwt.secret:}") String jwtSecret,
            @Value("${app.auth.jwt.access-token-ttl-minutes:10080}") long accessTokenTtlMinutes
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper();
        String secret = Optional.ofNullable(jwtSecret)
                .filter(value -> !value.isBlank())
                .orElse(LOCAL_DEV_SECRET);
        this.secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        this.accessTokenTtl = Duration.ofMinutes(Math.max(1, accessTokenTtlMinutes));
    }

    public String createAccessToken(long userId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(accessTokenTtl);
        String encodedHeader = base64UrlJson(Map.of(
                "alg", "HS256",
                "typ", "JWT"
        ));
        String encodedPayload = base64UrlJson(Map.of(
                "sub", Long.toString(userId),
                "jti", UUID.randomUUID().toString(),
                "iat", now.getEpochSecond(),
                "exp", expiresAt.getEpochSecond()
        ));
        String signingInput = encodedHeader + "." + encodedPayload;
        return signingInput + "." + base64UrlEncoder.encodeToString(hmac(signingInput));
    }

    public long requireUserId(String authorizationHeader) {
        JwtClaims claims = parseAndValidate(extractToken(authorizationHeader));
        if (isRevoked(claims.jti())) {
            throw DomainException.unauthorized("AUTH_TOKEN_REVOKED", "로그아웃된 인증 토큰입니다.");
        }
        return activeUserId(claims.userId());
    }

    public Optional<Long> optionalUserId(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(requireUserId(authorizationHeader));
    }

    public void revoke(String authorizationHeader) {
        JwtClaims claims = parseAndValidate(extractToken(authorizationHeader));
        cleanupExpiredRevocations();
        try {
            jdbcTemplate.update("""
                    insert into auth_token_revocation (token_jti, user_id, expires_at)
                    values (?, ?, ?)
                    """, claims.jti(), claims.userId(), localExpiry(claims.expiresAtEpochSecond()));
        } catch (DuplicateKeyException ignored) {
            // The token is already revoked.
        }
    }

    private JwtClaims parseAndValidate(String token) {
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw DomainException.unauthorized("AUTH_TOKEN_INVALID", "인증 토큰 형식이 올바르지 않습니다.");
        }
        String signingInput = parts[0] + "." + parts[1];
        if (!constantTimeEquals(parts[2], base64UrlEncoder.encodeToString(hmac(signingInput)))) {
            throw DomainException.unauthorized("AUTH_TOKEN_INVALID", "인증 토큰 서명이 올바르지 않습니다.");
        }

        JsonNode header = json(parts[0]);
        if (!"HS256".equals(header.path("alg").asText())) {
            throw DomainException.unauthorized("AUTH_TOKEN_INVALID", "지원하지 않는 인증 토큰 알고리즘입니다.");
        }

        JsonNode payload = json(parts[1]);
        long userId = parseUserId(payload.path("sub").asText());
        String jti = payload.path("jti").asText();
        long expiresAtEpochSecond = payload.path("exp").asLong(0);
        if (jti.isBlank() || expiresAtEpochSecond <= Instant.now().getEpochSecond()) {
            throw DomainException.unauthorized("AUTH_TOKEN_INVALID", "인증 토큰이 없거나 만료되었습니다.");
        }
        return new JwtClaims(userId, jti, expiresAtEpochSecond);
    }

    private long activeUserId(long userId) {
        return jdbcTemplate.query("""
                        select user_id
                        from user_account
                        where user_id = ?
                          and status = 'ACTIVE'
                        """,
                (rs, rowNum) -> rs.getLong("user_id"),
                userId
        ).stream().findFirst().orElseThrow(() -> DomainException.unauthorized(
                "AUTH_TOKEN_INVALID",
                "인증 토큰이 없거나 만료되었습니다."
        ));
    }

    private boolean isRevoked(String jti) {
        cleanupExpiredRevocations();
        Integer count = jdbcTemplate.queryForObject("""
                        select count(*)
                        from auth_token_revocation
                        where token_jti = ?
                          and expires_at > current_timestamp
                        """,
                Integer.class,
                jti
        );
        return count != null && count > 0;
    }

    private void cleanupExpiredRevocations() {
        jdbcTemplate.update("delete from auth_token_revocation where expires_at <= current_timestamp");
    }

    private LocalDateTime localExpiry(long expiresAtEpochSecond) {
        long remainingSeconds = Math.max(1, expiresAtEpochSecond - Instant.now().getEpochSecond());
        return LocalDateTime.now().plusSeconds(remainingSeconds);
    }

    private String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(TOKEN_PREFIX)) {
            throw DomainException.unauthorized("AUTH_HEADER_REQUIRED", "Authorization Bearer 토큰이 필요합니다.");
        }
        return authorizationHeader.substring(TOKEN_PREFIX.length()).trim();
    }

    private String base64UrlJson(Map<String, Object> values) {
        try {
            return base64UrlEncoder.encodeToString(objectMapper.writeValueAsBytes(values));
        } catch (Exception exception) {
            throw new IllegalStateException("JWT JSON 생성에 실패했습니다.", exception);
        }
    }

    private JsonNode json(String base64Url) {
        try {
            return objectMapper.readTree(base64UrlDecoder.decode(base64Url));
        } catch (Exception exception) {
            throw DomainException.unauthorized("AUTH_TOKEN_INVALID", "인증 토큰 형식이 올바르지 않습니다.");
        }
    }

    private byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKey);
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("JWT 서명 생성에 실패했습니다.", exception);
        }
    }

    private boolean constantTimeEquals(String actual, String expected) {
        return java.security.MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );
    }

    private long parseUserId(String subject) {
        try {
            long userId = Long.parseLong(subject);
            if (userId <= 0) {
                throw new NumberFormatException("userId must be positive");
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw DomainException.unauthorized("AUTH_TOKEN_INVALID", "인증 토큰 사용자 정보가 올바르지 않습니다.");
        }
    }

    private record JwtClaims(long userId, String jti, long expiresAtEpochSecond) {
    }
}
