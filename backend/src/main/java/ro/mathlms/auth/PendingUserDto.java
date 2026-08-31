package ro.mathlms.auth;

import ro.mathlms.user.Role;
import ro.mathlms.user.User;

/**
 * A pending account as the admin review screen sees it. {@code id} is the handle the
 * admin uses to approve/reject; {@code requestedRole} is what the applicant asked for
 * via the invite link (the admin confirms or overrides it on approval).
 */
public record PendingUserDto(
        Long id,
        String email,
        String fullName,
        Role requestedRole,
        boolean emailVerified
) {
    public static PendingUserDto from(User user) {
        return new PendingUserDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRequestedRole(),
                user.isEmailVerified());
    }
}
