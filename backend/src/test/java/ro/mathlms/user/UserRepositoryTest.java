package ro.mathlms.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private UserRepository userRepository;

    @Test
    void savesAndFindsUserByEmail() {
        userRepository.save(new User("ana@scoala.ro", "Ana Pop", Role.STUDENT));

        Optional<User> found = userRepository.findByEmail("ana@scoala.ro");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isNotNull();
        assertThat(found.get().getFullName()).isEqualTo("Ana Pop");
        assertThat(found.get().getRole()).isEqualTo(Role.STUDENT);
    }

    @Test
    void returnsEmptyWhenEmailNotFound() {
        assertThat(userRepository.findByEmail("nimeni@scoala.ro")).isEmpty();
    }

    @Test
    void enforcesUniqueEmail() {
        userRepository.saveAndFlush(new User("dup@scoala.ro", "Prima", Role.STUDENT));

        assertThatThrownBy(() ->
                userRepository.saveAndFlush(new User("dup@scoala.ro", "A doua", Role.ADMIN)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}