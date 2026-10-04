package ro.mathlms.outbox;

/** Lifecycle of an {@link OutboxEvent}: waiting (or backing off), done, or dead-lettered after too many failures. */
public enum OutboxStatus {
    PENDING,
    DONE,
    DEAD
}
