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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class FatSecretApiClient {

    private static final String TOKEN_URL = "https://oauth.fatsecret.com/connect/token";
    private static final String SEARCH_URL = "https://platform.fatsecret.com/rest/foods/search/v1";
    private static final String AUTOCOMPLETE_URL = "https://platform.fatsecret.com/rest/food/autocomplete/v1";
    private static final BigDecimal DEFAULT_SERVING_G = BigDecimal.valueOf(100);
    private static final Pattern CALORIES = Pattern.compile("(?i)calories:\\s*([0-9]+(?:\\.[0-9]+)?)\\s*kcal");
    private static final Pattern FAT = Pattern.compile("(?i)fat:\\s*([0-9]+(?:\\.[0-9]+)?)\\s*g");
    private static final Pattern CARB = Pattern.compile("(?i)(?:carbs|carbohydrate):\\s*([0-9]+(?:\\.[0-9]+)?)\\s*g");
    private static final Pattern PROTEIN = Pattern.compile("(?i)protein:\\s*([0-9]+(?:\\.[0-9]+)?)\\s*g");
    private static final Pattern PER_GRAMS = Pattern.compile("(?i)per\\s+([0-9]+(?:\\.[0-9]+)?)\\s*g\\b");

    private final String clientId;
    private final String clientSecret;
    private final String scope;
    private final String region;
    private final String language;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration timeout;
    private final Map<String, AccessToken> cachedAccessTokens = new ConcurrentHashMap<>();

    FatSecretApiClient(
            @Value("${app.external.fatsecret.client-id:}") String clientId,
            @Value("${app.external.fatsecret.client-secret:}") String clientSecret,
            @Value("${app.external.fatsecret.scope:basic}") String scope,
            @Value("${app.external.fatsecret.region:}") String region,
            @Value("${app.external.fatsecret.language:}") String language,
            @Value("${app.external.fatsecret.timeout-ms:5000}") long timeoutMs
    ) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.scope = scope == null ? "" : scope.trim();
        this.region = region == null ? "" : region.trim();
        this.language = language == null ? "" : language.trim();
        this.objectMapper = new ObjectMapper();
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
    }

    boolean hasCredentials() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    List<FoodRow> searchFoods(String query, int limit) {
        if (!hasCredentials() || query == null || query.isBlank()) {
            return List.of();
        }
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        String url = SEARCH_URL
                + "?search_expression=" + encode(query)
                + "&max_results=" + safeLimit
                + "&page_number=0"
                + "&format=json"
                + localizationQuery();
        JsonNode root = getJson(URI.create(url), scope);
        JsonNode food = root.path("foods").path("food");
        if (isMissing(food)) {
            return List.of();
        }
        List<JsonNode> nodes = new ArrayList<>();
        if (food.isArray()) {
            food.forEach(nodes::add);
        } else {
            nodes.add(food);
        }
        return nodes.stream()
                .map(this::foodRow)
                .filter(FoodRow::hasImportableData)
                .toList();
    }

    List<String> autocomplete(String query, int limit) {
        if (!hasCredentials() || query == null || query.isBlank()) {
            return List.of();
        }
        int safeLimit = Math.min(Math.max(limit, 1), 10);
        String url = AUTOCOMPLETE_URL
                + "?expression=" + encode(query)
                + "&max_results=" + safeLimit
                + "&format=json"
                + autocompleteLocalizationQuery();
        JsonNode root = getJson(URI.create(url), autocompleteScope());
        JsonNode suggestion = root.path("suggestions").path("suggestion");
        if (isMissing(suggestion)) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        if (suggestion.isArray()) {
            suggestion.forEach(item -> addSuggestion(items, item.asText()));
        } else {
            addSuggestion(items, suggestion.asText());
        }
        return items.stream().limit(safeLimit).toList();
    }

    private void addSuggestion(List<String> items, String suggestion) {
        if (suggestion == null) {
            return;
        }
        String value = suggestion.trim();
        if (!value.isBlank()) {
            items.add(value);
        }
    }

    private JsonNode getJson(URI uri, String requestedScope) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + accessToken(requestedScope))
                .build();
        return sendJson(request, "FATSECRET_API_FAILED", "FatSecret API 요청에 실패했습니다.");
    }

    private String accessToken(String requestedScope) {
        String cacheKey = normalizeScope(requestedScope);
        AccessToken current = cachedAccessTokens.get(cacheKey);
        if (current != null && Instant.now().isBefore(current.expiresAt())) {
            return current.value();
        }
        synchronized (this) {
            current = cachedAccessTokens.get(cacheKey);
            if (current != null && Instant.now().isBefore(current.expiresAt())) {
                return current.value();
            }
            AccessToken newToken = requestAccessToken(cacheKey);
            cachedAccessTokens.put(cacheKey, newToken);
            return newToken.value();
        }
    }

    private AccessToken requestAccessToken(String requestedScope) {
        String credentials = clientId + ":" + clientSecret;
        String form = "grant_type=client_credentials";
        if (!requestedScope.isBlank()) {
            form += "&scope=" + encode(requestedScope);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .timeout(timeout)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
                .build();
        JsonNode root = sendJson(request, "FATSECRET_TOKEN_FAILED", "FatSecret access token 발급에 실패했습니다.");
        String value = text(root, "access_token");
        if (value == null) {
            throw new DomainException(HttpStatus.BAD_GATEWAY, "FATSECRET_TOKEN_FAILED", "FatSecret access token 응답이 올바르지 않습니다.");
        }
        long expiresIn = Math.max(root.path("expires_in").asLong(86400) - 60, 60);
        return new AccessToken(value, Instant.now().plusSeconds(expiresIn));
    }

    private JsonNode sendJson(HttpRequest request, String code, String message) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new DomainException(HttpStatus.BAD_GATEWAY, code, message + " status=" + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode error = root.path("error");
            if (!isMissing(error)) {
                throw new DomainException(HttpStatus.BAD_GATEWAY, code, message + " error=" + error.toString());
            }
            return root;
        } catch (IOException exception) {
            throw new DomainException(HttpStatus.BAD_GATEWAY, code, message + " 응답을 처리하지 못했습니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DomainException(HttpStatus.BAD_GATEWAY, code, message + " 요청이 중단되었습니다.");
        }
    }

    private FoodRow foodRow(JsonNode node) {
        String foodId = text(node, "food_id");
        String foodName = text(node, "food_name");
        String brandName = text(node, "brand_name");
        String foodType = text(node, "food_type");
        String description = text(node, "food_description");
        BigDecimal servingG = servingAmountG(description);
        return new FoodRow(
                foodId,
                displayName(foodName, brandName),
                brandName,
                foodType,
                servingG,
                amountPer100g(amount(description, CALORIES), servingG),
                amountPer100g(amount(description, CARB), servingG),
                amountPer100g(amount(description, PROTEIN), servingG),
                amountPer100g(amount(description, FAT), servingG),
                null,
                null
        );
    }

    private String displayName(String foodName, String brandName) {
        if (foodName == null || foodName.isBlank()) {
            return null;
        }
        if (brandName == null || brandName.isBlank()) {
            return foodName.trim();
        }
        return (brandName.trim() + " " + foodName.trim()).trim();
    }

    private BigDecimal servingAmountG(String description) {
        if (description == null) {
            return DEFAULT_SERVING_G;
        }
        Matcher matcher = PER_GRAMS.matcher(description);
        if (!matcher.find()) {
            return DEFAULT_SERVING_G;
        }
        BigDecimal servingG = new BigDecimal(matcher.group(1));
        return servingG.compareTo(BigDecimal.ZERO) > 0 ? servingG : DEFAULT_SERVING_G;
    }

    private BigDecimal amount(String description, Pattern pattern) {
        if (description == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(description);
        if (!matcher.find()) {
            return null;
        }
        return new BigDecimal(matcher.group(1));
    }

    private BigDecimal amountPer100g(BigDecimal amount, BigDecimal servingG) {
        if (amount == null) {
            return null;
        }
        BigDecimal safeServingG = servingG == null || servingG.compareTo(BigDecimal.ZERO) <= 0 ? DEFAULT_SERVING_G : servingG;
        return amount.multiply(BigDecimal.valueOf(100))
                .divide(safeServingG, 4, RoundingMode.HALF_UP);
    }

    private String localizationQuery() {
        if (!hasPremierScope() || region.isBlank()) {
            return "";
        }
        String query = "&region=" + encode(region);
        if (!language.isBlank()) {
            query += "&language=" + encode(language);
        }
        return query;
    }

    private String autocompleteLocalizationQuery() {
        if (region.isBlank()) {
            return "";
        }
        return "&region=" + encode(region);
    }

    private String autocompleteScope() {
        return hasPremierScope() ? scope : "premier";
    }

    private boolean hasPremierScope() {
        return List.of(scope.toLowerCase(Locale.ROOT).split("\\s+")).contains("premier");
    }

    private String normalizeScope(String requestedScope) {
        return requestedScope == null ? "" : requestedScope.trim();
    }

    private boolean isMissing(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() || (node.isArray() && node.isEmpty());
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isBlank() ? null : text;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record AccessToken(String value, Instant expiresAt) {
    }

    record FoodRow(
            String foodCode,
            String foodName,
            String brandName,
            String foodType,
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
