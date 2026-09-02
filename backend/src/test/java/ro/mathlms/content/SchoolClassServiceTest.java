package ro.mathlms.content;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchoolClassServiceTest {

    private final SchoolClassRepository repository = mock(SchoolClassRepository.class);
    private final SchoolClassService service = new SchoolClassService(repository);

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
        when(repository.existsById(1L)).thenReturn(true);

        service.delete(1L);

        verify(repository).deleteById(1L);
    }
}
