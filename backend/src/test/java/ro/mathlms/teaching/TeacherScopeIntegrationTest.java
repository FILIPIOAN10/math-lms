package ro.mathlms.teaching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.content.Book;
import ro.mathlms.content.BookRepository;
import ro.mathlms.content.Enrollment;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAttempt;
import ro.mathlms.quiz.QuizAttemptRepository;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Two teachers on real Postgres: each one sees and changes only their own classes, tests, students and attempts — the
 * other's come back 404, exactly like ids that do not exist — while an ADMIN sees everything and is the only one who
 * runs the platform (accounts, journal, teacher links, shared EN pages) and hands classes between teachers.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TeacherScopeIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private SchoolClassRepository classRepository;
    @Autowired private BookRepository bookRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizAttemptRepository attemptRepository;

    private String stamp;
    private User ana;   // teacher A
    private User bogdan; // teacher B
    private SchoolClass classA;
    private SchoolClass classB;
    private Book bookB;
    private Quiz quizB;
    private User studentB;
    private QuizAttempt attemptB;

    @BeforeEach
    void setUp() {
        stamp = String.valueOf(System.nanoTime());
        ana = active("ana", Role.TEACHER);
        bogdan = active("bogdan", Role.TEACHER);
        classA = owned("Clasa A " + stamp, ana);
        classB = owned("Clasa B " + stamp, bogdan);
        bookB = bookRepository.save(new Book(classB, "Manual B", null));
        studentB = active("elev", Role.STUDENT);
        enrollmentRepository.save(new Enrollment(studentB, classB));
        quizB = new Quiz("Test B " + stamp, null);
        quizB.assignToClass(classB);
        quizB.ownedBy(bogdan.getId());
        quizB = quizRepository.save(quizB);
        attemptB = new QuizAttempt(quizB, studentB);
        attemptB.submit();
        attemptB = attemptRepository.save(attemptB);
    }

    private User active(String who, Role role) {
        User user = User.registerLocal(who + "." + stamp + "@scoala.ro", "Nume " + who, "HASH", role);
        user.verifyEmail();
        user.approve(role);
        return userRepository.save(user);
    }

    private SchoolClass owned(String name, User teacher) {
        SchoolClass schoolClass = new SchoolClass(name, null);
        schoolClass.assignTeacher(teacher.getId());
        return classRepository.save(schoolClass);
    }

    private static RequestPostProcessor as(User account, String role) {
        return user(account.getEmail()).authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("STATUS_ACTIVE"));
    }

    private RequestPostProcessor asAna() {
        return as(ana, "TEACHER");
    }

    private RequestPostProcessor asAdmin() {
        return user("director." + stamp + "@scoala.ro").roles("ADMIN").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("STATUS_ACTIVE"));
    }

    @Test
    void aTeacherSeesOnlyTheirOwnClasses() throws Exception {
        mockMvc.perform(get("/api/classes").with(asAna()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(classA.getId().intValue())))
                .andExpect(jsonPath("$[*].id", not(hasItem(classB.getId().intValue()))));
        mockMvc.perform(get("/api/classes/" + classB.getId()).with(asAna())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/books/" + bookB.getId()).with(asAna())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/classes/" + classB.getId() + "/enrollments").with(asAna()))
                .andExpect(status().isNotFound());
    }

    @Test
    void aTeacherCannotChangeAnotherTeachersClass() throws Exception {
        mockMvc.perform(put("/api/admin/classes/" + classB.getId()).with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Furat " + stamp + "\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/classes/" + classB.getId() + "/books").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Intrus\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/admin/books/" + bookB.getId()).with(asAna()).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(classRepository.findById(classB.getId()).orElseThrow().getName()).isEqualTo(classB.getName());
        assertThat(bookRepository.findById(bookB.getId())).isPresent();
    }

    @Test
    void aTeacherSeesOnlyTheirOwnTestsStudentsAndAttempts() throws Exception {
        mockMvc.perform(get("/api/admin/quizzes").with(asAna()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(quizB.getId().intValue()))));
        mockMvc.perform(get("/api/admin/quizzes/" + quizB.getId()).with(asAna())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/students/" + studentB.getId()).with(asAna())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/quiz/attempts/" + attemptB.getId()).with(asAna()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/users").param("role", "STUDENT").with(asAna()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(studentB.getId().intValue()))));
        mockMvc.perform(get("/api/admin/users").param("role", "STUDENT").with(as(bogdan, "TEACHER")))
                .andExpect(jsonPath("$[*].id", hasItem(studentB.getId().intValue())));
        mockMvc.perform(get("/api/admin/users").param("role", "PARENT").with(asAna())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/quiz/attempts").with(asAna()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].attemptId", not(hasItem(attemptB.getId().intValue()))));
        // the owner sees the same rows
        mockMvc.perform(get("/api/admin/quizzes/" + quizB.getId()).with(as(bogdan, "TEACHER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/students/" + studentB.getId()).with(as(bogdan, "TEACHER")))
                .andExpect(status().isOk());
    }

    @Test
    void theGradedListIsPagedNewestFirstWithOnlyTheTeachersOwnPapers() throws Exception {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            QuizAttempt graded = new QuizAttempt(quizB, active("notat" + i, Role.STUDENT));
            graded.submit();
            graded.markGraded(5);
            ids.add(attemptRepository.save(graded).getId());
        }

        // Bogdan's papers, two per page, newest first; the next page starts below the last id seen
        mockMvc.perform(get("/api/admin/quiz/attempts").param("status", "GRADED").param("limit", "2")
                        .with(as(bogdan, "TEACHER")))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].attemptId").value(ids.get(2).intValue()))
                .andExpect(jsonPath("$[1].attemptId").value(ids.get(1).intValue()));
        mockMvc.perform(get("/api/admin/quiz/attempts").param("status", "GRADED").param("limit", "2")
                        .param("before", String.valueOf(ids.get(1))).with(as(bogdan, "TEACHER")))
                .andExpect(jsonPath("$[0].attemptId").value(ids.get(0).intValue()));
        // none of them is Ana's
        mockMvc.perform(get("/api/admin/quiz/attempts").param("status", "GRADED").with(asAna()))
                .andExpect(jsonPath("$[*].attemptId", not(hasItem(ids.get(2).intValue()))));
    }

    @Test
    void whatATeacherCreatesIsTheirsAndATestNeedsOneOfTheirClasses() throws Exception {
        mockMvc.perform(post("/api/admin/classes").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Clasa noua " + stamp + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.teacherId").value(ana.getId().intValue()));

        // "for every student" would reach the other teacher's students
        mockMvc.perform(post("/api/admin/quizzes").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Pentru toti\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/quizzes").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"In clasa altuia\",\"schoolClassId\":" + classB.getId() + "}"))
                .andExpect(status().isNotFound());
        String created = mockMvc.perform(post("/api/admin/quizzes").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Al meu " + stamp + "\",\"schoolClassId\":" + classA.getId() + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long quizId = Long.valueOf(created.replaceAll(".*\"id\":(\\d+).*", "$1"));
        assertThat(quizRepository.findById(quizId).orElseThrow().getTeacherId()).isEqualTo(ana.getId());
    }

    @Test
    void runningThePlatformStaysWithTheAdmins() throws Exception {
        mockMvc.perform(get("/api/admin/users/pending").with(asAna())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/audit-log").with(asAna())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/invites/admin").with(asAna()).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/invites").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/en-chapters/1").with(asAna()).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/classes/" + classA.getId() + "/teacher").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"teacherId\":" + ana.getId() + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAdminSeesEverythingAndHandsAClassToAnotherTeacher() throws Exception {
        mockMvc.perform(get("/api/admin/quizzes/" + quizB.getId()).with(asAdmin())).andExpect(status().isOk());
        mockMvc.perform(get("/api/classes").with(asAdmin()))
                .andExpect(jsonPath("$[*].id", hasItem(classB.getId().intValue())));

        mockMvc.perform(put("/api/admin/classes/" + classA.getId() + "/teacher").with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"teacherId\":" + bogdan.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherId").value(bogdan.getId().intValue()));

        mockMvc.perform(get("/api/classes/" + classA.getId()).with(asAna())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/classes/" + classA.getId()).with(as(bogdan, "TEACHER"))).andExpect(status().isOk());
        // a student cannot be made the owner
        mockMvc.perform(put("/api/admin/classes/" + classA.getId() + "/teacher").with(asAdmin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"teacherId\":" + studentB.getId() + "}"))
                .andExpect(status().isNotFound());
    }
}
