package ro.mathlms.quiz;

/**
 * Outbox payload of one "result is ready" email. Ids only — the handler re-reads the current state, and no
 * personal data is stored in the outbox. One event per recipient, so a retry never re-mails a recipient who
 * already got the message.
 */
public record ResultReadyPayload(Long attemptId, Audience audience) {

    public enum Audience {
        STUDENT,
        PARENT
    }
}
