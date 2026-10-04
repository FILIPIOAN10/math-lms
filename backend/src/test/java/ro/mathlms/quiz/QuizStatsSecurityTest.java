package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import ro.mathlms.TestcontainersConfiguration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The teacher's quiz statistics are ADMIN-only; a quiz that does not exist answers 404. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class QuizStatsSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithAnonymousUser
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/quizzes/1/stats")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_STUDENT", "STATUS_ACTIVE"})
    void aStudentIsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/quizzes/1/stats")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_PARENT", "STATUS_ACTIVE"})
    void aParentIsForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/quizzes/1/stats")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {"ROLE_ADMIN", "STATUS_ACTIVE"})
    void anAdminGetsNotFoundForAnUnknownQuiz() throws Exception {
        mockMvc.perform(get("/api/admin/quizzes/999999/stats")).andExpect(status().isNotFound());
    }
}
