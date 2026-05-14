package com.database2026.backend.food;

import com.database2026.backend.common.DomainException;
import com.database2026.backend.common.NutrientTotals;
import com.database2026.backend.food.FoodDtos.CustomFoodCreateRequest;
import com.database2026.backend.food.FoodDtos.FoodDetail;
import com.database2026.backend.food.FoodDtos.FoodSearchResponse;
import com.database2026.backend.food.FoodDtos.FoodSummary;
import com.database2026.backend.support.SqlSupport;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    private static final String USER_CUSTOM_SOURCE_NAME = "USER_CUSTOM";
    private static final BigDecimal DEFAULT_CUSTOM_FOOD_AMOUNT_G = BigDecimal.valueOf(100);

    private final JdbcTemplate jdbcTemplate;
    private final SqlSupport sqlSupport;
    private final MfdsNutritionApiClient mfdsNutritionApiClient;

    public FoodService(JdbcTemplate jdbcTemplate, SqlSupport sqlSupport, MfdsNutritionApiClient mfdsNutritionApiClient) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlSupport = sqlSupport;
        this.mfdsNutritionApiClient = mfdsNutritionApiClient;
    }

    @Transactional
    public FoodSearchResponse search(String query, int limit, Optional<Long> userId) {
        String normalizedQuery = normalizeQuery(query);
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        String pattern = "%" + normalizedQuery + "%";
        String compactPattern = "%" + compactQuery(normalizedQuery) + "%";
        Long currentUserId = userId.orElse(null);
        List<FoodSummary> items = searchLocal(pattern, compactPattern, safeLimit, currentUserId);
        if (items.isEmpty()) {
            importMfdsNutritionRows(normalizedQuery, safeLimit);
            items = searchLocal(pattern, compactPattern, safeLimit, currentUserId);
        }
        return new FoodSearchResponse(items);
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
        return jdbcTemplate.query("""
                                select f.food_id,
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
                                       coalesce(sum(case when n.nutrient_code = 'CALORIES_KCAL' then v.amount_per_100g * f.default_serving_g / 100 end), 0) as calories_kcal,
                                       coalesce(sum(case when n.nutrient_code = 'CARB_G' then v.amount_per_100g * f.default_serving_g / 100 end), 0) as carb_g,
                                       coalesce(sum(case when n.nutrient_code = 'PROTEIN_G' then v.amount_per_100g * f.default_serving_g / 100 end), 0) as protein_g,
                                       coalesce(sum(case when n.nutrient_code = 'FAT_G' then v.amount_per_100g * f.default_serving_g / 100 end), 0) as fat_g,
                                       coalesce(sum(case when n.nutrient_code = 'SUGAR_G' then v.amount_per_100g * f.default_serving_g / 100 end), 0) as sugar_g,
                                       coalesce(sum(case when n.nutrient_code = 'SODIUM_MG' then v.amount_per_100g * f.default_serving_g / 100 end), 0) as sodium_mg
                                from food f
                                join food_nutrient_value v on v.food_id = f.food_id
                                join nutrient n on n.nutrient_id = v.nutrient_id
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
                                group by f.food_id, f.food_name, f.default_serving_g
                                order by case when f.source_name = 'USER_CUSTOM' then 0 else 1 end,
                                         f.food_name
                                limit ?
                                """,
                        (rs, rowNum) -> new FoodSummary(
                                rs.getLong("food_id"),
                                rs.getString("food_name"),
                                rs.getString("matched_alias"),
                                rs.getBigDecimal("default_serving_g"),
                                NutrientTotals.from(rs)
                        ),
                        pattern,
                        compactPattern,
                        MFDS_SOURCE_NAME,
                        userId,
                        userId,
                        pattern,
                        compactPattern,
                        pattern,
                        compactPattern,
                        safeLimit
                );
    }

    private void importMfdsNutritionRows(String query, int limit) {
        Set<String> queries = new LinkedHashSet<>();
        queries.add(query);
        queries.add(compactQuery(query));
        for (String apiQuery : queries) {
            for (MfdsNutritionApiClient.NutritionRow row : mfdsNutritionApiClient.searchFoods(apiQuery, limit)) {
                long foodId = upsertFood(row);
                insertAlias(foodId, row.foodName());
                upsertNutrientValue(foodId, "CALORIES_KCAL", row.caloriesKcal());
                upsertNutrientValue(foodId, "CARB_G", row.carbG());
                upsertNutrientValue(foodId, "PROTEIN_G", row.proteinG());
                upsertNutrientValue(foodId, "FAT_G", row.fatG());
                upsertNutrientValue(foodId, "SUGAR_G", row.sugarG());
                upsertNutrientValue(foodId, "SODIUM_MG", row.sodiumMg());
            }
        }
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
                    serving_amount_g = ?,
                    updated_at = current_timestamp
                where user_id = ?
                  and food_id = ?
                """, foodName, normalizedFoodName, amountG, userId, foodId);
        return foodId;
    }

    private long upsertFood(MfdsNutritionApiClient.NutritionRow row) {
        Optional<Long> existingFoodId = foodIdBySourceCode(row.foodCode());
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
            return foodIdBySourceCode(row.foodCode()).orElseThrow();
        }
    }

    private Optional<Long> foodIdBySourceCode(String sourceFoodCode) {
        List<Long> items = jdbcTemplate.query("""
                        select food_id
                        from food f
                        where source_name = ?
                          and source_food_code = ?
                        """,
                (rs, rowNum) -> rs.getLong("food_id"),
                MFDS_SOURCE_NAME,
                sourceFoodCode
        );
        return items.stream().findFirst();
    }

    private void insertAlias(long foodId, String aliasName) {
        if (aliasName == null || aliasName.isBlank()) {
            return;
        }
        try {
            jdbcTemplate.update("""
                    insert into food_alias (food_id, alias_name, normalized_alias, alias_type, priority)
                    values (?, ?, ?, 'SOURCE', 10)
                    """, foodId, aliasName.trim(), normalizeAlias(aliasName));
        } catch (DuplicateKeyException ignored) {
            // Existing aliases are stable search data.
        }
    }

    private void upsertNutrientValue(long foodId, String nutrientCode, BigDecimal amountPer100g) {
        if (amountPer100g == null) {
            return;
        }
        Optional<Long> nutrientId = nutrientId(nutrientCode);
        if (nutrientId.isEmpty()) {
            return;
        }
        int updated = jdbcTemplate.update("""
                update food_nutrient_value
                set amount_per_100g = ?
                where food_id = ?
                  and nutrient_id = ?
                """, amountPer100g, foodId, nutrientId.get());
        if (updated == 0) {
            jdbcTemplate.update("""
                    insert into food_nutrient_value (food_id, nutrient_id, amount_per_100g)
                    values (?, ?, ?)
                    """, foodId, nutrientId.get(), amountPer100g);
        }
    }

    private FoodSummary foodSummary(long foodId, long userId) {
        return jdbcTemplate.query("""
                                select f.food_id,
                                       f.food_name,
                                       f.default_serving_g,
                                       null as matched_alias,
                                       coalesce(sum(case when n.nutrient_code = 'CALORIES_KCAL' then v.amount_per_100g * f.default_serving_g / 100 end), 0) as calories_kcal,
                                       coalesce(sum(case when n.nutrient_code = 'CARB_G' then v.amount_per_100g * f.default_serving_g / 100 end), 0) as carb_g,
                                       coalesce(sum(case when n.nutrient_code = 'PROTEIN_G' then v.amount_per_100g * f.default_serving_g / 100 end), 0) as protein_g,
                                       coalesce(sum(case when n.nutrient_code = 'FAT_G' then v.amount_per_100g * f.default_serving_g / 100 end), 0) as fat_g,
                                       coalesce(sum(case when n.nutrient_code = 'SUGAR_G' then v.amount_per_100g * f.default_serving_g / 100 end), 0) as sugar_g,
                                       coalesce(sum(case when n.nutrient_code = 'SODIUM_MG' then v.amount_per_100g * f.default_serving_g / 100 end), 0) as sodium_mg
                                from food f
                                join user_custom_food ucf on ucf.food_id = f.food_id
                                left join food_nutrient_value v on v.food_id = f.food_id
                                left join nutrient n on n.nutrient_id = v.nutrient_id
                                where f.food_id = ?
                                  and f.source_name = ?
                                  and ucf.user_id = ?
                                group by f.food_id, f.food_name, f.default_serving_g
                                """,
                        (rs, rowNum) -> new FoodSummary(
                                rs.getLong("food_id"),
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

    private Optional<Long> nutrientId(String nutrientCode) {
        return jdbcTemplate.query("""
                        select nutrient_id
                        from nutrient
                        where nutrient_code = ?
                        """,
                (rs, rowNum) -> rs.getLong("nutrient_id"),
                nutrientCode
        ).stream().findFirst();
    }

    public FoodDetail detail(long foodId) {
        return jdbcTemplate.query("""
                        select f.food_id,
                               f.source_name,
                               f.source_food_code,
                               f.food_name,
                               f.source_category,
                               f.default_serving_g,
                               coalesce(sum(case when n.nutrient_code = 'CALORIES_KCAL' then v.amount_per_100g * f.default_serving_g / 100 end), 0) as calories_kcal,
                               coalesce(sum(case when n.nutrient_code = 'CARB_G' then v.amount_per_100g * f.default_serving_g / 100 end), 0) as carb_g,
                               coalesce(sum(case when n.nutrient_code = 'PROTEIN_G' then v.amount_per_100g * f.default_serving_g / 100 end), 0) as protein_g,
                               coalesce(sum(case when n.nutrient_code = 'FAT_G' then v.amount_per_100g * f.default_serving_g / 100 end), 0) as fat_g,
                               coalesce(sum(case when n.nutrient_code = 'SUGAR_G' then v.amount_per_100g * f.default_serving_g / 100 end), 0) as sugar_g,
                               coalesce(sum(case when n.nutrient_code = 'SODIUM_MG' then v.amount_per_100g * f.default_serving_g / 100 end), 0) as sodium_mg
                        from food f
                        join food_nutrient_value v on v.food_id = f.food_id
                        join nutrient n on n.nutrient_id = v.nutrient_id
                        where f.food_id = ?
                          and f.source_name = 'MFDS_INTEGRATED'
                        group by f.food_id, f.source_name, f.source_food_code, f.food_name, f.source_category, f.default_serving_g
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

    private BigDecimal optionalAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String customFoodCode(long userId) {
        return "USER-" + userId + "-" + UUID.randomUUID();
    }
}
