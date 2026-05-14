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

    @Schema(description = "식단 기록 생성 요청. 사용자가 식단 생성하기를 눌렀을 때 날짜와 끼니만 먼저 저장합니다.")
    public record MealLogCreateRequest(
            @NotNull @Schema(description = "식단 날짜", example = "2026-05-13") LocalDate logDate,
            @NotBlank @Schema(
                    description = "끼니 구분",
                    allowableValues = {"BREAKFAST", "LUNCH", "DINNER", "SNACK"},
                    example = "LUNCH"
            ) String mealType,
            @Schema(description = "식단 메모. 없으면 null 또는 빈 문자열로 보냅니다.", example = "점심 기록") String memo
    ) {
    }

    @Schema(description = "기존 식단에 음식 검색 결과를 추가하는 요청. 사용자가 검색 결과에서 선택한 음식을 한 번에 여러 개 보낼 수 있습니다.")
    public record FoodMealLogItemsAddRequest(
            @NotEmpty List<@Valid FoodMealLogItemRequest> items
    ) {
    }

    @Schema(description = "식단에 추가할 음식 1개")
    public record FoodMealLogItemRequest(
            @NotNull @Schema(description = "음식 검색 API에서 받은 foodId", example = "3101") Long foodId,
            @Positive @Schema(description = "사용자가 먹은 양(g). 생략하면 해당 음식의 기본 제공량을 사용합니다.", example = "100") BigDecimal amountG
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
