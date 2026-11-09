package ro.mathlms.common;

/**
 * A queue of AI jobs in the database (imports, barem reads, grade proposals), worked through in the background by a
 * {@link AiQueueWorker}.
 */
public interface AiQueue {

    /** Fails the jobs a restart interrupted mid-call, so the teacher can ask again; answers how many. */
    int recoverStuck();

    /** Runs the oldest waiting job, if any and if today's limit allows; {@code false} when there is nothing to do now. */
    boolean processNext();
}
