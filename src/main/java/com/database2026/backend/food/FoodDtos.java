package com.database2026.backend.food;

import com.database2026.backend.common.NutrientTotals;
import java.math.BigDecimal;
import java.util.List;

public final class FoodDtos {

    private FoodDtos() {
    }

    public record FoodSearchResponse(List<FoodSummary> items) {
    }

    public record FoodSummary(
            Long foodId,
            String foodName,
            String matchedAlias,
            BigDecimal defaultServingG,
            NutrientTotals nutrients
    ) {
    }

    public record FoodDetail(
            Long foodId,
            String sourceName,
            String sourceFoodCode,
            String foodName,
            String sourceCategory,
            BigDecimal defaultServingG,
            NutrientTotals nutrients
    ) {
    }
}
