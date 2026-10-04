package ro.mathlms.content;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookControllerTest {

    private final BookService service = mock(BookService.class);
    private final ContentAccess access = mock(ContentAccess.class);
    private final BookController controller = new BookController(service, access);
    private final Authentication auth = new UsernamePasswordAuthenticationToken("ana@scoala.ro", null);

    private final SchoolClass ninth = new SchoolClass("Clasa a 9-a", null);

    @Test
    void listByClassMapsToDtos() {
        when(service.listByClass(1L)).thenReturn(List.of(new Book(ninth, "M1", "Algebră")));

        List<BookDto> result = controller.listByClass(1L, auth);

        assertThat(result).singleElement().satisfies(dto ->
                assertThat(dto.title()).isEqualTo("M1"));
    }

    @Test
    void readsAskTheAccessGuardFirst() {
        when(service.listByClass(1L)).thenReturn(List.of());
        when(service.get(2L)).thenReturn(new Book(ninth, "M1", null));

        controller.listByClass(1L, auth);
        controller.get(2L, auth);

        verify(access).checkClass(1L, auth);
        verify(access).checkBook(2L, auth);
    }

    @Test
    void aDeniedReadNeverReachesTheService() {
        doThrow(new ContentNotFoundException("SchoolClass", 1L)).when(access).checkClass(1L, auth);

        assertThatThrownBy(() -> controller.listByClass(1L, auth)).isInstanceOf(ContentNotFoundException.class);
        verifyNoInteractions(service);
    }

    @Test
    void createReturns201() {
        when(service.create(1L, "M1", "d")).thenReturn(new Book(ninth, "M1", "d"));

        ResponseEntity<BookDto> response =
                controller.create(1L, new BookRequest("M1", "d"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().title()).isEqualTo("M1");
    }

    @Test
    void deleteReturns204AndDelegates() {
        ResponseEntity<Void> response = controller.delete(7L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).delete(7L);
    }
}
