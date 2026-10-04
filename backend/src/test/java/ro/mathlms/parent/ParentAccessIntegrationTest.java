package ro.mathlms.parent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAttempt;
import ro.mathlms.quiz.QuizAttemptRepository;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 5.1 — the "no data leaks" test: a parent sees ONLY their own children, on every endpoint,
 * even when they put someone else's ids in the URL. Real Postgres + the full security chain.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ParentAccessIntegrationTest {

    private static final String MARIA = "maria.parinte@scoala.ro"; // parent of Ana
    private static final String ION = "ion.parinte@scoala.ro";     // parent of Dan

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizAttemptRepository attemptRepository;

    private User ana;
    private User dan;
    private long anasAttemptId;
    private long dansAttemptId;

    private User student(String email, String name, User parent) {
        User student = new User(email, name, Role.STUDENT);
        student.linkParent(parent);
        return userRepository.saveAndFlush(student);
    }

    private long gradedAttempt(Quiz quiz, User student) {
        QuizAttempt attempt = new QuizAttempt(quiz, student);
        attempt.submit();
        attempt.markGraded(7);
        return attemptRepository.saveAndFlush(attempt).getId();
    }

    @BeforeEach
    void seed() {
        User maria = userRepository.saveAndFlush(new User(MARIA, "Maria Pop", Role.PARENT));
        User ion = userRepository.saveAndFlush(new User(ION, "Ion Rus", Role.PARENT));
        ana = student("ana.copil@scoala.ro", "Ana Pop", maria);
        dan = student("dan.copil@scoala.ro", "Dan Rus", ion);
        Quiz quiz = quizRepository.saveAndFlush(new Quiz("Simulare EN", null));
        anasAttemptId = gradedAttempt(quiz, ana);
        dansAttemptId = gradedAttempt(quiz, dan);
    }

    // --- a parent reads their own child ---

    @Test
    @WithMockUser(username = MARIA, authorities = {"ROLE_PARENT", "STATUS_ACTIVE"})
    void aParentListsOnlyTheirOwnChildren() throws Exception {
        mockMvc.perform(get("/api/parent/children"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[*].fullName", hasItem("Ana Pop")))
                .andExpect(jsonPath("$[*].fullName", not(hasItem("Dan Rus"))));
    }

    @Test
    @WithMockUser(username = MARIA, authorities = {"ROLE_PARENT", "STATUS_ACTIVE"})
    void aParentSeesTheirChildsAttemptsAndResult() throws Exception {
        mockMvc.perform(get("/api/parent/children/" + ana.getId() + "/attempts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].attemptId", hasItem((int) anasAttemptId)));

        mockMvc.perform(get("/api/parent/children/" + ana.getId() + "/attempts/" + anasAttemptId + "/result"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quizTitle").value("Simulare EN"));
    }

    // --- ... and nobody else's ---

    @Test
    @WithMockUser(username = MARIA, authorities = {"ROLE_PARENT", "STATUS_ACTIVE"})
    void aParentCannotReadAnotherParentsChild() throws Exception {
        mockMvc.perform(get("/api/parent/children/" + dan.getId() + "/attempts"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/parent/children/" + dan.getId() + "/attempts/" + dansAttemptId + "/result"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = MARIA, authorities = {"ROLE_PARENT", "STATUS_ACTIVE"})
    void anotherStudentsAttemptStaysHiddenEvenWithTheOwnChildsIdInTheUrl() throws Exception {
        mockMvc.perform(get("/api/parent/children/" + ana.getId() + "/attempts/" + dansAttemptId + "/result"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = MARIA, authorities = {"ROLE_PARENT", "STATUS_ACTIVE"})
    void anUnknownChildAndAnUnknownAttemptLookExactlyLikeForeignOnes() throws Exception {
        mockMvc.perform(get("/api/parent/children/999999/attempts"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/parent/children/" + ana.getId() + "/attempts/999999/result"))
                .andExpect(status().isForbidden());
    }

    // --- who may call /api/parent/** at all ---

    @Test
    @WithAnonymousUser
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/parent/children")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "ana.copil@scoala.ro", authorities = {"ROLE_STUDENT", "STATUS_ACTIVE"})
    void aStudentCannotUseTheParentApi() throws Exception {
        mockMvc.perform(get("/api/parent/children")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "prof@scoala.ro", authorities = {"ROLE_ADMIN", "STATUS_ACTIVE"})
    void anAdminCannotUseTheParentApi() throws Exception {
        mockMvc.perform(get("/api/parent/children")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = MARIA, authorities = {"ROLE_PARENT"})
    void aPendingParentIsForbidden() throws Exception {
        mockMvc.perform(get("/api/parent/children")).andExpect(status().isForbidden());
    }
}
