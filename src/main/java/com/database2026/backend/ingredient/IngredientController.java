package com.database2026.backend.ingredient;

import com.database2026.backend.auth.JwtAuthService;
import com.database2026.backend.common.ApiResponse;
import com.database2026.backend.ingredient.IngredientDtos.FoodIngredientListResponse;
import com.database2026.backend.ingredient.IngredientDtos.FoodIngredientSyncResponse;
import com.database2026.backend.ingredient.IngredientDtos.IngredientSearchResponse;
import com.database2026.backend.ingredient.IngredientDtos.UserIngredientAllergyAddRequest;
import com.database2026.backend.ingredient.IngredientDtos.UserIngredientAllergyBulkAddRequest;
import com.database2026.backend.ingredient.IngredientDtos.UserIngredientAllergyListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Ingredients", description = "원재료 검색과 원재료 기반 알레르기")
public class IngredientController {

    private final JwtAuthService jwtAuthService;
    private final IngredientService ingredientService;

    public IngredientController(JwtAuthService jwtAuthService, IngredientService ingredientService) {
        this.jwtAuthService = jwtAuthService;
        this.ingredientService = ingredientService;
    }

    @GetMapping("/ingredients/search")
    @Operation(summary = "원재료 검색", description = "로컬 원재료 DB를 검색하고, 설정된 식약처 원재료 API 키가 있으면 검색 결과를 캐싱합니다.")
    ApiResponse<IngredientSearchResponse> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ApiResponse.success(ingredientService.search(q, limit));
    }

    @GetMapping("/users/me/ingredient-allergies")
    @Operation(summary = "내 원재료 알레르기 목록 조회")
    @SecurityRequirement(name = "bearerAuth")
    ApiResponse<UserIngredientAllergyListResponse> userIngredientAllergies(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    ) {
        long userId = jwtAuthService.requireUserId(authorization);
        return ApiResponse.success(ingredientService.userIngredientAllergies(userId));
    }

    @PostMapping("/users/me/ingredient-allergies")
    @Operation(summary = "내 원재료 알레르기 추가", description = "원재료 검색 결과의 ingredientId 또는 직접 입력한 ingredientName을 저장합니다.")
    @SecurityRequirement(name = "bearerAuth")
    ApiResponse<UserIngredientAllergyListResponse> addUserIngredientAllergy(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody UserIngredientAllergyAddRequest request
    ) {
        long userId = jwtAuthService.requireUserId(authorization);
        return ApiResponse.success(ingredientService.addUserIngredientAllergy(userId, request));
    }

    @PostMapping("/users/me/ingredient-allergies/bulk")
    @Operation(summary = "내 원재료 알레르기 여러 개 추가", description = "프론트에서 선택한 여러 원재료 알레르기를 한 번에 저장합니다.")
    @SecurityRequirement(name = "bearerAuth")
    ApiResponse<UserIngredientAllergyListResponse> addUserIngredientAllergies(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody UserIngredientAllergyBulkAddRequest request
    ) {
        long userId = jwtAuthService.requireUserId(authorization);
        return ApiResponse.success(ingredientService.addUserIngredientAllergies(userId, request));
    }

    @DeleteMapping("/users/me/ingredient-allergies/{allergyId}")
    @Operation(summary = "내 원재료 알레르기 삭제")
    @SecurityRequirement(name = "bearerAuth")
    ApiResponse<UserIngredientAllergyListResponse> deleteUserIngredientAllergy(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable long allergyId
    ) {
        long userId = jwtAuthService.requireUserId(authorization);
        return ApiResponse.success(ingredientService.deleteUserIngredientAllergy(userId, allergyId));
    }

    @GetMapping("/foods/{foodId}/ingredients")
    @Operation(summary = "음식 원재료 조회", description = "캐싱된 품목제조보고 원재료 목록을 반환합니다.")
    ApiResponse<FoodIngredientListResponse> foodIngredients(@PathVariable long foodId) {
        return ApiResponse.success(ingredientService.foodIngredients(foodId));
    }

    @PostMapping("/foods/{foodId}/ingredients/sync")
    @Operation(summary = "음식 원재료 동기화", description = "품목제조보고 원재료 API(C002)를 호출해 원재료를 캐싱합니다.")
    ApiResponse<FoodIngredientSyncResponse> syncFoodIngredients(@PathVariable long foodId) {
        return ApiResponse.success(ingredientService.syncFoodIngredients(foodId));
    }
}
