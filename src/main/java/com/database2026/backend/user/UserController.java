package com.database2026.backend.user;

import com.database2026.backend.auth.JwtAuthService;
import com.database2026.backend.common.ApiResponse;
import com.database2026.backend.user.UserDtos.FoodAllergyAddRequest;
import com.database2026.backend.user.UserDtos.FoodAllergyListResponse;
import com.database2026.backend.user.UserDtos.HealthProfileResponse;
import com.database2026.backend.user.UserDtos.MeResponse;
import com.database2026.backend.user.UserDtos.ProfileUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "내 정보와 건강 프로필")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final JwtAuthService jwtAuthService;
    private final UserService userService;

    public UserController(JwtAuthService jwtAuthService, UserService userService) {
        this.jwtAuthService = jwtAuthService;
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회")
    ApiResponse<MeResponse> me(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        long userId = jwtAuthService.requireUserId(authorization);
        return ApiResponse.success(userService.me(userId));
    }

    @PutMapping("/me/profile")
    @Operation(
            summary = "내 건강 프로필 수정",
            description = "성별/나이/키/현재 몸무게/목표 몸무게/활동수준을 저장하고 BMI와 홈 권장 섭취량 계산 기준을 갱신합니다."
    )
    ApiResponse<HealthProfileResponse> updateProfile(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody ProfileUpdateRequest request
    ) {
        long userId = jwtAuthService.requireUserId(authorization);
        return ApiResponse.success(userService.updateProfile(userId, request));
    }

    @GetMapping("/me/allergies")
    @Operation(
            summary = "내 알레르기 음식 목록 조회",
            description = "음식 검색 결과에서 foodId로 등록한 알레르기/주의 음식 목록을 반환합니다. 원재료 알레르기 목록은 /api/v1/users/me/ingredient-allergies를 사용합니다."
    )
    ApiResponse<FoodAllergyListResponse> foodAllergies(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        long userId = jwtAuthService.requireUserId(authorization);
        return ApiResponse.success(userService.foodAllergies(userId));
    }

    @PostMapping("/me/allergies")
    @Operation(
            summary = "음식 알레르기 추가",
            description = """
                    음식 검색 결과의 foodId를 사용자의 알레르기/주의 음식으로 저장합니다.
                    이 API는 우유, 계란, 땅콩 같은 원재료 알레르기가 아니라 라면, 김치찌개, 저지방 우유처럼 특정 음식 데이터 자체를 저장할 때 사용합니다.
                    이후 식단 항목의 foodId가 등록된 foodId와 정확히 일치하면 FOOD_MATCH 경고가 내려갑니다.
                    원재료 포함 여부까지 넓게 경고하려면 /api/v1/users/me/ingredient-allergies에 ingredientId를 등록해야 합니다.
                    """
    )
    ApiResponse<FoodAllergyListResponse> addFoodAllergy(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody FoodAllergyAddRequest request
    ) {
        long userId = jwtAuthService.requireUserId(authorization);
        return ApiResponse.success(userService.addFoodAllergy(userId, request));
    }

    @DeleteMapping("/me/allergies/{foodId}")
    @Operation(
            summary = "음식 알레르기 삭제",
            description = "등록된 음식 알레르기 중 요청한 foodId와 일치하는 항목을 삭제합니다. 원재료 알레르기 삭제는 /api/v1/users/me/ingredient-allergies/{allergyId}를 사용합니다."
    )
    ApiResponse<FoodAllergyListResponse> deleteFoodAllergy(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable long foodId
    ) {
        long userId = jwtAuthService.requireUserId(authorization);
        return ApiResponse.success(userService.deleteFoodAllergy(userId, foodId));
    }

    @DeleteMapping("/me")
    @Operation(summary = "회원 탈퇴", description = "내 계정과 세션, 프로필, 학교 인증, 식단 기록, 알레르기 정보를 삭제합니다.")
    ApiResponse<Void> deleteMe(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        long userId = jwtAuthService.requireUserId(authorization);
        userService.deleteMe(userId);
        return ApiResponse.empty();
    }
}
