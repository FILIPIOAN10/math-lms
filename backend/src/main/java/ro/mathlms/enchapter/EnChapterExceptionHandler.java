package ro.mathlms.enchapter;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns the chapter pages' errors into status codes with a Romanian message the screens show as is. */
@RestControllerAdvice(assignableTypes = EnChapterController.class)
public class EnChapterExceptionHandler {

    @ExceptionHandler(EnChapterNotFoundException.class)
    public ResponseEntity<String> notFound(EnChapterNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(EnChapterConflictException.class)
    public ResponseEntity<String> conflict(EnChapterConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    /** A rule of the page itself (a code with spaces, a missing solution, not an image). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> invalid(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}
