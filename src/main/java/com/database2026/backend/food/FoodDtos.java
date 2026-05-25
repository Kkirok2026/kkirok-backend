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

    @Schema(description = "검색 결과에 없는 음식을 내 개인 음식으로 직접 등록하는 요청. 입력한 영양값은 amountG 기준이며 열량은 탄수화물/단백질/지방으로 자동 계산합니다.")
    public record CustomFoodCreateRequest(
            @NotBlank @Schema(description = "사용자가 입력한 음식명", example = "저지방 우유") String foodName,
            @Positive @Schema(description = "입력한 영양값의 기준량(g). 모르면 생략 가능하며 100g으로 저장합니다.", example = "200") BigDecimal amountG,
            @Positive @Schema(description = "음식의 총 식품 중량(g). 모르면 생략 가능하며 기준량으로 저장합니다.", example = "500") BigDecimal totalWeightG,
            @DecimalMin("0.0") @Schema(description = "100g당 열량(kcal). 생략하면 총 식품 중량별 열량 또는 탄수화물/단백질/지방으로 계산합니다.", example = "64") BigDecimal basisCaloriesKcal,
            @DecimalMin("0.0") @Schema(description = "총 식품 중량 기준 열량(kcal). 생략하면 100g당 열량 또는 탄수화물/단백질/지방으로 계산합니다.", example = "320") BigDecimal totalCaloriesKcal,
            @DecimalMin("0.0") @Schema(description = "Deprecated. 직접 입력 음식의 열량은 carbG/proteinG/fatG로 자동 계산하므로 보내지 않아도 됩니다.", example = "82") BigDecimal caloriesKcal,
            @NotNull @DecimalMin("0.0") @Schema(description = "입력한 기준량의 탄수화물(g)", example = "10") BigDecimal carbG,
            @NotNull @DecimalMin("0.0") @Schema(description = "입력한 기준량의 단백질(g)", example = "6") BigDecimal proteinG,
            @NotNull @DecimalMin("0.0") @Schema(description = "입력한 기준량의 지방(g)", example = "2") BigDecimal fatG,
            @DecimalMin("0.0") @Schema(description = "입력한 기준량의 당류(g). 모르면 생략합니다.", example = "10") BigDecimal sugarG,
            @DecimalMin("0.0") @Schema(description = "입력한 기준량의 나트륨(mg). 모르면 생략합니다.", example = "100") BigDecimal sodiumMg
    ) {
    }

    @Schema(description = "음식 상세 화면에서 열량을 직접 수정하는 요청")
    public record FoodCaloriesUpdateRequest(
            @DecimalMin("0.0") @Schema(description = "영양성분함량 기준량 기준 열량(kcal)", example = "64") BigDecimal basisCaloriesKcal,
            @DecimalMin("0.0") @Schema(description = "총 식품 중량 기준 열량(kcal)", example = "320") BigDecimal totalCaloriesKcal
    ) {
    }

    public record FoodSummary(
            Long foodId,
            String sourceName,
            String sourceFoodCode,
            String foodName,
            String matchedAlias,
            BigDecimal defaultServingG,
            BigDecimal nutritionBasisAmountG,
            BigDecimal totalWeightG,
            NutrientTotals nutrients,
            NutrientTotals nutritionBasisNutrients,
            NutrientTotals totalWeightNutrients
    ) {
    }

    public record FoodDetail(
            Long foodId,
            String sourceName,
            String sourceFoodCode,
            String foodName,
            BigDecimal defaultServingG,
            BigDecimal nutritionBasisAmountG,
            BigDecimal totalWeightG,
            NutrientTotals nutrients,
            NutrientTotals nutritionBasisNutrients,
            NutrientTotals totalWeightNutrients
    ) {
    }
}
