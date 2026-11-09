package ro.mathlms.aiusage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import ro.mathlms.TestcontainersConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every billed AI call is one row, and the admin reads this month's totals per feature and model. The database is
 * shared with other test classes, so each test uses a model name of its own.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AiUsageIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AiUsageRecorder recorder;

    @Test
    void theAdminSeesThisMonthsCallsAndTokensPerFeatureAndModel() throws Exception {
        String model = "test-model-" + System.nanoTime();
        recorder.record(AiUsageRecorder.GRADING, model, 4000, 2500);
        recorder.record(AiUsageRecorder.GRADING, model, 3000, 1500);
        recorder.record(AiUsageRecorder.IMPORT, model, 1000, null); // a call that reported no output tokens

        JsonNode rows = objectMapper.readTree(mockMvc.perform(get("/api/admin/ai-usage").with(user("admin@x.ro").roles("ADMIN")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("rows");

        JsonNode grading = find(rows, AiUsageRecorder.GRADING, model);
        assertThat(grading.get("calls").asInt()).isEqualTo(2);
        assertThat(grading.get("inputTokens").asLong()).isEqualTo(7000);
        assertThat(grading.get("outputTokens").asLong()).isEqualTo(4000);
        JsonNode imported = find(rows, AiUsageRecorder.IMPORT, model);
        assertThat(imported.get("calls").asInt()).isEqualTo(1);
        assertThat(imported.get("outputTokens").asLong()).isZero();
    }

    @Test
    void aTeacherCannotReadTheBill() throws Exception {
        mockMvc.perform(get("/api/admin/ai-usage").with(user("prof@x.ro").roles("TEACHER"))).andExpect(status().isForbidden());
    }

    @Test
    void aCallWithoutAnyTokenCountIsNotRecorded() throws Exception {
        String model = "test-empty-" + System.nanoTime();
        recorder.record(AiUsageRecorder.BAREM, model, null, null);

        JsonNode rows = objectMapper.readTree(mockMvc.perform(get("/api/admin/ai-usage").with(user("admin@x.ro").roles("ADMIN")))
                .andReturn().getResponse().getContentAsString()).get("rows");
        boolean found = false;
        for (JsonNode row : rows) {
            found |= model.equals(row.get("model").asString());
        }
        assertThat(found).isFalse();
    }

    private static JsonNode find(JsonNode rows, String feature, String model) {
        for (JsonNode row : rows) {
            if (feature.equals(row.get("feature").asString()) && model.equals(row.get("model").asString())) {
                return row;
            }
        }
        throw new AssertionError("no row for " + feature + " " + model + " in " + rows);
    }
}
