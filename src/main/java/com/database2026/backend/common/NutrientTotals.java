package com.database2026.backend.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;

public record NutrientTotals(
        BigDecimal caloriesKcal,
        BigDecimal carbG,
        BigDecimal proteinG,
        BigDecimal fatG,
        BigDecimal sugarG,
        BigDecimal sodiumMg
) {

    public static NutrientTotals from(ResultSet rs) throws SQLException {
        return new NutrientTotals(
                value(rs, "calories_kcal"),
                value(rs, "carb_g"),
                value(rs, "protein_g"),
                value(rs, "fat_g"),
                value(rs, "sugar_g"),
                value(rs, "sodium_mg")
        );
    }

    private static BigDecimal value(ResultSet rs, String columnName) throws SQLException {
        BigDecimal value = rs.getBigDecimal(columnName);
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value.setScale(2, RoundingMode.HALF_UP);
    }
}
