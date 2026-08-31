package ro.mathlms.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import ro.mathlms.user.AccountStatus;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminUserControllerTest {

    private final AdminUserService adminUserService = mock(AdminUserService.class);
    private final AdminUserController controller = new AdminUserController(adminUserService);

    @Test
    void pendingMapsServiceResultToDtos() {
        User ana = User.registerLocal("ana@example.com", "Ana Pop", "hash", Role.STUDENT);
        when(adminUserService.listPending()).thenReturn(List.of(ana));

        ResponseEntity<List<PendingUserDto>> response = controller.pending();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).singleElement().satisfies(dto -> {
            assertThat(dto.email()).isEqualTo("ana@example.com");
            assertThat(dto.fullName()).isEqualTo("Ana Pop");
            assertThat(dto.requestedRole()).isEqualTo(Role.STUDENT);
            assertThat(dto.emailVerified()).isFalse();
        });
    }

    @Test
    void pendingReturnsEmptyListWhenNoneAwaitApproval() {
        when(adminUserService.listPending()).thenReturn(List.of());

        ResponseEntity<List<PendingUserDto>> response = controller.pending();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void approveReturnsTheActivatedAccount() {
        User activated = User.registerLocal("dan@example.com", "Dan Ilie", "hash", Role.PARENT);
        activated.verifyEmail();
        activated.approve(Role.STUDENT);
        when(adminUserService.approve(7L, Role.STUDENT)).thenReturn(activated);

        ResponseEntity<UserDto> response = controller.approve(7L, new ApproveRequest(Role.STUDENT));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().role()).isEqualTo(Role.STUDENT);
        assertThat(response.getBody().status()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void rejectReturnsTheRejectedAccount() {
        User rejected = User.registerLocal("eva@example.com", "Eva Marin", "hash", Role.STUDENT);
        rejected.reject();
        when(adminUserService.reject(3L)).thenReturn(rejected);

        ResponseEntity<UserDto> response = controller.reject(3L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(AccountStatus.REJECTED);
    }

    @Test
    void linkParentDelegatesAndReturnsTheStudent() {
        User student = new User("copil@example.com", "Copil Pop", Role.STUDENT);
        when(adminUserService.linkParent(1L, 2L)).thenReturn(student);

        ResponseEntity<UserDto> response =
                controller.linkParent(1L, new LinkParentRequest(2L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().email()).isEqualTo("copil@example.com");
    }
}
