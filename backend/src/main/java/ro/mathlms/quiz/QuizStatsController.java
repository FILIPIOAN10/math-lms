package ro.mathlms.quiz;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.quiz.QuizStatsDtos.QuizStatsDto;

/** The teacher's statistics for one quiz (ADMIN — URL rule + method rule). */
@RestController
@PreAuthorize("hasRole('ADMIN')")
public class QuizStatsController {

    private final QuizStatsService service;

    public QuizStatsController(QuizStatsService service) {
        this.service = service;
    }

    @GetMapping("/api/admin/quizzes/{quizId}/stats")
    public QuizStatsDto stats(@PathVariable Long quizId) {
        return service.getStats(quizId);
    }
}
