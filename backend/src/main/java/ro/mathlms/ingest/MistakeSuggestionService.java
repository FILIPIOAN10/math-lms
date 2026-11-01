package ro.mathlms.ingest;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import ro.mathlms.ingest.MistakeSuggester.Question;
import ro.mathlms.quiz.InvalidQuizException;
import ro.mathlms.quiz.QuizNotFoundException;
import ro.mathlms.quiz.QuizOption;
import ro.mathlms.quiz.QuizOptionRepository;

import java.util.List;

/**
 * "Propune cu AI" on Statistici: what mistake a wrong option stands for. Nothing is saved here - the text lands in the
 * teacher's box, and only what they keep is stored. The question is read in a short transaction, so the call to the AI
 * holds no database connection.
 */
@Service
public class MistakeSuggestionService {

    public record MistakeSuggestionDto(String text) {
    }

    private final List<MistakeSuggester> suggesters;
    private final AiImportProperties properties;
    private final QuizOptionRepository optionRepository;
    private final TransactionTemplate readOnly;

    public MistakeSuggestionService(List<MistakeSuggester> suggesters, AiImportProperties properties,
                                    QuizOptionRepository optionRepository, PlatformTransactionManager transactionManager) {
        this.suggesters = suggesters;
        this.properties = properties;
        this.optionRepository = optionRepository;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    public MistakeSuggestionDto suggest(Long optionId) {
        if (!properties.enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        MistakeSuggester suggester = suggesters.stream().filter(s -> s.provider().equals(properties.provider())).findFirst()
                .orElseThrow(() -> new ChapterSuggestionException(AiImportService.NO_PROVIDER));
        Question question = readOnly.execute(status -> question(optionId));
        try {
            return new MistakeSuggestionDto(suggester.suggest(question));
        } catch (ExtractionException e) {
            throw new ChapterSuggestionException(e.getMessage());
        }
    }

    private Question question(Long optionId) {
        QuizOption option = optionRepository.findById(optionId)
                .orElseThrow(() -> new QuizNotFoundException("QuizOption", optionId));
        if (option.isCorrect()) {
            throw new InvalidQuizException("Varianta corectă nu are o greșeală de explicat.");
        }
        String correct = optionRepository.findByItemIdOrderByPosition(option.getItem().getId()).stream()
                .filter(QuizOption::isCorrect).map(QuizOption::getText).findFirst().orElse(null);
        return new Question(option.getItem().getStatement(), correct, option.getText());
    }
}
