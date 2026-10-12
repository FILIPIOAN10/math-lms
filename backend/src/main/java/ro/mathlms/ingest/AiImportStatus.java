package ro.mathlms.ingest;

/** Where an AI import is: waiting for the worker, being read, read (with questions), or failed (with a reason). */
public enum AiImportStatus {
    QUEUED,
    RUNNING,
    DONE,
    FAILED
}
