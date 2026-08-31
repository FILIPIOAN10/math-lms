package ro.mathlms.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import ro.mathlms.TestcontainersConfiguration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC on {@code /api/admin/**}: anonymous callers get 401, authenticated non-admins
 * get 403, and only ADMIN is let through. Enforced by the URL rule in SecurityConfig
 * and the method-level {@code @PreAuthorize} — both are exercised here.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AdminUserRbacTest {

    @Autowired
    private MockMvc mockMvc;

    // --- anonymous -> 401 ---

    @Test
    @WithAnonymousUser
    void anonymousCannotListPending() throws Exception {
        mockMvc.perform(get("/api/admin/users/pending"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithAnonymousUser
    void anonymousCannotApprove() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\"}"))
                .andExpect(status().isUnauthorized());
    }

    // --- authenticated non-admin -> 403 ---

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotListPending() throws Exception {
        mockMvc.perform(get("/api/admin/users/pending"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PARENT")
    void parentCannotApprove() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void studentCannotLinkParent() throws Exception {
        mockMvc.perform(post("/api/admin/users/1/link-parent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":2}"))
                .andExpect(status().isForbidden());
    }

    // --- admin -> allowed through RBAC (200 on an empty pending list) ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanListPending() throws Exception {
        mockMvc.perform(get("/api/admin/users/pending"))
                .andExpect(status().isOk());
    }
}
