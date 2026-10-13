package ro.mathlms.ingest;

/** The AI could not propose chapters right now; the message is worded for the teacher (a 503). */
public class ChapterSuggestionException extends RuntimeException {
    public ChapterSuggestionException(String message) {
        super(message);
    }
}
