package ro.mathlms.enchapter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.mathlms.TestcontainersConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * V35: every seeded EN chapter page starts with theory and worked examples, which a student can read at once (so the
 * "Vezi modele" button shows on a result) and the teacher can edit like anything they wrote.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class EnChapterTheoryIntegrationTest {

    private static final RequestPostProcessor STUDENT = user("elev@scoala.ro").authorities(
            new SimpleGrantedAuthority("ROLE_STUDENT"), new SimpleGrantedAuthority("STATUS_ACTIVE"));
    private static final List<String> SEEDED = List.of("A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8", "A9", "A10",
            "A11", "G1", "G2", "G3", "G4", "G5", "G6", "G7", "G8", "G9");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    private JsonNode read(String url) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(url).with(STUDENT)).andReturn().getResponse().getContentAsString());
    }

    @Test
    void everySeededChapterStartsWithTheoryAndWorkedExamples() throws Exception {
        Map<String, Long> ids = new HashMap<>();
        for (JsonNode c : read("/api/en-chapters")) {
            ids.put(c.get("code").asString(), c.get("id").asLong());
        }
        for (String code : SEEDED) {
            JsonNode page = read("/api/en-chapters/" + ids.get(code));
            assertThat(page.get("notes").asString()).as(code).hasSizeGreaterThan(500);
            assertThat(page.get("examples")).as(code).hasSizeGreaterThanOrEqualTo(4);
            page.get("examples").forEach(e -> {
                assertThat(e.get("statement").asString()).as(code).isNotBlank();
                assertThat(e.get("solution").asString()).as(code).isNotBlank();
            });
        }

        JsonNode a1 = read("/api/en-chapters/" + ids.get("A1"));
        assertThat(a1.get("notes").asString()).startsWith("Ordinea efectuării operațiilor");
        assertThat(a1.get("examples").get(0).get("statement").asString()).isEqualTo("Calculează $30 - 18 : 3$.");
        assertThat(a1.get("examples").get(0).get("solution").asString()).contains("Rezultat: $24$");
    }
}
