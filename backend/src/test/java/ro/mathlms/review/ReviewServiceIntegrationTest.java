package ro.mathlms.review;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.content.Enrollment;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.quiz.AttemptMode;
import ro.mathlms.quiz.ItemResponse;
import ro.mathlms.quiz.ItemResponseRepository;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAttempt;
import ro.mathlms.quiz.QuizAttemptRepository;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.quiz.QuizOption;
import ro.mathlms.quiz.QuizOptionRepository;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.review.ReviewDtos.ReviewQuestionDto;
import ro.mathlms.review.ReviewDtos.ReviewResultDto;
import ro.mathlms.review.ReviewDtos.ReviewStatusDto;
import ro.mathlms.review.ReviewDtos.ReviewTodayDto;
import ro.mathlms.review.ReviewDtos.StudentReviewStatusDto;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * E4 spaced repetition on a real Postgres (V22): the review queue is replayed from the student's multiple-choice answers
 * in practice, in handed-in tests and in earlier reviews - Leitner boxes 1/3/7/14/30 days, at most 10 a day.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ReviewServiceIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Bucharest");

    @Autowired private UserRepository userRepository;
    @Autowired private SchoolClassRepository classRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizOptionRepository optionRepository;
    @Autowired private QuizAttemptRepository attemptRepository;
    @Autowired private ItemResponseRepository responseRepository;
    @Autowired private ReviewAnswerRepository reviewAnswerRepository;

    @Autowired private TransactionTemplate tx;

    /**
     * The service as it is at 10:00 school time on that day. Built by hand to fix the clock, so Spring's
     * {@code @Transactional} proxy is not around it: each call runs in a transaction here instead.
     */
    private At at(LocalDate day) {
        Clock clock = Clock.fixed(day.atTime(10, 0).atZone(ZONE).toInstant(), ZONE);
        return new At(new ReviewService(userRepository, enrollmentRepository, quizRepository, attemptRepository,
                itemRepository, optionRepository, responseRepository, reviewAnswerRepository, clock));
    }

    private final class At {
        private final ReviewService service;

        At(ReviewService service) {
            this.service = service;
        }

        ReviewTodayDto today(String email) {
            return tx.execute(status -> service.today(email));
        }

        ReviewResultDto answer(String email, Long itemId, Long optionId) {
            return tx.execute(status -> service.answer(email, itemId, optionId));
        }

        ReviewStatusDto status(Long studentId) {
            return tx.execute(st -> service.status(studentId));
        }

        List<StudentReviewStatusDto> forClass(Long classId) {
            return tx.execute(st -> service.forClass(classId));
        }
    }

    private static Instant morning(LocalDate day) {
        return day.atTime(9, 0).atZone(ZONE).toInstant();
    }

    private User student(String tag) {
        return userRepository.save(new User("elev." + tag + "@scoala.ro", "Elev " + tag, Role.STUDENT));
    }

    private Quiz quiz(String title, boolean published) {
        Quiz quiz = new Quiz(title, null);
        quiz.allowPractice(true);
        if (published) {
            quiz.publish();
        }
        return quizRepository.save(quiz);
    }

    private QuizItem grila(Quiz quiz, int position) {
        QuizItem item = itemRepository.save(new QuizItem(quiz, position, QuizItemType.SINGLE_CHOICE,
                "Întrebarea " + position, 2, "Rezolvarea " + position));
        optionRepository.save(new QuizOption(item, 0, "corect", true));
        optionRepository.save(new QuizOption(item, 1, "greșit", false));
        return item;
    }

    private QuizOption option(QuizItem item, boolean correct) {
        return optionRepository.findByItemIdOrderByPosition(item.getId()).stream()
                .filter(o -> o.isCorrect() == correct).findFirst().orElseThrow();
    }

    private void answer(QuizAttempt attempt, QuizItem item, boolean correct) {
        ItemResponse response = new ItemResponse(attempt, item);
        response.answerSingleChoice(option(item, correct));
        response.gradeAuto(correct, correct ? item.getPoints() : 0);
        responseRepository.save(response);
    }

    private QuizAttempt practice(Quiz quiz, User who, LocalDate day) {
        return attemptRepository.save(new QuizAttempt(quiz, who, morning(day), AttemptMode.PRACTICE));
    }

    private static List<Long> ids(ReviewTodayDto today) {
        return today.questions().stream().map(ReviewQuestionDto::itemId).toList();
    }

    private static final LocalDate D1 = LocalDate.of(2026, 10, 5);

    @Test
    void practiceAnswersBuildTheQueueAndAnsweringMovesTheBox() {
        User ana = student("r1");
        Quiz quiz = quiz("Fracții r1", true);
        QuizItem q1 = grila(quiz, 0);
        QuizItem q2 = grila(quiz, 1);
        grila(quiz, 2); // never answered: not a card
        QuizAttempt p = practice(quiz, ana, D1);
        answer(p, q1, false); // box 1, due D1+1
        answer(p, q2, true);  // box 2, due D1+3

        ReviewTodayDto sameDay = at(D1).today(ana.getEmail());
        assertThat(sameDay.questions()).isEmpty();
        assertThat(sameDay.totalCards()).isEqualTo(2);

        ReviewTodayDto nextDay = at(D1.plusDays(1)).today(ana.getEmail());
        assertThat(ids(nextDay)).containsExactly(q1.getId());
        ReviewQuestionDto question = nextDay.questions().get(0);
        assertThat(question.statement()).isEqualTo("Întrebarea 0");
        assertThat(question.quizTitle()).isEqualTo("Fracții r1");
        assertThat(question.options()).hasSize(2);
        assertThat(question.box()).isEqualTo(1);

        ReviewResultDto result = at(D1.plusDays(1)).answer(ana.getEmail(), q1.getId(), option(q1, true).getId());
        assertThat(result.correct()).isTrue();
        assertThat(result.correctOptionId()).isEqualTo(option(q1, true).getId());
        assertThat(result.solution()).isEqualTo("Rezolvarea 0");
        assertThat(result.boxBefore()).isEqualTo(1);
        assertThat(result.boxAfter()).isEqualTo(2);
        assertThat(result.nextDueOn()).isEqualTo(D1.plusDays(4));

        ReviewTodayDto after = at(D1.plusDays(1)).today(ana.getEmail());
        assertThat(after.questions()).isEmpty();
        assertThat(after.doneToday()).isEqualTo(1);
        assertThat(after.streak()).isEqualTo(1);
    }

    @Test
    void aQuestionThatIsNotDueTodayCannotBeAnswered() {
        User ana = student("r2");
        Quiz quiz = quiz("Fracții r2", true);
        QuizItem q = grila(quiz, 0);
        answer(practice(quiz, ana, D1), q, true); // due D1+3

        assertThatThrownBy(() -> at(D1.plusDays(1)).answer(ana.getEmail(), q.getId(), option(q, true).getId()))
                .isInstanceOf(InvalidReviewException.class);
        assertThat(reviewAnswerRepository.findByStudentIdIn(List.of(ana.getId()))).isEmpty();
    }

    @Test
    void aHandedInTestCountsButATestStillBeingWrittenDoesNot() {
        User ana = student("r3");
        Quiz quiz = quiz("Teza r3", true);
        QuizItem handedIn = grila(quiz, 0);
        QuizAttempt test = attemptRepository.save(new QuizAttempt(quiz, ana, morning(D1), AttemptMode.TEST));
        answer(test, handedIn, false);
        test.submit();
        ReflectionTestUtils.setField(test, "submittedAt", morning(D1));
        test.markGraded(0);
        attemptRepository.save(test);

        Quiz other = quiz("Teza în lucru r3", true);
        QuizItem writing = grila(other, 0);
        QuizAttempt inProgress = attemptRepository.save(new QuizAttempt(other, ana, morning(D1), AttemptMode.TEST));
        answer(inProgress, writing, false);

        assertThat(ids(at(D1.plusDays(1)).today(ana.getEmail()))).containsExactly(handedIn.getId());
    }

    @Test
    void atMostTenADayOldestFirstAndTheLimitCountsWhatWasAlreadyDone() {
        User ana = student("r4");
        Quiz quiz = quiz("Multe r4", true);
        QuizAttempt p = practice(quiz, ana, D1);
        for (int i = 0; i < 12; i++) {
            answer(p, grila(quiz, i), false);
        }
        ReviewTodayDto today = at(D1.plusDays(1)).today(ana.getEmail());
        assertThat(today.questions()).hasSize(10);
        assertThat(today.remainingToday()).isEqualTo(10);
        assertThat(today.limit()).isEqualTo(10);

        QuizItem first = itemRepository.findById(today.questions().get(0).itemId()).orElseThrow();
        at(D1.plusDays(1)).answer(ana.getEmail(), first.getId(), option(first, false).getId());

        ReviewTodayDto later = at(D1.plusDays(1)).today(ana.getEmail());
        assertThat(later.doneToday()).isEqualTo(1);
        assertThat(later.questions()).hasSize(9); // 11 still due, but 10 a day
    }

    @Test
    void aQuizTheTeacherUnpublishedLeavesTheQueue() {
        User ana = student("r5");
        Quiz quiz = quiz("Retras r5", false);
        answer(practice(quiz, ana, D1), grila(quiz, 0), false);

        assertThat(at(D1.plusDays(1)).today(ana.getEmail()).questions()).isEmpty();
    }

    @Test
    void theStreakCountsConsecutiveDaysWithAReviewAndSurvivesUntilTheDayEnds() {
        User ana = student("r6");
        Quiz quiz = quiz("Serie r6", true);
        QuizItem a = grila(quiz, 0);
        QuizItem b = grila(quiz, 1);
        QuizAttempt p = practice(quiz, ana, D1);
        answer(p, a, false);
        answer(p, b, false);
        at(D1.plusDays(1)).answer(ana.getEmail(), a.getId(), option(a, false).getId()); // a: box 1 again, due D1+2
        at(D1.plusDays(2)).answer(ana.getEmail(), a.getId(), option(a, true).getId());

        assertThat(at(D1.plusDays(2)).today(ana.getEmail()).streak()).isEqualTo(2);
        assertThat(at(D1.plusDays(3)).today(ana.getEmail()).streak()).isEqualTo(2); // not broken before the day ends
        assertThat(at(D1.plusDays(4)).today(ana.getEmail()).streak()).isZero();
    }

    @Test
    void parentsAndTheTeacherSeeTheSameStatus() {
        User ana = student("r7");
        User bob = student("r7b");
        SchoolClass cls = classRepository.save(new SchoolClass("Clasa r7", null));
        enrollmentRepository.save(new Enrollment(ana, cls));
        enrollmentRepository.save(new Enrollment(bob, cls));
        Quiz quiz = quiz("Status r7", true);
        QuizItem q = grila(quiz, 0);
        answer(practice(quiz, ana, D1), q, false);
        at(D1.plusDays(1)).answer(ana.getEmail(), q.getId(), option(q, true).getId());

        ReviewStatusDto status = at(D1.plusDays(1)).status(ana.getId());
        assertThat(status.doneToday()).isEqualTo(1);
        assertThat(status.remainingToday()).isZero();
        assertThat(status.streak()).isEqualTo(1);
        assertThat(status.totalCards()).isEqualTo(1);
        assertThat(status.lastReviewOn()).isEqualTo(D1.plusDays(1));

        List<StudentReviewStatusDto> rows = at(D1.plusDays(1)).forClass(cls.getId());
        assertThat(rows).extracting(StudentReviewStatusDto::fullName).containsExactly("Elev r7", "Elev r7b");
        assertThat(rows.get(0).doneToday()).isEqualTo(1);
        assertThat(rows.get(1).totalCards()).isZero();
    }
}
