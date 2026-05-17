package com.database2026.backend.food;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FoodSearchAutocompleteIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void foodSearchFallsBackToFatSecretAutocompleteSuggestions() throws Exception {
        jdbcTemplate.update("delete from food_alias where food_id in (select food_id from food where source_food_code = ?)",
                "TEST-AUTOCOMPLETE-FATSECRET");
        jdbcTemplate.update("delete from food where source_food_code = ?", "TEST-AUTOCOMPLETE-FATSECRET");

        MvcResult result = mockMvc.perform(get("/api/v1/foods/search")
                        .param("q", "두쫀쿠")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode items = response.at("/data/items");
        assertThat(response.at("/success").asBoolean()).isTrue();
        assertThat(items.size()).isEqualTo(1);
        assertThat(items.get(0).at("/sourceName").asText()).isEqualTo("FATSECRET");
        assertThat(items.get(0).at("/sourceFoodCode").asText()).isEqualTo("TEST-AUTOCOMPLETE-FATSECRET");
        assertThat(items.get(0).at("/foodName").asText()).isEqualTo("두쫀볼");
    }

    @TestConfiguration
    static class FatSecretTestConfig {
        @Bean
        @Primary
        FatSecretApiClient fatSecretApiClient() {
            return new FakeFatSecretApiClient();
        }
    }

    private static class FakeFatSecretApiClient extends FatSecretApiClient {
        FakeFatSecretApiClient() {
            super("test-client-id", "test-client-secret", "premier", "KR", "ko", 5000);
        }

        @Override
        boolean hasCredentials() {
            return true;
        }

        @Override
        List<FoodRow> searchFoods(String query, int limit) {
            if (!"두쫀볼".equals(query)) {
                return List.of();
            }
            return List.of(new FoodRow(
                    "TEST-AUTOCOMPLETE-FATSECRET",
                    "두쫀볼",
                    null,
                    "Generic",
                    BigDecimal.valueOf(100),
                    BigDecimal.valueOf(210),
                    BigDecimal.valueOf(30),
                    BigDecimal.valueOf(12),
                    BigDecimal.valueOf(6),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO
            ));
        }

        @Override
        List<String> autocomplete(String query, int limit) {
            if ("두쫀쿠".equals(query)) {
                return List.of("두쫀볼");
            }
            return List.of();
        }
    }
}
