package com.database2026.backend.ingredient;

import com.database2026.backend.common.DomainException;
import com.database2026.backend.ingredient.IngredientDtos.FoodIngredientItem;
import com.database2026.backend.ingredient.IngredientDtos.FoodIngredientListResponse;
import com.database2026.backend.ingredient.IngredientDtos.FoodIngredientSyncResponse;
import com.database2026.backend.ingredient.IngredientDtos.IngredientItem;
import com.database2026.backend.ingredient.IngredientDtos.IngredientSearchResponse;
import com.database2026.backend.ingredient.IngredientDtos.UserIngredientAllergyAddRequest;
import com.database2026.backend.ingredient.IngredientDtos.UserIngredientAllergyBulkAddRequest;
import com.database2026.backend.ingredient.IngredientDtos.UserIngredientAllergyItem;
import com.database2026.backend.ingredient.IngredientDtos.UserIngredientAllergyListResponse;
import com.database2026.backend.support.SqlSupport;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngredientService {

    private final JdbcTemplate jdbcTemplate;
    private final SqlSupport sqlSupport;
    private final MfdsIngredientApiClient mfdsApiClient;

    public IngredientService(JdbcTemplate jdbcTemplate, SqlSupport sqlSupport, MfdsIngredientApiClient mfdsApiClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlSupport = sqlSupport;
        this.mfdsApiClient = mfdsApiClient;
    }

    @Transactional
    public IngredientSearchResponse search(String query, int limit) {
        String normalizedQuery = normalizeRequired(query, "INGREDIENT_QUERY_REQUIRED", "검색어 q가 필요합니다.");
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        if (mfdsApiClient.hasRawMaterialKey()) {
            for (MfdsIngredientApiClient.RawMaterialRow row : mfdsApiClient.searchRawMaterials(query.trim(), safeLimit)) {
                upsertRawMaterial(row);
            }
        }
        List<IngredientItem> items = searchLocal(normalizedQuery, safeLimit);
        if (items.isEmpty()) {
            upsertIngredientName(query.trim(), "USER_SEARCH");
            items = searchLocal(normalizedQuery, safeLimit);
        }
        return new IngredientSearchResponse(items);
    }

    public UserIngredientAllergyListResponse userIngredientAllergies(long userId) {
        return new UserIngredientAllergyListResponse(jdbcTemplate.query("""
                        select allergy_id, ingredient_id, allergy_name, reaction_note
                        from user_allergy
                        where user_id = ?
                          and allergy_type = 'INGREDIENT'
                        order by allergy_name
                        """,
                (rs, rowNum) -> new UserIngredientAllergyItem(
                        rs.getLong("allergy_id"),
                        rs.getObject("ingredient_id", Long.class),
                        rs.getString("allergy_name"),
                        rs.getString("reaction_note")
                ),
                userId
        ));
    }

    @Transactional
    public UserIngredientAllergyListResponse addUserIngredientAllergy(long userId, UserIngredientAllergyAddRequest request) {
        upsertUserIngredientAllergy(userId, request);
        return userIngredientAllergies(userId);
    }

    @Transactional
    public UserIngredientAllergyListResponse addUserIngredientAllergies(long userId, UserIngredientAllergyBulkAddRequest request) {
        for (UserIngredientAllergyAddRequest item : request.items()) {
            upsertUserIngredientAllergy(userId, item);
        }
        return userIngredientAllergies(userId);
    }

    private void upsertUserIngredientAllergy(long userId, UserIngredientAllergyAddRequest request) {
        AllergyIngredient ingredient = allergyIngredient(request);
        String note = normalizeNote(request.reactionNote());
        try {
            jdbcTemplate.update("""
                    insert into user_allergy (
                        user_id, allergy_type, food_id, ingredient_id, allergy_name, normalized_allergy_name, reaction_note
                    )
                    values (?, 'INGREDIENT', null, ?, ?, ?, ?)
                    """, userId, ingredient.ingredientId(), ingredient.name(), normalize(ingredient.name()), note);
        } catch (DuplicateKeyException exception) {
            jdbcTemplate.update("""
                    update user_allergy
                    set ingredient_id = ?,
                        allergy_name = ?,
                        reaction_note = ?
                    where user_id = ?
                      and allergy_type = 'INGREDIENT'
                      and normalized_allergy_name = ?
                    """, ingredient.ingredientId(), ingredient.name(), note, userId, normalize(ingredient.name()));
        }
    }

    @Transactional
    public UserIngredientAllergyListResponse deleteUserIngredientAllergy(long userId, long allergyId) {
        jdbcTemplate.update("""
                delete from user_allergy
                where user_id = ?
                  and allergy_type = 'INGREDIENT'
                  and allergy_id = ?
                """, userId, allergyId);
        return userIngredientAllergies(userId);
    }

    public FoodIngredientListResponse foodIngredients(long foodId) {
        assertFoodExists(foodId);
        return new FoodIngredientListResponse(foodIngredientItems(foodId));
    }

    @Transactional
    public FoodIngredientSyncResponse syncFoodIngredients(long foodId) {
        FoodLookup food = foodLookup(foodId);
        if (!mfdsApiClient.hasProductIngredientKey()) {
            throw DomainException.badRequest("MFDS_PRODUCT_INGREDIENT_KEY_REQUIRED", "품목제조보고 원재료 API 인증키가 필요합니다.");
        }

        jdbcTemplate.update("""
                delete from food_ingredient
                where food_id = ?
                  and source_name = 'MFDS_PRODUCT_INGREDIENT'
                """, foodId);

        int importedCount = 0;
        for (MfdsIngredientApiClient.ProductIngredientRow row : mfdsApiClient.searchProductIngredients(food.foodName(), 100)) {
            for (String rawIngredientName : splitRawIngredientNames(row.rawIngredientName())) {
                long ingredientId = upsertIngredientName(rawIngredientName, "MFDS_PRODUCT_INGREDIENT");
                insertFoodIngredient(foodId, ingredientId, row.productReportNo(), rawIngredientName, row.displayOrder());
                importedCount++;
            }
        }
        return new FoodIngredientSyncResponse(foodId, importedCount, foodIngredientItems(foodId));
    }

    private List<IngredientItem> searchLocal(String normalizedQuery, int limit) {
        String pattern = "%" + normalizedQuery + "%";
        return jdbcTemplate.query("""
                        select i.ingredient_id,
                               i.ingredient_name,
                               (
                                   select min(a.alias_name)
                                   from ingredient_alias a
                                   where a.ingredient_id = i.ingredient_id
                                     and a.normalized_alias like ?
                               ) as matched_alias,
                               i.large_category,
                               i.middle_category,
                               i.english_name
                        from ingredient i
                        where i.normalized_name like ?
                           or exists (
                               select 1
                               from ingredient_alias a
                               where a.ingredient_id = i.ingredient_id
                                 and a.normalized_alias like ?
                           )
                        order by i.ingredient_name
                        limit ?
                        """,
                (rs, rowNum) -> new IngredientItem(
                        rs.getLong("ingredient_id"),
                        rs.getString("ingredient_name"),
                        rs.getString("matched_alias"),
                        rs.getString("large_category"),
                        rs.getString("middle_category"),
                        rs.getString("english_name")
                ),
                pattern,
                pattern,
                pattern,
                limit
        );
    }

    private long upsertRawMaterial(MfdsIngredientApiClient.RawMaterialRow row) {
        long ingredientId = upsertIngredient(
                "MFDS_RAW_MATERIAL",
                null,
                row.representativeName(),
                row.largeCategory(),
                row.middleCategory(),
                row.englishName(),
                row.scientificName(),
                row.regionName(),
                row.statusName(),
                row.useCondition()
        );
        insertAlias(ingredientId, row.representativeName(), "REPRESENTATIVE");
        for (String alias : splitAliases(row.nicknames())) {
            insertAlias(ingredientId, alias, "NICKNAME");
        }
        return ingredientId;
    }

    private long upsertIngredientName(String ingredientName, String sourceName) {
        long ingredientId = upsertIngredient(sourceName, null, ingredientName, null, null, null, null, null, null, null);
        insertAlias(ingredientId, ingredientName, "SEARCH");
        return ingredientId;
    }

    private long upsertIngredient(
            String sourceName,
            String sourceCode,
            String ingredientName,
            String largeCategory,
            String middleCategory,
            String englishName,
            String scientificName,
            String regionName,
            String statusName,
            String useCondition
    ) {
        String normalizedName = normalize(ingredientName);
        Optional<Long> existingId = ingredientIdByNormalized(normalizedName);
        if (existingId.isPresent()) {
            jdbcTemplate.update("""
                    update ingredient
                    set source_name = ?,
                        source_code = ?,
                        ingredient_name = ?,
                        large_category = coalesce(?, large_category),
                        middle_category = coalesce(?, middle_category),
                        english_name = coalesce(?, english_name),
                        scientific_name = coalesce(?, scientific_name),
                        region_name = coalesce(?, region_name),
                        status_name = coalesce(?, status_name),
                        use_condition = coalesce(?, use_condition)
                    where ingredient_id = ?
                    """, sourceName, sourceCode, ingredientName, largeCategory, middleCategory, englishName,
                    scientificName, regionName, statusName, useCondition, existingId.get());
            return existingId.get();
        }
        return sqlSupport.insert("""
                insert into ingredient (
                    source_name, source_code, ingredient_name, normalized_name, large_category, middle_category,
                    english_name, scientific_name, region_name, status_name, use_condition
                )
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, sourceName, sourceCode, ingredientName, normalizedName, largeCategory, middleCategory,
                englishName, scientificName, regionName, statusName, useCondition);
    }

    private Optional<Long> ingredientIdByNormalized(String normalizedName) {
        return jdbcTemplate.query("""
                        select ingredient_id
                        from ingredient
                        where normalized_name = ?
                        """,
                (rs, rowNum) -> rs.getLong("ingredient_id"),
                normalizedName
        ).stream().findFirst();
    }

    private void insertAlias(long ingredientId, String aliasName, String aliasType) {
        if (aliasName == null || aliasName.isBlank()) {
            return;
        }
        try {
            jdbcTemplate.update("""
                    insert into ingredient_alias (ingredient_id, alias_name, normalized_alias, alias_type)
                    values (?, ?, ?, ?)
                    """, ingredientId, aliasName.trim(), normalize(aliasName), aliasType);
        } catch (DuplicateKeyException ignored) {
            // Existing aliases are stable search data.
        }
    }

    private AllergyIngredient allergyIngredient(UserIngredientAllergyAddRequest request) {
        if (request.ingredientId() != null) {
            return jdbcTemplate.query("""
                            select ingredient_id, ingredient_name
                            from ingredient
                            where ingredient_id = ?
                            """,
                    (rs, rowNum) -> new AllergyIngredient(rs.getLong("ingredient_id"), rs.getString("ingredient_name")),
                    request.ingredientId()
            ).stream().findFirst().orElseThrow(() -> DomainException.notFound("INGREDIENT_NOT_FOUND", "원재료를 찾을 수 없습니다."));
        }
        String ingredientName = requiredTrim(request.ingredientName(), "INGREDIENT_NAME_REQUIRED", "ingredientId 또는 ingredientName이 필요합니다.");
        long ingredientId = upsertIngredientName(ingredientName, "USER_INPUT");
        return new AllergyIngredient(ingredientId, ingredientName.trim());
    }

    private FoodLookup foodLookup(long foodId) {
        return jdbcTemplate.query("""
                        select food_id, food_name
                        from food
                        where food_id = ?
                          and source_name = 'MFDS_INTEGRATED'
                        """,
                (rs, rowNum) -> new FoodLookup(rs.getLong("food_id"), rs.getString("food_name")),
                foodId
        ).stream().findFirst().orElseThrow(() -> DomainException.notFound("FOOD_NOT_FOUND", "음식을 찾을 수 없습니다."));
    }

    private void assertFoodExists(long foodId) {
        foodLookup(foodId);
    }

    private void insertFoodIngredient(long foodId, long ingredientId, String sourceReference, String rawIngredientName, Integer displayOrder) {
        try {
            jdbcTemplate.update("""
                    insert into food_ingredient (food_id, ingredient_id, source_name, source_reference, raw_ingredient_name, display_order)
                    values (?, ?, 'MFDS_PRODUCT_INGREDIENT', ?, ?, ?)
                    """, foodId, ingredientId, sourceReference, rawIngredientName, displayOrder);
        } catch (DuplicateKeyException ignored) {
            // Same API ingredient was already cached for this food.
        }
    }

    private List<FoodIngredientItem> foodIngredientItems(long foodId) {
        return jdbcTemplate.query("""
                        select i.ingredient_id,
                               i.ingredient_name,
                               fi.raw_ingredient_name,
                               fi.source_name,
                               fi.source_reference
                        from food_ingredient fi
                        join ingredient i on i.ingredient_id = fi.ingredient_id
                        where fi.food_id = ?
                        order by fi.display_order, i.ingredient_name
                        """,
                (rs, rowNum) -> new FoodIngredientItem(
                        rs.getLong("ingredient_id"),
                        rs.getString("ingredient_name"),
                        rs.getString("raw_ingredient_name"),
                        rs.getString("source_name"),
                        rs.getString("source_reference")
                ),
                foodId
        );
    }

    private List<String> splitAliases(String aliases) {
        if (aliases == null || aliases.isBlank()) {
            return List.of();
        }
        return splitNames(aliases);
    }

    private List<String> splitRawIngredientNames(String rawIngredientNames) {
        if (rawIngredientNames == null || rawIngredientNames.isBlank()) {
            return List.of();
        }
        return splitNames(rawIngredientNames);
    }

    private List<String> splitNames(String value) {
        Set<String> names = new LinkedHashSet<>();
        for (String part : value.split("[,;/|·]")) {
            String name = part.trim();
            if (!name.isBlank()) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }

    private String normalizeRequired(String value, String code, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw DomainException.badRequest(code, message);
        }
        return normalize(normalized);
    }

    private String requiredTrim(String value, String code, String message) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isBlank()) {
            throw DomainException.badRequest(code, message);
        }
        return trimmed;
    }

    private String normalizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String normalized = note.trim();
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_\\-()\\[\\]{}]", "");
    }

    private record AllergyIngredient(Long ingredientId, String name) {
    }

    private record FoodLookup(Long foodId, String foodName) {
    }
}
