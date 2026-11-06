package ro.mathlms.content;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.audit.AuditLog;

import java.util.List;
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
    private final AuditLog auditLog = mock(AuditLog.class);
    private final ChapterService service = new ChapterService(chapterRepository, bookRepository, auditLog);

    private final Book book = new Book(new SchoolClass("Clasa a 9-a", null), "M1", null);

    @Test
    void chaptersAreListedInTheOrderOfTheBookNotOfTheAlphabet() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(chapterRepository.findByBookIdOrderByPositionAscIdAsc(1L)).thenReturn(List.of(
                new Chapter(book, "Numere reale", null), new Chapter(book, "Calcul algebric", null)));

        assertThat(service.listByBook(1L)).extracting(Chapter::getTitle)
                .containsExactly("Numere reale", "Calcul algebric");
    }

    @Test
    void aNewChapterGoesAfterTheLastOneOfItsBook() {
        when(bookRepository.findById(1L)).thenReturn(Optional.of(book));
        when(chapterRepository.existsByBookIdAndTitle(1L, "Funcții")).thenReturn(false);
        when(chapterRepository.findMaxPosition(1L)).thenReturn(3);
        when(chapterRepository.save(any(Chapter.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(service.create(1L, "Funcții", null).getPosition()).isEqualTo(4);
    }

    @Test
    void movingAChapterUpSwapsItWithTheOneBeforeItAndNumbersTheBookAgain() {
        ReflectionTestUtils.setField(book, "id", 7L);
        Chapter first = new Chapter(book, "Numere reale", null);
        Chapter second = new Chapter(book, "Calcul algebric", null);
        Chapter third = new Chapter(book, "Ecuații", null);
        when(chapterRepository.findById(3L)).thenReturn(Optional.of(third));
        when(chapterRepository.findByBookIdOrderByPositionAscIdAsc(7L)).thenReturn(List.of(first, second, third));

        service.move(3L, true);

        assertThat(List.of(first, third, second)).extracting(Chapter::getPosition).containsExactly(0, 1, 2);
        verify(auditLog).record(any());
    }

    @Test
    void theFirstChapterCannotMoveUpNorTheLastDown() {
        ReflectionTestUtils.setField(book, "id", 7L);
        Chapter first = new Chapter(book, "Numere reale", null);
        Chapter last = new Chapter(book, "Funcții", null);
        when(chapterRepository.findById(1L)).thenReturn(Optional.of(first));
        when(chapterRepository.findById(2L)).thenReturn(Optional.of(last));
        when(chapterRepository.findByBookIdOrderByPositionAscIdAsc(7L)).thenReturn(List.of(first, last));

        assertThatThrownBy(() -> service.move(1L, true)).isInstanceOf(ContentInUseException.class)
                .hasMessageContaining("deja primul");
        assertThatThrownBy(() -> service.move(2L, false)).isInstanceOf(ContentInUseException.class)
                .hasMessageContaining("deja ultimul");
    }

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

    @Test
    void deleteRefusesWhileTheChapterHasExercises() {
        Book algebra = new Book(new SchoolClass("Clasa a 9-a", null), "Algebra", null);
        when(chapterRepository.findById(9L)).thenReturn(Optional.of(new Chapter(algebra, "Ecuații", null)));
        when(chapterRepository.countExercises(9L)).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(9L))
                .isInstanceOf(ContentInUseException.class)
                .hasMessageContaining("Ecuații")
                .hasMessageContaining("1 exercițiu");
        verify(chapterRepository, never()).deleteById(any());
    }

    @Test
    void deleteRemovesAnEmptyChapter() {
        Book algebra = new Book(new SchoolClass("Clasa a 9-a", null), "Algebra", null);
        when(chapterRepository.findById(9L)).thenReturn(Optional.of(new Chapter(algebra, "Ecuații", null)));

        service.delete(9L);

        verify(chapterRepository).deleteById(9L);
    }
}
