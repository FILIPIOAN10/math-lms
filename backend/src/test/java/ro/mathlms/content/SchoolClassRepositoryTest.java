package ro.mathlms.content;

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
class SchoolClassRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private SchoolClassRepository repository;

    @Test
    void savesAndFindsByName() {
        repository.save(new SchoolClass("Clasa a 9-a", "Algebră"));

        Optional<SchoolClass> found = repository.findByName("Clasa a 9-a");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isNotNull();
        assertThat(found.get().getDescription()).isEqualTo("Algebră");
    }

    @Test
    void existsByNameReflectsPersistedRows() {
        repository.save(new SchoolClass("Clasa a 10-a", null));

        assertThat(repository.existsByName("Clasa a 10-a")).isTrue();
        assertThat(repository.existsByName("Clasa a 11-a")).isFalse();
    }

    @Test
    void enforcesUniqueName() {
        repository.saveAndFlush(new SchoolClass("Clasa a 9-a", "prima"));

        assertThatThrownBy(() ->
                repository.saveAndFlush(new SchoolClass("Clasa a 9-a", "a doua")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
