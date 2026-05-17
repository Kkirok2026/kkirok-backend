package com.database2026.backend.food;

import com.database2026.backend.auth.JwtAuthService;
import com.database2026.backend.common.ApiResponse;
import com.database2026.backend.food.FoodDtos.CustomFoodCreateRequest;
import com.database2026.backend.food.FoodDtos.FoodDetail;
import com.database2026.backend.food.FoodDtos.FoodSearchResponse;
import com.database2026.backend.food.FoodDtos.FoodSuggestionResponse;
import com.database2026.backend.food.FoodDtos.FoodSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/foods")
@Tag(name = "Foods", description = "공개 음식 영양성분 데이터 검색")
public class FoodController {

    private final JwtAuthService jwtAuthService;
    private final FoodService foodService;

    public FoodController(JwtAuthService jwtAuthService, FoodService foodService) {
        this.jwtAuthService = jwtAuthService;
        this.foodService = foodService;
    }

    @GetMapping("/search")
    @Operation(
            summary = "음식 검색",
            description = "DB에 저장된 공용 음식명과 별칭을 검색합니다. Authorization 토큰이 있으면 본인이 직접 입력한 음식도 함께 검색합니다. DB 결과가 없으면 식품의약품안전처 식품영양성분DB정보 OpenAPI를 호출해 검색 결과를 저장한 뒤 다시 반환합니다."
    )
    ApiResponse<FoodSearchResponse> search(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit
    ) {
        Optional<Long> userId = jwtAuthService.optionalUserId(authorization);
        return ApiResponse.success(foodService.search(q, limit, userId));
    }

    @GetMapping("/suggestions")
    @Operation(
            summary = "음식 검색어 추천",
            description = "FatSecret autocomplete를 먼저 호출해 검색어 추천을 반환합니다. FatSecret 권한이 없거나 실패하면 DB에 저장된 음식명과 별칭으로 추천어를 반환합니다."
    )
    ApiResponse<FoodSuggestionResponse> suggestions(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit
    ) {
        Optional<Long> userId = jwtAuthService.optionalUserId(authorization);
        return ApiResponse.success(foodService.suggestions(q, limit, userId));
    }

    @PostMapping("/custom")
    @Operation(
            summary = "내 직접 입력 음식 등록",
            description = "검색 결과가 없을 때 사용자가 음식명과 영양성분을 입력해 내 개인 음식으로 등록합니다. 등록 후 같은 계정의 음식 검색 결과에 표시되고, 기존 식단 항목 추가 API의 foodId로 사용할 수 있습니다."
    )
    ApiResponse<FoodSummary> createCustomFood(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody CustomFoodCreateRequest request
    ) {
        long userId = jwtAuthService.requireUserId(authorization);
        return ApiResponse.success(foodService.createCustomFood(userId, request));
    }

    @GetMapping("/{foodId}")
    @Operation(summary = "음식 상세 조회")
    ApiResponse<FoodDetail> detail(@PathVariable long foodId) {
        return ApiResponse.success(foodService.detail(foodId));
    }
}
