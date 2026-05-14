package com.database2026.backend.auth;

import com.database2026.backend.auth.AuthDtos.AuthResponse;
import com.database2026.backend.auth.AuthDtos.LoginRequest;
import com.database2026.backend.auth.AuthDtos.SchoolEmailVerificationRequest;
import com.database2026.backend.auth.AuthDtos.SchoolEmailVerificationResponse;
import com.database2026.backend.auth.AuthDtos.SignupRequest;
import com.database2026.backend.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "학교 이메일 인증 기반 회원가입/로그인")
public class AuthController {

    private final AuthService authService;
    private final AuthSessionService authSessionService;

    public AuthController(AuthService authService, AuthSessionService authSessionService) {
        this.authService = authService;
        this.authSessionService = authSessionService;
    }

    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "이메일 도메인으로 대학교를 자동 판별합니다. 등록된 학교 이메일이면 인증코드를 검증하고, 그 외 이메일은 일반 사용자로 가입합니다.")
    ResponseEntity<ApiResponse<AuthResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(authService.signup(request)));
    }

    @PostMapping("/school-email-verifications")
    @Operation(summary = "학교 이메일 인증코드 발급", description = "입력한 이메일 도메인으로 대학교를 자동 판별한 뒤 회원가입용 인증코드를 발급합니다.")
    ApiResponse<SchoolEmailVerificationResponse> requestSchoolEmailVerification(
            @Valid @RequestBody SchoolEmailVerificationRequest request
    ) {
        return ApiResponse.success(authService.requestSchoolEmailVerification(request));
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "로그인 성공 시 API 호출용 bearer token을 발급합니다.")
    ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃", description = "현재 bearer token을 폐기합니다.")
    @SecurityRequirement(name = "bearerAuth")
    ApiResponse<Void> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        authSessionService.revoke(authorization);
        return ApiResponse.empty();
    }
}
