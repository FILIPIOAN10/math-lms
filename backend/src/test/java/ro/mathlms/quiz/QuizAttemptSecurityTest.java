package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ro.mathlms.TestcontainersConfiguration;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The student quiz-taking API is gated twice: the URL needs STATUS_ACTIVE (an approved account)
 * and the controller needs the STUDENT role. Both must hold.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class QuizAttemptSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuizAttemptService service;

    @Test
    @WithAnonymousUser
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/quiz/quizzes")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_STUDENT"})
    void notYetApprovedStudentIsForbidden() throws Exception {
        // Authenticated but not STATUS_ACTIVE — the URL rule rejects it.
        mockMvc.perform(get("/api/quiz/quizzes")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"STATUS_ACTIVE", "ROLE_PARENT"})
    void activeParentIsForbidden() throws Exception {
        // Passes the URL rule but fails the controller's hasRole('STUDENT').
        mockMvc.perform(get("/api/quiz/quizzes")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"STATUS_ACTIVE", "ROLE_STUDENT"})
    void activeStudentCanListQuizzes() throws Exception {
        when(service.listPublished()).thenReturn(List.of());
        mockMvc.perform(get("/api/quiz/quizzes")).andExpect(status().isOk());
    }
}
