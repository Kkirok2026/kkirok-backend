package com.database2026.backend.meal;

import com.database2026.backend.auth.AuthSessionService;
import com.database2026.backend.common.ApiResponse;
import com.database2026.backend.meal.MealDtos.DailySummaryResponse;
import com.database2026.backend.meal.MealDtos.MealLogCreateRequest;
import com.database2026.backend.meal.MealDtos.MealLogItemRequest;
import com.database2026.backend.meal.MealDtos.MealLogListResponse;
import com.database2026.backend.meal.MealDtos.MealLogResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Meal Logs", description = "내 식단 추가/제외와 홈 영양 요약")
@SecurityRequirement(name = "bearerAuth")
public class MealController {

    private final AuthSessionService authSessionService;
    private final MealService mealService;

    public MealController(AuthSessionService authSessionService, MealService mealService) {
        this.authSessionService = authSessionService;
        this.mealService = mealService;
    }

    @PostMapping("/meal-logs")
    @Operation(summary = "식단 기록 생성", description = "음식 ID 또는 식당 메뉴 옵션 ID로 식단 항목을 추가합니다.")
    ResponseEntity<ApiResponse<MealLogResponse>> create(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody MealLogCreateRequest request
    ) {
        long userId = authSessionService.requireUserId(authorization);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(mealService.create(userId, request)));
    }

    @GetMapping("/meal-logs")
    @Operation(summary = "날짜별 식단 기록 조회")
    ApiResponse<MealLogListResponse> listByDate(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        long userId = authSessionService.requireUserId(authorization);
        return ApiResponse.success(mealService.listByDate(userId, date));
    }

    @GetMapping("/meal-logs/{mealLogId}")
    @Operation(summary = "식단 기록 상세 조회")
    ApiResponse<MealLogResponse> detail(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable long mealLogId
    ) {
        long userId = authSessionService.requireUserId(authorization);
        return ApiResponse.success(mealService.mealLog(userId, mealLogId));
    }

    @PostMapping("/meal-logs/{mealLogId}/items")
    @Operation(summary = "식단 항목 추가")
    ApiResponse<MealLogResponse> addItem(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable long mealLogId,
            @Valid @RequestBody MealLogItemRequest request
    ) {
        long userId = authSessionService.requireUserId(authorization);
        return ApiResponse.success(mealService.addItem(userId, mealLogId, request));
    }

    @PatchMapping("/meal-logs/{mealLogId}/items/{dietItemId}/exclude")
    @Operation(summary = "식단 항목 제외/복구", description = "홈 요약 계산에서 제외할지 여부를 변경합니다.")
    ApiResponse<MealLogResponse> setExcluded(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable long mealLogId,
            @PathVariable long dietItemId,
            @RequestParam(defaultValue = "true") boolean excluded
    ) {
        long userId = authSessionService.requireUserId(authorization);
        return ApiResponse.success(mealService.setExcluded(userId, mealLogId, dietItemId, excluded));
    }

    @GetMapping("/home/daily-summary")
    @Operation(summary = "홈 일일 영양 요약", description = "그날 섭취한 총 열량/탄단지와 기준 초과 경고를 반환합니다.")
    ApiResponse<DailySummaryResponse> dailySummary(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        long userId = authSessionService.requireUserId(authorization);
        return ApiResponse.success(mealService.dailySummary(userId, date));
    }
}
