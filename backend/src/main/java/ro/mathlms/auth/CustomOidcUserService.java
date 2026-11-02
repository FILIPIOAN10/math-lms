package ro.mathlms.auth;

import io.jsonwebtoken.JwtException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import ro.mathlms.invite.InviteUnavailableException;
import ro.mathlms.user.User;

@Service
public class CustomOidcUserService extends OidcUserService {

    private final UserProvisioningService provisioningService;

    public CustomOidcUserService(UserProvisioningService provisioningService) {
        this.provisioningService = provisioningService;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = super.loadUser(userRequest);

        // The email decides whose account this is, and whether it is an admin's (ADMIN_EMAILS): only one Google vouches for.
        if (!Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
            throw new OAuth2AuthenticationException(new OAuth2Error(GoogleLoginFailureHandler.EMAIL_NOT_VERIFIED));
        }

        String googleId = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        String fullName = oidcUser.getFullName();

        try {
            User user = provisioningService.provision(googleId, email, fullName, currentInviteToken());
            return new AppOidcUser(oidcUser, user);
        } catch (EmailNotAllowedException e) {
            throw refused(GoogleLoginFailureHandler.NO_ACCOUNT, e);
        } catch (InviteUnavailableException | JwtException e) {
            throw refused(GoogleLoginFailureHandler.INVITE_UNAVAILABLE, e);
        }
    }

    /** Only an authentication failure reaches {@link GoogleLoginFailureHandler}; anything else is the raw error page. */
    private static OAuth2AuthenticationException refused(String reason, RuntimeException cause) {
        return new OAuth2AuthenticationException(new OAuth2Error(reason), cause);
    }

    /**
     * The invite token, if any, surfaced onto the current request by
     * {@link InviteCapturingAuthorizationRequestRepository} during the OAuth callback.
     */
    private String currentInviteToken() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        Object invite = attributes.getAttribute(
                InviteAwareAuthorizationRequestResolver.INVITE_ATTRIBUTE,
                RequestAttributes.SCOPE_REQUEST);
        return invite instanceof String s ? s : null;
    }
}
