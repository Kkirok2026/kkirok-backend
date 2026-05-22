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
import java.sql.ResultSet;
import java.sql.SQLException;
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

    private static final String PUBLIC_NUTRITION_SOURCE_NAME = "NATIONAL_INTEGRATED";
    private static final String USER_CUSTOM_SOURCE_NAME = "USER_CUSTOM";
    private static final BigDecimal DEFAULT_CUSTOM_FOOD_AMOUNT_G = BigDecimal.valueOf(100);
    private static final BigDecimal CARB_KCAL_PER_G = BigDecimal.valueOf(4);
    private static final BigDecimal PROTEIN_KCAL_PER_G = BigDecimal.valueOf(4);
    private static final BigDecimal FAT_KCAL_PER_G = BigDecimal.valueOf(9);

    private final JdbcTemplate jdbcTemplate;
    private final SqlSupport sqlSupport;
    private final PublicNutritionApiClient publicNutritionApiClient;

    public FoodService(
            JdbcTemplate jdbcTemplate,
            SqlSupport sqlSupport,
            PublicNutritionApiClient publicNutritionApiClient
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlSupport = sqlSupport;
        this.publicNutritionApiClient = publicNutritionApiClient;
    }

    public boolean hasPublicNutritionServiceKey() {
        return publicNutritionApiClient.hasServiceKey();
    }

    @Transactional
    public FoodSearchResponse search(String query, int limit, Optional<Long> userId) {
        String normalizedQuery = normalizeQuery(query);
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        List<String> searchQueries = searchQueries(normalizedQuery);
        Long currentUserId = userId.orElse(null);

        List<FoodSummary> items = searchLocal(searchQueries, safeLimit, currentUserId);
        if (shouldImportPublicNutrition(normalizedQuery, items, safeLimit)) {
            importPublicNutritionSearchRows(searchQueries, safeLimit);
            items = searchLocal(searchQueries, safeLimit, currentUserId);
        }
        return new FoodSearchResponse(items);
    }

    public FoodSuggestionResponse suggestions(String query, int limit, Optional<Long> userId) {
        String normalizedQuery = normalizeQuery(query);
        int safeLimit = Math.min(Math.max(limit, 1), 10);
        Map<String, String> suggestions = new LinkedHashMap<>();

        for (String searchQuery : searchQueries(normalizedQuery)) {
            String pattern = "%" + searchQuery + "%";
            String compactPattern = "%" + compactQuery(searchQuery) + "%";
            for (String suggestion : localSuggestions(pattern, compactPattern, safeLimit * 4, userId.orElse(null))) {
                addSuggestion(suggestions, suggestion);
                if (suggestions.size() >= safeLimit) {
                    break;
                }
            }
            if (suggestions.size() >= safeLimit) {
                break;
            }
        }
        return new FoodSuggestionResponse(List.copyOf(suggestions.values()));
    }

    @Transactional
    public int importPublicNutritionFoods(Collection<String> queries, int limit) {
        return importPublicNutritionRows(queries, limit);
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
        upsertNutrientValue(foodId, "CALORIES_KCAL", per100g(calculatedCaloriesKcal(request), amountG));
        upsertNutrientValue(foodId, "CARB_G", per100g(request.carbG(), amountG));
        upsertNutrientValue(foodId, "PROTEIN_G", per100g(request.proteinG(), amountG));
        upsertNutrientValue(foodId, "FAT_G", per100g(request.fatG(), amountG));
        upsertNutrientValue(foodId, "SUGAR_G", per100g(optionalAmount(request.sugarG()), amountG));
        upsertNutrientValue(foodId, "SODIUM_MG", per100g(optionalAmount(request.sodiumMg()), amountG));
        return foodSummary(foodId, userId);
    }

    private List<FoodSummary> searchLocal(List<String> searchQueries, int safeLimit, Long userId) {
        List<FoodSummary> items = new java.util.ArrayList<>();
        for (String searchQuery : searchQueries) {
            String pattern = "%" + searchQuery + "%";
            String compactPattern = "%" + compactQuery(searchQuery) + "%";
            items.addAll(searchLocal(pattern, compactPattern, safeLimit, userId));
        }
        return deduplicateSearchResults(items, safeLimit);
    }

    private List<FoodSummary> searchLocal(String pattern, String compactPattern, int safeLimit, Long userId) {
        int fetchLimit = Math.min(Math.max(safeLimit * 4, safeLimit), 200);
        List<FoodSummary> items = jdbcTemplate.query("""
                                select f.food_id,
                                       f.source_name,
                                       f.source_food_code,
                                       f.food_name,
                                       f.default_serving_g,
                                       f.nutrition_basis_amount_g,
                                       f.total_weight_g,
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
                                       coalesce(f.sodium_mg * f.default_serving_g / 100, 0) as sodium_mg,
                                       coalesce(f.calories_kcal * f.nutrition_basis_amount_g / 100, 0) as basis_calories_kcal,
                                       coalesce(f.carb_g * f.nutrition_basis_amount_g / 100, 0) as basis_carb_g,
                                       coalesce(f.protein_g * f.nutrition_basis_amount_g / 100, 0) as basis_protein_g,
                                       coalesce(f.fat_g * f.nutrition_basis_amount_g / 100, 0) as basis_fat_g,
                                       coalesce(f.sugar_g * f.nutrition_basis_amount_g / 100, 0) as basis_sugar_g,
                                       coalesce(f.sodium_mg * f.nutrition_basis_amount_g / 100, 0) as basis_sodium_mg,
                                       coalesce(f.calories_kcal * coalesce(f.total_weight_g, f.nutrition_basis_amount_g) / 100, 0) as total_calories_kcal,
                                       coalesce(f.carb_g * coalesce(f.total_weight_g, f.nutrition_basis_amount_g) / 100, 0) as total_carb_g,
                                       coalesce(f.protein_g * coalesce(f.total_weight_g, f.nutrition_basis_amount_g) / 100, 0) as total_protein_g,
                                       coalesce(f.fat_g * coalesce(f.total_weight_g, f.nutrition_basis_amount_g) / 100, 0) as total_fat_g,
                                       coalesce(f.sugar_g * coalesce(f.total_weight_g, f.nutrition_basis_amount_g) / 100, 0) as total_sugar_g,
                                       coalesce(f.sodium_mg * coalesce(f.total_weight_g, f.nutrition_basis_amount_g) / 100, 0) as total_sodium_mg
                                from food f
                                where (
                                      f.source_name = ?
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
                                      or f.food_name like ?
                                      or replace(f.food_name, ' ', '') like ?
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
                                         f.nutrition_basis_amount_g, f.total_weight_g,
                                         f.calories_kcal, f.carb_g, f.protein_g, f.fat_g, f.sugar_g, f.sodium_mg
                                order by case
                                             when f.source_name = 'USER_CUSTOM' then 0
                                             when f.source_name = 'NATIONAL_INTEGRATED' then 1
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
                                rs.getBigDecimal("nutrition_basis_amount_g"),
                                rs.getBigDecimal("total_weight_g"),
                                NutrientTotals.from(rs),
                                nutrients(rs, "basis_"),
                                nutrients(rs, "total_")
                        ),
                        pattern,
                        compactPattern,
                        PUBLIC_NUTRITION_SOURCE_NAME,
                        userId,
                        userId,
                        pattern,
                        compactPattern,
                        pattern,
                        compactPattern,
                        pattern,
                        compactPattern,
                        fetchLimit
                );
        return deduplicateSearchResults(items, safeLimit);
    }

    private List<String> localSuggestions(String pattern, String compactPattern, int fetchLimit, Long userId) {
        List<String> items = new java.util.ArrayList<>();
        items.addAll(jdbcTemplate.query("""
                        select f.food_name as suggestion
                        from food f
                        where (
                              f.source_name = ?
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
                              or f.food_name like ?
                              or replace(f.food_name, ' ', '') like ?
                          )
                        order by case
                                     when f.source_name = 'USER_CUSTOM' then 0
                                     when f.source_name = 'NATIONAL_INTEGRATED' then 1
                                     else 3
                                 end,
                                 f.food_name
                        limit ?
                        """,
                (rs, rowNum) -> rs.getString("suggestion"),
                PUBLIC_NUTRITION_SOURCE_NAME,
                userId,
                USER_CUSTOM_SOURCE_NAME,
                userId,
                pattern,
                compactPattern,
                pattern,
                compactPattern,
                fetchLimit
        ));
        items.addAll(jdbcTemplate.query("""
                        select a.alias_name as suggestion
                        from food_alias a
                        join food f on f.food_id = a.food_id
                        where (
                              f.source_name = ?
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
                                     when f.source_name = 'USER_CUSTOM' then 0
                                     when f.source_name = 'NATIONAL_INTEGRATED' then 1
                                     else 3
                                 end,
                                 a.priority desc,
                                 a.alias_name
                        limit ?
                        """,
                (rs, rowNum) -> rs.getString("suggestion"),
                PUBLIC_NUTRITION_SOURCE_NAME,
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

    private List<FoodSummary> deduplicateSearchResults(List<FoodSummary> items, int limit) {
        Map<String, FoodSummary> deduplicated = new LinkedHashMap<>();
        for (FoodSummary item : items) {
            String key = searchResultDeduplicationKey(item);
            deduplicated.putIfAbsent(key, item);
            if (deduplicated.size() >= limit) {
                break;
            }
        }
        return List.copyOf(deduplicated.values());
    }

    private String searchResultDeduplicationKey(FoodSummary item) {
        if (!USER_CUSTOM_SOURCE_NAME.equals(item.sourceName())) {
            String foodNameKey = foodNameDeduplicationKey(item.foodName());
            if (!foodNameKey.isBlank()) {
                return item.sourceName() + ":" + foodNameKey;
            }
            if (item.sourceFoodCode() != null && !item.sourceFoodCode().isBlank()) {
                return item.sourceName() + ":" + item.sourceFoodCode();
            }
        }
        String foodName = item.foodName() == null ? "" : item.foodName();
        String normalizedFoodName = foodName.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-()/]+", "");
        if (!normalizedFoodName.isBlank()) {
            return item.sourceName() + ":" + normalizedFoodName;
        }
        return item.sourceName() + ":" + item.foodId();
    }

    private String suggestionDeduplicationKey(String suggestion) {
        return suggestion.toLowerCase(Locale.ROOT).replaceAll("[\\s_\\-()/]+", "");
    }

    private String foodNameDeduplicationKey(String foodName) {
        return foodName == null ? "" : foodName.trim().toLowerCase(Locale.ROOT);
    }

    private boolean shouldImportPublicNutrition(String normalizedQuery, List<FoodSummary> localItems, int safeLimit) {
        if (localItems.isEmpty()) {
            return true;
        }
        return localItems.size() < safeLimit;
    }

    private int importPublicNutritionRows(String query, int limit) {
        Set<String> queries = new LinkedHashSet<>();
        queries.add(query);
        queries.add(compactQuery(query));
        return importPublicNutritionRows(queries, limit);
    }

    private int importPublicNutritionSearchRows(Collection<String> queries, int limit) {
        int importedCount = 0;
        Set<String> importedFoodNames = new LinkedHashSet<>();
        for (String query : queries) {
            List<PublicNutritionApiClient.NutritionRow> rows;
            try {
                rows = publicNutritionApiClient.searchFoodsContaining(query, limit);
            } catch (DomainException exception) {
                if ("PUBLIC_NUTRITION_API_FAILED".equals(exception.code())) {
                    continue;
                }
                throw exception;
            }
            for (PublicNutritionApiClient.NutritionRow row : rows) {
                if (importPublicNutritionRowIfNewName(row, importedFoodNames)) {
                    importedCount++;
                }
            }
        }
        return importedCount;
    }

    private int importPublicNutritionRows(Collection<String> queries, int limit) {
        if (!publicNutritionApiClient.hasServiceKey()) {
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
        Set<String> importedFoodNames = new LinkedHashSet<>();
        for (String apiQuery : apiQueries) {
            List<PublicNutritionApiClient.NutritionRow> rows;
            try {
                rows = publicNutritionApiClient.searchFoods(apiQuery, safeLimit);
            } catch (DomainException exception) {
                if ("PUBLIC_NUTRITION_API_FAILED".equals(exception.code())) {
                    return importedCount;
                }
                throw exception;
            }
            for (PublicNutritionApiClient.NutritionRow row : rows) {
                if (importPublicNutritionRowIfNewName(row, importedFoodNames)) {
                    importedCount++;
                }
            }
        }
        return importedCount;
    }

    private boolean importPublicNutritionRowIfNewName(PublicNutritionApiClient.NutritionRow row, Set<String> importedFoodNames) {
        if (!row.hasImportableData()) {
            return false;
        }
        String foodNameKey = foodNameDeduplicationKey(row.foodName());
        if (foodNameKey.isBlank() || !importedFoodNames.add(foodNameKey)) {
            return false;
        }
        if (foodIdBySourceNameAndFoodName(PUBLIC_NUTRITION_SOURCE_NAME, row.foodName()).isPresent()) {
            return false;
        }
        importPublicNutritionRow(row);
        return true;
    }

    private void importPublicNutritionRow(PublicNutritionApiClient.NutritionRow row) {
        long foodId = upsertFood(row);
        insertAlias(foodId, row.foodName());
        upsertNutrientValue(foodId, "CALORIES_KCAL", row.caloriesKcal());
        upsertNutrientValue(foodId, "CARB_G", row.carbG());
        upsertNutrientValue(foodId, "PROTEIN_G", row.proteinG());
        upsertNutrientValue(foodId, "FAT_G", row.fatG());
        upsertNutrientValue(foodId, "SUGAR_G", row.sugarG());
        upsertNutrientValue(foodId, "SODIUM_MG", row.sodiumMg());
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
                insert into food (source_name, source_food_code, food_name, default_serving_g, nutrition_basis_amount_g, total_weight_g)
                values (?, ?, ?, ?, ?, ?)
                """, USER_CUSTOM_SOURCE_NAME, customFoodCode(userId), foodName, amountG, amountG, amountG);
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
                    nutrition_basis_amount_g = ?,
                    total_weight_g = ?
                where food_id = ?
                  and source_name = ?
                """, foodName, amountG, amountG, amountG, foodId, USER_CUSTOM_SOURCE_NAME);
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

    private long upsertFood(PublicNutritionApiClient.NutritionRow row) {
        Optional<Long> existingFoodId = foodIdBySourceCode(PUBLIC_NUTRITION_SOURCE_NAME, row.foodCode());
        if (existingFoodId.isPresent()) {
            jdbcTemplate.update("""
                    update food
                    set food_name = ?,
                        default_serving_g = ?,
                        nutrition_basis_amount_g = ?,
                        total_weight_g = ?
                    where food_id = ?
                    """, row.foodName(), row.nutritionBasisAmountG(), row.nutritionBasisAmountG(), row.totalWeightG(), existingFoodId.get());
            return existingFoodId.get();
        }
        try {
            return sqlSupport.insert("""
                    insert into food (source_name, source_food_code, food_name, default_serving_g, nutrition_basis_amount_g, total_weight_g)
                    values (?, ?, ?, ?, ?, ?)
                    """, PUBLIC_NUTRITION_SOURCE_NAME, row.foodCode(), row.foodName(), row.nutritionBasisAmountG(), row.nutritionBasisAmountG(), row.totalWeightG());
        } catch (DuplicateKeyException exception) {
            Optional<Long> duplicateFoodId = foodIdBySourceCode(PUBLIC_NUTRITION_SOURCE_NAME, row.foodCode())
                    .or(() -> foodIdBySourceNameAndFoodName(PUBLIC_NUTRITION_SOURCE_NAME, row.foodName()));
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
                                       f.nutrition_basis_amount_g,
                                       f.total_weight_g,
                                       null as matched_alias,
                                       coalesce(f.calories_kcal * f.default_serving_g / 100, 0) as calories_kcal,
                                       coalesce(f.carb_g * f.default_serving_g / 100, 0) as carb_g,
                                       coalesce(f.protein_g * f.default_serving_g / 100, 0) as protein_g,
                                       coalesce(f.fat_g * f.default_serving_g / 100, 0) as fat_g,
                                       coalesce(f.sugar_g * f.default_serving_g / 100, 0) as sugar_g,
                                       coalesce(f.sodium_mg * f.default_serving_g / 100, 0) as sodium_mg,
                                       coalesce(f.calories_kcal * f.nutrition_basis_amount_g / 100, 0) as basis_calories_kcal,
                                       coalesce(f.carb_g * f.nutrition_basis_amount_g / 100, 0) as basis_carb_g,
                                       coalesce(f.protein_g * f.nutrition_basis_amount_g / 100, 0) as basis_protein_g,
                                       coalesce(f.fat_g * f.nutrition_basis_amount_g / 100, 0) as basis_fat_g,
                                       coalesce(f.sugar_g * f.nutrition_basis_amount_g / 100, 0) as basis_sugar_g,
                                       coalesce(f.sodium_mg * f.nutrition_basis_amount_g / 100, 0) as basis_sodium_mg,
                                       coalesce(f.calories_kcal * coalesce(f.total_weight_g, f.nutrition_basis_amount_g) / 100, 0) as total_calories_kcal,
                                       coalesce(f.carb_g * coalesce(f.total_weight_g, f.nutrition_basis_amount_g) / 100, 0) as total_carb_g,
                                       coalesce(f.protein_g * coalesce(f.total_weight_g, f.nutrition_basis_amount_g) / 100, 0) as total_protein_g,
                                       coalesce(f.fat_g * coalesce(f.total_weight_g, f.nutrition_basis_amount_g) / 100, 0) as total_fat_g,
                                       coalesce(f.sugar_g * coalesce(f.total_weight_g, f.nutrition_basis_amount_g) / 100, 0) as total_sugar_g,
                                       coalesce(f.sodium_mg * coalesce(f.total_weight_g, f.nutrition_basis_amount_g) / 100, 0) as total_sodium_mg
                                from food f
                                join user_custom_food ucf on ucf.food_id = f.food_id
                                where f.food_id = ?
                                  and f.source_name = ?
                                  and ucf.user_id = ?
                                group by f.food_id, f.source_name, f.source_food_code, f.food_name, f.default_serving_g,
                                         f.nutrition_basis_amount_g, f.total_weight_g,
                                         f.calories_kcal, f.carb_g, f.protein_g, f.fat_g, f.sugar_g, f.sodium_mg
                                """,
                        (rs, rowNum) -> new FoodSummary(
                                rs.getLong("food_id"),
                                rs.getString("source_name"),
                                rs.getString("source_food_code"),
                                rs.getString("food_name"),
                                rs.getString("matched_alias"),
                                rs.getBigDecimal("default_serving_g"),
                                rs.getBigDecimal("nutrition_basis_amount_g"),
                                rs.getBigDecimal("total_weight_g"),
                                NutrientTotals.from(rs),
                                nutrients(rs, "basis_"),
                                nutrients(rs, "total_")
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
                               f.default_serving_g,
                               f.nutrition_basis_amount_g,
                               f.total_weight_g,
                               coalesce(f.calories_kcal * f.default_serving_g / 100, 0) as calories_kcal,
                               coalesce(f.carb_g * f.default_serving_g / 100, 0) as carb_g,
                               coalesce(f.protein_g * f.default_serving_g / 100, 0) as protein_g,
                               coalesce(f.fat_g * f.default_serving_g / 100, 0) as fat_g,
                               coalesce(f.sugar_g * f.default_serving_g / 100, 0) as sugar_g,
                               coalesce(f.sodium_mg * f.default_serving_g / 100, 0) as sodium_mg,
                               coalesce(f.calories_kcal * f.nutrition_basis_amount_g / 100, 0) as basis_calories_kcal,
                               coalesce(f.carb_g * f.nutrition_basis_amount_g / 100, 0) as basis_carb_g,
                               coalesce(f.protein_g * f.nutrition_basis_amount_g / 100, 0) as basis_protein_g,
                               coalesce(f.fat_g * f.nutrition_basis_amount_g / 100, 0) as basis_fat_g,
                               coalesce(f.sugar_g * f.nutrition_basis_amount_g / 100, 0) as basis_sugar_g,
                               coalesce(f.sodium_mg * f.nutrition_basis_amount_g / 100, 0) as basis_sodium_mg,
                               coalesce(f.calories_kcal * coalesce(f.total_weight_g, f.nutrition_basis_amount_g) / 100, 0) as total_calories_kcal,
                               coalesce(f.carb_g * coalesce(f.total_weight_g, f.nutrition_basis_amount_g) / 100, 0) as total_carb_g,
                               coalesce(f.protein_g * coalesce(f.total_weight_g, f.nutrition_basis_amount_g) / 100, 0) as total_protein_g,
                               coalesce(f.fat_g * coalesce(f.total_weight_g, f.nutrition_basis_amount_g) / 100, 0) as total_fat_g,
                               coalesce(f.sugar_g * coalesce(f.total_weight_g, f.nutrition_basis_amount_g) / 100, 0) as total_sugar_g,
                               coalesce(f.sodium_mg * coalesce(f.total_weight_g, f.nutrition_basis_amount_g) / 100, 0) as total_sodium_mg
                        from food f
                        where f.food_id = ?
                          and f.source_name = 'NATIONAL_INTEGRATED'
                        group by f.food_id, f.source_name, f.source_food_code, f.food_name, f.default_serving_g,
                                 f.nutrition_basis_amount_g, f.total_weight_g,
                                 f.calories_kcal, f.carb_g, f.protein_g, f.fat_g, f.sugar_g, f.sodium_mg
                        """,
                (rs, rowNum) -> new FoodDetail(
                        rs.getLong("food_id"),
                        rs.getString("source_name"),
                        rs.getString("source_food_code"),
                        rs.getString("food_name"),
                        rs.getBigDecimal("default_serving_g"),
                        rs.getBigDecimal("nutrition_basis_amount_g"),
                        rs.getBigDecimal("total_weight_g"),
                        NutrientTotals.from(rs),
                        nutrients(rs, "basis_"),
                        nutrients(rs, "total_")
                ),
                foodId
        ).stream().findFirst().orElseThrow(() -> DomainException.notFound("FOOD_NOT_FOUND", "음식을 찾을 수 없습니다."));
    }

    private NutrientTotals nutrients(ResultSet rs, String prefix) throws SQLException {
        return new NutrientTotals(
                value(rs, prefix + "calories_kcal"),
                value(rs, prefix + "carb_g"),
                value(rs, prefix + "protein_g"),
                value(rs, prefix + "fat_g"),
                value(rs, prefix + "sugar_g"),
                value(rs, prefix + "sodium_mg")
        );
    }

    private BigDecimal value(ResultSet rs, String columnName) throws SQLException {
        BigDecimal value = rs.getBigDecimal(columnName);
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeQuery(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw DomainException.badRequest("FOOD_QUERY_REQUIRED", "검색어 q가 필요합니다.");
        }
        return normalized;
    }

    private List<String> searchQueries(String query) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        queries.add(query);
        return List.copyOf(queries);
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

    private BigDecimal calculatedCaloriesKcal(CustomFoodCreateRequest request) {
        return nonNegativeAmount(request.carbG()).multiply(CARB_KCAL_PER_G)
                .add(nonNegativeAmount(request.proteinG()).multiply(PROTEIN_KCAL_PER_G))
                .add(nonNegativeAmount(request.fatG()).multiply(FAT_KCAL_PER_G));
    }

    private BigDecimal nonNegativeAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) < 0) {
            throw DomainException.badRequest("CUSTOM_FOOD_NUTRIENT_INVALID", "직접 입력 영양성분은 0 이상이어야 합니다.");
        }
        return amount;
    }

    private BigDecimal optionalAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String customFoodCode(long userId) {
        return "USER-" + userId + "-" + UUID.randomUUID();
    }
}
