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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The final result of a Subiectul III requirement, checked with no AI (V72), on real Postgres over HTTP: the teacher
 * writes the expected result next to the barem, the student types theirs next to the photo without ever being shown
 * the expected one, and the grading screen is told whether the two are the same value. Nothing here gives points.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PartResultIntegrationTest {

    private static final List<SimpleGrantedAuthority> STUDENT =
            List.of(new SimpleGrantedAuthority("ROLE_STUDENT"), new SimpleGrantedAuthority("STATUS_ACTIVE"));
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    private static final RequestPostProcessor TEACHER = user("prof@scoala.ro").roles("ADMIN");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;

    private User student;
    private long quizId;
    private long itemId;
    /** The requirements' ids: a) asks for no result, b) expects -1, c) expects 0,5. */
    private List<Long> parts;

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

    private static Map<String, Object> problem(String resultOfB, String resultOfC) {
        Map<String, Object> b = new HashMap<>(Map.of("statement", "Calculează $(8y-x)^{27}$.", "points", 3, "solution", "barem b"));
        b.put("finalAnswer", resultOfB);
        Map<String, Object> c = new HashMap<>(Map.of("statement", "Determină $y$.", "points", 2, "solution", "barem c"));
        c.put("finalAnswer", resultOfC);
        return Map.of("type", "OPEN", "position", 0, "points", 0,
                "statement", "Se consideră numerele reale $x$ și $y$.",
                "parts", List.of(Map.of("statement", "Arată că $x=25$.", "points", 2, "solution", "barem a"), b, c));
    }

    /** A published test with the one problem; {@code maxAttempts} above one keeps its key hidden after a hand-in. */
    private void createQuiz(int maxAttempts) throws Exception {
        quizId = json(send(post("/api/admin/quizzes"), Map.of("title", "Simulare EN " + System.nanoTime(),
                "maxAttempts", maxAttempts)).andExpect(status().isCreated())).get("id").asLong();
        JsonNode item = json(send(post("/api/admin/quizzes/" + quizId + "/items"), problem("-1", "0,5"))
                .andExpect(status().isCreated()));
        itemId = item.get("id").asLong();
        parts = item.get("parts").findValues("id").stream().map(JsonNode::asLong).toList();
        send(post("/api/admin/quizzes/" + quizId + "/publish"), Map.of()).andExpect(status().isOk());
    }

    @BeforeEach
    void setUp() throws Exception {
        student = userRepository.save(new User("elev-" + System.nanoTime() + "@scoala.ro", "Ana", Role.STUDENT));
        createQuiz(1);
    }

    private JsonNode start() throws Exception {
        return json(mockMvc.perform(post("/api/quiz/quizzes/" + quizId + "/attempts").with(asStudent()).with(csrf()))
                .andExpect(status().isOk()));
    }

    private ResultActions type(long attemptId, long item, Map<Long, String> values, UUID browser, Long seq) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("partValues", values);
        body.put("clientId", browser);
        body.put("clientSeq", seq);
        return mockMvc.perform(put("/api/quiz/attempts/" + attemptId + "/responses/" + item).with(asStudent()).with(csrf())
                .contentType("application/json").content(objectMapper.writeValueAsString(body)));
    }

    private void photoAndHandIn(long attemptId) throws Exception {
        mockMvc.perform(multipart("/api/quiz/attempts/" + attemptId + "/responses/" + itemId + "/photo")
                .file(new MockMultipartFile("file", "p.png", "image/png", PNG)).with(asStudent()).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/quiz/attempts/" + attemptId + "/submit").with(asStudent()).with(csrf()))
                .andExpect(status().isOk());
    }

    private JsonNode paperParts(long attemptId) throws Exception {
        return json(mockMvc.perform(get("/api/admin/quiz/attempts/" + attemptId).with(TEACHER))).get("items").get(0).get("parts");
    }

    private static String text(JsonNode node, String field) {
        return node.get(field).isNull() ? null : node.get(field).asString();
    }

    @Test
    void theEditorKeepsTheExpectedResultAndTheStudentIsOnlyToldWhichRequirementsAskForOne() throws Exception {
        JsonNode edited = json(mockMvc.perform(get("/api/admin/quizzes/" + quizId).with(TEACHER))).get("items").get(0).get("parts");
        assertThat(text(edited.get(0), "finalAnswer")).isNull();
        assertThat(text(edited.get(1), "finalAnswer")).isEqualTo("-1");
        assertThat(text(edited.get(2), "finalAnswer")).isEqualTo("0,5");

        JsonNode started = start();
        JsonNode seen = started.get("quiz").get("items").get(0).get("parts");
        assertThat(seen.findValues("asksResult").stream().map(JsonNode::asBoolean)).containsExactly(false, true, true);
        // The expected result is the answer: nothing of it reaches the student's page.
        assertThat(started.toString()).doesNotContain("finalAnswer").doesNotContain("expectedResult").doesNotContain("0,5");
    }

    @Test
    void anExpectedResultThatIsNotANumberIsRefused() throws Exception {
        send(post("/api/admin/quizzes/" + quizId + "/items"), problem("6 cm", null)).andExpect(status().isBadRequest());
        send(post("/api/admin/quizzes/" + quizId + "/items"), problem("  ", null)).andExpect(status().isCreated()); // blank = none
    }

    @Test
    void theTypedResultsComeBackOnTheTestPageAndAreCheckedForTheTeacher() throws Exception {
        long attemptId = start().get("attemptId").asLong();
        type(attemptId, itemId, Map.of(parts.get(1), "−1", parts.get(2), "0,6"), UUID.randomUUID(), 1L)
                .andExpect(status().isNoContent());

        // Reopening the test restores what was typed, with no photo yet.
        JsonNode saved = start().get("answers").get(0);
        assertThat(saved.get("itemId").asLong()).isEqualTo(itemId);
        assertThat(saved.get("photoUploaded").asBoolean()).isFalse();
        assertThat(saved.get("partAnswers").get(String.valueOf(parts.get(1))).asString()).isEqualTo("−1");
        assertThat(saved.get("partAnswers").get(String.valueOf(parts.get(2))).asString()).isEqualTo("0,6");

        photoAndHandIn(attemptId);
        JsonNode graded = paperParts(attemptId);
        assertThat(graded.get(0).get("resultCheck").isNull()).isTrue(); // a) asks for no result
        assertThat(text(graded.get(0), "typedResult")).isNull();
        assertThat(text(graded.get(1), "typedResult")).isEqualTo("−1");
        assertThat(text(graded.get(1), "expectedResult")).isEqualTo("-1");
        assertThat(text(graded.get(1), "resultCheck")).isEqualTo("RIGHT");
        assertThat(text(graded.get(2), "typedResult")).isEqualTo("0,6");
        assertThat(text(graded.get(2), "resultCheck")).isEqualTo("WRONG");
        // A right result gives no points by itself: the teacher still grades every requirement.
        assertThat(graded.findValues("awardedPoints").stream().allMatch(JsonNode::isNull)).isTrue();
    }

    @Test
    void aRequirementWithNothingTypedIsMissingAndTheWholeSetReplacesTheOldOne() throws Exception {
        long attemptId = start().get("attemptId").asLong();
        UUID browser = UUID.randomUUID();
        type(attemptId, itemId, Map.of(parts.get(1), "-1", parts.get(2), "1/2"), browser, 1L).andExpect(status().isNoContent());
        type(attemptId, itemId, Map.of(parts.get(2), "1/2"), browser, 3L).andExpect(status().isNoContent()); // b) wiped
        // Sent before the wipe, arrived after it: answered as saved, changes nothing.
        type(attemptId, itemId, Map.of(parts.get(1), "7", parts.get(2), "7"), browser, 2L).andExpect(status().isNoContent());

        photoAndHandIn(attemptId);
        JsonNode graded = paperParts(attemptId);
        assertThat(text(graded.get(1), "typedResult")).isNull();
        assertThat(text(graded.get(1), "resultCheck")).isEqualTo("MISSING");
        assertThat(text(graded.get(2), "typedResult")).isEqualTo("1/2");
        assertThat(text(graded.get(2), "resultCheck")).isEqualTo("RIGHT"); // 1/2 = 0,5
    }

    @Test
    void aResultIsRefusedWhereNoneIsAsked() throws Exception {
        long attemptId = start().get("attemptId").asLong();
        type(attemptId, itemId, Map.of(parts.get(0), "25"), null, null).andExpect(status().isBadRequest()); // "Arată că…"
        type(attemptId, itemId, Map.of(999_999_999L, "25"), null, null).andExpect(status().isBadRequest());
        type(attemptId, itemId, Map.of(parts.get(1), "9".repeat(41)), null, null).andExpect(status().isBadRequest());

        // Another student's attempt is not there to be typed into.
        User other = userRepository.save(new User("alt-" + System.nanoTime() + "@scoala.ro", "Dan", Role.STUDENT));
        mockMvc.perform(put("/api/quiz/attempts/" + attemptId + "/responses/" + itemId)
                        .with(user(other.getEmail()).authorities(STUDENT)).with(csrf()).contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("partValues", Map.of(parts.get(1), "-1")))))
                .andExpect(status().is4xxClientError());

        photoAndHandIn(attemptId);
        assertThat(text(paperParts(attemptId).get(1), "resultCheck")).isEqualTo("MISSING"); // nothing got through
        // Handed in: the results are part of the paper now.
        type(attemptId, itemId, Map.of(parts.get(1), "-1"), null, null).andExpect(status().is4xxClientError());
    }

    @Test
    void theStudentSeesTheVerdictOnceTheBaremIsTheirsToSee() throws Exception {
        long attemptId = start().get("attemptId").asLong();
        type(attemptId, itemId, Map.of(parts.get(1), "-1", parts.get(2), "3"), null, null).andExpect(status().isNoContent());
        photoAndHandIn(attemptId);

        JsonNode result = json(mockMvc.perform(get("/api/quiz/attempts/" + attemptId + "/result").with(asStudent())))
                .get("items").get(0).get("parts");
        assertThat(text(result.get(1), "resultCheck")).isEqualTo("RIGHT");
        assertThat(text(result.get(2), "resultCheck")).isEqualTo("WRONG");
        assertThat(text(result.get(2), "expectedResult")).isEqualTo("0,5");
        assertThat(text(result.get(2), "typedResult")).isEqualTo("3");
    }

    @Test
    void whileTheTestCanBeHandedInAgainTheVerdictStaysHidden() throws Exception {
        createQuiz(2);
        long attemptId = start().get("attemptId").asLong();
        type(attemptId, itemId, Map.of(parts.get(1), "-1", parts.get(2), "3"), null, null).andExpect(status().isNoContent());
        photoAndHandIn(attemptId);

        JsonNode result = json(mockMvc.perform(get("/api/quiz/attempts/" + attemptId + "/result").with(asStudent())));
        JsonNode shown = result.get("items").get(0).get("parts");
        assertThat(text(shown.get(1), "typedResult")).isEqualTo("-1"); // their own words stay
        assertThat(shown.findValues("resultCheck").stream().allMatch(JsonNode::isNull)).isTrue();
        assertThat(shown.findValues("expectedResult").stream().allMatch(JsonNode::isNull)).isTrue();
        assertThat(result.toString()).doesNotContain("0,5").doesNotContain("barem b");
        // The teacher still gets the check.
        assertThat(text(paperParts(attemptId).get(1), "resultCheck")).isEqualTo("RIGHT");
    }

    @Test
    void theExpectedResultCanBeCorrectedOnATestAlreadyTakenAndACopyKeepsIt() throws Exception {
        long attemptId = start().get("attemptId").asLong();
        type(attemptId, itemId, Map.of(parts.get(2), "0,25"), null, null).andExpect(status().isNoContent());
        photoAndHandIn(attemptId);
        assertThat(text(paperParts(attemptId).get(2), "resultCheck")).isEqualTo("WRONG");

        // The barem had a typo: c) is 1/4. Nothing is stored about the check, so the paper follows at once.
        JsonNode saved = json(send(put("/api/admin/quiz-items/" + itemId), problem("-1", "1/4")).andExpect(status().isOk()));
        assertThat(text(saved.get("parts").get(2), "finalAnswer")).isEqualTo("1/4");
        assertThat(text(paperParts(attemptId).get(2), "resultCheck")).isEqualTo("RIGHT");

        // No longer asking for b): what was typed there is not shown against nothing.
        send(put("/api/admin/quiz-items/" + itemId), problem(null, "1/4")).andExpect(status().isOk());
        assertThat(paperParts(attemptId).get(1).get("resultCheck").isNull()).isTrue();

        long copyId = json(send(post("/api/admin/quizzes/" + quizId + "/copy"), Map.of()).andExpect(status().isCreated()))
                .get("id").asLong();
        JsonNode copy = json(mockMvc.perform(get("/api/admin/quizzes/" + copyId).with(TEACHER))).get("items").get(0).get("parts");
        assertThat(text(copy.get(1), "finalAnswer")).isNull();
        assertThat(text(copy.get(2), "finalAnswer")).isEqualTo("1/4");
    }
}
