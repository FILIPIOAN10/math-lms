package ro.mathlms.invite;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps invite-link exceptions to HTTP responses. */
@RestControllerAdvice
public class InviteExceptionHandler {

    @ExceptionHandler(InviteNotFoundException.class)
    public ResponseEntity<String> handleNotFound(InviteNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(InviteRejectedException.class)
    public ResponseEntity<String> handleRejected(InviteRejectedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    /** 410 Gone: the link existed (or looks like one) but nobody new may join through it. */
    @ExceptionHandler(InviteUnavailableException.class)
    public ResponseEntity<String> handleUnavailable(InviteUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.GONE).body(ex.getMessage());
    }
}
