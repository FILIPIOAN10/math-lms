package ro.mathlms.student;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEntryRepository;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAttemptService;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.quiz.QuizOption;
import ro.mathlms.quiz.QuizOptionRepository;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.quiz.StudentQuizDtos.QuizPreviewDto;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Extra time on timed tests for one student (CES), on real Postgres: the teacher sets it on the student's page, the
 * journal keeps it, and the next test the student starts has the longer clock, which the screen before it already says.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"app.quiz.expiry-job.initial-delay-ms=3600000", "app.quiz.expiry-job.interval-ms=3600000"})
@AutoConfigureMockMvc
class ExtraTimeIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizOptionRepository optionRepository;
    @Autowired private QuizAttemptService attemptService;
    @Autowired private AuditEntryRepository auditRepository;

    private User ana;
    private User maria;
    private Quiz simulare;

    @BeforeEach
    void setUp() {
        String stamp = String.valueOf(System.nanoTime());
        ana = userRepository.save(new User("ana." + stamp + "@scoala.ro", "Ana Pop", Role.STUDENT));
        maria = userRepository.save(new User("maria." + stamp + "@scoala.ro", "Maria Pop", Role.PARENT));
        Quiz q = new Quiz("Simulare EN " + stamp, null);
        q.changeTimeLimit(120);
        q.publish();
        simulare = quizRepository.save(q);
        QuizItem item = itemRepository.save(new QuizItem(simulare, 1, QuizItemType.SINGLE_CHOICE, "g", 5, null));
        optionRepository.save(new QuizOption(item, 0, "bine", true));
    }

    private String body(int percent) {
        return "{\"percent\":" + percent + "}";
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void theTeacherSetsItAndTheJournalKeepsIt() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + ana.getId() + "/extra-time").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(50)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/admin/students/" + ana.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extraTimePercent").value(50));
        assertThat(auditRepository.findAll())
                .filteredOn(e -> e.getAction() == AuditAction.EXTRA_TIME_CHANGED && ana.getId().equals(e.getEntityId()))
                .singleElement()
                .satisfies(e -> assertThat(e.getDetails()).isEqualTo("fără → +50 %"));

        // The same value again is no change: no second journal row.
        mockMvc.perform(put("/api/admin/users/" + ana.getId() + "/extra-time").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(50)))
                .andExpect(status().isNoContent());
        assertThat(auditRepository.findAll())
                .filteredOn(e -> e.getAction() == AuditAction.EXTRA_TIME_CHANGED && ana.getId().equals(e.getEntityId()))
                .hasSize(1);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void refusesAStepThatIsNotAllowedAndAnAccountThatIsNotAStudent() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + ana.getId() + "/extra-time").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(30)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/users/" + maria.getId() + "/extra-time").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(50)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/admin/users/" + ana.getId() + "/extra-time").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findById(ana.getId()).orElseThrow().getExtraTimePercent()).isZero();
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void aStudentCannotGiveThemselvesMoreTime() throws Exception {
        mockMvc.perform(put("/api/admin/users/" + ana.getId() + "/extra-time").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(100)))
                .andExpect(status().isForbidden());
    }

    @Test
    void theNextTimedTestHasTheLongerClockAndSaysSo() {
        ana.changeExtraTime(50);
        userRepository.save(ana);

        QuizPreviewDto preview = attemptService.getPreview(simulare.getId(), ana.getEmail());
        assertThat(preview.timeLimitMinutes()).isEqualTo(180);
        assertThat(preview.extraTimePercent()).isEqualTo(50);

        StartedAttemptDto started = attemptService.startAttempt(simulare.getId(), ana.getEmail());
        assertThat(started.quiz().timeLimitMinutes()).isEqualTo(180);
        assertThat(Duration.between(started.serverNow(), started.deadlineAt()))
                .isBetween(Duration.ofMinutes(180).minusSeconds(10), Duration.ofMinutes(180));
    }

    @Test
    void withoutExtraTimeNothingChanges() {
        QuizPreviewDto preview = attemptService.getPreview(simulare.getId(), ana.getEmail());
        assertThat(preview.timeLimitMinutes()).isEqualTo(120);
        assertThat(preview.extraTimePercent()).isZero();
    }
}
