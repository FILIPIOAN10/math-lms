package ro.mathlms.auth;

import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import ro.mathlms.invite.InviteUnavailableException;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A Google sign-in the app refuses has to be an authentication failure: anything else leaves Spring's login filter
 * unhandled and the visitor gets the server's raw error page instead of the login page.
 */
class CustomOidcUserServiceTest {

    private final UserProvisioningService provisioning = mock(UserProvisioningService.class);
    private final CustomOidcUserService service = new CustomOidcUserService(provisioning);

    private static OidcUserRequest signIn() {
        Instant now = Instant.now();
        ClientRegistration google = ClientRegistration.withRegistrationId("google")
                .clientId("id")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/login/oauth2/code/google")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .build();
        return new OidcUserRequest(google,
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "acces", now, now.plusSeconds(60)),
                new OidcIdToken("token", now, now.plusSeconds(60),
                        Map.of("sub", "g-1", "email", "nou@gmail.com", "name", "Elev Nou")));
    }

    private OAuth2AuthenticationException refusal() {
        return catchThrowableOfType(OAuth2AuthenticationException.class, () -> service.loadUser(signIn()));
    }

    @Test
    void anEmailWithNoAccountIsRefusedWithAReasonTheLoginPageExplains() {
        when(provisioning.provision(any(), any(), any(), any())).thenThrow(new EmailNotAllowedException("nou@gmail.com"));

        assertThat(refusal().getError().getErrorCode()).isEqualTo(GoogleLoginFailureHandler.NO_ACCOUNT);
    }

    @Test
    void aStoredInvitationThatIsUsedUpOrRevokedIsRefused() {
        when(provisioning.provision(any(), any(), any(), any()))
                .thenThrow(new InviteUnavailableException("Linkul nu mai e valabil."));

        assertThat(refusal().getError().getErrorCode()).isEqualTo(GoogleLoginFailureHandler.INVITE_UNAVAILABLE);
    }

    @Test
    void aSignedInvitationThatExpiredOrWasTamperedWithIsRefused() {
        when(provisioning.provision(any(), any(), any(), any())).thenThrow(new MalformedJwtException("stricat"));

        assertThat(refusal().getError().getErrorCode()).isEqualTo(GoogleLoginFailureHandler.INVITE_UNAVAILABLE);
    }
}
