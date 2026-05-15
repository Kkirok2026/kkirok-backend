package com.database2026.backend.menu;

import com.database2026.backend.common.DomainException;
import com.database2026.backend.common.NutrientTotals;
import com.database2026.backend.menu.MenuDtos.DailyMenuResponse;
import com.database2026.backend.menu.MenuDtos.DiningPlaceItem;
import com.database2026.backend.menu.MenuDtos.DiningPlaceListResponse;
import com.database2026.backend.menu.MenuDtos.DiningPlaceMenu;
import com.database2026.backend.menu.MenuDtos.MenuCompareResponse;
import com.database2026.backend.menu.MenuDtos.MenuAllergyWarning;
import com.database2026.backend.menu.MenuDtos.MenuOptionCompareItem;
import com.database2026.backend.menu.MenuDtos.MenuOptionSummary;
import com.database2026.backend.menu.MenuDtos.UniversityItem;
import com.database2026.backend.menu.MenuDtos.UniversityListResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MenuService {

    private static final String ALLERGY_WARNING_MESSAGE =
            "%s 알레르기 항목이 포함되어 있을 수 있습니다. 섭취 전 원재료를 확인하세요.";

    private final JdbcTemplate jdbcTemplate;

    public MenuService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UniversityListResponse universities() {
        List<UniversityItem> items = new ArrayList<>();
        items.addAll(jdbcTemplate.query("""
                        select university_id, university_name
                        from universities
                        order by university_name
                        """,
                (rs, rowNum) -> new UniversityItem(
                        rs.getLong("university_id"),
                        rs.getString("university_name")
                )
        ));
        return new UniversityListResponse(items);
    }

    public DiningPlaceListResponse diningPlaces(long universityId) {
        List<DiningPlaceItem> items = jdbcTemplate.query("""
                        select dining_place_id, university_id, dining_place_name, dining_place_type, is_active
                        from dining_place
                        where university_id = ?
                        order by dining_place_type, dining_place_name
                        """,
                (rs, rowNum) -> new DiningPlaceItem(
                        rs.getLong("dining_place_id"),
                        rs.getLong("university_id"),
                        rs.getString("dining_place_name"),
                        rs.getString("dining_place_type"),
                        rs.getBoolean("is_active")
                ),
                universityId
        );
        return new DiningPlaceListResponse(items);
    }

    public DailyMenuResponse dailyMenu(long universityId, LocalDate date, String mealType) {
        String mealTypeCode = normalizeMealType(mealType);
        List<MenuOptionRow> rows = menuOptionRows(universityId, date, mealTypeCode);
        Map<Long, DiningPlaceAccumulator> grouped = new LinkedHashMap<>();
        for (MenuOptionRow row : rows) {
            grouped.computeIfAbsent(row.diningPlaceId(), ignored -> new DiningPlaceAccumulator(
                    row.diningPlaceId(),
                    row.diningPlaceName(),
                    row.diningPlaceType()
            )).options().add(new MenuOptionSummary(
                    row.optionId(),
                    row.categoryCode(),
                    row.categoryName(),
                    row.optionName(),
                    row.nutrients()
            ));
        }
        List<DiningPlaceMenu> diningPlaces = grouped.values()
                .stream()
                .map(accumulator -> new DiningPlaceMenu(
                        accumulator.diningPlaceId(),
                        accumulator.diningPlaceName(),
                        accumulator.diningPlaceType(),
                        accumulator.options()
                ))
                .toList();
        return new DailyMenuResponse(universityId, date, mealTypeCode, diningPlaces);
    }

    public MenuCompareResponse compare(long userId, long universityId, LocalDate date, String mealType, Long studentOptionId) {
        String mealTypeCode = normalizeMealType(mealType);
        if (studentOptionId != null) {
            assertStudentOptionCanCompare(universityId, date, mealTypeCode, studentOptionId);
        }
        List<MenuOptionCompareItem> items = menuOptionRows(universityId, date, mealTypeCode)
                .stream()
                .filter(row -> studentOptionId == null
                        || "DORMITORY".equals(row.diningPlaceType())
                        || row.optionId().equals(studentOptionId))
                .map(row -> new MenuOptionCompareItem(
                        row.optionId(),
                        row.diningPlaceName(),
                        row.diningPlaceType(),
                        row.categoryCode(),
                        row.categoryName(),
                        row.optionName(),
                        row.nutrients(),
                        allergyWarnings(userId, row.optionId())
                ))
                .toList();
        return new MenuCompareResponse(universityId, date, mealTypeCode, studentOptionId, items);
    }

    public void assertUserCanCompare(long userId, long universityId) {
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
            throw DomainException.badRequest("SCHOOL_EMAIL_USER_REQUIRED", "식당 메뉴 비교는 학교 이메일로 인증된 사용자만 이용할 수 있습니다.");
        }
        if (selectedUniversityId.longValue() != universityId) {
            throw DomainException.badRequest("UNIVERSITY_SELECTION_MISMATCH", "선택한 대학교의 식당 메뉴만 비교할 수 있습니다.");
        }
    }

    private List<MenuOptionRow> menuOptionRows(long universityId, LocalDate date, String mealTypeCode) {
        return jdbcTemplate.query("""
                        select dp.dining_place_id,
                               dp.dining_place_name,
                               dp.dining_place_type,
                               o.option_id,
                               c.category_code,
                               c.category_name,
                               o.option_name,
                               coalesce(sum(case when n.nutrient_code = 'CALORIES_KCAL' then v.amount_per_100g * mi.amount_g / 100 end), 0) as calories_kcal,
                               coalesce(sum(case when n.nutrient_code = 'CARB_G' then v.amount_per_100g * mi.amount_g / 100 end), 0) as carb_g,
                               coalesce(sum(case when n.nutrient_code = 'PROTEIN_G' then v.amount_per_100g * mi.amount_g / 100 end), 0) as protein_g,
                               coalesce(sum(case when n.nutrient_code = 'FAT_G' then v.amount_per_100g * mi.amount_g / 100 end), 0) as fat_g,
                               coalesce(sum(case when n.nutrient_code = 'SUGAR_G' then v.amount_per_100g * mi.amount_g / 100 end), 0) as sugar_g,
                               coalesce(sum(case when n.nutrient_code = 'SODIUM_MG' then v.amount_per_100g * mi.amount_g / 100 end), 0) as sodium_mg
                        from cafeteria_menu m
                        join dining_place dp on dp.dining_place_id = m.dining_place_id
                        join cafeteria_menu_option o on o.menu_id = m.menu_id
                        left join menu_category c on c.category_id = o.category_id
                        left join cafeteria_menu_item mi on mi.option_id = o.option_id
                        left join food_nutrient_value v on v.food_id = mi.food_id
                        left join nutrient n on n.nutrient_id = v.nutrient_id
                        where dp.university_id = ?
                          and m.served_date = ?
                          and m.meal_type = ?
                          and dp.is_active = true
                          and o.is_available = true
                        group by dp.dining_place_id,
                                 dp.dining_place_name,
                                 dp.dining_place_type,
                                 o.option_id,
                                 c.category_code,
                                 c.category_name,
                                 o.option_name,
                                 c.sort_order
                        order by case when dp.dining_place_type = 'DORMITORY' then 0 else 1 end,
                                 dp.dining_place_name,
                                 c.sort_order,
                                 o.option_name
                        """,
                (rs, rowNum) -> new MenuOptionRow(
                        rs.getLong("dining_place_id"),
                        rs.getString("dining_place_name"),
                        rs.getString("dining_place_type"),
                        rs.getLong("option_id"),
                        rs.getString("category_code"),
                        rs.getString("category_name"),
                        rs.getString("option_name"),
                        NutrientTotals.from(rs)
                ),
                universityId,
                date,
                mealTypeCode
        );
    }

    private void assertStudentOptionCanCompare(long universityId, LocalDate date, String mealTypeCode, long studentOptionId) {
        Integer count = jdbcTemplate.queryForObject("""
                        select count(*)
                        from cafeteria_menu_option o
                        join cafeteria_menu m on m.menu_id = o.menu_id
                        join dining_place dp on dp.dining_place_id = m.dining_place_id
                        where o.option_id = ?
                          and dp.university_id = ?
                          and dp.dining_place_type = 'STUDENT'
                          and dp.is_active = true
                          and o.is_available = true
                          and m.served_date = ?
                          and m.meal_type = ?
                        """,
                Integer.class,
                studentOptionId,
                universityId,
                date,
                mealTypeCode
        );
        if (count == null || count == 0) {
            throw DomainException.badRequest(
                    "STUDENT_MENU_OPTION_INVALID",
                    "studentOptionId는 해당 날짜/끼니의 학생식당 메뉴 옵션이어야 합니다."
            );
        }
    }

    private String normalizeMealType(String mealType) {
        String normalized = mealType == null ? "" : mealType.trim().toUpperCase(Locale.ROOT);
        if (!List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK").contains(normalized)) {
            throw DomainException.badRequest("MEAL_TYPE_INVALID", "mealType은 BREAKFAST, LUNCH, DINNER, SNACK 중 하나여야 합니다.");
        }
        return normalized;
    }

    private List<MenuAllergyWarning> allergyWarnings(long userId, long optionId) {
        List<MenuItemForWarning> menuItems = jdbcTemplate.query("""
                        select mi.food_id, mi.raw_item_name
                        from cafeteria_menu_item mi
                        where mi.option_id = ?
                        order by mi.menu_item_id
                        """,
                (rs, rowNum) -> new MenuItemForWarning(
                        rs.getObject("food_id", Long.class),
                        rs.getString("raw_item_name")
                ),
                optionId
        );
        if (menuItems.isEmpty()) {
            return List.of();
        }

        Map<String, MenuAllergyWarning> warnings = new LinkedHashMap<>();
        List<UserFoodAllergy> foodAllergies = userFoodAllergies(userId);
        List<UserIngredientKeyword> ingredientKeywords = userIngredientKeywords(userId);

        for (MenuItemForWarning item : menuItems) {
            for (UserFoodAllergy allergy : foodAllergies) {
                if (item.foodId() != null && item.foodId().equals(allergy.foodId())) {
                    addWarning(warnings, new MenuAllergyWarning(
                            "FOOD_MATCH",
                            allergy.foodName(),
                            item.rawItemName(),
                            "FOOD",
                            allergyWarningMessage(allergy.foodName())
                    ));
                }
            }

            String normalizedRawName = normalizeForMatch(item.rawItemName());
            for (UserIngredientKeyword keyword : ingredientKeywords) {
                if (!keyword.normalizedKeyword().isBlank() && normalizedRawName.contains(keyword.normalizedKeyword())) {
                    addWarning(warnings, new MenuAllergyWarning(
                            "POSSIBLE_INGREDIENT_NAME_MATCH",
                            keyword.allergyName(),
                            item.rawItemName(),
                            keyword.source(),
                            allergyWarningMessage(keyword.allergyName())
                    ));
                }
            }

            if (item.foodId() != null) {
                for (FoodIngredientMatch match : foodIngredientMatches(userId, item.foodId())) {
                    addWarning(warnings, new MenuAllergyWarning(
                            "FOOD_INGREDIENT_MATCH",
                            match.allergyName(),
                            match.ingredientName(),
                            "FOOD_INGREDIENT",
                            allergyWarningMessage(match.allergyName())
                    ));
                }
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

    private void addWarning(Map<String, MenuAllergyWarning> warnings, MenuAllergyWarning warning) {
        warnings.putIfAbsent(
                warning.warningType() + "|" + warning.allergyName() + "|" + warning.matchedText(),
                warning
        );
    }

    private String normalizeForMatch(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_\\-()\\[\\]{}]", "");
    }

    private record MenuOptionRow(
            Long diningPlaceId,
            String diningPlaceName,
            String diningPlaceType,
            Long optionId,
            String categoryCode,
            String categoryName,
            String optionName,
            NutrientTotals nutrients
    ) {
    }

    private record DiningPlaceAccumulator(
            Long diningPlaceId,
            String diningPlaceName,
            String diningPlaceType,
            List<MenuOptionSummary> options
    ) {
        DiningPlaceAccumulator(Long diningPlaceId, String diningPlaceName, String diningPlaceType) {
            this(diningPlaceId, diningPlaceName, diningPlaceType, new ArrayList<>());
        }
    }

    private record MenuItemForWarning(Long foodId, String rawItemName) {
    }

    private record UserFoodAllergy(Long foodId, String foodName) {
    }

    private record UserIngredientKeyword(String allergyName, String normalizedKeyword, String source) {
    }

    private record FoodIngredientMatch(String allergyName, String ingredientName) {
    }

    private record UserUniversity(Long universityId) {
    }
}
