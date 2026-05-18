package com.database2026.backend.menu;

import com.database2026.backend.common.DomainException;
import com.database2026.backend.menu.MenuDtos.InhaMenuCrawlResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InhaMenuCrawlerService {

    private static final URI INHA_STUDENT_MENU_PAGE_URI = URI.create("https://www.inha.ac.kr/kr/1072/subview.do");
    private static final URI INHA_STUDENT_MENU_URI = URI.create("https://www.inha.ac.kr/diet/kr/2/view.do");
    private static final DateTimeFormatter INHA_MENU_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final String INHA_MENU_USER_AGENT = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Mobile/15E148 Safari/604.1";
    private static final Pattern DAY_HEADING_PATTERN = Pattern.compile("(?is)<h2\\b[^>]*>(.*?\\d{1,2}[./]\\d{1,2}.*?)</h2>");
    private static final Pattern MEAL_HEADING_PATTERN = Pattern.compile("(?is)<h3\\b[^>]*>(.*?)</h3>");
    private static final Pattern TABLE_ROW_PATTERN = Pattern.compile("(?is)<tr\\b[^>]*>(.*?)</tr>");
    private static final Pattern TABLE_CELL_PATTERN = Pattern.compile("(?is)<t[dh]\\b[^>]*>(.*?)</t[dh]>");
    private static final Pattern WEEK_MONDAY_PATTERN = Pattern.compile("name=[\"']monday[\"'][^>]*value=[\"'](\\d{4})\\.(\\d{1,2})\\.(\\d{1,2})[\"']");
    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{1,2})/(\\d{1,2})");
    private static final Pattern DAY_HEADING_DATE_PATTERN = Pattern.compile("(\\d{1,2})[./](\\d{1,2})\\.?");
    private static final Pattern KCAL_PATTERN = Pattern.compile("(\\d{2,4})\\s*[kK][cC][aA][lL]");
    private static final Pattern FIRST_NUMBER_PATTERN = Pattern.compile("\\d+");

    private final JdbcTemplate jdbcTemplate;
    private final MenuFoodMatcher menuFoodMatcher;
    private final HttpClient httpClient;
    private final String inhaMenuCookie;
    private final String inhaMenuLayout;
    private final boolean matchFoodOnCrawl;
    private final Duration timeout;

    public InhaMenuCrawlerService(
            JdbcTemplate jdbcTemplate,
            MenuFoodMatcher menuFoodMatcher,
            @Value("${inha.menu.cookie:}") String inhaMenuCookie,
            @Value("${inha.menu.layout:J3sRfz6SuHMYDlWbLXHbgQ==}") String inhaMenuLayout,
            @Value("${inha.menu.match-food-on-crawl:false}") boolean matchFoodOnCrawl,
            @Value("${inha.menu.timeout-ms:5000}") long timeoutMs
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.menuFoodMatcher = menuFoodMatcher;
        this.inhaMenuCookie = inhaMenuCookie == null ? "" : inhaMenuCookie.trim();
        this.inhaMenuLayout = inhaMenuLayout == null ? "" : inhaMenuLayout.trim();
        this.matchFoodOnCrawl = matchFoodOnCrawl;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Transactional
    public InhaMenuCrawlResponse crawlStudentDining() {
        return crawlStudentDining(LocalDate.now());
    }

    @Transactional
    public InhaMenuCrawlResponse crawlStudentDining(LocalDate targetDate) {
        String html = fetchStudentMenuPage();
        boolean requiresAuth = requiresSso(html);
        List<CrawledMenu> menus = requiresAuth ? List.of() : parseMenus(html);

        if (menus.isEmpty()) {
            String fallbackHtml = fetchDietView(targetDate);
            requiresAuth = requiresAuth || requiresSso(fallbackHtml);
            if (!requiresSso(fallbackHtml)) {
                menus = parseMenus(fallbackHtml);
            }
        }

        if (menus.isEmpty()) {
            if (requiresAuth) {
                throw DomainException.unauthorized(
                        "INHA_MENU_REQUIRES_AUTH",
                        "인하대 학생식당 메뉴 페이지가 SSO 인증 화면을 반환했습니다. INHA_MENU_COOKIE에 로그인 세션 쿠키가 필요합니다."
                );
            }
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
            for (String rawItemName : menuFoodMatcher.splitMenuItems(menu.optionName())) {
                Long foodId = matchFoodOnCrawl ? menuFoodMatcher.matchedFoodId(rawItemName) : null;
                jdbcTemplate.update("""
                        insert into cafeteria_menu_item (option_id, food_id, raw_item_name, amount_g)
                        values (?, ?, ?, ?)
                        """, optionId, foodId, rawItemName, menuFoodMatcher.servingAmount(foodId));
            }
            importedCount++;
        }

        return new InhaMenuCrawlResponse(importedCount, List.of());
    }

    private String fetchStudentMenuPage() {
        return send(browserRequest(INHA_STUDENT_MENU_PAGE_URI)
                .GET()
                .setHeader("Referer", "https://idp.inha.ac.kr:8443/")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "same-site")
                .header("Upgrade-Insecure-Requests", "1"));
    }

    private String fetchDietView(LocalDate targetDate) {
        String body = formBody(targetDate);
        return send(browserRequest(INHA_STUDENT_MENU_URI)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .setHeader("Referer", INHA_STUDENT_MENU_PAGE_URI.toString()));
    }

    private HttpRequest.Builder browserRequest(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("User-Agent", INHA_MENU_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache");
    }

    private String send(HttpRequest.Builder requestBuilder) {
        if (!inhaMenuCookie.isBlank()) {
            requestBuilder.header("Cookie", inhaMenuCookie);
        }
        HttpRequest request = requestBuilder.build();
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

    private String formBody(LocalDate targetDate) {
        LocalDate monday = targetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return "layout=" + encode(inhaMenuLayout)
                + "&monday=" + encode(INHA_MENU_DATE_FORMAT.format(monday))
                + "&week=";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean requiresSso(String html) {
        String normalized = html.toLowerCase(Locale.ROOT);
        return normalized.contains("/sso/ssologin.do")
                || normalized.contains("failurecause")
                || normalized.contains("unauthorized");
    }

    private List<CrawledMenu> parseMenus(String html) {
        List<CrawledMenu> dietViewMenus = parseDietViewMenus(html);
        if (!dietViewMenus.isEmpty()) {
            return dietViewMenus;
        }

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
                        calories(raw).orElse(null),
                        null
                ));
            }
        }
        return menus;
    }

    private List<CrawledMenu> parseDietViewMenus(String html) {
        List<HeadingBlock> blocks = headingBlocks(html);
        if (blocks.isEmpty()) {
            return List.of();
        }

        LocalDate baseMonday = baseMonday(html);
        List<CrawledMenu> menus = new ArrayList<>();
        for (HeadingBlock block : blocks) {
            LocalDate servedDate = dateFromHeading(block.heading(), baseMonday).orElse(null);
            String mealHeading = mealHeading(block.html()).orElse("");
            Optional<String> mealType = mealType(mealHeading);
            if (servedDate == null || mealType.isEmpty()) {
                continue;
            }

            for (List<String> rawCells : rawTableRows(block.html())) {
                if (rawCells.size() < 3) {
                    continue;
                }

                String categoryLabel = cellText(rawCells.get(0));
                String optionName = optionNameFromMenuCell(rawCells.get(1));
                Integer price = price(cellText(rawCells.get(2))).orElse(null);
                if (categoryLabel.isBlank()
                        || "구분".equals(categoryLabel)
                        || optionName.isBlank()
                        || "메뉴".equals(optionName)) {
                    continue;
                }

                menus.add(new CrawledMenu(
                        servedDate,
                        mealType.get(),
                        optionName,
                        categoryCode(categoryLabel, mealType.get()),
                        categoryLabel,
                        null,
                        price
                ));
            }
        }
        return menus;
    }

    private List<HeadingBlock> headingBlocks(String html) {
        Matcher matcher = DAY_HEADING_PATTERN.matcher(html);
        List<HeadingMatch> headings = new ArrayList<>();
        while (matcher.find()) {
            headings.add(new HeadingMatch(cellText(matcher.group(1)), matcher.end(), matcher.start()));
        }
        if (headings.isEmpty()) {
            return List.of();
        }

        List<HeadingBlock> blocks = new ArrayList<>();
        for (int i = 0; i < headings.size(); i++) {
            HeadingMatch heading = headings.get(i);
            int blockEnd = i + 1 < headings.size() ? headings.get(i + 1).start() : html.length();
            blocks.add(new HeadingBlock(heading.text(), html.substring(heading.end(), blockEnd)));
        }
        return blocks;
    }

    private Optional<String> mealHeading(String blockHtml) {
        Matcher matcher = MEAL_HEADING_PATTERN.matcher(blockHtml);
        return matcher.find() ? Optional.of(cellText(matcher.group(1))) : Optional.empty();
    }

    private List<List<String>> rawTableRows(String html) {
        List<List<String>> rows = new ArrayList<>();
        Matcher rowMatcher = TABLE_ROW_PATTERN.matcher(html);
        while (rowMatcher.find()) {
            List<String> cells = new ArrayList<>();
            Matcher cellMatcher = TABLE_CELL_PATTERN.matcher(rowMatcher.group(1));
            while (cellMatcher.find()) {
                cells.add(cellMatcher.group(1));
            }
            if (!cells.isEmpty()) {
                rows.add(cells);
            }
        }
        return rows;
    }

    private LocalDate baseMonday(String html) {
        Matcher matcher = WEEK_MONDAY_PATTERN.matcher(html);
        if (matcher.find()) {
            return LocalDate.of(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3))
            );
        }
        return LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private Optional<LocalDate> dateFromHeading(String heading, LocalDate baseMonday) {
        Matcher matcher = DAY_HEADING_DATE_PATTERN.matcher(heading);
        if (!matcher.find()) {
            return Optional.empty();
        }

        int month = Integer.parseInt(matcher.group(1));
        int day = Integer.parseInt(matcher.group(2));
        LocalDate date = LocalDate.of(baseMonday.getYear(), month, day);
        if (date.isBefore(baseMonday.minusDays(3))) {
            date = date.plusYears(1);
        } else if (date.isAfter(baseMonday.plusDays(10))) {
            date = date.minusYears(1);
        }
        return Optional.of(date);
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
        if (text.contains("셀프라면")) {
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
        if ("BREAKFAST".equals(mealType)) {
            return "STUDENT_CRAWLED";
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
                || normalized.contains("원산지")) {
            return "";
        }
        if (normalized.length() > 255) {
            return normalized.substring(0, 255);
        }
        return normalized;
    }

    private String optionNameFromMenuCell(String html) {
        String menu = String.join(" / ", cellLines(html));
        return optionName(menu);
    }

    private List<String> cellLines(String html) {
        String text = html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?is)<script\\b[^>]*>.*?</script>", "")
                .replaceAll("(?is)<style\\b[^>]*>.*?</style>", "")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        return Arrays.stream(text.split("\\n+"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private Optional<Integer> price(String text) {
        Matcher matcher = FIRST_NUMBER_PATTERN.matcher(text.replace(",", ""));
        if (matcher.find()) {
            return Optional.of(Integer.parseInt(matcher.group()));
        }
        return Optional.empty();
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
        jdbcTemplate.update("""
                insert into cafeteria_menu (dining_place_id, meal_type, served_date)
                values (?, ?, ?)
                on duplicate key update menu_id = menu_id
                """, diningPlaceId, menu.mealType(), menu.servedDate());
        return jdbcTemplate.query("""
                        select menu_id
                        from cafeteria_menu
                        where dining_place_id = ?
                          and meal_type = ?
                          and served_date = ?
                        """,
                (rs, rowNum) -> rs.getLong("menu_id"),
                diningPlaceId,
                menu.mealType(),
                menu.servedDate()
        ).getFirst();
    }

    private long upsertMenuOption(long menuId, CrawledMenu menu) {
        Long categoryId = categoryId(menu.categoryCode());
        jdbcTemplate.update("""
                insert into cafeteria_menu_option (menu_id, category_id, option_name, source_label, is_available, calories_kcal)
                values (?, ?, ?, ?, true, ?)
                on duplicate key update category_id = values(category_id),
                                        source_label = values(source_label),
                                        is_available = true,
                                        calories_kcal = values(calories_kcal)
                """, menuId, categoryId, menu.optionName(), menu.categoryLabel(), menu.caloriesKcal());
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
            Integer caloriesKcal,
            Integer price
    ) {
    }

    private record HeadingMatch(String text, int end, int start) {
    }

    private record HeadingBlock(
            String heading,
            String html
    ) {
    }
}
