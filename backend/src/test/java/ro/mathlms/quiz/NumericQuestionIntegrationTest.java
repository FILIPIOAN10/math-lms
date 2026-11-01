package ro.mathlms.quiz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.quiz.QuizDtos.ItemDto;
import ro.mathlms.quiz.QuizStatsDtos.NumericStatDto;
import ro.mathlms.quiz.StudentQuizDtos.AnswerFeedbackDto;
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultDto;
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultViewDto;
import ro.mathlms.quiz.StudentQuizDtos.ItemResultDto;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A question answered with a number, from the teacher's dialog to the grade, on real Postgres. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"app.quiz.expiry-job.initial-delay-ms=3600000", "app.quiz.expiry-job.interval-ms=3600000"})
class NumericQuestionIntegrationTest {

    @Autowired private QuizAdminService service;
    @Autowired private QuizAttemptService attemptService;
    @Autowired private QuizStatsService statsService;
    @Autowired private AnswerKeyCorrection correction;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizAttemptRepository attemptRepository;
    @Autowired private ItemResponseRepository responseRepository;
    @Autowired private UserRepository userRepository;

    private Quiz quiz;
    private Long itemId;

    private static ItemRequest numeric(String answer) {
        return new ItemRequest(QuizItemType.NUMERIC, 0, "Cât face 1/2 + 1/4?", 5, "Aduci la numitorul 4.", null, List.of(),
                null, null, null, null, null, answer);
    }

    @BeforeEach
    void setUp() {
        Quiz q = new Quiz("Numere " + System.nanoTime(), null);
        q.publish();
        q.allowPractice(true);
        quiz = quizRepository.save(q);
        itemId = service.addItem(quiz.getId(), numeric("3/4")).id();
    }

    private User student() {
        return userRepository.save(new User("elev" + System.nanoTime() + "@scoala.ro", "Elev", Role.STUDENT));
    }

    private Long handIn(String value) {
        User student = student();
        StartedAttemptDto attempt = attemptService.startAttempt(quiz.getId(), student.getEmail());
        attemptService.saveNumericResponse(attempt.attemptId(), itemId, value, null, null, student.getEmail());
        AttemptResultDto result = attemptService.submit(attempt.attemptId(), student.getEmail());
        assertThat(result.status()).isEqualTo(QuizAttemptStatus.GRADED); // nothing waits for the teacher
        return attempt.attemptId();
    }

    private int score(Long attemptId) {
        return attemptRepository.findById(attemptId).orElseThrow().getScore();
    }

    @Test
    void anEquivalentFormEarnsThePoints() {
        assertThat(score(handIn("0,75"))).isEqualTo(5);
        assertThat(score(handIn("6/8"))).isEqualTo(5);
        assertThat(score(handIn("3 / 4"))).isEqualTo(5);
    }

    @Test
    void aWrongNumberOrSomethingElseEarnsNothing() {
        assertThat(score(handIn("0,7"))).isZero();
        assertThat(score(handIn("nu știu"))).isZero();
    }

    @Test
    void aBlankAnswerCountsAsNoAnswer() {
        assertThat(score(handIn("   "))).isZero();
    }

    @Test
    void theAnswerStaysHiddenInTheTestAndTheResultShowsBothAsWritten() {
        User student = student();
        StartedAttemptDto attempt = attemptService.startAttempt(quiz.getId(), student.getEmail());

        assertThat(attempt.quiz().items().get(0).type()).isEqualTo(QuizItemType.NUMERIC);
        assertThat(attempt.quiz().items().get(0).toString()).doesNotContain("3/4");
        assertThat(attemptService.saveNumericResponse(attempt.attemptId(), itemId, "0,5", null, null, student.getEmail()))
                .isEmpty();
        attemptService.submit(attempt.attemptId(), student.getEmail());

        AttemptResultViewDto view = attemptService.getResult(attempt.attemptId(), student.getEmail());
        ItemResultDto result = view.items().get(0);
        assertThat(result.selectedOptionText()).isEqualTo("0,5");
        assertThat(result.correctOptionText()).isEqualTo("3/4");
        assertThat(result.correct()).isFalse();
    }

    @Test
    void aResumedAttemptGetsItsNumberBack() {
        User student = student();
        StartedAttemptDto attempt = attemptService.startAttempt(quiz.getId(), student.getEmail());
        attemptService.saveNumericResponse(attempt.attemptId(), itemId, "0,75", null, null, student.getEmail());

        StartedAttemptDto resumed = attemptService.startAttempt(quiz.getId(), student.getEmail());

        assertThat(resumed.answers()).hasSize(1);
        assertThat(resumed.answers().get(0).numericAnswer()).isEqualTo("0,75");
    }

    @Test
    void anOlderRetryArrivingLateDoesNotReplaceANewerAnswer() {
        User student = student();
        StartedAttemptDto attempt = attemptService.startAttempt(quiz.getId(), student.getEmail());
        UUID browser = UUID.randomUUID();

        attemptService.saveNumericResponse(attempt.attemptId(), itemId, "3/4", browser, 2L, student.getEmail());
        attemptService.saveNumericResponse(attempt.attemptId(), itemId, "1", browser, 1L, student.getEmail());

        assertThat(responseRepository.findByAttemptIdAndItemId(attempt.attemptId(), itemId).orElseThrow().getNumericAnswer())
                .isEqualTo("3/4");
    }

    @Test
    void aPracticeSaysOnTheSpotAndGivesTheAnswer() {
        User student = student();
        StartedAttemptDto attempt = attemptService.startAttempt(quiz.getId(), student.getEmail(), AttemptMode.PRACTICE);

        AnswerFeedbackDto wrong = attemptService
                .saveNumericResponse(attempt.attemptId(), itemId, "1", null, null, student.getEmail()).orElseThrow();
        AnswerFeedbackDto right = attemptService
                .saveNumericResponse(attempt.attemptId(), itemId, "0,75", null, null, student.getEmail()).orElseThrow();

        assertThat(wrong.correct()).isFalse();
        assertThat(wrong.correctNumber()).isEqualTo("3/4");
        assertThat(wrong.solution()).isEqualTo("Aduci la numitorul 4.");
        assertThat(right.correct()).isTrue();
    }

    @Test
    void aNumberIsNotAcceptedOnAGrila() {
        QuizItem grila = itemRepository.save(new QuizItem(quiz, 1, QuizItemType.SINGLE_CHOICE, "g", 1, null));
        User student = student();
        StartedAttemptDto attempt = attemptService.startAttempt(quiz.getId(), student.getEmail());

        assertThatThrownBy(() -> attemptService.saveNumericResponse(attempt.attemptId(), grila.getId(), "1", null, null,
                student.getEmail())).isInstanceOf(InvalidQuizException.class);
    }

    @Test
    void theTeacherMustGiveANumber() {
        assertThatThrownBy(() -> service.addItem(quiz.getId(), numeric("20 cm"))).isInstanceOf(InvalidQuizException.class);
        assertThatThrownBy(() -> service.addItem(quiz.getId(), numeric(null))).isInstanceOf(InvalidQuizException.class);
    }

    @Test
    void theAnswerCanChangeBeforeTheTestIsTakenButNotQuietlyAfter() {
        ItemDto changed = service.updateItem(itemId, numeric("1/4"));
        assertThat(changed.numericAnswer()).isEqualTo("1/4");

        handIn("1/4");

        assertThatThrownBy(() -> service.updateItem(itemId, numeric("1/2"))).isInstanceOf(QuizInUseException.class);
        assertThat(service.updateItem(itemId, numeric("0,25")).numericAnswer()).isEqualTo("0,25"); // the same value
    }

    @Test
    void correctingTheAnswerMarksEveryPaperAgain() {
        Long wasWrong = handIn("1");
        Long wasRight = handIn("3/4");

        assertThat(correction.correctNumber(itemId, "1/1").changedGrades()).isEqualTo(2);

        assertThat(score(wasWrong)).isEqualTo(5);
        assertThat(score(wasRight)).isZero();
    }

    @Test
    void statisticsCountTheSameValueOnceHoweverItWasWritten() {
        handIn("3/4");
        handIn("0,75");
        handIn("1");

        QuizStatsDtos.ItemStatDto stat = statsService.getStats(quiz.getId()).items().get(0);

        assertThat(stat.correctRate()).isEqualTo(2.0 / 3);
        assertThat(stat.numericAnswer()).isEqualTo("3/4");
        assertThat(stat.numbers()).containsExactly(new NumericStatDto("3/4", 2, true), new NumericStatDto("1", 1, false));
    }

    @Test
    void aCopyOfTheTestKeepsTheAnswer() {
        Quiz copy = service.copyQuiz(quiz.getId());

        assertThat(itemRepository.findByQuizIdOrderByPosition(copy.getId()).get(0).getNumericAnswer()).isEqualTo("3/4");
    }
}
