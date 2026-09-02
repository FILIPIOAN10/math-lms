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

class BookServiceTest {

    private final BookRepository bookRepository = mock(BookRepository.class);
    private final SchoolClassRepository schoolClassRepository = mock(SchoolClassRepository.class);
    private final BookService service = new BookService(bookRepository, schoolClassRepository);

    private final SchoolClass ninth = new SchoolClass("Clasa a 9-a", null);

    @Test
    void listByClassThrowsWhenClassMissing() {
        when(schoolClassRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listByClass(404L))
                .isInstanceOf(ContentNotFoundException.class);
    }

    @Test
    void createSavesUnderExistingClass() {
        when(schoolClassRepository.findById(1L)).thenReturn(Optional.of(ninth));
        when(bookRepository.existsBySchoolClassIdAndTitle(1L, "M1")).thenReturn(false);
        when(bookRepository.save(any(Book.class))).thenAnswer(i -> i.getArgument(0));

        Book created = service.create(1L, "M1", "d");

        assertThat(created.getTitle()).isEqualTo("M1");
        assertThat(created.getSchoolClass()).isSameAs(ninth);
    }

    @Test
    void createThrowsWhenClassMissing() {
        when(schoolClassRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(404L, "M1", null))
                .isInstanceOf(ContentNotFoundException.class);
        verify(bookRepository, never()).save(any());
    }

    @Test
    void createRejectsDuplicateTitleInClass() {
        when(schoolClassRepository.findById(1L)).thenReturn(Optional.of(ninth));
        when(bookRepository.existsBySchoolClassIdAndTitle(1L, "M1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(1L, "M1", null))
                .isInstanceOf(DuplicateContentException.class);
        verify(bookRepository, never()).save(any());
    }

    @Test
    void updateRejectsRenameToExistingTitle() {
        Book book = new Book(ninth, "M1", null);
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(bookRepository.existsBySchoolClassIdAndTitle(any(), eq("M2"))).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, "M2", null))
                .isInstanceOf(DuplicateContentException.class);
        verify(bookRepository, never()).save(any());
    }

    @Test
    void updateSavesWhenTitleFree() {
        Book book = new Book(ninth, "M1", "vechi");
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(bookRepository.existsBySchoolClassIdAndTitle(any(), eq("M2"))).thenReturn(false);
        when(bookRepository.save(any(Book.class))).thenAnswer(i -> i.getArgument(0));

        Book updated = service.update(1L, "M2", "nou");

        assertThat(updated.getTitle()).isEqualTo("M2");
        assertThat(updated.getDescription()).isEqualTo("nou");
    }

    @Test
    void getThrowsWhenMissing() {
        when(bookRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(404L))
                .isInstanceOf(ContentNotFoundException.class);
    }

    @Test
    void deleteThrowsWhenMissing() {
        when(bookRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(404L))
                .isInstanceOf(ContentNotFoundException.class);
        verify(bookRepository, never()).deleteById(any());
    }
}
