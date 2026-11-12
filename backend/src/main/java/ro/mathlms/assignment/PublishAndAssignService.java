package ro.mathlms.assignment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.quiz.QuizAdminService;
import ro.mathlms.quiz.QuizStatus;

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
        return publishAndAssign(quizId, schoolClassId, dueAt, null);
    }

    /**
     * The same with a scheduled release ("prepare Friday's test on Tuesday"): a draft is published now but stays hidden
     * from every student - every class, not only this one - until {@code availableFrom} (V66). A test already published
     * is not taken away from the students who see it: then only the homework waits.
     */
    @Transactional
    public Assignment publishAndAssign(Long quizId, Long schoolClassId, Instant dueAt, Instant availableFrom) {
        boolean wasDraft = quizAdminService.getQuiz(quizId).getStatus() != QuizStatus.PUBLISHED;
        quizAdminService.setPublished(quizId, true); // refuses a quiz without questions
        return assignmentService.create(quizId, schoolClassId, dueAt, availableFrom, wasDraft);
    }
}
