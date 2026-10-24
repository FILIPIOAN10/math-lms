package ro.mathlms.user;

/**
 * ADMIN runs the platform (accounts, approvals, journal, shared EN pages) and sees every class; TEACHER sees and changes
 * only the classes and tests they own ({@code teacher_id}); STUDENT and PARENT are the learners' side.
 */
public enum Role {
    ADMIN,
    TEACHER,
    STUDENT,
    PARENT
}
