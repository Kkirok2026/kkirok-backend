package com.database2026.backend.food;

import com.database2026.backend.common.DomainException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class PublicNutritionApiClient {

    private static final Pattern FIRST_NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final Pattern PORTAL_TABLE_ROW = Pattern.compile("<tr\\s+class=\"contentsTr\"[^>]*>(.*?)</tr>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern PORTAL_TABLE_CELL = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final String API_URL = "https://api.data.go.kr/openapi/tn_pubr_public_nutri_info_api";
    private static final URI PORTAL_STANDARD_SEARCH_URI = URI.create("https://www.data.go.kr/en/tcs/dss/selectStdDataDetailView.do");
    private static final int CONTAINS_SEARCH_PAGE_SIZE = 1000;

    private final String serviceKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration timeout;
    private final int containsScanMaxPages;
    private final int containsScanConcurrency;

    PublicNutritionApiClient(
            @Value("${app.external.public-data.nutrition.service-key:}") String serviceKey,
            @Value("${app.external.public-data.timeout-ms:5000}") long timeoutMs,
            @Value("${app.external.public-data.nutrition.contains-scan-max-pages:700}") int containsScanMaxPages,
            @Value("${app.external.public-data.nutrition.contains-scan-concurrency:12}") int containsScanConcurrency
    ) {
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
        this.objectMapper = new ObjectMapper();
        this.timeout = Duration.ofMillis(timeoutMs);
        this.containsScanMaxPages = Math.max(1, containsScanMaxPages);
        this.containsScanConcurrency = Math.min(Math.max(1, containsScanConcurrency), 30);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
    }

    boolean hasServiceKey() {
        return !serviceKey.isBlank();
    }

    List<NutritionRow> searchFoods(String query, int limit) {
        if (!hasServiceKey()) {
            return List.of();
        }
        JsonNode root = getJson(searchUri(1, limit, query));
        Map<String, NutritionRow> rowsByFoodCode = new LinkedHashMap<>();
        for (JsonNode item : responseItems(root)) {
            NutritionRow row = nutritionRow(item);
            if (row.hasImportableData()) {
                rowsByFoodCode.putIfAbsent(row.foodCode(), row);
            }
        }
        return List.copyOf(rowsByFoodCode.values());
    }

    List<NutritionRow> searchFoodsContaining(String query, int limit) {
        String normalizedQuery = normalizeSearchText(query);
        if (normalizedQuery.isBlank()) {
            return List.of();
        }

        int safeLimit = Math.min(Math.max(limit, 1), 50);
        List<NutritionRow> portalRows = searchPortalFoodsContaining(query, safeLimit);
        if (!portalRows.isEmpty()) {
            return portalRows;
        }
        if (!hasServiceKey()) {
            return List.of();
        }
        Map<String, NutritionRow> rowsByFoodCode = new LinkedHashMap<>();

        JsonNode firstPage = getJson(searchUri(1, CONTAINS_SEARCH_PAGE_SIZE, null));
        int totalPages = containsScanMaxPages;
        int totalCount = totalCount(firstPage);
        if (totalCount > 0) {
            totalPages = Math.min(containsScanMaxPages, (int) Math.ceil((double) totalCount / CONTAINS_SEARCH_PAGE_SIZE));
        }
        collectContainingRows(firstPage, normalizedQuery, rowsByFoodCode, safeLimit);

        for (int firstPageNo = 2; firstPageNo <= totalPages && rowsByFoodCode.size() < safeLimit; firstPageNo += containsScanConcurrency) {
            int lastPageNo = Math.min(totalPages, firstPageNo + containsScanConcurrency - 1);
            for (JsonNode root : getJsonPages(firstPageNo, lastPageNo)) {
                collectContainingRows(root, normalizedQuery, rowsByFoodCode, safeLimit);
                if (rowsByFoodCode.size() >= safeLimit) {
                    break;
                }
            }
        }
        return List.copyOf(rowsByFoodCode.values());
    }

    private List<NutritionRow> searchPortalFoodsContaining(String query, int limit) {
        String body = "publicDataPk=15100064"
                + "&colCondition=FOOD_NM"
                + "&searchKeyword1=" + encode(query);
        HttpRequest request = HttpRequest.newBuilder(PORTAL_STANDARD_SEARCH_URI)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(timeout)
                .header("Accept", "text/html")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .build();
        String html;
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                return List.of();
            }
            html = response.body();
        } catch (IOException exception) {
            return List.of();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return List.of();
        }

        Map<String, NutritionRow> rowsByFoodCode = new LinkedHashMap<>();
        Matcher rowMatcher = PORTAL_TABLE_ROW.matcher(html);
        while (rowMatcher.find() && rowsByFoodCode.size() < limit) {
            NutritionRow row = nutritionRow(tableCells(rowMatcher.group(1)));
            if (row.hasImportableData()) {
                rowsByFoodCode.putIfAbsent(row.foodCode(), row);
            }
        }
        return List.copyOf(rowsByFoodCode.values());
    }

    private List<String> tableCells(String rowHtml) {
        List<String> cells = new ArrayList<>();
        Matcher cellMatcher = PORTAL_TABLE_CELL.matcher(rowHtml);
        while (cellMatcher.find()) {
            cells.add(cleanHtmlCell(cellMatcher.group(1)));
        }
        return cells;
    }

    private String cleanHtmlCell(String html) {
        return html.replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&#034;", "\"")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void collectContainingRows(
            JsonNode root,
            String normalizedQuery,
            Map<String, NutritionRow> rowsByFoodCode,
            int limit
    ) {
        for (JsonNode item : responseItems(root)) {
            NutritionRow row = nutritionRow(item);
            if (row.hasImportableData() && normalizeSearchText(row.foodName()).contains(normalizedQuery)) {
                rowsByFoodCode.putIfAbsent(row.foodCode(), row);
                if (rowsByFoodCode.size() >= limit) {
                    return;
                }
            }
        }
    }

    private NutritionRow nutritionRow(JsonNode node) {
        BigDecimal basisG = positiveOrDefault(amount(node, "nutConSrtrQua", "영양성분함량기준량"), BigDecimal.valueOf(100));
        BigDecimal defaultServingG = positiveOrDefault(amount(node, "foodSize", "식품중량"), basisG);
        return new NutritionRow(
                text(node, "foodCd", "식품코드"),
                text(node, "foodNm", "식품명"),
                defaultServingG,
                amountPer100g(amount(node, "enerc", "에너지(kcal)"), basisG),
                amountPer100g(amount(node, "chocdf", "탄수화물(g)"), basisG),
                amountPer100g(amount(node, "prot", "단백질(g)"), basisG),
                amountPer100g(amount(node, "fatce", "지방(g)"), basisG),
                amountPer100g(amount(node, "sugar", "당류(g)"), basisG),
                amountPer100g(amount(node, "nat", "나트륨(mg)"), basisG)
        );
    }

    private NutritionRow nutritionRow(List<String> cells) {
        if (cells.size() < 34) {
            return new NutritionRow(null, null, null, null, null, null, null, null, null);
        }
        BigDecimal basisG = positiveOrDefault(amount(cells.get(5)), BigDecimal.valueOf(100));
        BigDecimal defaultServingG = positiveOrDefault(amount(cells.get(33)), basisG);
        return new NutritionRow(
                cells.get(0),
                cells.get(1),
                defaultServingG,
                amountPer100g(amount(cells.get(4)), basisG),
                amountPer100g(amount(cells.get(10)), basisG),
                amountPer100g(amount(cells.get(7)), basisG),
                amountPer100g(amount(cells.get(8)), basisG),
                amountPer100g(amount(cells.get(11)), basisG),
                amountPer100g(amount(cells.get(17)), basisG)
        );
    }

    private JsonNode getJson(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(timeout)
                .header("Accept", "application/json")
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new DomainException(HttpStatus.BAD_GATEWAY, "PUBLIC_NUTRITION_API_FAILED", "전국통합식품영양성분정보 표준데이터 API 요청에 실패했습니다. status=" + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (IOException exception) {
            throw new DomainException(HttpStatus.BAD_GATEWAY, "PUBLIC_NUTRITION_API_FAILED", "전국통합식품영양성분정보 표준데이터 API 응답을 처리하지 못했습니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DomainException(HttpStatus.BAD_GATEWAY, "PUBLIC_NUTRITION_API_FAILED", "전국통합식품영양성분정보 표준데이터 API 요청이 중단되었습니다.");
        }
    }

    private List<JsonNode> getJsonPages(int firstPageNo, int lastPageNo) {
        List<CompletableFuture<JsonNode>> futures = new ArrayList<>();
        for (int pageNo = firstPageNo; pageNo <= lastPageNo; pageNo++) {
            futures.add(getJsonAsync(searchUri(pageNo, CONTAINS_SEARCH_PAGE_SIZE, null)));
        }
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException exception) {
            throw unwrapCompletionException(exception);
        }
        List<JsonNode> pages = new ArrayList<>();
        for (CompletableFuture<JsonNode> future : futures) {
            try {
                pages.add(future.join());
            } catch (CompletionException exception) {
                throw unwrapCompletionException(exception);
            }
        }
        return pages;
    }

    private CompletableFuture<JsonNode> getJsonAsync(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(timeout)
                .header("Accept", "application/json")
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    if (response.statusCode() >= 400) {
                        throw new CompletionException(new DomainException(
                                HttpStatus.BAD_GATEWAY,
                                "PUBLIC_NUTRITION_API_FAILED",
                                "전국통합식품영양성분정보 표준데이터 API 요청에 실패했습니다. status=" + response.statusCode()
                        ));
                    }
                    try {
                        return objectMapper.readTree(response.body());
                    } catch (IOException exception) {
                        throw new CompletionException(new DomainException(
                                HttpStatus.BAD_GATEWAY,
                                "PUBLIC_NUTRITION_API_FAILED",
                                "전국통합식품영양성분정보 표준데이터 API 응답을 처리하지 못했습니다."
                        ));
                    }
                });
    }

    private RuntimeException unwrapCompletionException(CompletionException exception) {
        if (exception.getCause() instanceof DomainException domainException) {
            return domainException;
        }
        return exception;
    }

    private List<JsonNode> responseItems(JsonNode root) {
        JsonNode items = root.path("response").path("body").path("items").path("item");
        if (isMissing(items)) {
            items = root.path("response").path("body").path("items");
        }
        if (isMissing(items)) {
            items = root.path("body").path("items").path("item");
        }
        if (isMissing(items)) {
            items = root.path("body").path("items");
        }
        if (isMissing(items)) {
            items = root.path("items").path("item");
        }
        if (isMissing(items)) {
            items = root.path("items");
        }
        if (isMissing(items)) {
            return List.of();
        }
        if (!items.isArray()) {
            return List.of(items);
        }
        List<JsonNode> rows = new ArrayList<>();
        items.forEach(rows::add);
        return rows;
    }

    private boolean isMissing(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() || (node.isArray() && node.isEmpty());
    }

    private int totalCount(JsonNode root) {
        JsonNode totalCount = root.path("response").path("body").path("totalCount");
        if (totalCount.isMissingNode()) {
            totalCount = root.path("body").path("totalCount");
        }
        return totalCount.asInt(0);
    }

    private String text(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.path(fieldName);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = value.asText().trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private BigDecimal amount(JsonNode node, String... fieldNames) {
        String value = text(node, fieldNames);
        return amount(value);
    }

    private BigDecimal amount(String value) {
        if (value == null) {
            return null;
        }
        Matcher matcher = FIRST_NUMBER.matcher(value.replace(",", ""));
        if (!matcher.find()) {
            return null;
        }
        return new BigDecimal(matcher.group());
    }

    private BigDecimal positiveOrDefault(BigDecimal value, BigDecimal defaultValue) {
        return value == null || value.compareTo(BigDecimal.ZERO) <= 0 ? defaultValue : value;
    }

    private BigDecimal amountPer100g(BigDecimal amount, BigDecimal basisG) {
        if (amount == null) {
            return null;
        }
        BigDecimal safeBasisG = positiveOrDefault(basisG, BigDecimal.valueOf(100));
        return amount.multiply(BigDecimal.valueOf(100))
                .divide(safeBasisG, 4, RoundingMode.HALF_UP);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private URI searchUri(int pageNo, int numOfRows, String foodName) {
        String url = API_URL
                + "?serviceKey=" + encode(serviceKey)
                + "&type=json"
                + "&pageNo=" + pageNo
                + "&numOfRows=" + numOfRows;
        if (foodName != null && !foodName.isBlank()) {
            url += "&foodNm=" + encode(foodName);
        }
        return URI.create(url);
    }

    private String normalizeSearchText(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-()/]+", "");
    }

    record NutritionRow(
            String foodCode,
            String foodName,
            BigDecimal defaultServingG,
            BigDecimal caloriesKcal,
            BigDecimal carbG,
            BigDecimal proteinG,
            BigDecimal fatG,
            BigDecimal sugarG,
            BigDecimal sodiumMg
    ) {
        boolean hasImportableData() {
            return foodCode != null && !foodCode.isBlank()
                    && foodName != null && !foodName.isBlank()
                    && (caloriesKcal != null || carbG != null || proteinG != null || fatG != null || sugarG != null || sodiumMg != null);
        }
    }
}
