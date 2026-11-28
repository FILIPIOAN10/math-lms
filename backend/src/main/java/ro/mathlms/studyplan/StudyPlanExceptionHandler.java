package ro.mathlms.studyplan;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** A target off the 1 - 10 scale, an exam date already gone: a 400 with a Romanian sentence the page shows as is. */
@RestControllerAdvice(assignableTypes = StudyPlanController.class)
public class StudyPlanExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> invalid(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}
