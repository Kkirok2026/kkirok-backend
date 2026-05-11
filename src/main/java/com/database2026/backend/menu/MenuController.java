package com.database2026.backend.menu;

import com.database2026.backend.common.ApiResponse;
import com.database2026.backend.menu.MenuDtos.DailyMenuResponse;
import com.database2026.backend.menu.MenuDtos.DiningPlaceListResponse;
import com.database2026.backend.menu.MenuDtos.MenuCompareResponse;
import com.database2026.backend.menu.MenuDtos.UniversityListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Dining Menus", description = "대학교 식당과 크롤링 메뉴 조회")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/universities")
    @Operation(summary = "대학교 목록 조회", description = "현재는 한 학교만 초기 데이터로 제공하지만 다대학 확장을 고려한 API입니다.")
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
    ApiResponse<MenuCompareResponse> compare(
            @RequestParam long universityId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String mealType
    ) {
        return ApiResponse.success(menuService.compare(universityId, date, mealType));
    }
}
