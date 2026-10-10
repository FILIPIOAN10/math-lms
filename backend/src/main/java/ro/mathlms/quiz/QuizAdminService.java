package ro.mathlms.quiz;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.cache.AfterCommitCacheEvictor;
import ro.mathlms.cache.CacheNames;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.RoCount;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.quiz.QuizDtos.ItemDto;
import ro.mathlms.quiz.QuizDtos.QuizDetailDto;

import java.util.List;

/** Admin authoring of quizzes: quiz CRUD, publish, and item/option management. */
@Service
public class QuizAdminService {

    private final QuizRepository quizRepository;
    private final QuizItemRepository itemRepository;
    private final QuizOptionRepository optionRepository;
    private final QuizItemHintRepository hintRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final AfterCommitCacheEvictor cacheEvictor;

    public QuizAdminService(QuizRepository quizRepository, QuizItemRepository itemRepository,
                            QuizOptionRepository optionRepository, QuizItemHintRepository hintRepository,
                            SchoolClassRepository schoolClassRepository, AfterCommitCacheEvictor cacheEvictor) {
        this.quizRepository = quizRepository;
        this.itemRepository = itemRepository;
        this.optionRepository = optionRepository;
        this.hintRepository = hintRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.cacheEvictor = cacheEvictor;
    }

    /** Items define a quiz's max score, so any change to them invalidates every stats/progress entry. */
    private void dropScoreCaches() {
        cacheEvictor.evictAll(CacheNames.QUIZ_STATS);
        cacheEvictor.evictAll(CacheNames.PROGRESS);
    }

    // --- quiz-level ---

    public List<Quiz> listQuizzes() {
        return quizRepository.findAll();
    }

    public Quiz getQuiz(Long id) {
        return quizRepository.findById(id)
                .orElseThrow(() -> new QuizNotFoundException("Quiz", id));
    }

    public QuizDetailDto getQuizDetail(Long id) {
        Quiz quiz = getQuiz(id);
        List<ItemDto> items = itemRepository.findByQuizIdOrderByPosition(id).stream()
                .map(item -> ItemDto.from(item, optionRepository.findByItemIdOrderByPosition(item.getId()),
                        hintRepository.findByItemIdOrderByPosition(item.getId())))
                .toList();
        return QuizDetailDto.of(quiz, items);
    }

    /** {@code schoolClassId} {@code null} = a quiz for every student. */
    @Transactional
    public Quiz createQuiz(String title, String description, Long schoolClassId, Integer timeLimitMinutes,
                           boolean practiceAllowed) {
        Quiz quiz = new Quiz(title, description);
        quiz.assignToClass(findClass(schoolClassId));
        quiz.changeTimeLimit(timeLimitMinutes);
        quiz.allowPractice(practiceAllowed);
        return quizRepository.save(quiz);
    }

    @Transactional
    public Quiz updateQuiz(Long id, String title, String description, Long schoolClassId, Integer timeLimitMinutes,
                           boolean practiceAllowed) {
        Quiz quiz = getQuiz(id);
        quiz.update(title, description);
        quiz.assignToClass(findClass(schoolClassId));
        quiz.changeTimeLimit(timeLimitMinutes); // attempts already running keep their own deadline
        quiz.allowPractice(practiceAllowed);    // practice already in progress simply finishes; no new one can start
        return quizRepository.save(quiz);
    }

    private SchoolClass findClass(Long schoolClassId) {
        if (schoolClassId == null) {
            return null;
        }
        return schoolClassRepository.findById(schoolClassId)
                .orElseThrow(() -> new QuizNotFoundException("SchoolClass", schoolClassId));
    }

    @Transactional
    public Quiz setPublished(Long id, boolean published) {
        Quiz quiz = getQuiz(id);
        if (published) {
            quiz.publish();
        } else {
            quiz.unpublish();
        }
        return quizRepository.save(quiz);
    }

    @Transactional
    public void deleteQuiz(Long id) {
        Quiz quiz = getQuiz(id);
        long attempts = quizRepository.countAttempts(id);
        if (attempts > 0) {
            throw new QuizInUseException("Quiz-ul „" + quiz.getTitle() + "” nu poate fi șters: elevii au deja "
                    + RoCount.of(attempts, "încercare", "încercări") + " (cu răspunsuri și note). "
                    + "Apasă „Depublică” ca să nu mai apară la elevi.");
        }
        for (QuizItem item : itemRepository.findByQuizIdOrderByPosition(id)) {
            optionRepository.deleteByItemId(item.getId());
            hintRepository.deleteByItemId(item.getId());
        }
        itemRepository.deleteAll(itemRepository.findByQuizIdOrderByPosition(id));
        quizRepository.delete(quiz);
        dropScoreCaches();
    }

    // --- item-level ---

    public QuizItem getItem(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new QuizNotFoundException("QuizItem", itemId));
    }

    @Transactional
    public ItemDto addItem(Long quizId, ItemRequest request) {
        Quiz quiz = getQuiz(quizId);
        requireValidHints(request.hints()); // before anything is written
        QuizItem item = itemRepository.save(new QuizItem(
                quiz, request.position(), request.type(), request.statement(),
                request.points(), request.solution()));
        List<QuizOption> options = saveOptionsIfSingleChoice(item, request);
        List<QuizItemHint> hints = saveHints(item, request.hints());
        dropScoreCaches();
        return ItemDto.from(item, options, hints);
    }

    @Transactional
    public ItemDto updateItem(Long itemId, ItemRequest request) {
        QuizItem item = getItem(itemId);
        if (item.getType() != request.type()) {
            throw new InvalidQuizException("The item type cannot be changed");
        }
        requireValidHints(request.hints());
        item.update(request.position(), request.statement(), request.points(), request.solution());
        itemRepository.save(item);
        // Replace options wholesale for a single-choice item.
        optionRepository.deleteByItemId(itemId);
        List<QuizOption> options = saveOptionsIfSingleChoice(item, request);
        hintRepository.deleteByItemId(itemId); // hints are replaced wholesale too
        List<QuizItemHint> hints = saveHints(item, request.hints());
        dropScoreCaches();
        return ItemDto.from(item, options, hints);
    }

    @Transactional
    public void deleteItem(Long itemId) {
        QuizItem item = getItem(itemId);
        optionRepository.deleteByItemId(itemId);
        hintRepository.deleteByItemId(itemId);
        itemRepository.delete(item);
        dropScoreCaches();
    }

    private static void requireValidHints(List<String> hints) {
        if (hints == null) {
            return;
        }
        if (hints.size() > QuizItem.MAX_HINTS) {
            throw new InvalidQuizException("An item can have at most " + QuizItem.MAX_HINTS + " hints");
        }
        if (hints.stream().anyMatch(h -> h == null || h.isBlank())) {
            throw new InvalidQuizException("A hint must not be blank");
        }
    }

    /** Persists an item's (already validated) hints in the order given, numbered from 1. Null/empty = no hints. */
    private List<QuizItemHint> saveHints(QuizItem item, List<String> hints) {
        if (hints == null || hints.isEmpty()) {
            return List.of();
        }
        List<QuizItemHint> saved = new java.util.ArrayList<>();
        for (int i = 0; i < hints.size(); i++) {
            saved.add(hintRepository.save(new QuizItemHint(item, i + 1, hints.get(i))));
        }
        return saved;
    }

    /**
     * For a single-choice item, validates and persists its options (at least two, exactly one
     * correct). For an open item, options are ignored and none are stored.
     */
    private List<QuizOption> saveOptionsIfSingleChoice(QuizItem item, ItemRequest request) {
        if (item.getType() != QuizItemType.SINGLE_CHOICE) {
            return List.of();
        }
        List<OptionRequest> options = request.options();
        if (options == null || options.size() < 2) {
            throw new InvalidQuizException("A single-choice item needs at least two options");
        }
        long correct = options.stream().filter(OptionRequest::correct).count();
        if (correct != 1) {
            throw new InvalidQuizException("A single-choice item must have exactly one correct option");
        }
        return options.stream()
                .map(o -> optionRepository.save(new QuizOption(item, o.position(), o.text(), o.correct())))
                .toList();
    }
}
