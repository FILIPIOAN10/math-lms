package ro.mathlms.quiz;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

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
 * EN questions pointing at their chapter page (V34), on real Postgres over HTTP: by default the link follows the
 * question's place on the paper (SUBIECTUL I, question 1 → A1; a requirement of Subiectul III may have its own), the
 * teacher overrides it on one question or one requirement, and the student's result names the chapter of every
 * question - and says whether its page has anything on it yet.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class EnChapterLinksIntegrationTest {

    private static final List<SimpleGrantedAuthority> STUDENT =
            List.of(new SimpleGrantedAuthority("ROLE_STUDENT"), new SimpleGrantedAuthority("STATUS_ACTIVE"));
    private static final RequestPostProcessor TEACHER =
            user("prof@scoala.ro").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("STATUS_ACTIVE"));
    private static final String SUBJECT_I = "SUBIECTUL I\nÎncercuiește litera corespunzătoare răspunsului corect.";
    private static final String SUBJECT_III = "SUBIECTUL al III-lea\nScrie rezolvările complete.";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;

    private User student;
    private long quizId;
    /** In paper order: I.1, I.2, III.1 … III.6 (III.6 has requirements a) and b)). */
    private final List<Long> items = new ArrayList<>();
    private final Map<String, Long> chapters = new HashMap<>();

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Object body) throws Exception {
        return mockMvc.perform(request.with(TEACHER).with(csrf()).contentType("application/json")
                .content(objectMapper.writeValueAsString(body)));
    }

    private static Map<String, Object> grila(int position, String statement, Long enChapterId) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "SINGLE_CHOICE");
        item.put("position", position);
        item.put("points", 5);
        item.put("section", SUBJECT_I);
        item.put("statement", statement);
        item.put("options", List.of(Map.of("text", "1", "correct", true), Map.of("text", "17", "correct", false)));
        item.put("enChapterId", enChapterId);
        return item;
    }

    private static Map<String, Object> problem(int position, List<Map<String, Object>> parts) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "OPEN");
        item.put("position", position);
        item.put("points", 5);
        item.put("section", SUBJECT_III);
        item.put("statement", "Problema " + position);
        if (parts != null) {
            item.put("parts", parts);
        }
        return item;
    }

    private static Map<String, Object> part(String statement, int points, Long enChapterId) {
        Map<String, Object> part = new LinkedHashMap<>();
        part.put("statement", statement);
        part.put("points", points);
        part.put("enChapterId", enChapterId);
        return part;
    }

    private List<Map<String, Object>> cubeParts(Long aChapter) {
        return List.of(part("Arată că volumul cubului este 512.", 2, aChapter), part("Arată că FO ⊥ DE.", 3, null));
    }

    @BeforeEach
    void setUp() throws Exception {
        student = userRepository.save(new User("elev-" + System.nanoTime() + "@scoala.ro", "Ana", Role.STUDENT));
        for (JsonNode c : json(mockMvc.perform(get("/api/en-chapters").with(TEACHER)))) {
            chapters.put(c.get("code").asString(), c.get("id").asLong());
        }
        quizId = json(send(post("/api/admin/quizzes"), Map.of("title", "Simulare EN " + System.nanoTime()))
                .andExpect(status().isCreated())).get("id").asLong();
        items.add(json(send(post("/api/admin/quizzes/" + quizId + "/items"), grila(0, "$36 : 4 - 4 \\cdot 2$", null))
                .andExpect(status().isCreated())).get("id").asLong());
        items.add(json(send(post("/api/admin/quizzes/" + quizId + "/items"), grila(1, "$\\frac{a}{4} = \\frac{5}{2}$", null))
                .andExpect(status().isCreated())).get("id").asLong());
        for (int n = 1; n <= 6; n++) {
            items.add(json(send(post("/api/admin/quizzes/" + quizId + "/items"), problem(1 + n, n == 6 ? cubeParts(null) : null))
                    .andExpect(status().isCreated())).get("id").asLong());
        }
    }

    private JsonNode detail() throws Exception {
        return json(mockMvc.perform(get("/api/admin/quizzes/" + quizId).with(TEACHER)).andExpect(status().isOk()));
    }

    private long id(JsonNode node) {
        return node.isNull() ? -1 : node.asLong();
    }

    @Test
    void byDefaultTheLinkFollowsThePlaceOnThePaper() throws Exception {
        JsonNode list = detail().get("items");

        assertThat(id(list.get(0).get("enChapterAuto"))).isEqualTo(chapters.get("A1"));
        assertThat(list.get(0).get("enChapterId").isNull()).isTrue();
        assertThat(id(list.get(1).get("enChapterAuto"))).isEqualTo(chapters.get("A2"));
        assertThat(id(list.get(2).get("enChapterAuto"))).isEqualTo(chapters.get("A9"));  // III.1
        assertThat(id(list.get(4).get("enChapterAuto"))).isEqualTo(chapters.get("A11")); // III.3
        JsonNode cube = list.get(7);                                                      // III.6
        assertThat(id(cube.get("enChapterAuto"))).isEqualTo(chapters.get("G9"));
        assertThat(id(cube.get("parts").get(0).get("enChapterAuto"))).isEqualTo(chapters.get("G8"));
        assertThat(id(cube.get("parts").get(1).get("enChapterAuto"))).isEqualTo(chapters.get("G9"));
    }

    @Test
    void theTeacherOverridesOneQuestionAndOneRequirement() throws Exception {
        send(put("/api/admin/quiz-items/" + items.get(1)), grila(1, "$\\frac{a}{4} = \\frac{5}{2}$", chapters.get("A3")))
                .andExpect(status().isOk());
        send(put("/api/admin/quiz-items/" + items.get(7)), problem(7, cubeParts(chapters.get("G4"))))
                .andExpect(status().isOk());

        JsonNode list = detail().get("items");
        assertThat(id(list.get(1).get("enChapterId"))).isEqualTo(chapters.get("A3"));
        assertThat(id(list.get(1).get("enChapterAuto"))).isEqualTo(chapters.get("A2")); // still shown, for "Automat"
        assertThat(id(list.get(7).get("parts").get(0).get("enChapterId"))).isEqualTo(chapters.get("G4"));
        assertThat(list.get(7).get("parts").get(1).get("enChapterId").isNull()).isTrue();

        // A copy (variant B) keeps the teacher's choices.
        long copy = json(send(post("/api/admin/quizzes/" + quizId + "/copy"), Map.of()).andExpect(status().isCreated()))
                .get("id").asLong();
        JsonNode copied = json(mockMvc.perform(get("/api/admin/quizzes/" + copy).with(TEACHER))).get("items");
        assertThat(id(copied.get(1).get("enChapterId"))).isEqualTo(chapters.get("A3"));
        assertThat(id(copied.get(7).get("parts").get(0).get("enChapterId"))).isEqualTo(chapters.get("G4"));
    }

    @Test
    void aChapterThatDoesNotExistIsRefused() throws Exception {
        send(put("/api/admin/quiz-items/" + items.get(0)), grila(0, "x", 999_999_999L)).andExpect(status().isBadRequest());
    }

    @Test
    void theResultNamesTheChapterOfEveryQuestionAndWhetherItsPageIsReady() throws Exception {
        // A page with a worked example on it, chosen for I.2.
        String code = "L" + String.valueOf(System.nanoTime()).substring(10);
        long ready = json(send(post("/api/admin/en-chapters"), Map.of("code", code, "title", "Proporții", "area", "ALGEBRA"))
                .andExpect(status().isCreated())).get("id").asLong();
        send(post("/api/admin/en-chapters/" + ready + "/examples"), Map.of("statement", "$\\frac{x}{3} = \\frac{4}{6}$", "solution", "$x = 2$"))
                .andExpect(status().isCreated());
        send(put("/api/admin/quiz-items/" + items.get(1)), grila(1, "$\\frac{a}{4} = \\frac{5}{2}$", ready)).andExpect(status().isOk());
        send(post("/api/admin/quizzes/" + quizId + "/publish"), Map.of()).andExpect(status().isOk());

        JsonNode started = json(mockMvc.perform(post("/api/quiz/quizzes/" + quizId + "/attempts")
                .with(user(student.getEmail()).authorities(STUDENT)).with(csrf())).andExpect(status().isOk()));
        long attemptId = started.get("attemptId").asLong();
        assertThat(started.toString()).doesNotContain("enChapter"); // nothing to give away while writing
        JsonNode first = started.get("quiz").get("items").get(0);
        long wrong = -1;
        for (JsonNode option : first.get("options")) {
            if (option.get("text").asString().equals("17")) {
                wrong = option.get("id").asLong();
            }
        }
        mockMvc.perform(put("/api/quiz/attempts/" + attemptId + "/responses/" + items.get(0))
                        .with(user(student.getEmail()).authorities(STUDENT)).with(csrf())
                        .contentType("application/json").content("{\"optionId\":" + wrong + "}"))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(post("/api/quiz/attempts/" + attemptId + "/submit")
                .with(user(student.getEmail()).authorities(STUDENT)).with(csrf())).andExpect(status().isOk());

        JsonNode result = json(mockMvc.perform(get("/api/quiz/attempts/" + attemptId + "/result")
                .with(user(student.getEmail()).authorities(STUDENT))).andExpect(status().isOk())).get("items");
        assertThat(result.get(0).get("enChapter").get("code").asString()).isEqualTo("A1");
        assertThat(result.get(0).get("enChapter").get("title").asString()).isEqualTo("Ordinea efectuării operațiilor");
        assertThat(result.get(1).get("enChapter").get("code").asString()).isEqualTo(code);
        assertThat(result.get(1).get("enChapter").get("ready").asBoolean()).isTrue();
        JsonNode cubeParts = result.get(7).get("parts");
        assertThat(cubeParts.get(0).get("enChapter").get("code").asString()).isEqualTo("G8");
        assertThat(cubeParts.get(1).get("enChapter").get("code").asString()).isEqualTo("G9");
    }
}
