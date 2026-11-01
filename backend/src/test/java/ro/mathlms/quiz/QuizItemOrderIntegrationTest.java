package ro.mathlms.quiz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.quiz.QuizDtos.ItemDto;
import ro.mathlms.quiz.QuizDtos.QuizDetailDto;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Moving and duplicating questions on real Postgres: the order holds, and a copy keeps everything but its identity. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"app.quiz.expiry-job.initial-delay-ms=3600000", "app.quiz.expiry-job.interval-ms=3600000"})
class QuizItemOrderIntegrationTest {

    private static final String FIRST = "SUBIECTUL I";
    private static final String SECOND = "SUBIECTUL al II-lea";

    @Autowired private QuizAdminService service;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizOptionRepository optionRepository;
    @Autowired private QuizItemHintRepository hintRepository;
    @Autowired private QuizItemPartRepository partRepository;
    @Autowired private QuizAttemptService attemptService;
    @Autowired private UserRepository userRepository;

    private Quiz quiz;
    private QuizItem a;
    private QuizItem b;
    private QuizItem c;

    @BeforeEach
    void setUp() {
        Quiz q = new Quiz("Ordine " + System.nanoTime(), null);
        q.publish();
        quiz = quizRepository.save(q);
        a = item("a", 0, FIRST);
        b = item("b", 1, FIRST);
        c = item("c", 2, SECOND);
    }

    private QuizItem item(String statement, int position, String section) {
        QuizItem item = new QuizItem(quiz, position, QuizItemType.OPEN, statement, 5, "barem " + statement);
        item.placeInSection(section);
        return itemRepository.save(item);
    }

    private List<String> order() {
        return service.getQuizDetail(quiz.getId()).items().stream().map(ItemDto::statement).toList();
    }

    private void takeTheTest() {
        User student = userRepository.save(new User("elev" + System.nanoTime() + "@scoala.ro", "Elev", Role.STUDENT));
        attemptService.startAttempt(quiz.getId(), student.getEmail());
    }

    @Test
    void aQuestionMovesUpAndDownInsideItsSection() {
        service.moveItem(b.getId(), true);
        assertThat(order()).containsExactly("b", "a", "c");

        service.moveItem(b.getId(), false);
        assertThat(order()).containsExactly("a", "b", "c");
    }

    @Test
    void aQuestionStopsAtTheEdgeOfItsSection() {
        assertThatThrownBy(() -> service.moveItem(b.getId(), false)).isInstanceOf(InvalidQuizException.class);
        assertThatThrownBy(() -> service.moveItem(c.getId(), true)).isInstanceOf(InvalidQuizException.class);
        assertThatThrownBy(() -> service.moveItem(a.getId(), true)).isInstanceOf(InvalidQuizException.class);

        assertThat(order()).containsExactly("a", "b", "c");
    }

    @Test
    void aCopySitsRightUnderItsOriginalWithEverythingItHad() {
        optionRepository.save(new QuizOption(a, 0, "varianta", true));
        hintRepository.save(new QuizItemHint(a, 1, "indiciu"));
        partRepository.save(new QuizItemPart(a, 0, "cerința", 5, "barem cerință"));

        service.duplicateItem(a.getId());

        QuizDetailDto detail = service.getQuizDetail(quiz.getId());
        assertThat(detail.items()).extracting(ItemDto::statement).containsExactly("a", "a", "b", "c");
        ItemDto original = detail.items().get(0);
        ItemDto copy = detail.items().get(1);
        assertThat(copy.id()).isNotEqualTo(original.id());
        assertThat(copy.section()).isEqualTo(FIRST);
        assertThat(copy.solution()).isEqualTo("barem a");
        assertThat(copy.options()).extracting(QuizDtos.OptionDto::text).containsExactly("varianta");
        assertThat(copy.hints()).containsExactly("indiciu");
        assertThat(copy.parts()).hasSize(1);
        assertThat(copy.options().get(0).id()).isNotEqualTo(original.options().get(0).id());
    }

    @Test
    void aCopyTakesNothingFromItsOriginalWhenChangedLater() {
        service.duplicateItem(b.getId());
        ItemDto copy = service.getQuizDetail(quiz.getId()).items().get(2);

        service.deleteItem(copy.id());

        assertThat(order()).containsExactly("a", "b", "c");
    }

    @Test
    void aTestStudentsTookKeepsItsOrderAndItsQuestions() {
        takeTheTest();

        assertThatThrownBy(() -> service.moveItem(b.getId(), true))
                .isInstanceOf(QuizInUseException.class)
                .hasMessageContaining("ordinea");
        assertThatThrownBy(() -> service.duplicateItem(b.getId())).isInstanceOf(QuizInUseException.class);

        assertThat(order()).containsExactly("a", "b", "c");
        assertThat(service.getQuizDetail(quiz.getId()).taken()).isTrue();
    }
}
