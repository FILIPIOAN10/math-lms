package ro.mathlms.outbox;

/** Outbox event-type strings. Constants so the publisher and its handler cannot drift apart on a typo. */
public final class OutboxEventTypes {

    /** "The result of your / your child's test is ready" email — one event per recipient. */
    public static final String RESULT_READY_EMAIL = "RESULT_READY_EMAIL";
    public static final String ASSIGNMENT_REMINDER_EMAIL = "ASSIGNMENT_REMINDER_EMAIL";

    private OutboxEventTypes() {
    }
}
