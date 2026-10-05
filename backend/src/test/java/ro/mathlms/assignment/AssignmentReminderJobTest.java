package ro.mathlms.assignment;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssignmentReminderJobTest {

    private final AssignmentService service = mock(AssignmentService.class);

    @Test
    void queuesTheRemindersOfEveryDueAssignment() {
        when(service.findDueForReminderIds()).thenReturn(List.of(1L, 2L));

        new AssignmentReminderJob(service, true).tick();

        verify(service).queueReminders(1L);
        verify(service).queueReminders(2L);
    }

    @Test
    void oneFailingAssignmentDoesNotStopTheOthers() {
        when(service.findDueForReminderIds()).thenReturn(List.of(1L, 2L));
        doThrow(new IllegalStateException("boom")).when(service).queueReminders(1L);

        new AssignmentReminderJob(service, true).tick();

        verify(service).queueReminders(2L);
    }

    @Test
    void aFailingLookupDoesNotKillTheTick() {
        when(service.findDueForReminderIds()).thenThrow(new IllegalStateException("db down"));

        new AssignmentReminderJob(service, true).tick(); // must not throw: it would end the scheduler thread's loop

        verify(service, never()).queueReminders(anyLong());
    }

    @Test
    void doesNothingUnlessRemindersAreSwitchedOn() {
        new AssignmentReminderJob(service, false).tick();

        verify(service, never()).findDueForReminderIds();
    }
}
