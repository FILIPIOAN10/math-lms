package ro.mathlms.quiz;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.quiz.QuizDtos.ItemDto;
import ro.mathlms.quiz.QuizDtos.QuizDetailDto;

import java.util.List;

/** Admin authoring of quizzes: quiz CRUD, publish, and item/option management. */
@Service
public class QuizAdminService {

    private final QuizRepository quizRepository;
    private final QuizItemRepository itemRepository;
    private final QuizOptionRepository optionRepository;

    public QuizAdminService(QuizRepository quizRepository, QuizItemRepository itemRepository,
                            QuizOptionRepository optionRepository) {
        this.quizRepository = quizRepository;
        this.itemRepository = itemRepository;
        this.optionRepository = optionRepository;
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
                .map(item -> ItemDto.from(item, optionRepository.findByItemIdOrderByPosition(item.getId())))
                .toList();
        return QuizDetailDto.of(quiz, items);
    }

    @Transactional
    public Quiz createQuiz(String title, String description) {
        return quizRepository.save(new Quiz(title, description));
    }

    @Transactional
    public Quiz updateQuiz(Long id, String title, String description) {
        Quiz quiz = getQuiz(id);
        quiz.update(title, description);
        return quizRepository.save(quiz);
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
        for (QuizItem item : itemRepository.findByQuizIdOrderByPosition(id)) {
            optionRepository.deleteByItemId(item.getId());
        }
        itemRepository.deleteAll(itemRepository.findByQuizIdOrderByPosition(id));
        quizRepository.delete(quiz);
    }

    // --- item-level ---

    public QuizItem getItem(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new QuizNotFoundException("QuizItem", itemId));
    }

    @Transactional
    public ItemDto addItem(Long quizId, ItemRequest request) {
        Quiz quiz = getQuiz(quizId);
        QuizItem item = itemRepository.save(new QuizItem(
                quiz, request.position(), request.type(), request.statement(),
                request.points(), request.solution()));
        List<QuizOption> options = saveOptionsIfSingleChoice(item, request);
        return ItemDto.from(item, options);
    }

    @Transactional
    public ItemDto updateItem(Long itemId, ItemRequest request) {
        QuizItem item = getItem(itemId);
        if (item.getType() != request.type()) {
            throw new InvalidQuizException("The item type cannot be changed");
        }
        item.update(request.position(), request.statement(), request.points(), request.solution());
        itemRepository.save(item);
        // Replace options wholesale for a single-choice item.
        optionRepository.deleteByItemId(itemId);
        List<QuizOption> options = saveOptionsIfSingleChoice(item, request);
        return ItemDto.from(item, options);
    }

    @Transactional
    public void deleteItem(Long itemId) {
        QuizItem item = getItem(itemId);
        optionRepository.deleteByItemId(itemId);
        itemRepository.delete(item);
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
