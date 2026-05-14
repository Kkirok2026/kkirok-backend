package com.database2026.backend.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
            @Min(1) @Max(120) @Schema(description = "나이. 생략하면 기존 회원 나이를 유지합니다.", example = "22") Integer age,
            @NotNull @DecimalMin("1.0") BigDecimal heightCm,
            @NotNull @Positive BigDecimal weightKg,
            @Schema(description = "목표 몸무게(kg). 설정하지 않으면 null", example = "52.0")
            @Positive BigDecimal targetWeightKg,
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
            BigDecimal bmi,
            String activityLevel
    ) {
    }

    public record StudentVerificationResponse(
            String studentEmail,
            String status
    ) {
    }

    @Schema(description = "음식 검색 결과를 알레르기/주의 음식으로 저장하는 요청. 원재료 알레르기가 아니라 특정 foodId 음식 자체를 저장합니다.")
    public record FoodAllergyAddRequest(
            @NotNull
            @Schema(
                    description = "음식 검색 API(/api/v1/foods/search)에서 받은 foodId. 식단 항목의 foodId가 이 값과 정확히 일치하면 FOOD_MATCH 경고가 발생합니다.",
                    example = "3101"
            )
            Long foodId,
            @Schema(description = "사용자가 기록하는 반응 메모. 없으면 null 또는 빈 문자열로 보냅니다.", example = "먹으면 속이 불편함")
            String reactionNote
    ) {
    }

    @Schema(description = "사용자가 foodId 기준으로 등록한 알레르기/주의 음식 목록")
    public record FoodAllergyListResponse(
            @Schema(description = "등록된 음식 알레르기 목록") List<FoodAllergyItem> items
    ) {
    }

    @Schema(description = "foodId 기준 알레르기/주의 음식 항목")
    public record FoodAllergyItem(
            @Schema(description = "음식 알레르기 등록 번호", example = "1")
            Long allergyId,
            @Schema(description = "등록된 음식의 foodId", example = "3101")
            Long foodId,
            @Schema(description = "등록된 음식명", example = "라면")
            String foodName,
            @Schema(description = "음식 데이터의 분류", example = "면류")
            String sourceCategory,
            @Schema(description = "사용자가 입력한 반응 메모", example = "먹으면 속이 불편함")
            String reactionNote
    ) {
    }
}
