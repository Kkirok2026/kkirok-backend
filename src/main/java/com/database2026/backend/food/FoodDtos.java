package com.database2026.backend.food;

import com.database2026.backend.common.NutrientTotals;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

public final class FoodDtos {

    private FoodDtos() {
    }

    public record FoodSearchResponse(List<FoodSummary> items) {
    }

    public record FoodSuggestionResponse(List<String> items) {
    }

    @Schema(description = "검색 결과에 없는 음식을 내 개인 음식으로 직접 등록하는 요청. 입력한 영양값은 amountG에 해당하는 섭취량 기준입니다.")
    public record CustomFoodCreateRequest(
            @NotBlank @Schema(description = "사용자가 입력한 음식명", example = "저지방 우유") String foodName,
            @Positive @Schema(description = "입력한 영양값이 해당하는 섭취량(g). 모르면 생략 가능하며 100g으로 저장합니다.", example = "200") BigDecimal amountG,
            @NotNull @DecimalMin("0.0") @Schema(description = "입력한 섭취량 기준 열량(kcal)", example = "80") BigDecimal caloriesKcal,
            @NotNull @DecimalMin("0.0") @Schema(description = "입력한 섭취량 기준 탄수화물(g)", example = "10") BigDecimal carbG,
            @NotNull @DecimalMin("0.0") @Schema(description = "입력한 섭취량 기준 단백질(g)", example = "6") BigDecimal proteinG,
            @NotNull @DecimalMin("0.0") @Schema(description = "입력한 섭취량 기준 지방(g)", example = "2") BigDecimal fatG,
            @DecimalMin("0.0") @Schema(description = "입력한 섭취량 기준 당류(g). 모르면 생략합니다.", example = "10") BigDecimal sugarG,
            @DecimalMin("0.0") @Schema(description = "입력한 섭취량 기준 나트륨(mg). 모르면 생략합니다.", example = "100") BigDecimal sodiumMg
    ) {
    }

    public record FoodSummary(
            Long foodId,
            String sourceName,
            String sourceFoodCode,
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
