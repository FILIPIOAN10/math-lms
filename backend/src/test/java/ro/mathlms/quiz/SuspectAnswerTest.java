package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.quiz.QuizStatsDtos.SuspectDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SuspectAnswerTest {

    private static final int POINTS = 5;

    private final Quiz quiz = new Quiz("Teză", null);
    private final QuizItem item = withId(new QuizItem(quiz, 0, QuizItemType.SINGLE_CHOICE, "1 + 1 =", POINTS, null), 1L);
    private final QuizOption a = withId(new QuizOption(item, 0, "1", true), 10L); // marked right by mistake
    private final QuizOption b = withId(new QuizOption(item, 1, "2", false), 11L);
    private final QuizOption c = withId(new QuizOption(item, 2, "3", false), 12L);
    private final List<QuizOption> options = List.of(a, b, c);

    private static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static QuizAttemptRepository.CountedAttemptRow row(long attemptId, int score) {
        return new QuizAttemptRepository.CountedAttemptRow() {
            @Override
            public Long getAttemptId() {
                return attemptId;
            }

            @Override
            public Integer getScore() {
                return score;
            }
        };
    }

    /** One student per entry: the option chosen and the points on the rest of the test. */
    private record Student(QuizOption chose, int restOfTest) {
    }

    private Optional<SuspectDto> find(Student... students) {
        List<QuizAttemptRepository.CountedAttemptRow> counted = new ArrayList<>();
        List<OptionChoice> choices = new ArrayList<>();
        long attemptId = 100;
        for (Student student : students) {
            attemptId++;
            boolean right = student.chose() != null && student.chose().isCorrect();
            counted.add(row(attemptId, student.restOfTest() + (right ? POINTS : 0)));
            if (student.chose() != null) {
                choices.add(new OptionChoice(attemptId, item.getId(), student.chose().getId(), right));
            }
        }
        return SuspectAnswer.find(options, choices, counted, POINTS);
    }

    @Test
    void theStrongestStudentsAgreeingOnAnotherOptionIsSuspect() {
        Optional<SuspectDto> suspect = find(new Student(b, 40), new Student(b, 38), new Student(b, 36),
                new Student(a, 12), new Student(a, 10), new Student(c, 8));

        assertThat(suspect).contains(new SuspectDto("b", 3, 3));
    }

    @Test
    void aWrongKeyDoesNotHideTheStudentsItPunishes() {
        // Their totals are lower only because the key took 5 points from them: the rest of the test ranks them.
        Optional<SuspectDto> suspect = find(new Student(b, 20), new Student(b, 20), new Student(b, 20),
                new Student(a, 18), new Student(a, 18), new Student(a, 18));

        assertThat(suspect).contains(new SuspectDto("b", 3, 3));
    }

    @Test
    void aHardQuestionTheStrongestGotRightIsNotSuspect() {
        assertThat(find(new Student(a, 40), new Student(a, 38), new Student(a, 36),
                new Student(b, 12), new Student(b, 10), new Student(b, 8))).isEmpty();
    }

    @Test
    void theStrongestSplitBetweenWrongOptionsIsNotSuspect() {
        assertThat(find(new Student(a, 40), new Student(b, 38), new Student(c, 36),
                new Student(a, 12), new Student(b, 10), new Student(c, 8))).isEmpty();
    }

    @Test
    void aSmallClassProvesNothing() {
        assertThat(find(new Student(b, 40), new Student(b, 38), new Student(b, 36), new Student(a, 12), new Student(a, 10)))
                .isEmpty();
    }

    @Test
    void aGrilaStillWaitingForItsAnswerHasNothingToSuspect() {
        List<QuizOption> noKey = List.of(withId(new QuizOption(item, 0, "1", false), 20L), b);

        assertThat(SuspectAnswer.find(noKey, List.of(),
                List.of(row(1, 1), row(2, 1), row(3, 1), row(4, 1), row(5, 1), row(6, 1)), POINTS)).isEmpty();
    }
}
