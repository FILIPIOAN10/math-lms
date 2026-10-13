package ro.mathlms.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import ro.mathlms.enchapter.EnChapter;
import ro.mathlms.enchapter.EnChapterRepository;
import ro.mathlms.ingest.ChapterSuggester.Choice;
import ro.mathlms.ingest.ChapterSuggester.PartSuggestion;
import ro.mathlms.ingest.ChapterSuggester.Question;
import ro.mathlms.ingest.ChapterSuggester.Suggestion;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemPart;
import ro.mathlms.quiz.QuizItemPartRepository;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.quiz.QuizNotFoundException;
import ro.mathlms.quiz.QuizOption;
import ro.mathlms.quiz.QuizOptionRepository;
import ro.mathlms.quiz.QuizRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * EN chapter proposals (stage 3): after an import is read, for the teacher's review; and on demand for a test that
 * already exists. Proposals never change anything by themselves - the review screen pre-fills its choices with them,
 * and the builder applies only what the teacher keeps.
 */
@Service
public class ChapterSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(ChapterSuggestionService.class);

    /** More than an EN paper (18 questions) by far; bounds one call's cost. */
    static final int MAX_QUESTIONS = 60;

    /** The answers the builder shows: one per question, in order, with the chapter's id ready to apply. */
    public record QuizSuggestionsDto(List<ItemSuggestionDto> items) {
    }

    public record ItemSuggestionDto(Long itemId, Long chapterId, String code, String reason, List<PartSuggestionDto> parts) {
    }

    public record PartSuggestionDto(Long partId, Long chapterId, String code, String reason) {
    }

    private final List<ChapterSuggester> suggesters;
    private final AiImportProperties properties;
    private final EnChapterRepository chapterRepository;
    private final QuizRepository quizRepository;
    private final QuizItemRepository itemRepository;
    private final QuizItemPartRepository partRepository;
    private final QuizOptionRepository optionRepository;
    private final TransactionTemplate readOnly;

    public ChapterSuggestionService(List<ChapterSuggester> suggesters, AiImportProperties properties,
                                    EnChapterRepository chapterRepository, QuizRepository quizRepository,
                                    QuizItemRepository itemRepository, QuizItemPartRepository partRepository,
                                    QuizOptionRepository optionRepository, PlatformTransactionManager transactionManager) {
        this.optionRepository = optionRepository;
        this.suggesters = suggesters;
        this.properties = properties;
        this.chapterRepository = chapterRepository;
        this.quizRepository = quizRepository;
        this.itemRepository = itemRepository;
        this.partRepository = partRepository;
        this.readOnly = new TransactionTemplate(transactionManager);
        this.readOnly.setReadOnly(true);
    }

    /**
     * The imported questions with a chapter proposed for each (and each requirement). Best effort: without a suggester,
     * or when it fails, the questions come back as they were - the import itself never fails because of this.
     */
    public List<ExtractedItem> annotate(List<ExtractedItem> items) {
        Optional<ChapterSuggester> suggester = suggester();
        if (suggester.isEmpty() || items.isEmpty()) {
            return items;
        }
        try {
            List<Choice> choices = choices(chapterRepository.findAllByOrderByPositionAscIdAsc());
            List<Question> questions = items.stream().map(i -> new Question(i.section(), i.statement(), i.options(),
                    i.parts().stream().map(ExtractedItem.Part::statement).toList())).toList();
            List<Suggestion> suggestions = suggester.get().suggest(questions, choices);
            List<ExtractedItem> out = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                ExtractedItem item = items.get(i);
                Suggestion s = i < suggestions.size() ? suggestions.get(i) : Suggestion.none(item.parts().size());
                List<ExtractedItem.Part> parts = new ArrayList<>();
                for (int p = 0; p < item.parts().size(); p++) {
                    PartSuggestion ps = p < s.parts().size() ? s.parts().get(p) : new PartSuggestion(null, null);
                    parts.add(item.parts().get(p).withChapter(ps.code(), ps.reason()));
                }
                out.add(item.withChapter(s.code(), s.reason(), List.copyOf(parts)));
            }
            return out;
        } catch (Exception e) {
            log.warn("EN chapter proposals for an import failed - the questions go to review without them", e);
            return items;
        }
    }

    /** Proposals for every question of an existing test; nothing is changed. */
    public QuizSuggestionsDto suggestForQuiz(Long quizId) {
        if (!properties.enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        ChapterSuggester suggester = suggester()
                .orElseThrow(() -> new ChapterSuggestionException(AiImportService.NO_PROVIDER));
        Loaded loaded = readOnly.execute(status -> load(quizId));
        if (loaded.items().isEmpty()) {
            return new QuizSuggestionsDto(List.of());
        }
        List<Suggestion> suggestions;
        try {
            suggestions = suggester.suggest(loaded.questions(), choices(loaded.chapters()));
        } catch (ExtractionException e) {
            throw new ChapterSuggestionException(e.getMessage());
        }
        Map<String, Long> ids = loaded.chapters().stream().collect(Collectors.toMap(EnChapter::getCode, EnChapter::getId));
        List<ItemSuggestionDto> out = new ArrayList<>();
        for (int i = 0; i < loaded.items().size(); i++) {
            ItemRef item = loaded.items().get(i);
            Suggestion s = i < suggestions.size() ? suggestions.get(i) : Suggestion.none(item.partIds().size());
            List<PartSuggestionDto> parts = new ArrayList<>();
            for (int p = 0; p < item.partIds().size(); p++) {
                PartSuggestion ps = p < s.parts().size() ? s.parts().get(p) : new PartSuggestion(null, null);
                parts.add(new PartSuggestionDto(item.partIds().get(p), ps.code() == null ? null : ids.get(ps.code()),
                        ps.code(), ps.reason()));
            }
            out.add(new ItemSuggestionDto(item.id(), s.code() == null ? null : ids.get(s.code()), s.code(), s.reason(),
                    parts));
        }
        return new QuizSuggestionsDto(out);
    }

    private record ItemRef(Long id, List<Long> partIds) {
    }

    private record Loaded(List<ItemRef> items, List<Question> questions, List<EnChapter> chapters) {
    }

    /** Everything the call needs, read in one short transaction so none stays open while the AI thinks. */
    private Loaded load(Long quizId) {
        if (!quizRepository.existsById(quizId)) {
            throw new QuizNotFoundException("Quiz", quizId);
        }
        List<QuizItem> items = itemRepository.findByQuizIdOrderByPosition(quizId);
        if (items.size() > MAX_QUESTIONS) {
            items = items.subList(0, MAX_QUESTIONS);
        }
        List<ItemRef> refs = new ArrayList<>();
        List<Question> questions = new ArrayList<>();
        for (QuizItem item : items) {
            List<QuizItemPart> parts = partRepository.findByItemIdOrderByPosition(item.getId());
            List<String> options = optionRepository.findByItemIdOrderByPosition(item.getId()).stream()
                    .map(QuizOption::getText).toList();
            refs.add(new ItemRef(item.getId(), parts.stream().map(QuizItemPart::getId).toList()));
            questions.add(new Question(item.getSection(), item.getStatement(), options,
                    parts.stream().map(QuizItemPart::getStatement).toList()));
        }
        return new Loaded(refs, questions, chapterRepository.findAllByOrderByPositionAscIdAsc());
    }

    private static List<Choice> choices(List<EnChapter> chapters) {
        return chapters.stream()
                .map(c -> new Choice(c.getCode(), c.getTitle(), EnChapterClues.BY_CODE.get(c.getCode())))
                .toList();
    }

    private Optional<ChapterSuggester> suggester() {
        return suggesters.stream().filter(s -> s.provider().equals(properties.provider())).findFirst();
    }
}
