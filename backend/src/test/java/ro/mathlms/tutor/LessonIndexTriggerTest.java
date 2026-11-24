package ro.mathlms.tutor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Plan step 6: an approval or a show / hide refreshes the tutor's lesson index, after the commit, never blocking. */
class LessonIndexTriggerTest {

    private final TutorClient client = mock(TutorClient.class);

    private LessonIndexTrigger trigger(boolean tutorOn) {
        return new LessonIndexTrigger(new TutorProperties(tutorOn, "http://localhost:8001", "t", Duration.ofSeconds(55),
                false, 20), client, Runnable::run);
    }

    @AfterEach
    void clear() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void theIndexIsRefreshedOnlyOnceTheChangeIsCommitted() {
        when(client.indexLessons()).thenReturn(new TutorClient.IndexReport(3, 0, 0));
        TransactionSynchronizationManager.initSynchronization();

        trigger(true).lessonsChanged();

        verify(client, never()).indexLessons();  // a rolled back approval must not reach the tutor
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(client).indexLessons();
    }

    @Test
    void nothingWhileTheTutorIsOffAndAFailureIsOnlyLogged() {
        trigger(false).lessonsChanged();
        verify(client, never()).indexLessons();

        when(client.indexLessons()).thenThrow(new TutorException("Tutorul nu răspunde."));
        assertThatCode(() -> trigger(true).lessonsChanged()).doesNotThrowAnyException();
        verify(client).indexLessons();
    }
}
