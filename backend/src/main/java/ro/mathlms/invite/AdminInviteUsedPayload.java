package ro.mathlms.invite;

/**
 * Outbox payload of one "someone used an admin invite link" email. Ids only — the handler re-reads the current state,
 * and no personal data is stored in the outbox. One event per admin, so a retry never re-mails an admin who already
 * got the message.
 */
public record AdminInviteUsedPayload(Long newUserId, Long adminId) {
}
