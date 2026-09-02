package ro.mathlms.content;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EnrollmentControllerTest {

    private final EnrollmentService service = mock(EnrollmentService.class);
    private final EnrollmentController controller = new EnrollmentController(service);

    private final SchoolClass ninth = new SchoolClass("Clasa a 9-a", null);

    @Test
    void rosterMapsToDtos() {
        User student = new User("elev@scoala.ro", "Ana Pop", Role.STUDENT);
        when(service.roster(1L)).thenReturn(List.of(new Enrollment(student, ninth)));

        List<EnrollmentDto> result = controller.roster(1L);

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.studentName()).isEqualTo("Ana Pop");
            assertThat(dto.studentEmail()).isEqualTo("elev@scoala.ro");
        });
    }

    @Test
    void enrollReturns201() {
        User student = new User("elev@scoala.ro", "Ana Pop", Role.STUDENT);
        when(service.enroll(1L, 2L)).thenReturn(new Enrollment(student, ninth));

        ResponseEntity<EnrollmentDto> response = controller.enroll(1L, new EnrollRequest(2L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().studentName()).isEqualTo("Ana Pop");
    }

    @Test
    void unenrollReturns204AndDelegates() {
        ResponseEntity<Void> response = controller.unenroll(5L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).unenroll(5L);
    }
}
