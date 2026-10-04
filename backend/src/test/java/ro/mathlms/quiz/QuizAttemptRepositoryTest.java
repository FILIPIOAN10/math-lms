package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class QuizAttemptRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private QuizItemRepository quizItemRepository;

    @Autowired
    private QuizOptionRepository quizOptionRepository;

    @Autowired
    private QuizAttemptRepository quizAttemptRepository;

    @Autowired
    private ItemResponseRepository itemResponseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private User student(String email) {
        return userRepository.save(new User(email, "Elev " + email, Role.STUDENT));
    }

    @Test
    void persistsAnAttemptWithResponses() {
        Quiz quiz = quizRepository.save(new Quiz("Simulare EN", null));
        QuizItem grila = quizItemRepository.save(
                new QuizItem(quiz, 1, QuizItemType.SINGLE_CHOICE, "Cât e $2+2$?", 5, null));
        QuizOption right = quizOptionRepository.save(new QuizOption(grila, 0, "4", true));
        QuizItem deschis = quizItemRepository.save(
                new QuizItem(quiz, 2, QuizItemType.OPEN, "Rezolvă.", 30, "barem"));
        User ana = student("ana@scoala.ro");

        QuizAttempt attempt = quizAttemptRepository.save(new QuizAttempt(quiz, ana));
        ItemResponse r1 = new ItemResponse(attempt, grila);
        r1.answerSingleChoice(right);
        ItemResponse r2 = new ItemResponse(attempt, deschis);
        r2.answerOpen("uploads/rezolvare.jpg");
        itemResponseRepository.save(r1);
        itemResponseRepository.save(r2);

        List<ItemResponse> responses = itemResponseRepository.findByAttemptId(attempt.getId());

        assertThat(responses).hasSize(2);
        assertThat(itemResponseRepository.findByAttemptIdAndItemId(attempt.getId(), grila.getId()))
                .get().extracting(r -> r.getSelectedOption().getId()).isEqualTo(right.getId());
        assertThat(itemResponseRepository.findByAttemptIdAndItemId(attempt.getId(), deschis.getId()))
                .get().extracting(ItemResponse::getImageKey).isEqualTo("uploads/rezolvare.jpg");
    }

    @Test
    void enforcesOneResponsePerAttemptAndItem() {
        Quiz quiz = quizRepository.save(new Quiz("Simulare EN", null));
        QuizItem item = quizItemRepository.save(
                new QuizItem(quiz, 1, QuizItemType.OPEN, "Rezolvă.", 30, null));
        User ana = student("ana@scoala.ro");
        QuizAttempt attempt = quizAttemptRepository.save(new QuizAttempt(quiz, ana));
        ItemResponse first = new ItemResponse(attempt, item);
        first.answerOpen("a.jpg");
        itemResponseRepository.saveAndFlush(first);

        ItemResponse duplicate = new ItemResponse(attempt, item);
        duplicate.answerOpen("b.jpg");

        assertThatThrownBy(() -> itemResponseRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void answerSingleChoiceRejectsAnotherItemsOption() {
        Quiz quiz = quizRepository.save(new Quiz("Simulare EN", null));
        QuizItem item = quizItemRepository.save(
                new QuizItem(quiz, 1, QuizItemType.SINGLE_CHOICE, "s1", 5, null));
        QuizItem other = quizItemRepository.save(
                new QuizItem(quiz, 2, QuizItemType.SINGLE_CHOICE, "s2", 5, null));
        QuizOption strayOption = quizOptionRepository.save(new QuizOption(other, 0, "X", true));
        User ana = student("ana@scoala.ro");
        QuizAttempt attempt = quizAttemptRepository.save(new QuizAttempt(quiz, ana));
        ItemResponse response = new ItemResponse(attempt, item);

        assertThatThrownBy(() -> response.answerSingleChoice(strayOption))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void findsAttemptsByStudentAndInProgress() {
        Quiz quiz = quizRepository.save(new Quiz("Simulare EN", null));
        User ana = student("ana@scoala.ro");
        QuizAttempt inProgress = quizAttemptRepository.save(new QuizAttempt(quiz, ana));
        QuizAttempt done = new QuizAttempt(quiz, ana);
        done.submit();
        quizAttemptRepository.save(done);

        assertThat(quizAttemptRepository.findByStudentIdOrderByStartedAtDesc(ana.getId()))
                .hasSize(2);
        assertThat(quizAttemptRepository.findByQuizIdAndStudentIdAndStatusAndMode(
                quiz.getId(), ana.getId(), QuizAttemptStatus.IN_PROGRESS, AttemptMode.TEST))
                .get().extracting(QuizAttempt::getId).isEqualTo(inProgress.getId());
    }

    @Test
    void allowsOnlyOneInProgressAttemptPerQuizAndStudent() {
        Quiz quiz = quizRepository.save(new Quiz("Simulare EN", null));
        User ana = student("ana@scoala.ro");
        quizAttemptRepository.saveAndFlush(new QuizAttempt(quiz, ana));

        assertThatThrownBy(() -> quizAttemptRepository.saveAndFlush(new QuizAttempt(quiz, ana)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void finishedAttemptsDoNotBlockANewOne() {
        Quiz quiz = quizRepository.save(new Quiz("Simulare EN", null));
        User ana = student("ana@scoala.ro");
        QuizAttempt done = new QuizAttempt(quiz, ana);
        done.submit();
        quizAttemptRepository.saveAndFlush(done);

        QuizAttempt again = quizAttemptRepository.saveAndFlush(new QuizAttempt(quiz, ana));

        assertThat(again.getId()).isNotNull();
    }

    @Test
    void gradedAttemptsOfAStudentComeOldestFirstAndOnlyTheirOwn() {
        Quiz quiz = quizRepository.save(new Quiz("Simulare EN", null));
        User ana = student("ana@scoala.ro");
        User dan = student("dan@scoala.ro");
        quizAttemptRepository.save(new QuizAttempt(quiz, ana)); // still in progress
        QuizAttempt submittedOnly = new QuizAttempt(quiz, ana);
        submittedOnly.submit();
        quizAttemptRepository.save(submittedOnly);
        QuizAttempt first = new QuizAttempt(quiz, ana);
        first.submit();
        first.markGraded(5);
        quizAttemptRepository.saveAndFlush(first);
        QuizAttempt second = new QuizAttempt(quiz, ana);
        second.submit();
        second.markGraded(9);
        quizAttemptRepository.saveAndFlush(second);
        QuizAttempt others = new QuizAttempt(quiz, dan);
        others.submit();
        others.markGraded(1);
        quizAttemptRepository.saveAndFlush(others);

        List<QuizAttempt> graded = quizAttemptRepository
                .findByStudentIdAndStatusAndModeOrderBySubmittedAtAsc(ana.getId(), QuizAttemptStatus.GRADED, AttemptMode.TEST);

        assertThat(graded).extracting(QuizAttempt::getScore).containsExactly(5, 9);
    }

    @Test
    void sumsTheItemPointsOfEachQuizInOneQuery() {
        Quiz simulare = quizRepository.save(new Quiz("Simulare EN", null));
        Quiz empty = quizRepository.save(new Quiz("Fara subiecte", null));
        quizItemRepository.save(new QuizItem(simulare, 1, QuizItemType.SINGLE_CHOICE, "s1", 5, null));
        quizItemRepository.save(new QuizItem(simulare, 2, QuizItemType.OPEN, "s2", 10, "barem"));

        List<QuizMaxScore> totals = quizItemRepository.sumPointsByQuiz(List.of(simulare.getId(), empty.getId()));

        assertThat(totals).singleElement().satisfies(total -> {
            assertThat(total.quizId()).isEqualTo(simulare.getId());
            assertThat(total.maxScore()).isEqualTo(15L);
        });
    }

    @Test
    void gradedScoresAndPerItemStatsOfAQuiz() {
        Quiz quiz = quizRepository.save(new Quiz("Simulare EN", null));
        QuizItem grila = quizItemRepository.save(new QuizItem(quiz, 1, QuizItemType.SINGLE_CHOICE, "g", 5, null));
        QuizOption right = quizOptionRepository.save(new QuizOption(grila, 0, "4", true));
        QuizOption wrong = quizOptionRepository.save(new QuizOption(grila, 1, "5", false));
        QuizItem deschis = quizItemRepository.save(new QuizItem(quiz, 2, QuizItemType.OPEN, "d", 10, "barem"));

        gradedAttempt(quiz, student("ana@scoala.ro"), grila, right, deschis, 8, 13);
        gradedAttempt(quiz, student("dan@scoala.ro"), grila, wrong, deschis, 4, 4);
        quizAttemptRepository.saveAndFlush(new QuizAttempt(quiz, student("inprogress@scoala.ro"))); // ignored

        assertThat(quizAttemptRepository.findGradedScoresByQuizId(quiz.getId())).containsExactlyInAnyOrder(13, 4);

        List<ItemStat> stats = itemResponseRepository.findItemStatsByQuizId(quiz.getId());
        ItemStat grilaStat = stats.stream().filter(s -> s.itemId().equals(grila.getId())).findFirst().orElseThrow();
        ItemStat deschisStat = stats.stream().filter(s -> s.itemId().equals(deschis.getId())).findFirst().orElseThrow();
        assertThat(grilaStat.answered()).isEqualTo(2L);
        assertThat(grilaStat.correct()).isEqualTo(1L);
        assertThat(deschisStat.totalPoints()).isEqualTo(12L); // 8 + 4
    }

    private void gradedAttempt(Quiz quiz, User student, QuizItem grila, QuizOption chosen, QuizItem deschis,
                               int openPoints, int score) {
        QuizAttempt attempt = quizAttemptRepository.save(new QuizAttempt(quiz, student));
        ItemResponse r1 = new ItemResponse(attempt, grila);
        r1.answerSingleChoice(chosen);
        r1.gradeAuto(chosen.isCorrect(), chosen.isCorrect() ? 5 : 0);
        ItemResponse r2 = new ItemResponse(attempt, deschis);
        r2.answerOpen("uploads/x.jpg");
        r2.gradeManual(openPoints);
        itemResponseRepository.save(r1);
        itemResponseRepository.save(r2);
        attempt.submit();
        attempt.markGraded(score);
        quizAttemptRepository.saveAndFlush(attempt);
    }

    @Test
    void timeLimitAndDeadlineRoundTripAndOnlyOverdueInProgressAttemptsAreFound() {
        Quiz timed = new Quiz("Cronometrat", null);
        timed.changeTimeLimit(10);
        timed = quizRepository.save(timed);
        Quiz untimed = quizRepository.save(new Quiz("Fara limita", null));

        QuizAttempt overdue = quizAttemptRepository.save(new QuizAttempt(timed, student("a@t.ro")));
        QuizAttempt stillOpen = quizAttemptRepository.save(new QuizAttempt(timed, student("b@t.ro")));
        QuizAttempt noDeadline = quizAttemptRepository.save(new QuizAttempt(untimed, student("c@t.ro")));
        QuizAttempt alreadyIn = new QuizAttempt(timed, student("d@t.ro"));
        alreadyIn.submit();
        alreadyIn = quizAttemptRepository.save(alreadyIn);
        quizAttemptRepository.flush();

        assertThat(quizRepository.findById(timed.getId()).orElseThrow().getTimeLimitMinutes()).isEqualTo(10);
        assertThat(noDeadline.getDeadlineAt()).isNull();

        // "now" is 11 minutes after everyone started: every 10-minute deadline has passed ...
        java.time.Instant later = overdue.getStartedAt().plus(java.time.Duration.ofMinutes(11));
        assertThat(quizAttemptRepository.findOverdueIds(later))
                .contains(overdue.getId(), stillOpen.getId())      // ... for the two in-progress timed attempts
                .doesNotContain(noDeadline.getId(), alreadyIn.getId()); // not for an untimed or an already submitted one

        // ... and 5 minutes in, nothing is overdue yet
        assertThat(quizAttemptRepository.findOverdueIds(overdue.getStartedAt().plus(java.time.Duration.ofMinutes(5))))
                .doesNotContain(overdue.getId(), stillOpen.getId());
    }

    @Test
    void theDatabaseRejectsAnOutOfRangeTimeLimit() {
        Quiz quiz = quizRepository.save(new Quiz("Cronometrat", null));

        assertThatThrownBy(() -> {
            jdbc.update("update quizzes set time_limit_minutes = 0 where id = ?", quiz.getId());
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- E2 practice mode ---

    private QuizAttempt gradedTest(Quiz quiz, QuizItem item, QuizOption option, User who, int score) {
        QuizAttempt attempt = new QuizAttempt(quiz, who);
        ItemResponse response = new ItemResponse(attempt, item);
        response.answerSingleChoice(option);
        response.gradeAuto(true, score);
        attempt.submit();
        attempt.markGraded(score);
        attempt = quizAttemptRepository.save(attempt);
        itemResponseRepository.save(response);
        return attempt;
    }

    private QuizAttempt finishedPractice(Quiz quiz, QuizItem item, QuizOption option, User who) {
        QuizAttempt attempt = new QuizAttempt(quiz, who, java.time.Instant.now(), AttemptMode.PRACTICE);
        ItemResponse response = new ItemResponse(attempt, item);
        response.answerSingleChoice(option);
        response.gradeAuto(true, 5);
        attempt.submit();
        attempt.completePractice();
        attempt = quizAttemptRepository.save(attempt);
        itemResponseRepository.save(response);
        return attempt;
    }

    @Test
    void practiceAttemptsNeverReachStatisticsProgressItemStatsOrTheGradingQueue() {
        Quiz quiz = quizRepository.save(new Quiz("Cu practica", null));
        QuizItem item = quizItemRepository.save(new QuizItem(quiz, 1, QuizItemType.SINGLE_CHOICE, "s", 5, null));
        QuizOption right = quizOptionRepository.save(new QuizOption(item, 0, "A", true));
        User ana = student("ana.practica@t.ro");
        QuizAttempt test = gradedTest(quiz, item, right, ana, 5);
        finishedPractice(quiz, item, right, ana);
        QuizAttempt submittedPractice = new QuizAttempt(quiz, student("bob.practica@t.ro"), java.time.Instant.now(), AttemptMode.PRACTICE);
        submittedPractice.submit();
        quizAttemptRepository.save(submittedPractice);
        quizAttemptRepository.flush();

        // the teacher's statistics: one graded TEST, no null score from the practice
        assertThat(quizAttemptRepository.findGradedScoresByQuizId(quiz.getId())).containsExactly(5);
        // per-item stats count the test's answer only
        assertThat(itemResponseRepository.findItemStatsByQuizId(quiz.getId()))
                .singleElement().satisfies(stat -> assertThat(stat.answered()).isEqualTo(1L));
        // the student's progress chart
        assertThat(quizAttemptRepository.findByStudentIdAndStatusAndModeOrderBySubmittedAtAsc(
                ana.getId(), QuizAttemptStatus.GRADED, AttemptMode.TEST))
                .extracting(QuizAttempt::getId).containsExactly(test.getId());
        // the grading queue
        assertThat(quizAttemptRepository.findByStatusForGrading(QuizAttemptStatus.SUBMITTED))
                .extracting(QuizAttempt::getId).doesNotContain(submittedPractice.getId());
        // ... while "my attempts" still lists both sittings
        assertThat(quizAttemptRepository.findByStudentIdOrderByStartedAtDesc(ana.getId())).hasSize(2);
    }

    @Test
    void oneTestAndOnePracticeCanBeOpenTogetherButNotTwoOfTheSameKind() {
        Quiz quiz = quizRepository.save(new Quiz("Doua moduri", null));
        User ana = student("ana.moduri@t.ro");
        quizAttemptRepository.save(new QuizAttempt(quiz, ana));
        quizAttemptRepository.save(new QuizAttempt(quiz, ana, java.time.Instant.now(), AttemptMode.PRACTICE));
        quizAttemptRepository.flush();

        assertThatThrownBy(() -> {
            quizAttemptRepository.save(new QuizAttempt(quiz, ana, java.time.Instant.now(), AttemptMode.PRACTICE));
            quizAttemptRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseItselfRefusesAPracticeWithADeadline() {
        Quiz quiz = quizRepository.save(new Quiz("Fara ceas", null));
        QuizAttempt practice = quizAttemptRepository.save(
                new QuizAttempt(quiz, student("ana.ceas@t.ro"), java.time.Instant.now(), AttemptMode.PRACTICE));
        quizAttemptRepository.flush();

        assertThatThrownBy(() ->
                jdbc.update("update quiz_attempts set deadline_at = now() where id = ?", practice.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void practiceAllowedAndTheModeRoundTripThroughTheDatabase() {
        Quiz quiz = new Quiz("Opt-in", null);
        quiz.allowPractice(true);
        quiz = quizRepository.save(quiz);
        QuizAttempt practice = quizAttemptRepository.save(
                new QuizAttempt(quiz, student("ana.rt@t.ro"), java.time.Instant.now(), AttemptMode.PRACTICE));
        quizAttemptRepository.flush();

        assertThat(quizRepository.findById(quiz.getId()).orElseThrow().isPracticeAllowed()).isTrue();
        assertThat(quizAttemptRepository.findById(practice.getId()).orElseThrow().getMode()).isEqualTo(AttemptMode.PRACTICE);
    }
}
