package ro.mathlms.quiz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * The server-side timer end to end on real Postgres, with a clock the test moves by hand. Covers what the mocked unit
 * tests cannot: the SQL of the overdue lookup, the row lock, the {@code @Transactional} proxy on the job's entry
 * point, and the flush order that lets a fresh attempt be inserted right after the expired one is handed in
 * (the database allows only one IN_PROGRESS attempt per student and quiz).
 */
@Import({TestcontainersConfiguration.class, QuizTimerIntegrationTest.ClockOverride.class})
// The scheduler is pushed an hour out; the test ticks the job itself, at the moments it chooses.
@SpringBootTest(properties = {"app.quiz.expiry-job.initial-delay-ms=3600000", "app.quiz.expiry-job.interval-ms=3600000"})
class QuizTimerIntegrationTest {

    /** A clock that starts at the real time and only moves when the test says so. */
    static final class SettableClock extends Clock {
        volatile Instant now = Instant.now();

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @TestConfiguration
    static class ClockOverride {
        @Bean
        @Primary
        SettableClock testClock() {
            return new SettableClock();
        }
    }

    @Autowired private SettableClock clock;
    @Autowired private UserRepository userRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizOptionRepository optionRepository;
    @Autowired private QuizAttemptRepository attemptRepository;
    @Autowired private QuizAttemptService service;
    @Autowired private AttemptExpiryJob job;

    private User student;
    private Quiz quiz;
    private QuizItem item;
    private QuizOption right;

    @BeforeEach
    void setUp() {
        clock.now = Instant.now();
        String stamp = String.valueOf(System.nanoTime());
        student = userRepository.save(new User("elev." + stamp + "@scoala.ro", "Ana Pop", Role.STUDENT));
        Quiz q = new Quiz("Cronometrat " + stamp, null);
        q.changeTimeLimit(10);
        q.publish();
        quiz = quizRepository.save(q);
        item = itemRepository.save(new QuizItem(quiz, 1, QuizItemType.SINGLE_CHOICE, "s", 5, null));
        right = optionRepository.save(new QuizOption(item, 0, "A", true));
        optionRepository.save(new QuizOption(item, 1, "B", false));
    }

    private QuizAttempt reload(Long attemptId) {
        return attemptRepository.findById(attemptId).orElseThrow();
    }

    @Test
    void theDeadlineIsStoredAndReturnedToTheBrowser() {
        StartedAttemptDto started = service.startAttempt(quiz.getId(), student.getEmail());

        assertThat(started.quiz().timeLimitMinutes()).isEqualTo(10);
        // Postgres keeps microseconds, Instant has nanoseconds: equal to within a millisecond is exact enough
        assertThat(started.deadlineAt()).isCloseTo(reload(started.attemptId()).getDeadlineAt(), within(1, ChronoUnit.MILLIS));
        assertThat(started.deadlineAt()).isAfter(started.serverNow());
    }

    @Test
    void afterTheDeadlineAnswersAreRejectedAndTheJobGradesWhatWasSaved() {
        StartedAttemptDto started = service.startAttempt(quiz.getId(), student.getEmail());
        service.saveResponse(started.attemptId(), item.getId(), right.getId(), student.getEmail());

        clock.now = started.deadlineAt().plus(QuizAttemptService.SUBMIT_GRACE).plusSeconds(1);

        assertThatThrownBy(() -> service.saveResponse(started.attemptId(), item.getId(), right.getId(), student.getEmail()))
                .isInstanceOf(AttemptExpiredException.class);

        job.tick();

        QuizAttempt handedIn = reload(started.attemptId());
        assertThat(handedIn.getStatus()).isEqualTo(QuizAttemptStatus.GRADED);
        assertThat(handedIn.getScore()).isEqualTo(5);

        job.tick(); // a second pass finds nothing left to do
        assertThat(reload(started.attemptId()).getScore()).isEqualTo(5);
    }

    @Test
    void theJobLeavesAnAttemptAloneUntilTheGracePeriodIsOver() {
        StartedAttemptDto started = service.startAttempt(quiz.getId(), student.getEmail());
        clock.now = started.deadlineAt().plusSeconds(5); // past the deadline, inside the grace

        job.tick();

        assertThat(reload(started.attemptId()).getStatus()).isEqualTo(QuizAttemptStatus.IN_PROGRESS);
    }

    @Test
    void comingBackToAnExpiredAttemptHandsItInAndStartsAFreshOne() {
        StartedAttemptDto first = service.startAttempt(quiz.getId(), student.getEmail());
        service.saveResponse(first.attemptId(), item.getId(), right.getId(), student.getEmail());
        clock.now = first.deadlineAt().plus(QuizAttemptService.SUBMIT_GRACE).plusSeconds(1);

        StartedAttemptDto second = service.startAttempt(quiz.getId(), student.getEmail());

        assertThat(second.attemptId()).isNotEqualTo(first.attemptId());
        assertThat(reload(first.attemptId()).getStatus()).isEqualTo(QuizAttemptStatus.GRADED);
        assertThat(reload(first.attemptId()).getScore()).isEqualTo(5);
        assertThat(second.answers()).isEmpty();                    // a clean sheet
        assertThat(second.deadlineAt()).isAfter(second.serverNow()); // with its own full time
    }

    @Test
    void aLateSubmitOfAnExpiredAttemptStillCountsWhatWasSavedInTime() {
        StartedAttemptDto started = service.startAttempt(quiz.getId(), student.getEmail());
        service.saveResponse(started.attemptId(), item.getId(), right.getId(), student.getEmail());
        clock.now = started.deadlineAt().plus(Duration.ofHours(3));

        service.submit(started.attemptId(), student.getEmail());

        assertThat(reload(started.attemptId()).getScore()).isEqualTo(5);
    }

    @Test
    void expiredIsReportedToTheBrowserAsConflict() {
        var response = new QuizExceptionHandler().handleExpired(new AttemptExpiredException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
