package ro.mathlms.enchapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEntryRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The teacher's screen for the automatic links (which chapter each place on the EN paper points at by default):
 * the 22 seeded by V34 are listed, a save replaces them all, and questions follow the new defaults at once. Every
 * test puts the seeded list back, since other tests rely on it.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class EnChapterSlotsIntegrationTest {

    private static final RequestPostProcessor TEACHER =
            user("prof@scoala.ro").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("STATUS_ACTIVE"));
    private static final RequestPostProcessor STUDENT =
            user("elev@scoala.ro").authorities(new SimpleGrantedAuthority("ROLE_STUDENT"), new SimpleGrantedAuthority("STATUS_ACTIVE"));

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuditEntryRepository auditRepository;

    private final Map<String, Long> chapters = new HashMap<>();
    private JsonNode seeded;

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private ResultActions save(Object slots) throws Exception {
        return mockMvc.perform(put("/api/admin/en-chapter-slots").with(TEACHER).with(csrf())
                .contentType("application/json").content(objectMapper.writeValueAsString(Map.of("slots", slots))));
    }

    @BeforeEach
    void setUp() throws Exception {
        for (JsonNode c : json(mockMvc.perform(get("/api/en-chapters").with(TEACHER)))) {
            chapters.put(c.get("code").asText(), c.get("id").asLong());
        }
        seeded = json(mockMvc.perform(get("/api/admin/en-chapter-slots").with(TEACHER)).andExpect(status().isOk()));
    }

    @AfterEach
    void restore() throws Exception {
        save(objectMapper.convertValue(seeded, List.class)).andExpect(status().isNoContent());
    }

    private Map<String, Long> bySlot(JsonNode slots) {
        Map<String, Long> out = new LinkedHashMap<>();
        slots.forEach(s -> out.put(s.get("slot").asText(), s.get("chapterId").asLong()));
        return out;
    }

    @Test
    void listsTheSeededLinksInPaperOrder() {
        Map<String, Long> links = bySlot(seeded);

        assertThat(links).hasSize(22);
        assertThat(links.get("I.1")).isEqualTo(chapters.get("A1"));
        assertThat(links.get("III.6.b")).isEqualTo(chapters.get("G9"));
        assertThat(new ArrayList<>(links.keySet()).subList(0, 3)).containsExactly("I.1", "I.2", "I.3");
        assertThat(new ArrayList<>(links.keySet()).indexOf("III.5")).isLessThan(new ArrayList<>(links.keySet()).indexOf("III.5.a"));
    }

    @Test
    void aSaveReplacesThemAllAndQuestionsFollowAtOnce() throws Exception {
        List<Map<String, Object>> changed = new ArrayList<>();
        for (Map.Entry<String, Long> e : bySlot(seeded).entrySet()) {
            if (e.getKey().equals("II.6")) {
                continue; // no default any more
            }
            changed.add(Map.of("slot", e.getKey(), "chapterId", e.getKey().equals("I.2") ? chapters.get("A3") : e.getValue()));
        }
        changed.add(Map.of("slot", "III.4.a", "chapterId", chapters.get("G4")));
        save(changed).andExpect(status().isNoContent());

        Map<String, Long> after = bySlot(json(mockMvc.perform(get("/api/admin/en-chapter-slots").with(TEACHER))));
        assertThat(after.get("I.2")).isEqualTo(chapters.get("A3"));
        assertThat(after).doesNotContainKey("II.6").containsEntry("III.4.a", chapters.get("G4"));
        assertThat(auditRepository.findAll()).anyMatch(e -> e.getAction() == AuditAction.EN_CHAPTER_SLOTS_UPDATED);

        // A question in place I.2 now points at A3 by default.
        long quizId = json(mockMvc.perform(post("/api/admin/quizzes").with(TEACHER).with(csrf()).contentType("application/json")
                .content("{\"title\":\"Sloturi " + System.nanoTime() + "\"}"))).get("id").asLong();
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/admin/quizzes/" + quizId + "/items").with(TEACHER).with(csrf()).contentType("application/json")
                            .content("{\"type\":\"OPEN\",\"position\":" + i + ",\"points\":5,\"section\":\"SUBIECTUL I\",\"statement\":\"Q" + i + "\"}"))
                    .andExpect(status().isCreated());
        }
        JsonNode items = json(mockMvc.perform(get("/api/admin/quizzes/" + quizId).with(TEACHER))).get("items");
        assertThat(items.get(1).get("enChapterAuto").asLong()).isEqualTo(chapters.get("A3"));
    }

    @Test
    void aPlaceThatIsNotOnTheEnPaperOrAChapterThatDoesNotExistIsRefused() throws Exception {
        save(List.of(Map.of("slot", "IV.1", "chapterId", chapters.get("A1")))).andExpect(status().isBadRequest());
        save(List.of(Map.of("slot", "I.7", "chapterId", chapters.get("A1")))).andExpect(status().isBadRequest());
        save(List.of(Map.of("slot", "I.1.a", "chapterId", chapters.get("A1")))).andExpect(status().isBadRequest()); // grile have no a), b)
        save(List.of(Map.of("slot", "I.1", "chapterId", 999_999_999L))).andExpect(status().isBadRequest());
        save(List.of(Map.of("slot", "I.1", "chapterId", chapters.get("A1")),
                Map.of("slot", "I.1", "chapterId", chapters.get("A2")))).andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheTeacherSeesAndChangesThem() throws Exception {
        mockMvc.perform(get("/api/admin/en-chapter-slots").with(STUDENT)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/en-chapter-slots").with(STUDENT).with(csrf())
                .contentType("application/json").content("{\"slots\":[]}")).andExpect(status().isForbidden());
    }
}
