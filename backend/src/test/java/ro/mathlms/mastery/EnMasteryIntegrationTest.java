package ro.mathlms.mastery;

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
import ro.mathlms.content.Enrollment;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.SchoolClassRepository;
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
 * Stage 4 of the EN chapters, on real Postgres over HTTP: a student's level in each EN chapter, from the latest answer
 * to every question - and to every requirement a), b) of a problem, each counting toward its own chapter. The student
 * and their parent see it, the teacher sees it per student and as a class grid of the chapters the class has met.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class EnMasteryIntegrationTest {

    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");
    private static final RequestPostProcessor TEACHER =
            user("prof@scoala.ro").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("STATUS_ACTIVE"));

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private SchoolClassRepository schoolClassRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;

    private User student;
    private User parent;
    private long classId;
    private long quizId;
    private long grilaRight;
    private long grilaWrong;
    private long problem;
    private final Map<String, Long> chapters = new HashMap<>();

    private static RequestPostProcessor as(User u) {
        return user(u.getEmail()).authorities(new SimpleGrantedAuthority("ROLE_" + u.getRole()),
                new SimpleGrantedAuthority("STATUS_ACTIVE"));
    }

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
        item.put("section", "SUBIECTUL I");
        item.put("statement", statement);
        item.put("options", List.of(Map.of("position", 0, "text", "bun", "correct", true),
                Map.of("position", 1, "text", "greșit", "correct", false)));
        item.put("enChapterId", enChapterId);
        return item;
    }

    @BeforeEach
    void setUp() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        parent = userRepository.save(new User("parinte-" + stamp + "@scoala.ro", "Maria", Role.PARENT));
        User s = new User("elev-" + stamp + "@scoala.ro", "Ana", Role.STUDENT);
        s.linkParent(parent);
        student = userRepository.save(s);
        SchoolClass clasa = schoolClassRepository.save(new SchoolClass("Clasa a 8-a " + stamp, null));
        classId = clasa.getId();
        enrollmentRepository.save(new Enrollment(student, clasa));
        for (JsonNode c : json(mockMvc.perform(get("/api/en-chapters").with(TEACHER)))) {
            chapters.put(c.get("code").asString(), c.get("id").asLong());
        }

        quizId = json(send(post("/api/admin/quizzes"), Map.of("title", "Simulare " + stamp, "schoolClassId", classId))
                .andExpect(status().isCreated())).get("id").asLong();
        grilaRight = json(send(post("/api/admin/quizzes/" + quizId + "/items"), grila(0, "$36 : 4 - 4 \\cdot 2$", null))
                .andExpect(status().isCreated())).get("id").asLong();                              // I.1 → A1 (automatic)
        grilaWrong = json(send(post("/api/admin/quizzes/" + quizId + "/items"), grila(1, "$40\\%$ din 250", chapters.get("A3")))
                .andExpect(status().isCreated())).get("id").asLong();                              // I.2 → A3 (chosen)
        Map<String, Object> open = new LinkedHashMap<>();
        open.put("type", "OPEN");
        open.put("position", 2);
        open.put("points", 0);
        open.put("section", "SUBIECTUL al III-lea");
        open.put("statement", "Vârstele lui Bogdan și Tudor.");
        open.put("parts", List.of(Map.of("statement", "Verifică.", "points", 2),                       // a) → A9 (automatic III.1)
                Map.of("statement", "Distanța la plan.", "points", 3, "enChapterId", chapters.get("G9")))); // b) → G9 (chosen)
        problem = json(send(post("/api/admin/quizzes/" + quizId + "/items"), open).andExpect(status().isCreated()))
                .get("id").asLong();
        send(post("/api/admin/quizzes/" + quizId + "/publish"), Map.of()).andExpect(status().isOk());
    }

    /** The student gets I.1 right and I.2 wrong, hands in the problem; the teacher gives a) 2/2 and b) 1/3. */
    private void takeAndGrade() throws Exception {
        JsonNode started = json(mockMvc.perform(post("/api/quiz/quizzes/" + quizId + "/attempts").with(as(student)).with(csrf()))
                .andExpect(status().isOk()));
        long attempt = started.get("attemptId").asLong();
        for (JsonNode item : started.get("quiz").get("items")) {
            String wanted = item.get("id").asLong() == grilaRight ? "bun" : "greșit";
            for (JsonNode option : item.get("options")) {
                if (option.get("text").asString().equals(wanted)) {
                    mockMvc.perform(put("/api/quiz/attempts/" + attempt + "/responses/" + item.get("id").asLong())
                                    .with(as(student)).with(csrf()).contentType("application/json")
                                    .content("{\"optionId\":" + option.get("id").asLong() + "}"))
                            .andExpect(status().is2xxSuccessful());
                }
            }
        }
        mockMvc.perform(multipart("/api/quiz/attempts/" + attempt + "/responses/" + problem + "/photo")
                .file(new MockMultipartFile("file", "p.png", "image/png", PNG)).with(as(student)).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/quiz/attempts/" + attempt + "/submit").with(as(student)).with(csrf()))
                .andExpect(status().isOk());

        JsonNode paper = json(mockMvc.perform(get("/api/admin/quiz/attempts/" + attempt).with(TEACHER)));
        JsonNode parts = null;
        for (JsonNode item : paper.get("items")) {
            if (item.get("itemId").asLong() == problem) {
                parts = item.get("parts");
            }
        }
        send(put("/api/admin/quiz/attempts/" + attempt + "/responses/" + problem + "/grade"), Map.of("parts", List.of(
                Map.of("partId", parts.get(0).get("id").asLong(), "points", 2),
                Map.of("partId", parts.get(1).get("id").asLong(), "points", 1)))).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/admin/quiz/attempts/" + attempt + "/mark-graded").with(TEACHER).with(csrf()))
                .andExpect(status().isNoContent());
    }

    private static Map<String, JsonNode> byCode(JsonNode rows) {
        Map<String, JsonNode> out = new HashMap<>();
        rows.forEach(r -> out.put(r.get("code").asString(), r));
        return out;
    }

    @Test
    void theStudentSeesTheirLevelInEachEnChapterTheyMet() throws Exception {
        JsonNode before = json(mockMvc.perform(get("/api/quiz/en-mastery").with(as(student))).andExpect(status().isOk()));
        assertThat(byCode(before).get("A1").get("percent").isNull()).isTrue(); // every chapter listed, none met yet

        takeAndGrade();

        Map<String, JsonNode> mine = byCode(json(mockMvc.perform(get("/api/quiz/en-mastery").with(as(student)))));
        assertThat(mine.get("A1").get("percent").asInt()).isEqualTo(100);
        assertThat(mine.get("A1").get("questions").asInt()).isEqualTo(1);
        assertThat(mine.get("A3").get("percent").asInt()).isZero();
        assertThat(mine.get("A9").get("percent").asInt()).isEqualTo(100); // a) 2 / 2
        assertThat(mine.get("G9").get("earned").asInt()).isEqualTo(1);    // b) 1 / 3
        assertThat(mine.get("G9").get("possible").asInt()).isEqualTo(3);
        assertThat(mine.get("G9").get("percent").asInt()).isEqualTo(33);
        assertThat(mine.get("G5").get("percent").isNull()).isTrue();
        assertThat(mine.get("A1").get("title").asString()).isEqualTo("Ordinea efectuării operațiilor");
        assertThat(mine.get("A1").has("ready")).isTrue();

        // The parent sees the same; the teacher too, on the student's page.
        Map<String, JsonNode> parents = byCode(json(mockMvc.perform(
                get("/api/parent/children/" + student.getId() + "/en-mastery").with(as(parent))).andExpect(status().isOk())));
        assertThat(parents.get("G9").get("percent").asInt()).isEqualTo(33);
        Map<String, JsonNode> teachers = byCode(json(mockMvc.perform(
                get("/api/admin/students/" + student.getId() + "/en-mastery").with(TEACHER)).andExpect(status().isOk())));
        assertThat(teachers.get("A3").get("percent").asInt()).isZero();
    }

    @Test
    void theTeacherSeesTheClassAsAGridOfTheChaptersItMet() throws Exception {
        takeAndGrade();

        JsonNode grid = json(mockMvc.perform(get("/api/admin/classes/" + classId + "/en-mastery").with(TEACHER))
                .andExpect(status().isOk()));

        List<String> columns = grid.get("chapters").findValuesAsString("code");
        assertThat(columns).containsExactly("A1", "A3", "A9", "G9"); // only what the class met, in list order
        JsonNode row = grid.get("students").get(0);
        assertThat(row.get("fullName").asString()).isEqualTo("Ana");
        assertThat(row.get("cells").findValues("percent").stream().map(JsonNode::asInt)).containsExactly(100, 0, 100, 33);
    }

    @Test
    void nobodyElseSeesAStudentsMap() throws Exception {
        User stranger = userRepository.save(new User("alt-" + System.nanoTime() + "@scoala.ro", "Alt", Role.PARENT));
        mockMvc.perform(get("/api/parent/children/" + student.getId() + "/en-mastery").with(as(stranger)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/classes/" + classId + "/en-mastery").with(as(student)))
                .andExpect(status().isForbidden());
    }
}
