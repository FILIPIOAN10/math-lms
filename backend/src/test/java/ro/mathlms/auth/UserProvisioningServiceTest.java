package ro.mathlms.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

class UserProvisioningServiceTest {

    private UserRepository userRepository;
    private UserProvisioningService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        AuthProperties properties = new AuthProperties(
                List.of("profesor@gmail.com"),
                List.of("elev@gmail.com"),
                "test-secret-at-least-32-characters-long!!",
                60);
        service = new UserProvisioningService(userRepository, properties);
    }

    @Test
    void createsAdminForConfiguredAdminEmail() {
        when(userRepository.findByEmail("profesor@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = service.provision("profesor@gmail.com", "Prof Ion");

        assertThat(result.getRole()).isEqualTo(Role.ADMIN);
        assertThat(result.getEmail()).isEqualTo("profesor@gmail.com");
    }

    @Test
    void createsStudentForAllowedEmail() {
        when(userRepository.findByEmail("elev@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = service.provision("elev@gmail.com", "Ana Pop");

        assertThat(result.getRole()).isEqualTo(Role.STUDENT);
    }

    @Test
    void reusesExistingUserWithoutSaving() {
        User existing = new User("profesor@gmail.com", "Prof Ion", Role.ADMIN);
        when(userRepository.findByEmail("profesor@gmail.com")).thenReturn(Optional.of(existing));

        User result = service.provision("profesor@gmail.com", "Prof Ion");

        assertThat(result).isSameAs(existing);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void normalizesEmailToLowercase() {
        when(userRepository.findByEmail("profesor@gmail.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User result = service.provision("Profesor@Gmail.COM", "Prof Ion");

        assertThat(result.getEmail()).isEqualTo("profesor@gmail.com");
    }

    @Test
    void rejectsEmailNotOnAnyList() {
        when(userRepository.findByEmail("strain@gmail.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.provision("strain@gmail.com", "Cineva"))
                .isInstanceOf(EmailNotAllowedException.class)
                .hasMessageContaining("strain@gmail.com");

        verify(userRepository, never()).save(any(User.class));
    }
}
