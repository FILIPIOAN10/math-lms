package ro.mathlms.recap;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


@RestControllerAdvice
public class RecapExceptionHandler {

    @ExceptionHandler(RecapNotFoundException.class)
    public ResponseEntity<String> notFound(RecapNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(RecapConflictException.class)
    public ResponseEntity<String> conflict(RecapConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    @ExceptionHandler(InvalidRecapException.class)
    public ResponseEntity<String> invalid(InvalidRecapException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}
