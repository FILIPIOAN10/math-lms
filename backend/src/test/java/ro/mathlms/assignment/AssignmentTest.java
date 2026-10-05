package ro.mathlms.assignment;

import org.junit.jupiter.api.Test;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.quiz.Quiz;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssignmentTest {

    private final Quiz quiz = new Quiz("Simulare EN", null);
    private final SchoolClass schoolClass = new SchoolClass("Clasa a 9-a", null);
    private final Instant created = Instant.parse("2026-10-05T10:00:00Z");
    private final Instant due = created.plus(Duration.ofDays(3));

    @Test
    void holdsItsQuizClassAndDeadline() {
        Assignment assignment = new Assignment(quiz, schoolClass, due, created);

        assertThat(assignment.getQuiz()).isSameAs(quiz);
        assertThat(assignment.getSchoolClass()).isSameAs(schoolClass);
        assertThat(assignment.getDueAt()).isEqualTo(due);
        assertThat(assignment.getCreatedAt()).isEqualTo(created);
        assertThat(assignment.getReminderSentAt()).isNull();
    }

    @Test
    void everyFieldIsRequired() {
        assertThatThrownBy(() -> new Assignment(null, schoolClass, due, created)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Assignment(quiz, null, due, created)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Assignment(quiz, schoolClass, null, created)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Assignment(quiz, schoolClass, due, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void isOverdueOnlyAfterTheDeadline() {
        Assignment assignment = new Assignment(quiz, schoolClass, due, created);

        assertThat(assignment.isOverdue(due.minusSeconds(1))).isFalse();
        assertThat(assignment.isOverdue(due)).isFalse();            // exactly at the deadline is still on time
        assertThat(assignment.isOverdue(due.plusSeconds(1))).isTrue();
    }

    @Test
    void movingTheDeadlineAllowsANewReminder() {
        Assignment assignment = new Assignment(quiz, schoolClass, due, created);
        assignment.markReminderSent(created.plusSeconds(60));

        assignment.reschedule(due.plus(Duration.ofDays(2)));

        assertThat(assignment.getDueAt()).isEqualTo(due.plus(Duration.ofDays(2)));
        assertThat(assignment.getReminderSentAt()).isNull();
    }

    @Test
    void aReminderIsRecordedOnce() {
        Assignment assignment = new Assignment(quiz, schoolClass, due, created);

        assignment.markReminderSent(created.plusSeconds(5));

        assertThat(assignment.getReminderSentAt()).isEqualTo(created.plusSeconds(5));
    }
}
