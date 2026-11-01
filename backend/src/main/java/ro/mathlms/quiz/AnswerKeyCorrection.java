package ro.mathlms.quiz;

import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEvent;
import ro.mathlms.audit.AuditLog;
import ro.mathlms.audit.AuditText;
import ro.mathlms.cache.AfterCommitCacheEvictor;
import ro.mathlms.cache.CacheNames;
import ro.mathlms.content.RoCount;

import java.util.List;
import java.util.Objects;

/**
 * Corrects a grilă whose marked answer was wrong, also after students handed it in: everywhere else a taken question
 * keeps its answer, so this is the one way, and it marks every paper again in the same transaction. Totals of graded
 * papers follow, and a grade that moved is new for its student. A paper still open is marked by the new key at hand-in.
 */
@Service
public class AnswerKeyCorrection {

    public record CorrectOptionRequest(@NotNull Long optionId) {
    }

    public record AnswerKeyResultDto(int changedGrades) {
    }

    private final QuizItemRepository itemRepository;
    private final QuizOptionRepository optionRepository;
    private final ItemResponseRepository responseRepository;
    private final AfterCommitCacheEvictor cacheEvictor;
    private final AuditLog auditLog;

    public AnswerKeyCorrection(QuizItemRepository itemRepository, QuizOptionRepository optionRepository,
                               ItemResponseRepository responseRepository, AfterCommitCacheEvictor cacheEvictor,
                               AuditLog auditLog) {
        this.itemRepository = itemRepository;
        this.optionRepository = optionRepository;
        this.responseRepository = responseRepository;
        this.cacheEvictor = cacheEvictor;
        this.auditLog = auditLog;
    }

    @Transactional
    public AnswerKeyResultDto correct(Long itemId, Long optionId) {
        QuizItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new QuizNotFoundException("QuizItem", itemId));
        if (item.getType() != QuizItemType.SINGLE_CHOICE) {
            throw new InvalidQuizException("Doar o grilă are o variantă corectă de schimbat.");
        }
        List<QuizOption> options = optionRepository.findByItemIdOrderByPosition(itemId);
        QuizOption right = options.stream().filter(o -> o.getId().equals(optionId)).findFirst()
                .orElseThrow(() -> new InvalidQuizException("Varianta aleasă nu e a acestei întrebări."));
        if (right.isCorrect()) {
            return new AnswerKeyResultDto(0);
        }
        QuizOption wrong = options.stream().filter(QuizOption::isCorrect).findFirst().orElse(null);
        options.forEach(o -> o.markCorrect(o.getId().equals(optionId)));
        optionRepository.saveAll(options);

        int changed = 0;
        for (ItemResponse response : responseRepository.findByItemIdFetchAttempt(itemId)) {
            if (response.getCorrect() != null && remark(response, right, item)) {
                changed++;
            }
        }
        cacheEvictor.evict(CacheNames.QUIZ_STATS, item.getQuiz().getId());
        cacheEvictor.evictAll(CacheNames.PROGRESS);
        String number = QuestionNumbers.of(item, itemRepository.findByQuizIdOrderByPosition(item.getQuiz().getId()));
        auditLog.record(AuditEvent.of(AuditAction.ANSWER_KEY_CORRECTED, item.getQuiz().getId(),
                        AuditText.question(item.getQuiz().getTitle(), number))
                .details("răspuns corect " + (wrong == null ? "nemarcat" : QuizAttemptService.letterOf(options, wrong) + ")")
                        + " → " + QuizAttemptService.letterOf(options, right) + "), "
                        + RoCount.of(changed, "notă schimbată", "note schimbate")));
        return new AnswerKeyResultDto(changed);
    }

    /** Marks one answer by the new key; true when the student's graded total moved. */
    private static boolean remark(ItemResponse response, QuizOption right, QuizItem item) {
        boolean correct = response.getSelectedOption() != null
                && Objects.equals(response.getSelectedOption().getId(), right.getId());
        int before = response.getAwardedPoints() == null ? 0 : response.getAwardedPoints();
        int after = correct ? item.getPoints() : 0;
        response.gradeAuto(correct, after);
        QuizAttempt attempt = response.getAttempt();
        if (before == after || attempt.getMode() != AttemptMode.TEST || attempt.getStatus() != QuizAttemptStatus.GRADED) {
            return false;
        }
        attempt.correctScore(attempt.getScore() + after - before);
        return true;
    }
}
