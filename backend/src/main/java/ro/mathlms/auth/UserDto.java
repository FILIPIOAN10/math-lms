package ro.mathlms.auth;

import ro.mathlms.user.AccountStatus;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;

/**
 * What the frontend hydrates from {@code /api/auth/me}. {@code status} lets the SPA
 * route a PENDING account to the waiting screen instead of the dashboard;
 * {@code role} is null until an admin approves the account.
 */
public record UserDto(
        String email,
        String fullName,
        Role role,
        AccountStatus status,
        /** F8: two-step sign-in is on for this account. */
        boolean twoFactorEnabled,
        /** F8: an ADMIN who must set it up before the staff pages open. */
        boolean twoFactorSetupRequired
) {
    public static UserDto from(User user) {
        return from(user, false);
    }

    public static UserDto from(User user, boolean twoFactorSetupRequired) {
        return new UserDto(user.getEmail(), user.getFullName(), user.getRole(), user.getStatus(),
                user.isTwoFactorEnabled(), twoFactorSetupRequired);
    }
}
