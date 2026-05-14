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
            @NotNull @Positive BigDecimal weightKg,
            @Schema(description = "목표 몸무게(kg). 설정하지 않으면 null", example = "52.0")
            @Positive BigDecimal targetWeightKg
    ) {
    }

    public record MeResponse(
            Long userId,
            String email,
            String name,
            Integer age,
            UniversityResponse university,
            HealthProfileResponse profile,
            Boolean profileCompleted,
            StudentVerificationResponse studentVerification
    ) {
    }

    public record UniversityResponse(Long universityId, String universityName) {
    }

    public record HealthProfileResponse(
            String gender,
            BigDecimal heightCm,
            BigDecimal weightKg,
            BigDecimal targetWeightKg,
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
