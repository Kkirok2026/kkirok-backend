package com.database2026.backend.meal;

import com.database2026.backend.common.NutrientTotals;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class MealDtos {

    private MealDtos() {
    }

    public record MealLogCreateRequest(
            @NotNull LocalDate logDate,
            @NotBlank @Schema(allowableValues = {"BREAKFAST", "LUNCH", "DINNER", "SNACK"}) String mealType,
            String memo,
            @NotEmpty List<@Valid MealLogItemRequest> items
    ) {
    }

    public record MealLogItemRequest(
            Long foodId,
            @Positive BigDecimal amountG,
            Long menuOptionId
    ) {
    }

    public record MealLogResponse(
            Long mealLogId,
            LocalDate logDate,
            String mealType,
            String memo,
            List<MealLogItemResponse> items,
            NutrientTotals totals
    ) {
    }

    public record MealLogItemResponse(
            Long dietItemId,
            Long foodId,
            Long sourceOptionId,
            String itemName,
            BigDecimal amountG,
            Boolean excluded,
            NutrientTotals nutrients
    ) {
    }

    public record MealLogListResponse(List<MealLogResponse> items) {
    }

    public record DailySummaryResponse(
            LocalDate date,
            NutrientTotals totals,
            List<NutritionWarning> warnings
    ) {
    }

    public record NutritionWarning(
            String nutrientCode,
            String nutrientName,
            BigDecimal actualAmount,
            BigDecimal recommendedAmount,
            BigDecimal upperLimitAmount,
            String message
    ) {
    }
}
