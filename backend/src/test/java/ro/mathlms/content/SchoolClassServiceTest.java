package ro.mathlms.content;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchoolClassServiceTest {

    private final SchoolClassRepository repository = mock(SchoolClassRepository.class);
    private final SchoolClassService service = new SchoolClassService(repository);

    @Test
    void classesAreListedInNaturalOrderSoTenthGradeComesAfterNinth() {
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(
                new SchoolClass("Clasa a 10-a", null), new SchoolClass("Clasa a 9-a", null)));

        assertThat(service.list()).extracting(SchoolClass::getName).containsExactly("Clasa a 9-a", "Clasa a 10-a");
    }

    @Test
    void getReturnsTheClass() {
        SchoolClass ninth = new SchoolClass("Clasa a 9-a", null);
        when(repository.findById(1L)).thenReturn(Optional.of(ninth));

        assertThat(service.get(1L)).isSameAs(ninth);
    }

    @Test
    void getThrowsWhenMissing() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(404L))
                .isInstanceOf(ContentNotFoundException.class);
    }

    @Test
    void createSavesWhenNameIsFree() {
        when(repository.existsByName("Clasa a 9-a")).thenReturn(false);
        when(repository.save(any(SchoolClass.class))).thenAnswer(i -> i.getArgument(0));

        SchoolClass created = service.create("Clasa a 9-a", "Algebră");

        assertThat(created.getName()).isEqualTo("Clasa a 9-a");
        verify(repository).save(any(SchoolClass.class));
    }

    @Test
    void createRejectsDuplicateName() {
        when(repository.existsByName("Clasa a 9-a")).thenReturn(true);

        assertThatThrownBy(() -> service.create("Clasa a 9-a", null))
                .isInstanceOf(DuplicateContentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void updateChangesFields() {
        SchoolClass ninth = new SchoolClass("Clasa a 9-a", "vechi");
        when(repository.findById(1L)).thenReturn(Optional.of(ninth));
        when(repository.save(any(SchoolClass.class))).thenAnswer(i -> i.getArgument(0));

        SchoolClass updated = service.update(1L, "Clasa a 9-a", "nou");

        assertThat(updated.getDescription()).isEqualTo("nou");
    }

    @Test
    void updateRejectsRenameToAnExistingName() {
        SchoolClass ninth = new SchoolClass("Clasa a 9-a", null);
        when(repository.findById(1L)).thenReturn(Optional.of(ninth));
        when(repository.existsByName("Clasa a 10-a")).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, "Clasa a 10-a", null))
                .isInstanceOf(DuplicateContentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void deleteThrowsWhenMissing() {
        when(repository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(404L))
                .isInstanceOf(ContentNotFoundException.class);
        verify(repository, never()).deleteById(any());
    }

    @Test
    void deleteRemovesWhenPresent() {
        when(repository.findById(1L)).thenReturn(Optional.of(new SchoolClass("Clasa a 9-a", null)));

        service.delete(1L);

        verify(repository).deleteById(1L);
    }

    @Test
    void deleteUnenrollsTheStudentsBeforeRemovingTheClass() {
        when(repository.findById(1L)).thenReturn(Optional.of(new SchoolClass("Clasa a 9-a", null)));

        service.delete(1L);

        InOrder order = inOrder(repository);
        order.verify(repository).deleteEnrollments(1L);
        order.verify(repository).deleteById(1L);
    }

    @Test
    void deleteRefusesWhileBooksOrQuizzesBelongToTheClassAndSaysWhich() {
        when(repository.findById(1L)).thenReturn(Optional.of(new SchoolClass("Clasa a 9-a", null)));
        when(repository.countBooks(1L)).thenReturn(2L);
        when(repository.countQuizzes(1L)).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(ContentInUseException.class)
                .hasMessageContaining("Clasa a 9-a")
                .hasMessageContaining("2 cărți")
                .hasMessageContaining("1 quiz");
        verify(repository, never()).deleteEnrollments(any());
        verify(repository, never()).deleteById(any());
    }

    @Test
    void deleteRefusalOnlyGivesTheAdviceThatApplies() {
        when(repository.findById(1L)).thenReturn(Optional.of(new SchoolClass("Clasa a 9-a", null)));
        when(repository.countBooks(1L)).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(1L))
                .hasMessageContaining("are 1 carte.")
                .hasMessageContaining("Șterge întâi cărțile")
                .hasMessageNotContaining("Quiz");
    }
}
