package com.database2026.backend.auth;

import com.database2026.backend.auth.AuthDtos.AuthResponse;
import com.database2026.backend.auth.AuthDtos.LoginRequest;
import com.database2026.backend.auth.AuthDtos.SignupRequest;
import com.database2026.backend.common.DomainException;
import com.database2026.backend.support.SqlSupport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final JdbcTemplate jdbcTemplate;
    private final SqlSupport sqlSupport;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService authSessionService;

    public AuthService(
            JdbcTemplate jdbcTemplate,
            SqlSupport sqlSupport,
            PasswordEncoder passwordEncoder,
            AuthSessionService authSessionService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlSupport = sqlSupport;
        this.passwordEncoder = passwordEncoder;
        this.authSessionService = authSessionService;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        validateGender(request.gender());
        validateUniversityEmail(request.universityId(), request.email());
        BigDecimal bmi = calculateBmi(request.heightCm(), request.weightKg());

        long userId;
        try {
            userId = sqlSupport.insert("""
                    insert into user_account (primary_university_id, email, password_hash, name)
                    values (?, ?, ?, ?)
                    """,
                    request.universityId(),
                    normalizeEmail(request.email()),
                    passwordEncoder.encode(request.password()),
                    request.name()
            );
        } catch (DuplicateKeyException exception) {
            throw DomainException.conflict("EMAIL_ALREADY_EXISTS", "이미 가입된 이메일입니다.");
        }

        sqlSupport.insert("""
                insert into user_health_profile (user_id, height_cm, weight_kg, gender, bmi)
                values (?, ?, ?, ?, ?)
                """, userId, request.heightCm(), request.weightKg(), request.gender(), bmi);

        sqlSupport.insert("""
                insert into student_verifications (user_id, university_id, student_email, status, verified_at)
                values (?, ?, ?, ?, ?)
                """, userId, request.universityId(), normalizeEmail(request.email()), "DOMAIN_VERIFIED", LocalDateTime.now());

        String token = authSessionService.createSession(userId);
        return new AuthResponse(userId, request.universityId(), token, bmi);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        UserLoginRow user = jdbcTemplate.query("""
                        select u.user_id, u.primary_university_id, u.password_hash, p.bmi
                        from user_account u
                        join user_health_profile p on p.user_id = u.user_id
                        where u.email = ? and u.status = 'ACTIVE'
                        """,
                (rs, rowNum) -> new UserLoginRow(
                        rs.getLong("user_id"),
                        rs.getLong("primary_university_id"),
                        rs.getString("password_hash"),
                        rs.getBigDecimal("bmi")
                ),
                normalizeEmail(request.email())
        ).stream().findFirst().orElseThrow(() -> DomainException.unauthorized(
                "LOGIN_FAILED",
                "이메일 또는 비밀번호가 올바르지 않습니다."
        ));

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw DomainException.unauthorized("LOGIN_FAILED", "이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        jdbcTemplate.update("update user_account set last_login_at = current_timestamp where user_id = ?", user.userId());
        String token = authSessionService.createSession(user.userId());
        return new AuthResponse(user.userId(), user.universityId(), token, user.bmi());
    }

    private void validateUniversityEmail(Long universityId, String email) {
        String domain = extractEmailDomain(email);
        boolean valid = jdbcTemplate.queryForObject("""
                        select count(*)
                        from university_email_domains
                        where university_id = ?
                          and lower(email_domain) = ?
                          and is_active = true
                        """,
                Integer.class,
                universityId,
                domain
        ) > 0;
        if (!valid) {
            throw DomainException.badRequest(
                    "UNIVERSITY_EMAIL_DOMAIN_INVALID",
                    "해당 학교에서 허용한 이메일 도메인이 아닙니다."
            );
        }
    }

    private String extractEmailDomain(String email) {
        String normalizedEmail = normalizeEmail(email);
        int atIndex = normalizedEmail.lastIndexOf('@');
        if (atIndex < 0 || atIndex == normalizedEmail.length() - 1) {
            throw DomainException.badRequest("EMAIL_INVALID", "이메일 형식이 올바르지 않습니다.");
        }
        return normalizedEmail.substring(atIndex + 1);
    }

    private String normalizeEmail(String email) {
        return Optional.ofNullable(email)
                .orElse("")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private BigDecimal calculateBmi(BigDecimal heightCm, BigDecimal weightKg) {
        BigDecimal heightM = heightCm.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        return weightKg.divide(heightM.multiply(heightM), 2, RoundingMode.HALF_UP);
    }

    private void validateGender(String gender) {
        if (!"MALE".equals(gender) && !"FEMALE".equals(gender) && !"OTHER".equals(gender)) {
            throw DomainException.badRequest("GENDER_INVALID", "gender는 MALE, FEMALE, OTHER 중 하나여야 합니다.");
        }
    }

    private record UserLoginRow(Long userId, Long universityId, String passwordHash, BigDecimal bmi) {
    }
}
