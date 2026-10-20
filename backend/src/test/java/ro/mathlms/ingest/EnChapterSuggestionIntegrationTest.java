package ro.mathlms.ingest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 3 of the EN chapters, on real Postgres with the offline fake suggester: an AI import proposes a chapter (and
 * why) for every question it read, the teacher's pick goes into the quiz; on an existing test the teacher asks for
 * proposals and applies the ones they keep.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class EnChapterSuggestionIntegrationTest {

    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private AiImportService importService;

    private RequestPostProcessor teacher;
    private final Map<String, Long> chapters = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        User prof = userRepository.save(new User("prof-" + System.nanoTime() + "@scoala.ro", "Prof", Role.ADMIN));
        teacher = user(prof.getEmail()).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("STATUS_ACTIVE"));
        while (importService.processNext()) {
            // other tests' leftovers must not be read as ours
        }
        for (JsonNode c : json(mockMvc.perform(get("/api/en-chapters").with(teacher)))) {
            chapters.put(c.get("code").asString(), c.get("id").asLong());
        }
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mockMvc.perform(request.with(teacher).with(csrf()).contentType("application/json")
                .content(objectMapper.writeValueAsString(body)));
    }

    @Test
    void anImportProposesAChapterForEveryQuestionAndTheTeachersPickGoesIntoTheQuiz() throws Exception {
        long id = json(mockMvc.perform(multipart("/api/admin/ai-imports")
                        .file(new MockMultipartFile("files", "p1.png", "image/png", PNG)).with(teacher).with(csrf()))
                .andExpect(status().isAccepted())).get("id").asLong();
        assertThat(importService.processNext()).isTrue();

        JsonNode items = json(mockMvc.perform(get("/api/admin/ai-imports/" + id).with(teacher))).get("items");
        // The fake reads "Rezultatul calculului ..." and "triunghiul ABC ... perimetrul".
        assertThat(items.get(0).get("enChapter").asString()).isEqualTo("A1");
        assertThat(items.get(0).get("enChapterReason").asString()).isNotBlank();
        assertThat(items.get(1).get("enChapter").asString()).isEqualTo("G3");

        Map<String, Object> grila = new LinkedHashMap<>();
        grila.put("type", "SINGLE_CHOICE");
        grila.put("statement", items.get(0).get("statement").asString());
        grila.put("points", 5);
        grila.put("section", "SUBIECTUL I");
        grila.put("options", List.of(Map.of("position", 0, "text", "5", "correct", false),
                Map.of("position", 1, "text", "7", "correct", true)));
        grila.put("enChapterId", chapters.get("A1")); // I.1's automatic link anyway
        Map<String, Object> triangle = new LinkedHashMap<>(grila);
        triangle.put("statement", items.get(1).get("statement").asString());
        triangle.put("enChapterId", chapters.get("G3")); // not I.2's A2: the teacher's own choice
        long quizId = json(send(post("/api/admin/ai-imports/" + id + "/quiz"),
                Map.of("title", "Din manual " + id, "items", List.of(grila, triangle))).andExpect(status().isCreated()))
                .get("quizId").asLong();

        JsonNode created = json(mockMvc.perform(get("/api/admin/quizzes/" + quizId).with(teacher))).get("items");
        // The same as automatic stays automatic, so it follows the automatic links when they change.
        assertThat(created.get(0).get("enChapterId").isNull()).isTrue();
        assertThat(created.get(0).get("enChapterAuto").asLong()).isEqualTo(chapters.get("A1"));
        assertThat(created.get(1).get("enChapterId").asLong()).isEqualTo(chapters.get("G3"));
    }

    @Test
    void anImportedRequirementKeepsOnlyAChapterItsProblemWouldNotGiveIt() throws Exception {
        long id = json(mockMvc.perform(multipart("/api/admin/ai-imports")
                        .file(new MockMultipartFile("files", "p1.png", "image/png", PNG)).with(teacher).with(csrf()))
                .andExpect(status().isAccepted())).get("id").asLong();
        assertThat(importService.processNext()).isTrue();

        Map<String, Object> problem = new LinkedHashMap<>();
        problem.put("type", "OPEN");
        problem.put("statement", "Cubul ABCDA'B'C'D'");
        problem.put("points", 10);
        problem.put("section", "SUBIECTUL al III-lea");
        problem.put("enChapterId", chapters.get("G9")); // III.1's automatic link is A9: kept
        problem.put("parts", List.of(
                Map.of("statement", "Aria", "points", 5, "enChapterId", chapters.get("G9")), // = its problem: automatic
                Map.of("statement", "Volumul", "points", 5, "enChapterId", chapters.get("G8"))));
        long quizId = json(send(post("/api/admin/ai-imports/" + id + "/quiz"),
                Map.of("title", "Din manual " + id, "items", List.of(problem))).andExpect(status().isCreated()))
                .get("quizId").asLong();

        JsonNode created = json(mockMvc.perform(get("/api/admin/quizzes/" + quizId).with(teacher))).get("items").get(0);
        assertThat(created.get("enChapterId").asLong()).isEqualTo(chapters.get("G9"));
        assertThat(created.get("parts").get(0).get("enChapterId").isNull()).isTrue();
        assertThat(created.get("parts").get(0).get("enChapterAuto").asLong()).isEqualTo(chapters.get("G9"));
        assertThat(created.get("parts").get(1).get("enChapterId").asLong()).isEqualTo(chapters.get("G8"));
    }

    @Test
    void onAnExistingTestTheTeacherAsksForProposalsAndAppliesThem() throws Exception {
        long quizId = json(send(post("/api/admin/quizzes"), Map.of("title", "Simulare " + System.nanoTime()))
                .andExpect(status().isCreated())).get("id").asLong();
        long grila = json(send(post("/api/admin/quizzes/" + quizId + "/items"), Map.of(
                "type", "SINGLE_CHOICE", "position", 0, "points", 5, "section", "SUBIECTUL I",
                "statement", "Dintre cei 250 de elevi, $40\\%$ sunt băieți. Numărul băieților este:",
                "options", List.of(Map.of("position", 0, "text", "100", "correct", true),
                        Map.of("position", 1, "text", "150", "correct", false)))).andExpect(status().isCreated()))
                .get("id").asLong();
        JsonNode cube = json(send(post("/api/admin/quizzes/" + quizId + "/items"), Map.of(
                "type", "OPEN", "position", 1, "points", 5, "section", "SUBIECTUL al III-lea",
                "statement", "În figura alăturată este reprezentat cubul $ABCDA'B'C'D'$ cu $AB = 8$ cm.",
                "parts", List.of(Map.of("statement", "Arată că volumul cubului este $512$ cm³.", "points", 2),
                        Map.of("statement", "Arată că distanța de la punctul $D'$ la planul $(MNP)$ este $\\frac{4\\sqrt{3}}{3}$.", "points", 3))))
                .andExpect(status().isCreated()));

        JsonNode proposals = json(mockMvc.perform(post("/api/admin/quizzes/" + quizId + "/en-chapter-suggestions")
                .with(teacher).with(csrf())).andExpect(status().isOk())).get("items");

        assertThat(proposals.get(0).get("itemId").asLong()).isEqualTo(grila);
        assertThat(proposals.get(0).get("code").asString()).isEqualTo("A3");
        assertThat(proposals.get(0).get("chapterId").asLong()).isEqualTo(chapters.get("A3"));
        assertThat(proposals.get(0).get("reason").asString()).isNotBlank();
        JsonNode parts = proposals.get(1).get("parts");
        assertThat(parts.get(0).get("code").asString()).isEqualTo("G8");
        assertThat(parts.get(1).get("code").asString()).isEqualTo("G9");

        // Nothing changes until the teacher applies what they keep: here the grilă and requirement b) only.
        JsonNode before = json(mockMvc.perform(get("/api/admin/quizzes/" + quizId).with(teacher))).get("items");
        assertThat(before.get(0).get("enChapterId").isNull()).isTrue();

        long partB = cube.get("parts").get(1).get("id").asLong();
        send(put("/api/admin/quizzes/" + quizId + "/en-chapters"), Map.of("items", List.of(
                Map.of("itemId", grila, "enChapterId", chapters.get("A3")),
                Map.of("itemId", cube.get("id").asLong(), "parts", List.of(Map.of("partId", partB, "enChapterId", chapters.get("G9")))))))
                .andExpect(status().isNoContent());

        JsonNode after = json(mockMvc.perform(get("/api/admin/quizzes/" + quizId).with(teacher))).get("items");
        assertThat(after.get(0).get("enChapterId").asLong()).isEqualTo(chapters.get("A3"));
        assertThat(after.get(1).get("enChapterId").isNull()).isTrue();
        assertThat(after.get(1).get("parts").get(0).get("enChapterId").isNull()).isTrue();
        assertThat(after.get(1).get("parts").get(1).get("enChapterId").asLong()).isEqualTo(chapters.get("G9"));
    }

    @Test
    void aQuestionOfAnotherTestCannotBeChangedThroughThisOne() throws Exception {
        long quizId = json(send(post("/api/admin/quizzes"), Map.of("title", "A " + System.nanoTime()))
                .andExpect(status().isCreated())).get("id").asLong();
        long otherQuiz = json(send(post("/api/admin/quizzes"), Map.of("title", "B " + System.nanoTime()))
                .andExpect(status().isCreated())).get("id").asLong();
        long foreign = json(send(post("/api/admin/quizzes/" + otherQuiz + "/items"), Map.of(
                "type", "OPEN", "position", 0, "points", 5, "statement", "Calculează.")).andExpect(status().isCreated()))
                .get("id").asLong();

        send(put("/api/admin/quizzes/" + quizId + "/en-chapters"), Map.of("items", List.of(
                Map.of("itemId", foreign, "enChapterId", chapters.get("A1"))))).andExpect(status().isNotFound());
    }
}
