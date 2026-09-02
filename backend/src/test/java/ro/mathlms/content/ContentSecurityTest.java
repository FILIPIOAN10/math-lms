package ro.mathlms.content;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security rules for the content hierarchy: reads need an active account, writes need
 * ADMIN. Uses classes as the representative resource (books/chapters/exercises share the
 * same {@code /api/...} read + {@code /api/admin/...} write layout).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ContentSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SchoolClassService schoolClassService;

    @Test
    @WithAnonymousUser
    void anonymousCannotReadClasses() throws Exception {
        mockMvc.perform(get("/api/classes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "pending", authorities = {"ROLE_STUDENT"})
    void nonActiveAccountCannotReadClasses() throws Exception {
        // Authenticated but without STATUS_ACTIVE (a pending student).
        mockMvc.perform(get("/api/classes"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "ana", authorities = {"ROLE_STUDENT", "STATUS_ACTIVE"})
    void activeStudentCanReadClasses() throws Exception {
        mockMvc.perform(get("/api/classes"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "ana", roles = "STUDENT")
    void studentCannotCreateClass() throws Exception {
        mockMvc.perform(post("/api/admin/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Clasa a 9-a\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanCreateClass() throws Exception {
        when(schoolClassService.create(anyString(), any()))
                .thenReturn(new SchoolClass("Clasa a 9-a", null));

        mockMvc.perform(post("/api/admin/classes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Clasa a 9-a\"}"))
                .andExpect(status().isCreated());
    }
}
