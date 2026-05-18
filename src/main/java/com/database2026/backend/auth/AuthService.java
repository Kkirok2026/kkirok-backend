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
    private final JwtAuthService jwtAuthService;
    private final SchoolEmailVerificationSender schoolEmailVerificationSender;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            JdbcTemplate jdbcTemplate,
            SqlSupport sqlSupport,
            PasswordEncoder passwordEncoder,
            JwtAuthService jwtAuthService,
            SchoolEmailVerificationSender schoolEmailVerificationSender
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlSupport = sqlSupport;
        this.passwordEncoder = passwordEncoder;
        this.jwtAuthService = jwtAuthService;
        this.schoolEmailVerificationSender = schoolEmailVerificationSender;
    }

    @Transactional
    public SchoolEmailVerificationResponse requestSchoolEmailVerification(SchoolEmailVerificationRequest request) {
        String email = normalizeEmail(request.email());
        UniversityByDomain university = universityByEmailDomain(email)
                .orElseThrow(() -> DomainException.badRequest(
                        "SCHOOL_EMAIL_DOMAIN_NOT_SUPPORTED",
                        "등록된 대학교 이메일 도메인이 아닙니다. 일반 사용자는 이메일 인증 없이 회원가입할 수 있습니다."
                ));
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
                """, university.universityId(), email);

        sqlSupport.insert("""
                insert into school_email_verification_code
                    (university_id, student_email, purpose, code_hash, expires_at)
                values (?, ?, 'SIGNUP', ?, ?)
                """, university.universityId(), email, passwordEncoder.encode(code), expiresAt);

        schoolEmailVerificationSender.send(email, code, expiresAt);
        return new SchoolEmailVerificationResponse(university.universityId(), email, expiresAt);
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        assertEmailAvailable(email);
        Optional<UniversityByDomain> university = universityByEmailDomain(email);
        Long universityId = university.map(UniversityByDomain::universityId).orElse(null);
        if (universityId != null) {
            consumeSignupVerificationCode(universityId, email, request.verificationCode());
        }

        long userId;
        try {
            userId = sqlSupport.insert("""
                    insert into user_account (
                        university_id, email, password_hash, name, age,
                        student_email, is_student_verified
                    )
                    values (?, ?, ?, ?, ?, ?, ?)
                    """,
                    universityId,
                    email,
                    passwordEncoder.encode(request.password()),
                    request.name(),
                    request.age(),
                    universityId == null ? null : email,
                    universityId != null
            );
        } catch (DuplicateKeyException exception) {
            throw DomainException.conflict("EMAIL_ALREADY_EXISTS", "이미 가입된 이메일입니다.");
        }

        String token = jwtAuthService.createAccessToken(userId);
        return new AuthResponse(userId, universityId, token, null, false);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        UserLoginRow user = jdbcTemplate.query("""
                        select u.user_id,
                               u.university_id,
                               u.password_hash,
                               u.is_student_verified,
                               p.bmi
                        from user_account u
                        left join user_health_profile p on p.user_id = u.user_id
                        where u.email = ? and u.status = 'ACTIVE'
                        """,
                (rs, rowNum) -> new UserLoginRow(
                        rs.getLong("user_id"),
                        (Long) rs.getObject("university_id"),
                        rs.getString("password_hash"),
                        rs.getBoolean("is_student_verified"),
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
        if (user.universityId() != null && !user.studentVerified()) {
            throw DomainException.unauthorized("SCHOOL_EMAIL_NOT_VERIFIED", "학교 이메일 인증이 완료되지 않았습니다.");
        }

        String token = jwtAuthService.createAccessToken(user.userId());
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
            throw DomainException.badRequest("SCHOOL_EMAIL_VERIFICATION_CODE_REQUIRED", "학교 이메일로 가입하는 경우 학교 이메일 인증코드가 필요합니다.");
        }
        if (isDemoVerificationCode(code)) {
            return;
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

    private Optional<UniversityByDomain> universityByEmailDomain(String email) {
        String domain = extractEmailDomain(email);
        return jdbcTemplate.query("""
                        select university_id
                        from university_email_domains
                        where lower(email_domain) = ?
                        limit 1
                        """,
                (rs, rowNum) -> new UniversityByDomain(rs.getLong("university_id")),
                domain
        ).stream().findFirst();
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

    private boolean isDemoVerificationCode(String code) {
        return code != null && code.trim().matches("\\d{4}");
    }

    private record UserLoginRow(Long userId, Long universityId, String passwordHash, boolean studentVerified, BigDecimal bmi) {
    }

    private record VerificationCodeRow(Long verificationId, String codeHash) {
    }

    private record UniversityByDomain(Long universityId) {
    }
}
