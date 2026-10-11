package ro.mathlms.mastery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.content.Book;
import ro.mathlms.content.BookRepository;
import ro.mathlms.content.Chapter;
import ro.mathlms.content.ChapterRepository;
import ro.mathlms.content.Enrollment;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.mastery.MasteryDtos.ChapterMasteryDto;
import ro.mathlms.mastery.MasteryDtos.ClassMasteryDto;
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
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The mastery map on a real Postgres: the chapter tags on quizzes and items (V21), the queries that load attempts,
 * items and answers, and the rules - TEST and PRACTICE both count, the latest answer per question wins, an unanswered
 * question of a handed-in test is a 0, an open answer still waiting for the teacher does not count yet.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MasteryServiceIntegrationTest {

    @Autowired private MasteryService service;
    @Autowired private UserRepository userRepository;
    @Autowired private SchoolClassRepository classRepository;
    @Autowired private BookRepository bookRepository;
    @Autowired private ChapterRepository chapterRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizOptionRepository optionRepository;
    @Autowired private QuizAttemptRepository attemptRepository;
    @Autowired private ItemResponseRepository responseRepository;
    @Autowired private ro.mathlms.review.ReviewAnswerRepository reviewAnswerRepository;

    private record World(User ana, User bob, SchoolClass cls, Chapter fractii, Chapter ecuatii, Chapter geometrie) {}

    /** No rollback between @SpringBootTest tests and names are unique: key everything on a per-test tag. */
    private World seed(String tag) {
        User ana = userRepository.save(new User("ana." + tag + "@scoala.ro", "Ana " + tag, Role.STUDENT));
        User bob = userRepository.save(new User("bob." + tag + "@scoala.ro", "Bob " + tag, Role.STUDENT));
        SchoolClass cls = classRepository.save(new SchoolClass("Clasa " + tag, null));
        enrollmentRepository.save(new Enrollment(ana, cls));
        enrollmentRepository.save(new Enrollment(bob, cls));
        Book book = bookRepository.save(new Book(cls, "Algebra " + tag, null));
        Chapter fractii = chapterRepository.save(new Chapter(book, "Fracții", null));
        Chapter ecuatii = chapterRepository.save(new Chapter(book, "Ecuații", null));
        Chapter geometrie = chapterRepository.save(new Chapter(book, "Geometrie", null)); // never practised
        return new World(ana, bob, cls, fractii, ecuatii, geometrie);
    }

    private QuizItem grila(Quiz quiz, int position, int points, Chapter chapter) {
        QuizItem item = new QuizItem(quiz, position, QuizItemType.SINGLE_CHOICE, "Întrebarea " + position, points, null);
        item.tagChapter(chapter);
        item = itemRepository.save(item);
        optionRepository.save(new QuizOption(item, 0, "corect", true));
        optionRepository.save(new QuizOption(item, 1, "greșit", false));
        return item;
    }

    private QuizOption option(QuizItem item, boolean correct) {
        return optionRepository.findByItemIdOrderByPosition(item.getId()).stream()
                .filter(o -> o.isCorrect() == correct).findFirst().orElseThrow();
    }

    private QuizAttempt attempt(Quiz quiz, User who, AttemptMode mode, Instant startedAt) {
        return attemptRepository.save(new QuizAttempt(quiz, who, startedAt, mode));
    }

    private void answer(QuizAttempt attempt, QuizItem item, boolean correct) {
        ItemResponse response = new ItemResponse(attempt, item);
        response.answerSingleChoice(option(item, correct));
        response.gradeAuto(correct, correct ? item.getPoints() : 0);
        responseRepository.save(response);
    }

    private void handIn(QuizAttempt attempt, Instant at, Integer score) {
        attempt.submit();
        ReflectionTestUtils.setField(attempt, "submittedAt", at);
        if (score != null) {
            attempt.markGraded(score);
        }
        attemptRepository.save(attempt);
    }

    private static ChapterMasteryDto chapter(List<ChapterMasteryDto> rows, String title) {
        return rows.stream().filter(r -> r.chapterTitle().equals(title)).findFirst().orElseThrow();
    }

    @Test
    void testsAndPracticeBothCountAndTheLatestAnswerWins() {
        World w = seed("m1");
        Quiz quiz = new Quiz("Recapitulare m1", null);
        quiz.tagChapter(w.fractii());                       // the quiz's default chapter...
        quiz = quizRepository.save(quiz);
        QuizItem q1 = grila(quiz, 0, 2, null);              // ...inherited
        QuizItem q2 = grila(quiz, 1, 3, w.ecuatii());       // overridden
        QuizItem q3 = grila(quiz, 2, 5, null);              // inherited, left unanswered in the test

        Instant t0 = Instant.parse("2026-10-01T10:00:00Z");
        QuizAttempt test = attempt(quiz, w.ana(), AttemptMode.TEST, t0);
        answer(test, q1, false);
        answer(test, q2, true);
        handIn(test, t0.plusSeconds(600), 3);

        QuizAttempt practice = attempt(quiz, w.ana(), AttemptMode.PRACTICE, t0.plusSeconds(86_400)); // the next day
        answer(practice, q1, true); // fixed it while practising - still in progress, practice answers count at once

        List<ChapterMasteryDto> rows = service.forStudent(w.ana().getEmail());

        ChapterMasteryDto fractii = chapter(rows, "Fracții");
        assertThat(fractii.earned()).isEqualTo(2);    // q1: latest = practice, right (2/2)
        assertThat(fractii.possible()).isEqualTo(7);  // q3: unanswered in a handed-in test = 0 of 5
        assertThat(fractii.percent()).isEqualTo(29);
        assertThat(fractii.questions()).isEqualTo(2);
        assertThat(fractii.bookTitle()).isEqualTo("Algebra m1");
        assertThat(chapter(rows, "Ecuații").percent()).isEqualTo(100);
        // every chapter of the student's classes is listed; one never practised has no percent
        assertThat(chapter(rows, "Geometrie").percent()).isNull();
        assertThat(chapter(rows, "Geometrie").questions()).isZero();
    }

    @Test
    void aTestStillBeingWrittenDoesNotCountButAPracticeDoes() {
        World w = seed("m2");
        Quiz quiz = new Quiz("Fracții m2", null);
        quiz.tagChapter(w.fractii());
        quiz = quizRepository.save(quiz);
        QuizItem q1 = grila(quiz, 0, 2, null);
        Instant t0 = Instant.parse("2026-10-02T10:00:00Z");

        QuizAttempt writing = attempt(quiz, w.ana(), AttemptMode.TEST, t0); // answers saved, not handed in
        ItemResponse saved = new ItemResponse(writing, q1);
        saved.answerSingleChoice(option(q1, true));
        responseRepository.save(saved);

        assertThat(chapter(service.forStudent(w.ana().getEmail()), "Fracții").percent()).isNull();
    }

    @Test
    void anOpenAnswerWaitingForTheTeacherDoesNotCountUntilItIsGraded() {
        World w = seed("m3");
        Quiz quiz = new Quiz("Probleme m3", null);
        quiz.tagChapter(w.fractii());
        quiz = quizRepository.save(quiz);
        QuizItem open = itemRepository.save(new QuizItem(quiz, 0, QuizItemType.OPEN, "Rezolvă", 4, null));
        Instant t0 = Instant.parse("2026-10-03T10:00:00Z");
        QuizAttempt test = attempt(quiz, w.ana(), AttemptMode.TEST, t0);
        ItemResponse photo = new ItemResponse(test, open);
        photo.answerOpen("p.jpg");
        photo = responseRepository.save(photo);
        handIn(test, t0.plusSeconds(600), null); // SUBMITTED, waiting for the teacher

        assertThat(chapter(service.forStudent(w.ana().getEmail()), "Fracții").percent()).isNull();

        photo.gradeManual(3);
        responseRepository.save(photo);
        test.markGraded(3);
        attemptRepository.save(test);

        assertThat(chapter(service.forStudent(w.ana().getEmail()), "Fracții").percent()).isEqualTo(75);
    }

    @Test
    void theClassGridHasEveryStudentAgainstEveryChapterOfTheClass() {
        World w = seed("m4");
        Quiz quiz = new Quiz("Fracții m4", null);
        quiz.tagChapter(w.fractii());
        quiz = quizRepository.save(quiz);
        QuizItem q1 = grila(quiz, 0, 2, null);
        Instant t0 = Instant.parse("2026-10-04T10:00:00Z");
        QuizAttempt anas = attempt(quiz, w.ana(), AttemptMode.TEST, t0);
        answer(anas, q1, true);
        handIn(anas, t0.plusSeconds(60), 2);

        ClassMasteryDto grid = service.forClass(w.cls().getId());

        assertThat(grid.chapters()).extracting(MasteryDtos.ChapterRefDto::title)
                .containsExactly("Ecuații", "Fracții", "Geometrie"); // natural order within the book
        assertThat(grid.students()).extracting(MasteryDtos.StudentRowDto::fullName).containsExactly("Ana m4", "Bob m4");
        MasteryDtos.StudentRowDto ana = grid.students().get(0);
        assertThat(ana.cells()).extracting(MasteryDtos.CellDto::percent).containsExactly(null, 100, null);
        assertThat(grid.students().get(1).cells()).allSatisfy(c -> assertThat(c.percent()).isNull());
    }

    @Test
    void aReviewAnswerIsTheLatestAnswerToo() {
        World w = seed("m5");
        Quiz quiz = new Quiz("Fracții m5", null);
        quiz.tagChapter(w.fractii());
        quiz = quizRepository.save(quiz);
        QuizItem q1 = grila(quiz, 0, 2, null);
        Instant t0 = Instant.parse("2026-10-05T10:00:00Z");
        QuizAttempt test = attempt(quiz, w.ana(), AttemptMode.TEST, t0);
        answer(test, q1, false);
        handIn(test, t0.plusSeconds(60), 0);
        reviewAnswerRepository.save(new ro.mathlms.review.ReviewAnswer(w.ana(), q1, option(q1, true), true,
                t0.plusSeconds(86_400)));

        assertThat(chapter(service.forStudent(w.ana().getEmail()), "Fracții").percent()).isEqualTo(100);
    }
}
