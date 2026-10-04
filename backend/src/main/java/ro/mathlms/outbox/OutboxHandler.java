package ro.mathlms.outbox;

/**
 * Performs one type of outbox side effect.
 *
 * <p>Implementations must be <strong>idempotent</strong>: delivery is at-least-once, so the same payload can
 * arrive twice (the effect succeeded but the DONE write did not). Throw to signal failure — the dispatcher
 * backs off and retries, then dead-letters. Never swallow an error and return normally.
 */
public interface OutboxHandler {

    /** The {@link OutboxEventTypes} value this handler consumes. */
    String eventType();

    /** @param payload the JSON stored on the outbox row */
    void handle(String payload);
}
