package ro.mathlms.content;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChapterControllerTest {

    private final ChapterService service = mock(ChapterService.class);
    private final ContentAccess access = mock(ContentAccess.class);
    private final ChapterController controller = new ChapterController(service, access);
    private final Authentication auth = new UsernamePasswordAuthenticationToken("ana@scoala.ro", null);

    private final Book book = new Book(new SchoolClass("Clasa a 9-a", null), "M1", null);

    @Test
    void listByBookMapsToDtos() {
        when(service.listByBook(1L)).thenReturn(List.of(new Chapter(book, "Ecuații", null)));

        List<ChapterDto> result = controller.listByBook(1L, auth);

        assertThat(result).singleElement().satisfies(dto ->
                assertThat(dto.title()).isEqualTo("Ecuații"));
    }

    @Test
    void readsAskTheAccessGuardFirst() {
        when(service.listByBook(1L)).thenReturn(List.of());
        when(service.get(2L)).thenReturn(new Chapter(book, "Ecuații", null));

        controller.listByBook(1L, auth);
        controller.get(2L, auth);

        verify(access).checkBook(1L, auth);
        verify(access).checkChapter(2L, auth);
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
