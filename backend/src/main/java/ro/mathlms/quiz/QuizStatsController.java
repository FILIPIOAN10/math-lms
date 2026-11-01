package ro.mathlms.quiz;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.quiz.AnswerKeyCorrection.AnswerKeyResultDto;
import ro.mathlms.quiz.AnswerKeyCorrection.CorrectOptionRequest;
import ro.mathlms.quiz.QuizStatsDtos.QuizStatsDto;
import ro.mathlms.teaching.TeacherScope;

/** The teacher's statistics for one quiz (ADMIN — URL rule + method rule). */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class QuizStatsController {

    private final QuizStatsService service;
    private final AnswerKeyCorrection correction;
    private final TeacherScope scope;

    public QuizStatsController(QuizStatsService service, AnswerKeyCorrection correction, TeacherScope scope) {
        this.service = service;
        this.correction = correction;
        this.scope = scope;
    }

    @GetMapping("/api/admin/quizzes/{quizId}/stats")
    public QuizStatsDto stats(@PathVariable Long quizId) {
        scope.requireQuiz(quizId);
        return service.getStats(quizId);
    }

    /** The marked answer of a grilă was wrong: changes it and marks every paper again. */
    @PutMapping("/api/admin/quiz-items/{itemId}/correct-option")
    public AnswerKeyResultDto correctAnswer(@PathVariable Long itemId, @Valid @RequestBody CorrectOptionRequest request) {
        scope.requireQuizItem(itemId);
        return correction.correct(itemId, request.optionId());
    }
}
