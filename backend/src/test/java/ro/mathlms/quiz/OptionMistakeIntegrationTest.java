package ro.mathlms.quiz;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.content.Book;
import ro.mathlms.content.BookRepository;
import ro.mathlms.content.Chapter;
import ro.mathlms.content.ChapterRepository;
import ro.mathlms.content.Difficulty;
import ro.mathlms.content.Exercise;
import ro.mathlms.content.ExerciseRepository;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.quiz.StudentQuizDtos.AnswerFeedbackDto;
import ro.mathlms.quiz.StudentQuizDtos.ItemResultDto;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** What a teacher writes about a wrong option reaches the student who chose it, and only them; on real Postgres. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"app.quiz.expiry-job.initial-delay-ms=3600000", "app.quiz.expiry-job.interval-ms=3600000"})
class OptionMistakeIntegrationTest {

    private static final String MISTAKE = "Ai adunat numitorii: aduci întâi la același numitor.";

    @Autowired private QuizAdminService service;
    @Autowired private QuizAttemptService attemptService;
    @Autowired private QuizStatsService statsService;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizOptionRepository optionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SchoolClassRepository classRepository;
    @Autowired private BookRepository bookRepository;
    @Autowired private ChapterRepository chapterRepository;
    @Autowired private ExerciseRepository exerciseRepository;

    private Quiz quiz;
    private QuizItem item;
    private QuizOption right;
    private QuizOption wrong;

    @BeforeEach
    void setUp() {
        Quiz q = new Quiz("Fracții " + System.nanoTime(), null);
        q.publish();
        q.allowPractice(true);
        quiz = quizRepository.save(q);
        item = itemRepository.save(new QuizItem(quiz, 0, QuizItemType.SINGLE_CHOICE, "1/2 + 1/4 =", 5, "Rezolvarea generală"));
        right = optionRepository.save(new QuizOption(item, 0, "3/4", true));
        wrong = optionRepository.save(new QuizOption(item, 1, "2/6", false));
    }

    private User student() {
        return userRepository.save(new User("elev" + System.nanoTime() + "@scoala.ro", "Elev", Role.STUDENT));
    }

    private ItemResultDto handIn(QuizOption answer) {
        User student = student();
        StartedAttemptDto attempt = attemptService.startAttempt(quiz.getId(), student.getEmail());
        attemptService.saveResponse(attempt.attemptId(), item.getId(), answer.getId(), student.getEmail());
        attemptService.submit(attempt.attemptId(), student.getEmail());
        return attemptService.getResult(attempt.attemptId(), student.getEmail()).items().get(0);
    }

    @Test
    void theStudentWhoChoseTheWrongOptionReadsWhyAndTheOtherDoesNot() {
        service.explainMistake(wrong.getId(), MISTAKE);

        assertThat(handIn(wrong).mistake()).isEqualTo(MISTAKE);
        assertThat(handIn(right).mistake()).isNull();
    }

    @Test
    void itReachesPapersAlreadyHandedInAndCanBeTakenBack() {
        User student = student();
        StartedAttemptDto attempt = attemptService.startAttempt(quiz.getId(), student.getEmail());
        attemptService.saveResponse(attempt.attemptId(), item.getId(), wrong.getId(), student.getEmail());
        attemptService.submit(attempt.attemptId(), student.getEmail());

        service.explainMistake(wrong.getId(), MISTAKE);
        assertThat(attemptService.getResult(attempt.attemptId(), student.getEmail()).items().get(0).mistake()).isEqualTo(MISTAKE);

        service.explainMistake(wrong.getId(), "  ");
        assertThat(attemptService.getResult(attempt.attemptId(), student.getEmail()).items().get(0).mistake()).isNull();
    }

    @Test
    void aPracticeSaysItOnTheSpot() {
        service.explainMistake(wrong.getId(), MISTAKE);
        User student = student();
        StartedAttemptDto attempt = attemptService.startAttempt(quiz.getId(), student.getEmail(), AttemptMode.PRACTICE);

        AnswerFeedbackDto feedback = attemptService
                .saveResponse(attempt.attemptId(), item.getId(), wrong.getId(), student.getEmail()).orElseThrow();

        assertThat(feedback.correct()).isFalse();
        assertThat(feedback.mistake()).isEqualTo(MISTAKE);
    }

    @Test
    void aGradedTestRevealsNothingBeforeHandIn() {
        service.explainMistake(wrong.getId(), MISTAKE);
        User student = student();
        StartedAttemptDto attempt = attemptService.startAttempt(quiz.getId(), student.getEmail());

        assertThat(attemptService.saveResponse(attempt.attemptId(), item.getId(), wrong.getId(), student.getEmail())).isEmpty();
    }

    @Test
    void theRightOptionHasNoMistakeToExplain() {
        assertThatThrownBy(() -> service.explainMistake(right.getId(), MISTAKE)).isInstanceOf(InvalidQuizException.class);
    }

    @Test
    void anUnknownOptionIsNotFound() {
        assertThatThrownBy(() -> service.itemIdOfOption(-1L)).isInstanceOf(QuizNotFoundException.class);
    }

    @Test
    void theTeacherSeesItNextToTheCountsAtOnce() {
        handIn(wrong);
        assertThat(statsService.getStats(quiz.getId()).items().get(0).options().get(1).mistake()).isNull();

        service.explainMistake(wrong.getId(), MISTAKE);

        assertThat(statsService.getStats(quiz.getId()).items().get(0).options().get(1).mistake()).isEqualTo(MISTAKE);
    }

    @Test
    void aCopyOfTheTestKeepsTheExplanation() {
        service.explainMistake(wrong.getId(), MISTAKE);

        Quiz copy = service.copyQuiz(quiz.getId());

        Long copiedItem = itemRepository.findByQuizIdOrderByPosition(copy.getId()).get(0).getId();
        assertThat(optionRepository.findByItemIdOrderByPosition(copiedItem).get(1).getMistake()).isEqualTo(MISTAKE);
    }

    @Test
    void editingADraftQuestionKeepsWhatTheDialogSends() {
        ItemRequest request = new ItemRequest(QuizItemType.SINGLE_CHOICE, 0, "1/2 + 1/4 =", 5, null,
                List.of(new OptionRequest(0, "3/4", true), new OptionRequest(1, "2/6", false, MISTAKE)));

        service.updateItem(item.getId(), request);

        assertThat(optionRepository.findByItemIdOrderByPosition(item.getId())).extracting(QuizOption::getMistake)
                .containsExactly(null, MISTAKE);
    }

    @Test
    void aResultLinksToTheChapterOnlyWhenItHasExercises() {
        SchoolClass schoolClass = classRepository.save(new SchoolClass("Clasa " + System.nanoTime(), null));
        Book book = bookRepository.save(new Book(schoolClass, "Algebră", null));
        Chapter chapter = chapterRepository.save(new Chapter(book, "Fracții", null));
        quiz.tagChapter(chapter);
        quizRepository.save(quiz);
        assertThat(handIn(wrong).chapter()).isNull();

        exerciseRepository.save(new Exercise(chapter, "Calculează", "Rezolvare", Difficulty.EASY));

        assertThat(handIn(wrong).chapter()).satisfies(link -> {
            assertThat(link.id()).isEqualTo(chapter.getId());
            assertThat(link.title()).isEqualTo("Fracții");
            assertThat(link.bookId()).isEqualTo(book.getId());
            assertThat(link.classId()).isEqualTo(schoolClass.getId());
        });
    }
}
