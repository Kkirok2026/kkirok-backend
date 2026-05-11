package com.database2026.backend.meal;

import com.database2026.backend.common.DomainException;
import com.database2026.backend.common.NutrientTotals;
import com.database2026.backend.meal.MealDtos.DailySummaryResponse;
import com.database2026.backend.meal.MealDtos.MealLogCreateRequest;
import com.database2026.backend.meal.MealDtos.MealLogItemRequest;
import com.database2026.backend.meal.MealDtos.MealLogItemResponse;
import com.database2026.backend.meal.MealDtos.MealLogListResponse;
import com.database2026.backend.meal.MealDtos.MealLogResponse;
import com.database2026.backend.meal.MealDtos.NutritionWarning;
import com.database2026.backend.support.SqlSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MealService {

    private final JdbcTemplate jdbcTemplate;
    private final SqlSupport sqlSupport;

    public MealService(JdbcTemplate jdbcTemplate, SqlSupport sqlSupport) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlSupport = sqlSupport;
    }

    @Transactional
    public MealLogResponse create(long userId, MealLogCreateRequest request) {
        long mealTypeId = mealTypeId(request.mealType());
        long mealLogId;
        try {
            mealLogId = sqlSupport.insert("""
                    insert into diet_entry (user_id, meal_type_id, consumed_date, memo)
                    values (?, ?, ?, ?)
                    """, userId, mealTypeId, request.logDate(), request.memo());
        } catch (DuplicateKeyException exception) {
            throw DomainException.conflict("MEAL_LOG_ALREADY_EXISTS", "이미 해당 날짜/끼니 식단 기록이 있습니다.");
        }

        for (MealLogItemRequest item : request.items()) {
            insertItem(mealLogId, item);
        }
        return mealLog(userId, mealLogId);
    }

    @Transactional
    public MealLogResponse addItem(long userId, long mealLogId, MealLogItemRequest request) {
        assertMealLogOwner(userId, mealLogId);
        insertItem(mealLogId, request);
        return mealLog(userId, mealLogId);
    }

    @Transactional
    public MealLogResponse setExcluded(long userId, long mealLogId, long dietItemId, boolean excluded) {
        int updated = jdbcTemplate.update("""
                update diet_entry_item
                set is_excluded = ?
                where diet_item_id = ?
                  and diet_entry_id in (
                      select diet_entry_id
                      from diet_entry
                      where diet_entry_id = ? and user_id = ?
                  )
                """, excluded, dietItemId, mealLogId, userId);
        if (updated == 0) {
            throw DomainException.notFound("MEAL_LOG_ITEM_NOT_FOUND", "식단 항목을 찾을 수 없습니다.");
        }
        return mealLog(userId, mealLogId);
    }

    public MealLogResponse mealLog(long userId, long mealLogId) {
        MealLogHeader header = jdbcTemplate.query("""
                        select d.diet_entry_id,
                               d.consumed_date,
                               mt.meal_type_code,
                               d.memo
                        from diet_entry d
                        join meal_type mt on mt.meal_type_id = d.meal_type_id
                        where d.user_id = ? and d.diet_entry_id = ?
                        """,
                (rs, rowNum) -> new MealLogHeader(
                        rs.getLong("diet_entry_id"),
                        rs.getObject("consumed_date", LocalDate.class),
                        rs.getString("meal_type_code"),
                        rs.getString("memo")
                ),
                userId,
                mealLogId
        ).stream().findFirst().orElseThrow(() -> DomainException.notFound("MEAL_LOG_NOT_FOUND", "식단 기록을 찾을 수 없습니다."));

        return new MealLogResponse(
                header.mealLogId(),
                header.logDate(),
                header.mealType(),
                header.memo(),
                mealLogItems(mealLogId),
                entryTotals(mealLogId)
        );
    }

    public MealLogListResponse listByDate(long userId, LocalDate date) {
        List<Long> ids = jdbcTemplate.query("""
                        select d.diet_entry_id
                        from diet_entry d
                        join meal_type mt on mt.meal_type_id = d.meal_type_id
                        where d.user_id = ? and d.consumed_date = ?
                        order by mt.meal_type_id
                        """,
                (rs, rowNum) -> rs.getLong("diet_entry_id"),
                userId,
                date
        );
        return new MealLogListResponse(ids.stream().map(id -> mealLog(userId, id)).toList());
    }

    public DailySummaryResponse dailySummary(long userId, LocalDate date) {
        NutrientTotals totals = dailyTotals(userId, date);
        List<NutritionWarning> warnings = warnings(userId, totals);
        return new DailySummaryResponse(date, totals, warnings);
    }

    private void insertItem(long mealLogId, MealLogItemRequest item) {
        boolean hasFood = item.foodId() != null;
        boolean hasMenuOption = item.menuOptionId() != null;
        if (hasFood == hasMenuOption) {
            throw DomainException.badRequest(
                    "MEAL_LOG_ITEM_REFERENCE_INVALID",
                    "foodId 또는 menuOptionId 중 정확히 하나만 입력해야 합니다."
            );
        }
        if (hasMenuOption) {
            insertMenuOptionItems(mealLogId, item.menuOptionId());
            return;
        }
        FoodPortion food = foodPortion(item.foodId());
        BigDecimal amountG = Optional.ofNullable(item.amountG()).orElse(food.defaultServingG());
        insertDietItem(mealLogId, food.foodId(), null, food.foodName(), amountG);
    }

    private void insertMenuOptionItems(long mealLogId, long menuOptionId) {
        List<MenuFoodItem> foods = jdbcTemplate.query("""
                        select mi.food_id,
                               mi.raw_item_name,
                               mi.amount_g
                        from cafeteria_menu_item mi
                        where mi.option_id = ?
                          and mi.food_id is not null
                        order by mi.menu_item_id
                        """,
                (rs, rowNum) -> new MenuFoodItem(
                        rs.getLong("food_id"),
                        rs.getString("raw_item_name"),
                        rs.getBigDecimal("amount_g")
                ),
                menuOptionId
        );
        if (foods.isEmpty()) {
            throw DomainException.notFound("MENU_OPTION_NOT_FOUND", "추가할 수 있는 식당 메뉴 옵션을 찾을 수 없습니다.");
        }
        for (MenuFoodItem food : foods) {
            insertDietItem(mealLogId, food.foodId(), menuOptionId, food.rawItemName(), food.amountG());
        }
    }

    private void insertDietItem(long mealLogId, long foodId, Long sourceOptionId, String itemName, BigDecimal amountG) {
        if (amountG == null || amountG.compareTo(BigDecimal.ZERO) <= 0) {
            throw DomainException.badRequest("AMOUNT_INVALID", "amountG는 0보다 커야 합니다.");
        }
        sqlSupport.update("""
                insert into diet_entry_item (diet_entry_id, food_id, source_option_id, item_name_snapshot, amount_g)
                values (?, ?, ?, ?, ?)
                """, mealLogId, foodId, sourceOptionId, itemName, amountG);
    }

    private FoodPortion foodPortion(Long foodId) {
        return jdbcTemplate.query("""
                        select food_id, food_name, default_serving_g
                        from food
                        where food_id = ?
                        """,
                (rs, rowNum) -> new FoodPortion(
                        rs.getLong("food_id"),
                        rs.getString("food_name"),
                        rs.getBigDecimal("default_serving_g")
                ),
                foodId
        ).stream().findFirst().orElseThrow(() -> DomainException.notFound("FOOD_NOT_FOUND", "음식을 찾을 수 없습니다."));
    }

    private List<MealLogItemResponse> mealLogItems(long mealLogId) {
        return jdbcTemplate.query("""
                        select i.diet_item_id,
                               i.food_id,
                               i.source_option_id,
                               i.item_name_snapshot,
                               i.amount_g,
                               i.is_excluded,
                               coalesce(sum(case when n.nutrient_code = 'CALORIES_KCAL' then v.amount_per_100g * i.amount_g / 100 end), 0) as calories_kcal,
                               coalesce(sum(case when n.nutrient_code = 'CARB_G' then v.amount_per_100g * i.amount_g / 100 end), 0) as carb_g,
                               coalesce(sum(case when n.nutrient_code = 'PROTEIN_G' then v.amount_per_100g * i.amount_g / 100 end), 0) as protein_g,
                               coalesce(sum(case when n.nutrient_code = 'FAT_G' then v.amount_per_100g * i.amount_g / 100 end), 0) as fat_g,
                               coalesce(sum(case when n.nutrient_code = 'SUGAR_G' then v.amount_per_100g * i.amount_g / 100 end), 0) as sugar_g,
                               coalesce(sum(case when n.nutrient_code = 'SODIUM_MG' then v.amount_per_100g * i.amount_g / 100 end), 0) as sodium_mg
                        from diet_entry_item i
                        join food_nutrient_value v on v.food_id = i.food_id
                        join nutrient n on n.nutrient_id = v.nutrient_id
                        where i.diet_entry_id = ?
                        group by i.diet_item_id, i.food_id, i.source_option_id, i.item_name_snapshot, i.amount_g, i.is_excluded
                        order by i.diet_item_id
                        """,
                (rs, rowNum) -> new MealLogItemResponse(
                        rs.getLong("diet_item_id"),
                        rs.getLong("food_id"),
                        (Long) rs.getObject("source_option_id"),
                        rs.getString("item_name_snapshot"),
                        rs.getBigDecimal("amount_g"),
                        rs.getBoolean("is_excluded"),
                        NutrientTotals.from(rs)
                ),
                mealLogId
        );
    }

    private NutrientTotals entryTotals(long mealLogId) {
        return jdbcTemplate.query("""
                        select coalesce(sum(case when n.nutrient_code = 'CALORIES_KCAL' then v.amount_per_100g * i.amount_g / 100 end), 0) as calories_kcal,
                               coalesce(sum(case when n.nutrient_code = 'CARB_G' then v.amount_per_100g * i.amount_g / 100 end), 0) as carb_g,
                               coalesce(sum(case when n.nutrient_code = 'PROTEIN_G' then v.amount_per_100g * i.amount_g / 100 end), 0) as protein_g,
                               coalesce(sum(case when n.nutrient_code = 'FAT_G' then v.amount_per_100g * i.amount_g / 100 end), 0) as fat_g,
                               coalesce(sum(case when n.nutrient_code = 'SUGAR_G' then v.amount_per_100g * i.amount_g / 100 end), 0) as sugar_g,
                               coalesce(sum(case when n.nutrient_code = 'SODIUM_MG' then v.amount_per_100g * i.amount_g / 100 end), 0) as sodium_mg
                        from diet_entry_item i
                        join food_nutrient_value v on v.food_id = i.food_id
                        join nutrient n on n.nutrient_id = v.nutrient_id
                        where i.diet_entry_id = ?
                          and i.is_excluded = false
                        """,
                (rs, rowNum) -> NutrientTotals.from(rs),
                mealLogId
        ).getFirst();
    }

    private NutrientTotals dailyTotals(long userId, LocalDate date) {
        return jdbcTemplate.query("""
                        select coalesce(sum(case when n.nutrient_code = 'CALORIES_KCAL' then v.amount_per_100g * i.amount_g / 100 end), 0) as calories_kcal,
                               coalesce(sum(case when n.nutrient_code = 'CARB_G' then v.amount_per_100g * i.amount_g / 100 end), 0) as carb_g,
                               coalesce(sum(case when n.nutrient_code = 'PROTEIN_G' then v.amount_per_100g * i.amount_g / 100 end), 0) as protein_g,
                               coalesce(sum(case when n.nutrient_code = 'FAT_G' then v.amount_per_100g * i.amount_g / 100 end), 0) as fat_g,
                               coalesce(sum(case when n.nutrient_code = 'SUGAR_G' then v.amount_per_100g * i.amount_g / 100 end), 0) as sugar_g,
                               coalesce(sum(case when n.nutrient_code = 'SODIUM_MG' then v.amount_per_100g * i.amount_g / 100 end), 0) as sodium_mg
                        from diet_entry d
                        join diet_entry_item i on i.diet_entry_id = d.diet_entry_id
                        join food_nutrient_value v on v.food_id = i.food_id
                        join nutrient n on n.nutrient_id = v.nutrient_id
                        where d.user_id = ?
                          and d.consumed_date = ?
                          and i.is_excluded = false
                        """,
                (rs, rowNum) -> NutrientTotals.from(rs),
                userId,
                date
        ).getFirst();
    }

    private List<NutritionWarning> warnings(long userId, NutrientTotals totals) {
        UserStandardProfile profile = jdbcTemplate.query("""
                        select gender, bmi
                        from user_health_profile
                        where user_id = ?
                        """,
                (rs, rowNum) -> new UserStandardProfile(rs.getString("gender"), rs.getBigDecimal("bmi")),
                userId
        ).stream().findFirst().orElseThrow(() -> DomainException.notFound("USER_PROFILE_NOT_FOUND", "사용자 프로필을 찾을 수 없습니다."));

        Optional<Long> standardGroupId = jdbcTemplate.query("""
                        select standard_group_id
                        from nutrition_standard_group
                        where gender in (?, 'ALL')
                          and (bmi_min is null or bmi_min <= ?)
                          and (bmi_max is null or bmi_max >= ?)
                        order by case when gender = ? then 0 else 1 end
                        limit 1
                        """,
                (rs, rowNum) -> rs.getLong("standard_group_id"),
                profile.gender(),
                profile.bmi(),
                profile.bmi(),
                profile.gender()
        ).stream().findFirst();

        if (standardGroupId.isEmpty()) {
            return List.of();
        }

        List<StandardValue> standards = jdbcTemplate.query("""
                        select n.nutrient_code,
                               n.nutrient_name,
                               v.recommended_amount,
                               v.upper_limit_amount
                        from nutrition_standard_value v
                        join nutrient n on n.nutrient_id = v.nutrient_id
                        where v.standard_group_id = ?
                        """,
                (rs, rowNum) -> new StandardValue(
                        rs.getString("nutrient_code"),
                        rs.getString("nutrient_name"),
                        rs.getBigDecimal("recommended_amount"),
                        rs.getBigDecimal("upper_limit_amount")
                ),
                standardGroupId.get()
        );

        List<NutritionWarning> warnings = new ArrayList<>();
        for (StandardValue standard : standards) {
            BigDecimal actual = actualAmount(totals, standard.nutrientCode());
            if (standard.upperLimitAmount() != null && actual.compareTo(standard.upperLimitAmount()) > 0) {
                warnings.add(new NutritionWarning(
                        standard.nutrientCode(),
                        standard.nutrientName(),
                        actual,
                        standard.recommendedAmount(),
                        standard.upperLimitAmount(),
                        warningMessage(standard.nutrientCode(), standard.nutrientName())
                ));
            }
        }
        return warnings;
    }

    private BigDecimal actualAmount(NutrientTotals totals, String nutrientCode) {
        return switch (nutrientCode) {
            case "CALORIES_KCAL" -> totals.caloriesKcal();
            case "CARB_G" -> totals.carbG();
            case "PROTEIN_G" -> totals.proteinG();
            case "FAT_G" -> totals.fatG();
            case "SUGAR_G" -> totals.sugarG();
            case "SODIUM_MG" -> totals.sodiumMg();
            default -> BigDecimal.ZERO;
        };
    }

    private String warningMessage(String nutrientCode, String nutrientName) {
        if ("CARB_G".equals(nutrientCode)) {
            return "오늘 탄수화물 섭취량이 기준 상한보다 높습니다. 다음 끼니는 밥/면류 양을 줄여보세요.";
        }
        return "오늘 " + nutrientName + " 섭취량이 기준 상한보다 높습니다.";
    }

    private long mealTypeId(String mealType) {
        String normalized = mealType == null ? "" : mealType.trim().toUpperCase(Locale.ROOT);
        return jdbcTemplate.query("""
                        select meal_type_id
                        from meal_type
                        where meal_type_code = ?
                        """,
                (rs, rowNum) -> rs.getLong("meal_type_id"),
                normalized
        ).stream().findFirst().orElseThrow(() -> DomainException.badRequest(
                "MEAL_TYPE_INVALID",
                "mealType은 BREAKFAST, LUNCH, DINNER, SNACK 중 하나여야 합니다."
        ));
    }

    private void assertMealLogOwner(long userId, long mealLogId) {
        boolean exists = jdbcTemplate.queryForObject("""
                        select count(*)
                        from diet_entry
                        where user_id = ? and diet_entry_id = ?
                        """,
                Integer.class,
                userId,
                mealLogId
        ) > 0;
        if (!exists) {
            throw DomainException.notFound("MEAL_LOG_NOT_FOUND", "식단 기록을 찾을 수 없습니다.");
        }
    }

    private record MealLogHeader(Long mealLogId, LocalDate logDate, String mealType, String memo) {
    }

    private record FoodPortion(Long foodId, String foodName, BigDecimal defaultServingG) {
    }

    private record MenuFoodItem(Long foodId, String rawItemName, BigDecimal amountG) {
    }

    private record UserStandardProfile(String gender, BigDecimal bmi) {
    }

    private record StandardValue(
            String nutrientCode,
            String nutrientName,
            BigDecimal recommendedAmount,
            BigDecimal upperLimitAmount
    ) {
    }
}
