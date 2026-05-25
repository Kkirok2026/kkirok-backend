package com.database2026.backend.menu;

import com.database2026.backend.common.NutrientTotals;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class MenuDtos {

    private MenuDtos() {
    }

    public record UniversityListResponse(List<UniversityItem> items) {
    }

    public record UniversityItem(Long universityId, String universityName) {
    }

    public record DiningPlaceListResponse(List<DiningPlaceItem> items) {
    }

    public record DiningPlaceItem(
            Long diningPlaceId,
            Long universityId,
            String diningPlaceName,
            String diningPlaceType,
            Boolean isActive
    ) {
    }

    public record DailyMenuResponse(
            Long universityId,
            LocalDate date,
            String mealType,
            List<DiningPlaceMenu> diningPlaces
    ) {
    }

    public record DiningPlaceMenu(
            Long diningPlaceId,
            String diningPlaceName,
            String diningPlaceType,
            List<MenuOptionSummary> options
    ) {
    }

    public record MenuOptionSummary(
            Long optionId,
            String categoryCode,
            String categoryName,
            String optionName,
            NutrientTotals nutrients
    ) {
    }

    public record MenuCompareResponse(
            Long universityId,
            LocalDate date,
            String mealType,
            Long selectedStudentOptionId,
            List<MenuOptionCompareItem> items
    ) {
    }

    public record InhaMenuCrawlResponse(
            Integer importedCount,
            List<String> warnings
    ) {
    }

    public record MenuOptionCompareItem(
            Long optionId,
            String diningPlaceName,
            String diningPlaceType,
            String categoryCode,
            String categoryName,
            String optionName,
            NutrientTotals nutrients
    ) {
    }

    @Schema(description = "식당 메뉴 옵션 열량 임시 보정 요청")
    public record MenuOptionCaloriesUpdateRequest(
            @NotNull @DecimalMin("0.0") @Schema(description = "메뉴 옵션 기준 열량(kcal)", example = "730") BigDecimal caloriesKcal
    ) {
    }
}
