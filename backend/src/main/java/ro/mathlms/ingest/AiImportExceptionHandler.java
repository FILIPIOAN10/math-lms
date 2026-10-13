package ro.mathlms.ingest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps AI-import exceptions to HTTP responses (not-found and bad-input reuse the quiz ones). */
@RestControllerAdvice
public class AiImportExceptionHandler {

    @ExceptionHandler(AiImportConflictException.class)
    public ResponseEntity<String> handleConflict(AiImportConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(ChapterSuggestionException.class)
    public ResponseEntity<String> handleSuggestion(ChapterSuggestionException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ex.getMessage());
    }
}
