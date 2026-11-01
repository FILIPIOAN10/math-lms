package ro.mathlms.quiz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEntryRepository;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A wrong answer key corrected after hand-in, on real Postgres: every paper is marked again and the totals follow. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"app.quiz.expiry-job.initial-delay-ms=3600000", "app.quiz.expiry-job.interval-ms=3600000"})
class AnswerKeyCorrectionIntegrationTest {

    @Autowired private AnswerKeyCorrection correction;
    @Autowired private QuizAttemptService attemptService;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizOptionRepository optionRepository;
    @Autowired private QuizAttemptRepository attemptRepository;
    @Autowired private ItemResponseRepository responseRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditEntryRepository auditRepository;

    private QuizItem wrongKey;
    private QuizOption markedRight;
    private QuizOption reallyRight;
    private QuizItem other;
    private QuizOption otherRight;

    @BeforeEach
    void setUp() {
        Quiz q = new Quiz("Cheie " + System.nanoTime(), null);
        q.publish();
        Quiz quiz = quizRepository.save(q);
        wrongKey = itemRepository.save(new QuizItem(quiz, 0, QuizItemType.SINGLE_CHOICE, "2 + 2 =", 5, null));
        markedRight = optionRepository.save(new QuizOption(wrongKey, 0, "5", true));
        reallyRight = optionRepository.save(new QuizOption(wrongKey, 1, "4", false));
        other = itemRepository.save(new QuizItem(quiz, 1, QuizItemType.SINGLE_CHOICE, "3 + 3 =", 3, null));
        otherRight = optionRepository.save(new QuizOption(other, 0, "6", true));
        optionRepository.save(new QuizOption(other, 1, "7", false));
    }

    private Long handIn(QuizOption answer) {
        User student = userRepository.save(new User("elev" + System.nanoTime() + "@scoala.ro", "Elev", Role.STUDENT));
        StartedAttemptDto attempt = attemptService.startAttempt(wrongKey.getQuiz().getId(), student.getEmail());
        attemptService.saveResponse(attempt.attemptId(), wrongKey.getId(), answer.getId(), student.getEmail());
        attemptService.saveResponse(attempt.attemptId(), other.getId(), otherRight.getId(), student.getEmail());
        attemptService.submit(attempt.attemptId(), student.getEmail());
        attemptService.markResultSeen(attempt.attemptId(), student.getEmail());
        return attempt.attemptId();
    }

    private QuizAttempt attempt(Long id) {
        return attemptRepository.findById(id).orElseThrow();
    }

    private ItemResponse response(Long attemptId) {
        return responseRepository.findByAttemptIdAndItemId(attemptId, wrongKey.getId()).orElseThrow();
    }

    @Test
    void everyPaperIsMarkedAgainAndTheNewGradeIsNews() {
        Long gainsPoints = handIn(reallyRight);
        Long losesPoints = handIn(markedRight);

        assertThat(correction.correct(wrongKey.getId(), reallyRight.getId()).changedGrades()).isEqualTo(2);

        assertThat(attempt(gainsPoints).getScore()).isEqualTo(8);
        assertThat(attempt(losesPoints).getScore()).isEqualTo(3);
        assertThat(attempt(gainsPoints).hasUnseenResult()).isTrue();
        assertThat(response(gainsPoints).getCorrect()).isTrue();
        assertThat(response(gainsPoints).getAwardedPoints()).isEqualTo(5);
        assertThat(response(losesPoints).getCorrect()).isFalse();
        assertThat(response(losesPoints).getAwardedPoints()).isZero();
        assertThat(optionRepository.findByItemIdOrderByPosition(wrongKey.getId()))
                .extracting(QuizOption::isCorrect).containsExactly(false, true);
    }

    @Test
    void aGradeTheNewKeyDoesNotTouchStaysAsItWas() {
        QuizOption third = optionRepository.save(new QuizOption(wrongKey, 2, "6", false));
        Long unchanged = handIn(third); // wrong before, wrong after

        assertThat(correction.correct(wrongKey.getId(), reallyRight.getId()).changedGrades()).isZero();

        assertThat(attempt(unchanged).getScore()).isEqualTo(3);
        assertThat(attempt(unchanged).hasUnseenResult()).isFalse();
    }

    @Test
    void aTestHandedInAfterTheCorrectionIsMarkedByTheNewKey() {
        correction.correct(wrongKey.getId(), reallyRight.getId());

        assertThat(attempt(handIn(reallyRight)).getScore()).isEqualTo(8);
    }

    @Test
    void theJournalSaysWhatChanged() {
        handIn(reallyRight);

        correction.correct(wrongKey.getId(), reallyRight.getId());

        assertThat(auditRepository.findAll())
                .filteredOn(row -> row.getAction() == AuditAction.ANSWER_KEY_CORRECTED)
                .anySatisfy(row -> assertThat(row.getDetails()).isEqualTo("răspuns corect a) → b), 1 notă schimbată"));
    }

    @Test
    void choosingTheSameAnswerChangesNothing() {
        Long paper = handIn(markedRight);

        assertThat(correction.correct(wrongKey.getId(), markedRight.getId()).changedGrades()).isZero();

        assertThat(attempt(paper).getScore()).isEqualTo(8);
    }

    @Test
    void anOptionOfAnotherQuestionIsRefused() {
        assertThatThrownBy(() -> correction.correct(wrongKey.getId(), otherRight.getId()))
                .isInstanceOf(InvalidQuizException.class);
    }

    @Test
    void anOpenQuestionHasNoKeyToCorrect() {
        QuizItem open = itemRepository.save(new QuizItem(wrongKey.getQuiz(), 2, QuizItemType.OPEN, "Rezolvă", 5, null));

        assertThatThrownBy(() -> correction.correct(open.getId(), reallyRight.getId()))
                .isInstanceOf(InvalidQuizException.class);
    }
}
