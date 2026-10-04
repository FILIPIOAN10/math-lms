package ro.mathlms.quiz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.quiz.QuizDtos.ItemDto;
import ro.mathlms.quiz.StudentQuizDtos.HintDto;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** E3 end to end on real Postgres: the teacher writes hints, a student reveals them in practice, a test gets none. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"app.quiz.expiry-job.initial-delay-ms=3600000", "app.quiz.expiry-job.interval-ms=3600000"})
class QuizHintsIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizAdminService adminService;
    @Autowired private QuizAttemptService service;

    private User student;
    private Quiz quiz;
    private Long itemId;

    @BeforeEach
    void setUp() {
        String stamp = String.valueOf(System.nanoTime());
        student = userRepository.save(new User("elev." + stamp + "@scoala.ro", "Ana Pop", Role.STUDENT));
        Quiz q = new Quiz("Indicii " + stamp, null);
        q.allowPractice(true);
        q.changeTimeLimit(30);
        q.publish();
        quiz = quizRepository.save(q);
        ItemDto item = adminService.addItem(quiz.getId(), new ItemRequest(QuizItemType.OPEN, 1, "Rezolva ecuatia", 10,
                "x = 2", null, List.of("Muta termenii", "Imparte la coeficient", "Verifica solutia")));
        itemId = item.id();
        assertThat(item.hints()).containsExactly("Muta termenii", "Imparte la coeficient", "Verifica solutia");
    }

    @Test
    void aStudentRevealsHintsOneByOneAndTheyComeBackAfterAReload() {
        StartedAttemptDto practice = service.startAttempt(quiz.getId(), student.getEmail(), AttemptMode.PRACTICE);
        assertThat(practice.quiz().items().get(0).hintCount()).isEqualTo(3);
        assertThat(practice.revealedHints()).isEmpty();

        assertThat(service.revealHint(practice.attemptId(), itemId, 1, student.getEmail()))
                .isEqualTo(new HintDto(1, "Muta termenii", 3));
        // no skipping ahead
        assertThatThrownBy(() -> service.revealHint(practice.attemptId(), itemId, 3, student.getEmail()))
                .isInstanceOf(InvalidQuizException.class);
        assertThat(service.revealHint(practice.attemptId(), itemId, 2, student.getEmail()).text()).isEqualTo("Imparte la coeficient");
        // a double click on an earlier hint changes nothing
        assertThat(service.revealHint(practice.attemptId(), itemId, 1, student.getEmail()).text()).isEqualTo("Muta termenii");

        // page reload: same attempt, the two revealed hints are restored, and no phantom "answer" appears
        StartedAttemptDto resumed = service.startAttempt(quiz.getId(), student.getEmail(), AttemptMode.PRACTICE);
        assertThat(resumed.attemptId()).isEqualTo(practice.attemptId());
        assertThat(resumed.answers()).isEmpty();
        assertThat(resumed.revealedHints()).singleElement().satisfies(r -> {
            assertThat(r.itemId()).isEqualTo(itemId);
            assertThat(r.hints()).containsExactly("Muta termenii", "Imparte la coeficient");
        });

        // finishing records how many hints were used
        service.submit(practice.attemptId(), student.getEmail());
        assertThat(service.getResult(practice.attemptId(), student.getEmail()).items()).singleElement().satisfies(i -> {
            assertThat(i.hintsUsed()).isEqualTo(2);
            assertThat(i.hintsAvailable()).isEqualTo(3);
        });
    }

    @Test
    void aGradedTestNeverShowsOrGivesHints() {
        StartedAttemptDto test = service.startAttempt(quiz.getId(), student.getEmail());

        assertThat(test.quiz().items().get(0).hintCount()).isZero();   // the screen does not even learn they exist
        assertThatThrownBy(() -> service.revealHint(test.attemptId(), itemId, 1, student.getEmail()))
                .isInstanceOf(InvalidQuizException.class);

        service.submit(test.attemptId(), student.getEmail());
        assertThat(service.getResult(test.attemptId(), student.getEmail()).items()).singleElement().satisfies(i -> {
            assertThat(i.hintsUsed()).isZero();
            assertThat(i.hintsAvailable()).isZero();
        });
    }

    @Test
    void updatingTheItemReplacesItsHints() {
        ItemDto updated = adminService.updateItem(itemId, new ItemRequest(QuizItemType.OPEN, 1, "Rezolva ecuatia", 10,
                "x = 2", null, List.of("Doar un indiciu nou")));

        assertThat(updated.hints()).containsExactly("Doar un indiciu nou");
        assertThat(adminService.getQuizDetail(quiz.getId()).items().get(0).hints()).containsExactly("Doar un indiciu nou");
    }
}
