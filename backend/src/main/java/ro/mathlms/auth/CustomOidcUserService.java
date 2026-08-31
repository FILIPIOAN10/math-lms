package ro.mathlms.auth;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
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

        String googleId = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        String fullName = oidcUser.getFullName();

        User user = provisioningService.provision(googleId, email, fullName, currentInviteToken());

        return new AppOidcUser(oidcUser, user);
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
