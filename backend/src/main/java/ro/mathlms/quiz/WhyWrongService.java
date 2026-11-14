package ro.mathlms.quiz;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.cache.AfterCommitCacheEvictor;
import ro.mathlms.cache.CacheNames;
import ro.mathlms.enchapter.LessonLinks;

import java.time.Clock;
import java.util.List;

/**
 * „De ce am greșit?" from the student's result: they ask why the option they chose is wrong, and the request waits for
 * the teacher, who sees on Statistici how many asked next to that option. Nothing is generated for the student: the
 * teacher writes (or drafts from the lessons, then edits) the explanation, and only what they save is read - by every
 * student who chose that option, the same text, through the option's {@code mistake} (V47).
 *
 * <p>Allowed only where the result already shows the answer key - a practice, or a test the student cannot hand in
 * again ({@link AnswerKeys}) - for a grilă answered wrong whose option has no explanation yet.
 */
@Service
public class WhyWrongService {

    /** The request cannot be made (right answer, key still hidden, already explained): the message is for the student. */
    public static class RefusedException extends RuntimeException {
        public RefusedException(String message) {
            super(message);
        }
    }

    private final QuizAttemptRepository attemptRepository;
    private final ItemResponseRepository responseRepository;
    private final AnswerKeys answerKeys;
    private final ExplanationRequests requests;
    private final AfterCommitCacheEvictor cacheEvictor;
    private final Clock clock;
    private final QuizAdminService adminService;
    private final QuizOptionRepository optionRepository;
    private final LessonLinks lessonLinks;

    public WhyWrongService(QuizAttemptRepository attemptRepository, ItemResponseRepository responseRepository,
                           QuizRepository quizRepository, ExplanationRequests requests, AfterCommitCacheEvictor cacheEvictor,
                           Clock clock, QuizAdminService adminService, QuizOptionRepository optionRepository,
                           LessonLinks lessonLinks) {
        this.adminService = adminService;
        this.optionRepository = optionRepository;
        this.lessonLinks = lessonLinks;
        this.attemptRepository = attemptRepository;
        this.responseRepository = responseRepository;
        this.answerKeys = new AnswerKeys(quizRepository);
        this.requests = requests;
        this.cacheEvictor = cacheEvictor;
        this.clock = clock;
    }

    @Transactional
    public void ask(Long attemptId, Long itemId, String studentEmail) {
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .filter(a -> a.getStudent().getEmail().equals(studentEmail))
                .orElseThrow(() -> new QuizNotFoundException("QuizAttempt", attemptId));
        if (attempt.getStatus() == QuizAttemptStatus.IN_PROGRESS) {
            throw new RefusedException("Predă întâi testul.");
        }
        if (attempt.getMode() == AttemptMode.TEST && answerKeys.hidden(attempt.getQuiz(),
                attemptRepository.findByStudentIdAndQuizIdIn(attempt.getStudent().getId(), List.of(attempt.getQuiz().getId())))) {
            throw new RefusedException("Explicațiile apar după ultima încercare la acest test.");
        }
        ItemResponse response = responseRepository.findByAttemptIdAndItemId(attemptId, itemId)
                .orElseThrow(() -> new RefusedException("Nu ai ales nicio variantă la această întrebare."));
        QuizOption chosen = response.getSelectedOption();
        if (chosen == null) {
            throw new RefusedException("Nu ai ales nicio variantă la această întrebare.");
        }
        if (chosen.isCorrect()) {
            throw new RefusedException("Ai răspuns corect la această întrebare.");
        }
        if (chosen.getMistake() != null) {
            throw new RefusedException("Profesorul a scris deja explicația: o vezi sub întrebare.");
        }
        if (requests.add(chosen.getId(), attempt.getStudent().getId(), clock.instant())) {
            cacheEvictor.evict(CacheNames.QUIZ_STATS, attempt.getQuiz().getId()); // the count next to the option
        }
    }

    /**
     * The teacher saves the explanation of a wrong option, with the lesson it comes from (optional). A non-blank one
     * answers every student waiting about that option, so their requests go.
     */
    @Transactional
    public void explain(Long optionId, String mistake, Long lessonId, Integer lessonPage) {
        if (lessonId != null && !lessonLinks.exists(lessonId)) {
            throw new InvalidQuizException("Lecția aleasă nu mai există.");
        }
        adminService.explainMistake(optionId, mistake); // the rules, the audit and the statistics cache, as before
        QuizOption option = optionRepository.findById(optionId)
                .orElseThrow(() -> new QuizNotFoundException("QuizOption", optionId));
        if (option.getMistake() != null) {
            option.pointMistakeTo(lessonId, lessonPage);
            optionRepository.save(option);
            requests.deleteForOption(optionId);
        }
    }
}
