package ro.mathlms.quiz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The test list's warning about a wrong answer key, on real Postgres and the real stats cache. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"app.quiz.expiry-job.initial-delay-ms=3600000", "app.quiz.expiry-job.interval-ms=3600000"})
class AnswerKeyChecksIntegrationTest {

    @Autowired private AnswerKeyChecks checks;
    @Autowired private AnswerKeyCorrection correction;
    @Autowired private QuizAdminService adminService;
    @Autowired private QuizAttemptService attemptService;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizOptionRepository optionRepository;
    @Autowired private UserRepository userRepository;

    private Quiz quiz;
    private QuizItem wrongKey;
    private QuizOption markedRight;
    private QuizOption reallyRight;
    private QuizItem other;
    private QuizOption otherRight;
    private QuizOption otherWrong;
    private QuizItem third;
    private QuizOption thirdRight;
    private QuizOption thirdWrong;

    @BeforeEach
    void setUp() {
        Quiz q = new Quiz("Cheie de verificat " + System.nanoTime(), null);
        q.publish();
        quiz = quizRepository.save(q);
        wrongKey = itemRepository.save(new QuizItem(quiz, 0, QuizItemType.SINGLE_CHOICE, "2 + 2 =", 5, null));
        markedRight = optionRepository.save(new QuizOption(wrongKey, 0, "5", true));
        reallyRight = optionRepository.save(new QuizOption(wrongKey, 1, "4", false));
        // Two more questions worth more than the key, so the students strong on them stay the strongest on each.
        other = itemRepository.save(new QuizItem(quiz, 1, QuizItemType.SINGLE_CHOICE, "3 + 3 =", 6, null));
        otherRight = optionRepository.save(new QuizOption(other, 0, "6", true));
        otherWrong = optionRepository.save(new QuizOption(other, 1, "7", false));
        third = itemRepository.save(new QuizItem(quiz, 2, QuizItemType.SINGLE_CHOICE, "4 + 4 =", 6, null));
        thirdRight = optionRepository.save(new QuizOption(third, 0, "8", true));
        thirdWrong = optionRepository.save(new QuizOption(third, 1, "9", false));
    }

    /** A strong student: right on the other two, and chose what is really right on the key. */
    private void handInStrong() {
        handIn(reallyRight, otherRight, thirdRight);
    }

    private void handInWeak() {
        handIn(markedRight, otherWrong, thirdWrong);
    }

    private void handIn(QuizOption answer, QuizOption otherAnswer, QuizOption thirdAnswer) {
        User student = userRepository.save(new User("elev" + System.nanoTime() + "@scoala.ro", "Elev", Role.STUDENT));
        StartedAttemptDto attempt = attemptService.startAttempt(quiz.getId(), student.getEmail());
        attemptService.saveResponse(attempt.attemptId(), wrongKey.getId(), answer.getId(), student.getEmail());
        attemptService.saveResponse(attempt.attemptId(), other.getId(), otherAnswer.getId(), student.getEmail());
        attemptService.saveResponse(attempt.attemptId(), third.getId(), thirdAnswer.getId(), student.getEmail());
        attemptService.submit(attempt.attemptId(), student.getEmail());
    }

    private int toCheck() {
        List<QuizListSummaryDto> rows = checks.withToCheck(adminService.listSummaries());
        return rows.stream().filter(row -> row.quizId().equals(quiz.getId())).findFirst().orElseThrow().toCheck();
    }

    @Test
    void theStrongestStudentsDisagreeingWithTheKeyFlagTheTestUntilTheKeyIsCorrected() {
        for (int i = 0; i < 3; i++) {
            handInStrong();
            handInWeak();
        }

        assertThat(toCheck()).isEqualTo(1);

        correction.correct(wrongKey.getId(), reallyRight.getId());

        assertThat(toCheck()).isZero();
    }

    @Test
    void aTestWithTooFewPapersIsNeverFlagged() {
        for (int i = 0; i < 3; i++) {
            handInStrong();
        }

        assertThat(toCheck()).isZero();
    }
}
