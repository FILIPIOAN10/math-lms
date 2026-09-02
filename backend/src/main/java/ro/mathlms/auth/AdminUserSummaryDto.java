package ro.mathlms.auth;

import ro.mathlms.user.Role;
import ro.mathlms.user.User;

/**
 * A lightweight view of an active account for the admin linking screen. {@code id} is the
 * handle used to link a student to a parent; for a student, {@code parentId}/{@code parentName}
 * describe the parent they are already linked to (both null for parents or unlinked students).
 */
public record AdminUserSummaryDto(
        Long id,
        String email,
        String fullName,
        Role role,
        Long parentId,
        String parentName
) {
    public static AdminUserSummaryDto from(User user) {
        User parent = user.getParent();
        return new AdminUserSummaryDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                parent == null ? null : parent.getId(),
                parent == null ? null : parent.getFullName());
    }
}
