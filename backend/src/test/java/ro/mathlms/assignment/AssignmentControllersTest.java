package ro.mathlms.assignment;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.assignment.AssignmentDtos.AssignmentRequest;
import ro.mathlms.assignment.AssignmentDtos.AssignmentSummaryDto;
import ro.mathlms.assignment.AssignmentDtos.RescheduleRequest;
import ro.mathlms.assignment.AssignmentDtos.StudentAssignmentDto;
import ro.mathlms.assignment.AssignmentProgress.State;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.quiz.Quiz;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssignmentControllersTest {

    private final AssignmentService service = mock(AssignmentService.class);
    private final AssignmentAdminController admin = new AssignmentAdminController(service);
    private final AssignmentStudentController student = new AssignmentStudentController(service);
    private final Instant due = Instant.parse("2026-10-12T15:00:00Z");

    private AssignmentSummaryDto summary() {
        return new AssignmentSummaryDto(1L, 10L, "Simulare EN", 5L, "Clasa a 9-a", due, false, 4, 1, 0, 1, 2);
    }

    @Test
    void createReturns201WithTheSummary() {
        Assignment created = new Assignment(new Quiz("Simulare EN", null), new SchoolClass("Clasa a 9-a", null), due,
                Instant.parse("2026-10-05T10:00:00Z"));
        ReflectionTestUtils.setField(created, "id", 1L);
        when(service.create(10L, 5L, due)).thenReturn(created);
        when(service.summaryOf(1L)).thenReturn(summary());

        ResponseEntity<AssignmentSummaryDto> response = admin.create(new AssignmentRequest(10L, 5L, due));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(summary());
    }

    @Test
    void listStatusRescheduleAndDeleteDelegate() {
        when(service.list()).thenReturn(List.of(summary()));
        when(service.summaryOf(1L)).thenReturn(summary());

        assertThat(admin.list()).containsExactly(summary());
        assertThat(admin.reschedule(1L, new RescheduleRequest(due))).isEqualTo(summary());
        verify(service).reschedule(1L, due);

        assertThat(admin.delete(1L).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).delete(1L);

        admin.status(1L);
        verify(service).status(1L);
    }

    @Test
    void aStudentGetsTheirOwnAssignmentsByPrincipalEmail() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("ana@scoala.ro");
        List<StudentAssignmentDto> mine = List.of(new StudentAssignmentDto(
                1L, 10L, "Simulare EN", "Clasa a 9-a", due, State.NOT_STARTED, false, false, null, 30));
        when(service.myAssignments("ana@scoala.ro")).thenReturn(mine);

        assertThat(student.mine(auth)).isEqualTo(mine);
    }
}
