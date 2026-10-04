package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The hint tables on real PostgreSQL: order, uniqueness, the per-quiz count query and the new constraints. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class QuizItemHintRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizItemHintRepository hintRepository;
    @Autowired private QuizAttemptRepository attemptRepository;
    @Autowired private ItemResponseRepository responseRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;

    private QuizItem item(Quiz quiz, int position) {
        return itemRepository.save(new QuizItem(quiz, position, QuizItemType.OPEN, "s", 10, null));
    }

    @Test
    void hintsComeBackInPositionOrderNoMatterTheInsertOrder() {
        QuizItem item = item(quizRepository.save(new Quiz("Cu indicii", null)), 1);
        hintRepository.save(new QuizItemHint(item, 3, "trei"));
        hintRepository.save(new QuizItemHint(item, 1, "unu"));
        hintRepository.save(new QuizItemHint(item, 2, "doi"));
        hintRepository.flush();

        assertThat(hintRepository.findByItemIdOrderByPosition(item.getId()))
                .extracting(QuizItemHint::getText).containsExactly("unu", "doi", "trei");
    }

    @Test
    void twoHintsOfOneItemCannotShareAPosition() {
        QuizItem item = item(quizRepository.save(new Quiz("Duplicat", null)), 1);
        hintRepository.save(new QuizItemHint(item, 1, "unu"));
        hintRepository.flush();

        assertThatThrownBy(() -> {
            hintRepository.save(new QuizItemHint(item, 1, "alt unu"));
            hintRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingAnItemsHintsLeavesTheOthersAlone() {
        Quiz quiz = quizRepository.save(new Quiz("Doua iteme", null));
        QuizItem first = item(quiz, 1);
        QuizItem second = item(quiz, 2);
        hintRepository.save(new QuizItemHint(first, 1, "a"));
        hintRepository.save(new QuizItemHint(second, 1, "b"));
        hintRepository.flush();

        hintRepository.deleteByItemId(first.getId());
        hintRepository.flush();

        assertThat(hintRepository.findByItemIdOrderByPosition(first.getId())).isEmpty();
        assertThat(hintRepository.findByItemIdOrderByPosition(second.getId())).hasSize(1);
    }

    @Test
    void theCountQueryReturnsOneRowPerItemThatHasHintsAndOnlyForThatQuiz() {
        Quiz quiz = quizRepository.save(new Quiz("Numarare", null));
        QuizItem withTwo = item(quiz, 1);
        QuizItem without = item(quiz, 2);
        QuizItem withOne = item(quiz, 3);
        hintRepository.save(new QuizItemHint(withTwo, 1, "a"));
        hintRepository.save(new QuizItemHint(withTwo, 2, "b"));
        hintRepository.save(new QuizItemHint(withOne, 1, "c"));
        QuizItem otherQuizItem = item(quizRepository.save(new Quiz("Alt quiz", null)), 1);
        hintRepository.save(new QuizItemHint(otherQuizItem, 1, "x"));
        hintRepository.flush();

        List<HintCount> counts = hintRepository.countsByQuizId(quiz.getId());

        assertThat(counts).extracting(HintCount::itemId, HintCount::total)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(withTwo.getId(), 2L),
                        org.assertj.core.groups.Tuple.tuple(withOne.getId(), 1L))
                .doesNotContain(org.assertj.core.groups.Tuple.tuple(without.getId(), 0L));
    }

    @Test
    void hintsUsedIsStoredOnTheResponseAndCannotGoNegative() {
        Quiz quiz = quizRepository.save(new Quiz("Folosite", null));
        QuizItem item = item(quiz, 1);
        User student = userRepository.save(new User("elev.hints@t.ro", "Elev", Role.STUDENT));
        QuizAttempt attempt = attemptRepository.save(
                new QuizAttempt(quiz, student, java.time.Instant.now(), AttemptMode.PRACTICE));
        ItemResponse response = new ItemResponse(attempt, item);
        response.revealHint(2);
        response = responseRepository.save(response);
        responseRepository.flush();

        assertThat(responseRepository.findByAttemptIdAndItemId(attempt.getId(), item.getId()).orElseThrow().getHintsUsed())
                .isEqualTo(2);
        Long id = response.getId();
        assertThatThrownBy(() -> jdbc.update("update item_responses set hints_used = -1 where id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existingResponsesDefaultToNoHintsUsed() {
        Quiz quiz = quizRepository.save(new Quiz("Implicit", null));
        QuizItem item = item(quiz, 1);
        User student = userRepository.save(new User("elev.implicit@t.ro", "Elev", Role.STUDENT));
        QuizAttempt attempt = attemptRepository.save(new QuizAttempt(quiz, student));
        responseRepository.save(new ItemResponse(attempt, item));
        responseRepository.flush();

        assertThat(responseRepository.findByAttemptId(attempt.getId())).singleElement()
                .satisfies(r -> assertThat(r.getHintsUsed()).isZero());
    }
}
