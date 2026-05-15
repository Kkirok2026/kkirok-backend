package com.database2026.backend.user;

import com.database2026.backend.auth.JwtAuthService;
import com.database2026.backend.common.ApiResponse;
import com.database2026.backend.user.UserDtos.HealthProfileResponse;
import com.database2026.backend.user.UserDtos.MeResponse;
import com.database2026.backend.user.UserDtos.ProfileUpdateRequest;
import com.database2026.backend.user.UserDtos.UserAllergyAddRequest;
import com.database2026.backend.user.UserDtos.UserAllergyListResponse;
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
            summary = "내 알레르기 목록 조회",
            description = "음식(FOOD)과 원재료(INGREDIENT)로 등록한 알레르기/주의 항목을 한 목록으로 반환합니다."
    )
    ApiResponse<UserAllergyListResponse> allergies(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        long userId = jwtAuthService.requireUserId(authorization);
        return ApiResponse.success(userService.allergies(userId));
    }

    @PostMapping("/me/allergies")
    @Operation(
            summary = "알레르기 추가",
            description = """
                    사용자가 선택한 음식 또는 원재료를 알레르기/주의 항목으로 저장합니다.
                    FOOD는 음식 검색 결과의 foodId와 식단 항목 foodId가 정확히 일치할 때 경고합니다.
                    INGREDIENT는 원재료 검색 결과의 ingredientId 또는 직접 입력한 ingredientName을 저장하고, 메뉴명/음식 원재료/원재료 별칭과 매칭되면 경고합니다.
                    요청 예시: {"allergyType":"FOOD","targetId":3101,"reactionNote":"주의"} 또는 {"allergyType":"INGREDIENT","ingredientName":"우유","reactionNote":"주의"}
                    """
    )
    ApiResponse<UserAllergyListResponse> addAllergy(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody UserAllergyAddRequest request
    ) {
        long userId = jwtAuthService.requireUserId(authorization);
        return ApiResponse.success(userService.addAllergy(userId, request));
    }

    @DeleteMapping("/me/allergies/{allergyId}")
    @Operation(
            summary = "알레르기 삭제",
            description = "GET /api/v1/users/me/allergies 응답의 allergyId를 사용해 음식/원재료 알레르기 항목을 삭제합니다."
    )
    ApiResponse<UserAllergyListResponse> deleteAllergy(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable long allergyId
    ) {
        long userId = jwtAuthService.requireUserId(authorization);
        return ApiResponse.success(userService.deleteAllergy(userId, allergyId));
    }

    @DeleteMapping("/me")
    @Operation(summary = "회원 탈퇴", description = "내 계정과 토큰 무효화 기록, 프로필, 학교 인증, 식단 기록, 알레르기 정보를 삭제합니다.")
    ApiResponse<Void> deleteMe(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        long userId = jwtAuthService.requireUserId(authorization);
        userService.deleteMe(userId);
        return ApiResponse.empty();
    }
}
