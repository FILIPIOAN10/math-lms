package ro.mathlms.invite;

import ro.mathlms.user.Role;

/** What a stored invite link does for whoever joins through it, and the role it grants them. */
public enum InviteLinkKind {
    /** Joins a class: the newcomer becomes a STUDENT enrolled in it. */
    CLASS(Role.STUDENT),
    /** Belongs to one student: the newcomer becomes a PARENT linked to that student. */
    PARENT(Role.PARENT),
    /** Admin invite: the newcomer becomes an ADMIN/TEACHER. */
    ADMIN(Role.ADMIN);

    private final Role role;

    InviteLinkKind(Role role) {
        this.role = role;
    }

    public Role role() {
        return role;
    }
}
