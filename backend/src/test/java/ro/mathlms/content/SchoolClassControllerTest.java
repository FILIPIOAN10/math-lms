package ro.mathlms.content;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchoolClassControllerTest {

    private final SchoolClassService service = mock(SchoolClassService.class);
    private final SchoolClassController controller = new SchoolClassController(service);

    @Test
    void listMapsToDtos() {
        when(service.list()).thenReturn(List.of(new SchoolClass("Clasa a 9-a", "Algebră")));

        List<SchoolClassDto> result = controller.list();

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.name()).isEqualTo("Clasa a 9-a");
            assertThat(dto.description()).isEqualTo("Algebră");
        });
    }

    @Test
    void getMapsToDto() {
        when(service.get(1L)).thenReturn(new SchoolClass("Clasa a 9-a", null));

        assertThat(controller.get(1L).name()).isEqualTo("Clasa a 9-a");
    }

    @Test
    void createReturns201() {
        when(service.create("Clasa a 9-a", "d")).thenReturn(new SchoolClass("Clasa a 9-a", "d"));

        ResponseEntity<SchoolClassDto> response =
                controller.create(new SchoolClassRequest("Clasa a 9-a", "d"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().name()).isEqualTo("Clasa a 9-a");
    }

    @Test
    void deleteReturns204AndDelegates() {
        ResponseEntity<Void> response = controller.delete(5L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).delete(5L);
    }
}
