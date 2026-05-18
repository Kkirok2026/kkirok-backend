package com.database2026.backend.meal;

import com.database2026.backend.common.DomainException;
import com.database2026.backend.common.NutrientTotals;
import com.database2026.backend.meal.MealDtos.DailySummaryResponse;
import com.database2026.backend.meal.MealDtos.FoodMealLogItemRequest;
import com.database2026.backend.meal.MealDtos.FoodMealLogItemsAddRequest;
import com.database2026.backend.meal.MealDtos.MacroEnergyRatio;
import com.database2026.backend.meal.MealDtos.MealAllergyWarning;
import com.database2026.backend.meal.MealDtos.MealLogCreateRequest;
import com.database2026.backend.meal.MealDtos.MealLogItemResponse;
import com.database2026.backend.meal.MealDtos.MealLogListResponse;
import com.database2026.backend.meal.MealDtos.MealLogResponse;
import com.database2026.backend.meal.MealDtos.MenuOptionMealLogAddRequest;
import com.database2026.backend.meal.MealDtos.NutritionWarning;
import com.database2026.backend.meal.MealDtos.RecommendedNutritionTargets;
import com.database2026.backend.support.SqlSupport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MealService {

    private static final String DEFAULT_ACTIVITY_LEVEL = "LOW_ACTIVE";
    private static final BigDecimal SODIUM_MAX_MG = BigDecimal.valueOf(2300);
    private static final String ALLERGY_WARNING_MESSAGE =
            "%s 알레르기 항목이 포함되어 있을 수 있습니다. 섭취 전 원재료를 확인하세요.";
    private static final String TARGET_BASIS =
            "IOM DRI 성인 EER 공식 + 2025 한국인 영양소 섭취기준 에너지 적정비율"
                    + "(탄수화물 50-65%, 단백질 10-20%, 지방 15-30%, 총당류 20% 이내)";

    private final JdbcTemplate jdbcTemplate;
    private final SqlSupport sqlSupport;

    public MealService(JdbcTemplate jdbcTemplate, SqlSupport sqlSupport) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlSupport = sqlSupport;
    }

    @Transactional
    public MealLogResponse create(long userId, MealLogCreateRequest request) {
        long mealLogId = createMealLog(userId, request.logDate(), request.mealType(), request.memo());
        return mealLog(userId, mealLogId);
    }

    @Transactional
    public MealLogResponse addFoodItems(long userId, long mealLogId, FoodMealLogItemsAddRequest request) {
        assertMealLogOwner(userId, mealLogId);
        for (FoodMealLogItemRequest item : request.items()) {
            insertFoodItem(userId, mealLogId, item.foodId(), item.amountG());
        }
        return mealLog(userId, mealLogId);
    }

    @Transactional
    public MealLogResponse addMenuOption(long userId, MenuOptionMealLogAddRequest request) {
        MenuOptionContext option = menuOptionContext(request.menuOptionId());
        assertUserCanUseMenuOption(userId, option.universityId());
        long mealLogId = findMealLogId(userId, option.servedDate(), option.mealType())
                .orElseGet(() -> createMealLog(userId, option.servedDate(), option.mealType(), request.memo()));
        assertMenuOptionNotAlreadyAdded(mealLogId, option.optionId());
        insertMenuOptionItems(mealLogId, option);
        return mealLog(userId, mealLogId);
    }

    @Transactional
    public MealLogResponse setExcluded(long userId, long mealLogId, long mealLogItemId, boolean excluded) {
        int updated = jdbcTemplate.update("""
                update meal_log_item
                set is_excluded = ?
                where meal_log_item_id = ?
                  and meal_log_id in (
                      select meal_log_id
                      from meal_log
                      where meal_log_id = ? and user_id = ?
                  )
                """, excluded, mealLogItemId, mealLogId, userId);
        if (updated == 0) {
            throw DomainException.notFound("MEAL_LOG_ITEM_NOT_FOUND", "식단 항목을 찾을 수 없습니다.");
        }
        return mealLog(userId, mealLogId);
    }

    public MealLogResponse mealLog(long userId, long mealLogId) {
        MealLogHeader header = jdbcTemplate.query("""
                        select d.meal_log_id,
                               d.log_date,
                               d.meal_type,
                               d.memo
                        from meal_log d
                        where d.user_id = ? and d.meal_log_id = ?
                        """,
                (rs, rowNum) -> new MealLogHeader(
                        rs.getLong("meal_log_id"),
                        rs.getObject("log_date", LocalDate.class),
                        rs.getString("meal_type"),
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
                mealLogItems(userId, mealLogId),
                entryTotals(mealLogId)
        );
    }

    public MealLogListResponse listByDate(long userId, LocalDate date) {
        List<Long> ids = jdbcTemplate.query("""
                        select d.meal_log_id
                        from meal_log d
                        where d.user_id = ? and d.log_date = ?
                        order by case d.meal_type
                                     when 'BREAKFAST' then 1
                                     when 'LUNCH' then 2
                                     when 'DINNER' then 3
                                     else 4
                                 end
                        """,
                (rs, rowNum) -> rs.getLong("meal_log_id"),
                userId,
                date
        );
        return new MealLogListResponse(ids.stream().map(id -> mealLog(userId, id)).toList());
    }

    public DailySummaryResponse dailySummary(long userId, LocalDate date) {
        NutrientTotals totals = dailyTotals(userId, date);
        RecommendedNutritionTargets targets = recommendedTargets(userId);
        MacroEnergyRatio macroRatios = macroRatios(totals);
        List<NutritionWarning> warnings = feedbackWarnings(totals, targets, macroRatios);
        return new DailySummaryResponse(date, totals, targets, macroRatios, warnings);
    }

    private long createMealLog(long userId, LocalDate logDate, String mealType, String memo) {
        String normalizedMealType = normalizeMealType(mealType);
        try {
            return sqlSupport.insert("""
                    insert into meal_log (user_id, meal_type, log_date, memo)
                    values (?, ?, ?, ?)
                    """, userId, normalizedMealType, logDate, memo);
        } catch (DuplicateKeyException exception) {
            throw DomainException.conflict("MEAL_LOG_ALREADY_EXISTS", "이미 해당 날짜/끼니 식단 기록이 있습니다.");
        }
    }

    private void insertFoodItem(long userId, long mealLogId, long foodId, BigDecimal requestedAmountG) {
        FoodPortion food = foodPortion(userId, foodId);
        BigDecimal amountG = Optional.ofNullable(requestedAmountG).orElse(food.defaultServingG());
        insertMealLogItem(mealLogId, food.foodId(), null, food.foodName(), amountG);
    }

    private void insertMenuOptionItems(long mealLogId, MenuOptionContext option) {
        if (option.hasOptionNutrients()) {
            insertMealLogItem(mealLogId, null, option.optionId(), option.optionName(), BigDecimal.valueOf(100));
            return;
        }
        List<CafeteriaMenuItem> menuItems = cafeteriaMenuItems(option.optionId());
        if (menuItems.isEmpty()) {
            insertMealLogItem(mealLogId, null, option.optionId(), option.optionName(), BigDecimal.valueOf(100));
            return;
        }
        for (CafeteriaMenuItem item : menuItems) {
            insertMealLogItem(mealLogId, item.foodId(), option.optionId(), item.rawItemName(), item.amountG());
        }
    }

    private void insertMealLogItem(long mealLogId, Long foodId, Long sourceMenuOptionId, String itemName, BigDecimal amountG) {
        if (amountG == null || amountG.compareTo(BigDecimal.ZERO) <= 0) {
            throw DomainException.badRequest("AMOUNT_INVALID", "amountG는 0보다 커야 합니다.");
        }
        sqlSupport.update("""
                insert into meal_log_item (meal_log_id, food_id, source_menu_option_id, item_name_snapshot, amount_g)
                values (?, ?, ?, ?, ?)
                """, mealLogId, foodId, sourceMenuOptionId, itemName, amountG);
    }

    private FoodPortion foodPortion(long userId, Long foodId) {
        return jdbcTemplate.query("""
                        select food_id, food_name, default_serving_g
                        from food
                        where food_id = ?
                          and (
                              source_name = 'NATIONAL_INTEGRATED'
                              or (
                                  source_name = 'USER_CUSTOM'
                                  and exists (
                                      select 1
                                      from user_custom_food ucf
                                      where ucf.food_id = food.food_id
                                        and ucf.user_id = ?
                                  )
                              )
                          )
                        """,
                (rs, rowNum) -> new FoodPortion(
                        rs.getLong("food_id"),
                        rs.getString("food_name"),
                        rs.getBigDecimal("default_serving_g")
                ),
                foodId,
                userId
        ).stream().findFirst().orElseThrow(() -> DomainException.notFound("FOOD_NOT_FOUND", "음식을 찾을 수 없습니다."));
    }

    private MenuOptionContext menuOptionContext(long optionId) {
        return jdbcTemplate.query("""
                        select o.option_id,
                               o.option_name,
                               dp.university_id,
                               m.served_date,
                               m.meal_type,
                               case when o.calories_kcal is not null
                                      or o.carb_g is not null
                                      or o.protein_g is not null
                                      or o.fat_g is not null
                                      or o.sugar_g is not null
                                      or o.sodium_mg is not null
                                    then true
                                    else false
                               end as has_option_nutrients
                        from cafeteria_menu_option o
                        join cafeteria_menu m on m.menu_id = o.menu_id
                        join dining_place dp on dp.dining_place_id = m.dining_place_id
                        where o.option_id = ?
                          and o.is_available = true
                          and dp.is_active = true
                        """,
                (rs, rowNum) -> new MenuOptionContext(
                        rs.getLong("option_id"),
                        rs.getString("option_name"),
                        rs.getLong("university_id"),
                        rs.getObject("served_date", LocalDate.class),
                        rs.getString("meal_type"),
                        rs.getBoolean("has_option_nutrients")
                ),
                optionId
        ).stream().findFirst().orElseThrow(() -> DomainException.notFound("MENU_OPTION_NOT_FOUND", "식당 메뉴를 찾을 수 없습니다."));
    }

    private List<CafeteriaMenuItem> cafeteriaMenuItems(long optionId) {
        return jdbcTemplate.query("""
                        select food_id, raw_item_name, amount_g
                        from cafeteria_menu_item
                        where option_id = ?
                        order by menu_item_id
                        """,
                (rs, rowNum) -> new CafeteriaMenuItem(
                        rs.getObject("food_id", Long.class),
                        rs.getString("raw_item_name"),
                        rs.getBigDecimal("amount_g")
                ),
                optionId
        );
    }

    private void assertUserCanUseMenuOption(long userId, long universityId) {
        UserUniversity userUniversity = jdbcTemplate.query("""
                        select university_id
                        from user_account
                        where user_id = ?
                          and status = 'ACTIVE'
                        """,
                (rs, rowNum) -> new UserUniversity(rs.getObject("university_id", Long.class)),
                userId
        ).stream().findFirst().orElseThrow(() -> DomainException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
        Long selectedUniversityId = userUniversity.universityId();
        if (selectedUniversityId == null) {
            throw DomainException.badRequest("SCHOOL_EMAIL_USER_REQUIRED", "식당 메뉴 추가는 학교 이메일로 인증된 사용자만 이용할 수 있습니다.");
        }
        if (!selectedUniversityId.equals(universityId)) {
            throw DomainException.badRequest("UNIVERSITY_SELECTION_MISMATCH", "본인 학교의 식당 메뉴만 식단에 추가할 수 있습니다.");
        }
    }

    private Optional<Long> findMealLogId(long userId, LocalDate logDate, String mealType) {
        return jdbcTemplate.query("""
                        select d.meal_log_id
                        from meal_log d
                        where d.user_id = ?
                          and d.log_date = ?
                          and d.meal_type = ?
                        """,
                (rs, rowNum) -> rs.getLong("meal_log_id"),
                userId,
                logDate,
                mealType
        ).stream().findFirst();
    }

    private void assertMenuOptionNotAlreadyAdded(long mealLogId, long optionId) {
        Integer count = jdbcTemplate.queryForObject("""
                        select count(*)
                        from meal_log_item
                        where meal_log_id = ?
                          and source_menu_option_id = ?
                        """,
                Integer.class,
                mealLogId,
                optionId
        );
        if (count != null && count > 0) {
            throw DomainException.conflict("MENU_OPTION_ALREADY_ADDED", "이미 이 식당 메뉴가 식단에 추가되어 있습니다.");
        }
    }

    private List<MealLogItemResponse> mealLogItems(long userId, long mealLogId) {
        return jdbcTemplate.query("""
                        select i.meal_log_item_id,
                               i.food_id,
                               i.source_menu_option_id,
                               i.item_name_snapshot,
                               i.amount_g,
                               i.is_excluded,
                               case when i.food_id is not null
                                    then coalesce(f.calories_kcal * i.amount_g / 100, 0)
                                    else coalesce(o.calories_kcal * i.amount_g / 100, 0)
                               end as calories_kcal,
                               case when i.food_id is not null
                                    then coalesce(f.carb_g * i.amount_g / 100, 0)
                                    else coalesce(o.carb_g * i.amount_g / 100, 0)
                               end as carb_g,
                               case when i.food_id is not null
                                    then coalesce(f.protein_g * i.amount_g / 100, 0)
                                    else coalesce(o.protein_g * i.amount_g / 100, 0)
                               end as protein_g,
                               case when i.food_id is not null
                                    then coalesce(f.fat_g * i.amount_g / 100, 0)
                                    else coalesce(o.fat_g * i.amount_g / 100, 0)
                               end as fat_g,
                               case when i.food_id is not null
                                    then coalesce(f.sugar_g * i.amount_g / 100, 0)
                                    else coalesce(o.sugar_g * i.amount_g / 100, 0)
                               end as sugar_g,
                               case when i.food_id is not null
                                    then coalesce(f.sodium_mg * i.amount_g / 100, 0)
                                    else coalesce(o.sodium_mg * i.amount_g / 100, 0)
                               end as sodium_mg
                        from meal_log_item i
                        left join v_menu_option_comparison o on o.option_id = i.source_menu_option_id
                        left join food f on f.food_id = i.food_id
                        where i.meal_log_id = ?
                        order by i.meal_log_item_id
                        """,
                (rs, rowNum) -> {
                    Long foodId = rs.getObject("food_id", Long.class);
                    String itemName = rs.getString("item_name_snapshot");
                    return new MealLogItemResponse(
                            rs.getLong("meal_log_item_id"),
                            foodId,
                            (Long) rs.getObject("source_menu_option_id"),
                            itemName,
                            rs.getBigDecimal("amount_g"),
                            rs.getBoolean("is_excluded"),
                            NutrientTotals.from(rs),
                            allergyWarnings(userId, foodId, itemName)
                    );
                },
                mealLogId
        );
    }

    private List<MealAllergyWarning> allergyWarnings(long userId, Long foodId, String itemName) {
        Map<String, MealAllergyWarning> warnings = new LinkedHashMap<>();
        for (UserFoodAllergy allergy : userFoodAllergies(userId)) {
            if (foodId != null && foodId.equals(allergy.foodId())) {
                addAllergyWarning(warnings, new MealAllergyWarning(
                        "FOOD_MATCH",
                        allergy.foodName(),
                        itemName,
                        "FOOD",
                        allergyWarningMessage(allergy.foodName())
                ));
            }
        }

        String normalizedItemName = normalizeForAllergyMatch(itemName);
        for (UserIngredientKeyword keyword : userIngredientKeywords(userId)) {
            if (!keyword.normalizedKeyword().isBlank() && normalizedItemName.contains(keyword.normalizedKeyword())) {
                addAllergyWarning(warnings, new MealAllergyWarning(
                        "POSSIBLE_INGREDIENT_NAME_MATCH",
                        keyword.allergyName(),
                        itemName,
                        keyword.source(),
                        allergyWarningMessage(keyword.allergyName())
                ));
            }
        }

        if (foodId != null) {
            for (FoodIngredientMatch match : foodIngredientMatches(userId, foodId)) {
                addAllergyWarning(warnings, new MealAllergyWarning(
                        "FOOD_INGREDIENT_MATCH",
                        match.allergyName(),
                        match.ingredientName(),
                        "FOOD_INGREDIENT",
                        allergyWarningMessage(match.allergyName())
                ));
            }
        }
        return List.copyOf(warnings.values());
    }

    private List<UserFoodAllergy> userFoodAllergies(long userId) {
        return jdbcTemplate.query("""
                        select a.food_id, f.food_name
                        from user_allergy a
                        join food f on f.food_id = a.food_id
                        where a.user_id = ?
                          and a.allergy_type = 'FOOD'
                        """,
                (rs, rowNum) -> new UserFoodAllergy(rs.getLong("food_id"), rs.getString("food_name")),
                userId
        );
    }

    private List<UserIngredientKeyword> userIngredientKeywords(long userId) {
        Set<UserIngredientKeyword> keywords = new LinkedHashSet<>();
        keywords.addAll(jdbcTemplate.query("""
                        select allergy_name, normalized_allergy_name as keyword, 'USER_INPUT' as source
                        from user_allergy
                        where user_id = ?
                          and allergy_type = 'INGREDIENT'
                        """,
                (rs, rowNum) -> new UserIngredientKeyword(
                        rs.getString("allergy_name"),
                        rs.getString("keyword"),
                        rs.getString("source")
                ),
                userId
        ));
        keywords.addAll(jdbcTemplate.query("""
                        select uia.allergy_name, i.normalized_name as keyword, 'INGREDIENT' as source
                        from user_allergy uia
                        join ingredient i on i.ingredient_id = uia.ingredient_id
                        where uia.user_id = ?
                          and uia.allergy_type = 'INGREDIENT'
                        """,
                (rs, rowNum) -> new UserIngredientKeyword(
                        rs.getString("allergy_name"),
                        rs.getString("keyword"),
                        rs.getString("source")
                ),
                userId
        ));
        keywords.addAll(jdbcTemplate.query("""
                        select uia.allergy_name, ia.normalized_alias as keyword, 'INGREDIENT_ALIAS' as source
                        from user_allergy uia
                        join ingredient_alias ia on ia.ingredient_id = uia.ingredient_id
                        where uia.user_id = ?
                          and uia.allergy_type = 'INGREDIENT'
                        """,
                (rs, rowNum) -> new UserIngredientKeyword(
                        rs.getString("allergy_name"),
                        rs.getString("keyword"),
                        rs.getString("source")
                ),
                userId
        ));
        return List.copyOf(keywords);
    }

    private List<FoodIngredientMatch> foodIngredientMatches(long userId, long foodId) {
        return jdbcTemplate.query("""
                        select distinct uia.allergy_name, i.ingredient_name
                        from user_allergy uia
                        join food_ingredient fi on fi.ingredient_id = uia.ingredient_id
                        join ingredient i on i.ingredient_id = fi.ingredient_id
                        where uia.user_id = ?
                          and uia.allergy_type = 'INGREDIENT'
                          and fi.food_id = ?
                        """,
                (rs, rowNum) -> new FoodIngredientMatch(
                        rs.getString("allergy_name"),
                        rs.getString("ingredient_name")
                ),
                userId,
                foodId
        );
    }

    private String allergyWarningMessage(String allergyName) {
        return ALLERGY_WARNING_MESSAGE.formatted(allergyName);
    }

    private void addAllergyWarning(Map<String, MealAllergyWarning> warnings, MealAllergyWarning warning) {
        warnings.putIfAbsent(
                warning.warningType() + "|" + warning.allergyName() + "|" + warning.matchedText(),
                warning
        );
    }

    private String normalizeForAllergyMatch(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_\\-()\\[\\]{}]", "");
    }

    private NutrientTotals entryTotals(long mealLogId) {
        return jdbcTemplate.query("""
                        select coalesce(sum(case
                                   when i.food_id is null and i.source_menu_option_id is not null then o.calories_kcal * i.amount_g / 100
                                   when i.food_id is not null then f.calories_kcal * i.amount_g / 100
                                   else 0
                               end), 0) as calories_kcal,
                               coalesce(sum(case
                                   when i.food_id is null and i.source_menu_option_id is not null then o.carb_g * i.amount_g / 100
                                   when i.food_id is not null then f.carb_g * i.amount_g / 100
                                   else 0
                               end), 0) as carb_g,
                               coalesce(sum(case
                                   when i.food_id is null and i.source_menu_option_id is not null then o.protein_g * i.amount_g / 100
                                   when i.food_id is not null then f.protein_g * i.amount_g / 100
                                   else 0
                               end), 0) as protein_g,
                               coalesce(sum(case
                                   when i.food_id is null and i.source_menu_option_id is not null then o.fat_g * i.amount_g / 100
                                   when i.food_id is not null then f.fat_g * i.amount_g / 100
                                   else 0
                               end), 0) as fat_g,
                               coalesce(sum(case
                                   when i.food_id is null and i.source_menu_option_id is not null then o.sugar_g * i.amount_g / 100
                                   when i.food_id is not null then f.sugar_g * i.amount_g / 100
                                   else 0
                               end), 0) as sugar_g,
                               coalesce(sum(case
                                   when i.food_id is null and i.source_menu_option_id is not null then o.sodium_mg * i.amount_g / 100
                                   when i.food_id is not null then f.sodium_mg * i.amount_g / 100
                                   else 0
                               end), 0) as sodium_mg
                        from meal_log_item i
                        left join v_menu_option_comparison o on o.option_id = i.source_menu_option_id
                        left join food f on f.food_id = i.food_id
                        where i.meal_log_id = ?
                          and i.is_excluded = false
                        """,
                (rs, rowNum) -> NutrientTotals.from(rs),
                mealLogId
        ).getFirst();
    }

    private NutrientTotals dailyTotals(long userId, LocalDate date) {
        return jdbcTemplate.query("""
                        select coalesce(sum(case
                                   when i.food_id is null and i.source_menu_option_id is not null then o.calories_kcal * i.amount_g / 100
                                   when i.food_id is not null then f.calories_kcal * i.amount_g / 100
                                   else 0
                               end), 0) as calories_kcal,
                               coalesce(sum(case
                                   when i.food_id is null and i.source_menu_option_id is not null then o.carb_g * i.amount_g / 100
                                   when i.food_id is not null then f.carb_g * i.amount_g / 100
                                   else 0
                               end), 0) as carb_g,
                               coalesce(sum(case
                                   when i.food_id is null and i.source_menu_option_id is not null then o.protein_g * i.amount_g / 100
                                   when i.food_id is not null then f.protein_g * i.amount_g / 100
                                   else 0
                               end), 0) as protein_g,
                               coalesce(sum(case
                                   when i.food_id is null and i.source_menu_option_id is not null then o.fat_g * i.amount_g / 100
                                   when i.food_id is not null then f.fat_g * i.amount_g / 100
                                   else 0
                               end), 0) as fat_g,
                               coalesce(sum(case
                                   when i.food_id is null and i.source_menu_option_id is not null then o.sugar_g * i.amount_g / 100
                                   when i.food_id is not null then f.sugar_g * i.amount_g / 100
                                   else 0
                               end), 0) as sugar_g,
                               coalesce(sum(case
                                   when i.food_id is null and i.source_menu_option_id is not null then o.sodium_mg * i.amount_g / 100
                                   when i.food_id is not null then f.sodium_mg * i.amount_g / 100
                                   else 0
                               end), 0) as sodium_mg
                        from meal_log d
                        join meal_log_item i on i.meal_log_id = d.meal_log_id
                        left join v_menu_option_comparison o on o.option_id = i.source_menu_option_id
                        left join food f on f.food_id = i.food_id
                        where d.user_id = ?
                          and d.log_date = ?
                          and i.is_excluded = false
                        """,
                (rs, rowNum) -> NutrientTotals.from(rs),
                userId,
                date
        ).getFirst();
    }

    private RecommendedNutritionTargets recommendedTargets(long userId) {
        NutritionProfile profile = jdbcTemplate.query("""
                        select u.age,
                               p.gender,
                               p.height_cm,
                               p.weight_kg,
                               p.activity_level
                        from user_account u
                        left join user_health_profile p on p.user_id = u.user_id
                        where u.user_id = ?
                        """,
                (rs, rowNum) -> new NutritionProfile(
                        (Integer) rs.getObject("age"),
                        rs.getString("gender"),
                        rs.getBigDecimal("height_cm"),
                        rs.getBigDecimal("weight_kg"),
                        rs.getString("activity_level")
                ),
                userId
        ).stream().findFirst().orElse(null);

        if (profile == null
                || profile.age() == null
                || profile.age() < 9
                || profile.gender() == null
                || profile.heightCm() == null
                || profile.weightKg() == null) {
            return null;
        }

        String activityLevel = Optional.ofNullable(profile.activityLevel()).orElse(DEFAULT_ACTIVITY_LEVEL);
        BigDecimal calories = BigDecimal.valueOf(estimatedEnergyRequirement(profile, activityLevel))
                .setScale(0, RoundingMode.HALF_UP);

        return new RecommendedNutritionTargets(
                calories,
                macroGram(calories, "0.50", 4),
                macroGram(calories, "0.65", 4),
                macroGram(calories, "0.10", 4),
                macroGram(calories, "0.20", 4),
                macroGram(calories, "0.15", 9),
                macroGram(calories, "0.30", 9),
                macroGram(calories, "0.20", 4),
                SODIUM_MAX_MG,
                activityLevel,
                TARGET_BASIS
        );
    }

    private double estimatedEnergyRequirement(NutritionProfile profile, String activityLevel) {
        double age = profile.age();
        double weightKg = profile.weightKg().doubleValue();
        double heightM = profile.heightCm().doubleValue() / 100.0;
        if (age < 19) {
            if ("MALE".equals(profile.gender())) {
                return eerBoy(age, weightKg, heightM, activityLevel);
            }
            if ("FEMALE".equals(profile.gender())) {
                return eerGirl(age, weightKg, heightM, activityLevel);
            }
            return (eerBoy(age, weightKg, heightM, activityLevel) + eerGirl(age, weightKg, heightM, activityLevel)) / 2.0;
        }
        if ("MALE".equals(profile.gender())) {
            return eerMale(age, weightKg, heightM, activityLevel);
        }
        if ("FEMALE".equals(profile.gender())) {
            return eerFemale(age, weightKg, heightM, activityLevel);
        }
        return (eerMale(age, weightKg, heightM, activityLevel) + eerFemale(age, weightKg, heightM, activityLevel)) / 2.0;
    }

    private double eerMale(double age, double weightKg, double heightM, String activityLevel) {
        return 662 - 9.53 * age + physicalActivityCoefficient("MALE", activityLevel, true)
                * (15.91 * weightKg + 539.6 * heightM);
    }

    private double eerFemale(double age, double weightKg, double heightM, String activityLevel) {
        return 354 - 6.91 * age + physicalActivityCoefficient("FEMALE", activityLevel, true)
                * (9.36 * weightKg + 726 * heightM);
    }

    private double eerBoy(double age, double weightKg, double heightM, String activityLevel) {
        return 88.5 - 61.9 * age + physicalActivityCoefficient("MALE", activityLevel, false)
                * (26.7 * weightKg + 903 * heightM) + 25;
    }

    private double eerGirl(double age, double weightKg, double heightM, String activityLevel) {
        return 135.3 - 30.8 * age + physicalActivityCoefficient("FEMALE", activityLevel, false)
                * (10.0 * weightKg + 934 * heightM) + 25;
    }

    private double physicalActivityCoefficient(String gender, String activityLevel, boolean adult) {
        String normalized = activityLevel == null ? DEFAULT_ACTIVITY_LEVEL : activityLevel.trim().toUpperCase(Locale.ROOT);
        if ("MALE".equals(gender)) {
            return switch (normalized) {
                case "SEDENTARY" -> 1.00;
                case "ACTIVE" -> adult ? 1.25 : 1.26;
                case "VERY_ACTIVE" -> adult ? 1.48 : 1.42;
                default -> adult ? 1.11 : 1.13;
            };
        }
        return switch (normalized) {
            case "SEDENTARY" -> 1.00;
            case "ACTIVE" -> adult ? 1.27 : 1.31;
            case "VERY_ACTIVE" -> adult ? 1.45 : 1.56;
            default -> adult ? 1.12 : 1.16;
        };
    }

    private BigDecimal macroGram(BigDecimal calories, String ratio, int kcalPerGram) {
        return calories.multiply(new BigDecimal(ratio))
                .divide(BigDecimal.valueOf(kcalPerGram), 1, RoundingMode.HALF_UP);
    }

    private MacroEnergyRatio macroRatios(NutrientTotals totals) {
        if (totals.caloriesKcal().compareTo(BigDecimal.ZERO) <= 0) {
            return new MacroEnergyRatio(zeroPercent(), zeroPercent(), zeroPercent(), zeroPercent());
        }
        return new MacroEnergyRatio(
                energyPercent(totals.carbG(), 4, totals.caloriesKcal()),
                energyPercent(totals.proteinG(), 4, totals.caloriesKcal()),
                energyPercent(totals.fatG(), 9, totals.caloriesKcal()),
                energyPercent(totals.sugarG(), 4, totals.caloriesKcal())
        );
    }

    private BigDecimal energyPercent(BigDecimal grams, int kcalPerGram, BigDecimal totalCalories) {
        return grams.multiply(BigDecimal.valueOf(kcalPerGram))
                .multiply(BigDecimal.valueOf(100))
                .divide(totalCalories, 1, RoundingMode.HALF_UP);
    }

    private BigDecimal zeroPercent() {
        return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
    }

    private List<NutritionWarning> feedbackWarnings(
            NutrientTotals totals,
            RecommendedNutritionTargets targets,
            MacroEnergyRatio macroRatios
    ) {
        if (targets == null) {
            return List.of();
        }
        List<NutritionWarning> warnings = new ArrayList<>();
        BigDecimal calorieLowerLimit = targets.caloriesKcal().multiply(new BigDecimal("0.90")).setScale(0, RoundingMode.HALF_UP);
        BigDecimal calorieUpperLimit = targets.caloriesKcal().multiply(new BigDecimal("1.10")).setScale(0, RoundingMode.HALF_UP);
        if (totals.caloriesKcal().compareTo(calorieUpperLimit) > 0) {
            warnings.add(new NutritionWarning(
                    "CALORIES_HIGH",
                    "CALORIES_KCAL",
                    "열량",
                    totals.caloriesKcal(),
                    targets.caloriesKcal(),
                    calorieLowerLimit,
                    calorieUpperLimit,
                    "IOM DRI 성인 EER 공식으로 계산한 권장 섭취 열량의 110% 초과",
                    "해당 날짜의 총 섭취 열량이 권장 섭취 열량보다 높습니다."
            ));
        } else if (totals.caloriesKcal().compareTo(calorieLowerLimit) < 0 && totals.caloriesKcal().compareTo(BigDecimal.ZERO) > 0) {
            warnings.add(new NutritionWarning(
                    "CALORIES_LOW",
                    "CALORIES_KCAL",
                    "열량",
                    totals.caloriesKcal(),
                    targets.caloriesKcal(),
                    calorieLowerLimit,
                    calorieUpperLimit,
                    "IOM DRI 성인 EER 공식으로 계산한 권장 섭취 열량의 90% 미만",
                    "해당 날짜의 총 섭취 열량이 권장 섭취 열량보다 낮습니다."
            ));
        }
        addRatioWarning(warnings, "CARB_RATIO_LOW", "CARB_G", "탄수화물", macroRatios.carbPercent(),
                "50", "65", "2025 한국인 영양소 섭취기준 탄수화물 에너지 적정비율 50-65%",
                "탄수화물 에너지 비율이 권장 범위보다 낮습니다.", false);
        addRatioWarning(warnings, "CARB_RATIO_HIGH", "CARB_G", "탄수화물", macroRatios.carbPercent(),
                "50", "65", "2025 한국인 영양소 섭취기준 탄수화물 에너지 적정비율 50-65%",
                "탄수화물 에너지 비율이 권장 범위보다 높습니다.", true);
        addRatioWarning(warnings, "PROTEIN_RATIO_LOW", "PROTEIN_G", "단백질", macroRatios.proteinPercent(),
                "10", "20", "2025 한국인 영양소 섭취기준 단백질 에너지 적정비율 10-20%",
                "단백질 에너지 비율이 권장 범위보다 낮습니다.", false);
        addRatioWarning(warnings, "PROTEIN_RATIO_HIGH", "PROTEIN_G", "단백질", macroRatios.proteinPercent(),
                "10", "20", "2025 한국인 영양소 섭취기준 단백질 에너지 적정비율 10-20%",
                "단백질 에너지 비율이 권장 범위보다 높습니다.", true);
        addRatioWarning(warnings, "FAT_RATIO_LOW", "FAT_G", "지방", macroRatios.fatPercent(),
                "15", "30", "2025 한국인 영양소 섭취기준 지방 에너지 적정비율 15-30%",
                "지방 에너지 비율이 권장 범위보다 낮습니다.", false);
        addRatioWarning(warnings, "FAT_RATIO_HIGH", "FAT_G", "지방", macroRatios.fatPercent(),
                "15", "30", "2025 한국인 영양소 섭취기준 지방 에너지 적정비율 15-30%",
                "지방 에너지 비율이 권장 범위보다 높습니다.", true);
        if (macroRatios.sugarPercent().compareTo(BigDecimal.valueOf(20)) > 0) {
            warnings.add(new NutritionWarning(
                    "SUGAR_RATIO_HIGH",
                    "SUGAR_G",
                    "당류",
                    macroRatios.sugarPercent(),
                    null,
                    null,
                    BigDecimal.valueOf(20),
                    "2025 한국인 영양소 섭취기준 총당류 에너지 섭취비율 20% 이내",
                    "당류 에너지 비율이 권장 상한보다 높습니다."
            ));
        }
        if (totals.sodiumMg().compareTo(targets.sodiumMaxMg()) > 0) {
            warnings.add(new NutritionWarning(
                    "SODIUM_HIGH",
                    "SODIUM_MG",
                    "나트륨",
                    totals.sodiumMg(),
                    null,
                    null,
                    targets.sodiumMaxMg(),
                    "2020 한국인 영양소 섭취기준 19-64세 성인 나트륨 만성질환위험감소섭취량 2300mg/일 기준",
                    "나트륨 섭취량이 기준보다 높습니다."
            ));
        }
        return warnings;
    }

    private void addRatioWarning(
            List<NutritionWarning> warnings,
            String warningCode,
            String nutrientCode,
            String nutrientName,
            BigDecimal actualPercent,
            String lowerLimit,
            String upperLimit,
            String basis,
            String message,
            boolean high
    ) {
        BigDecimal lower = new BigDecimal(lowerLimit);
        BigDecimal upper = new BigDecimal(upperLimit);
        boolean shouldAdd = high ? actualPercent.compareTo(upper) > 0 : actualPercent.compareTo(lower) < 0;
        if (!shouldAdd || actualPercent.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        warnings.add(new NutritionWarning(
                warningCode,
                nutrientCode,
                nutrientName,
                actualPercent,
                null,
                lower,
                upper,
                basis,
                message
        ));
    }

    private String normalizeMealType(String mealType) {
        String normalized = mealType == null ? "" : mealType.trim().toUpperCase(Locale.ROOT);
        if (!List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK").contains(normalized)) {
            throw DomainException.badRequest(
                    "MEAL_TYPE_INVALID",
                    "mealType은 BREAKFAST, LUNCH, DINNER, SNACK 중 하나여야 합니다."
            );
        }
        return normalized;
    }

    private void assertMealLogOwner(long userId, long mealLogId) {
        boolean exists = jdbcTemplate.queryForObject("""
                        select count(*)
                        from meal_log
                        where user_id = ? and meal_log_id = ?
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

    private record MenuOptionContext(
            Long optionId,
            String optionName,
            Long universityId,
            LocalDate servedDate,
            String mealType,
            Boolean hasOptionNutrients
    ) {
    }

    private record CafeteriaMenuItem(Long foodId, String rawItemName, BigDecimal amountG) {
    }

    private record UserFoodAllergy(Long foodId, String foodName) {
    }

    private record UserIngredientKeyword(String allergyName, String normalizedKeyword, String source) {
    }

    private record FoodIngredientMatch(String allergyName, String ingredientName) {
    }

    private record UserUniversity(Long universityId) {
    }

    private record NutritionProfile(
            Integer age,
            String gender,
            BigDecimal heightCm,
            BigDecimal weightKg,
            String activityLevel
    ) {
    }
}
