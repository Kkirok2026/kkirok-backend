package com.database2026.backend.user;

import com.database2026.backend.common.DomainException;
import com.database2026.backend.user.UserDtos.HealthProfileResponse;
import com.database2026.backend.user.UserDtos.MeResponse;
import com.database2026.backend.user.UserDtos.ProfileUpdateRequest;
import com.database2026.backend.user.UserDtos.StudentVerificationResponse;
import com.database2026.backend.user.UserDtos.UniversityResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final JdbcTemplate jdbcTemplate;

    public UserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public MeResponse me(long userId) {
        return jdbcTemplate.query("""
                        select u.user_id,
                               u.email,
                               u.name,
                               univ.university_id,
                               univ.university_code,
                               univ.university_name,
                               p.gender,
                               p.height_cm,
                               p.weight_kg,
                               p.bmi,
                               sv.student_email,
                               sv.status as verification_status
                        from user_account u
                        join universities univ on univ.university_id = u.primary_university_id
                        join user_health_profile p on p.user_id = u.user_id
                        left join student_verifications sv on sv.user_id = u.user_id and sv.university_id = univ.university_id
                        where u.user_id = ?
                        """,
                (rs, rowNum) -> new MeResponse(
                        rs.getLong("user_id"),
                        rs.getString("email"),
                        rs.getString("name"),
                        new UniversityResponse(
                                rs.getLong("university_id"),
                                rs.getString("university_code"),
                                rs.getString("university_name")
                        ),
                        new HealthProfileResponse(
                                rs.getString("gender"),
                                rs.getBigDecimal("height_cm"),
                                rs.getBigDecimal("weight_kg"),
                                rs.getBigDecimal("bmi")
                        ),
                        new StudentVerificationResponse(
                                rs.getString("student_email"),
                                rs.getString("verification_status")
                        )
                ),
                userId
        ).stream().findFirst().orElseThrow(() -> DomainException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    @Transactional
    public HealthProfileResponse updateProfile(long userId, ProfileUpdateRequest request) {
        validateGender(request.gender());
        BigDecimal bmi = calculateBmi(request.heightCm(), request.weightKg());
        int updated = jdbcTemplate.update("""
                update user_health_profile
                set gender = ?,
                    height_cm = ?,
                    weight_kg = ?,
                    bmi = ?,
                    updated_at = current_timestamp
                where user_id = ?
                """, request.gender(), request.heightCm(), request.weightKg(), bmi, userId);
        if (updated == 0) {
            throw DomainException.notFound("USER_PROFILE_NOT_FOUND", "사용자 프로필을 찾을 수 없습니다.");
        }
        return new HealthProfileResponse(request.gender(), request.heightCm(), request.weightKg(), bmi);
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
}
