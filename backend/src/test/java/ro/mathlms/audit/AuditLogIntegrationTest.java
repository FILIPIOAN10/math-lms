package ro.mathlms.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.IllegalTransactionStateException;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAttempt;
import ro.mathlms.quiz.QuizAttemptRepository;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F1 on real Postgres, through the HTTP API: a change and its journal row commit together, the row names the teacher
 * and the student as they are called TODAY (so an erased account is anonymised in the journal too), and the filters
 * and keyset paging work. The database is shared with other test classes, so every read filters by this test's student.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuditLogIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AuditLog auditLog;
    @Autowired private UserRepository userRepository;
    @Autowired private SchoolClassRepository schoolClassRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizAttemptRepository attemptRepository;

    private User teacher;
    private User student;
    private QuizAttempt attempt;
    private QuizItem open;
    private String stamp;

    @BeforeEach
    void setUp() {
        stamp = String.valueOf(System.nanoTime());
        teacher = userRepository.save(new User("prof-" + stamp + "@scoala.ro", "Prof. Ionescu", Role.ADMIN));
        student = userRepository.save(new User("elev-" + stamp + "@scoala.ro", "Ana Pop", Role.STUDENT));
        Quiz quiz = quizRepository.save(new Quiz("Teză " + stamp, null));
        itemRepository.save(new QuizItem(quiz, 0, QuizItemType.OPEN, "Calculează", 5, null));
        open = itemRepository.save(new QuizItem(quiz, 1, QuizItemType.OPEN, "Demonstrează", 5, null));
        QuizAttempt started = new QuizAttempt(quiz, student);
        started.submit();
        attempt = attemptRepository.save(started);
    }

    private RequestPostProcessor asTeacher() {
        return user(teacher.getEmail()).roles("ADMIN");
    }

    private ResultActions grade(int points) throws Exception {
        return mockMvc.perform(put("/api/admin/quiz/attempts/" + attempt.getId() + "/responses/" + open.getId() + "/grade")
                .with(asTeacher()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"points\":" + points + "}"));
    }

    private JsonNode journal(String query) throws Exception {
        String body = mockMvc.perform(get("/api/admin/audit-log?studentId=" + student.getId() + query).with(asTeacher()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    @Test
    void aChangedGradeIsJournalledNewestFirstWithTheTeacherAndTheStudentByName() throws Exception {
        grade(3).andExpect(status().isNoContent());
        grade(5).andExpect(status().isNoContent());

        JsonNode entries = journal("").get("entries");
        assertThat(entries).hasSize(2);
        JsonNode latest = entries.get(0);
        assertThat(latest.get("action").asText()).isEqualTo("ANSWER_GRADED");
        assertThat(latest.get("category").asText()).isEqualTo("GRADING");
        assertThat(latest.get("label").asText()).isEqualTo("Teză " + stamp + " · întrebarea 2");
        assertThat(latest.get("details").asText()).isEqualTo("3 → 5 p din 5");
        assertThat(latest.get("actorName").asText()).isEqualTo("Prof. Ionescu");
        assertThat(latest.get("studentName").asText()).isEqualTo("Ana Pop");
        assertThat(latest.get("entityId").asLong()).isEqualTo(attempt.getId());
        assertThat(entries.get(1).get("details").asText()).isEqualTo("3 p din 5");
    }

    @Test
    void aRefusedChangeLeavesNoRow() throws Exception {
        grade(9).andExpect(status().isBadRequest()); // the question is worth 5

        assertThat(journal("").get("entries")).isEmpty();
    }

    @Test
    void anErasedStudentIsAnonymisedInTheJournalToo() throws Exception {
        grade(4).andExpect(status().isNoContent());
        User erased = userRepository.findById(student.getId()).orElseThrow();
        erased.erase("erased-" + stamp + "@erased.invalid", "Utilizator șters");
        userRepository.save(erased);

        assertThat(journal("").get("entries").get(0).get("studentName").asText()).isEqualTo("Utilizator șters");
    }

    @Test
    void theCategoryFilterAndTheKeysetPagingWork() throws Exception {
        SchoolClass a = schoolClassRepository.save(new SchoolClass("Clasa A " + stamp, null));
        SchoolClass b = schoolClassRepository.save(new SchoolClass("Clasa B " + stamp, null));
        for (SchoolClass c : new SchoolClass[] {a, b}) {
            mockMvc.perform(post("/api/admin/classes/" + c.getId() + "/enrollments").with(asTeacher()).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{\"studentId\":" + student.getId() + "}"))
                    .andExpect(status().isCreated());
        }
        grade(2).andExpect(status().isNoContent());

        assertThat(journal("&category=GRADING").get("entries")).hasSize(1);
        JsonNode first = journal("&category=PEOPLE&limit=1");
        assertThat(first.get("entries").get(0).get("label").asText()).isEqualTo("Clasa B " + stamp);
        long next = first.get("nextBefore").asLong();
        JsonNode second = journal("&category=PEOPLE&limit=1&before=" + next);
        assertThat(second.get("entries").get(0).get("label").asText()).isEqualTo("Clasa A " + stamp);
        assertThat(second.get("nextBefore").isNull()).isTrue();
    }

    @Test
    void outsideATransactionNothingCanBeRecorded() {
        assertThatThrownBy(() -> auditLog.record(AuditEvent.of(AuditAction.CLASS_CREATED, 1L, "x")))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void onlyTeachersReadTheJournalAndAnUnknownCategoryIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/admin/audit-log").with(user(student.getEmail()).roles("STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/audit-log?category=NIMIC").with(asTeacher()))
                .andExpect(status().isBadRequest());
    }
}
