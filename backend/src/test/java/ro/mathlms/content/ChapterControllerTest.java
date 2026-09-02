package ro.mathlms.content;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChapterControllerTest {

    private final ChapterService service = mock(ChapterService.class);
    private final ChapterController controller = new ChapterController(service);

    private final Book book = new Book(new SchoolClass("Clasa a 9-a", null), "M1", null);

    @Test
    void listByBookMapsToDtos() {
        when(service.listByBook(1L)).thenReturn(List.of(new Chapter(book, "Ecuații", null)));

        List<ChapterDto> result = controller.listByBook(1L);

        assertThat(result).singleElement().satisfies(dto ->
                assertThat(dto.title()).isEqualTo("Ecuații"));
    }

    @Test
    void createReturns201() {
        when(service.create(1L, "Ecuații", "d")).thenReturn(new Chapter(book, "Ecuații", "d"));

        ResponseEntity<ChapterDto> response =
                controller.create(1L, new ChapterRequest("Ecuații", "d"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().title()).isEqualTo("Ecuații");
    }

    @Test
    void deleteReturns204AndDelegates() {
        ResponseEntity<Void> response = controller.delete(9L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).delete(9L);
    }
}
