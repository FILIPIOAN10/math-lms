package ro.mathlms.assignment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.quiz.AttemptMode;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAttempt;
import ro.mathlms.quiz.QuizAttemptRepository;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The homework tables and queries on real PostgreSQL. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AssignmentRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private AssignmentRepository assignmentRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private SchoolClassRepository schoolClassRepository;
    @Autowired private QuizAttemptRepository attemptRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;

    private final Instant now = Instant.now();

    private Quiz quiz(String title) {
        return quizRepository.save(new Quiz(title, null));
    }

    private SchoolClass schoolClass(String name) {
        return schoolClassRepository.save(new SchoolClass(name, null));
    }

    private User student(String email) {
        return userRepository.save(new User(email, "Elev " + email, Role.STUDENT));
    }

    @Test
    void anAssignmentRoundTripsWithItsQuizClassAndDates() {
        Quiz quiz = quiz("Tema 1");
        SchoolClass cls = schoolClass("Clasa A");
        Assignment saved = assignmentRepository.save(new Assignment(quiz, cls, now.plus(Duration.ofDays(2)), now));
        assignmentRepository.flush();

        Assignment loaded = assignmentRepository.findByIdFetched(saved.getId()).orElseThrow();

        assertThat(loaded.getQuiz().getTitle()).isEqualTo("Tema 1");
        assertThat(loaded.getSchoolClass().getName()).isEqualTo("Clasa A");
        assertThat(loaded.getDueAt()).isCloseTo(now.plus(Duration.ofDays(2)), org.assertj.core.api.Assertions.within(1, java.time.temporal.ChronoUnit.MILLIS));
        assertThat(loaded.getReminderSentAt()).isNull();
    }

    @Test
    void theSameQuizCannotBeAssignedTwiceToOneClassButCanGoToAnotherClass() {
        Quiz quiz = quiz("Tema unica");
        SchoolClass a = schoolClass("Clasa U1");
        SchoolClass b = schoolClass("Clasa U2");
        assignmentRepository.save(new Assignment(quiz, a, now.plus(Duration.ofDays(1)), now));
        assignmentRepository.save(new Assignment(quiz, b, now.plus(Duration.ofDays(1)), now)); // another class: fine
        assignmentRepository.flush();
        assertThat(assignmentRepository.existsByQuizIdAndSchoolClassId(quiz.getId(), a.getId())).isTrue();

        assertThatThrownBy(() -> {
            assignmentRepository.save(new Assignment(quiz, a, now.plus(Duration.ofDays(5)), now));
            assignmentRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingTheQuizOrTheClassTakesItsAssignmentsWithIt() {
        Quiz quiz = quiz("Se sterge");
        SchoolClass cls = schoolClass("Se sterge clasa");
        assignmentRepository.save(new Assignment(quiz, cls, now.plus(Duration.ofDays(1)), now));
        assignmentRepository.flush();

        jdbc.update("delete from quizzes where id = ?", quiz.getId());

        assertThat(jdbc.queryForObject("select count(*) from assignments where school_class_id = ?", Long.class, cls.getId()))
                .isZero();
    }

    @Test
    void theReminderLookupFindsOnlyUnremindedAssignmentsDueInsideTheWindow() {
        SchoolClass cls = schoolClass("Fereastra");
        Instant from = now;
        Instant until = now.plus(Duration.ofHours(24));
        Assignment inside = assignmentRepository.save(new Assignment(quiz("In"), cls, now.plus(Duration.ofHours(10)), now));
        Assignment edge = assignmentRepository.save(new Assignment(quiz("Margine"), cls, until, now));
        Assignment tooFar = assignmentRepository.save(new Assignment(quiz("Departe"), cls, now.plus(Duration.ofHours(30)), now));
        Assignment passed = assignmentRepository.save(new Assignment(quiz("Trecut"), cls, now.minus(Duration.ofHours(1)), now.minus(Duration.ofDays(2))));
        Assignment reminded = new Assignment(quiz("Deja"), cls, now.plus(Duration.ofHours(5)), now);
        reminded.markReminderSent(now);
        reminded = assignmentRepository.save(reminded);
        assignmentRepository.flush();

        List<Long> ids = assignmentRepository.findIdsDueForReminder(from, until);

        assertThat(ids).contains(inside.getId(), edge.getId())
                .doesNotContain(tooFar.getId(), passed.getId(), reminded.getId());
        assertThat(ids.indexOf(inside.getId())).isLessThan(ids.indexOf(edge.getId())); // soonest first
    }

    @Test
    void theAssignmentsOfAStudentsClassesComeBackWithQuizAndClassLoaded() {
        SchoolClass mine = schoolClass("A mea");
        SchoolClass other = schoolClass("Alta");
        Assignment a1 = assignmentRepository.save(new Assignment(quiz("T1"), mine, now.plus(Duration.ofDays(1)), now));
        assignmentRepository.save(new Assignment(quiz("T2"), other, now.plus(Duration.ofDays(1)), now));
        assignmentRepository.flush();

        List<Assignment> found = assignmentRepository.findBySchoolClassIdsFetched(List.of(mine.getId()));

        assertThat(found).extracting(Assignment::getId).containsExactly(a1.getId());
        assertThat(found.get(0).getQuiz().getTitle()).isEqualTo("T1"); // loaded: no lazy proxy to trip over later
    }

    @Test
    void theProgressQueryKeepsOnlyGradedTestAttemptsOfTheRightStudentsSinceTheAssignment() {
        Quiz quiz = quiz("Progres");
        User ana = student("ana.prog@t.ro");
        User bob = student("bob.prog@t.ro");
        User outsider = student("out.prog@t.ro");
        Instant created = now;
        QuizAttempt anaTest = attemptRepository.save(new QuizAttempt(quiz, ana, created.plusSeconds(60), AttemptMode.TEST));
        QuizAttempt bobBefore = attemptRepository.save(new QuizAttempt(quiz, bob, created.minus(Duration.ofDays(3)), AttemptMode.TEST));
        QuizAttempt bobPractice = attemptRepository.save(new QuizAttempt(quiz, bob, created.plusSeconds(90), AttemptMode.PRACTICE));
        QuizAttempt outsiderTest = attemptRepository.save(new QuizAttempt(quiz, outsider, created.plusSeconds(60), AttemptMode.TEST));
        attemptRepository.flush();

        List<QuizAttempt> found = attemptRepository.findForAssignment(quiz.getId(), List.of(ana.getId(), bob.getId()), created);

        assertThat(found).extracting(QuizAttempt::getId).containsExactly(anaTest.getId());
        assertThat(found).extracting(QuizAttempt::getId).doesNotContain(bobBefore.getId(), bobPractice.getId(), outsiderTest.getId());
        assertThat(found.get(0).getStudent().getEmail()).isEqualTo("ana.prog@t.ro"); // student loaded with the attempt
    }

    @Test
    void aStudentsOwnAttemptsAtSeveralQuizzesComeBackTogether() {
        Quiz q1 = quiz("Unu");
        Quiz q2 = quiz("Doi");
        Quiz q3 = quiz("Trei");
        User ana = student("ana.multi@t.ro");
        QuizAttempt a1 = attemptRepository.save(new QuizAttempt(quiz("x"), ana)); // an attempt at a quiz not asked for
        QuizAttempt a2 = attemptRepository.save(new QuizAttempt(q1, ana));
        QuizAttempt a3 = attemptRepository.save(new QuizAttempt(q2, ana));
        attemptRepository.flush();

        List<QuizAttempt> found = attemptRepository.findByStudentIdAndQuizIdIn(ana.getId(), List.of(q1.getId(), q2.getId(), q3.getId()));

        assertThat(found).extracting(QuizAttempt::getId).containsExactlyInAnyOrder(a2.getId(), a3.getId())
                .doesNotContain(a1.getId());
        assertThat(found.get(0).getQuiz().getTitle()).isNotNull(); // quiz loaded by the entity graph
    }
}
