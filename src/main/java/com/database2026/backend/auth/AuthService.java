package com.database2026.backend.auth;

import com.database2026.backend.auth.AuthDtos.AuthResponse;
import com.database2026.backend.auth.AuthDtos.LoginRequest;
import com.database2026.backend.auth.AuthDtos.SchoolEmailVerificationRequest;
import com.database2026.backend.auth.AuthDtos.SchoolEmailVerificationResponse;
import com.database2026.backend.auth.AuthDtos.SignupRequest;
import com.database2026.backend.common.DomainException;
import com.database2026.backend.support.SqlSupport;
import java.math.BigDecimal;
import java.security.SecureRandom;
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
    private final SchoolEmailVerificationSender schoolEmailVerificationSender;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            JdbcTemplate jdbcTemplate,
            SqlSupport sqlSupport,
            PasswordEncoder passwordEncoder,
            AuthSessionService authSessionService,
            SchoolEmailVerificationSender schoolEmailVerificationSender
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlSupport = sqlSupport;
        this.passwordEncoder = passwordEncoder;
        this.authSessionService = authSessionService;
        this.schoolEmailVerificationSender = schoolEmailVerificationSender;
    }

    @Transactional
    public SchoolEmailVerificationResponse requestSchoolEmailVerification(SchoolEmailVerificationRequest request) {
        String email = normalizeEmail(request.email());
        validateUniversityEmail(request.universityId(), email);
        assertEmailAvailable(email);

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(10);
        String code = generateVerificationCode();

        jdbcTemplate.update("""
                update school_email_verification_code
                set consumed_at = current_timestamp
                where university_id = ?
                  and student_email = ?
                  and purpose = 'SIGNUP'
                  and consumed_at is null
                """, request.universityId(), email);

        long verificationId = sqlSupport.insert("""
                insert into school_email_verification_code
                    (university_id, student_email, purpose, code_hash, expires_at)
                values (?, ?, 'SIGNUP', ?, ?)
                """, request.universityId(), email, passwordEncoder.encode(code), expiresAt);

        schoolEmailVerificationSender.send(email, code, expiresAt);
        return new SchoolEmailVerificationResponse(verificationId, request.universityId(), email, expiresAt);
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        assertEmailAvailable(email);
        if (request.universityId() != null) {
            validateUniversityEmail(request.universityId(), email);
            consumeSignupVerificationCode(request.universityId(), email, request.verificationCode());
        }

        long userId;
        try {
            userId = sqlSupport.insert("""
                    insert into user_account (primary_university_id, email, password_hash, name)
                    values (?, ?, ?, ?)
                    """,
                    request.universityId(),
                    email,
                    passwordEncoder.encode(request.password()),
                    request.name()
            );
        } catch (DuplicateKeyException exception) {
            throw DomainException.conflict("EMAIL_ALREADY_EXISTS", "이미 가입된 이메일입니다.");
        }

        if (request.universityId() != null) {
            sqlSupport.update("""
                    insert into student_verifications (user_id, university_id, student_email, status, verified_at)
                    values (?, ?, ?, ?, ?)
                    """, userId, request.universityId(), email, "VERIFIED", LocalDateTime.now());
        }

        String token = authSessionService.createSession(userId);
        return new AuthResponse(userId, request.universityId(), token, null, false);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        UserLoginRow user = jdbcTemplate.query("""
                        select u.user_id, u.primary_university_id, u.password_hash, p.bmi
                        from user_account u
                        left join user_health_profile p on p.user_id = u.user_id
                        where u.email = ? and u.status = 'ACTIVE'
                        """,
                (rs, rowNum) -> new UserLoginRow(
                        rs.getLong("user_id"),
                        (Long) rs.getObject("primary_university_id"),
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
        if (user.universityId() != null) {
            assertSchoolVerified(user.userId(), user.universityId());
        }

        jdbcTemplate.update("update user_account set last_login_at = current_timestamp where user_id = ?", user.userId());
        String token = authSessionService.createSession(user.userId());
        return new AuthResponse(user.userId(), user.universityId(), token, user.bmi(), user.bmi() != null);
    }

    private void assertEmailAvailable(String email) {
        boolean exists = jdbcTemplate.queryForObject("""
                        select count(*)
                        from user_account
                        where email = ?
                        """,
                Integer.class,
                email
        ) > 0;
        if (exists) {
            throw DomainException.conflict("EMAIL_ALREADY_EXISTS", "이미 가입된 이메일입니다.");
        }
    }

    private void consumeSignupVerificationCode(Long universityId, String email, String code) {
        if (code == null || code.isBlank()) {
            throw DomainException.badRequest("SCHOOL_EMAIL_VERIFICATION_CODE_REQUIRED", "학교를 선택한 경우 학교 이메일 인증코드가 필요합니다.");
        }
        VerificationCodeRow verification = jdbcTemplate.query("""
                        select verification_id, code_hash
                        from school_email_verification_code
                        where university_id = ?
                          and student_email = ?
                          and purpose = 'SIGNUP'
                          and consumed_at is null
                          and expires_at > current_timestamp
                        order by verification_id desc
                        """,
                (rs, rowNum) -> new VerificationCodeRow(
                        rs.getLong("verification_id"),
                        rs.getString("code_hash")
                ),
                universityId,
                email
        ).stream().filter(row -> passwordEncoder.matches(code, row.codeHash()))
                .findFirst()
                .orElseThrow(() -> DomainException.badRequest(
                        "SCHOOL_EMAIL_VERIFICATION_INVALID",
                        "학교 이메일 인증코드가 올바르지 않거나 만료되었습니다."
                ));

        jdbcTemplate.update("""
                update school_email_verification_code
                set consumed_at = current_timestamp
                where verification_id = ?
                """, verification.verificationId());
    }

    private void assertSchoolVerified(Long userId, Long universityId) {
        boolean verified = jdbcTemplate.queryForObject("""
                        select count(*)
                        from student_verifications
                        where user_id = ?
                          and university_id = ?
                          and status = 'VERIFIED'
                        """,
                Integer.class,
                userId,
                universityId
        ) > 0;
        if (!verified) {
            throw DomainException.unauthorized("SCHOOL_EMAIL_NOT_VERIFIED", "학교 이메일 인증이 완료되지 않았습니다.");
        }
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

    private String generateVerificationCode() {
        return "%06d".formatted(secureRandom.nextInt(1_000_000));
    }

    private record UserLoginRow(Long userId, Long universityId, String passwordHash, BigDecimal bmi) {
    }

    private record VerificationCodeRow(Long verificationId, String codeHash) {
    }
}
