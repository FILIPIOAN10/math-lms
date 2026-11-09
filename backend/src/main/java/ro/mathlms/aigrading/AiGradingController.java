package ro.mathlms.aigrading;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.teaching.TeacherScope;

import java.util.List;

/**
 * The AI's grade proposals on the grading screen: read them (polled while some are still being made), and ask for one
 * (again). They are only proposals: grades are still saved through the grading endpoints.
 */
@RestController
@PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
public class AiGradingController {

    private final AiGradingService service;
    private final TeacherScope scope;

    public AiGradingController(AiGradingService service, TeacherScope scope) {
        this.service = service;
        this.scope = scope;
    }

    @GetMapping("/api/admin/quiz/attempts/{attemptId}/ai-proposals")
    public List<AiProposalDto> proposals(@PathVariable Long attemptId) {
        scope.requireAttempt(attemptId);
        return service.proposals(attemptId);
    }

    /** Whether hand-ins of this test ask the AI by themselves; {@code queued} = 0 when only read. */
    public record AutoProposalsDto(boolean auto, int queued) {
    }

    public record AutoProposalsRequest(boolean auto) {
    }

    @GetMapping("/api/admin/quizzes/{quizId}/ai-proposals")
    public AutoProposalsDto autoProposals(@PathVariable Long quizId) {
        scope.requireQuiz(quizId);
        return new AutoProposalsDto(service.autoProposals(quizId), 0);
    }

    /** Turning them on also asks for the papers already waiting; answers how many proposals that queued. */
    @PutMapping("/api/admin/quizzes/{quizId}/ai-proposals")
    public AutoProposalsDto setAutoProposals(@PathVariable Long quizId, @RequestBody AutoProposalsRequest request) {
        scope.requireQuiz(quizId);
        int queued = service.setAutoProposals(quizId, request.auto());
        return new AutoProposalsDto(request.auto(), queued);
    }

    @PostMapping("/api/admin/quiz/attempts/{attemptId}/responses/{itemId}/ai-proposal")
    public AiProposalDto request(@PathVariable Long attemptId, @PathVariable Long itemId) {
        scope.requireAttempt(attemptId);
        return service.request(attemptId, itemId);
    }
}
