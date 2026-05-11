package com.database2026.backend.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        ApiError error,
        Meta meta
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, Meta.create());
    }

    public static ApiResponse<Void> empty() {
        return new ApiResponse<>(true, null, null, Meta.create());
    }

    public static ApiResponse<Void> failure(String code, String message, List<FieldError> details) {
        return new ApiResponse<>(false, null, new ApiError(code, message, details), Meta.create());
    }

    public record ApiError(String code, String message, List<FieldError> details) {
    }

    public record FieldError(String field, String reason) {
    }

    public record Meta(String requestId) {
        static Meta create() {
            return new Meta(UUID.randomUUID().toString());
        }
    }
}
