package ro.mathlms.content;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchoolClassControllerTest {

    private final SchoolClassService service = mock(SchoolClassService.class);
    private final ContentAccess access = mock(ContentAccess.class);
    private final EnrollmentService enrollmentService = mock(EnrollmentService.class);
    private final SchoolClassController controller = new SchoolClassController(service, access, enrollmentService);
    private final Authentication auth = new UsernamePasswordAuthenticationToken("ana@scoala.ro", null);

    @Test
    void listMapsToDtos() {
        when(service.list()).thenReturn(List.of(new SchoolClass("Clasa a 9-a", "Algebră")));

        List<SchoolClassDto> result = controller.list(auth);

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.name()).isEqualTo("Clasa a 9-a");
            assertThat(dto.description()).isEqualTo("Algebră");
        });
    }

    @Test
    void aStudentListsOnlyTheirOwnClasses() {
        when(access.isStudent(auth)).thenReturn(true);
        when(enrollmentService.myClasses("ana@scoala.ro")).thenReturn(List.of(new SchoolClass("Clasa mea", null)));

        List<SchoolClassDto> result = controller.list(auth);

        assertThat(result).extracting(SchoolClassDto::name).containsExactly("Clasa mea");
        verify(service, never()).list();
    }

    @Test
    void getMapsToDtoAfterTheAccessCheck() {
        when(service.get(1L)).thenReturn(new SchoolClass("Clasa a 9-a", null));

        assertThat(controller.get(1L, auth).name()).isEqualTo("Clasa a 9-a");
        verify(access).checkClass(1L, auth);
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
