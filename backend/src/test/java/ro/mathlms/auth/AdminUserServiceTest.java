package ro.mathlms.auth;

import org.junit.jupiter.api.Test;
import ro.mathlms.user.AccountStatus;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminUserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AdminUserService service = new AdminUserService(userRepository);

    @Test
    void listPendingReturnsAccountsAwaitingApproval() {
        User pending = User.registerLocal("ana@example.com", "Ana Pop", "hash", Role.STUDENT);
        when(userRepository.findByStatus(AccountStatus.PENDING_APPROVAL))
                .thenReturn(List.of(pending));

        List<User> result = service.listPending();

        assertThat(result).containsExactly(pending);
        verify(userRepository).findByStatus(AccountStatus.PENDING_APPROVAL);
    }
}
