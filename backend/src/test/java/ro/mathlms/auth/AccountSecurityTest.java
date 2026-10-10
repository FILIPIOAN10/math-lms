package ro.mathlms.auth;

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
import ro.mathlms.user.Role;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Contul meu" endpoints: any APPROVED account, whatever its role, and always the caller's own account (the email comes
 * from the principal, never from the request).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AccountSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    private static final String CHANGE = "{\"currentPassword\":\"parola-veche\",\"newPassword\":\"parola-noua-123\"}";

    @Test
    @WithAnonymousUser
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/me/account")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "nou@scoala.ro", authorities = {"ROLE_STUDENT"})
    void anAccountNotApprovedYetIsForbidden() throws Exception {
        mockMvc.perform(get("/api/me/account")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "maria@scoala.ro", authorities = {"STATUS_ACTIVE", "ROLE_PARENT"})
    void anyApprovedRoleReadsItsOwnAccount() throws Exception {
        when(accountService.account("maria@scoala.ro"))
                .thenReturn(new AccountDto("maria@scoala.ro", "Maria Parinte", Role.PARENT, true, false));

        mockMvc.perform(get("/api/me/account"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Maria Parinte"))
                .andExpect(jsonPath("$.hasPassword").value(true));
    }

    @Test
    @WithMockUser(username = "ana@scoala.ro", authorities = {"STATUS_ACTIVE", "ROLE_STUDENT"})
    void changesTheCallersOwnPassword() throws Exception {
        mockMvc.perform(post("/api/me/password").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(CHANGE))
                .andExpect(status().isNoContent());

        verify(accountService).changePassword(eq("ana@scoala.ro"), eq("parola-veche"), eq("parola-noua-123"), any());
    }

    @Test
    @WithMockUser(username = "ana@scoala.ro", authorities = {"STATUS_ACTIVE", "ROLE_STUDENT"})
    void aShortNewPasswordIsRejectedBeforeTheService() throws Exception {
        mockMvc.perform(post("/api/me/password").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"parola-veche\",\"newPassword\":\"scurta\"}"))
                .andExpect(status().isBadRequest());

        verify(accountService, never()).changePassword(anyString(), anyString(), anyString(), any());
    }

    @Test
    @WithMockUser(username = "ana@scoala.ro", authorities = {"STATUS_ACTIVE", "ROLE_STUDENT"})
    void aWrongCurrentPasswordIsA400WithTheReason() throws Exception {
        // 400, not 401: a 401 would make the SPA try a token refresh and replay the request
        doThrow(new PasswordChangeException("Parola actuală nu e corectă."))
                .when(accountService).changePassword(anyString(), anyString(), anyString(), any());

        mockMvc.perform(post("/api/me/password").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(CHANGE))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Parola actuală nu e corectă."));
    }

    @Test
    @WithMockUser(username = "ana@scoala.ro", authorities = {"STATUS_ACTIVE", "ROLE_STUDENT"})
    void changingThePasswordNeedsTheCsrfToken() throws Exception {
        mockMvc.perform(post("/api/me/password").contentType(MediaType.APPLICATION_JSON).content(CHANGE))
                .andExpect(status().isForbidden());
    }
}
