package ro.mathlms.auth;

import jakarta.validation.constraints.NotNull;
import ro.mathlms.user.Role;

/** Admin picks which role the invite link will grant to whoever registers with it. */
public record CreateInviteRequest(

        @NotNull
        Role role
) {
}
