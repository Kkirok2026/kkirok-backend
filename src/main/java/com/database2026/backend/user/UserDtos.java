package com.database2026.backend.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public final class UserDtos {

    private UserDtos() {
    }

    public record ProfileUpdateRequest(
            @NotBlank @Schema(allowableValues = {"MALE", "FEMALE", "OTHER"}) String gender,
            @Min(1) @Max(120) @Schema(description = "나이. 생략하면 기존 회원 나이를 유지합니다.", example = "22") Integer age,
            @NotNull @DecimalMin("1.0") BigDecimal heightCm,
            @NotNull @Positive BigDecimal weightKg,
            @Schema(description = "목표 몸무게(kg). 설정하지 않으면 null", example = "52.0")
            @Positive BigDecimal targetWeightKg,
            @Schema(description = "목표 기간 값. 설정하지 않으면 null", example = "3")
            @Min(1) Integer targetPeriodValue,
            @Schema(
                    description = "목표 기간 단위. targetPeriodValue가 있고 생략하면 MONTH로 저장합니다.",
                    allowableValues = {"WEEK", "MONTH"},
                    example = "MONTH"
            )
            String targetPeriodUnit,
            @Schema(
                    description = "활동수준. 생략하면 LOW_ACTIVE로 저장합니다.",
                    allowableValues = {"SEDENTARY", "LOW_ACTIVE", "ACTIVE", "VERY_ACTIVE"},
                    example = "LOW_ACTIVE"
            )
            String activityLevel
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
            Integer targetPeriodValue,
            String targetPeriodUnit,
            BigDecimal bmi,
            String activityLevel
    ) {
    }

    public record StudentVerificationResponse(
            String studentEmail,
            String status
    ) {
    }
}
