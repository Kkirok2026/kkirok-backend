package com.database2026.backend.ingredient;

import com.database2026.backend.common.DomainException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
class MfdsIngredientApiClient {

    private final String rawMaterialServiceKey;
    private final String productIngredientServiceKey;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration timeout;

    MfdsIngredientApiClient(
            @Value("${app.external.mfds.raw-material.service-key:}") String rawMaterialServiceKey,
            @Value("${app.external.mfds.product-ingredient.service-key:}") String productIngredientServiceKey,
            @Value("${app.external.mfds.timeout-ms:5000}") long timeoutMs
    ) {
        this.rawMaterialServiceKey = rawMaterialServiceKey;
        this.productIngredientServiceKey = productIngredientServiceKey;
        this.objectMapper = new ObjectMapper();
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
    }

    boolean hasRawMaterialKey() {
        return rawMaterialServiceKey != null && !rawMaterialServiceKey.isBlank();
    }

    boolean hasProductIngredientKey() {
        return productIngredientServiceKey != null && !productIngredientServiceKey.isBlank();
    }

    List<RawMaterialRow> searchRawMaterials(String query, int limit) {
        if (!hasRawMaterialKey()) {
            return List.of();
        }
        String url = "https://apis.data.go.kr/1471000/FoodRwmatrInfoService01/getFoodRwmatrList01"
                + "?serviceKey=" + encode(rawMaterialServiceKey)
                + "&type=json"
                + "&pageNo=1"
                + "&numOfRows=" + limit
                + "&rprsnt_rawmtrl_nm=" + encode(query);
        JsonNode root = getJson(URI.create(url), "MFDS_RAW_MATERIAL_API_FAILED");
        JsonNode items = root.path("response").path("body").path("items").path("item");
        return arrayItems(items).stream()
                .map(node -> new RawMaterialRow(
                        text(node, "RPRSNT_RAWMTRL_NM"),
                        text(node, "RAWMTRL_NCKNM"),
                        text(node, "ENG_NM"),
                        text(node, "LCLAS_NM"),
                        text(node, "MLSFC_NM"),
                        text(node, "SCNM"),
                        text(node, "REGN_CD_NM"),
                        text(node, "RAWMTRL_STATS_CD_NM"),
                        text(node, "USE_CND_NM")
                ))
                .filter(row -> row.representativeName() != null && !row.representativeName().isBlank())
                .toList();
    }

    List<ProductIngredientRow> searchProductIngredients(String productName, int limit) {
        if (!hasProductIngredientKey()) {
            return List.of();
        }
        String url = "https://openapi.foodsafetykorea.go.kr/api/"
                + encodePath(productIngredientServiceKey)
                + "/C002/json/1/" + limit
                + "/PRDLST_NM=" + encodePath(productName);
        JsonNode root = getJson(URI.create(url), "MFDS_PRODUCT_INGREDIENT_API_FAILED");
        JsonNode rows = root.path("C002").path("row");
        return arrayItems(rows).stream()
                .map(node -> new ProductIngredientRow(
                        text(node, "PRDLST_REPORT_NO"),
                        text(node, "PRDLST_NM"),
                        text(node, "RAWMTRL_NM"),
                        intValue(node, "RAWMTRL_ORDNO")
                ))
                .filter(row -> row.rawIngredientName() != null && !row.rawIngredientName().isBlank())
                .toList();
    }

    private JsonNode getJson(URI uri, String errorCode) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(timeout)
                .header("Accept", "application/json")
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new DomainException(HttpStatus.BAD_GATEWAY, errorCode, "식약처 원재료 API 요청에 실패했습니다. status=" + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (IOException exception) {
            throw new DomainException(HttpStatus.BAD_GATEWAY, errorCode, "식약처 원재료 API 응답을 처리하지 못했습니다.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DomainException(HttpStatus.BAD_GATEWAY, errorCode, "식약처 원재료 API 요청이 중단되었습니다.");
        }
    }

    private List<JsonNode> arrayItems(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            return List.of(node);
        }
        List<JsonNode> items = new ArrayList<>();
        node.forEach(items::add);
        return items;
    }

    private String text(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        return text.isBlank() ? null : text;
    }

    private Integer intValue(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        if (value.isInt()) {
            return value.asInt();
        }
        try {
            return Integer.parseInt(value.asText().trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String encodePath(String value) {
        return encode(value).replace("+", "%20");
    }

    record RawMaterialRow(
            String representativeName,
            String nicknames,
            String englishName,
            String largeCategory,
            String middleCategory,
            String scientificName,
            String regionName,
            String statusName,
            String useCondition
    ) {
    }

    record ProductIngredientRow(
            String productReportNo,
            String productName,
            String rawIngredientName,
            Integer displayOrder
    ) {
    }
}
