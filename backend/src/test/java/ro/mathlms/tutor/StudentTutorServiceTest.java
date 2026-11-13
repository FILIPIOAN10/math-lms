package ro.mathlms.tutor;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import ro.mathlms.aiusage.AiUsageRecorder;
import ro.mathlms.enchapter.EnChapterRepository;
import ro.mathlms.quiz.QuizAttemptRepository;
import ro.mathlms.user.UserRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The student's box is off unless switched on by itself (TUTOR_STUDENT_ENABLED): a pupil's typed text goes to the model
 * provider, which is a separate decision from the teacher's hint proposals. Off, nothing is asked and nothing is read.
 */
class StudentTutorServiceTest {

    private final TutorClient client = mock(TutorClient.class);
    private final UserRepository users = mock(UserRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

    private StudentTutorService service(boolean tutorOn, boolean studentsOn) {
        return new StudentTutorService(new TutorProperties(tutorOn, "http://localhost:8001", "t", Duration.ofSeconds(55),
                studentsOn, 20), client, users, mock(QuizAttemptRepository.class), mock(EnChapterRepository.class), jdbc,
                AiUsageRecorder.NONE, Clock.systemUTC());
    }

    @Test
    void offByDefaultEvenWhenTheTeachersTutorIsOn() {
        StudentTutorService service = service(true, false);

        assertThat(service.status("ana@scoala.ro").enabled()).isFalse();
        assertThatThrownBy(() -> service.explain("ana@scoala.ro", "ce e modulul?", List.of()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(client, users, jdbc);
    }

    @Test
    void theStudentsBoxNeedsTheTutorItself() {
        StudentTutorService service = service(false, true);

        assertThat(service.status("ana@scoala.ro").enabled()).isFalse();
        assertThatThrownBy(() -> service.explain("ana@scoala.ro", "ce e modulul?", List.of()))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(client, users, jdbc);
    }
}
