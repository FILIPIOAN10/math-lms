package ro.mathlms.quiz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.quiz.StudentQuizDtos.OpenAttemptDto;
import ro.mathlms.quiz.StudentQuizDtos.QuizPreviewDto;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The screen before a test says when the student is coming back to one they started: how far they got and, on a timed
 * test, that the clock already runs. On real Postgres, through the same service the start goes through.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"app.quiz.expiry-job.initial-delay-ms=3600000", "app.quiz.expiry-job.interval-ms=3600000"})
class PreviewOpenAttemptIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizOptionRepository optionRepository;
    @Autowired private QuizAttemptService attemptService;

    private User ana;
    private Quiz quiz;
    private QuizItem first;

    @BeforeEach
    void setUp() {
        ana = userRepository.save(new User("ana." + System.nanoTime() + "@scoala.ro", "Ana Pop", Role.STUDENT));
    }

    private void grileTest(Integer timeLimitMinutes) {
        Quiz q = new Quiz("Reluare " + System.nanoTime(), null);
        q.allowPractice(true);
        q.changeTimeLimit(timeLimitMinutes);
        q.publish();
        quiz = quizRepository.save(q);
        first = grila(1);
        grila(2);
    }

    private QuizItem grila(int position) {
        QuizItem item = itemRepository.save(new QuizItem(quiz, position, QuizItemType.SINGLE_CHOICE, "g", 5, null));
        optionRepository.save(new QuizOption(item, 0, "bine", true));
        optionRepository.save(new QuizOption(item, 1, "greșit", false));
        return item;
    }

    private Long anOption(QuizItem item) {
        return optionRepository.findByItemIdOrderByPosition(item.getId()).getFirst().getId();
    }

    private QuizPreviewDto preview() {
        return attemptService.getPreview(quiz.getId(), ana.getEmail());
    }

    @Test
    void nothingStartedMeansNothingToResume() {
        grileTest(null);

        assertThat(preview().openTest()).isNull();
        assertThat(preview().openPractice()).isNull();
    }

    @Test
    void aStartedTestSaysHowManyQuestionsAreAnswered() {
        grileTest(null);
        StartedAttemptDto attempt = attemptService.startAttempt(quiz.getId(), ana.getEmail());
        attemptService.saveResponse(attempt.attemptId(), first.getId(), anOption(first), ana.getEmail());

        OpenAttemptDto open = preview().openTest();

        assertThat(open).isNotNull();
        assertThat(open.answered()).isEqualTo(1);
        assertThat(open.deadlineAt()).isNull();
        assertThat(preview().openPractice()).isNull(); // the test and the practice are resumed apart
    }

    @Test
    void aTimedTestStartedEarlierHasItsDeadlineAndTheServerClock() {
        grileTest(30);
        StartedAttemptDto attempt = attemptService.startAttempt(quiz.getId(), ana.getEmail());

        OpenAttemptDto open = preview().openTest();

        // Postgres keeps microseconds, the attempt returned on start had nanoseconds
        assertThat(open.deadlineAt()).isCloseTo(attempt.deadlineAt(), within(1, ChronoUnit.MILLIS));
        assertThat(open.serverNow()).isBefore(open.deadlineAt());
    }

    @Test
    void anOpenPracticeIsReportedOnItsOwn() {
        grileTest(null);
        attemptService.startAttempt(quiz.getId(), ana.getEmail(), AttemptMode.PRACTICE);

        assertThat(preview().openPractice()).isNotNull();
        assertThat(preview().openPractice().answered()).isZero();
        assertThat(preview().openTest()).isNull();
    }

    @Test
    void aHandedInTestIsNoLongerOpen() {
        grileTest(null);
        StartedAttemptDto attempt = attemptService.startAttempt(quiz.getId(), ana.getEmail());
        attemptService.submit(attempt.attemptId(), ana.getEmail());

        assertThat(preview().openTest()).isNull();
    }
}
