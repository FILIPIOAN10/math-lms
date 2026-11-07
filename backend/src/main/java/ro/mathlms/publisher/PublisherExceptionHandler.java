package ro.mathlms.publisher;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** The "Edituri" errors as status codes with a Romanian message the page shows as is. */
@RestControllerAdvice(assignableTypes = PublisherController.class)
public class PublisherExceptionHandler {

    @ExceptionHandler(PublisherNotFoundException.class)
    public ResponseEntity<String> notFound(PublisherNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(PublisherConflictException.class)
    public ResponseEntity<String> conflict(PublisherConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    /** A rule of the publisher itself: an empty name, too long a text, a logo that is not an image. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> invalid(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}
