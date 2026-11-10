package ro.mathlms.quiz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.assignment.AssignmentService;
import ro.mathlms.content.Enrollment;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultViewDto;
import ro.mathlms.quiz.StudentQuizDtos.ItemResultDto;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The answer key of a handed-in test - the right option, the numeric answer, the barem (of the question and of each
 * requirement) and the note on the wrong option chosen - only once the student cannot hand the test in again: a retake
 * must not copy it. Until then the result says only right or wrong. A test open for practice shows it anyway (each
 * practice answer gives it away). On real Postgres, through the result page every student and parent reads.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"app.quiz.expiry-job.initial-delay-ms=3600000", "app.quiz.expiry-job.interval-ms=3600000"})
class AnswerKeyIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizOptionRepository optionRepository;
    @Autowired private QuizItemPartRepository partRepository;
    @Autowired private SchoolClassRepository schoolClassRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private QuizAttemptService attemptService;
    @Autowired private AssignmentService assignmentService;
    @Autowired private AttemptGrantService grantService;

    private SchoolClass schoolClass;
    private User ana;
    private Quiz quiz;
    private QuizItem grila;
    private QuizItem numeric;

    @BeforeEach
    void setUp() {
        String stamp = String.valueOf(System.nanoTime());
        schoolClass = schoolClassRepository.save(new SchoolClass("Clasa " + stamp, null));
        ana = userRepository.save(new User("ana." + stamp + "@scoala.ro", "Ana Pop", Role.STUDENT));
        enrollmentRepository.save(new Enrollment(ana, schoolClass));
    }

    /** A published test of the class: a grilă, a numeric question and a problem with one requirement, each with a barem. */
    private void test(Integer maxAttempts, boolean practiceAllowed) {
        Quiz q = new Quiz("Simulare " + System.nanoTime(), null);
        q.assignToClass(schoolClass);
        q.limitAttempts(maxAttempts);
        q.allowPractice(practiceAllowed);
        q.publish();
        quiz = quizRepository.save(q);

        grila = itemRepository.save(new QuizItem(quiz, 1, QuizItemType.SINGLE_CHOICE, "Cât face 2 + 2?", 5,
                "Aduni cele două numere."));
        optionRepository.save(new QuizOption(grila, 0, "4", true));
        QuizOption wrong = new QuizOption(grila, 1, "5", false);
        wrong.explainMistake("Ai numărat un 1 în plus.");
        optionRepository.save(wrong);

        QuizItem n = new QuizItem(quiz, 2, QuizItemType.NUMERIC, "Cât face 1/2 + 1/4?", 5, "Aduci la numitorul 4.");
        n.expectNumber("0,75");
        numeric = itemRepository.save(n);

        QuizItem problem = itemRepository.save(new QuizItem(quiz, 3, QuizItemType.OPEN, "Rezolvă ecuația.", 10,
                "Barem problemă"));
        partRepository.save(new QuizItemPart(problem, 0, "Află x.", 10, "Barem a)"));
    }

    private Long option(boolean right) {
        return optionRepository.findByItemIdOrderByPosition(grila.getId()).stream()
                .filter(o -> o.isCorrect() == right).findFirst().orElseThrow().getId();
    }

    /** Ana writes the test - the grilă and the number wrong, the problem left for the teacher - and hands it in. */
    private Long handIn() {
        Long attempt = attemptService.startAttempt(quiz.getId(), ana.getEmail()).attemptId();
        attemptService.saveResponse(attempt, grila.getId(), option(false), ana.getEmail());
        attemptService.saveNumericResponse(attempt, numeric.getId(), "1", null, null, ana.getEmail());
        attemptService.submit(attempt, ana.getEmail());
        return attempt;
    }

    private AttemptResultViewDto result(Long attempt) {
        return attemptService.getResult(attempt, ana.getEmail());
    }

    private static ItemResultDto item(AttemptResultViewDto result, int position) {
        return result.items().stream().filter(i -> i.position() == position).findFirst().orElseThrow();
    }

    /** Right or wrong and what the student wrote stay; nothing of the key does. */
    private static void assertKeyHidden(AttemptResultViewDto result) {
        assertThat(result.answerKeyHidden()).isTrue();
        ItemResultDto g = item(result, 1);
        assertThat(g.correct()).isFalse();
        assertThat(g.selectedOptionText()).isEqualTo("5");
        assertThat(g.selectedOptionLetter()).isEqualTo("b");
        assertThat(g.correctOptionText()).isNull();
        assertThat(g.correctOptionLetter()).isNull();
        assertThat(g.barem()).isNull();
        assertThat(g.mistake()).isNull();
        ItemResultDto n = item(result, 2);
        assertThat(n.correct()).isFalse();
        assertThat(n.selectedOptionText()).isEqualTo("1");
        assertThat(n.correctOptionText()).isNull();
        assertThat(n.barem()).isNull();
        ItemResultDto p = item(result, 3);
        assertThat(p.barem()).isNull();
        assertThat(p.parts()).hasSize(1);
        assertThat(p.parts().get(0).statement()).isEqualTo("Află x.");
        assertThat(p.parts().get(0).barem()).isNull();
    }

    private static void assertKeyShown(AttemptResultViewDto result) {
        assertThat(result.answerKeyHidden()).isFalse();
        ItemResultDto g = item(result, 1);
        assertThat(g.correctOptionText()).isEqualTo("4");
        assertThat(g.correctOptionLetter()).isEqualTo("a");
        assertThat(g.barem()).isEqualTo("Aduni cele două numere.");
        assertThat(g.mistake()).isEqualTo("Ai numărat un 1 în plus.");
        ItemResultDto n = item(result, 2);
        assertThat(n.correctOptionText()).isEqualTo("0,75");
        assertThat(n.barem()).isEqualTo("Aduci la numitorul 4.");
        ItemResultDto p = item(result, 3);
        assertThat(p.barem()).isEqualTo("Barem problemă");
        assertThat(p.parts().get(0).barem()).isEqualTo("Barem a)");
    }

    @Test
    void aRetakeLeftShowsOnlyRightOrWrong() {
        test(2, false);

        assertKeyHidden(result(handIn()));
    }

    @Test
    void theLastAttemptShowsTheWholeKey() {
        test(1, false);

        assertKeyShown(result(handIn()));
    }

    @Test
    void aTestWithoutALimitKeepsItsKey() {
        test(null, false);

        assertKeyHidden(result(handIn()));
    }

    @Test
    void aTestOpenForPracticeShowsTheKeyAnyway() {
        test(2, true);

        assertKeyShown(result(handIn()));
    }

    @Test
    void plusOneAttemptHidesTheKeyAgain() {
        test(1, false);
        Long attempt = handIn();
        assertKeyShown(result(attempt));

        grantService.grantOne(attempt);

        assertKeyHidden(result(attempt));
    }

    @Test
    void aCatchUpRedoHidesTheKeyUntilTheRedoIsHandedIn() {
        test(1, false);
        Long first = handIn();
        assertKeyShown(result(first));

        assignmentService.createForStudents(quiz.getId(), schoolClass.getId(), List.of(ana.getId()),
                Instant.now().plus(Duration.ofDays(3)));

        assertKeyHidden(result(first));
        Long redo = handIn();
        assertKeyShown(result(redo));
        assertKeyShown(result(first)); // the key belongs to the test, not to one paper
    }
}
