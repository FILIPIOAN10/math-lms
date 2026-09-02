package ro.mathlms.content;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps content-layer exceptions to HTTP responses. */
@RestControllerAdvice
public class ContentExceptionHandler {

    @ExceptionHandler(ContentNotFoundException.class)
    public ResponseEntity<String> handleNotFound(ContentNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(DuplicateContentException.class)
    public ResponseEntity<String> handleDuplicate(DuplicateContentException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    /** Two admins edited the same exercise; the second save lost the race. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<String> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("This item was changed by someone else. Reload and try again.");
    }

    /** e.g. deleting a class that still has books, or a leftover unique violation. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<String> handleIntegrity(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body("This item is still in use or conflicts with an existing one.");
    }
}
