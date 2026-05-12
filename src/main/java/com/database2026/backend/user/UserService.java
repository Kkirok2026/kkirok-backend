package com.database2026.backend.user;

import com.database2026.backend.common.DomainException;
import com.database2026.backend.user.UserDtos.FoodAllergyAddRequest;
import com.database2026.backend.user.UserDtos.FoodAllergyItem;
import com.database2026.backend.user.UserDtos.FoodAllergyListResponse;
import com.database2026.backend.user.UserDtos.HealthProfileResponse;
import com.database2026.backend.user.UserDtos.MeResponse;
import com.database2026.backend.user.UserDtos.ProfileUpdateRequest;
import com.database2026.backend.user.UserDtos.StudentVerificationResponse;
import com.database2026.backend.user.UserDtos.UniversityResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.dao.DuplicateKeyException;
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
                        left join universities univ on univ.university_id = u.primary_university_id
                        left join user_health_profile p on p.user_id = u.user_id
                        left join student_verifications sv on sv.user_id = u.user_id and sv.university_id = univ.university_id
                        where u.user_id = ?
                        """,
                (rs, rowNum) -> new MeResponse(
                        rs.getLong("user_id"),
                        rs.getString("email"),
                        rs.getString("name"),
                        universityResponse(rs.getObject("university_id", Long.class), rs.getString("university_code"), rs.getString("university_name")),
                        healthProfileResponse(rs.getString("gender"), rs.getBigDecimal("height_cm"), rs.getBigDecimal("weight_kg"), rs.getBigDecimal("bmi")),
                        rs.getBigDecimal("bmi") != null,
                        new StudentVerificationResponse(
                                rs.getString("student_email"),
                                rs.getString("verification_status")
                        )
                ),
                userId
        ).stream().findFirst().orElseThrow(() -> DomainException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
    }

    private UniversityResponse universityResponse(Long universityId, String universityCode, String universityName) {
        if (universityId == null) {
            return null;
        }
        return new UniversityResponse(universityId, universityCode, universityName);
    }

    private HealthProfileResponse healthProfileResponse(String gender, BigDecimal heightCm, BigDecimal weightKg, BigDecimal bmi) {
        if (gender == null) {
            return null;
        }
        return new HealthProfileResponse(gender, heightCm, weightKg, bmi);
    }

    @Transactional
    public HealthProfileResponse updateProfile(long userId, ProfileUpdateRequest request) {
        validateGender(request.gender());
        BigDecimal bmi = calculateBmi(request.heightCm(), request.weightKg());
        jdbcTemplate.update("""
                insert into user_health_profile (user_id, height_cm, weight_kg, gender, bmi)
                values (?, ?, ?, ?, ?)
                on duplicate key update height_cm = values(height_cm),
                                        weight_kg = values(weight_kg),
                                        gender = values(gender),
                                        bmi = values(bmi),
                                        updated_at = current_timestamp
                """, userId, request.heightCm(), request.weightKg(), request.gender(), bmi);
        return new HealthProfileResponse(request.gender(), request.heightCm(), request.weightKg(), bmi);
    }

    public FoodAllergyListResponse foodAllergies(long userId) {
        return new FoodAllergyListResponse(jdbcTemplate.query("""
                        select a.allergy_id,
                               f.food_id,
                               f.food_name,
                               f.source_category,
                               a.reaction_note
                        from user_food_allergy a
                        join food f on f.food_id = a.food_id
                        where a.user_id = ?
                        order by f.food_name
                        """,
                (rs, rowNum) -> new FoodAllergyItem(
                        rs.getLong("allergy_id"),
                        rs.getLong("food_id"),
                        rs.getString("food_name"),
                        rs.getString("source_category"),
                        rs.getString("reaction_note")
                ),
                userId
        ));
    }

    @Transactional
    public FoodAllergyListResponse addFoodAllergy(long userId, FoodAllergyAddRequest request) {
        assertAllergyFoodExists(request.foodId());
        try {
            jdbcTemplate.update("""
                    insert into user_food_allergy (user_id, food_id, reaction_note)
                    values (?, ?, ?)
                    """, userId, request.foodId(), normalizeNote(request.reactionNote()));
        } catch (DuplicateKeyException exception) {
            jdbcTemplate.update("""
                    update user_food_allergy
                    set reaction_note = ?
                    where user_id = ?
                      and food_id = ?
                    """, normalizeNote(request.reactionNote()), userId, request.foodId());
        }
        return foodAllergies(userId);
    }

    @Transactional
    public FoodAllergyListResponse deleteFoodAllergy(long userId, long foodId) {
        jdbcTemplate.update("""
                delete from user_food_allergy
                where user_id = ?
                  and food_id = ?
                """, userId, foodId);
        return foodAllergies(userId);
    }

    @Transactional
    public void deleteMe(long userId) {
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
    }

    private void assertAllergyFoodExists(long foodId) {
        boolean exists = jdbcTemplate.queryForObject("""
                        select count(*)
                        from food
                        where food_id = ?
                          and source_name = 'MFDS_INTEGRATED'
                        """,
                Integer.class,
                foodId
        ) > 0;
        if (!exists) {
            throw DomainException.notFound("FOOD_NOT_FOUND", "음식을 찾을 수 없습니다.");
        }
    }

    private String normalizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String normalized = note.trim();
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
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
