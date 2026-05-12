package com.database2026.backend.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;

public final class UserDtos {

    private UserDtos() {
    }

    public record ProfileUpdateRequest(
            @NotBlank @Schema(allowableValues = {"MALE", "FEMALE", "OTHER"}) String gender,
            @NotNull @DecimalMin("1.0") BigDecimal heightCm,
            @NotNull @Positive BigDecimal weightKg
    ) {
    }

    public record MeResponse(
            Long userId,
            String email,
            String name,
            UniversityResponse university,
            HealthProfileResponse profile,
            Boolean profileCompleted,
            StudentVerificationResponse studentVerification
    ) {
    }

    public record UniversityResponse(Long universityId, String universityCode, String universityName) {
    }

    public record HealthProfileResponse(
            String gender,
            BigDecimal heightCm,
            BigDecimal weightKg,
            BigDecimal bmi
    ) {
    }

    public record StudentVerificationResponse(
            String studentEmail,
            String status
    ) {
    }

    public record FoodAllergyAddRequest(
            @NotNull Long foodId,
            String reactionNote
    ) {
    }

    public record FoodAllergyListResponse(List<FoodAllergyItem> items) {
    }

    public record FoodAllergyItem(
            Long allergyId,
            Long foodId,
            String foodName,
            String sourceCategory,
            String reactionNote
    ) {
    }
}
