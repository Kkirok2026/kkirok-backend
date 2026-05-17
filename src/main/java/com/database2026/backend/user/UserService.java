package com.database2026.backend.user;

import com.database2026.backend.common.DomainException;
import com.database2026.backend.user.UserDtos.HealthProfileResponse;
import com.database2026.backend.user.UserDtos.MeResponse;
import com.database2026.backend.user.UserDtos.ProfileUpdateRequest;
import com.database2026.backend.user.UserDtos.StudentVerificationResponse;
import com.database2026.backend.user.UserDtos.UserAllergyAddRequest;
import com.database2026.backend.user.UserDtos.UserAllergyItem;
import com.database2026.backend.user.UserDtos.UserAllergyListResponse;
import com.database2026.backend.user.UserDtos.UniversityResponse;
import com.database2026.backend.support.SqlSupport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final JdbcTemplate jdbcTemplate;
    private final SqlSupport sqlSupport;

    public UserService(JdbcTemplate jdbcTemplate, SqlSupport sqlSupport) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlSupport = sqlSupport;
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

    public UserAllergyListResponse allergies(long userId) {
        return new UserAllergyListResponse(jdbcTemplate.query("""
                        select a.allergy_id,
                               a.allergy_type,
                               coalesce(a.food_id, a.ingredient_id) as target_id,
                               a.allergy_name,
                               a.reaction_note
                        from user_allergy a
                        where a.user_id = ?
                        order by a.allergy_type, a.allergy_name
                        """,
                (rs, rowNum) -> new UserAllergyItem(
                        rs.getString("allergy_type"),
                        rs.getLong("allergy_id"),
                        rs.getObject("target_id", Long.class),
                        rs.getString("allergy_name"),
                        rs.getString("reaction_note")
                ),
                userId
        ));
    }

    @Transactional
    public UserAllergyListResponse addAllergy(long userId, UserAllergyAddRequest request) {
        AllergyTarget target = allergyTarget(request);
        String note = normalizeNote(request.reactionNote());
        try {
            jdbcTemplate.update("""
                    insert into user_allergy (
                        user_id, allergy_type, food_id, ingredient_id, allergy_name, normalized_allergy_name, reaction_note
                    )
                    values (?, ?, ?, ?, ?, ?, ?)
                    """, userId, target.allergyType(), target.foodId(), target.ingredientId(), target.name(), normalize(target.name()), note);
        } catch (DuplicateKeyException exception) {
            jdbcTemplate.update("""
                    update user_allergy
                    set food_id = ?,
                        ingredient_id = ?,
                        allergy_name = ?,
                        reaction_note = ?
                    where user_id = ?
                      and allergy_type = ?
                      and normalized_allergy_name = ?
                    """, target.foodId(), target.ingredientId(), target.name(), note, userId, target.allergyType(), normalize(target.name()));
        }
        return allergies(userId);
    }

    @Transactional
    public UserAllergyListResponse deleteAllergy(long userId, long allergyId) {
        jdbcTemplate.update("""
                delete from user_allergy
                where user_id = ?
                  and allergy_id = ?
                """, userId, allergyId);
        return allergies(userId);
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

    private AllergyTarget allergyTarget(UserAllergyAddRequest request) {
        String type = normalizeAllergyType(request.allergyType());
        if ("FOOD".equals(type)) {
            long foodId = firstNonNull(request.targetId(), request.foodId(), "FOOD_ID_REQUIRED", "FOOD 알레르기는 targetId 또는 foodId가 필요합니다.");
            FoodLookup food = foodLookup(foodId);
            return new AllergyTarget(type, food.foodId(), null, food.foodName());
        }

        Long ingredientId = request.targetId() != null ? request.targetId() : request.ingredientId();
        IngredientLookup ingredient = ingredientId == null
                ? upsertIngredient(requiredTrim(request.ingredientName(), "INGREDIENT_NAME_REQUIRED", "INGREDIENT 알레르기는 targetId, ingredientId, ingredientName 중 하나가 필요합니다."))
                : ingredientLookup(ingredientId);
        return new AllergyTarget(type, null, ingredient.ingredientId(), ingredient.ingredientName());
    }

    private String normalizeAllergyType(String allergyType) {
        String normalized = allergyType == null ? "" : allergyType.trim().toUpperCase(Locale.ROOT);
        if (!List.of("FOOD", "INGREDIENT").contains(normalized)) {
            throw DomainException.badRequest("ALLERGY_TYPE_INVALID", "allergyType은 FOOD 또는 INGREDIENT여야 합니다.");
        }
        return normalized;
    }

    private long firstNonNull(Long primary, Long secondary, String code, String message) {
        Long value = primary != null ? primary : secondary;
        if (value == null) {
            throw DomainException.badRequest(code, message);
        }
        return value;
    }

    private FoodLookup foodLookup(long foodId) {
        return jdbcTemplate.query("""
                        select food_id, food_name
                        from food
                        where food_id = ?
                        """,
                (rs, rowNum) -> new FoodLookup(rs.getLong("food_id"), rs.getString("food_name")),
                foodId
        ).stream().findFirst().orElseThrow(() -> DomainException.notFound("FOOD_NOT_FOUND", "음식을 찾을 수 없습니다."));
    }

    private IngredientLookup ingredientLookup(long ingredientId) {
        return jdbcTemplate.query("""
                        select ingredient_id, ingredient_name
                        from ingredient
                        where ingredient_id = ?
                        """,
                (rs, rowNum) -> new IngredientLookup(rs.getLong("ingredient_id"), rs.getString("ingredient_name")),
                ingredientId
        ).stream().findFirst().orElseThrow(() -> DomainException.notFound("INGREDIENT_NOT_FOUND", "원재료를 찾을 수 없습니다."));
    }

    private IngredientLookup upsertIngredient(String ingredientName) {
        String normalizedName = normalize(ingredientName);
        Optional<IngredientLookup> existing = jdbcTemplate.query("""
                        select ingredient_id, ingredient_name
                        from ingredient
                        where normalized_name = ?
                        """,
                (rs, rowNum) -> new IngredientLookup(rs.getLong("ingredient_id"), rs.getString("ingredient_name")),
                normalizedName
        ).stream().findFirst();
        if (existing.isPresent()) {
            return existing.get();
        }
        long ingredientId = sqlSupport.insert("""
                insert into ingredient (source_name, ingredient_name, normalized_name)
                values ('USER_INPUT', ?, ?)
                """, ingredientName, normalizedName);
        insertIngredientAlias(ingredientId, ingredientName);
        return new IngredientLookup(ingredientId, ingredientName);
    }

    private void insertIngredientAlias(long ingredientId, String ingredientName) {
        try {
            jdbcTemplate.update("""
                    insert into ingredient_alias (ingredient_id, alias_name, normalized_alias, alias_type)
                    values (?, ?, ?, 'USER_INPUT')
                    """, ingredientId, ingredientName, normalize(ingredientName));
        } catch (DuplicateKeyException ignored) {
            // Existing aliases are stable search data.
        }
    }

    private String requiredTrim(String value, String code, String message) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isBlank()) {
            throw DomainException.badRequest(code, message);
        }
        return trimmed;
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

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_\\-()\\[\\]{}]", "");
    }

    private record AllergyTarget(String allergyType, Long foodId, Long ingredientId, String name) {
    }

    private record FoodLookup(Long foodId, String foodName) {
    }

    private record IngredientLookup(Long ingredientId, String ingredientName) {
    }
}
