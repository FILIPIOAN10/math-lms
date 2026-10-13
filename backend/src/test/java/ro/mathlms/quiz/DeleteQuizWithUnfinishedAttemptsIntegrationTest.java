package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * "Depublică, apoi Șterge" on a test a student started and never handed in: the attempt, its answers, requirement
 * points and photo rows go with the test - against the real schema, so no foreign key is left pointing at it.
 * Not @Transactional on purpose: the deletion commits, like in production.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DeleteQuizWithUnfinishedAttemptsIntegrationTest {

    @Autowired private QuizAdminService adminService;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizItemPartRepository partRepository;
    @Autowired private QuizAttemptRepository attemptRepository;
    @Autowired private ItemResponseRepository responseRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TransactionTemplate tx;

    @Test
    void anUnpublishedTestIsDeletedWithTheAttemptNobodyHandedIn() {
        long stamp = System.nanoTime();
        User student = userRepository.save(new User("neterminat" + stamp + "@scoala.ro", "Elev Neterminat", Role.STUDENT));
        Quiz quiz = quizRepository.save(new Quiz("De șters " + stamp, null));
        QuizItem problem = itemRepository.save(new QuizItem(quiz, 0, QuizItemType.OPEN, "Problema", 10, null));
        QuizItemPart a = partRepository.save(new QuizItemPart(problem, 0, "a)", 5, null));
        partRepository.save(new QuizItemPart(problem, 1, "b)", 5, null));
        quiz.publish();
        quizRepository.save(quiz);
        Long attemptId = tx.execute(status -> {
            QuizAttempt attempt = attemptRepository.save(new QuizAttempt(quiz, student));
            ItemResponse response = new ItemResponse(attempt, problem);
            response.addPhoto("lipsa-" + stamp + ".jpg", Instant.now()); // no file on disk: deleting it is quiet
            response.gradeParts(partRepository.findByItemIdOrderByPosition(problem.getId()), Map.of(a.getId(), 2), null);
            responseRepository.save(response);
            return attempt.getId();
        });

        // Published: the student may be writing right now.
        assertThatThrownBy(() -> adminService.deleteQuiz(quiz.getId())).isInstanceOf(QuizInUseException.class);
        assertThat(attemptRepository.findById(attemptId)).isPresent();

        adminService.setPublished(quiz.getId(), false);
        adminService.deleteQuiz(quiz.getId());

        assertThat(quizRepository.findById(quiz.getId())).isEmpty();
        assertThat(attemptRepository.findById(attemptId)).isEmpty();
        assertThat(responseRepository.findByAttemptId(attemptId)).isEmpty();
        assertThat(itemRepository.findByQuizIdOrderByPosition(quiz.getId())).isEmpty();
    }

    @Test
    void aHandedInAttemptKeepsTheTest() {
        long stamp = System.nanoTime();
        User student = userRepository.save(new User("predat" + stamp + "@scoala.ro", "Elev Predat", Role.STUDENT));
        Quiz quiz = quizRepository.save(new Quiz("Cu note " + stamp, null));
        itemRepository.save(new QuizItem(quiz, 0, QuizItemType.OPEN, "Problema", 10, null));
        QuizAttempt attempt = new QuizAttempt(quiz, student);
        attempt.submit();
        attemptRepository.save(attempt);

        assertThatThrownBy(() -> adminService.deleteQuiz(quiz.getId()))
                .isInstanceOf(QuizInUseException.class)
                .hasMessageContaining("E deja ciornă");
        assertThat(quizRepository.findById(quiz.getId())).isPresent();
        assertThat(attemptRepository.findByQuizId(quiz.getId())).hasSize(1);
        assertThat(List.of(attempt.getStatus())).containsExactly(QuizAttemptStatus.SUBMITTED);
    }
}
