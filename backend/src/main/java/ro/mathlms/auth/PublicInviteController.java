package ro.mathlms.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.invite.InviteLinkDtos.InvitePreviewDto;

/**
 * Lets the register page say what a link is for ("Te alături clasei Clasa a 8-a") and refuse a dead link before the
 * newcomer fills in the form. Public: the visitor has no account yet; the token itself is the secret.
 */
@RestController
public class PublicInviteController {

    private final InviteRedeemer inviteRedeemer;

    public PublicInviteController(InviteRedeemer inviteRedeemer) {
        this.inviteRedeemer = inviteRedeemer;
    }

    @GetMapping("/api/public/invites/preview")
    public InvitePreviewDto preview(@RequestParam String token) {
        return inviteRedeemer.preview(token);
    }
}
