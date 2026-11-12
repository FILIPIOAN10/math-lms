package ro.mathlms.assignment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.content.Enrollment;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAttemptService;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.quiz.QuizNotFoundException;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Scheduled homework on real Postgres (V64, audit 2026-11-12 M1/M2): a homework scheduled for later hides the homework
 * itself from its students, but its TEST only when the test was published for it ("Publică și dă ca temă" on a
 * draft) - and then from every student, not only the class it was given to.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ScheduledReleaseIntegrationTest {

    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private SchoolClassRepository schoolClassRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AssignmentService assignmentService;
    @Autowired private PublishAndAssignService publishAndAssignService;
    @Autowired private QuizAttemptService quizAttemptService;

    private SchoolClass clasa;
    private User ana;  // in the class the homework is for
    private User bob;  // in another class
    private User cleo; // in no class
    private Instant due;
    private Instant friday;

    @BeforeEach
    void setUp() {
        String stamp = String.valueOf(System.nanoTime());
        clasa = schoolClassRepository.save(new SchoolClass("Clasa a 8-a " + stamp, null));
        SchoolClass alta = schoolClassRepository.save(new SchoolClass("Clasa a 9-a " + stamp, null));
        ana = student("ana." + stamp);
        bob = student("bob." + stamp);
        cleo = student("cleo." + stamp);
        enrollmentRepository.save(new Enrollment(ana, clasa));
        enrollmentRepository.save(new Enrollment(bob, alta));
        friday = Instant.now().plus(2, ChronoUnit.DAYS);
        due = Instant.now().plus(5, ChronoUnit.DAYS);
    }

    private User student(String tag) {
        return userRepository.save(new User(tag + "@scoala.ro", "Elev " + tag, Role.STUDENT));
    }

    /** A test with one question; "Toți elevii" when {@code forClass} is null. */
    private Quiz quiz(String title, SchoolClass forClass, boolean published) {
        Quiz quiz = new Quiz(title + " " + System.nanoTime(), null);
        if (forClass != null) {
            quiz.assignToClass(forClass);
        }
        if (published) {
            quiz.publish();
        }
        Quiz saved = quizRepository.save(quiz);
        itemRepository.save(new QuizItem(saved, 1, QuizItemType.OPEN, "Rezolvă", 5, null));
        return saved;
    }

    private boolean listed(User student, Quiz quiz) {
        return quizAttemptService.listPublished(student.getEmail()).stream().anyMatch(q -> q.getId().equals(quiz.getId()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aTestStudentsCanAlreadySeeStaysVisibleWhenItIsGivenAsHomeworkForLater() {
        Quiz fractii = quiz("Fracții", clasa, true);
        Long attemptId = quizAttemptService.startAttempt(fractii.getId(), ana.getEmail()).attemptId();

        assignmentService.create(fractii.getId(), clasa.getId(), due, friday);

        assertThat(listed(ana, fractii)).isTrue();
        // the test she is writing reopens (it used to be a 404 until Friday)
        assertThat(quizAttemptService.getPreview(fractii.getId(), ana.getEmail()).id()).isEqualTo(fractii.getId());
        assertThat(quizAttemptService.startAttempt(fractii.getId(), ana.getEmail()).attemptId()).isEqualTo(attemptId);
        // the homework itself still waits for Friday
        assertThat(assignmentService.myAssignments(ana.getEmail()))
                .noneMatch(a -> a.quizId().equals(fractii.getId()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void publishAndAssignOfATestAlreadyPublishedDoesNotHideIt() {
        Quiz global = quiz("Recapitulare", null, true);

        publishAndAssignService.publishAndAssign(global.getId(), clasa.getId(), due, friday);

        assertThat(listed(ana, global)).isTrue();
        assertThat(listed(bob, global)).isTrue();
        assertThat(listed(cleo, global)).isTrue();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aTestPublishedForAScheduledHomeworkIsHiddenFromEveryStudentUntilTheRelease() {
        Quiz teza = quiz("Teza de vineri", null, false); // "Toți elevii"

        Assignment homework = publishAndAssignService.publishAndAssign(teza.getId(), clasa.getId(), due, friday);

        for (User student : new User[] {ana, bob, cleo}) {
            assertThat(listed(student, teza)).as(student.getEmail()).isFalse();
            assertThatThrownBy(() -> quizAttemptService.getPreview(teza.getId(), student.getEmail()))
                    .isInstanceOf(QuizNotFoundException.class);
            assertThatThrownBy(() -> quizAttemptService.startAttempt(teza.getId(), student.getEmail()))
                    .isInstanceOf(QuizNotFoundException.class);
        }

        assignmentService.setRelease(homework.getId(), null); // "Dă-o acum"

        assertThat(listed(ana, teza)).isTrue();
        assertThat(listed(bob, teza)).isTrue();
        assertThat(listed(cleo, teza)).isTrue();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aClassTestPublishedForAScheduledHomeworkWaitsForTheRelease() {
        Quiz teza = quiz("Teza clasei", clasa, false);

        publishAndAssignService.publishAndAssign(teza.getId(), clasa.getId(), due, friday);

        assertThat(listed(ana, teza)).isFalse();
        assertThat(assignmentService.myAssignments(ana.getEmail())).noneMatch(a -> a.quizId().equals(teza.getId()));
    }
}
