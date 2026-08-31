package ro.mathlms.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    void createsUserWithValidData() {
        User user = new User("ana@scoala.ro", "Ana Pop", Role.STUDENT);

        assertThat(user.getEmail()).isEqualTo("ana@scoala.ro");
        assertThat(user.getFullName()).isEqualTo("Ana Pop");
        assertThat(user.getRole()).isEqualTo(Role.STUDENT);
        assertThat(user.getId()).isNull(); // id vine abia după salvare în DB
    }

    @Test
    void rejectsBlankEmail() {
        assertThatThrownBy(() -> new User("  ", "Ana Pop", Role.STUDENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @Test
    void rejectsNullEmail() {
        assertThatThrownBy(() -> new User(null, "Ana Pop", Role.STUDENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("email");
    }

    @Test
    void rejectsBlankFullName() {
        assertThatThrownBy(() -> new User("ana@scoala.ro", "", Role.STUDENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fullName");
    }

    @Test
    void rejectsNullRole() {
        assertThatThrownBy(() -> new User("ana@scoala.ro", "Ana Pop", null))
                .isInstanceOf(NullPointerException.class);
    }

    // --- Step 1.6c: onboarding state transitions ---

    private User pendingUser() {
        return new User("ana@scoala.ro", "Ana Pop", Role.STUDENT);
    }

    @Test
    void newUserStartsPendingVerification() {
        User user = pendingUser();

        assertThat(user.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(user.isEmailVerified()).isFalse();
    }

    @Test
    void verifyEmailMovesToPendingApproval() {
        User user = pendingUser();

        user.verifyEmail();

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getStatus()).isEqualTo(AccountStatus.PENDING_APPROVAL);
    }

    @Test
    void verifyEmailTwiceIsRejected() {
        User user = pendingUser();
        user.verifyEmail();

        assertThatThrownBy(user::verifyEmail)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void approveActivatesAndAssignsRole() {
        User user = pendingUser();
        user.verifyEmail();

        user.approve(Role.STUDENT);

        assertThat(user.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.getRole()).isEqualTo(Role.STUDENT);
    }

    @Test
    void approveFromWrongStateIsRejected() {
        User user = pendingUser(); // still PENDING_VERIFICATION

        assertThatThrownBy(() -> user.approve(Role.STUDENT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void approveWithNullRoleIsRejected() {
        User user = pendingUser();
        user.verifyEmail();

        assertThatThrownBy(() -> user.approve(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectMarksAccountRejected() {
        User user = pendingUser();
        user.verifyEmail();

        user.reject();

        assertThat(user.getStatus()).isEqualTo(AccountStatus.REJECTED);
    }

    @Test
    void rejectAfterActivationIsRejected() {
        User user = pendingUser();
        user.verifyEmail();
        user.approve(Role.STUDENT);

        assertThatThrownBy(user::reject)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void linkGoogleRejectsBlankId() {
        assertThatThrownBy(() -> pendingUser().linkGoogle(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("googleId");
    }

    @Test
    void setPasswordRejectsBlankHash() {
        assertThatThrownBy(() -> pendingUser().setPassword("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("password");
    }

    // --- Step 1.6l: linking a student to a parent ---

    @Test
    void linkParentAttachesTheParentAccount() {
        User student = new User("copil@scoala.ro", "Copil Pop", Role.STUDENT);
        User parent = new User("parinte@scoala.ro", "Parinte Pop", Role.PARENT);

        student.linkParent(parent);

        assertThat(student.getParent()).isSameAs(parent);
    }

    @Test
    void linkParentRejectsWhenThisAccountIsNotStudent() {
        User notStudent = new User("prof@scoala.ro", "Prof Ion", Role.ADMIN);
        User parent = new User("parinte@scoala.ro", "Parinte Pop", Role.PARENT);

        assertThatThrownBy(() -> notStudent.linkParent(parent))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void linkParentRejectsWhenTargetIsNotParent() {
        User student = new User("copil@scoala.ro", "Copil Pop", Role.STUDENT);
        User notParent = new User("altul@scoala.ro", "Alt Elev", Role.STUDENT);

        assertThatThrownBy(() -> student.linkParent(notParent))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void linkParentRejectsSelfLink() {
        User student = new User("copil@scoala.ro", "Copil Pop", Role.STUDENT);

        assertThatThrownBy(() -> student.linkParent(student))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void linkParentRejectsNull() {
        User student = new User("copil@scoala.ro", "Copil Pop", Role.STUDENT);

        assertThatThrownBy(() -> student.linkParent(null))
                .isInstanceOf(NullPointerException.class);
    }
}