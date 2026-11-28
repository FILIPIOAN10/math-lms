package ro.mathlms.studyplan;

import org.junit.jupiter.api.Test;
import ro.mathlms.user.AccountStatus;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The daily estimate for the plan's chart: off unless switched on, and one student's failure never stops the rest. */
class EnPredictionSnapshotJobTest {

    private final UserRepository users = mock(UserRepository.class);
    private final StudyPlanService plans = mock(StudyPlanService.class);

    @Test
    void offByDefaultItReadsNothing() {
        new EnPredictionSnapshotJob(users, plans, false).run();

        verifyNoInteractions(users, plans);
    }

    @Test
    void itKeepsTodaysEstimateForEveryActiveStudentEvenWhenOneFails() {
        User ana = new User("ana@scoala.ro", "Ana", Role.STUDENT);
        User bogdan = new User("bogdan@scoala.ro", "Bogdan", Role.STUDENT);
        User carmen = new User("carmen@scoala.ro", "Carmen", Role.STUDENT);
        when(users.findByRoleAndStatusAndErasedFalse(Role.STUDENT, AccountStatus.ACTIVE)).thenReturn(List.of(ana, bogdan, carmen));
        when(plans.snapshot(ana)).thenReturn(true);
        doThrow(new IllegalStateException("boom")).when(plans).snapshot(bogdan);
        when(plans.snapshot(carmen)).thenReturn(false); // too few answers for an estimate yet

        int kept = new EnPredictionSnapshotJob(users, plans, true).run();

        verify(plans).snapshot(ana);
        verify(plans).snapshot(bogdan);
        verify(plans).snapshot(carmen);
        assertThat(kept).isEqualTo(1); // only Ana had an estimate to keep
    }

    @Test
    void aFailedListOfStudentsNeverKillsTheSchedulerThread() {
        when(users.findByRoleAndStatusAndErasedFalse(Role.STUDENT, AccountStatus.ACTIVE)).thenThrow(new IllegalStateException("db down"));

        new EnPredictionSnapshotJob(users, plans, true).run(); // no exception

        verifyNoInteractions(plans);
    }
}
