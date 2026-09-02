package ro.mathlms.quiz;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps quiz-layer exceptions to HTTP responses. */
@RestControllerAdvice
public class QuizExceptionHandler {

    @ExceptionHandler(QuizNotFoundException.class)
    public ResponseEntity<String> handleNotFound(QuizNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(InvalidQuizException.class)
    public ResponseEntity<String> handleInvalid(InvalidQuizException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}
