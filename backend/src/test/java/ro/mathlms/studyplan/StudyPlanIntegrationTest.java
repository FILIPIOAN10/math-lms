package ro.mathlms.studyplan;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * „Ținta la EN" and the study plan on real Postgres over HTTP: the admin sets the exam date, the student picks a target
 * and gets a plan from their own answers, the teacher pins a chapter and sees the class, the parent only reads.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class StudyPlanIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Bucharest");
    private static final RequestPostProcessor ADMIN =
            user("prof@scoala.ro").authorities(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("STATUS_ACTIVE"));

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private SchoolClassRepository schoolClassRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private JdbcTemplate jdbc;

    private User student;
    private User parent;
    private long classId;
    private LocalDate examDate;
    private final Map<String, Long> chapters = new HashMap<>();

    private static RequestPostProcessor as(User u) {
        return user(u.getEmail()).authorities(new SimpleGrantedAuthority("ROLE_" + u.getRole()),
                new SimpleGrantedAuthority("STATUS_ACTIVE"));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, RequestPostProcessor who, Object body) throws Exception {
        return mockMvc.perform(request.with(who).with(csrf()).contentType("application/json")
                .content(objectMapper.writeValueAsString(body)));
    }

    private static Map<String, Object> examDateBody(LocalDate date) {
        Map<String, Object> body = new HashMap<>();
        body.put("examDate", date == null ? null : date.toString());
        return body;
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
        for (JsonNode c : json(mockMvc.perform(get("/api/en-chapters").with(ADMIN)))) {
            chapters.put(c.get("code").asString(), c.get("id").asLong());
        }
        examDate = LocalDate.now(ZONE).plusDays(94);
        send(put("/api/admin/en-exam-date"), ADMIN, examDateBody(examDate)).andExpect(status().isOk());
    }

    /** A published test of 20 grile, each linked to a chapter; the student gets the ones in {@code right} right. */
    private void answerTwentyGrile(List<String> codes, List<String> right) throws Exception {
        long quizId = json(send(post("/api/admin/quizzes"), ADMIN,
                Map.of("title", "Exersare " + System.nanoTime(), "schoolClassId", classId))
                .andExpect(status().isCreated())).get("id").asLong();
        Map<Long, String> codeOfItem = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            String code = codes.get(i % codes.size());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", "SINGLE_CHOICE");
            item.put("position", i);
            item.put("points", 5);
            item.put("statement", "Întrebarea " + i);
            item.put("options", List.of(Map.of("position", 0, "text", "bun", "correct", true),
                    Map.of("position", 1, "text", "greșit", "correct", false)));
            item.put("enChapterId", chapters.get(code));
            long itemId = json(send(post("/api/admin/quizzes/" + quizId + "/items"), ADMIN, item)
                    .andExpect(status().isCreated())).get("id").asLong();
            codeOfItem.put(itemId, code);
        }
        send(post("/api/admin/quizzes/" + quizId + "/publish"), ADMIN, Map.of()).andExpect(status().isOk());

        JsonNode started = json(mockMvc.perform(post("/api/quiz/quizzes/" + quizId + "/attempts")
                .with(as(student)).with(csrf())).andExpect(status().isOk()));
        long attempt = started.get("attemptId").asLong();
        for (JsonNode item : started.get("quiz").get("items")) {
            String wanted = right.contains(codeOfItem.get(item.get("id").asLong())) ? "bun" : "greșit";
            for (JsonNode option : item.get("options")) {
                if (option.get("text").asString().equals(wanted)) {
                    send(put("/api/quiz/attempts/" + attempt + "/responses/" + item.get("id").asLong()), as(student),
                            Map.of("optionId", option.get("id").asLong())).andExpect(status().is2xxSuccessful());
                }
            }
        }
        mockMvc.perform(post("/api/quiz/attempts/" + attempt + "/submit").with(as(student)).with(csrf()))
                .andExpect(status().isOk());
    }

    private static List<Long> plannedChapters(JsonNode plan) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode week : plan.get("weeks")) {
            for (JsonNode item : week.get("items")) {
                if (!item.get("chapterId").isNull() && !ids.contains(item.get("chapterId").asLong())) {
                    ids.add(item.get("chapterId").asLong());
                }
            }
        }
        return ids;
    }

    private Map<String, Object> lastJournalRow(String action) {
        return jdbc.queryForMap("select label, details, student_id from admin_audit_log where action = ? order by id desc limit 1",
                action);
    }

    // --- the exam date ---

    @Test
    void theAdminSetsTheExamDateOnceForEveryoneAndTheJournalSaysSo() throws Exception {
        JsonNode read = json(mockMvc.perform(get("/api/admin/en-exam-date").with(ADMIN)).andExpect(status().isOk()));
        assertThat(read.get("examDate").asString()).isEqualTo(examDate.toString());

        LocalDate later = examDate.plusDays(3);
        send(put("/api/admin/en-exam-date"), ADMIN, examDateBody(later)).andExpect(status().isOk());

        Map<String, Object> row = lastJournalRow("EN_EXAM_DATE_SET");
        assertThat(row.get("label")).isEqualTo("Data EN");
        assertThat((String) row.get("details")).endsWith(String.format("%02d.%02d.%d",
                later.getDayOfMonth(), later.getMonthValue(), later.getYear()));
        JsonNode plan = json(mockMvc.perform(get("/api/quiz/study-plan").with(as(student))).andExpect(status().isOk()));
        assertThat(plan.get("examDate").asString()).isEqualTo(later.toString());
        assertThat(plan.get("daysLeft").asInt()).isEqualTo(97);
    }

    @Test
    void anExamDateInThePastIsRefused() throws Exception {
        String message = send(put("/api/admin/en-exam-date"), ADMIN, examDateBody(LocalDate.now(ZONE).minusDays(1)))
                .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
        assertThat(message).contains("trecut");
    }

    @Test
    void withoutAnExamDateThereIsNoPlan() throws Exception {
        send(put("/api/admin/en-exam-date"), ADMIN, examDateBody(null)).andExpect(status().isOk());

        JsonNode plan = json(mockMvc.perform(get("/api/quiz/study-plan").with(as(student))).andExpect(status().isOk()));
        assertThat(plan.get("mode").asString()).isEqualTo("NO_EXAM_DATE");
        assertThat(plan.get("weeks")).isEmpty();
    }

    // --- the student's target and plan ---

    @Test
    void aStudentWithoutATargetIsAskedForOne() throws Exception {
        JsonNode plan = json(mockMvc.perform(get("/api/quiz/study-plan").with(as(student))).andExpect(status().isOk()));

        assertThat(plan.get("mode").asString()).isEqualTo("NO_TARGET");
        assertThat(plan.get("target").isNull()).isTrue();
        assertThat(plan.get("daysLeft").asInt()).isEqualTo(94);
        JsonNode goal = json(mockMvc.perform(get("/api/quiz/en-goal").with(as(student))).andExpect(status().isOk()));
        assertThat(goal.get("target").isNull()).isTrue();
        assertThat(goal.get("daysLeft").asInt()).isEqualTo(94);
    }

    @Test
    void aTargetIsAGradeFromOneToTenWithAtMostTwoDecimals() throws Exception {
        for (String bad : List.of("10.5", "0.99", "8.555")) {
            String message = send(put("/api/quiz/en-goal"), as(student), Map.of("targetGrade", new java.math.BigDecimal(bad)))
                    .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
            assertThat(message).contains("de la 1 la 10");
        }
    }

    @Test
    void beforeTwentyAnswersTheTargetIsKeptAndOnlyTheFirstWeekIsPlanned() throws Exception {
        JsonNode plan = json(send(put("/api/quiz/en-goal"), as(student), Map.of("targetGrade", 9))
                .andExpect(status().isOk()));

        assertThat(plan.get("mode").asString()).isEqualTo("NEEDS_ANSWERS");
        assertThat(plan.get("target").asDouble()).isEqualTo(9.0);
        assertThat(plan.get("weeks")).hasSize(1);
        assertThat(plan.get("history")).isEmpty(); // no estimate yet, nothing to draw
        assertThat(jdbc.queryForObject("select baseline_grade is null from student_en_goals where student_id = ?",
                Boolean.class, student.getId())).isTrue();
    }

    @Test
    void theStudentsPlanStartsWithTheChapterThatWouldRaiseTheirGradeMost() throws Exception {
        answerTwentyGrile(List.of("A1", "G5"), List.of("A1")); // A1 all right, G5 all wrong

        JsonNode plan = json(send(put("/api/quiz/en-goal"), as(student), Map.of("targetGrade", 10))
                .andExpect(status().isOk()));
        JsonNode prediction = json(mockMvc.perform(get("/api/quiz/en-prediction").with(as(student))));

        assertThat(plan.get("mode").asString()).isEqualTo("CATCH_UP");
        assertThat(plan.get("grade").asDouble()).isEqualTo(prediction.get("grade").asDouble());
        long top = prediction.get("topGains").get(0).get("chapterId").asLong();
        assertThat(plannedChapters(plan).getFirst()).isEqualTo(top);
        assertThat(plannedChapters(plan)).doesNotContain(chapters.get("A1")); // mastered: nothing to gain
        // Never met or all wrong: the lesson comes first.
        assertThat(plan.get("weeks").get(0).get("items").get(0).get("type").asString()).isEqualTo("LESSON");
        assertThat(plan.get("practiceQuestions").asInt()).isEqualTo(10);

        // Setting the target keeps today's estimate as the start of the line and of the chart.
        assertThat(plan.get("history")).hasSize(1);
        assertThat(plan.get("history").get(0).get("day").asString()).isEqualTo(LocalDate.now(ZONE).toString());
        assertThat(plan.get("history").get(0).get("grade").asDouble()).isEqualTo(prediction.get("grade").asDouble());
        assertThat(plan.get("behind").asBoolean()).isFalse(); // just set: two weeks of grace
        assertThat(jdbc.queryForObject("select baseline_grade from student_en_goals where student_id = ?",
                Double.class, student.getId())).isEqualTo(prediction.get("grade").asDouble());

        JsonNode goal = json(mockMvc.perform(get("/api/quiz/en-goal").with(as(student))));
        assertThat(goal.get("target").asDouble()).isEqualTo(10.0);
    }

    // --- the teacher ---

    @Test
    void theTeacherPinsAChapterIntoTheStudentsPlanAndTakesItOut() throws Exception {
        answerTwentyGrile(List.of("A1", "G5"), List.of("A1"));
        send(put("/api/quiz/en-goal"), as(student), Map.of("targetGrade", 10)).andExpect(status().isOk());
        long a1 = chapters.get("A1");

        JsonNode pinned = json(mockMvc.perform(put("/api/admin/students/" + student.getId() + "/study-plan/pins/" + a1)
                .with(ADMIN).with(csrf())).andExpect(status().isOk()));

        assertThat(plannedChapters(pinned).getFirst()).isEqualTo(a1); // first, though nothing is left to gain there
        assertThat(pinned.get("pins").findValues("chapterId").stream().map(JsonNode::asLong)).containsExactly(a1);
        Map<String, Object> row = lastJournalRow("STUDY_PLAN_PINNED");
        assertThat(((Number) row.get("student_id")).longValue()).isEqualTo(student.getId());
        assertThat((String) row.get("label")).startsWith("A1");
        // The student sees the same plan.
        JsonNode own = json(mockMvc.perform(get("/api/quiz/study-plan").with(as(student))));
        assertThat(plannedChapters(own).getFirst()).isEqualTo(a1);

        JsonNode unpinned = json(mockMvc.perform(delete("/api/admin/students/" + student.getId() + "/study-plan/pins/" + a1)
                .with(ADMIN).with(csrf())).andExpect(status().isOk()));
        assertThat(plannedChapters(unpinned)).doesNotContain(a1);
        assertThat(unpinned.get("pins")).isEmpty();
        assertThat(((Number) lastJournalRow("STUDY_PLAN_UNPINNED").get("student_id")).longValue()).isEqualTo(student.getId());
    }

    @Test
    void pinningAChapterThatDoesNotExistIsANotFound() throws Exception {
        mockMvc.perform(put("/api/admin/students/" + student.getId() + "/study-plan/pins/987654321")
                .with(ADMIN).with(csrf())).andExpect(status().isNotFound());
    }

    @Test
    void theTeacherSeesEachStudentsEstimateAndTargetForTheClass() throws Exception {
        answerTwentyGrile(List.of("A1", "G5"), List.of("A1"));
        JsonNode plan = json(send(put("/api/quiz/en-goal"), as(student), Map.of("targetGrade", 9.5)));

        JsonNode rows = json(mockMvc.perform(get("/api/admin/classes/" + classId + "/en-goals").with(ADMIN))
                .andExpect(status().isOk()));

        assertThat(rows).hasSize(1);
        JsonNode row = rows.get(0);
        assertThat(row.get("studentId").asLong()).isEqualTo(student.getId());
        assertThat(row.get("fullName").asString()).isEqualTo("Ana");
        assertThat(row.get("target").asDouble()).isEqualTo(9.5);
        assertThat(row.get("grade").asDouble()).isEqualTo(plan.get("grade").asDouble());
        assertThat(row.get("behind").asBoolean()).isFalse();
        JsonNode one = json(mockMvc.perform(get("/api/admin/students/" + student.getId() + "/study-plan").with(ADMIN))
                .andExpect(status().isOk()));
        assertThat(one.get("target").asDouble()).isEqualTo(9.5);
    }

    // --- the parent ---

    @Test
    void theParentReadsTheirChildsPlanButNobodyElsesAndCannotChangeIt() throws Exception {
        send(put("/api/quiz/en-goal"), as(student), Map.of("targetGrade", 8.5)).andExpect(status().isOk());

        JsonNode plan = json(mockMvc.perform(get("/api/parent/children/" + student.getId() + "/study-plan").with(as(parent)))
                .andExpect(status().isOk()));
        assertThat(plan.get("target").asDouble()).isEqualTo(8.5);
        JsonNode goal = json(mockMvc.perform(get("/api/parent/children/" + student.getId() + "/en-goal").with(as(parent)))
                .andExpect(status().isOk()));
        assertThat(goal.get("target").asDouble()).isEqualTo(8.5);

        User stranger = userRepository.save(new User("alt-" + System.nanoTime() + "@scoala.ro", "Alt", Role.PARENT));
        mockMvc.perform(get("/api/parent/children/" + student.getId() + "/study-plan").with(as(stranger)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/parent/children/" + student.getId() + "/en-goal").with(as(stranger)))
                .andExpect(status().isForbidden());
        send(put("/api/quiz/en-goal"), as(parent), Map.of("targetGrade", 10)).andExpect(status().isForbidden());
    }
}
