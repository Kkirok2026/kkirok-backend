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

    @Schema(description = "음식 또는 원재료를 알레르기/주의 항목으로 저장하는 요청")
    public record UserAllergyAddRequest(
            @NotBlank
            @Schema(
                    description = "등록 타입. FOOD는 특정 음식 데이터, INGREDIENT는 원재료/재료명을 의미합니다.",
                    allowableValues = {"FOOD", "INGREDIENT"},
                    example = "INGREDIENT"
            )
            String allergyType,
            @Schema(
                    description = "선택한 대상 ID. FOOD이면 foodId, INGREDIENT이면 ingredientId입니다. foodId/ingredientId 필드 대신 사용할 수 있습니다.",
                    example = "3101"
            )
            Long targetId,
            @Schema(description = "하위 호환용 음식 ID. allergyType=FOOD일 때 targetId 대신 사용할 수 있습니다.", example = "3101")
            Long foodId,
            @Schema(description = "하위 호환용 원재료 ID. allergyType=INGREDIENT일 때 targetId 대신 사용할 수 있습니다.", example = "2")
            Long ingredientId,
            @Schema(description = "원재료를 직접 입력할 때 사용하는 이름. ingredientId가 없을 때만 사용합니다.", example = "우유")
            String ingredientName,
            @Schema(description = "사용자가 기록하는 반응 메모. 없으면 null 또는 빈 문자열로 보냅니다.", example = "주의")
            String reactionNote
    ) {
    }

    @Schema(description = "사용자가 등록한 음식/원재료 알레르기 목록")
    public record UserAllergyListResponse(
            @Schema(description = "등록된 알레르기/주의 항목 목록") List<UserAllergyItem> items
    ) {
    }

    @Schema(description = "사용자가 등록한 알레르기/주의 항목")
    public record UserAllergyItem(
            @Schema(description = "FOOD 또는 INGREDIENT", example = "FOOD")
            String allergyType,
            @Schema(description = "알레르기 등록 번호", example = "3")
            Long allergyId,
            @Schema(description = "FOOD이면 foodId, INGREDIENT이면 ingredientId", example = "3101")
            Long targetId,
            @Schema(description = "음식명 또는 원재료명", example = "라면")
            String name,
            @Schema(description = "사용자가 입력한 반응 메모", example = "주의")
            String reactionNote
    ) {
    }
}
