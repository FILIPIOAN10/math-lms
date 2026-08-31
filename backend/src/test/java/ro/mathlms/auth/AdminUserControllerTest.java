package ro.mathlms.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
}
