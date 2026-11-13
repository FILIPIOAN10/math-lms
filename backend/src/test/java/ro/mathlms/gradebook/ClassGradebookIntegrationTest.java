package ro.mathlms.gradebook;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.assignment.Assignment;
import ro.mathlms.assignment.AssignmentRepository;
import ro.mathlms.content.ContentNotFoundException;
import ro.mathlms.content.Enrollment;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.gradebook.ClassGradebookService.Gradebook;
import ro.mathlms.gradebook.ClassGradebookService.StudentRow;
import ro.mathlms.gradebook.ClassGradebookService.TestColumn;
import ro.mathlms.quiz.AttemptMode;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAttempt;
import ro.mathlms.quiz.QuizAttemptRepository;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The class gradebook on a real Postgres: which tests are its columns, which grade counts for a student, and who may
 * download the file.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ClassGradebookIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-11-02T08:00:00Z");

    @Autowired private ClassGradebookService service;
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private SchoolClassRepository classRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizAttemptRepository attemptRepository;
    @Autowired private AssignmentRepository assignmentRepository;

    private User student(String name, String tag, SchoolClass schoolClass) {
        User user = userRepository.save(new User(name.toLowerCase() + "." + tag + "@scoala.ro", name + " " + tag, Role.STUDENT));
        enrollmentRepository.save(new Enrollment(user, schoolClass));
        return user;
    }

    /** A test worth 10 points, for one class or ({@code null}) open to everybody. */
    private Quiz quiz(String title, SchoolClass schoolClass) {
        Quiz quiz = new Quiz(title, null);
        quiz.assignToClass(schoolClass);
        quiz.publish();
        quiz = quizRepository.save(quiz);
        itemRepository.save(new QuizItem(quiz, 0, QuizItemType.OPEN, "Rezolvă.", 10, null));
        return quiz;
    }

    private void graded(Quiz quiz, User who, int score) {
        QuizAttempt attempt = new QuizAttempt(quiz, who, T0, AttemptMode.TEST);
        attempt.submit();
        attempt.markGraded(score);
        attemptRepository.save(attempt);
    }

    private static RequestPostProcessor as(String email, String role) {
        return user(email).authorities(new SimpleGrantedAuthority("ROLE_" + role), new SimpleGrantedAuthority("STATUS_ACTIVE"));
    }

    @Test
    void studentsDownTestsAcrossAndTheHighestGradeCounts() {
        SchoolClass cls = classRepository.save(new SchoolClass("Clasa g1", null));
        SchoolClass other = classRepository.save(new SchoolClass("Alta g1", null));
        User ana = student("Ana", "g1", cls);
        User bob = student("Bob", "g1", cls);
        User dan = student("Dan", "g1", other);

        Quiz own = quiz("Fracții g1", cls);
        Quiz homework = quiz("Ecuații g1", null); // open to everybody, given to the class as homework
        assignmentRepository.save(new Assignment(homework, cls, T0.plus(7, ChronoUnit.DAYS), T0));
        Quiz notTaken = quiz("Netrecut g1", cls);
        Quiz elsewhere = quiz("Al altei clase g1", other);

        graded(own, ana, 4);
        graded(own, ana, 8);      // the retake is the one that counts
        graded(homework, bob, 10);
        graded(elsewhere, dan, 9);
        graded(own, dan, 7);      // not in this class: no row for him

        Gradebook book = service.forClass(cls.getId());

        assertThat(book.className()).isEqualTo("Clasa g1");
        assertThat(book.tests()).extracting(TestColumn::title).containsExactly("Fracții g1", "Ecuații g1");
        assertThat(book.tests()).extracting(TestColumn::quizId).doesNotContain(notTaken.getId(), elsewhere.getId());
        assertThat(book.students()).extracting(StudentRow::fullName).containsExactly("Ana g1", "Bob g1");
        assertThat(book.students().get(0).grades()).containsExactly(new BigDecimal("8.20"), null);
        assertThat(book.students().get(1).grades()).containsExactly(null, new BigDecimal("10.00"));

        assertThat(ClassGradebookService.toCsv(book)).isEqualTo(
                "﻿Elev;Fracții g1;Ecuații g1\r\nAna g1;8,20;\r\nBob g1;;10\r\n");
    }

    @Test
    void aClassWithNoGradesStillListsItsStudents() {
        SchoolClass cls = classRepository.save(new SchoolClass("Clasa g2", null));
        student("Ana", "g2", cls);

        Gradebook book = service.forClass(cls.getId());

        assertThat(book.tests()).isEmpty();
        assertThat(ClassGradebookService.toCsv(book)).isEqualTo("﻿Elev\r\nAna g2\r\n");
    }

    @Test
    void aMissingClassIsNotFound() {
        assertThatThrownBy(() -> service.forClass(-1L)).isInstanceOf(ContentNotFoundException.class);
    }

    @Test
    void theFileIsADownloadForTheClassOwnerOnly() throws Exception {
        User owner = userRepository.save(new User("prof.g3@scoala.ro", "Prof g3", Role.TEACHER));
        User stranger = userRepository.save(new User("alt.prof.g3@scoala.ro", "Alt prof g3", Role.TEACHER));
        SchoolClass cls = new SchoolClass("Clasa g3", null);
        cls.assignTeacher(owner.getId());
        cls = classRepository.save(cls);
        User ana = student("Ana", "g3", cls);
        graded(quiz("Fracții g3", cls), ana, 5);
        String url = "/api/admin/classes/" + cls.getId() + "/grades.csv";

        byte[] file = mockMvc.perform(get(url).with(as(owner.getEmail(), "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(file, StandardCharsets.UTF_8)).isEqualTo("﻿Elev;Fracții g3\r\nAna g3;5,50\r\n");

        // Another teacher's class looks exactly like a class that does not exist.
        mockMvc.perform(get(url).with(as(stranger.getEmail(), "TEACHER"))).andExpect(status().isNotFound());
        mockMvc.perform(get(url).with(as(ana.getEmail(), "STUDENT"))).andExpect(status().isForbidden());
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized());
    }
}
