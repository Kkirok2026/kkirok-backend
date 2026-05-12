package com.database2026.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record SignupRequest(
            @Email @NotBlank String email,
            String verificationCode,
            @NotBlank String password,
            @NotBlank String name,
            Long universityId
    ) {
    }

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {
    }

    public record AuthResponse(
            Long userId,
            Long universityId,
            String accessToken,
            BigDecimal bmi,
            Boolean profileCompleted
    ) {
    }

    public record SchoolEmailVerificationRequest(
            @NotNull Long universityId,
            @Email @NotBlank String email
    ) {
    }

    public record SchoolEmailVerificationResponse(
            Long verificationId,
            Long universityId,
            String email,
            LocalDateTime expiresAt
    ) {
    }
}
