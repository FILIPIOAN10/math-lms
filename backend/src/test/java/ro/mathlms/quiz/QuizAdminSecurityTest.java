package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import ro.mathlms.TestcontainersConfiguration;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The admin quiz-builder API is ADMIN-only. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class QuizAdminSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuizAdminService service;

    @Test
    @WithAnonymousUser
    void anonymousCannotListQuizzes() throws Exception {
        mockMvc.perform(get("/api/admin/quizzes")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotCreateQuiz() throws Exception {
        mockMvc.perform(post("/api/admin/quizzes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Simulare\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanListQuizzes() throws Exception {
        when(service.listQuizzes()).thenReturn(List.of());
        mockMvc.perform(get("/api/admin/quizzes")).andExpect(status().isOk());
    }
}
