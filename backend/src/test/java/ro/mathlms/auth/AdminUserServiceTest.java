package ro.mathlms.auth;

import org.junit.jupiter.api.Test;
import ro.mathlms.user.AccountStatus;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    @Test
    void listActiveByRoleReturnsActiveAccountsOfThatRole() {
        User parent = new User("parinte@example.com", "Parinte Pop", Role.PARENT);
        when(userRepository.findByStatusAndRoleFetchParent(AccountStatus.ACTIVE, Role.PARENT))
                .thenReturn(List.of(parent));

        List<User> result = service.listActiveByRole(Role.PARENT);

        assertThat(result).containsExactly(parent);
        verify(userRepository).findByStatusAndRoleFetchParent(AccountStatus.ACTIVE, Role.PARENT);
    }

    @Test
    void approveAssignsTheConfirmedRoleAndActivatesTheAccount() {
        User user = User.registerLocal("dan@example.com", "Dan Ilie", "hash", Role.PARENT);
        user.verifyEmail(); // now PENDING_APPROVAL
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        // Admin overrides the requested PARENT role with the real STUDENT role.
        User result = service.approve(7L, Role.STUDENT);

        assertThat(result.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(result.getRole()).isEqualTo(Role.STUDENT);
        verify(userRepository).save(user);
    }

    @Test
    void approveThrowsWhenAccountDoesNotExist() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(404L, Role.STUDENT))
                .isInstanceOf(UserNotFoundException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectMovesTheAccountToRejected() {
        User user = User.registerLocal("eva@example.com", "Eva Marin", "hash", Role.STUDENT);
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = service.reject(3L);

        assertThat(result.getStatus()).isEqualTo(AccountStatus.REJECTED);
        verify(userRepository).save(user);
    }

    @Test
    void rejectThrowsWhenAccountDoesNotExist() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reject(404L))
                .isInstanceOf(UserNotFoundException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void linkParentAttachesParentAndSavesTheStudent() {
        User student = new User("copil@example.com", "Copil Pop", Role.STUDENT);
        User parent = new User("parinte@example.com", "Parinte Pop", Role.PARENT);
        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        when(userRepository.findById(2L)).thenReturn(Optional.of(parent));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = service.linkParent(1L, 2L);

        assertThat(result.getParent()).isSameAs(parent);
        verify(userRepository).save(student);
    }

    @Test
    void linkParentThrowsWhenStudentDoesNotExist() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.linkParent(1L, 2L))
                .isInstanceOf(UserNotFoundException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void linkParentThrowsWhenParentDoesNotExist() {
        User student = new User("copil@example.com", "Copil Pop", Role.STUDENT);
        when(userRepository.findById(1L)).thenReturn(Optional.of(student));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.linkParent(1L, 2L))
                .isInstanceOf(UserNotFoundException.class);
        verify(userRepository, never()).save(any());
    }
}
