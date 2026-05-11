package com.database2026.backend.user;

import com.database2026.backend.auth.AuthSessionService;
import com.database2026.backend.common.ApiResponse;
import com.database2026.backend.user.UserDtos.HealthProfileResponse;
import com.database2026.backend.user.UserDtos.MeResponse;
import com.database2026.backend.user.UserDtos.ProfileUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
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
}
