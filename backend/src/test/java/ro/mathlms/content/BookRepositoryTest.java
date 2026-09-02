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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class BookRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private SchoolClassRepository schoolClassRepository;

    @Test
    void findsBooksOfAClassOrderedByTitle() {
        SchoolClass ninth = schoolClassRepository.save(new SchoolClass("Clasa a 9-a", null));
        SchoolClass tenth = schoolClassRepository.save(new SchoolClass("Clasa a 10-a", null));
        bookRepository.save(new Book(ninth, "Beta", null));
        bookRepository.save(new Book(ninth, "Alfa", null));
        bookRepository.save(new Book(tenth, "Gama", null));

        List<Book> ninthBooks = bookRepository.findBySchoolClassIdOrderByTitle(ninth.getId());

        assertThat(ninthBooks).extracting(Book::getTitle).containsExactly("Alfa", "Beta");
    }

    @Test
    void enforcesUniqueTitleWithinAClass() {
        SchoolClass ninth = schoolClassRepository.save(new SchoolClass("Clasa a 9-a", null));
        bookRepository.saveAndFlush(new Book(ninth, "Manual M1", "prima"));

        assertThatThrownBy(() ->
                bookRepository.saveAndFlush(new Book(ninth, "Manual M1", "a doua")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsSameTitleInDifferentClasses() {
        SchoolClass ninth = schoolClassRepository.save(new SchoolClass("Clasa a 9-a", null));
        SchoolClass tenth = schoolClassRepository.save(new SchoolClass("Clasa a 10-a", null));
        bookRepository.saveAndFlush(new Book(ninth, "Manual M1", null));

        bookRepository.saveAndFlush(new Book(tenth, "Manual M1", null));

        assertThat(bookRepository.existsBySchoolClassIdAndTitle(tenth.getId(), "Manual M1")).isTrue();
    }
}
