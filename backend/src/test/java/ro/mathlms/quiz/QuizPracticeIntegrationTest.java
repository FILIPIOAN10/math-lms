package ro.mathlms.quiz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.quiz.QuizStatsDtos.QuizStatsDto;
import ro.mathlms.quiz.StudentQuizDtos.AnswerFeedbackDto;
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultViewDto;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E2 end to end on real Postgres + Redis: a student practises a quiz (immediate feedback, no grade), then sits the
 * graded test of the very same quiz - and the practice leaves no trace in the progress chart or the teacher's stats.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"app.quiz.expiry-job.initial-delay-ms=3600000", "app.quiz.expiry-job.interval-ms=3600000"})
class QuizPracticeIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizOptionRepository optionRepository;
    @Autowired private QuizAttemptService service;
    @Autowired private QuizStatsService statsService;

    private User student;
    private Quiz quiz;
    private QuizItem item;
    private QuizOption right;
    private QuizOption wrong;

    @BeforeEach
    void setUp() {
        String stamp = String.valueOf(System.nanoTime());
        student = userRepository.save(new User("elev." + stamp + "@scoala.ro", "Ana Pop", Role.STUDENT));
        Quiz q = new Quiz("Practica " + stamp, null);
        q.changeTimeLimit(30);          // timed test ...
        q.allowPractice(true);          // ... that can also be practised, without the clock
        q.publish();
        quiz = quizRepository.save(q);
        item = itemRepository.save(new QuizItem(quiz, 1, QuizItemType.SINGLE_CHOICE, "Cat e $2+2$?", 5, "2+2=4"));
        right = optionRepository.save(new QuizOption(item, 0, "4", true));
        wrong = optionRepository.save(new QuizOption(item, 1, "5", false));
    }

    @Test
    void practiseWithFeedbackThenSitTheTestWithoutAnyLeak() {
        // --- practice: no clock, instant feedback, answers can be changed ---
        StartedAttemptDto practice = service.startAttempt(quiz.getId(), student.getEmail(), AttemptMode.PRACTICE);
        assertThat(practice.mode()).isEqualTo(AttemptMode.PRACTICE);
        assertThat(practice.deadlineAt()).isNull();

        AnswerFeedbackDto first = service.saveResponse(practice.attemptId(), item.getId(), wrong.getId(), student.getEmail()).orElseThrow();
        assertThat(first.correct()).isFalse();
        assertThat(first.correctOptionId()).isEqualTo(right.getId());
        assertThat(first.solution()).isEqualTo("2+2=4");

        AnswerFeedbackDto second = service.saveResponse(practice.attemptId(), item.getId(), right.getId(), student.getEmail()).orElseThrow();
        assertThat(second.correct()).isTrue();

        // a practice that is resumed (page reloaded) shows the same feedback again
        StartedAttemptDto resumed = service.startAttempt(quiz.getId(), student.getEmail(), AttemptMode.PRACTICE);
        assertThat(resumed.attemptId()).isEqualTo(practice.attemptId());
        assertThat(resumed.answers()).singleElement().satisfies(a -> assertThat(a.feedback().correct()).isTrue());

        service.submit(practice.attemptId(), student.getEmail());
        AttemptResultViewDto practiceResult = service.getResult(practice.attemptId(), student.getEmail());
        assertThat(practiceResult.mode()).isEqualTo(AttemptMode.PRACTICE);
        assertThat(practiceResult.finalScore()).isNull();                       // "fara nota"

        // --- the practice left no trace in the graded world ---
        assertThat(service.getProgress(student.getEmail())).isEmpty();
        assertThat(statsService.getStats(quiz.getId()).gradedAttempts()).isZero();

        // --- the graded test of the same quiz: timed, and it reveals nothing while open ---
        StartedAttemptDto test = service.startAttempt(quiz.getId(), student.getEmail());
        assertThat(test.mode()).isEqualTo(AttemptMode.TEST);
        assertThat(test.deadlineAt()).isNotNull();
        assertThat(test.answers()).isEmpty();                                   // the practice answers did not carry over
        // the practice ended on the RIGHT answer; the test deliberately answers WRONG, so a leak would show as 50% right
        assertThat(service.saveResponse(test.attemptId(), item.getId(), wrong.getId(), student.getEmail())).isEmpty();
        service.submit(test.attemptId(), student.getEmail());

        assertThat(service.getProgress(student.getEmail())).singleElement()
                .satisfies(p -> assertThat(p.score()).isZero());
        QuizStatsDto stats = statsService.getStats(quiz.getId());
        assertThat(stats.gradedAttempts()).isEqualTo(1);
        assertThat(stats.items()).singleElement().satisfies(i -> assertThat(i.correctRate()).isZero()); // only the test counts

        // --- and the student can practise again, as often as they like ---
        StartedAttemptDto again = service.startAttempt(quiz.getId(), student.getEmail(), AttemptMode.PRACTICE);
        assertThat(again.attemptId()).isNotEqualTo(practice.attemptId());
        assertThat(service.listMyAttempts(student.getEmail())).extracting(a -> a.mode())
                .containsExactlyInAnyOrder(AttemptMode.PRACTICE, AttemptMode.TEST, AttemptMode.PRACTICE);
    }

    @Test
    void aTestAndAPracticeCanBeOpenAtTheSameTime() {
        StartedAttemptDto test = service.startAttempt(quiz.getId(), student.getEmail());
        StartedAttemptDto practice = service.startAttempt(quiz.getId(), student.getEmail(), AttemptMode.PRACTICE);

        assertThat(practice.attemptId()).isNotEqualTo(test.attemptId());
        // each mode resumes its own
        assertThat(service.startAttempt(quiz.getId(), student.getEmail()).attemptId()).isEqualTo(test.attemptId());
        assertThat(service.startAttempt(quiz.getId(), student.getEmail(), AttemptMode.PRACTICE).attemptId()).isEqualTo(practice.attemptId());
    }

    @Test
    void practiceIsRefusedOnAQuizTheTeacherDidNotOpenForIt() {
        quiz.allowPractice(false);
        quizRepository.save(quiz);

        assertThatThrownBy(() -> service.startAttempt(quiz.getId(), student.getEmail(), AttemptMode.PRACTICE))
                .isInstanceOf(InvalidQuizException.class);
        // the graded test of that quiz is untouched
        assertThat(service.startAttempt(quiz.getId(), student.getEmail()).mode()).isEqualTo(AttemptMode.TEST);
    }
}
