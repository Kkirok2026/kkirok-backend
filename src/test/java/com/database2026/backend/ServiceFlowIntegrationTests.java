package com.database2026.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.database2026.backend.menu.MenuFoodMatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ServiceFlowIntegrationTests {

    private static final long TEST_STUDENT_MENU_ID = 990001L;
    private static final long TEST_STUDENT_OPTION_ID = 990001L;
    private static final long TEST_COMPOSITE_MENU_ID = 990002L;
    private static final long TEST_COMPOSITE_OPTION_ID = 990002L;

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MenuFoodMatcher menuFoodMatcher;

    @Test
    void schoolUserCanUseCoreServiceFlowAfterSchemaRefactor() throws Exception {
        seedStudentMenuOptionForComparison();

        String token = login("test@inha.edu", "test");

        JsonNode me = getOk("/api/v1/users/me", token);
        assertThat(me.at("/data/university/universityId").asLong()).isEqualTo(2L);
        assertThat(me.at("/data/studentVerification/status").asText()).isEqualTo("VERIFIED");

        JsonNode profile = putOk("/api/v1/users/me/profile", token, """
                {
                  "age": 23,
                  "gender": "FEMALE",
                  "heightCm": 164,
                  "weightKg": 58,
                  "targetWeightKg": 55,
                  "targetPeriodValue": 3,
                  "targetPeriodUnit": "MONTH",
                  "activityLevel": "LOW_ACTIVE"
                }
                """);
        assertThat(profile.at("/data/bmi").decimalValue()).isGreaterThan(BigDecimal.ZERO);
        assertThat(profile.at("/data/targetPeriodValue").asInt()).isEqualTo(3);
        assertThat(profile.at("/data/targetPeriodUnit").asText()).isEqualTo("MONTH");

        JsonNode foodSearch = getOkWithParams("/api/v1/foods/search", token, "q", "라 면", "limit", "5");
        assertThat(foodSearch.at("/data/items").size()).isGreaterThan(0);
        assertThat(foodSearch.at("/data/items/0/foodId").asLong()).isEqualTo(3101L);

        JsonNode customFood = postOk("/api/v1/foods/custom", token, """
                {
                  "foodName": "테스트 저지방 우유",
                  "amountG": 200,
                  "carbG": 10,
                  "proteinG": 6,
                  "fatG": 2,
                  "sugarG": 8,
                  "sodiumMg": 100
                }
                """);
        long customFoodId = customFood.at("/data/foodId").asLong();
        assertThat(customFoodId).isPositive();
        assertThat(customFood.at("/data/nutrients/caloriesKcal").decimalValue()).isEqualByComparingTo(new BigDecimal("82.00"));

        JsonNode customSearch = getOkWithParams("/api/v1/foods/search", token, "q", "저 지방우 유", "limit", "5");
        assertThat(customSearch.at("/data/items/0/foodId").asLong()).isEqualTo(customFoodId);

        JsonNode allergies = postOk("/api/v1/users/me/allergies", token, """
                {
                  "allergyType": "INGREDIENT",
                  "ingredientName": "우유",
                  "reactionNote": "테스트"
                }
                """);
        assertThat(allergies.at("/data/items").size()).isGreaterThan(0);

        JsonNode mealLog = postCreated("/api/v1/meal-logs", token, """
                {
                  "logDate": "2026-05-13",
                  "mealType": "LUNCH",
                  "memo": "통합 테스트 점심"
                }
                """);
        long mealLogId = mealLog.at("/data/mealLogId").asLong();

        JsonNode addedFoods = postOk("/api/v1/meal-logs/" + mealLogId + "/food-items", token, """
                {
                  "items": [
                    {
                      "foodId": 3101,
                      "amountG": 100
                    },
                    {
                      "foodId": %d,
                      "amountG": 200
                    }
                  ]
                }
                """.formatted(customFoodId));
        assertThat(addedFoods.at("/data/items").size()).isEqualTo(2);
        assertThat(addedFoods.at("/data/items/1/allergyWarnings").size()).isGreaterThan(0);
        assertThat(addedFoods.at("/data/totals/caloriesKcal").decimalValue()).isGreaterThan(BigDecimal.ZERO);
        long firstMealLogItemId = addedFoods.at("/data/items/0/mealLogItemId").asLong();

        JsonNode dailySummary = getOk("/api/v1/home/daily-summary?date=2026-05-13", token);
        assertThat(dailySummary.at("/data/totals/caloriesKcal").decimalValue()).isGreaterThan(BigDecimal.ZERO);
        assertThat(dailySummary.at("/data/recommendedTargets/caloriesKcal").decimalValue()).isGreaterThan(BigDecimal.ZERO);

        JsonNode dailyMenu = getOk("/api/v1/menus/daily?universityId=2&date=2026-05-13&mealType=LUNCH", token);
        assertThat(dailyMenu.at("/data/diningPlaces").size()).isGreaterThanOrEqualTo(2);

        JsonNode compare = getOk(
                "/api/v1/menus/compare?universityId=2&date=2026-05-13&mealType=LUNCH&studentOptionId=" + TEST_STUDENT_OPTION_ID,
                token
        );
        assertThat(compare.at("/data/items").size()).isGreaterThanOrEqualTo(2);

        JsonNode addedMenu = postOk("/api/v1/meal-logs/from-menu-option", token, """
                {
                  "menuOptionId": %d,
                  "memo": "학생식당 메뉴 추가"
                }
                """.formatted(TEST_STUDENT_OPTION_ID));
        assertThat(addedMenu.at("/data/mealLogId").asLong()).isEqualTo(mealLogId);
        assertThat(addedMenu.at("/data/items").size()).isGreaterThanOrEqualTo(3);

        JsonNode excluded = patchOk("/api/v1/meal-logs/" + mealLogId + "/items/" + firstMealLogItemId + "/exclude?excluded=true", token);
        assertThat(excluded.at("/data/items/0/excluded").asBoolean()).isTrue();

        logout(token);
        getUnauthorized("/api/v1/users/me", token);
    }

    @Test
    void generalUserCannotUseSchoolDiningComparison() throws Exception {
        JsonNode signup = postCreated("/api/v1/auth/signup", null, """
                {
                  "email": "flow-general@example.com",
                  "password": "test",
                  "name": "일반사용자",
                  "age": 22
                }
                """);
        String token = signup.at("/data/accessToken").asText();
        assertThat(signup.at("/data/universityId").isNull()).isTrue();

        MvcResult result = mockMvc.perform(get("/api/v1/menus/compare")
                        .param("universityId", "2")
                        .param("date", "2026-05-13")
                        .param("mealType", "LUNCH")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.at("/error/code").asText()).isEqualTo("SCHOOL_EMAIL_USER_REQUIRED");
    }

    @Test
    void foodSearchKeepsDistinctPublicNutritionRowsWithSameName() throws Exception {
        seedDuplicateFoods();

        JsonNode response = getOkWithParams("/api/v1/foods/search", null, "q", "테스트중복음식", "limit", "10");

        JsonNode items = response.at("/data/items");
        assertThat(items.size()).isEqualTo(2);
        assertThat(items.get(0).at("/sourceName").asText()).isEqualTo("NATIONAL_INTEGRATED");
        assertThat(items.get(0).at("/foodName").asText()).isEqualTo("테스트 중복 음식");
        assertThat(items.get(1).at("/sourceName").asText()).isEqualTo("NATIONAL_INTEGRATED");
        assertThat(items.get(1).at("/foodName").asText()).isEqualTo("테스트중복음식");
    }

    @Test
    void foodSuggestionsUseLocalFallbackAndDeduplicateByName() throws Exception {
        seedDuplicateFoods();

        JsonNode response = getOkWithParams("/api/v1/foods/suggestions", null, "q", "테스트중복음식", "limit", "10");

        JsonNode items = response.at("/data/items");
        assertThat(items.size()).isEqualTo(1);
        assertThat(items.get(0).asText()).isEqualTo("테스트 중복 음식");
    }

    @Test
    void menuMatcherSplitsCompositeMenuItemsBeforeMatching() {
        LocalDate servedDate = LocalDate.of(2026, 6, 30);
        String optionName = "청양풍간장닭볶음 / 쌀밥 / 콩가루배추국 / 도시락김 / 실곤약콩나물무침 / 깍두기";
        seedCompositeMenuOption(servedDate, optionName);

        menuFoodMatcher.resolveMissingMenuItems(2L, servedDate, "LUNCH");

        List<String> rawItems = jdbcTemplate.query("""
                        select raw_item_name
                        from cafeteria_menu_item
                        where option_id = ?
                        order by menu_item_id
                        """,
                (rs, rowNum) -> rs.getString("raw_item_name"),
                TEST_COMPOSITE_OPTION_ID
        );
        assertThat(rawItems).containsExactly(
                "청양풍간장닭볶음",
                "쌀밥",
                "콩가루배추국",
                "도시락김",
                "실곤약콩나물무침",
                "깍두기"
        );
    }

    private void seedStudentMenuOptionForComparison() {
        jdbcTemplate.update("delete from cafeteria_menu_item where option_id = ?", TEST_STUDENT_OPTION_ID);
        jdbcTemplate.update("delete from cafeteria_menu_option where option_id = ?", TEST_STUDENT_OPTION_ID);
        jdbcTemplate.update("delete from cafeteria_menu where menu_id = ?", TEST_STUDENT_MENU_ID);
        jdbcTemplate.update("""
                insert into cafeteria_menu (menu_id, dining_place_id, meal_type, served_date)
                values (?, 3, 'LUNCH', ?)
                """, TEST_STUDENT_MENU_ID, LocalDate.of(2026, 5, 13));
        jdbcTemplate.update("""
                insert into cafeteria_menu_option (option_id, menu_id, category_id, option_name, source_label)
                values (?, ?, 16, '테스트 학생식당 라면', '통합 테스트 학생식당')
                """, TEST_STUDENT_OPTION_ID, TEST_STUDENT_MENU_ID);
        jdbcTemplate.update("""
                insert into cafeteria_menu_item (option_id, food_id, raw_item_name, amount_g)
                values (?, 3101, '라면', 550.00)
                """, TEST_STUDENT_OPTION_ID);
    }

    private void seedCompositeMenuOption(LocalDate servedDate, String optionName) {
        jdbcTemplate.update("delete from cafeteria_menu_item where option_id = ?", TEST_COMPOSITE_OPTION_ID);
        jdbcTemplate.update("delete from cafeteria_menu_option where option_id = ?", TEST_COMPOSITE_OPTION_ID);
        jdbcTemplate.update("delete from cafeteria_menu where menu_id = ?", TEST_COMPOSITE_MENU_ID);
        jdbcTemplate.update("""
                insert into cafeteria_menu (menu_id, dining_place_id, meal_type, served_date)
                values (?, 3, 'LUNCH', ?)
                """, TEST_COMPOSITE_MENU_ID, servedDate);
        jdbcTemplate.update("""
                insert into cafeteria_menu_option (option_id, menu_id, category_id, option_name, source_label)
                values (?, ?, (select min(category_id) from menu_category), ?, '통합 테스트 복합 메뉴')
                """, TEST_COMPOSITE_OPTION_ID, TEST_COMPOSITE_MENU_ID, optionName);
        jdbcTemplate.update("""
                insert into cafeteria_menu_item (option_id, food_id, raw_item_name, amount_g)
                values (?, null, ?, 100.00)
                """, TEST_COMPOSITE_OPTION_ID, optionName);
    }

    private void seedDuplicateFoods() {
        jdbcTemplate.update("delete from food_alias where food_id in (select food_id from food where source_food_code in (?, ?))",
                "TEST-DUP-PUBLIC-1", "TEST-DUP-PUBLIC-2");
        jdbcTemplate.update("delete from food where source_food_code in (?, ?)", "TEST-DUP-PUBLIC-1", "TEST-DUP-PUBLIC-2");
        jdbcTemplate.update("""
                insert into food (
                    source_name, source_food_code, food_name, default_serving_g,
                    calories_kcal, carb_g, protein_g, fat_g, sugar_g, sodium_mg
                )
                values ('NATIONAL_INTEGRATED', 'TEST-DUP-PUBLIC-1', '테스트중복음식', 100.00,
                        100.00, 10.00, 20.00, 30.00, 0.00, 0.00)
                """);
        jdbcTemplate.update("""
                insert into food (
                    source_name, source_food_code, food_name, default_serving_g,
                    calories_kcal, carb_g, protein_g, fat_g, sugar_g, sodium_mg
                )
                values ('NATIONAL_INTEGRATED', 'TEST-DUP-PUBLIC-2', '테스트 중복 음식', 100.00,
                        200.00, 20.00, 30.00, 40.00, 0.00, 0.00)
                """);
    }

    private String login(String email, String password) throws Exception {
        JsonNode response = postOk("/api/v1/auth/login", null, """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password));
        String token = response.at("/data/accessToken").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private void logout(String token) throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    private JsonNode getOk(String path, String token) throws Exception {
        var builder = get(path);
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        MvcResult result = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn();
        return json(result);
    }

    private JsonNode getOkWithParams(String path, String token, String... params) throws Exception {
        var builder = get(path);
        for (int i = 0; i < params.length; i += 2) {
            builder.param(params[i], params[i + 1]);
        }
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        MvcResult result = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn();
        return json(result);
    }

    private void getUnauthorized(String path, String token) throws Exception {
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode postOk(String path, String token, String content) throws Exception {
        var builder = post(path).contentType(MediaType.APPLICATION_JSON).content(content);
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        MvcResult result = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn();
        return json(result);
    }

    private JsonNode postCreated(String path, String token, String content) throws Exception {
        var builder = post(path).contentType(MediaType.APPLICATION_JSON).content(content);
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        MvcResult result = mockMvc.perform(builder)
                .andExpect(status().isCreated())
                .andReturn();
        return json(result);
    }

    private JsonNode putOk(String path, String token, String content) throws Exception {
        var builder = put(path).contentType(MediaType.APPLICATION_JSON).content(content);
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, bearer(token));
        }
        MvcResult result = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn();
        return json(result);
    }

    private JsonNode patchOk(String path, String token) throws Exception {
        MvcResult result = mockMvc.perform(patch(path)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result);
    }

    private JsonNode json(MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.at("/success").asBoolean()).isTrue();
        return response;
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
