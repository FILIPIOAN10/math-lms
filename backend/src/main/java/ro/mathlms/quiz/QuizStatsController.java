package ro.mathlms.quiz;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.quiz.QuizStatsDtos.QuizStatsDto;
import ro.mathlms.teaching.TeacherScope;

/** The teacher's statistics for one quiz (ADMIN — URL rule + method rule). */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class QuizStatsController {

    private final QuizStatsService service;
    private final TeacherScope scope;

    public QuizStatsController(QuizStatsService service, TeacherScope scope) {
        this.service = service;
        this.scope = scope;
    }

    @GetMapping("/api/admin/quizzes/{quizId}/stats")
    public QuizStatsDto stats(@PathVariable Long quizId) {
        scope.requireQuiz(quizId);
        return service.getStats(quizId);
    }
}
