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
        assertThat(quizAttemptRepository.findByQuizIdAndStudentIdAndStatus(
                quiz.getId(), ana.getId(), QuizAttemptStatus.IN_PROGRESS))
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
                .findByStudentIdAndStatusOrderBySubmittedAtAsc(ana.getId(), QuizAttemptStatus.GRADED);

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
}
