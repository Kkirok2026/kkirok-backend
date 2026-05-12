package com.database2026.backend.menu;

import com.database2026.backend.auth.AuthSessionService;
import com.database2026.backend.common.ApiResponse;
import com.database2026.backend.menu.MenuDtos.DailyMenuResponse;
import com.database2026.backend.menu.MenuDtos.DiningPlaceListResponse;
import com.database2026.backend.menu.MenuDtos.InhaMenuCrawlResponse;
import com.database2026.backend.menu.MenuDtos.MenuCompareResponse;
import com.database2026.backend.menu.MenuDtos.UniversityListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Dining Menus", description = "대학교 식당과 크롤링 메뉴 조회")
public class MenuController {

    private final AuthSessionService authSessionService;
    private final MenuService menuService;
    private final InhaMenuCrawlerService inhaMenuCrawlerService;

    public MenuController(
            AuthSessionService authSessionService,
            MenuService menuService,
            InhaMenuCrawlerService inhaMenuCrawlerService
    ) {
        this.authSessionService = authSessionService;
        this.menuService = menuService;
        this.inhaMenuCrawlerService = inhaMenuCrawlerService;
    }

    @GetMapping("/universities")
    @Operation(summary = "대학교 목록 조회", description = "초기 데이터는 인하대학교를 제공하며, 다대학 확장을 고려한 API입니다.")
    ApiResponse<UniversityListResponse> universities() {
        return ApiResponse.success(menuService.universities());
    }

    @GetMapping("/dining-places")
    @Operation(summary = "식당 목록 조회")
    ApiResponse<DiningPlaceListResponse> diningPlaces(@RequestParam long universityId) {
        return ApiResponse.success(menuService.diningPlaces(universityId));
    }

    @GetMapping("/menus/daily")
    @Operation(summary = "날짜/끼니별 식당 메뉴 조회", description = "학생식당 점심은 한식/양식 옵션, 기숙사식당은 중식/석식 단일 옵션으로 반환합니다.")
    ApiResponse<DailyMenuResponse> dailyMenu(
            @RequestParam long universityId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String mealType
    ) {
        return ApiResponse.success(menuService.dailyMenu(universityId, date, mealType));
    }

    @GetMapping("/menus/compare")
    @Operation(summary = "식당 메뉴 탄단지 비교", description = "같은 날짜/끼니의 모든 식당 옵션별 열량, 탄수화물, 단백질, 지방을 비교합니다.")
    @SecurityRequirement(name = "bearerAuth")
    ApiResponse<MenuCompareResponse> compare(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam long universityId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String mealType
    ) {
        long userId = authSessionService.requireUserId(authorization);
        menuService.assertUserCanCompare(userId, universityId);
        return ApiResponse.success(menuService.compare(universityId, date, mealType));
    }

    @PostMapping("/menus/crawl/inha/student")
    @Operation(summary = "인하대 학생식당 메뉴 크롤링", description = "인하대 학생식당 메뉴 페이지를 크롤링해 DB에 저장합니다.")
    ApiResponse<InhaMenuCrawlResponse> crawlInhaStudentDining() {
        return ApiResponse.success(inhaMenuCrawlerService.crawlStudentDining());
    }
}
