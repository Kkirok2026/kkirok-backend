package com.database2026.backend.user;

import com.database2026.backend.auth.AuthSessionService;
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

    private final AuthSessionService authSessionService;
    private final UserService userService;

    public UserController(AuthSessionService authSessionService, UserService userService) {
        this.authSessionService = authSessionService;
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "내 정보 조회")
    ApiResponse<MeResponse> me(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        long userId = authSessionService.requireUserId(authorization);
        return ApiResponse.success(userService.me(userId));
    }

    @PutMapping("/me/profile")
    @Operation(summary = "내 건강 프로필 수정", description = "키/몸무게/성별 변경 시 BMI를 다시 계산합니다.")
    ApiResponse<HealthProfileResponse> updateProfile(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody ProfileUpdateRequest request
    ) {
        long userId = authSessionService.requireUserId(authorization);
        return ApiResponse.success(userService.updateProfile(userId, request));
    }

    @GetMapping("/me/allergies")
    @Operation(summary = "내 알레르기 음식 목록 조회", description = "음식 검색에서 선택해 저장한 알레르기 음식 목록을 반환합니다.")
    ApiResponse<FoodAllergyListResponse> foodAllergies(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        long userId = authSessionService.requireUserId(authorization);
        return ApiResponse.success(userService.foodAllergies(userId));
    }

    @PostMapping("/me/allergies")
    @Operation(summary = "내 알레르기 음식 추가", description = "음식 검색 결과의 foodId를 알레르기 음식으로 저장합니다.")
    ApiResponse<FoodAllergyListResponse> addFoodAllergy(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody FoodAllergyAddRequest request
    ) {
        long userId = authSessionService.requireUserId(authorization);
        return ApiResponse.success(userService.addFoodAllergy(userId, request));
    }

    @DeleteMapping("/me/allergies/{foodId}")
    @Operation(summary = "내 알레르기 음식 삭제")
    ApiResponse<FoodAllergyListResponse> deleteFoodAllergy(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable long foodId
    ) {
        long userId = authSessionService.requireUserId(authorization);
        return ApiResponse.success(userService.deleteFoodAllergy(userId, foodId));
    }

    @DeleteMapping("/me")
    @Operation(summary = "회원 탈퇴", description = "내 계정과 세션, 프로필, 학교 인증, 식단 기록, 알레르기 정보를 삭제합니다.")
    ApiResponse<Void> deleteMe(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        long userId = authSessionService.requireUserId(authorization);
        userService.deleteMe(userId);
        return ApiResponse.empty();
    }
}
