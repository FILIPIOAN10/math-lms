package ro.mathlms.content;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChapterServiceTest {

    private final ChapterRepository chapterRepository = mock(ChapterRepository.class);
    private final BookRepository bookRepository = mock(BookRepository.class);
    private final ChapterService service = new ChapterService(chapterRepository, bookRepository);

    private final Book book = new Book(new SchoolClass("Clasa a 9-a", null), "M1", null);

    @Test
    void listByBookThrowsWhenBookMissing() {
        when(bookRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listByBook(404L))
                .isInstanceOf(ContentNotFoundException.class);
    }

    @Test
    void createSavesUnderExistingBook() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(chapterRepository.existsByBookIdAndTitle(1L, "Ecuații")).thenReturn(false);
        when(chapterRepository.save(any(Chapter.class))).thenAnswer(i -> i.getArgument(0));

        Chapter created = service.create(1L, "Ecuații", "d");

        assertThat(created.getTitle()).isEqualTo("Ecuații");
        assertThat(created.getBook()).isSameAs(book);
    }

    @Test
    void createThrowsWhenBookMissing() {
        when(bookRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(404L, "Ecuații", null))
                .isInstanceOf(ContentNotFoundException.class);
        verify(chapterRepository, never()).save(any());
    }

    @Test
    void createRejectsDuplicateTitleInBook() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(chapterRepository.existsByBookIdAndTitle(1L, "Ecuații")).thenReturn(true);

        assertThatThrownBy(() -> service.create(1L, "Ecuații", null))
                .isInstanceOf(DuplicateContentException.class);
        verify(chapterRepository, never()).save(any());
    }

    @Test
    void updateRejectsRenameToExistingTitle() {
        Chapter chapter = new Chapter(book, "Ecuații", null);
        when(chapterRepository.findById(1L)).thenReturn(Optional.of(chapter));
        when(chapterRepository.existsByBookIdAndTitle(any(), eq("Inecuații"))).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, "Inecuații", null))
                .isInstanceOf(DuplicateContentException.class);
        verify(chapterRepository, never()).save(any());
    }

    @Test
    void updateSavesWhenTitleFree() {
        Chapter chapter = new Chapter(book, "Ecuații", "vechi");
        when(chapterRepository.findById(1L)).thenReturn(Optional.of(chapter));
        when(chapterRepository.existsByBookIdAndTitle(any(), eq("Inecuații"))).thenReturn(false);
        when(chapterRepository.save(any(Chapter.class))).thenAnswer(i -> i.getArgument(0));

        Chapter updated = service.update(1L, "Inecuații", "nou");

        assertThat(updated.getTitle()).isEqualTo("Inecuații");
        assertThat(updated.getDescription()).isEqualTo("nou");
    }

    @Test
    void deleteThrowsWhenMissing() {
        when(chapterRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(404L))
                .isInstanceOf(ContentNotFoundException.class);
        verify(chapterRepository, never()).deleteById(any());
    }
}
