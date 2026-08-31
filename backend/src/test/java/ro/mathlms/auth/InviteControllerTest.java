package ro.mathlms.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import ro.mathlms.user.Role;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InviteControllerTest {

    private final InviteTokenService inviteTokenService = mock(InviteTokenService.class);
    private final InviteController controller =
            new InviteController(inviteTokenService, "https://app.mathlms.ro");

    @Test
    void buildsRegisterLinkCarryingTheGeneratedToken() {
        when(inviteTokenService.generate(Role.STUDENT)).thenReturn("INVITE_TOKEN");

        ResponseEntity<InviteResponse> response =
                controller.create(new CreateInviteRequest(Role.STUDENT));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().role()).isEqualTo(Role.STUDENT);
        assertThat(response.getBody().url())
                .isEqualTo("https://app.mathlms.ro/register?token=INVITE_TOKEN");
    }

    @Test
    void passesTheRequestedRoleThroughToTheTokenService() {
        when(inviteTokenService.generate(Role.PARENT)).thenReturn("PARENT_TOKEN");

        ResponseEntity<InviteResponse> response =
                controller.create(new CreateInviteRequest(Role.PARENT));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().role()).isEqualTo(Role.PARENT);
        assertThat(response.getBody().url()).contains("token=PARENT_TOKEN");
    }
}
