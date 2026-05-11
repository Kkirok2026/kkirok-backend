package com.database2026.backend.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record SignupRequest(
            @Email @NotBlank String email,
            @NotBlank String password,
            @NotBlank String name,
            @NotNull Long universityId,
            @NotBlank @Schema(allowableValues = {"MALE", "FEMALE", "OTHER"}) String gender,
            @NotNull @DecimalMin("1.0") BigDecimal heightCm,
            @NotNull @Positive BigDecimal weightKg
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
            BigDecimal bmi
    ) {
    }
}
