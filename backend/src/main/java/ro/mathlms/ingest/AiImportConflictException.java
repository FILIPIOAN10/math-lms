package ro.mathlms.ingest;

/** The import is not in a state for that (still being read, quiz already made): 409 with the reason, in Romanian. */
public class AiImportConflictException extends RuntimeException {

    public AiImportConflictException(String message) {
        super(message);
    }
}
