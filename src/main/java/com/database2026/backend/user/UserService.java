package com.database2026.backend.user;

import com.database2026.backend.common.DomainException;
import com.database2026.backend.user.UserDtos.HealthProfileResponse;
import com.database2026.backend.user.UserDtos.MeResponse;
import com.database2026.backend.user.UserDtos.ProfileUpdateRequest;
import com.database2026.backend.user.UserDtos.StudentVerificationResponse;
import com.database2026.backend.user.UserDtos.UniversityResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
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
                               u.age,
                               univ.university_id,
                               univ.university_name,
                               p.gender,
                               p.height_cm,
                               p.weight_kg,
                               p.target_weight_kg,
                               p.target_period_value,
                               p.target_period_unit,
                               p.bmi,
                               p.activity_level,
                               u.student_email,
                               case
                                   when u.is_student_verified = true then 'VERIFIED'
                                   else null
                               end as verification_status
                        from user_account u
                        left join universities univ on univ.university_id = u.university_id
                        left join user_health_profile p on p.user_id = u.user_id
                        where u.user_id = ?
                        """,
                (rs, rowNum) -> new MeResponse(
                        rs.getLong("user_id"),
                        rs.getString("email"),
                        rs.getString("name"),
                        (Integer) rs.getObject("age"),
                        universityResponse(rs.getObject("university_id", Long.class), rs.getString("university_name")),
                        healthProfileResponse(
                                rs.getString("gender"),
                                rs.getBigDecimal("height_cm"),
                                rs.getBigDecimal("weight_kg"),
                                rs.getBigDecimal("target_weight_kg"),
                                rs.getObject("target_period_value", Integer.class),
                                rs.getString("target_period_unit"),
                                rs.getBigDecimal("bmi"),
                                rs.getString("activity_level")
                        ),
                        rs.getBigDecimal("bmi") != null,
                        new StudentVerificationResponse(
                                rs.getString("student_email"),
                                rs.getString("verification_status")
                        )
                ),
                userId
        ).stream().findFirst().orElseThrow(() -> DomainException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    private UniversityResponse universityResponse(Long universityId, String universityName) {
        if (universityId == null) {
            return null;
        }
        return new UniversityResponse(universityId, universityName);
    }

    private HealthProfileResponse healthProfileResponse(
            String gender,
            BigDecimal heightCm,
            BigDecimal weightKg,
            BigDecimal targetWeightKg,
            Integer targetPeriodValue,
            String targetPeriodUnit,
            BigDecimal bmi,
            String activityLevel
    ) {
        if (gender == null) {
            return null;
        }
        return new HealthProfileResponse(
                gender,
                heightCm,
                weightKg,
                targetWeightKg,
                targetPeriodValue,
                targetPeriodUnit,
                bmi,
                activityLevel
        );
    }

    @Transactional
    public HealthProfileResponse updateProfile(long userId, ProfileUpdateRequest request) {
        validateGender(request.gender());
        String activityLevel = normalizeActivityLevel(request.activityLevel());
        String targetPeriodUnit = normalizeTargetPeriodUnit(request.targetPeriodValue(), request.targetPeriodUnit());
        BigDecimal bmi = calculateBmi(request.heightCm(), request.weightKg());
        if (request.age() != null) {
            jdbcTemplate.update("""
                    update user_account
                    set age = ?
                    where user_id = ?
                    """, request.age(), userId);
        }
        jdbcTemplate.update("""
                insert into user_health_profile (
                    user_id, height_cm, weight_kg, target_weight_kg, target_period_value,
                    target_period_unit, gender, bmi, activity_level
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on duplicate key update height_cm = values(height_cm),
                                        weight_kg = values(weight_kg),
                                        target_weight_kg = values(target_weight_kg),
                                        target_period_value = values(target_period_value),
                                        target_period_unit = values(target_period_unit),
                                        gender = values(gender),
                                        bmi = values(bmi),
                                        activity_level = values(activity_level)
                """,
                userId,
                request.heightCm(),
                request.weightKg(),
                request.targetWeightKg(),
                request.targetPeriodValue(),
                targetPeriodUnit,
                request.gender(),
                bmi,
                activityLevel
        );
        return new HealthProfileResponse(
                request.gender(),
                request.heightCm(),
                request.weightKg(),
                request.targetWeightKg(),
                request.targetPeriodValue(),
                targetPeriodUnit,
                bmi,
                activityLevel
        );
    }

    @Transactional
    public void deleteMe(long userId) {
        List<Long> customFoodIds = jdbcTemplate.query("""
                        select food_id
                        from user_custom_food
                        where user_id = ?
                        """,
                (rs, rowNum) -> rs.getLong("food_id"),
                userId
        );
        String email = jdbcTemplate.query("""
                        select email
                        from user_account
                        where user_id = ?
                        """,
                (rs, rowNum) -> rs.getString("email"),
                userId
        ).stream().findFirst().orElseThrow(() -> DomainException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));

        jdbcTemplate.update("""
                delete from school_email_verification_code
                where student_email = ?
                """, email);
        jdbcTemplate.update("""
                delete from user_account
                where user_id = ?
                """, userId);
        for (Long foodId : customFoodIds) {
            jdbcTemplate.update("""
                    delete from food
                    where food_id = ?
                      and source_name = 'USER_CUSTOM'
                    """, foodId);
        }
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

    private String normalizeActivityLevel(String activityLevel) {
        if (activityLevel == null || activityLevel.isBlank()) {
            return "LOW_ACTIVE";
        }
        String normalized = activityLevel.trim().toUpperCase(Locale.ROOT);
        if (!List.of("SEDENTARY", "LOW_ACTIVE", "ACTIVE", "VERY_ACTIVE").contains(normalized)) {
            throw DomainException.badRequest(
                    "ACTIVITY_LEVEL_INVALID",
                    "activityLevel은 SEDENTARY, LOW_ACTIVE, ACTIVE, VERY_ACTIVE 중 하나여야 합니다."
            );
        }
        return normalized;
    }

    private String normalizeTargetPeriodUnit(Integer targetPeriodValue, String targetPeriodUnit) {
        if (targetPeriodValue == null) {
            return null;
        }
        if (targetPeriodUnit == null || targetPeriodUnit.isBlank()) {
            return "MONTH";
        }
        String normalized = targetPeriodUnit.trim().toUpperCase(Locale.ROOT);
        if (!List.of("WEEK", "MONTH").contains(normalized)) {
            throw DomainException.badRequest(
                    "TARGET_PERIOD_UNIT_INVALID",
                    "targetPeriodUnit은 WEEK 또는 MONTH여야 합니다."
            );
        }
        return normalized;
    }
}
