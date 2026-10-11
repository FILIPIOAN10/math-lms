package ro.mathlms.assignment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.quiz.QuizAdminService;

import java.time.Instant;

/**
 * T3: "Publică și dă ca temă" in one step. Publishing and assigning run in ONE transaction, so if the homework is
 * refused (a deadline in the past, another class's quiz, already given) the quiz is not left published either - the
 * teacher fixes the dialog and tries again, with nothing half done.
 */
@Service
public class PublishAndAssignService {

    private final QuizAdminService quizAdminService;
    private final AssignmentService assignmentService;

    public PublishAndAssignService(QuizAdminService quizAdminService, AssignmentService assignmentService) {
        this.quizAdminService = quizAdminService;
        this.assignmentService = assignmentService;
    }

    @Transactional
    public Assignment publishAndAssign(Long quizId, Long schoolClassId, Instant dueAt) {
        quizAdminService.setPublished(quizId, true); // refuses a quiz without questions
        return assignmentService.create(quizId, schoolClassId, dueAt);
    }
}
