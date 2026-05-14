package com.database2026.backend.ingredient;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public final class IngredientDtos {

    private IngredientDtos() {
    }

    public record IngredientSearchResponse(List<IngredientItem> items) {
    }

    public record IngredientItem(
            Long ingredientId,
            String ingredientName,
            String matchedAlias,
            String largeCategory,
            String middleCategory,
            String englishName
    ) {
    }

    public record UserIngredientAllergyAddRequest(
            Long ingredientId,
            String ingredientName,
            String reactionNote
    ) {
    }

    public record UserIngredientAllergyBulkAddRequest(
            @NotEmpty List<@Valid UserIngredientAllergyAddRequest> items
    ) {
    }

    public record UserIngredientAllergyListResponse(List<UserIngredientAllergyItem> items) {
    }

    public record UserIngredientAllergyItem(
            Long allergyId,
            Long ingredientId,
            String allergyName,
            String reactionNote
    ) {
    }

    public record FoodIngredientListResponse(List<FoodIngredientItem> items) {
    }

    public record FoodIngredientSyncResponse(
            Long foodId,
            Integer importedCount,
            List<FoodIngredientItem> items
    ) {
    }

    public record FoodIngredientItem(
            Long ingredientId,
            String ingredientName,
            String rawIngredientName,
            String sourceName,
            String sourceReference
    ) {
    }
}
