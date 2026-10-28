package ro.mathlms.tutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import ro.mathlms.quiz.QuestionNumbers;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemPart;
import ro.mathlms.quiz.QuizItemPartRepository;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.quiz.QuizNotFoundException;
import ro.mathlms.quiz.QuizOption;
import ro.mathlms.quiz.QuizOptionRepository;

import java.util.List;
import java.util.regex.Pattern;

/**
 * "Sugerează indicii (AI)" in the question editor: the tutor writes a hint, a bigger hint and the full solution for one
 * question, only from the EN chapter pages, with the textbook pages each one used - or says "Nu am asta în manual.".
 * Proposals only: nothing is saved here. The teacher copies what they keep into the question's hints and saves it the
 * usual way, so students see only text a teacher approved, and asking costs one call per question, never per student.
 */
@Service
public class TutorHintService {

    private static final Logger log = LoggerFactory.getLogger(TutorHintService.class);

    static final int LEVELS = 3;
    /** The tutor service refuses longer statements. */
    static final int MAX_STATEMENT = 4000;
    /** An exam position the tutor's slot map knows: "I.1" .. "III.6" (the service accepts the same shape). */
    private static final Pattern SLOT = Pattern.compile("^[IVX]{1,4}\\.[0-9]{1,2}$");

    public record TutorSettingsDto(boolean enabled) {
    }

    public record TutorHintsDto(boolean answered, List<LevelDto> levels, String refusal, String refusalReason,
                                int inputTokens, int outputTokens, double costUsd) {
    }

    public record LevelDto(int level, String text, List<SourceDto> sources) {
    }

    public record SourceDto(String code, String title, int bookPage) {
    }

    /** What the tutor is asked about: the question as text, and its exam position when that names the chapter. */
    record Exercise(String statement, String slot) {
    }

    private final TutorProperties properties;
    private final TutorClient client;
    private final QuizItemRepository itemRepository;
    private final QuizOptionRepository optionRepository;
    private final QuizItemPartRepository partRepository;
    private final TransactionTemplate readOnly;

    public TutorHintService(TutorProperties properties, TutorClient client, QuizItemRepository itemRepository,
                            QuizOptionRepository optionRepository, QuizItemPartRepository partRepository,
                            PlatformTransactionManager transactionManager) {
        this.properties = properties;
        this.client = client;
        this.itemRepository = itemRepository;
        this.optionRepository = optionRepository;
        this.partRepository = partRepository;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    public TutorSettingsDto settings() {
        return new TutorSettingsDto(properties.enabled());
    }

    /** The three levels for one question; nothing is changed. 404 while the tutor is switched off. */
    public TutorHintsDto suggest(Long itemId) {
        if (!properties.enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        // Read in one short transaction, so none stays open while the model writes.
        Exercise exercise = readOnly.execute(status -> exercise(itemId));
        TutorClient.Answer answer = client.help(exercise.statement(), exercise.slot(), LEVELS);
        log.info("Tutor hints for item {}: answered {}, strategy {}, {} input + {} output tokens (~{} USD)", itemId,
                answer.answered(), answer.strategy(), answer.inputTokens(), answer.outputTokens(), answer.costUsd());
        List<LevelDto> levels = answer.steps().stream()
                .map(s -> new LevelDto(s.level(), s.text(), (s.sources() == null ? List.<TutorClient.Source>of() : s.sources())
                        .stream().map(src -> new SourceDto(src.code(), src.title(), src.bookPage())).toList()))
                .toList();
        TutorClient.Refusal refusal = answer.refusal();
        return new TutorHintsDto(!levels.isEmpty(), levels, refusal == null ? null : refusal.message(),
                refusal == null ? null : refusal.reason(), answer.inputTokens(), answer.outputTokens(), answer.costUsd());
    }

    Exercise exercise(Long itemId) {
        QuizItem item = itemRepository.findById(itemId).orElseThrow(() -> new QuizNotFoundException("QuizItem", itemId));
        StringBuilder text = new StringBuilder(item.getStatement().strip());
        if (item.getType() == QuizItemType.SINGLE_CHOICE) {
            List<QuizOption> options = optionRepository.findByItemIdOrderByPosition(itemId);
            if (!options.isEmpty()) {
                text.append("\nVariante:");
                for (int i = 0; i < options.size(); i++) {
                    text.append(' ').append((char) ('a' + i)).append(") ").append(options.get(i).getText().strip())
                            .append(i + 1 < options.size() ? ";" : "");
                }
            }
        }
        List<QuizItemPart> parts = partRepository.findByItemIdOrderByPosition(itemId);
        for (int i = 0; i < parts.size(); i++) {
            text.append('\n').append((char) ('a' + i)).append(") ").append(parts.get(i).getStatement().strip());
        }
        String statement = text.length() > MAX_STATEMENT ? text.substring(0, MAX_STATEMENT) : text.toString();
        // The exam position names the chapter page only when the teacher has not chosen one for this question.
        String slot = null;
        if (item.getEnChapter() == null) {
            String number = QuestionNumbers.of(itemRepository.findByQuizIdOrderByPosition(item.getQuiz().getId()))
                    .get(itemId);
            slot = number != null && SLOT.matcher(number).matches() ? number : null;
        }
        return new Exercise(statement, slot);
    }
}
