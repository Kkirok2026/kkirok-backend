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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class MfdsNutritionApiClient {

    private static final Pattern FIRST_NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final String API_URL = "https://apis.data.go.kr/1471000/FoodNtrCpntDbInfo02/getFoodNtrCpntDbInq02";

    private final String serviceKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration timeout;

    MfdsNutritionApiClient(
            @Value("${app.external.mfds.nutrition.service-key:}") String serviceKey,
            @Value("${app.external.mfds.timeout-ms:5000}") long timeoutMs
    ) {
        this.serviceKey = serviceKey;
        this.objectMapper = new ObjectMapper();
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
    }

    boolean hasServiceKey() {
        return serviceKey != null && !serviceKey.isBlank();
    }

    List<NutritionRow> searchFoods(String query, int limit) {
        if (!hasServiceKey()) {
            return List.of();
        }
        String url = API_URL
                + "?serviceKey=" + encode(serviceKey)
                + "&type=json"
                + "&pageNo=1"
                + "&numOfRows=" + limit
                + "&FOOD_NM_KR=" + encode(query);
        JsonNode root = getJson(URI.create(url));
        return responseItems(root).stream()
                .map(this::nutritionRow)
                .filter(NutritionRow::hasImportableData)
                .toList();
    }

    private NutritionRow nutritionRow(JsonNode node) {
        BigDecimal basisG = positiveOrDefault(amount(node, "NUT_CONSR_STD", "NUTRITION_STANDARD", "SERVING_UNIT"), BigDecimal.valueOf(100));
        BigDecimal defaultServingG = positiveOrDefault(
                amount(node, "DISH_ONE_SERVING", "FOOD_SIZE", "SERVING_SIZE", "NUT_CONSR_STD"),
                basisG
        );
        return new NutritionRow(
                text(node, "FOOD_CD", "FOOD_CODE"),
                text(node, "FOOD_NM_KR", "FOOD_NM", "DESC_KOR", "PRDLST_NM"),
                text(node, "FOOD_CAT1_NM", "FOOD_CAT2_NM", "FOOD_CAT3_NM", "DB_CLASS_NM", "GROUP_NAME", "DATA_CLASS_NM"),
                defaultServingG,
                amountPer100g(amount(node, "AMT_NUM1", "NUTR_CONT1", "ENERGY", "에너지(kcal)"), basisG),
                amountPer100g(amount(node, "AMT_NUM6", "NUTR_CONT2", "CARBOHYDRATE", "탄수화물(g)"), basisG),
                amountPer100g(amount(node, "AMT_NUM3", "NUTR_CONT3", "PROTEIN", "단백질(g)"), basisG),
                amountPer100g(amount(node, "AMT_NUM4", "NUTR_CONT4", "FAT", "지방(g)"), basisG),
                amountPer100g(amount(node, "AMT_NUM7", "NUTR_CONT5", "SUGAR", "당류(g)"), basisG),
                amountPer100g(amount(node, "AMT_NUM13", "NUTR_CONT6", "SODIUM", "나트륨(mg)"), basisG)
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
                throw new DomainException(HttpStatus.BAD_GATEWAY, "MFDS_NUTRITION_API_FAILED", "식품영양성분DB OpenAPI 요청에 실패했습니다. status=" + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (IOException exception) {
            throw new DomainException(HttpStatus.BAD_GATEWAY, "MFDS_NUTRITION_API_FAILED", "식품영양성분DB OpenAPI 응답을 처리하지 못했습니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DomainException(HttpStatus.BAD_GATEWAY, "MFDS_NUTRITION_API_FAILED", "식품영양성분DB OpenAPI 요청이 중단되었습니다.");
        }
    }

    private List<JsonNode> responseItems(JsonNode root) {
        JsonNode items = root.path("response").path("body").path("items").path("item");
        if (isMissing(items)) {
            items = root.path("body").path("items").path("item");
        }
        if (isMissing(items)) {
            items = root.path("items").path("item");
        }
        if (isMissing(items)) {
            items = root.path("response").path("body").path("items");
        }
        if (isMissing(items)) {
            items = root.path("body").path("items");
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

    record NutritionRow(
            String foodCode,
            String foodName,
            String categoryName,
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
