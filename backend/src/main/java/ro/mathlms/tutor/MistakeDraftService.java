package ro.mathlms.tutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import ro.mathlms.aiusage.AiUsageRecorder;
import ro.mathlms.enchapter.LessonLinks;
import ro.mathlms.enchapter.LessonLinks.LessonLinkDto;
import ro.mathlms.quiz.InvalidQuizException;
import ro.mathlms.quiz.QuizNotFoundException;
import ro.mathlms.quiz.QuizOption;
import ro.mathlms.quiz.QuizOptionRepository;

import java.util.List;

/**
 * „De ce am greșit?", the teacher's side: "Propune din lecții" next to a wrong option on Statistici asks the tutor why
 * that option is wrong, only from the EN lessons, with the lesson it cites. A proposal only: the text lands in the
 * teacher's box, and only what they save (with the lesson, if they keep it) reaches the students who chose the option.
 * One call per option, never per student. 404 while the tutor is switched off.
 */
@Service
public class MistakeDraftService {

    private static final Logger log = LoggerFactory.getLogger(MistakeDraftService.class);

    /** {@code lesson} is the first cited page that still exists; {@code refusal} "Nu am asta în manual." when none. */
    public record MistakeDraftDto(boolean answered, String text, LessonLinkDto lesson, String refusal) {
    }

    /** What the tutor is asked: the question as text with its options, its exam position, the two options. */
    record Asked(String statement, String slot, String chosen, String correct) {
    }

    private final TutorProperties properties;
    private final TutorClient client;
    private final TutorHintService hints;
    private final QuizOptionRepository optionRepository;
    private final LessonLinks lessonLinks;
    private final AiUsageRecorder usage;
    private final TransactionTemplate readOnly;

    public MistakeDraftService(TutorProperties properties, TutorClient client, TutorHintService hints,
                               QuizOptionRepository optionRepository, LessonLinks lessonLinks, AiUsageRecorder usage,
                               PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.client = client;
        this.hints = hints;
        this.optionRepository = optionRepository;
        this.lessonLinks = lessonLinks;
        this.usage = usage;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    public MistakeDraftDto draft(Long optionId) {
        if (!properties.enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        // Read in one short transaction, so none stays open while the model writes.
        Asked asked = readOnly.execute(status -> asked(optionId));
        TutorClient.MistakeDraft answer = client.mistake(asked.statement(), asked.slot(), asked.chosen(), asked.correct());
        if (answer.inputTokens() > 0 || answer.outputTokens() > 0) {
            usage.record("tutor-mistake", "tutor", answer.inputTokens(), answer.outputTokens());
        }
        LessonLinkDto lesson = !answer.answered() ? null : answer.sources().stream()
                .map(s -> lessonLinks.ofCode(s.code(), s.bookPage()).orElse(null))
                .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        // Rule 1 holds for drafts too: no page that still exists, no draft.
        boolean answered = answer.answered() && answer.text() != null && !answer.text().isBlank() && lesson != null;
        log.info("Mistake draft for option {}: answered {}, {} input + {} output tokens", optionId, answered,
                answer.inputTokens(), answer.outputTokens());
        String refusal = answered ? null : answer.refusal() != null ? answer.refusal().message() : "Nu am asta în manual.";
        return new MistakeDraftDto(answered, answered ? answer.text().strip() : null, answered ? lesson : null, refusal);
    }

    private Asked asked(Long optionId) {
        QuizOption option = optionRepository.findById(optionId)
                .orElseThrow(() -> new QuizNotFoundException("QuizOption", optionId));
        if (option.isCorrect()) {
            throw new InvalidQuizException("Varianta corectă nu are o greșeală de explicat.");
        }
        Long itemId = option.getItem().getId();
        TutorHintService.Exercise exercise = hints.exercise(itemId);
        List<QuizOption> options = optionRepository.findByItemIdOrderByPosition(itemId);
        String correct = options.stream().filter(QuizOption::isCorrect).findFirst().map(o -> lettered(options, o)).orElse(null);
        return new Asked(exercise.statement(), exercise.slot(), lettered(options, option), correct);
    }

    private static String lettered(List<QuizOption> options, QuizOption option) {
        return (char) ('a' + options.indexOf(option)) + ") " + option.getText().strip();
    }
}
