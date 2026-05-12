package com.database2026.backend.menu;

import com.database2026.backend.common.DomainException;
import com.database2026.backend.common.NutrientTotals;
import com.database2026.backend.menu.MenuDtos.DailyMenuResponse;
import com.database2026.backend.menu.MenuDtos.DiningPlaceItem;
import com.database2026.backend.menu.MenuDtos.DiningPlaceListResponse;
import com.database2026.backend.menu.MenuDtos.DiningPlaceMenu;
import com.database2026.backend.menu.MenuDtos.MenuCompareResponse;
import com.database2026.backend.menu.MenuDtos.MenuOptionCompareItem;
import com.database2026.backend.menu.MenuDtos.MenuOptionSummary;
import com.database2026.backend.menu.MenuDtos.UniversityItem;
import com.database2026.backend.menu.MenuDtos.UniversityListResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MenuService {

    private final JdbcTemplate jdbcTemplate;

    public MenuService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UniversityListResponse universities() {
        List<UniversityItem> items = new ArrayList<>();
        items.add(new UniversityItem(null, "NONE", "선택 안함"));
        items.addAll(jdbcTemplate.query("""
                        select university_id, university_code, university_name
                        from universities
                        where is_active = true
                        order by university_name
                        """,
                (rs, rowNum) -> new UniversityItem(
                        rs.getLong("university_id"),
                        rs.getString("university_code"),
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
                    row.price(),
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

    public MenuCompareResponse compare(long universityId, LocalDate date, String mealType) {
        String mealTypeCode = normalizeMealType(mealType);
        List<MenuOptionCompareItem> items = menuOptionRows(universityId, date, mealTypeCode)
                .stream()
                .map(row -> new MenuOptionCompareItem(
                        row.optionId(),
                        row.diningPlaceName(),
                        row.diningPlaceType(),
                        row.categoryName(),
                        row.optionName(),
                        row.nutrients()
                ))
                .toList();
        return new MenuCompareResponse(universityId, date, mealTypeCode, items);
    }

    public void assertUserCanCompare(long userId, long universityId) {
        Long selectedUniversityId = jdbcTemplate.query("""
                        select primary_university_id
                        from user_account
                        where user_id = ?
                          and status = 'ACTIVE'
                        """,
                (rs, rowNum) -> (Long) rs.getObject("primary_university_id"),
                userId
        ).stream().findFirst().orElseThrow(() -> DomainException.notFound("USER_NOT_FOUND", "사용자를 찾을 수 없습니다."));
        if (selectedUniversityId == null) {
            throw DomainException.badRequest("UNIVERSITY_SELECTION_REQUIRED", "식당 메뉴 비교를 이용하려면 대학교를 선택해야 합니다.");
        }
        if (selectedUniversityId != universityId) {
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
                               o.price,
                               coalesce(sum(case when n.nutrient_code = 'CALORIES_KCAL' then v.amount_per_100g * mi.amount_g / 100 end), 0) as calories_kcal,
                               coalesce(sum(case when n.nutrient_code = 'CARB_G' then v.amount_per_100g * mi.amount_g / 100 end), 0) as carb_g,
                               coalesce(sum(case when n.nutrient_code = 'PROTEIN_G' then v.amount_per_100g * mi.amount_g / 100 end), 0) as protein_g,
                               coalesce(sum(case when n.nutrient_code = 'FAT_G' then v.amount_per_100g * mi.amount_g / 100 end), 0) as fat_g,
                               coalesce(sum(case when n.nutrient_code = 'SUGAR_G' then v.amount_per_100g * mi.amount_g / 100 end), 0) as sugar_g,
                               coalesce(sum(case when n.nutrient_code = 'SODIUM_MG' then v.amount_per_100g * mi.amount_g / 100 end), 0) as sodium_mg
                        from cafeteria_menu m
                        join dining_place dp on dp.dining_place_id = m.dining_place_id
                        join meal_type mt on mt.meal_type_id = m.meal_type_id
                        join cafeteria_menu_option o on o.menu_id = m.menu_id
                        left join menu_category c on c.category_id = o.category_id
                        left join cafeteria_menu_item mi on mi.option_id = o.option_id
                        left join food_nutrient_value v on v.food_id = mi.food_id
                        left join nutrient n on n.nutrient_id = v.nutrient_id
                        where dp.university_id = ?
                          and m.served_date = ?
                          and mt.meal_type_code = ?
                          and dp.is_active = true
                          and o.is_available = true
                        group by dp.dining_place_id,
                                 dp.dining_place_name,
                                 dp.dining_place_type,
                                 o.option_id,
                                 c.category_code,
                                 c.category_name,
                                 o.option_name,
                                 o.price,
                                 c.sort_order
                        order by dp.dining_place_type desc, dp.dining_place_name, c.sort_order, o.option_name
                        """,
                (rs, rowNum) -> new MenuOptionRow(
                        rs.getLong("dining_place_id"),
                        rs.getString("dining_place_name"),
                        rs.getString("dining_place_type"),
                        rs.getLong("option_id"),
                        rs.getString("category_code"),
                        rs.getString("category_name"),
                        rs.getString("option_name"),
                        (Integer) rs.getObject("price"),
                        NutrientTotals.from(rs)
                ),
                universityId,
                date,
                mealTypeCode
        );
    }

    private String normalizeMealType(String mealType) {
        String normalized = mealType == null ? "" : mealType.trim().toUpperCase(Locale.ROOT);
        if (!List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK").contains(normalized)) {
            throw DomainException.badRequest("MEAL_TYPE_INVALID", "mealType은 BREAKFAST, LUNCH, DINNER, SNACK 중 하나여야 합니다.");
        }
        return normalized;
    }

    private record MenuOptionRow(
            Long diningPlaceId,
            String diningPlaceName,
            String diningPlaceType,
            Long optionId,
            String categoryCode,
            String categoryName,
            String optionName,
            Integer price,
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
}
