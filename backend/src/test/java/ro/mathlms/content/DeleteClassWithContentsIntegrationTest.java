package ro.mathlms.content;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.quiz.AttemptMode;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAttemptRepository;
import ro.mathlms.quiz.QuizAttemptService;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * „Șterge cu tot conținutul": a class with books and tests of its own can be deleted from the menu after a second,
 * explicit yes. A plain delete is still refused and says what is in it (headers); {@code withContents} takes its
 * books, chapters, exercises and tests (with their grades) along - the EN theory and the other classes stay.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"app.quiz.expiry-job.initial-delay-ms=3600000", "app.quiz.expiry-job.interval-ms=3600000"})
@AutoConfigureMockMvc
class DeleteClassWithContentsIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private SchoolClassRepository classRepository;
    @Autowired private BookRepository bookRepository;
    @Autowired private ChapterRepository chapterRepository;
    @Autowired private ExerciseRepository exerciseRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizAttemptRepository attemptRepository;
    @Autowired private QuizAttemptService attemptService;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;

    private record Filled(SchoolClass schoolClass, Book book, Chapter chapter, Exercise exercise, Quiz quiz) {
    }

    /** A class with one book (a chapter, an exercise) and one published test of its own. */
    private Filled filledClass() {
        long stamp = System.nanoTime();
        SchoolClass c = classRepository.save(new SchoolClass("Clasa " + stamp, null));
        Book book = bookRepository.save(new Book(c, "Manual " + stamp, null));
        Chapter chapter = chapterRepository.save(new Chapter(book, "Numere reale", null));
        Exercise exercise = exerciseRepository.save(new Exercise(chapter, "Calculează 2+2.", "4", Difficulty.EASY));
        Quiz quiz = new Quiz("Test clasă " + stamp, null);
        quiz.assignToClass(c);
        quiz.publish();
        quiz = quizRepository.save(quiz);
        itemRepository.save(new QuizItem(quiz, 0, QuizItemType.OPEN, "Problema", 10, null));
        return new Filled(c, book, chapter, exercise, quiz);
    }

    private ResultActions deleteClass(SchoolClass c, boolean withContents) throws Exception {
        return mockMvc.perform(delete("/api/admin/classes/" + c.getId() + (withContents ? "?withContents=true" : ""))
                .with(user("admin@scoala.ro").roles("ADMIN")).with(csrf()));
    }

    @Test
    void aPlainDeleteIsStillRefusedAndSaysWhatIsInTheClass() throws Exception {
        Filled f = filledClass();

        deleteClass(f.schoolClass(), false)
                .andExpect(status().isConflict())
                .andExpect(header().string(SchoolClassController.BOOKS_HEADER, "1"))
                .andExpect(header().string(SchoolClassController.QUIZZES_HEADER, "1"));

        assertThat(classRepository.findById(f.schoolClass().getId())).isPresent();
    }

    @Test
    void withContentsTheClassGoesWithItsBooksAndTestsAndTheRestStays() throws Exception {
        Filled f = filledClass();
        Filled other = filledClass();
        User ana = userRepository.save(new User("ana." + System.nanoTime() + "@scoala.ro", "Ana", Role.STUDENT));
        jdbc.update("INSERT INTO enrollments (student_id, school_class_id) VALUES (?, ?)",
                ana.getId(), f.schoolClass().getId());
        var attempt = attemptService.startAttempt(f.quiz().getId(), ana.getEmail(), AttemptMode.TEST);
        attemptService.submit(attempt.attemptId(), ana.getEmail()); // a grade on the class's test
        long enChapters = jdbc.queryForObject("SELECT count(*) FROM en_chapters", Long.class);

        deleteClass(f.schoolClass(), true).andExpect(status().isNoContent());

        assertThat(classRepository.findById(f.schoolClass().getId())).isEmpty();
        assertThat(bookRepository.findById(f.book().getId())).isEmpty();
        assertThat(chapterRepository.findById(f.chapter().getId())).isEmpty();
        assertThat(exerciseRepository.findById(f.exercise().getId())).isEmpty();
        assertThat(quizRepository.findById(f.quiz().getId())).isEmpty();
        assertThat(attemptRepository.findById(attempt.attemptId())).isEmpty();
        assertThat(userRepository.findById(ana.getId())).isPresent(); // the student only leaves the class
        assertThat(classRepository.findById(other.schoolClass().getId())).isPresent();
        assertThat(bookRepository.findById(other.book().getId())).isPresent();
        assertThat(quizRepository.findById(other.quiz().getId())).isPresent();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM en_chapters", Long.class)).isEqualTo(enChapters);
        assertThat(jdbc.queryForObject("SELECT details FROM admin_audit_log WHERE action = 'CLASS_DELETED' AND entity_id = ?",
                String.class, f.schoolClass().getId())).contains("1 carte").contains("1 test");
    }

    @Test
    void notWhileAStudentIsWritingOneOfItsTests() throws Exception {
        Filled f = filledClass();
        User ion = userRepository.save(new User("ion." + System.nanoTime() + "@scoala.ro", "Ion", Role.STUDENT));
        jdbc.update("INSERT INTO enrollments (student_id, school_class_id) VALUES (?, ?)",
                ion.getId(), f.schoolClass().getId());
        attemptService.startAttempt(f.quiz().getId(), ion.getEmail(), AttemptMode.TEST); // writing right now

        deleteClass(f.schoolClass(), true).andExpect(status().isConflict());

        assertThat(classRepository.findById(f.schoolClass().getId())).isPresent();
        assertThat(bookRepository.findById(f.book().getId())).isPresent(); // all or nothing
    }
}
