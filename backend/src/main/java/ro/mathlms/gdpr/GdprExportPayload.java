package ro.mathlms.gdpr;

/** Outbox payload of one export build. The id only: the worker re-reads the row (and skips it if no longer PENDING). */
public record GdprExportPayload(Long exportId) {
}
