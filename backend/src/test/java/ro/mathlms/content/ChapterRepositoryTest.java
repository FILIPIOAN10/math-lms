package ro.mathlms.content;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ro.mathlms.TestImages;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ChapterRepositoryTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer(TestImages.POSTGRES);

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
    void findsChaptersOfABookInTheirPlaceAndTheLastPlaceTaken() {
        Book book = persistBook("M1");
        Chapter numere = new Chapter(book, "Numere reale", null);
        numere.placeAt(0);
        Chapter calcul = new Chapter(book, "Calcul algebric", null);
        calcul.placeAt(1);
        chapterRepository.save(calcul);
        chapterRepository.save(numere);

        List<Chapter> chapters = chapterRepository.findByBookIdOrderByPositionAscIdAsc(book.getId());

        assertThat(chapters).extracting(Chapter::getTitle).containsExactly("Numere reale", "Calcul algebric");
        assertThat(chapterRepository.findMaxPosition(book.getId())).isEqualTo(1);
        assertThat(chapterRepository.findMaxPosition(persistBook("Goală").getId())).as("an empty book").isEqualTo(-1);
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
