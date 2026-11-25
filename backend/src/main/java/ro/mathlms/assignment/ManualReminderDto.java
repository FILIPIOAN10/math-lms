package ro.mathlms.assignment;

import java.time.Instant;

/**
 * What "Trimite reminder" did: how many students who had not handed in were emailed, how many linked parents got
 * their copy, and when (the next one waits {@link AssignmentService#MANUAL_REMINDER_PAUSE}).
 */
public record ManualReminderDto(int students, int parents, Instant sentAt) {
}
