package ro.mathlms.quiz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * „Șterge și notele": a test students already handed in can be deleted from the menu, but only when the teacher says so
 * twice - a plain delete is still refused and tells how many attempts would go (header), and only {@code withResults}
 * deletes the test with every attempt, answer and request on it. Against the real schema, so no foreign key is left
 * pointing at a deleted row.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"app.quiz.expiry-job.initial-delay-ms=3600000", "app.quiz.expiry-job.interval-ms=3600000"})
@AutoConfigureMockMvc
class DeleteQuizWithResultsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizOptionRepository optionRepository;
    @Autowired private QuizAttemptRepository attemptRepository;
    @Autowired private ItemResponseRepository responseRepository;
    @Autowired private QuizAttemptService attemptService;
    @Autowired private JdbcTemplate jdbc;

    private User ana;
    private User ion;
    private User prof;

    @BeforeEach
    void setUp() {
        String stamp = String.valueOf(System.nanoTime());
        ana = userRepository.save(new User("ana." + stamp + "@scoala.ro", "Ana", Role.STUDENT));
        ion = userRepository.save(new User("ion." + stamp + "@scoala.ro", "Ion", Role.STUDENT));
        prof = userRepository.save(new User("prof." + stamp + "@scoala.ro", "Prof", Role.TEACHER));
    }

    private RequestPostProcessor as(User teacher) {
        return user(teacher.getEmail()).roles("TEACHER");
    }

    private Quiz publishedTest() {
        Quiz q = new Quiz("Teza " + System.nanoTime(), null);
        q.ownedBy(prof.getId());
        q.publish();
        return quizRepository.save(q);
    }

    private QuizItem grila(Quiz quiz) {
        QuizItem item = itemRepository.save(new QuizItem(quiz, 0, QuizItemType.SINGLE_CHOICE, "2 + 2 = ?", 5, null));
        optionRepository.save(new QuizOption(item, 0, "4", true));
        optionRepository.save(new QuizOption(item, 1, "5", false));
        return item;
    }

    private Long handIn(User student, Quiz quiz, QuizItem item, boolean right) {
        Long optionId = optionRepository.findByItemIdOrderByPosition(item.getId()).stream()
                .filter(o -> o.isCorrect() == right).findFirst().orElseThrow().getId();
        StartedAttemptDto attempt = attemptService.startAttempt(quiz.getId(), student.getEmail(), AttemptMode.TEST);
        attemptService.saveResponse(attempt.attemptId(), item.getId(), optionId, student.getEmail());
        attemptService.submit(attempt.attemptId(), student.getEmail());
        return attempt.attemptId();
    }

    private ResultActions deleteQuiz(Quiz quiz, User teacher, boolean withResults) throws Exception {
        return mockMvc.perform(delete("/api/admin/quizzes/" + quiz.getId() + (withResults ? "?withResults=true" : ""))
                .with(as(teacher)).with(csrf()));
    }

    @Test
    void aPlainDeleteIsStillRefusedAndSaysHowManyAttemptsWouldGo() throws Exception {
        Quiz quiz = publishedTest();
        QuizItem item = grila(quiz);
        handIn(ana, quiz, item, true);
        handIn(ion, quiz, item, false);

        deleteQuiz(quiz, prof, false)
                .andExpect(status().isConflict())
                .andExpect(header().string(QuizAdminController.HANDED_IN_HEADER, "2"));

        assertThat(quizRepository.findById(quiz.getId())).isPresent();
    }

    @Test
    void withResultsTheTestGoesWithEveryAttemptAnswerAndRequest() throws Exception {
        Quiz quiz = publishedTest();
        QuizItem item = grila(quiz);
        Long right = handIn(ana, quiz, item, true);
        Long wrong = handIn(ion, quiz, item, false);
        Long wrongOption = optionRepository.findByItemIdOrderByPosition(item.getId()).stream()
                .filter(o -> !o.isCorrect()).findFirst().orElseThrow().getId();
        jdbc.update("INSERT INTO mistake_explanation_requests (option_id, student_id, requested_at) VALUES (?, ?, ?)",
                wrongOption, ion.getId(), java.sql.Timestamp.from(Instant.now()));
        Quiz other = publishedTest();
        QuizItem otherItem = grila(other);
        Long kept = handIn(ana, other, otherItem, true);

        deleteQuiz(quiz, prof, true).andExpect(status().isNoContent());

        assertThat(quizRepository.findById(quiz.getId())).isEmpty();
        assertThat(attemptRepository.findById(right)).isEmpty();
        assertThat(attemptRepository.findById(wrong)).isEmpty();
        assertThat(responseRepository.findByAttemptId(wrong)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mistake_explanation_requests WHERE option_id = ?",
                Long.class, wrongOption)).isZero();
        assertThat(attemptRepository.findById(kept)).isPresent(); // another test's grades stay
        assertThat(jdbc.queryForObject("SELECT details FROM admin_audit_log WHERE action = 'QUIZ_DELETED' AND entity_id = ?",
                String.class, quiz.getId())).contains("2 încercări predate");
    }

    @Test
    void notWhileAStudentIsWritingIt() throws Exception {
        Quiz quiz = publishedTest();
        QuizItem item = grila(quiz);
        handIn(ana, quiz, item, true);
        attemptService.startAttempt(quiz.getId(), ion.getEmail(), AttemptMode.TEST); // Ion is writing right now

        deleteQuiz(quiz, prof, true).andExpect(status().isConflict());

        assertThat(quizRepository.findById(quiz.getId())).isPresent();
    }

    @Test
    void anotherTeachersTestIsNotTheirsToDelete() throws Exception {
        User other = userRepository.save(new User("alt." + System.nanoTime() + "@scoala.ro", "Alt", Role.TEACHER));
        Quiz quiz = publishedTest();
        QuizItem item = grila(quiz);
        handIn(ana, quiz, item, true);

        deleteQuiz(quiz, other, true).andExpect(status().isNotFound()); // someone else's test looks like no test

        assertThat(quizRepository.findById(quiz.getId())).isPresent();
    }
}
