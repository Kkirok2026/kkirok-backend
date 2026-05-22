package com.database2026.backend.meal;

import com.database2026.backend.auth.JwtAuthService;
import com.database2026.backend.common.ApiResponse;
import com.database2026.backend.meal.MealDtos.DailySummaryResponse;
import com.database2026.backend.meal.MealDtos.FoodMealLogItemsAddRequest;
import com.database2026.backend.meal.MealDtos.MealLogCreateRequest;
import com.database2026.backend.meal.MealDtos.MealLogItemAmountUpdateRequest;
import com.database2026.backend.meal.MealDtos.MealLogListResponse;
import com.database2026.backend.meal.MealDtos.MealLogResponse;
import com.database2026.backend.meal.MealDtos.MenuOptionMealLogAddRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneId;
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
@Tag(name = "Meal Logs", description = "사용자 식단 기록 생성, 음식 추가, 조회")
@SecurityRequirement(name = "bearerAuth")
public class MealController {

    private final JwtAuthService jwtAuthService;
    private final MealService mealService;

    public MealController(JwtAuthService jwtAuthService, MealService mealService) {
        this.jwtAuthService = jwtAuthService;
        this.mealService = mealService;
    }

    @PostMapping("/meal-logs")
    @Operation(
            summary = "식단 기록 생성",
            description = "사용자가 식단 생성하기를 눌렀을 때 빈 식단 기록을 먼저 만듭니다. 음식은 생성 후 음식 검색 결과 추가 API로 여러 번 추가합니다."
    )
    ResponseEntity<ApiResponse<MealLogResponse>> create(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody MealLogCreateRequest request
    ) {
        long userId = jwtAuthService.requireUserId(authorization);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(mealService.create(userId, request)));
    }

    @GetMapping("/meal-logs")
    @Operation(summary = "날짜별 식단 기록 조회", description = "특정 날짜에 사용자가 만든 아침/점심/저녁/간식 식단 기록을 조회합니다.")
    ApiResponse<MealLogListResponse> listByDate(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        long userId = jwtAuthService.requireUserId(authorization);
        return ApiResponse.success(mealService.listByDate(userId, date));
    }

    @GetMapping("/meal-logs/{mealLogId}")
    @Operation(summary = "식단 기록 상세 조회", description = "식단에 추가된 음식 목록과 현재까지의 영양 합계를 조회합니다.")
    ApiResponse<MealLogResponse> detail(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable long mealLogId
    ) {
        long userId = jwtAuthService.requireUserId(authorization);
        return ApiResponse.success(mealService.mealLog(userId, mealLogId));
    }

    @PostMapping("/meal-logs/{mealLogId}/food-items")
    @Operation(
            summary = "검색한 음식들을 식단에 추가",
            description = "사용자가 음식 검색 결과에서 하나 이상 선택한 뒤 식단 추가하기를 눌렀을 때 호출합니다. 같은 식단에 다른 음식을 더 추가할 때도 이 API를 반복해서 호출합니다."
    )
    ApiResponse<MealLogResponse> addFoodItems(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable long mealLogId,
            @Valid @RequestBody FoodMealLogItemsAddRequest request
    ) {
        long userId = jwtAuthService.requireUserId(authorization);
        return ApiResponse.success(mealService.addFoodItems(userId, mealLogId, request));
    }

    @PostMapping("/meal-logs/from-menu-option")
    @Operation(
            summary = "식당 메뉴 선택 후 식단에 바로 추가",
            description = "식당 메뉴 조회/비교 결과의 optionId를 보내면 해당 메뉴의 날짜/끼니 식단 기록을 찾거나 새로 만든 뒤, 메뉴 구성 항목을 식단에 추가합니다. 학교 이메일로 인증된 본인 학교 메뉴만 추가할 수 있습니다."
    )
    ApiResponse<MealLogResponse> addMenuOption(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody MenuOptionMealLogAddRequest request
    ) {
        long userId = jwtAuthService.requireUserId(authorization);
        return ApiResponse.success(mealService.addMenuOption(userId, request));
    }

    @PatchMapping("/meal-logs/{mealLogId}/items/{mealLogItemId}/exclude")
    @Operation(summary = "식단 항목 제외/복구", description = "홈 요약 계산에서 제외할지 여부를 변경합니다.")
    ApiResponse<MealLogResponse> setExcluded(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable long mealLogId,
            @PathVariable long mealLogItemId,
            @RequestParam(defaultValue = "true") boolean excluded
    ) {
        long userId = jwtAuthService.requireUserId(authorization);
        return ApiResponse.success(mealService.setExcluded(userId, mealLogId, mealLogItemId, excluded));
    }

    @PatchMapping("/meal-logs/{mealLogId}/items/{mealLogItemId}/amount")
    @Operation(summary = "식단 항목 섭취량 수정", description = "사용자가 실제로 먹은 양(g)을 수정하면 해당 양 기준으로 열량과 영양성분을 다시 계산합니다.")
    ApiResponse<MealLogResponse> updateItemAmount(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable long mealLogId,
            @PathVariable long mealLogItemId,
            @Valid @RequestBody MealLogItemAmountUpdateRequest request
    ) {
        long userId = jwtAuthService.requireUserId(authorization);
        return ApiResponse.success(mealService.updateItemAmount(userId, mealLogId, mealLogItemId, request));
    }

    @GetMapping("/home/daily-summary")
    @Operation(
            summary = "홈 일일 영양 요약",
            description = "그날 식단에 추가한 음식의 총 영양성분, 사용자 프로필 기반 권장 섭취량, 탄단지 에너지 비율, 기준 초과/부족 경고를 반환합니다."
    )
    ApiResponse<DailySummaryResponse> dailySummary(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        long userId = jwtAuthService.requireUserId(authorization);
        return ApiResponse.success(mealService.dailySummary(userId, date));
    }

    @GetMapping("/home/yesterday-feedback")
    @Operation(
            summary = "전날 식사 피드백",
            description = "어제 날짜의 식단 합계를 기준으로 권장 섭취량 대비 열량, 탄단지 비율, 당류, 나트륨 경고를 반환합니다."
    )
    ApiResponse<DailySummaryResponse> yesterdayFeedback(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    ) {
        long userId = jwtAuthService.requireUserId(authorization);
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        return ApiResponse.success(mealService.dailySummary(userId, yesterday));
    }
}
