package ro.mathlms.quiz;

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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

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
 * EN Subiectul III on real Postgres, over HTTP: a problem with requirements a) 2p and b) 3p is worth 5p, the student
 * sees the requirements but no barem, the teacher grades each requirement, the paper can be finalised only once both
 * have points, and the result shows every requirement with its points and barem.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class QuizItemPartsIntegrationTest {

    private static final List<SimpleGrantedAuthority> STUDENT =
            List.of(new SimpleGrantedAuthority("ROLE_STUDENT"), new SimpleGrantedAuthority("STATUS_ACTIVE"));
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;

    private User student;
    private long quizId;
    private long itemId;

    private static final RequestPostProcessor TEACHER = user("prof@scoala.ro").roles("ADMIN");

    private RequestPostProcessor asStudent() {
        return user(student.getEmail()).authorities(STUDENT);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               Object body) throws Exception {
        return mockMvc.perform(request.with(TEACHER).with(csrf()).contentType("application/json")
                .content(objectMapper.writeValueAsString(body)));
    }

    @BeforeEach
    void setUp() throws Exception {
        student = userRepository.save(new User("elev-" + System.nanoTime() + "@scoala.ro", "Ana", Role.STUDENT));
        quizId = json(send(post("/api/admin/quizzes"), Map.of("title", "Simulare EN " + System.nanoTime()))
                .andExpect(status().isCreated())).get("id").asLong();
        JsonNode item = json(send(post("/api/admin/quizzes/" + quizId + "/items"), Map.of(
                "type", "OPEN", "position", 0, "points", 0, "section", "SUBIECTUL al III-lea\nScrie rezolvările complete.",
                "statement", "Se consideră numerele reale $x$ și $y$.",
                "parts", List.of(
                        Map.of("statement", "Arată că $x=25$.", "points", 2, "solution", "x = 25 (2p)"),
                        Map.of("statement", "Calculează $(8y-x)^{27}$.", "points", 3, "solution", "-1 (3p)"))))
                .andExpect(status().isCreated()));
        itemId = item.get("id").asLong();
        assertThat(item.get("points").asInt()).isEqualTo(5); // the sum of its requirements
        assertThat(item.get("parts").findValuesAsString("label")).containsExactly("a", "b");
        send(post("/api/admin/quizzes/" + quizId + "/publish"), Map.of()).andExpect(status().isOk());
    }

    private long takeAndHandIn() throws Exception {
        JsonNode started = json(mockMvc.perform(post("/api/quiz/quizzes/" + quizId + "/attempts")
                .with(asStudent()).with(csrf())).andExpect(status().isOk()));
        JsonNode seen = started.get("quiz").get("items").get(0);
        assertThat(seen.get("parts").findValuesAsString("statement")).containsExactly("Arată că $x=25$.", "Calculează $(8y-x)^{27}$.");
        assertThat(started.toString()).doesNotContain("x = 25 (2p)"); // no barem before the result
        long attemptId = started.get("attemptId").asLong();
        mockMvc.perform(multipart("/api/quiz/attempts/" + attemptId + "/responses/" + itemId + "/photo")
                .file(new MockMultipartFile("file", "p.png", "image/png", PNG)).with(asStudent()).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/quiz/attempts/" + attemptId + "/submit").with(asStudent()).with(csrf()))
                .andExpect(status().isOk());
        return attemptId;
    }

    private List<Long> partIds(long attemptId) throws Exception {
        JsonNode paper = json(mockMvc.perform(get("/api/admin/quiz/attempts/" + attemptId).with(TEACHER)));
        return paper.get("items").get(0).get("parts").findValues("id").stream().map(JsonNode::asLong).toList();
    }

    private ResultActions grade(long attemptId, List<Map<String, Object>> parts) throws Exception {
        return send(put("/api/admin/quiz/attempts/" + attemptId + "/responses/" + itemId + "/grade"),
                Map.of("comment", "Atenție la b).", "parts", parts));
    }

    @Test
    void eachRequirementIsGradedAndThePaperIsFinalOnlyWhenAllHavePoints() throws Exception {
        long attemptId = takeAndHandIn();
        List<Long> parts = partIds(attemptId);

        grade(attemptId, List.of(Map.of("partId", parts.get(0), "points", 2))).andExpect(status().isNoContent());
        JsonNode half = json(mockMvc.perform(get("/api/admin/quiz/attempts/" + attemptId).with(TEACHER))).get("items").get(0);
        assertThat(half.get("awardedPoints").isNull()).isTrue(); // b) still missing
        assertThat(half.get("parts").get(0).get("awardedPoints").asInt()).isEqualTo(2);
        mockMvc.perform(post("/api/admin/quiz/attempts/" + attemptId + "/mark-graded").with(TEACHER).with(csrf()))
                .andExpect(status().is4xxClientError());

        grade(attemptId, List.of(Map.of("partId", parts.get(1), "points", 4))).andExpect(status().isBadRequest()); // b) is 3p
        grade(attemptId, List.of(Map.of("partId", parts.get(1), "points", 1))).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/admin/quiz/attempts/" + attemptId + "/mark-graded").with(TEACHER).with(csrf()))
                .andExpect(status().isNoContent());

        JsonNode result = json(mockMvc.perform(get("/api/quiz/attempts/" + attemptId + "/result").with(asStudent())));
        JsonNode item = result.get("items").get(0);
        assertThat(item.get("awardedPoints").asInt()).isEqualTo(3);
        assertThat(item.get("parts").findValues("awardedPoints").stream().map(JsonNode::asInt)).containsExactly(2, 1);
        assertThat(item.get("parts").findValuesAsString("barem")).containsExactly("x = 25 (2p)", "-1 (3p)");
        assertThat(result.get("finalScore").asInt()).isEqualTo(3);
    }

    @Test
    void aSingleGradeIsRefusedOnAProblemWithRequirements() throws Exception {
        long attemptId = takeAndHandIn();
        send(put("/api/admin/quiz/attempts/" + attemptId + "/responses/" + itemId + "/grade"), Map.of("points", 4))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onceTakenTheRequirementsKeepTheirPointsButTheirTextsCanBeCorrected() throws Exception {
        takeAndHandIn();
        Map<String, Object> changedPoints = Map.of("type", "OPEN", "position", 0, "points", 0,
                "statement", "Se consideră numerele reale $x$ și $y$.",
                "parts", List.of(Map.of("statement", "Arată că $x=25$.", "points", 3),
                        Map.of("statement", "Calculează $(8y-x)^{27}$.", "points", 2)));
        send(put("/api/admin/quiz-items/" + itemId), changedPoints).andExpect(status().isConflict());

        Map<String, Object> fixedText = Map.of("type", "OPEN", "position", 0, "points", 0,
                "statement", "Se consideră numerele reale $x$ și $y$.",
                "parts", List.of(Map.of("statement", "Arată că $x = 25$.", "points", 2, "solution", "x = 25"),
                        Map.of("statement", "Calculează $(8y - x)^{27}$.", "points", 3)));
        JsonNode saved = json(send(put("/api/admin/quiz-items/" + itemId), fixedText).andExpect(status().isOk()));
        assertThat(saved.get("parts").get(0).get("statement").asString()).isEqualTo("Arată că $x = 25$.");

        long copyId = json(send(post("/api/admin/quizzes/" + quizId + "/copy"), Map.of()).andExpect(status().isCreated()))
                .get("id").asLong();
        JsonNode copy = json(mockMvc.perform(get("/api/admin/quizzes/" + copyId).with(TEACHER)));
        assertThat(copy.get("items").get(0).get("parts").findValues("points").stream().map(JsonNode::asInt))
                .containsExactly(2, 3);
    }
}
