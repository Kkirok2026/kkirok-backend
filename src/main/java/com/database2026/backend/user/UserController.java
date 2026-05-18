package com.database2026.backend.user;

import com.database2026.backend.auth.JwtAuthService;
import com.database2026.backend.common.ApiResponse;
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

    @DeleteMapping("/me")
    @Operation(summary = "회원 탈퇴", description = "내 계정과 토큰 무효화 기록, 프로필, 학교 인증, 식단 기록을 삭제합니다.")
    ApiResponse<Void> deleteMe(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        long userId = jwtAuthService.requireUserId(authorization);
        userService.deleteMe(userId);
        return ApiResponse.empty();
    }
}
