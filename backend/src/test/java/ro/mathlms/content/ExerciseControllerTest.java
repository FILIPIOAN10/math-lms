package ro.mathlms.content;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExerciseControllerTest {

    private final ExerciseService service = mock(ExerciseService.class);
    private final ExerciseController controller = new ExerciseController(service);

    private final Chapter chapter =
            new Chapter(new Book(new SchoolClass("Clasa a 9-a", null), "M1", null), "Ecuații", null);

    @Test
    void listByChapterMapsToDtos() {
        when(service.listByChapter(1L))
                .thenReturn(List.of(new Exercise(chapter, "enunț", null, Difficulty.EASY)));

        List<ExerciseDto> result = controller.listByChapter(1L);

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.statement()).isEqualTo("enunț");
            assertThat(dto.difficulty()).isEqualTo(Difficulty.EASY);
        });
    }

    @Test
    void createReturns201() {
        when(service.create(1L, "enunț", "sol", Difficulty.MEDIUM))
                .thenReturn(new Exercise(chapter, "enunț", "sol", Difficulty.MEDIUM));

        ResponseEntity<ExerciseDto> response = controller.create(1L,
                new ExerciseCreateRequest("enunț", "sol", Difficulty.MEDIUM));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().statement()).isEqualTo("enunț");
    }

    @Test
    void updatePassesVersionToService() {
        when(service.update(1L, "nou", null, null, 2L))
                .thenReturn(new Exercise(chapter, "nou", null, null));

        ExerciseDto dto = controller.update(1L,
                new ExerciseUpdateRequest("nou", null, null, 2L));

        assertThat(dto.statement()).isEqualTo("nou");
        verify(service).update(1L, "nou", null, null, 2L);
    }

    @Test
    void deleteReturns204AndDelegates() {
        ResponseEntity<Void> response = controller.delete(11L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).delete(11L);
    }
}
