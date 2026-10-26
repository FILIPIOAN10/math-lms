package ro.mathlms.aigrading;

/** QUEUED (waiting for the worker or the daily limit) -> RUNNING -> DONE | FAILED; a teacher may ask again. */
public enum AiProposalStatus {
    QUEUED, RUNNING, DONE, FAILED
}
