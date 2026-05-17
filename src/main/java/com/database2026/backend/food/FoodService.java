package com.database2026.backend.food;

import com.database2026.backend.common.DomainException;
import com.database2026.backend.common.NutrientTotals;
import com.database2026.backend.food.FoodDtos.CustomFoodCreateRequest;
import com.database2026.backend.food.FoodDtos.FoodDetail;
import com.database2026.backend.food.FoodDtos.FoodSearchResponse;
import com.database2026.backend.food.FoodDtos.FoodSuggestionResponse;
import com.database2026.backend.food.FoodDtos.FoodSummary;
import com.database2026.backend.support.SqlSupport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FoodService {

    private static final String MFDS_SOURCE_NAME = "MFDS_INTEGRATED";
    private static final String FATSECRET_SOURCE_NAME = "FATSECRET";
    private static final String USER_CUSTOM_SOURCE_NAME = "USER_CUSTOM";
    private static final BigDecimal DEFAULT_CUSTOM_FOOD_AMOUNT_G = BigDecimal.valueOf(100);

    private final JdbcTemplate jdbcTemplate;
    private final SqlSupport sqlSupport;
    private final MfdsNutritionApiClient mfdsNutritionApiClient;
    private final FatSecretApiClient fatSecretApiClient;

    public FoodService(
            JdbcTemplate jdbcTemplate,
            SqlSupport sqlSupport,
            MfdsNutritionApiClient mfdsNutritionApiClient,
            FatSecretApiClient fatSecretApiClient
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlSupport = sqlSupport;
        this.mfdsNutritionApiClient = mfdsNutritionApiClient;
        this.fatSecretApiClient = fatSecretApiClient;
    }

    public boolean hasMfdsNutritionServiceKey() {
        return mfdsNutritionApiClient.hasServiceKey();
    }

    @Transactional
    public FoodSearchResponse search(String query, int limit, Optional<Long> userId) {
        String normalizedQuery = normalizeQuery(query);
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        String pattern = "%" + normalizedQuery + "%";
        String compactPattern = "%" + compactQuery(normalizedQuery) + "%";
        Long currentUserId = userId.orElse(null);

        importFatSecretRows(normalizedQuery, safeLimit);
        List<FoodSummary> items = searchLocal(pattern, compactPattern, safeLimit, currentUserId);
        if (items.size() < safeLimit) {
            importMfdsNutritionRows(normalizedQuery, safeLimit);
            items = searchLocal(pattern, compactPattern, safeLimit, currentUserId);
        }
        return new FoodSearchResponse(items);
    }

    public FoodSuggestionResponse suggestions(String query, int limit, Optional<Long> userId) {
        String normalizedQuery = normalizeQuery(query);
        int safeLimit = Math.min(Math.max(limit, 1), 10);
        Map<String, String> suggestions = new LinkedHashMap<>();

        for (String suggestion : fatSecretSuggestions(normalizedQuery, safeLimit)) {
            addSuggestion(suggestions, suggestion);
            if (suggestions.size() >= safeLimit) {
                return new FoodSuggestionResponse(List.copyOf(suggestions.values()));
            }
        }

        String pattern = "%" + normalizedQuery + "%";
        String compactPattern = "%" + compactQuery(normalizedQuery) + "%";
        for (String suggestion : localSuggestions(pattern, compactPattern, safeLimit * 4, userId.orElse(null))) {
            addSuggestion(suggestions, suggestion);
            if (suggestions.size() >= safeLimit) {
                break;
            }
        }
        return new FoodSuggestionResponse(List.copyOf(suggestions.values()));
    }

    @Transactional
    public int importMfdsFoods(Collection<String> queries, int limit) {
        return importMfdsNutritionRows(queries, limit);
    }

    @Transactional
    public void addFoodAlias(long foodId, String aliasName, String aliasType, int priority) {
        insertAlias(foodId, aliasName, aliasType, priority);
    }

    @Transactional
    public FoodSummary createCustomFood(long userId, CustomFoodCreateRequest request) {
        String foodName = normalizeFoodName(request.foodName());
        String normalizedFoodName = normalizedCustomFoodName(foodName);
        BigDecimal amountG = customFoodAmount(request.amountG());
        long foodId = customFoodId(userId, normalizedFoodName)
                .map(existingFoodId -> updateCustomFood(existingFoodId, userId, foodName, normalizedFoodName, amountG))
                .orElseGet(() -> insertCustomFood(userId, foodName, normalizedFoodName, amountG));
        insertAlias(foodId, foodName);
        upsertNutrientValue(foodId, "CALORIES_KCAL", per100g(request.caloriesKcal(), amountG));
        upsertNutrientValue(foodId, "CARB_G", per100g(request.carbG(), amountG));
        upsertNutrientValue(foodId, "PROTEIN_G", per100g(request.proteinG(), amountG));
        upsertNutrientValue(foodId, "FAT_G", per100g(request.fatG(), amountG));
        upsertNutrientValue(foodId, "SUGAR_G", per100g(optionalAmount(request.sugarG()), amountG));
        upsertNutrientValue(foodId, "SODIUM_MG", per100g(optionalAmount(request.sodiumMg()), amountG));
        return foodSummary(foodId, userId);
    }

    private List<FoodSummary> searchLocal(String pattern, String compactPattern, int safeLimit, Long userId) {
        int fetchLimit = Math.min(Math.max(safeLimit * 4, safeLimit), 200);
        List<FoodSummary> items = jdbcTemplate.query("""
                                select f.food_id,
                                       f.source_name,
                                       f.source_food_code,
                                       f.food_name,
                                       f.default_serving_g,
                                       (
                                           select min(a.alias_name)
                                           from food_alias a
                                           where a.food_id = f.food_id
                                             and (
                                                 lower(a.normalized_alias) like ?
                                                 or replace(lower(a.normalized_alias), ' ', '') like ?
                                             )
                                       ) as matched_alias,
                                       coalesce(f.calories_kcal * f.default_serving_g / 100, 0) as calories_kcal,
                                       coalesce(f.carb_g * f.default_serving_g / 100, 0) as carb_g,
                                       coalesce(f.protein_g * f.default_serving_g / 100, 0) as protein_g,
                                       coalesce(f.fat_g * f.default_serving_g / 100, 0) as fat_g,
                                       coalesce(f.sugar_g * f.default_serving_g / 100, 0) as sugar_g,
                                       coalesce(f.sodium_mg * f.default_serving_g / 100, 0) as sodium_mg
                                from food f
                                where (
                                      f.source_name in (?, ?)
                                      or (
                                          ? is not null
                                          and f.source_name = 'USER_CUSTOM'
                                          and exists (
                                              select 1
                                              from user_custom_food ucf
                                              where ucf.food_id = f.food_id
                                                and ucf.user_id = ?
                                          )
                                      )
                                  )
                                  and (
                                      lower(f.food_name) like ?
                                      or replace(lower(f.food_name), ' ', '') like ?
                                      or exists (
                                       select 1
                                       from food_alias a
                                       where a.food_id = f.food_id
                                         and (
                                             lower(a.normalized_alias) like ?
                                             or replace(lower(a.normalized_alias), ' ', '') like ?
                                         )
                                      )
                                )
                                group by f.food_id, f.source_name, f.source_food_code, f.food_name, f.default_serving_g,
                                         f.calories_kcal, f.carb_g, f.protein_g, f.fat_g, f.sugar_g, f.sodium_mg
                                order by case
                                             when f.source_name = 'FATSECRET' then 0
                                             when f.source_name = 'USER_CUSTOM' then 1
                                             when f.source_name = 'MFDS_INTEGRATED' then 2
                                             else 3
                                         end,
                                         f.food_name
                                limit ?
                                """,
                        (rs, rowNum) -> new FoodSummary(
                                rs.getLong("food_id"),
                                rs.getString("source_name"),
                                rs.getString("source_food_code"),
                                rs.getString("food_name"),
                                rs.getString("matched_alias"),
                                rs.getBigDecimal("default_serving_g"),
                                NutrientTotals.from(rs)
                        ),
                        pattern,
                        compactPattern,
                        MFDS_SOURCE_NAME,
                        FATSECRET_SOURCE_NAME,
                        userId,
                        userId,
                        pattern,
                        compactPattern,
                        pattern,
                        compactPattern,
                        fetchLimit
                );
        return deduplicateByFoodName(items, safeLimit);
    }

    private List<String> fatSecretSuggestions(String query, int limit) {
        if (!fatSecretApiClient.hasCredentials()) {
            return List.of();
        }
        try {
            return fatSecretApiClient.autocomplete(query, limit);
        } catch (DomainException exception) {
            if (exception.code() != null && exception.code().startsWith("FATSECRET_")) {
                return List.of();
            }
            throw exception;
        }
    }

    private List<String> localSuggestions(String pattern, String compactPattern, int fetchLimit, Long userId) {
        List<String> items = new java.util.ArrayList<>();
        items.addAll(jdbcTemplate.query("""
                        select f.food_name as suggestion
                        from food f
                        where (
                              f.source_name in (?, ?)
                              or (
                                  ? is not null
                                  and f.source_name = ?
                                  and exists (
                                      select 1
                                      from user_custom_food ucf
                                      where ucf.food_id = f.food_id
                                        and ucf.user_id = ?
                                  )
                              )
                          )
                          and (
                              lower(f.food_name) like ?
                              or replace(lower(f.food_name), ' ', '') like ?
                          )
                        order by case
                                     when f.source_name = 'FATSECRET' then 0
                                     when f.source_name = 'USER_CUSTOM' then 1
                                     when f.source_name = 'MFDS_INTEGRATED' then 2
                                     else 3
                                 end,
                                 f.food_name
                        limit ?
                        """,
                (rs, rowNum) -> rs.getString("suggestion"),
                FATSECRET_SOURCE_NAME,
                MFDS_SOURCE_NAME,
                userId,
                USER_CUSTOM_SOURCE_NAME,
                userId,
                pattern,
                compactPattern,
                fetchLimit
        ));
        items.addAll(jdbcTemplate.query("""
                        select a.alias_name as suggestion
                        from food_alias a
                        join food f on f.food_id = a.food_id
                        where (
                              f.source_name in (?, ?)
                              or (
                                  ? is not null
                                  and f.source_name = ?
                                  and exists (
                                      select 1
                                      from user_custom_food ucf
                                      where ucf.food_id = f.food_id
                                        and ucf.user_id = ?
                                  )
                              )
                          )
                          and (
                              lower(a.normalized_alias) like ?
                              or replace(lower(a.normalized_alias), ' ', '') like ?
                          )
                        order by case
                                     when f.source_name = 'FATSECRET' then 0
                                     when f.source_name = 'USER_CUSTOM' then 1
                                     when f.source_name = 'MFDS_INTEGRATED' then 2
                                     else 3
                                 end,
                                 a.priority desc,
                                 a.alias_name
                        limit ?
                        """,
                (rs, rowNum) -> rs.getString("suggestion"),
                FATSECRET_SOURCE_NAME,
                MFDS_SOURCE_NAME,
                userId,
                USER_CUSTOM_SOURCE_NAME,
                userId,
                pattern,
                compactPattern,
                fetchLimit
        ));
        return items;
    }

    private void addSuggestion(Map<String, String> suggestions, String suggestion) {
        if (suggestion == null || suggestion.isBlank()) {
            return;
        }
        String value = suggestion.trim();
        suggestions.putIfAbsent(suggestionDeduplicationKey(value), value);
    }

    private List<FoodSummary> deduplicateByFoodName(List<FoodSummary> items, int limit) {
        Map<String, FoodSummary> deduplicated = new LinkedHashMap<>();
        for (FoodSummary item : items) {
            String key = foodNameDeduplicationKey(item);
            deduplicated.putIfAbsent(key, item);
            if (deduplicated.size() >= limit) {
                break;
            }
        }
        return List.copyOf(deduplicated.values());
    }

    private String foodNameDeduplicationKey(FoodSummary item) {
        String foodName = item.foodName() == null ? "" : item.foodName();
        String normalizedFoodName = foodName.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-()/]+", "");
        if (!normalizedFoodName.isBlank()) {
            return normalizedFoodName;
        }
        return item.sourceName() + ":" + item.sourceFoodCode();
    }

    private String suggestionDeduplicationKey(String suggestion) {
        return suggestion.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-()/]+", "");
    }

    private int importMfdsNutritionRows(String query, int limit) {
        Set<String> queries = new LinkedHashSet<>();
        queries.add(query);
        queries.add(compactQuery(query));
        return importMfdsNutritionRows(queries, limit);
    }

    private int importMfdsNutritionRows(Collection<String> queries, int limit) {
        if (!mfdsNutritionApiClient.hasServiceKey()) {
            return 0;
        }
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        Set<String> apiQueries = new LinkedHashSet<>();
        for (String apiQuery : queries) {
            String normalizedQuery = optionalSearchQuery(apiQuery);
            if (normalizedQuery.isBlank()) {
                continue;
            }
            apiQueries.add(normalizedQuery);
            apiQueries.add(compactQuery(normalizedQuery));
        }

        int importedCount = 0;
        for (String apiQuery : apiQueries) {
            List<MfdsNutritionApiClient.NutritionRow> rows;
            try {
                rows = mfdsNutritionApiClient.searchFoods(apiQuery, safeLimit);
            } catch (DomainException exception) {
                if (exception.code() != null && exception.code().startsWith("MFDS_")) {
                    return importedCount;
                }
                throw exception;
            }
            for (MfdsNutritionApiClient.NutritionRow row : rows) {
                long foodId = upsertFood(row);
                insertAlias(foodId, row.foodName());
                upsertNutrientValue(foodId, "CALORIES_KCAL", row.caloriesKcal());
                upsertNutrientValue(foodId, "CARB_G", row.carbG());
                upsertNutrientValue(foodId, "PROTEIN_G", row.proteinG());
                upsertNutrientValue(foodId, "FAT_G", row.fatG());
                upsertNutrientValue(foodId, "SUGAR_G", row.sugarG());
                upsertNutrientValue(foodId, "SODIUM_MG", row.sodiumMg());
                importedCount++;
            }
        }
        return importedCount;
    }

    private int importFatSecretRows(String query, int limit) {
        if (!fatSecretApiClient.hasCredentials()) {
            return 0;
        }
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        List<FatSecretApiClient.FoodRow> rows;
        try {
            rows = fatSecretApiClient.searchFoods(query, safeLimit);
        } catch (DomainException exception) {
            if (exception.code() != null && exception.code().startsWith("FATSECRET_")) {
                return 0;
            }
            throw exception;
        }

        int importedCount = 0;
        for (FatSecretApiClient.FoodRow row : rows) {
            long foodId = upsertFatSecretFood(row);
            insertAlias(foodId, row.foodName());
            insertAlias(foodId, row.brandName(), "BRAND", 50);
            upsertNutrientValue(foodId, "CALORIES_KCAL", row.caloriesKcal());
            upsertNutrientValue(foodId, "CARB_G", row.carbG());
            upsertNutrientValue(foodId, "PROTEIN_G", row.proteinG());
            upsertNutrientValue(foodId, "FAT_G", row.fatG());
            upsertNutrientValue(foodId, "SUGAR_G", row.sugarG());
            upsertNutrientValue(foodId, "SODIUM_MG", row.sodiumMg());
            importedCount++;
        }
        return importedCount;
    }

    private Optional<Long> customFoodId(long userId, String normalizedFoodName) {
        return jdbcTemplate.query("""
                        select ucf.food_id
                        from user_custom_food ucf
                        join food f on f.food_id = ucf.food_id
                        where ucf.user_id = ?
                          and ucf.normalized_food_name = ?
                          and f.source_name = ?
                        """,
                (rs, rowNum) -> rs.getLong("food_id"),
                userId,
                normalizedFoodName,
                USER_CUSTOM_SOURCE_NAME
        ).stream().findFirst();
    }

    private long insertCustomFood(long userId, String foodName, String normalizedFoodName, BigDecimal amountG) {
        long foodId = sqlSupport.insert("""
                insert into food (source_name, source_food_code, food_name, default_serving_g, source_category)
                values (?, ?, ?, ?, ?)
                """, USER_CUSTOM_SOURCE_NAME, customFoodCode(userId), foodName, amountG, "사용자 직접 입력");
        sqlSupport.update("""
                insert into user_custom_food (user_id, food_id, food_name, normalized_food_name, serving_amount_g)
                values (?, ?, ?, ?, ?)
                """, userId, foodId, foodName, normalizedFoodName, amountG);
        return foodId;
    }

    private long updateCustomFood(long foodId, long userId, String foodName, String normalizedFoodName, BigDecimal amountG) {
        jdbcTemplate.update("""
                update food
                set food_name = ?,
                    default_serving_g = ?,
                    source_category = '사용자 직접 입력'
                where food_id = ?
                  and source_name = ?
                """, foodName, amountG, foodId, USER_CUSTOM_SOURCE_NAME);
        jdbcTemplate.update("""
                update user_custom_food
                set food_name = ?,
                    normalized_food_name = ?,
                    serving_amount_g = ?
                where user_id = ?
                  and food_id = ?
                """, foodName, normalizedFoodName, amountG, userId, foodId);
        return foodId;
    }

    private long upsertFood(MfdsNutritionApiClient.NutritionRow row) {
        Optional<Long> existingFoodId = foodIdBySourceCode(MFDS_SOURCE_NAME, row.foodCode());
        if (existingFoodId.isPresent()) {
            jdbcTemplate.update("""
                    update food
                    set food_name = ?,
                        default_serving_g = ?,
                        source_category = coalesce(?, source_category)
                    where food_id = ?
                    """, row.foodName(), row.defaultServingG(), row.categoryName(), existingFoodId.get());
            return existingFoodId.get();
        }
        try {
            return sqlSupport.insert("""
                    insert into food (source_name, source_food_code, food_name, default_serving_g, source_category)
                    values (?, ?, ?, ?, ?)
                    """, MFDS_SOURCE_NAME, row.foodCode(), row.foodName(), row.defaultServingG(), row.categoryName());
        } catch (DuplicateKeyException exception) {
            Optional<Long> duplicateFoodId = foodIdBySourceCode(MFDS_SOURCE_NAME, row.foodCode())
                    .or(() -> foodIdBySourceNameAndFoodName(MFDS_SOURCE_NAME, row.foodName()));
            if (duplicateFoodId.isPresent()) {
                return duplicateFoodId.get();
            }
            throw exception;
        }
    }

    private long upsertFatSecretFood(FatSecretApiClient.FoodRow row) {
        String foodName = truncate(row.foodName(), 255);
        String sourceFoodCode = truncate(row.foodCode(), 100);
        String sourceCategory = truncate(fatSecretCategory(row.foodType()), 100);
        BigDecimal defaultServingG = positiveOrDefault(row.defaultServingG(), BigDecimal.valueOf(100));
        Optional<Long> existingFoodId = foodIdBySourceCode(FATSECRET_SOURCE_NAME, sourceFoodCode);
        if (existingFoodId.isPresent()) {
            jdbcTemplate.update("""
                    update food
                    set food_name = ?,
                        default_serving_g = ?,
                        source_category = ?
                    where food_id = ?
                    """, foodName, defaultServingG, sourceCategory, existingFoodId.get());
            return existingFoodId.get();
        }
        try {
            return sqlSupport.insert("""
                    insert into food (source_name, source_food_code, food_name, default_serving_g, source_category)
                    values (?, ?, ?, ?, ?)
                    """, FATSECRET_SOURCE_NAME, sourceFoodCode, foodName, defaultServingG, sourceCategory);
        } catch (DuplicateKeyException exception) {
            Optional<Long> duplicateFoodId = foodIdBySourceCode(FATSECRET_SOURCE_NAME, sourceFoodCode)
                    .or(() -> foodIdBySourceNameAndFoodName(FATSECRET_SOURCE_NAME, foodName));
            if (duplicateFoodId.isPresent()) {
                return duplicateFoodId.get();
            }
            throw exception;
        }
    }

    private Optional<Long> foodIdBySourceCode(String sourceName, String sourceFoodCode) {
        List<Long> items = jdbcTemplate.query("""
                        select food_id
                        from food f
                        where source_name = ?
                          and source_food_code = ?
                """,
                (rs, rowNum) -> rs.getLong("food_id"),
                sourceName,
                sourceFoodCode
        );
        return items.stream().findFirst();
    }

    private Optional<Long> foodIdBySourceNameAndFoodName(String sourceName, String foodName) {
        List<Long> items = jdbcTemplate.query("""
                        select food_id
                        from food f
                        where source_name = ?
                          and food_name = ?
                        order by food_id
                        limit 1
                """,
                (rs, rowNum) -> rs.getLong("food_id"),
                sourceName,
                foodName
        );
        return items.stream().findFirst();
    }

    private void insertAlias(long foodId, String aliasName) {
        insertAlias(foodId, aliasName, "SOURCE", 10);
    }

    private void insertAlias(long foodId, String aliasName, String aliasType, int priority) {
        if (aliasName == null || aliasName.isBlank()) {
            return;
        }
        try {
            jdbcTemplate.update("""
                    insert into food_alias (food_id, alias_name, normalized_alias, alias_type, priority)
                    values (?, ?, ?, ?, ?)
                    """, foodId, aliasName.trim(), normalizeAlias(aliasName), aliasType, priority);
        } catch (DuplicateKeyException ignored) {
            // Existing aliases are stable search data.
        }
    }

    private void upsertNutrientValue(long foodId, String nutrientCode, BigDecimal amountPer100g) {
        if (amountPer100g == null) {
            return;
        }
        switch (nutrientCode) {
            case "CALORIES_KCAL" -> jdbcTemplate.update("update food set calories_kcal = ? where food_id = ?", amountPer100g, foodId);
            case "CARB_G" -> jdbcTemplate.update("update food set carb_g = ? where food_id = ?", amountPer100g, foodId);
            case "PROTEIN_G" -> jdbcTemplate.update("update food set protein_g = ? where food_id = ?", amountPer100g, foodId);
            case "FAT_G" -> jdbcTemplate.update("update food set fat_g = ? where food_id = ?", amountPer100g, foodId);
            case "SUGAR_G" -> jdbcTemplate.update("update food set sugar_g = ? where food_id = ?", amountPer100g, foodId);
            case "SODIUM_MG" -> jdbcTemplate.update("update food set sodium_mg = ? where food_id = ?", amountPer100g, foodId);
            default -> {
            }
        }
    }

    private FoodSummary foodSummary(long foodId, long userId) {
        return jdbcTemplate.query("""
                                select f.food_id,
                                       f.source_name,
                                       f.source_food_code,
                                       f.food_name,
                                       f.default_serving_g,
                                       null as matched_alias,
                                       coalesce(f.calories_kcal * f.default_serving_g / 100, 0) as calories_kcal,
                                       coalesce(f.carb_g * f.default_serving_g / 100, 0) as carb_g,
                                       coalesce(f.protein_g * f.default_serving_g / 100, 0) as protein_g,
                                       coalesce(f.fat_g * f.default_serving_g / 100, 0) as fat_g,
                                       coalesce(f.sugar_g * f.default_serving_g / 100, 0) as sugar_g,
                                       coalesce(f.sodium_mg * f.default_serving_g / 100, 0) as sodium_mg
                                from food f
                                join user_custom_food ucf on ucf.food_id = f.food_id
                                where f.food_id = ?
                                  and f.source_name = ?
                                  and ucf.user_id = ?
                                group by f.food_id, f.source_name, f.source_food_code, f.food_name, f.default_serving_g,
                                         f.calories_kcal, f.carb_g, f.protein_g, f.fat_g, f.sugar_g, f.sodium_mg
                                """,
                        (rs, rowNum) -> new FoodSummary(
                                rs.getLong("food_id"),
                                rs.getString("source_name"),
                                rs.getString("source_food_code"),
                                rs.getString("food_name"),
                                rs.getString("matched_alias"),
                                rs.getBigDecimal("default_serving_g"),
                                NutrientTotals.from(rs)
                        ),
                        foodId,
                        USER_CUSTOM_SOURCE_NAME,
                        userId
                ).stream().findFirst()
                .orElseThrow(() -> DomainException.notFound("FOOD_NOT_FOUND", "음식을 찾을 수 없습니다."));
    }

    public FoodDetail detail(long foodId) {
        return jdbcTemplate.query("""
                        select f.food_id,
                               f.source_name,
                               f.source_food_code,
                               f.food_name,
                               f.source_category,
                               f.default_serving_g,
                               coalesce(f.calories_kcal * f.default_serving_g / 100, 0) as calories_kcal,
                               coalesce(f.carb_g * f.default_serving_g / 100, 0) as carb_g,
                               coalesce(f.protein_g * f.default_serving_g / 100, 0) as protein_g,
                               coalesce(f.fat_g * f.default_serving_g / 100, 0) as fat_g,
                               coalesce(f.sugar_g * f.default_serving_g / 100, 0) as sugar_g,
                               coalesce(f.sodium_mg * f.default_serving_g / 100, 0) as sodium_mg
                        from food f
                        where f.food_id = ?
                          and f.source_name in ('MFDS_INTEGRATED', 'FATSECRET')
                        group by f.food_id, f.source_name, f.source_food_code, f.food_name, f.source_category, f.default_serving_g,
                                 f.calories_kcal, f.carb_g, f.protein_g, f.fat_g, f.sugar_g, f.sodium_mg
                        """,
                (rs, rowNum) -> new FoodDetail(
                        rs.getLong("food_id"),
                        rs.getString("source_name"),
                        rs.getString("source_food_code"),
                        rs.getString("food_name"),
                        rs.getString("source_category"),
                        rs.getBigDecimal("default_serving_g"),
                        NutrientTotals.from(rs)
                ),
                foodId
        ).stream().findFirst().orElseThrow(() -> DomainException.notFound("FOOD_NOT_FOUND", "음식을 찾을 수 없습니다."));
    }

    private String normalizeQuery(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw DomainException.badRequest("FOOD_QUERY_REQUIRED", "검색어 q가 필요합니다.");
        }
        return normalized;
    }

    private String optionalSearchQuery(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeAlias(String alias) {
        return alias.trim().toLowerCase(Locale.ROOT);
    }

    private String compactQuery(String query) {
        return query.replaceAll("\\s+", "");
    }

    private String normalizeFoodName(String foodName) {
        String normalized = foodName == null ? "" : foodName.trim();
        if (normalized.isBlank()) {
            throw DomainException.badRequest("CUSTOM_FOOD_NAME_REQUIRED", "직접 입력 음식명은 필수입니다.");
        }
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }

    private String normalizedCustomFoodName(String foodName) {
        return compactQuery(foodName.toLowerCase(Locale.ROOT));
    }

    private BigDecimal customFoodAmount(BigDecimal amountG) {
        BigDecimal amount = Optional.ofNullable(amountG).orElse(DEFAULT_CUSTOM_FOOD_AMOUNT_G);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw DomainException.badRequest("AMOUNT_INVALID", "amountG는 0보다 커야 합니다.");
        }
        return amount;
    }

    private BigDecimal per100g(BigDecimal amount, BigDecimal amountG) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw DomainException.badRequest("CUSTOM_FOOD_NUTRIENT_INVALID", "직접 입력 영양성분은 0 이상이어야 합니다.");
        }
        return amount.multiply(BigDecimal.valueOf(100)).divide(amountG, 4, RoundingMode.HALF_UP);
    }

    private BigDecimal positiveOrDefault(BigDecimal value, BigDecimal defaultValue) {
        return value == null || value.compareTo(BigDecimal.ZERO) <= 0 ? defaultValue : value;
    }

    private BigDecimal optionalAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String fatSecretCategory(String foodType) {
        if (foodType == null || foodType.isBlank()) {
            return "FatSecret";
        }
        return "FatSecret " + foodType.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    private String customFoodCode(long userId) {
        return "USER-" + userId + "-" + UUID.randomUUID();
    }
}
