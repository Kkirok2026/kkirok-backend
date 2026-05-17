package com.database2026.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
                  "caloriesKcal": 80,
                  "carbG": 10,
                  "proteinG": 6,
                  "fatG": 2,
                  "sugarG": 8,
                  "sodiumMg": 100
                }
                """);
        long customFoodId = customFood.at("/data/foodId").asLong();
        assertThat(customFoodId).isPositive();

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

    private void seedStudentMenuOptionForComparison() {
        Long menuId = jdbcTemplate.query("""
                        select m.menu_id
                        from cafeteria_menu m
                        where m.dining_place_id = 3
                          and m.meal_type = 'LUNCH'
                          and m.served_date = ?
                        """,
                (rs, rowNum) -> rs.getLong("menu_id"),
                LocalDate.of(2026, 5, 13)
        ).getFirst();
        jdbcTemplate.update("delete from cafeteria_menu_item where option_id = ?", TEST_STUDENT_OPTION_ID);
        jdbcTemplate.update("delete from cafeteria_menu_option where option_id = ?", TEST_STUDENT_OPTION_ID);
        jdbcTemplate.update("""
                insert into cafeteria_menu_option (option_id, menu_id, category_id, option_name, source_label)
                values (?, ?, 16, '테스트 학생식당 라면', '통합 테스트 학생식당')
                """, TEST_STUDENT_OPTION_ID, menuId);
        jdbcTemplate.update("""
                insert into cafeteria_menu_item (option_id, food_id, raw_item_name, amount_g)
                values (?, 3101, '라면', 550.00)
                """, TEST_STUDENT_OPTION_ID);
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
