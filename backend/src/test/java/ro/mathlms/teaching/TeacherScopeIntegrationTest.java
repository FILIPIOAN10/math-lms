package ro.mathlms.teaching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.assignment.Assignment;
import ro.mathlms.assignment.AssignmentRepository;
import ro.mathlms.content.Book;
import ro.mathlms.content.BookRepository;
import ro.mathlms.content.Chapter;
import ro.mathlms.content.ChapterRepository;
import ro.mathlms.content.Enrollment;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.Exercise;
import ro.mathlms.content.ExerciseRepository;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.invite.InviteLink;
import ro.mathlms.invite.InviteLinkRepository;
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

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private SchoolClassRepository classRepository;
    @Autowired private BookRepository bookRepository;
    @Autowired private ChapterRepository chapterRepository;
    @Autowired private ExerciseRepository exerciseRepository;
    @Autowired private EnrollmentRepository enrollmentRepository;
    @Autowired private InviteLinkRepository inviteLinkRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizAttemptRepository attemptRepository;
    @Autowired private QuizItemRepository quizItemRepository;
    @Autowired private QuizOptionRepository quizOptionRepository;
    @Autowired private AssignmentRepository assignmentRepository;

    private String stamp;
    private User ana;   // teacher A
    private User bogdan; // teacher B
    private SchoolClass classA;
    private SchoolClass classB;
    private Book bookB;
    private Chapter chapterB;
    private Exercise exerciseB;
    private Enrollment enrollmentB;
    private InviteLink inviteB;
    private Quiz quizB;
    private QuizItem itemB;
    private QuizOption optionB;
    private Assignment assignmentB;
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
        chapterB = chapterRepository.saveAndFlush(new Chapter(bookB, "Capitol B " + stamp, null));
        exerciseB = exerciseRepository.saveAndFlush(new Exercise(chapterB, "Exercițiu B", null, null));
        studentB = active("elev", Role.STUDENT);
        enrollmentB = enrollmentRepository.saveAndFlush(new Enrollment(studentB, classB));
        inviteB = inviteLinkRepository.saveAndFlush(InviteLink.forClass(
                "scope-" + stamp, classB, 1, Instant.now()));
        quizB = new Quiz("Test B " + stamp, null);
        quizB.assignToClass(classB);
        quizB.ownedBy(bogdan.getId());
        quizB = quizRepository.save(quizB);
        itemB = quizItemRepository.saveAndFlush(
                new QuizItem(quizB, 0, QuizItemType.SINGLE_CHOICE, "Întrebare B", 1, null));
        optionB = quizOptionRepository.saveAndFlush(new QuizOption(itemB, 0, "Răspuns B", true));
        Instant now = Instant.now();
        assignmentB = assignmentRepository.saveAndFlush(
                new Assignment(quizB, classB, now.plusSeconds(86400), now));
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
    void everyStudentProfileEndpointHidesAStudentOutsideTheTeachersClasses() throws Exception {
        for (String path : java.util.List.of(
                "/api/admin/students/" + studentB.getId(),
                "/api/admin/students/" + studentB.getId() + "/attempts",
                "/api/admin/students/" + studentB.getId() + "/assignments",
                "/api/admin/students/" + studentB.getId() + "/progress",
                "/api/admin/students/" + studentB.getId() + "/mastery",
                "/api/admin/students/" + studentB.getId() + "/review",
                "/api/admin/students/" + studentB.getId() + "/en-mastery",
                "/api/admin/students/" + studentB.getId() + "/en-prediction")) {
            mockMvc.perform(get(path).with(asAna())).andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/api/admin/students/summary").with(asAna()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].studentId", not(hasItem(studentB.getId().intValue()))));
        mockMvc.perform(put("/api/admin/users/" + studentB.getId() + "/extra-time").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"percent\":25}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/admin/users/" + studentB.getId() + "/extra-time")
                        .with(as(bogdan, "TEACHER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"percent\":25}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void everyAssignmentEndpointHidesAnotherTeachersAssignmentQuizAndClass() throws Exception {
        String dueAt = Instant.now().plusSeconds(172800).toString();
        String assignmentBody = "{\"quizId\":" + quizB.getId() + ",\"schoolClassId\":"
                + classB.getId() + ",\"dueAt\":\"" + dueAt + "\"}";

        mockMvc.perform(get("/api/admin/assignments").with(asAna()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(assignmentB.getId().intValue()))));
        mockMvc.perform(post("/api/admin/assignments").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(assignmentBody))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/assignments/batch").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quizId\":" + quizB.getId() + ",\"schoolClassIds\":[" + classB.getId()
                                + "],\"dueAt\":\"" + dueAt + "\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/assignments/for-students").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quizId\":" + quizB.getId() + ",\"schoolClassId\":" + classB.getId()
                                + ",\"studentIds\":[" + studentB.getId() + "],\"dueAt\":\"" + dueAt + "\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/quizzes/" + quizB.getId() + "/publish-and-assign")
                        .with(asAna()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schoolClassId\":" + classB.getId() + ",\"dueAt\":\"" + dueAt + "\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/admin/assignments/" + assignmentB.getId()).with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dueAt\":\"" + dueAt + "\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/admin/assignments/" + assignmentB.getId() + "/release").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"availableFrom\":null}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/admin/assignments/" + assignmentB.getId()).with(asAna()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/admin/assignments/" + assignmentB.getId() + "/students/" + studentB.getId()
                        + "/override").with(asAna()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"excused\":true}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/assignments/" + assignmentB.getId() + "/remind")
                        .with(asAna()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/assignments/" + assignmentB.getId() + "/status").with(asAna()))
                .andExpect(status().isNotFound());
    }

    @Test
    void everyContentEnrollmentAndClassInviteEndpointHidesAnotherTeachersResources() throws Exception {
        String chapterRequest = "{\"title\":\"Intrus " + stamp + "\"}";
        String bookRequest = "{\"title\":\"Intrus " + stamp + "\"}";
        String exerciseRequest = "{\"statement\":\"Intrus\"}";

        String classSummary = mockMvc.perform(get("/api/admin/classes/summary").with(asAna()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(classSummary).doesNotContain(classB.getName());
        String chapterCatalog = mockMvc.perform(get("/api/admin/chapters").with(asAna()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(chapterCatalog).doesNotContain(chapterB.getTitle());
        mockMvc.perform(put("/api/admin/classes/" + classB.getId()).with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Intrus\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/admin/classes/" + classB.getId()).with(asAna()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/classes/" + classB.getId() + "/books").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(bookRequest))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/admin/books/" + bookB.getId()).with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(bookRequest))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/admin/books/" + bookB.getId()).with(asAna()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/books/" + bookB.getId() + "/chapters").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(chapterRequest))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/admin/chapters/" + chapterB.getId()).with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(chapterRequest))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/admin/chapters/" + chapterB.getId()).with(asAna()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/chapters/" + chapterB.getId() + "/move").param("up", "true")
                        .with(asAna()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/chapters/" + chapterB.getId() + "/exercises")
                        .with(asAna()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(exerciseRequest))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/admin/exercises/" + exerciseB.getId()).with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statement\":\"Intrus\",\"version\":" + exerciseB.getVersion() + "}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/admin/exercises/" + exerciseB.getId()).with(asAna()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/classes/" + classB.getId() + "/enrollments")
                        .with(asAna()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentId\":" + studentB.getId() + "}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/classes/" + classB.getId() + "/enrollments").with(asAna()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/admin/enrollments/" + enrollmentB.getId()).with(asAna()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/classes/" + classB.getId() + "/invites").with(asAna()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/classes/" + classB.getId() + "/invites")
                        .with(asAna()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/users/" + studentB.getId() + "/parent-invite")
                        .with(asAna()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/admin/invite-links/" + inviteB.getId()).with(asAna()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/classes/" + classB.getId() + "/grades.csv").with(asAna()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/classes/" + classB.getId() + "/mastery").with(asAna()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/classes/" + classB.getId() + "/en-mastery").with(asAna()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/classes/" + classB.getId() + "/review").with(asAna()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/classes/" + classB.getId() + "/tutor-questions").with(asAna()))
                .andExpect(status().isNotFound());
    }

    @Test
    void aTeacherCannotStartAnAiImportInsideAnotherTeachersClassOrChapter() throws Exception {
        MockMultipartFile page = new MockMultipartFile("files", "page.png", "image/png", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/admin/ai-imports").file(page)
                        .param("schoolClassId", classB.getId().toString()).with(asAna()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(multipart("/api/admin/ai-imports").file(page)
                        .param("chapterId", chapterB.getId().toString()).with(asAna()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void aTeacherCannotCreateRecapTestsForAnotherTeachersClass() throws Exception {
        mockMvc.perform(post("/api/admin/recap/quizzes").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Intrus\",\"exerciseIds\":[1],\"schoolClassId\":"
                                + classB.getId() + "}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/admin/recap/catch-up").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schoolClassId\":" + classB.getId()
                                + ",\"enChapterId\":1,\"studentIds\":[1],\"count\":1,\"dueAt\":\""
                                + Instant.now().plusSeconds(86400) + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void explanationRequestsAreFilteredToTheTeachersOwnQuizzes() throws Exception {
        jdbcTemplate.update("INSERT INTO mistake_explanation_requests (option_id, student_id, requested_at) VALUES (?, ?, ?)",
                optionB.getId(), studentB.getId(), Timestamp.from(Instant.now()));

        mockMvc.perform(get("/api/admin/explanation-requests").with(asAna()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].quizId", not(hasItem(quizB.getId().intValue()))));
        mockMvc.perform(get("/api/admin/explanation-requests").with(as(bogdan, "TEACHER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].quizId", hasItem(quizB.getId().intValue())));
    }

    @Test
    void everyQuizItemAndGradingEndpointHidesAnotherTeachersQuizAndPaper() throws Exception {
        String quizPath = "/api/admin/quizzes/" + quizB.getId();
        String itemPath = "/api/admin/quiz-items/" + itemB.getId();
        String attemptPath = "/api/admin/quiz/attempts/" + attemptB.getId();
        String itemRequest = "{\"type\":\"OPEN\",\"position\":0,\"statement\":\"Intrus\",\"points\":1}";
        String quizRequest = "{\"title\":\"Intrus\",\"schoolClassId\":" + classB.getId() + "}";

        mockMvc.perform(get("/api/admin/quizzes").with(asAna()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItem(quizB.getId().intValue()))));
        mockMvc.perform(get("/api/admin/quizzes/summary").with(asAna()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].quizId", not(hasItem(quizB.getId().intValue()))));
        mockMvc.perform(get(quizPath).with(asAna())).andExpect(status().isNotFound());
        mockMvc.perform(get(quizPath + "/shuffle").with(asAna())).andExpect(status().isNotFound());
        mockMvc.perform(put(quizPath + "/shuffle").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\":true,\"options\":true}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/quizzes").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(quizRequest))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(quizPath).with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(quizRequest))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(quizPath + "/copy").with(asAna()).with(csrf())).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/chapters/" + chapterB.getId() + "/quiz").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Intrus\",\"exerciseIds\":[" + exerciseB.getId() + "],\"points\":1}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(quizPath + "/publish").with(asAna()).with(csrf())).andExpect(status().isNotFound());
        mockMvc.perform(get(quizPath + "/activity").with(asAna())).andExpect(status().isNotFound());
        mockMvc.perform(post(quizPath + "/unpublish").with(asAna()).with(csrf())).andExpect(status().isNotFound());
        mockMvc.perform(delete(quizPath).with(asAna()).with(csrf())).andExpect(status().isNotFound());
        mockMvc.perform(post(quizPath + "/items").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(itemRequest))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(itemPath).with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(itemRequest))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(quizPath + "/en-chapters").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(itemPath + "/move").param("up", "true").with(asAna()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(itemPath + "/copy").with(asAna()).with(csrf())).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/admin/quiz-options/" + optionB.getId() + "/mistake").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"mistake\":\"Intrus\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(itemPath).with(asAna()).with(csrf())).andExpect(status().isNotFound());

        mockMvc.perform(get(quizPath + "/stats").with(asAna())).andExpect(status().isNotFound());
        mockMvc.perform(put(itemPath + "/correct-option").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"optionId\":" + optionB.getId() + "}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(itemPath + "/correct-number").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"1\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(multipart(itemPath + "/image")
                        .file(new MockMultipartFile("file", "question.png", "image/png", new byte[]{1, 2, 3}))
                        .with(asAna()).with(csrf()).with(request -> { request.setMethod("PUT"); return request; }))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(itemPath + "/image").with(asAna()).with(csrf())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/admin/quiz/attempts/" + attemptB.getId() + "/ai-proposals").with(asAna()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(quizPath + "/ai-proposals").with(asAna())).andExpect(status().isNotFound());
        mockMvc.perform(put(quizPath + "/ai-proposals").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"auto\":true}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(attemptPath + "/responses/" + itemB.getId() + "/ai-proposal")
                        .with(asAna()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(attemptPath + "/extra-attempt").with(asAna()).with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(attemptPath).with(asAna())).andExpect(status().isNotFound());
        mockMvc.perform(get(attemptPath + "/responses/" + itemB.getId() + "/photos/999").with(asAna()))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(attemptPath + "/responses/" + itemB.getId() + "/grade").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"points\":0}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(attemptPath + "/comment").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"Intrus\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(attemptPath + "/mark-graded").with(asAna()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(attemptPath + "/reopen").with(asAna()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(itemPath + "/tutor-hints").with(asAna()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/quiz-options/" + optionB.getId() + "/mistake-suggestion")
                        .with(asAna()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/quiz-options/" + optionB.getId() + "/mistake-from-lessons")
                        .with(asAna()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(multipart("/api/admin/quizzes/" + quizB.getId() + "/barem-reads")
                        .file(new MockMultipartFile("files", "barem.png", "image/png", new byte[]{1, 2, 3}))
                        .with(asAna()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(quizPath + "/en-chapter-suggestions").with(asAna()).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(quizPath + "/barem-reads/999999").with(asAna()))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(quizPath + "/barem").with(asAna()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"itemId\":" + itemB.getId() + "}]}"))
                .andExpect(status().isNotFound());
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
