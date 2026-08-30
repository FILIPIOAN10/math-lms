package ro.mathlms.auth;

import io.jsonwebtoken.JwtException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps auth-flow exceptions to HTTP responses without leaking internals. */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<String> handleDuplicateEmail(EmailAlreadyRegisteredException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body("An account with this email already exists");
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<String> handleInvalidToken(JwtException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid or expired token");
    }

    /** Invalid state transition, e.g. verifying an already-verified account. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body("This action is not valid for the account's current state");
    }
}
