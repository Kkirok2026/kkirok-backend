package com.database2026.backend.menu;

import com.database2026.backend.auth.JwtAuthService;
import com.database2026.backend.common.ApiResponse;
import com.database2026.backend.menu.MenuDtos.DailyMenuResponse;
import com.database2026.backend.menu.MenuDtos.DiningPlaceListResponse;
import com.database2026.backend.menu.MenuDtos.InhaMenuCrawlResponse;
import com.database2026.backend.menu.MenuDtos.MenuCompareResponse;
import com.database2026.backend.menu.MenuDtos.MenuOptionCaloriesUpdateRequest;
import com.database2026.backend.menu.MenuDtos.MenuOptionCompareItem;
import com.database2026.backend.menu.MenuDtos.UniversityListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Dining Menus", description = "대학교 식당과 크롤링 메뉴 조회")
public class MenuController {

    private final JwtAuthService jwtAuthService;
    private final MenuService menuService;
    private final InhaMenuCrawlerService inhaMenuCrawlerService;

    public MenuController(
            JwtAuthService jwtAuthService,
            MenuService menuService,
            InhaMenuCrawlerService inhaMenuCrawlerService
    ) {
        this.jwtAuthService = jwtAuthService;
        this.menuService = menuService;
        this.inhaMenuCrawlerService = inhaMenuCrawlerService;
    }

    @GetMapping("/universities")
    @Operation(summary = "대학교 목록 조회", description = "초기 데이터는 인하대학교를 제공하며, 이메일 도메인 자동 판별과 다대학 확장을 고려한 API입니다.")
    ApiResponse<UniversityListResponse> universities() {
        return ApiResponse.success(menuService.universities());
    }

    @GetMapping("/dining-places")
    @Operation(summary = "식당 목록 조회")
    ApiResponse<DiningPlaceListResponse> diningPlaces(@RequestParam long universityId) {
        return ApiResponse.success(menuService.diningPlaces(universityId));
    }

    @GetMapping("/menus/daily")
    @Operation(summary = "날짜/끼니별 식당 메뉴 조회", description = "생활관식당은 점심/저녁 메뉴, 학생식당 점심은 한상한담/ONE PLATE/Noodle/셀프라면 등 코너별 메뉴, 학생식당 저녁은 석식 메뉴로 반환합니다.")
    ApiResponse<DailyMenuResponse> dailyMenu(
            @RequestParam long universityId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String mealType
    ) {
        return ApiResponse.success(menuService.dailyMenu(universityId, date, mealType));
    }

    @GetMapping("/menus/compare")
    @Operation(summary = "식당 메뉴 탄단지 비교", description = "학교 이메일로 인증된 사용자만 사용할 수 있습니다. 생활관식당 메뉴와 사용자가 선택한 학생식당 메뉴를 비교합니다.")
    @SecurityRequirement(name = "bearerAuth")
    ApiResponse<MenuCompareResponse> compare(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam long universityId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String mealType,
            @RequestParam(required = false) Long studentOptionId
    ) {
        long userId = jwtAuthService.requireUserId(authorization);
        menuService.assertUserCanCompare(userId, universityId);
        return ApiResponse.success(menuService.compare(userId, universityId, date, mealType, studentOptionId));
    }

    @PatchMapping("/menus/options/{optionId}/calories")
    @Operation(summary = "식당 메뉴 옵션 열량 임시 보정")
    @SecurityRequirement(name = "bearerAuth")
    ApiResponse<MenuOptionCompareItem> updateOptionCalories(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable long optionId,
            @Valid @RequestBody MenuOptionCaloriesUpdateRequest request
    ) {
        long userId = jwtAuthService.requireUserId(authorization);
        return ApiResponse.success(menuService.updateOptionCalories(userId, optionId, request));
    }

    @PostMapping("/menus/crawl/inha/student")
    @Operation(summary = "인하대 학생식당 메뉴 크롤링", description = "인하대 학생식당 메뉴 페이지를 크롤링해 DB에 저장합니다.")
    ApiResponse<InhaMenuCrawlResponse> crawlInhaStudentDining() {
        return ApiResponse.success(inhaMenuCrawlerService.crawlStudentDining());
    }
}
