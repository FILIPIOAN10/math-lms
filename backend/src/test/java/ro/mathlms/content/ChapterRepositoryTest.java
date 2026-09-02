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
class ChapterRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ChapterRepository chapterRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private SchoolClassRepository schoolClassRepository;

    private Book persistBook(String title) {
        SchoolClass klass = schoolClassRepository.save(new SchoolClass("Clasa " + title, null));
        return bookRepository.save(new Book(klass, title, null));
    }

    @Test
    void findsChaptersOfABookOrderedByTitle() {
        Book book = persistBook("M1");
        chapterRepository.save(new Chapter(book, "Beta", null));
        chapterRepository.save(new Chapter(book, "Alfa", null));

        List<Chapter> chapters = chapterRepository.findByBookIdOrderByTitle(book.getId());

        assertThat(chapters).extracting(Chapter::getTitle).containsExactly("Alfa", "Beta");
    }

    @Test
    void enforcesUniqueTitleWithinABook() {
        Book book = persistBook("M1");
        chapterRepository.saveAndFlush(new Chapter(book, "Ecuații", "prima"));

        assertThatThrownBy(() ->
                chapterRepository.saveAndFlush(new Chapter(book, "Ecuații", "a doua")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void allowsSameTitleInDifferentBooks() {
        Book m1 = persistBook("M1");
        Book m2 = persistBook("M2");
        chapterRepository.saveAndFlush(new Chapter(m1, "Ecuații", null));

        chapterRepository.saveAndFlush(new Chapter(m2, "Ecuații", null));

        assertThat(chapterRepository.existsByBookIdAndTitle(m2.getId(), "Ecuații")).isTrue();
    }
}
