package ro.mathlms.quiz;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    public record CorrectNumberRequest(@NotBlank @Size(max = NumericAnswer.MAX_LENGTH) String value) {
    }

    /** The same for a numeric question: every number typed is marked again by the new answer. */
    @Transactional
    public AnswerKeyResultDto correctNumber(Long itemId, String value) {
        QuizItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new QuizNotFoundException("QuizItem", itemId));
        if (item.getType() != QuizItemType.NUMERIC) {
            throw new InvalidQuizException("Doar o întrebare numerică are un număr de schimbat.");
        }
        if (NumericAnswer.parse(value).isEmpty()) {
            throw new InvalidQuizException("Scrie răspunsul corect ca număr: 12, 0,5, 1/2 sau 0,(3).");
        }
        if (NumericAnswer.same(item.getNumericAnswer(), value)) {
            return new AnswerKeyResultDto(0);
        }
        String before = item.getNumericAnswer();
        item.expectNumber(value);
        itemRepository.save(item);
        int changed = 0;
        for (ItemResponse response : responseRepository.findByItemIdFetchAttempt(itemId)) {
            if (response.getCorrect() != null && remark(response, NumericAnswer.same(value, response.getNumericAnswer()), item)) {
                changed++;
            }
        }
        journal(item, before + " → " + item.getNumericAnswer(), changed);
        return new AnswerKeyResultDto(changed);
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
            boolean correct = response.getSelectedOption() != null
                    && Objects.equals(response.getSelectedOption().getId(), right.getId());
            if (response.getCorrect() != null && remark(response, correct, item)) {
                changed++;
            }
        }
        journal(item, (wrong == null ? "nemarcat" : QuizAttemptService.letterOf(options, wrong) + ")")
                + " → " + QuizAttemptService.letterOf(options, right) + ")", changed);
        return new AnswerKeyResultDto(changed);
    }

    private void journal(QuizItem item, String change, int changed) {
        cacheEvictor.evict(CacheNames.QUIZ_STATS, item.getQuiz().getId());
        cacheEvictor.evictAll(CacheNames.PROGRESS);
        String number = QuestionNumbers.of(item, itemRepository.findByQuizIdOrderByPosition(item.getQuiz().getId()));
        auditLog.record(AuditEvent.of(AuditAction.ANSWER_KEY_CORRECTED, item.getQuiz().getId(),
                        AuditText.question(item.getQuiz().getTitle(), number))
                .details("răspuns corect " + change + ", " + RoCount.of(changed, "notă schimbată", "note schimbate")));
    }

    /** Marks one answer by the new key; true when the student's graded total moved. */
    private static boolean remark(ItemResponse response, boolean correct, QuizItem item) {
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
