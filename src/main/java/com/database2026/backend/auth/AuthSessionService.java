package com.database2026.backend.auth;

import com.database2026.backend.common.DomainException;
import com.database2026.backend.support.SqlSupport;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuthSessionService {

    private final JdbcTemplate jdbcTemplate;
    private final SqlSupport sqlSupport;

    public AuthSessionService(JdbcTemplate jdbcTemplate, SqlSupport sqlSupport) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlSupport = sqlSupport;
    }

    public String createSession(long userId) {
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        sqlSupport.update("""
                insert into auth_sessions (user_id, access_token, expires_at)
                values (?, ?, ?)
                """, userId, token, LocalDateTime.now().plusDays(7));
        return token;
    }

    public long requireUserId(String authorizationHeader) {
        String token = extractToken(authorizationHeader);
        Optional<Long> userId = jdbcTemplate.query("""
                        select user_id
                        from auth_sessions
                        where access_token = ?
                          and revoked_at is null
                          and expires_at > current_timestamp
                        """,
                (rs, rowNum) -> rs.getLong("user_id"),
                token
        ).stream().findFirst();
        return userId.orElseThrow(() -> DomainException.unauthorized(
                "AUTH_TOKEN_INVALID",
                "인증 토큰이 없거나 만료되었습니다."
        ));
    }

    public Optional<Long> optionalUserId(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(requireUserId(authorizationHeader));
    }

    public void revoke(String authorizationHeader) {
        String token = extractToken(authorizationHeader);
        jdbcTemplate.update("""
                update auth_sessions
                set revoked_at = current_timestamp
                where access_token = ? and revoked_at is null
                """, token);
    }

    private String extractToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw DomainException.unauthorized("AUTH_HEADER_REQUIRED", "Authorization Bearer 토큰이 필요합니다.");
        }
        return authorizationHeader.substring("Bearer ".length()).trim();
    }
}
