package ro.mathlms.ingest;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.ingest.ChapterSuggestionService.QuizSuggestionsDto;
import ro.mathlms.teaching.TeacherScope;

/**
 * "Sugerează capitolele (AI)" in the quiz builder: proposals only (a POST because it calls the AI and costs). The
 * teacher applies what they keep through {@code PUT /api/admin/quizzes/{id}/en-chapters}.
 */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class ChapterSuggestionController {

    private final ChapterSuggestionService service;
    private final TeacherScope scope;

    public ChapterSuggestionController(ChapterSuggestionService service, TeacherScope scope) {
        this.service = service;
        this.scope = scope;
    }

    @PostMapping("/api/admin/quizzes/{quizId}/en-chapter-suggestions")
    public QuizSuggestionsDto suggest(@PathVariable Long quizId) {
        scope.requireQuiz(quizId);
        return service.suggestForQuiz(quizId);
    }
}
