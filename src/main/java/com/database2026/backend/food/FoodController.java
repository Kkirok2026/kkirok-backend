package com.database2026.backend.food;

import com.database2026.backend.common.ApiResponse;
import com.database2026.backend.food.FoodDtos.FoodDetail;
import com.database2026.backend.food.FoodDtos.FoodSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/foods")
@Tag(name = "Foods", description = "공개 음식 영양성분 데이터 검색")
public class FoodController {

    private final FoodService foodService;

    public FoodController(FoodService foodService) {
        this.foodService = foodService;
    }

    @GetMapping("/search")
    @Operation(summary = "음식 검색", description = "음식명과 등록된 별칭을 함께 검색합니다.")
    ApiResponse<FoodSearchResponse> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResponse.success(foodService.search(q, limit));
    }

    @GetMapping("/{foodId}")
    @Operation(summary = "음식 상세 조회")
    ApiResponse<FoodDetail> detail(@PathVariable long foodId) {
        return ApiResponse.success(foodService.detail(foodId));
    }
}
