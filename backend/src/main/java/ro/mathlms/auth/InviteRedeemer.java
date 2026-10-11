package ro.mathlms.auth;

import io.jsonwebtoken.JwtException;
import org.springframework.stereotype.Service;
import ro.mathlms.invite.InviteLink;
import ro.mathlms.invite.InviteLinkDtos.InvitePreviewDto;
import ro.mathlms.invite.InviteLinkService;
import ro.mathlms.invite.InviteUnavailableException;
import ro.mathlms.user.Role;

/**
 * The one place that turns the {@code token} of a {@code /register?token=…} link into a role. Two kinds of link share
 * that URL: a stored class/parent link (T1 — capped, revocable, carries where the newcomer belongs) and the older
 * signed role invite (a JWT, nothing stored; the account then waits for approval). They are told apart by shape.
 */
@Service
public class InviteRedeemer {

    /** The role to register with and, for a stored link, the link to finish joining from (else null). */
    public record Redemption(Role role, Long inviteLinkId) {
    }

    private final InviteTokenService inviteTokenService;
    private final InviteLinkService inviteLinkService;

    public InviteRedeemer(InviteTokenService inviteTokenService, InviteLinkService inviteLinkService) {
        this.inviteTokenService = inviteTokenService;
        this.inviteLinkService = inviteLinkService;
    }

    /**
     * Uses the invite for a registration: a stored link gives up one of its uses (in the caller's transaction).
     *
     * @throws InviteUnavailableException for a stored link that is unknown, revoked, expired or full (410)
     * @throws JwtException               for a role invite that is tampered or expired (400)
     */
    public Redemption redeem(String token) {
        if (InviteLinkService.isStoredCode(token)) {
            InviteLink link = inviteLinkService.claim(token);
            return new Redemption(link.getKind().role(), link.getId());
        }
        return new Redemption(inviteTokenService.verify(token), null);
    }

    /** What the register page may show before the form is filled in; nothing is used up. 410 for a dead link. */
    public InvitePreviewDto preview(String token) {
        if (InviteLinkService.isStoredCode(token)) {
            return inviteLinkService.preview(token);
        }
        try {
            return new InvitePreviewDto(inviteTokenService.verify(token), null, false);
        } catch (JwtException e) {
            throw new InviteUnavailableException("Linkul de invitație nu e valid sau a expirat. Cere profesorului unul nou.");
        }
    }
}
