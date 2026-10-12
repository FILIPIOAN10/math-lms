package ro.mathlms.ingest;

/** The provider could not read the pages. The message is shown to the teacher, so it is Romanian and plain. */
public class ExtractionException extends Exception {

    public ExtractionException(String message) {
        super(message);
    }

    public ExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
