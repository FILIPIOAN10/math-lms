package ro.mathlms.auth;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only endpoint that mints role-scoped invite links. Access is restricted to
 * ADMIN by the {@code /api/admin/**} rule in {@link SecurityConfig}. The link points
 * at the frontend register page, which forwards the embedded token back to the API.
 */
@RestController
@RequestMapping("/api/admin/invites")
public class InviteController {

    private final InviteTokenService inviteTokenService;
    private final String frontendBaseUrl;

    public InviteController(InviteTokenService inviteTokenService,
                            @Value("${app.frontend.base-url}") String frontendBaseUrl) {
        this.inviteTokenService = inviteTokenService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @PostMapping
    public ResponseEntity<InviteResponse> create(@Valid @RequestBody CreateInviteRequest request) {
        String token = inviteTokenService.generate(request.role());
        String url = frontendBaseUrl + "/register?token=" + token;
        return ResponseEntity.ok(new InviteResponse(request.role(), url));
    }
}
