package ro.mathlms.tutor;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The nightly „Dă-mi una asemănătoare" indexing: only with the tutor on, and a failure waits for the next night. */
class SimilarIndexJobTest {

    private final TutorClient client = mock(TutorClient.class);

    private SimilarIndexJob job(boolean tutorOn) {
        return new SimilarIndexJob(new TutorProperties(tutorOn, "http://localhost:8001", "t", Duration.ofSeconds(55),
                false, 20), client);
    }

    @Test
    void asksTheTutorToIndexWhenItIsOn() {
        when(client.indexSimilar()).thenReturn(new TutorClient.IndexReport(3, 40, 1));

        job(true).run();

        verify(client).indexSimilar();
    }

    @Test
    void doesNothingWhileTheTutorIsOff() {
        job(false).run();

        verifyNoInteractions(client);
    }

    @Test
    void aFailureIsOnlyLogged() {
        when(client.indexSimilar()).thenThrow(new TutorException("Tutorul nu răspunde acum."));

        job(true).run(); // no exception: the scheduler keeps going, tomorrow catches up
    }
}
