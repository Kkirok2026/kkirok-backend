package com.database2026.backend.food;

import com.database2026.backend.common.DomainException;
import com.database2026.backend.common.NutrientTotals;
import com.database2026.backend.food.FoodDtos.FoodDetail;
import com.database2026.backend.food.FoodDtos.FoodSearchResponse;
import com.database2026.backend.food.FoodDtos.FoodSummary;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FoodService {

    private final JdbcTemplate jdbcTemplate;

    public FoodService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public FoodSearchResponse search(String query, int limit) {
        String normalizedQuery = normalizeQuery(query);
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        String pattern = "%" + normalizedQuery + "%";
        List<FoodSummary> items = jdbcTemplate.query("""
                        select f.food_id,
                               f.food_name,
                               f.default_serving_g,
                               (
                                   select min(a.alias_name)
                                   from food_alias a
                                   where a.food_id = f.food_id
                                     and lower(a.normalized_alias) like ?
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
                        where lower(f.food_name) like ?
                           or exists (
                               select 1
                               from food_alias a
                               where a.food_id = f.food_id
                                 and lower(a.normalized_alias) like ?
                           )
                        group by f.food_id, f.food_name, f.default_serving_g
                        order by f.food_name
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
                pattern,
                pattern,
                safeLimit
        );
        return new FoodSearchResponse(items);
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
}
