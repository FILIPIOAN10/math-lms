package ro.mathlms.assignment;

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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Managing homework is ADMIN-only; reading one's own list needs a signed-in account. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AssignmentSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private AssignmentService service;

    @Test
    @WithAnonymousUser
    void anonymousCannotListAssignments() throws Exception {
        mockMvc.perform(get("/api/admin/assignments")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/quiz/assignments")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void aStudentCannotManageAssignments() throws Exception {
        mockMvc.perform(get("/api/admin/assignments")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/assignments").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quizId\":1,\"schoolClassId\":1,\"dueAt\":\"2030-01-01T10:00:00Z\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void anAdminCanListAssignments() throws Exception {
        when(service.list()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/assignments")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aMissingFieldIsA400NotA500() throws Exception {
        mockMvc.perform(post("/api/admin/assignments").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quizId\":1}"))
                .andExpect(status().isBadRequest());
    }
}
