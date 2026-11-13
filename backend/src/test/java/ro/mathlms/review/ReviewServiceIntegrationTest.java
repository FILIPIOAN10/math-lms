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
import ro.mathlms.enchapter.EnChapter;
import ro.mathlms.enchapter.EnChapterRepository;
import ro.mathlms.quiz.AttemptMode;
import ro.mathlms.quiz.EnChapterLinks;
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
    @Autowired private ro.mathlms.quiz.QuizItemHintRepository hintRepository;
    @Autowired private QuizAttemptRepository attemptRepository;
    @Autowired private ItemResponseRepository responseRepository;
    @Autowired private ReviewAnswerRepository reviewAnswerRepository;
    @Autowired private EnChapterLinks enChapterLinks;
    @Autowired private EnChapterRepository enChapterRepository;

    @Autowired private TransactionTemplate tx;

    /**
     * The service as it is at 10:00 school time on that day. Built by hand to fix the clock, so Spring's
     * {@code @Transactional} proxy is not around it: each call runs in a transaction here instead.
     */
    private At at(LocalDate day) {
        Clock clock = Clock.fixed(day.atTime(10, 0).atZone(ZONE).toInstant(), ZONE);
        return new At(new ReviewService(userRepository, enrollmentRepository, quizRepository, attemptRepository,
                itemRepository, optionRepository, responseRepository, reviewAnswerRepository, enChapterLinks, clock,
                hintRepository));
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

        List<ReviewQuestionDto> extra(String email, List<Long> seen) {
            return tx.execute(st -> service.extra(email, seen));
        }

        ReviewResultDto answerExtra(String email, Long itemId, Long optionId) {
            return tx.execute(st -> service.answerExtra(email, itemId, optionId));
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

    /** A published test closed to practice, with this many attempts (null = no limit). */
    private Quiz testWithAttempts(String title, Integer maxAttempts) {
        Quiz quiz = new Quiz(title, null);
        quiz.limitAttempts(maxAttempts);
        quiz.publish();
        return quizRepository.save(quiz);
    }

    private void handIn(Quiz quiz, User who, QuizItem item, boolean correct) {
        QuizAttempt test = attemptRepository.save(new QuizAttempt(quiz, who, morning(D1), AttemptMode.TEST));
        answer(test, item, correct);
        test.submit();
        ReflectionTestUtils.setField(test, "submittedAt", morning(D1));
        test.markGraded(correct ? item.getPoints() : 0);
        attemptRepository.save(test);
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
    void aWrongReviewAnswerNamesTheLessonAndARightOneDoesNot() {
        User ana = student("r-lesson");
        Quiz quiz = quiz("Fracții r-lesson", true);
        QuizItem wrongNow = grila(quiz, 0);
        QuizItem rightNow = grila(quiz, 1);
        EnChapter g1 = enChapterRepository.findAllByOrderByPositionAscIdAsc().stream()
                .filter(c -> c.getCode().equals("G1")).findFirst().orElseThrow();
        wrongNow.pointToEnChapter(g1);
        rightNow.pointToEnChapter(g1);
        itemRepository.saveAll(List.of(wrongNow, rightNow));
        QuizAttempt p = practice(quiz, ana, D1);
        answer(p, wrongNow, false);
        answer(p, rightNow, false);

        ReviewResultDto wrong = at(D1.plusDays(1)).answer(ana.getEmail(), wrongNow.getId(), option(wrongNow, false).getId());
        ReviewResultDto right = at(D1.plusDays(1)).answer(ana.getEmail(), rightNow.getId(), option(rightNow, true).getId());

        assertThat(wrong.enChapter().code()).isEqualTo("G1");
        assertThat(wrong.enChapter().ready()).isTrue();
        assertThat(wrong.enChapter().revisionTopic()).isEqualTo("simetrie și segmente");
        assertThat(right.enChapter()).isNull();
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

    /** A test the student may still hand in again keeps its key: its grile wait for the last attempt (or practice). */
    @Test
    void aHandedInTestWithARetakeLeftWaitsForItsLastAttempt() {
        User ana = student("r-retake");
        Quiz twice = testWithAttempts("Teza r-retake 2", 2);
        QuizItem hidden = grila(twice, 0);
        handIn(twice, ana, hidden, false);
        Quiz unlimited = testWithAttempts("Teza r-retake nelimitat", null);
        QuizItem alsoHidden = grila(unlimited, 0);
        handIn(unlimited, ana, alsoHidden, false);
        Quiz once = testWithAttempts("Teza r-retake 1", 1);
        QuizItem shown = grila(once, 0);
        handIn(once, ana, shown, false);

        assertThat(ids(at(D1.plusDays(1)).today(ana.getEmail()))).containsExactly(shown.getId());
        assertThatThrownBy(() -> at(D1.plusDays(1)).answer(ana.getEmail(), hidden.getId(), option(hidden, true).getId()))
                .isInstanceOf(InvalidReviewException.class);

        handIn(twice, ana, hidden, false); // the last attempt

        assertThat(ids(at(D1.plusDays(1)).today(ana.getEmail()))).containsExactlyInAnyOrder(shown.getId(), hidden.getId());
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

    // --- "Încă 5" ---

    @Test
    void extraQuestionsComeOnlyOnceTodayIsDoneWeakestFirstAndSkipTheSeenOnes() {
        User ana = student("x1");
        Quiz quiz = quiz("Fracții x1", true);
        QuizItem wrong = grila(quiz, 0);
        List<QuizItem> right = java.util.stream.IntStream.rangeClosed(1, 6).mapToObj(n -> grila(quiz, n)).toList();
        QuizAttempt p = practice(quiz, ana, D1);
        answer(p, wrong, false);              // box 1, due tomorrow
        right.forEach(q -> answer(p, q, true)); // box 2, due in 3 days

        // Tomorrow the wrong one is due: today's queue first, no extra yet.
        LocalDate d2 = D1.plusDays(1);
        assertThat(at(d2).extra(ana.getEmail(), List.of())).isEmpty();
        at(d2).answer(ana.getEmail(), wrong.getId(), option(wrong, true).getId());

        List<ReviewQuestionDto> first = at(d2).extra(ana.getEmail(), List.of());
        assertThat(first).hasSize(5).extracting(ReviewQuestionDto::box).containsOnly(2);
        assertThat(first).extracting(ReviewQuestionDto::itemId).doesNotContain(wrong.getId());
        List<Long> seen = first.stream().map(ReviewQuestionDto::itemId).toList();
        // The just-reviewed card (now box 2, due later) and the one not shown yet are what is left.
        assertThat(at(d2).extra(ana.getEmail(), seen)).extracting(ReviewQuestionDto::itemId).hasSize(2)
                .doesNotContainAnyElementsOf(seen);
    }

    @Test
    void anExtraAnswerIsCheckedButMovesNothing() {
        User ana = student("x2");
        Quiz quiz = quiz("Fracții x2", true);
        QuizItem q = grila(quiz, 0);
        answer(practice(quiz, ana, D1), q, true); // box 2, due D1+3

        ReviewResultDto wrong = at(D1).answerExtra(ana.getEmail(), q.getId(), option(q, false).getId());
        assertThat(wrong.correct()).isFalse();
        assertThat(wrong.correctOptionId()).isEqualTo(option(q, true).getId());
        assertThat(wrong.boxBefore()).isEqualTo(2);
        assertThat(wrong.boxAfter()).isEqualTo(2);
        assertThat(wrong.nextDueOn()).isEqualTo(D1.plusDays(3));

        // Nothing stored: not today's count, not the streak, and the card is not due any earlier.
        ReviewTodayDto today = at(D1).today(ana.getEmail());
        assertThat(today.doneToday()).isZero();
        assertThat(today.streak()).isZero();
        assertThat(ids(at(D1.plusDays(2)).today(ana.getEmail()))).isEmpty();
        assertThat(ids(at(D1.plusDays(3)).today(ana.getEmail()))).containsExactly(q.getId());
    }

    @Test
    void anExtraAnswerOnlyForOnesOwnCards() {
        User ana = student("x3");
        User bob = student("x4");
        Quiz quiz = quiz("Fracții x3", true);
        QuizItem q = grila(quiz, 0);
        answer(practice(quiz, ana, D1), q, true);

        assertThatThrownBy(() -> at(D1).answerExtra(bob.getEmail(), q.getId(), option(q, true).getId()))
                .isInstanceOf(InvalidReviewException.class);
    }

    // ---------------------------------------------------------------- mixed order

    @Test
    void todaysQuestionsComeMixedNotTestByTest() {
        User ana = student("m1");
        Quiz fractii = quiz("Fracții m1", true);
        Quiz ecuatii = quiz("Ecuații m1", true);
        List<QuizItem> f = java.util.stream.IntStream.range(0, 3).mapToObj(n -> grila(fractii, n)).toList();
        List<QuizItem> e = java.util.stream.IntStream.range(0, 3).mapToObj(n -> grila(ecuatii, n)).toList();
        QuizAttempt pf = practice(fractii, ana, D1);
        f.forEach(item -> answer(pf, item, false));
        QuizAttempt pe = practice(ecuatii, ana, D1);
        e.forEach(item -> answer(pe, item, false));

        // All six are due tomorrow, same date and same box: by id alone they would come three and three.
        ReviewTodayDto today = at(D1.plusDays(1)).today(ana.getEmail());

        assertThat(today.questions()).extracting(ReviewQuestionDto::quizTitle).containsExactly(
                "Fracții m1", "Ecuații m1", "Fracții m1", "Ecuații m1", "Fracții m1", "Ecuații m1");
        // Only the order changed: the same six questions, each test's own in the order they had.
        assertThat(ids(today)).filteredOn(id -> f.stream().anyMatch(item -> item.getId().equals(id)))
                .containsExactlyElementsOf(f.stream().map(QuizItem::getId).toList());
        assertThat(ids(today)).hasSize(6);
    }
}
