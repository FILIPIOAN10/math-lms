package ro.mathlms.invite;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import ro.mathlms.user.Role;

import java.time.Instant;

/** Request/response shapes of the invite-link API. */
public final class InviteLinkDtos {

    private InviteLinkDtos() {
    }

    /** A link as the teacher sees it: where it leads, how much of it is used, until when it works. */
    public record InviteLinkDto(
            Long id,
            InviteLinkKind kind,
            String url,
            int uses,
            int maxUses,
            Instant expiresAt,
            Instant createdAt,
            String className,   // CLASS links
            String studentName  // PARENT links
    ) {
    }

    /** Optional cap for a new class link; null = {@link InviteLink#DEFAULT_CLASS_USES}. */
    public record CreateClassInviteRequest(
            @Min(1) @Max(InviteLink.MAX_USES) Integer maxUses
    ) {
    }

    /**
     * What the register page may show before the form is filled in: the role the link grants, for a class link the
     * class, and whether the account will be active right after the email is confirmed ({@code direct}: a stored
     * class/parent link) or wait for the teacher's approval (a role invite). Nothing about a student — the page is public.
     */
    public record InvitePreviewDto(
            Role role,
            String className,
            boolean direct
    ) {
    }
}
