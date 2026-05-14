package com.database2026.backend.menu;

import com.database2026.backend.common.DomainException;
import com.database2026.backend.menu.MenuDtos.InhaMenuCrawlResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InhaMenuCrawlerService {

    private static final URI INHA_STUDENT_MENU_URI = URI.create("https://www.inha.ac.kr/kr/1072/subview.do");
    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile("(?is)<tr\\b[^>]*>(.*?)</tr>");
    private static final Pattern TABLE_CELL_PATTERN = Pattern.compile("(?is)<t[dh]\\b[^>]*>(.*?)</t[dh]>");
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{1,2})/(\\d{1,2})");
    private static final Pattern KCAL_PATTERN = Pattern.compile("(\\d{2,4})\\s*[kK][cC][aA][lL]");

    private final JdbcTemplate jdbcTemplate;
    private final HttpClient httpClient;

    public InhaMenuCrawlerService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Transactional
    public InhaMenuCrawlResponse crawlStudentDining() {
        String html = fetch(INHA_STUDENT_MENU_URI);
        if (requiresSso(html)) {
            throw DomainException.unauthorized(
                    "INHA_MENU_REQUIRES_AUTH",
                    "인하대 학생식당 메뉴 페이지가 SSO 인증 화면을 반환했습니다. 공개 HTML 접근이 가능한 URL 또는 세션이 필요합니다."
            );
        }

        List<CrawledMenu> menus = parseMenus(html);
        if (menus.isEmpty()) {
            throw DomainException.notFound(
                    "INHA_MENU_NOT_FOUND",
                    "인하대 학생식당 메뉴 표를 찾지 못했습니다. 페이지 HTML 구조를 확인해야 합니다."
            );
        }

        long diningPlaceId = diningPlaceId();
        int importedCount = 0;
        for (CrawledMenu menu : menus) {
            long menuId = upsertMenu(diningPlaceId, menu);
            long optionId = upsertMenuOption(menuId, menu);
            jdbcTemplate.update("delete from cafeteria_menu_item where option_id = ?", optionId);
            for (String rawItemName : splitMenuItems(menu.optionName())) {
                Long foodId = matchedFoodId(rawItemName);
                jdbcTemplate.update("""
                        insert into cafeteria_menu_item (option_id, food_id, raw_item_name, amount_g)
                        values (?, ?, ?, ?)
                        """, optionId, foodId, rawItemName, BigDecimal.valueOf(100));
            }
            importedCount++;
        }

        return new InhaMenuCrawlResponse(importedCount, List.of());
    }

    private String fetch(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "text/html,application/xhtml+xml")
                .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                throw new DomainException(
                        HttpStatus.BAD_GATEWAY,
                        "INHA_MENU_FETCH_FAILED",
                        "인하대 학생식당 메뉴 페이지 요청에 실패했습니다. status=" + response.statusCode()
                );
            }
            return new String(response.body(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new DomainException(HttpStatus.BAD_GATEWAY, "INHA_MENU_FETCH_FAILED", "인하대 학생식당 메뉴 페이지를 가져오지 못했습니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DomainException(HttpStatus.BAD_GATEWAY, "INHA_MENU_FETCH_INTERRUPTED", "인하대 학생식당 메뉴 요청이 중단되었습니다.");
        }
    }

    private boolean requiresSso(String html) {
        String normalized = html.toLowerCase(Locale.ROOT);
        return normalized.contains("/sso/ssologin.do")
                || normalized.contains("failurecause")
                || normalized.contains("unauthorized");
    }

    private List<CrawledMenu> parseMenus(String html) {
        List<List<String>> rows = tableRows(html);
        Map<Integer, LocalDate> dateColumns = new LinkedHashMap<>();
        String currentMealType = null;
        List<CrawledMenu> menus = new ArrayList<>();

        for (List<String> row : rows) {
            if (dateColumns.isEmpty()) {
                dateColumns.putAll(dateColumns(row));
                continue;
            }
            if (row.isEmpty()) {
                continue;
            }

            String leading = row.getFirst();
            Optional<String> mealType = mealType(leading);
            if (mealType.isPresent()) {
                currentMealType = mealType.get();
            }
            if (currentMealType == null) {
                continue;
            }

            String categoryLabel = categoryLabel(row, leading, currentMealType);
            String categoryCode = categoryCode(categoryLabel, currentMealType);
            for (Map.Entry<Integer, LocalDate> entry : dateColumns.entrySet()) {
                int cellIndex = entry.getKey();
                if (cellIndex >= row.size()) {
                    continue;
                }
                String raw = row.get(cellIndex);
                String optionName = optionName(raw);
                if (optionName.isBlank()) {
                    continue;
                }
                menus.add(new CrawledMenu(
                        entry.getValue(),
                        currentMealType,
                        optionName,
                        categoryCode,
                        categoryLabel,
                        calories(raw).orElse(null)
                ));
            }
        }
        return menus;
    }

    private List<List<String>> tableRows(String html) {
        List<List<String>> rows = new ArrayList<>();
        Matcher rowMatcher = TABLE_ROW_PATTERN.matcher(html);
        while (rowMatcher.find()) {
            List<String> cells = new ArrayList<>();
            Matcher cellMatcher = TABLE_CELL_PATTERN.matcher(rowMatcher.group(1));
            while (cellMatcher.find()) {
                String text = cellText(cellMatcher.group(1));
                if (!text.isBlank()) {
                    cells.add(text);
                }
            }
            if (!cells.isEmpty()) {
                rows.add(cells);
            }
        }
        return rows;
    }

    private Map<Integer, LocalDate> dateColumns(List<String> row) {
        Map<Integer, LocalDate> columns = new LinkedHashMap<>();
        int year = Year.now().getValue();
        for (int i = 0; i < row.size(); i++) {
            Matcher matcher = DATE_PATTERN.matcher(row.get(i));
            if (matcher.find()) {
                int month = Integer.parseInt(matcher.group(1));
                int day = Integer.parseInt(matcher.group(2));
                columns.put(i, LocalDate.of(year, month, day));
            }
        }
        return columns.size() >= 2 ? columns : Map.of();
    }

    private Optional<String> mealType(String text) {
        if (text.contains("아침") || text.contains("조식")) {
            return Optional.of("BREAKFAST");
        }
        if (text.contains("점심") || text.contains("중식")) {
            return Optional.of("LUNCH");
        }
        if (text.contains("저녁") || text.contains("석식")) {
            return Optional.of("DINNER");
        }
        if (text.contains("간식") || text.contains("식간")) {
            return Optional.of("SNACK");
        }
        return Optional.empty();
    }

    private String categoryLabel(List<String> row, String leading, String mealType) {
        if ("DINNER".equals(mealType)) {
            return "석식";
        }
        String normalizedLeading = normalizeCategoryText(leading);
        if (normalizedLeading.contains("한상한담")) {
            return "한상한담";
        }
        if (normalizedLeading.contains("oneplate")) {
            return "ONE PLATE";
        }
        if (normalizedLeading.contains("noodle") || normalizedLeading.contains("누들")) {
            return "Noodle";
        }
        if (normalizedLeading.contains("셀프라면")) {
            return "셀프라면";
        }
        if (List.of("A", "B").contains(leading)) {
            return leading;
        }
        if (row.size() > 1) {
            String second = row.get(1);
            String normalizedSecond = normalizeCategoryText(second);
            if (normalizedSecond.contains("한상한담")) {
                return "한상한담";
            }
            if (normalizedSecond.contains("oneplate")) {
                return "ONE PLATE";
            }
            if (normalizedSecond.contains("noodle") || normalizedSecond.contains("누들")) {
                return "Noodle";
            }
            if (normalizedSecond.contains("셀프라면")) {
                return "셀프라면";
            }
            if (List.of("A", "B").contains(second)) {
                return second;
            }
        }
        if (leading.contains("간편식")) {
            return "간편식";
        }
        if (leading.contains("라면")) {
            return "셀프라면";
        }
        return "학생식당";
    }

    private String categoryCode(String categoryLabel, String mealType) {
        if ("DINNER".equals(mealType)) {
            return "STUDENT_DINNER";
        }
        String normalized = normalizeCategoryText(categoryLabel);
        if (normalized.contains("한상한담")) {
            return "STUDENT_HANSANG";
        }
        if (normalized.contains("oneplate")) {
            return "STUDENT_ONE_PLATE";
        }
        if (normalized.contains("noodle") || normalized.contains("누들")) {
            return "STUDENT_NOODLE";
        }
        if (normalized.contains("셀프라면") || normalized.contains("라면")) {
            return "STUDENT_SELF_RAMEN";
        }
        if (normalized.contains("간편식")) {
            return "STUDENT_SIMPLE";
        }
        return "STUDENT_CRAWLED";
    }

    private String normalizeCategoryText(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-]", "");
    }

    private String optionName(String raw) {
        String normalized = raw
                .replaceAll("(?i)\\d{2,4}\\s*kcal", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.isBlank()
                || normalized.contains("운영시간")
                || normalized.contains("원산지")
                || normalized.contains("알레르기")) {
            return "";
        }
        if (normalized.length() > 255) {
            return normalized.substring(0, 255);
        }
        return normalized;
    }

    private Optional<Integer> calories(String text) {
        Matcher matcher = KCAL_PATTERN.matcher(text);
        if (matcher.find()) {
            return Optional.of(Integer.parseInt(matcher.group(1)));
        }
        return Optional.empty();
    }

    private String cellText(String html) {
        return html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?is)<script\\b[^>]*>.*?</script>", "")
                .replaceAll("(?is)<style\\b[^>]*>.*?</style>", "")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n+", " ")
                .trim();
    }

    private long diningPlaceId() {
        return jdbcTemplate.query("""
                        select dp.dining_place_id
                        from dining_place dp
                        join universities u on u.university_id = dp.university_id
                        where u.university_name = '인하대학교'
                          and dp.dining_place_type = 'STUDENT'
                        limit 1
                        """,
                (rs, rowNum) -> rs.getLong("dining_place_id")
        ).stream().findFirst().orElseThrow(() -> DomainException.notFound("INHA_DINING_PLACE_NOT_FOUND", "인하대 학생식당이 DB에 없습니다."));
    }

    private long upsertMenu(long diningPlaceId, CrawledMenu menu) {
        long mealTypeId = mealTypeId(menu.mealType());
        jdbcTemplate.update("""
                insert into cafeteria_menu (dining_place_id, meal_type_id, served_date)
                values (?, ?, ?)
                on duplicate key update crawled_at = current_timestamp
                """, diningPlaceId, mealTypeId, menu.servedDate());
        return jdbcTemplate.query("""
                        select menu_id
                        from cafeteria_menu
                        where dining_place_id = ?
                          and meal_type_id = ?
                          and served_date = ?
                        """,
                (rs, rowNum) -> rs.getLong("menu_id"),
                diningPlaceId,
                mealTypeId,
                menu.servedDate()
        ).getFirst();
    }

    private long upsertMenuOption(long menuId, CrawledMenu menu) {
        Long categoryId = categoryId(menu.categoryCode());
        jdbcTemplate.update("""
                insert into cafeteria_menu_option (menu_id, category_id, option_name, source_label, is_available)
                values (?, ?, ?, ?, true)
                on duplicate key update category_id = values(category_id),
                                        source_label = values(source_label),
                                        is_available = true
                """, menuId, categoryId, menu.optionName(), menu.categoryLabel());
        return jdbcTemplate.query("""
                        select option_id
                        from cafeteria_menu_option
                        where menu_id = ?
                          and option_name = ?
                        """,
                (rs, rowNum) -> rs.getLong("option_id"),
                menuId,
                menu.optionName()
        ).getFirst();
    }

    private List<String> splitMenuItems(String optionName) {
        List<String> items = Pattern.compile("\\s*(/|\\n|,)\\s*")
                .splitAsStream(optionName)
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
        return items.isEmpty() ? List.of(optionName) : items;
    }

    private Long matchedFoodId(String rawItemName) {
        for (String candidate : foodMatchCandidates(rawItemName)) {
            Optional<Long> foodId = jdbcTemplate.query("""
                            select f.food_id
                            from food f
                            where f.source_name = 'MFDS_INTEGRATED'
                              and lower(f.food_name) = lower(?)
                            union
                            select a.food_id
                            from food_alias a
                            join food f on f.food_id = a.food_id
                            where f.source_name = 'MFDS_INTEGRATED'
                              and lower(a.normalized_alias) = lower(?)
                            limit 1
                            """,
                    (rs, rowNum) -> rs.getLong("food_id"),
                    candidate,
                    candidate
            ).stream().findFirst();
            if (foodId.isPresent()) {
                return foodId.get();
            }
        }
        return null;
    }

    private List<String> foodMatchCandidates(String rawItemName) {
        String cleaned = rawItemName
                .replaceAll("\\([^)]*\\)", "")
                .replaceAll("^[가-힣A-Za-z0-9]+\\)", "")
                .trim();
        if (cleaned.isBlank()) {
            return List.of(rawItemName);
        }
        if (cleaned.contains("*")) {
            String primary = cleaned.substring(0, cleaned.indexOf('*')).trim();
            if (!primary.isBlank() && !primary.equals(cleaned)) {
                return List.of(cleaned, primary);
            }
        }
        return List.of(cleaned);
    }

    private long mealTypeId(String mealTypeCode) {
        return jdbcTemplate.query("""
                        select meal_type_id
                        from meal_type
                        where meal_type_code = ?
                        """,
                (rs, rowNum) -> rs.getLong("meal_type_id"),
                mealTypeCode
        ).getFirst();
    }

    private Long categoryId(String categoryCode) {
        return jdbcTemplate.query("""
                        select category_id
                        from menu_category
                        where category_code = ?
                        """,
                (rs, rowNum) -> rs.getLong("category_id"),
                categoryCode
        ).stream().findFirst().orElse(null);
    }

    private record CrawledMenu(
            LocalDate servedDate,
            String mealType,
            String optionName,
            String categoryCode,
            String categoryLabel,
            Integer caloriesKcal
    ) {
    }
}
