package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class QuizRepositoryTest {

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

    @Test
    void findsPublishedQuizzes() {
        Quiz draft = new Quiz("Draft", null);
        Quiz published = new Quiz("Publicat", null);
        published.publish();
        quizRepository.save(draft);
        quizRepository.save(published);

        List<Quiz> result = quizRepository.findByStatusOrderByTitle(QuizStatus.PUBLISHED);

        assertThat(result).extracting(Quiz::getTitle).containsExactly("Publicat");
    }

    @Test
    void savesItemsAndOptionsInOrder() {
        Quiz quiz = quizRepository.save(new Quiz("Simulare EN", null));
        QuizItem s2 = quizItemRepository.save(
                new QuizItem(quiz, 2, QuizItemType.SINGLE_CHOICE, "s2", 5, null));
        quizItemRepository.save(new QuizItem(quiz, 1, QuizItemType.SINGLE_CHOICE, "s1", 5, null));
        quizOptionRepository.save(new QuizOption(s2, 1, "B", false));
        quizOptionRepository.save(new QuizOption(s2, 0, "A", true));

        List<QuizItem> items = quizItemRepository.findByQuizIdOrderByPosition(quiz.getId());
        List<QuizOption> options = quizOptionRepository.findByItemIdOrderByPosition(s2.getId());

        assertThat(items).extracting(QuizItem::getStatement).containsExactly("s1", "s2");
        assertThat(options).extracting(QuizOption::getText).containsExactly("A", "B");
        assertThat(options).filteredOn(QuizOption::isCorrect).extracting(QuizOption::getText)
                .containsExactly("A");
    }

    @Test
    void deleteByItemIdRemovesAnItemsOptions() {
        Quiz quiz = quizRepository.save(new Quiz("Simulare EN", null));
        QuizItem item = quizItemRepository.save(
                new QuizItem(quiz, 1, QuizItemType.SINGLE_CHOICE, "s1", 5, null));
        quizOptionRepository.save(new QuizOption(item, 0, "A", true));
        quizOptionRepository.save(new QuizOption(item, 1, "B", false));

        quizOptionRepository.deleteByItemId(item.getId());

        assertThat(quizOptionRepository.findByItemIdOrderByPosition(item.getId())).isEmpty();
    }
}
