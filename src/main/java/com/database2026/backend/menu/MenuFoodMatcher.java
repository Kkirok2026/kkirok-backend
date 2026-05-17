package com.database2026.backend.menu;

import com.database2026.backend.common.DomainException;
import com.database2026.backend.food.FoodService;
import com.database2026.backend.menu.MenuItemMatchSupport.FoodCandidate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MenuFoodMatcher {

    private static final int MFDS_MENU_SEARCH_LIMIT = 10;

    private final JdbcTemplate jdbcTemplate;
    private final FoodService foodService;
    private final Set<String> remoteLookupMisses = ConcurrentHashMap.newKeySet();

    public MenuFoodMatcher(JdbcTemplate jdbcTemplate, FoodService foodService) {
        this.jdbcTemplate = jdbcTemplate;
        this.foodService = foodService;
    }

    public List<String> splitMenuItems(String optionName) {
        return MenuItemMatchSupport.splitMenuItems(optionName);
    }

    public void resolveMissingMenuItems(long universityId, LocalDate date, String mealTypeCode) {
        for (MenuItemRow item : missingMenuItems(universityId, date, mealTypeCode)) {
            Long foodId = matchedFoodId(item.rawItemName());
            if (foodId == null) {
                continue;
            }
            jdbcTemplate.update("""
                    update cafeteria_menu_item
                    set food_id = ?,
                        amount_g = ?
                    where menu_item_id = ?
                      and food_id is null
                    """, foodId, servingAmount(foodId), item.menuItemId());
        }
    }

    public Long matchedFoodId(String rawItemName) {
        Long localFoodId = matchLocalFood(rawItemName);
        if (localFoodId != null) {
            return localFoodId;
        }
        if (!foodService.hasMfdsNutritionServiceKey()) {
            return null;
        }

        String lookupKey = MenuItemMatchSupport.normalize(rawItemName);
        if (lookupKey.isBlank() || remoteLookupMisses.contains(lookupKey)) {
            return null;
        }

        int importedCount;
        try {
            importedCount = foodService.importMfdsFoods(MenuItemMatchSupport.searchQueries(rawItemName), MFDS_MENU_SEARCH_LIMIT);
        } catch (DomainException exception) {
            if ("MFDS_NUTRITION_API_FAILED".equals(exception.code())) {
                return null;
            }
            throw exception;
        }
        if (importedCount == 0) {
            remoteLookupMisses.add(lookupKey);
            return null;
        }

        Long importedFoodId = matchLocalFood(rawItemName);
        if (importedFoodId == null) {
            remoteLookupMisses.add(lookupKey);
            return null;
        }
        foodService.addFoodAlias(importedFoodId, rawItemName, "MENU_ITEM", 300);
        return importedFoodId;
    }

    public BigDecimal servingAmount(Long foodId) {
        if (foodId == null) {
            return BigDecimal.valueOf(100);
        }
        return defaultServingByFoodId().getOrDefault(foodId, BigDecimal.valueOf(100));
    }

    private Long matchLocalFood(String rawItemName) {
        return MenuItemMatchSupport.matchFoodId(rawItemName, foodCandidates());
    }

    private List<MenuItemRow> missingMenuItems(long universityId, LocalDate date, String mealTypeCode) {
        return jdbcTemplate.query("""
                        select mi.menu_item_id, mi.raw_item_name
                        from cafeteria_menu_item mi
                        join cafeteria_menu_option o on o.option_id = mi.option_id
                        join cafeteria_menu m on m.menu_id = o.menu_id
                        join dining_place dp on dp.dining_place_id = m.dining_place_id
                        where dp.university_id = ?
                          and dp.is_active = true
                          and o.is_available = true
                          and m.served_date = ?
                          and m.meal_type = ?
                          and mi.food_id is null
                        order by mi.menu_item_id
                        """,
                (rs, rowNum) -> new MenuItemRow(
                        rs.getLong("menu_item_id"),
                        rs.getString("raw_item_name")
                ),
                universityId,
                date,
                mealTypeCode
        );
    }

    private List<FoodCandidate> foodCandidates() {
        return jdbcTemplate.query("""
                        select f.food_id, f.food_name as label, 100 as priority
                        from food f
                        where not exists (
                            select 1
                            from user_custom_food ucf
                            where ucf.food_id = f.food_id
                        )
                        union all
                        select a.food_id, a.alias_name as label, coalesce(a.priority, 0) + 200 as priority
                        from food_alias a
                        join food f on f.food_id = a.food_id
                        where not exists (
                            select 1
                            from user_custom_food ucf
                            where ucf.food_id = f.food_id
                        )
                        """,
                (rs, rowNum) -> new FoodCandidate(
                        rs.getLong("food_id"),
                        rs.getString("label"),
                        MenuItemMatchSupport.normalize(rs.getString("label")),
                        rs.getInt("priority")
                )
        );
    }

    private Map<Long, BigDecimal> defaultServingByFoodId() {
        return jdbcTemplate.query("""
                        select f.food_id, f.default_serving_g
                        from food f
                        where not exists (
                            select 1
                            from user_custom_food ucf
                            where ucf.food_id = f.food_id
                        )
                        """,
                (rs, rowNum) -> Map.entry(
                        rs.getLong("food_id"),
                        rs.getBigDecimal("default_serving_g")
                )
        ).stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private record MenuItemRow(long menuItemId, String rawItemName) {
    }
}
