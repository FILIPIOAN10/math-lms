package ro.mathlms.invite;

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
import ro.mathlms.invite.InviteLinkDtos.InviteLinkDto;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Minting and revoking invite links is ADMIN-only; only the preview is public. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class InviteLinkSecurityTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private InviteLinkService service;

    @Test
    @WithAnonymousUser
    void anonymousCannotManageLinks() throws Exception {
        mockMvc.perform(get("/api/admin/classes/1/invites")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/classes/1/invites").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/users/1/parent-invite").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/invite-links/1").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "STUDENT")
    void aStudentCannotMintALinkForAClass() throws Exception {
        mockMvc.perform(post("/api/admin/classes/1/invites").with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/users/1/parent-invite").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void anAdminMintsAClassLinkWithOrWithoutACap() throws Exception {
        when(service.createClassInvite(1L, null)).thenReturn(new InviteLinkDto(9L, InviteLinkKind.CLASS,
                "http://localhost:5173/register?token=x", 0, 30, Instant.now(), Instant.now(), "Clasa a 8-a", null));

        mockMvc.perform(post("/api/admin/classes/1/invites").with(csrf())).andExpect(status().isCreated());
        verify(service).createClassInvite(1L, null);

        mockMvc.perform(post("/api/admin/classes/1/invites").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"maxUses\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/classes/1/invites").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"maxUses\":201}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void anAdminListsAndRevokes() throws Exception {
        when(service.activeClassInvites(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/classes/1/invites")).andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/invite-links/3").with(csrf())).andExpect(status().isNoContent());
        verify(service).revoke(3L);
    }

    @Test
    @WithAnonymousUser
    void thePreviewIsPublic() throws Exception {
        // A junk code is answered by the feature (410), not by the security chain (401).
        when(service.preview("0".repeat(24))).thenThrow(new InviteUnavailableException("mort"));

        mockMvc.perform(get("/api/public/invites/preview").param("token", "0".repeat(24)))
                .andExpect(status().isGone());
    }
}
