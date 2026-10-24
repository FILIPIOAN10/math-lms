package ro.mathlms.idempotency;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** A key reused for another request is 422 (it cannot mean two things); one still in flight is 409 (retry soon). */
@RestControllerAdvice
public class IdempotencyExceptionHandler {

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    public ResponseEntity<String> reused(IdempotencyKeyReusedException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body("Cererea a fost deja trimisă cu alt conținut.");
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<String> conflict(IdempotencyConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body("Lucrarea se trimite chiar acum. Reîncearcă peste câteva secunde.");
    }
}
