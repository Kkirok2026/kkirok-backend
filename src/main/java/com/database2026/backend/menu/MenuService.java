package com.database2026.backend.menu;

import com.database2026.backend.common.DomainException;
import com.database2026.backend.common.NutrientTotals;
import com.database2026.backend.menu.MenuDtos.DailyMenuResponse;
import com.database2026.backend.menu.MenuDtos.DiningPlaceItem;
import com.database2026.backend.menu.MenuDtos.DiningPlaceListResponse;
import com.database2026.backend.menu.MenuDtos.DiningPlaceMenu;
import com.database2026.backend.menu.MenuDtos.MenuCompareResponse;
import com.database2026.backend.menu.MenuDtos.MenuOptionCaloriesUpdateRequest;
import com.database2026.backend.menu.MenuDtos.MenuOptionCompareItem;
import com.database2026.backend.menu.MenuDtos.MenuOptionSummary;
import com.database2026.backend.menu.MenuDtos.UniversityItem;
import com.database2026.backend.menu.MenuDtos.UniversityListResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MenuService {

    private static final Logger log = LoggerFactory.getLogger(MenuService.class);

    private final JdbcTemplate jdbcTemplate;
    private final InhaMenuCrawlerService inhaMenuCrawlerService;
    private final MenuFoodMatcher menuFoodMatcher;
    private volatile boolean inhaStudentCrawlUnavailable;

    public MenuService(JdbcTemplate jdbcTemplate, InhaMenuCrawlerService inhaMenuCrawlerService, MenuFoodMatcher menuFoodMatcher) {
        this.jdbcTemplate = jdbcTemplate;
        this.inhaMenuCrawlerService = inhaMenuCrawlerService;
        this.menuFoodMatcher = menuFoodMatcher;
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
        refreshInhaStudentMenuIfMissing(universityId, date, mealTypeCode);
        menuFoodMatcher.resolveMissingMenuItems(universityId, date, mealTypeCode);
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
        refreshInhaStudentMenuIfMissing(universityId, date, mealTypeCode);
        menuFoodMatcher.resolveMissingMenuItems(universityId, date, mealTypeCode);
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
                        row.nutrients()
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

    @Transactional
    public MenuOptionCompareItem updateOptionCalories(long userId, long optionId, MenuOptionCaloriesUpdateRequest request) {
        OptionUniversity optionUniversity = optionUniversity(optionId);
        assertUserCanCompare(userId, optionUniversity.universityId());
        if (request.caloriesKcal() == null || request.caloriesKcal().compareTo(BigDecimal.ZERO) < 0) {
            throw DomainException.badRequest("CALORIES_INVALID", "칼로리는 0 이상의 숫자로 입력해 주세요.");
        }
        jdbcTemplate.update("update cafeteria_menu_option set calories_kcal = ? where option_id = ?", request.caloriesKcal(), optionId);
        return menuOptionCompareItem(optionId);
    }

    private List<MenuOptionRow> menuOptionRows(long universityId, LocalDate date, String mealTypeCode) {
        return jdbcTemplate.query("""
                        select dining_place_id,
                               dining_place_name,
                               dining_place_type,
                               option_id,
                               category_code,
                               category_name,
                               option_name,
                               calories_kcal,
                               carb_g,
                               protein_g,
                               fat_g,
                               sugar_g,
                               sodium_mg
                        from v_menu_option_comparison
                        where university_id = ?
                          and served_date = ?
                          and meal_type = ?
                          and dining_place_is_active = true
                          and is_available = true
                        order by case when dining_place_type = 'DORMITORY' then 0 else 1 end,
                                 dining_place_name,
                                 category_sort_order,
                                 option_name
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

    private OptionUniversity optionUniversity(long optionId) {
        return jdbcTemplate.query("""
                        select dp.university_id
                        from cafeteria_menu_option o
                        join cafeteria_menu m on m.menu_id = o.menu_id
                        join dining_place dp on dp.dining_place_id = m.dining_place_id
                        where o.option_id = ?
                          and dp.is_active = true
                          and o.is_available = true
                        """,
                (rs, rowNum) -> new OptionUniversity(rs.getLong("university_id")),
                optionId
        ).stream().findFirst().orElseThrow(() -> DomainException.notFound("MENU_OPTION_NOT_FOUND", "식당 메뉴를 찾을 수 없습니다."));
    }

    private MenuOptionCompareItem menuOptionCompareItem(long optionId) {
        return jdbcTemplate.query("""
                        select dining_place_name,
                               dining_place_type,
                               option_id,
                               category_code,
                               category_name,
                               option_name,
                               calories_kcal,
                               carb_g,
                               protein_g,
                               fat_g,
                               sugar_g,
                               sodium_mg
                        from v_menu_option_comparison
                        where option_id = ?
                        """,
                (rs, rowNum) -> new MenuOptionCompareItem(
                        rs.getLong("option_id"),
                        rs.getString("dining_place_name"),
                        rs.getString("dining_place_type"),
                        rs.getString("category_code"),
                        rs.getString("category_name"),
                        rs.getString("option_name"),
                        NutrientTotals.from(rs)
                ),
                optionId
        ).stream().findFirst().orElseThrow(() -> DomainException.notFound("MENU_OPTION_NOT_FOUND", "식당 메뉴를 찾을 수 없습니다."));
    }

    private void refreshInhaStudentMenuIfMissing(long universityId, LocalDate date, String mealTypeCode) {
        if (inhaStudentCrawlUnavailable || !isInhaUniversity(universityId) || hasStudentMenu(universityId, date, mealTypeCode)) {
            return;
        }

        try {
            inhaMenuCrawlerService.crawlStudentDining(date);
        } catch (DomainException exception) {
            if ("INHA_MENU_REQUIRES_AUTH".equals(exception.code())
                    || "INHA_MENU_FETCH_FAILED".equals(exception.code())
                    || "INHA_MENU_FETCH_INTERRUPTED".equals(exception.code())
                    || "INHA_MENU_NOT_FOUND".equals(exception.code())) {
                inhaStudentCrawlUnavailable = true;
                return;
            }
            throw exception;
        } catch (RuntimeException exception) {
            inhaStudentCrawlUnavailable = true;
            log.warn("인하대 학생식당 메뉴 자동 크롤링 실패. 기존 메뉴 데이터만 반환합니다.", exception);
        }
    }

    private boolean isInhaUniversity(long universityId) {
        Integer count = jdbcTemplate.queryForObject("""
                        select count(*)
                        from universities
                        where university_id = ?
                          and university_name = '인하대학교'
                        """,
                Integer.class,
                universityId
        );
        return count != null && count > 0;
    }

    private boolean hasStudentMenu(long universityId, LocalDate date, String mealTypeCode) {
        Integer count = jdbcTemplate.queryForObject("""
                        select count(*)
                        from cafeteria_menu_option o
                        join cafeteria_menu m on m.menu_id = o.menu_id
                        join dining_place dp on dp.dining_place_id = m.dining_place_id
                        where dp.university_id = ?
                          and dp.dining_place_type = 'STUDENT'
                          and dp.is_active = true
                          and o.is_available = true
                          and m.served_date = ?
                          and m.meal_type = ?
                        """,
                Integer.class,
                universityId,
                date,
                mealTypeCode
        );
        return count != null && count > 0;
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

    private record UserUniversity(Long universityId) {
    }

    private record OptionUniversity(Long universityId) {
    }
}
