package ro.mathlms.auth;

import ro.mathlms.user.Role;

/** The shareable invite link (and the role it grants) returned to the admin. */
public record InviteResponse(
        Role role,
        String url
) {
}
