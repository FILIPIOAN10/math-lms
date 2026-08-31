package ro.mathlms.auth;

import jakarta.validation.constraints.NotNull;
import ro.mathlms.user.Role;

/** Admin's decision on a pending account: the real role to assign on approval. */
public record ApproveRequest(
        @NotNull Role role
) {
}
