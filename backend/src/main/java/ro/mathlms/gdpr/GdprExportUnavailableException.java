package ro.mathlms.gdpr;

/** The emailed link was used, expired or forged, or the archive is gone: 410 with a sentence the person can act on. */
public class GdprExportUnavailableException extends RuntimeException {
    public GdprExportUnavailableException(String message) {
        super(message);
    }
}
