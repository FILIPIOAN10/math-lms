package ro.mathlms.ingest;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.ingest.MistakeSuggestionService.MistakeSuggestionDto;
import ro.mathlms.quiz.QuizAdminService;
import ro.mathlms.teaching.TeacherScope;

/** A proposal for the teacher's box (a POST because it calls the AI and costs); saving it is a separate request. */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class MistakeSuggestionController {

    private final MistakeSuggestionService service;
    private final QuizAdminService quizzes;
    private final TeacherScope scope;

    public MistakeSuggestionController(MistakeSuggestionService service, QuizAdminService quizzes, TeacherScope scope) {
        this.service = service;
        this.quizzes = quizzes;
        this.scope = scope;
    }

    @PostMapping("/api/admin/quiz-options/{optionId}/mistake-suggestion")
    public MistakeSuggestionDto suggest(@PathVariable Long optionId) {
        scope.requireQuizItem(quizzes.itemIdOfOption(optionId));
        return service.suggest(optionId);
    }
}
