package ro.mathlms.quiz;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.cache.AfterCommitCacheEvictor;
import ro.mathlms.cache.CacheNames;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.content.RoCount;
import ro.mathlms.content.Chapter;
import ro.mathlms.content.ChapterRepository;
import ro.mathlms.content.Exercise;
import ro.mathlms.content.ExerciseRepository;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.quiz.QuizDtos.ItemDto;
import ro.mathlms.quiz.QuizDtos.QuizDetailDto;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Admin authoring of quizzes: quiz CRUD, publish, and item/option management. */
@Service
public class QuizAdminService {

    private final QuizRepository quizRepository;
    private final QuizItemRepository itemRepository;
    private final QuizOptionRepository optionRepository;
    private final QuizItemHintRepository hintRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ChapterRepository chapterRepository;
    private final ExerciseRepository exerciseRepository;
    private final AfterCommitCacheEvictor cacheEvictor;
    private final QuestionImageService imageService;

    public QuizAdminService(QuizRepository quizRepository, QuizItemRepository itemRepository,
                            QuizOptionRepository optionRepository, QuizItemHintRepository hintRepository,
                            SchoolClassRepository schoolClassRepository, ChapterRepository chapterRepository,
                            ExerciseRepository exerciseRepository, AfterCommitCacheEvictor cacheEvictor,
                            QuestionImageService imageService) {
        this.imageService = imageService;
        this.chapterRepository = chapterRepository;
        this.exerciseRepository = exerciseRepository;
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
        return createQuiz(title, description, schoolClassId, timeLimitMinutes, practiceAllowed, null);
    }

    /** ...tagged with the chapter its questions practise ({@code null} = untagged), which must fit the quiz's class. */
    @Transactional
    public Quiz createQuiz(String title, String description, Long schoolClassId, Integer timeLimitMinutes,
                           boolean practiceAllowed, Long chapterId) {
        Quiz quiz = new Quiz(title, description);
        SchoolClass target = findClass(schoolClassId);
        quiz.assignToClass(target);
        quiz.tagChapter(chapterFitting(chapterId, target));
        quiz.changeTimeLimit(timeLimitMinutes);
        quiz.allowPractice(practiceAllowed);
        return quizRepository.save(quiz);
    }

    @Transactional
    public Quiz updateQuiz(Long id, String title, String description, Long schoolClassId, Integer timeLimitMinutes,
                           boolean practiceAllowed) {
        return updateQuiz(id, title, description, schoolClassId, timeLimitMinutes, practiceAllowed, null);
    }

    @Transactional
    public Quiz updateQuiz(Long id, String title, String description, Long schoolClassId, Integer timeLimitMinutes,
                           boolean practiceAllowed, Long chapterId) {
        Quiz quiz = getQuiz(id);
        SchoolClass target = findClass(schoolClassId);
        Chapter chapter = chapterFitting(chapterId, target);
        if (target != null) {
            List<String> stranded = quizRepository.findAssignedClassNamesOtherThan(id, schoolClassId);
            if (!stranded.isEmpty()) {
                throw new QuizInUseException("Quiz-ul „" + quiz.getTitle() + "” e temă pentru " + String.join(", ", stranded)
                        + ": dacă îl rezervi pentru " + target.getName() + ", elevii de acolo nu-l mai pot deschide. "
                        + "Șterge întâi tema din pagina Teme sau lasă quiz-ul la „Toți elevii”.");
            }
        }
        quiz.update(title, description);
        quiz.assignToClass(target);
        quiz.changeTimeLimit(timeLimitMinutes); // attempts already running keep their own deadline
        quiz.allowPractice(practiceAllowed);    // practice already in progress simply finishes; no new one can start
        quiz.tagChapter(chapter);               // only bookkeeping for the mastery map: allowed even once taken
        return quizRepository.save(quiz);
    }

    /**
     * The chapter to tag with, or {@code null}. A quiz for one class may only use that class's chapters (a quiz for
     * every student may use any): otherwise its answers would colour a class map it does not belong to.
     */
    private Chapter chapterFitting(Long chapterId, SchoolClass quizClass) {
        if (chapterId == null) {
            return null;
        }
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new QuizNotFoundException("Chapter", chapterId));
        SchoolClass chapterClass = chapter.getBook().getSchoolClass();
        if (quizClass != null && !chapterClass.getId().equals(quizClass.getId())) {
            throw new InvalidQuizException("Quiz-ul e pentru " + quizClass.getName() + ", dar capitolul „"
                    + chapter.getTitle() + "” e din " + chapterClass.getName() + ".");
        }
        return chapter;
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
            if (itemRepository.countByQuizId(id) == 0) {
                throw new InvalidQuizException("Adaugă cel puțin un subiect înainte să publici quiz-ul.");
            }
            quiz.publish();
        } else {
            quiz.unpublish();
        }
        return quizRepository.save(quiz);
    }

    /**
     * A DRAFT copy with the same audience, timer, practice flag, items, options and hints: variant B of a test, or the
     * editable version of a quiz students already took (that one's items are frozen). Attempts and homework stay behind.
     */
    @Transactional
    public Quiz copyQuiz(Long id) {
        Quiz source = getQuiz(id);
        Quiz copy = new Quiz(copyTitle(source.getTitle()), source.getDescription());
        copy.assignToClass(source.getSchoolClass());
        copy.changeTimeLimit(source.getTimeLimitMinutes());
        copy.allowPractice(source.isPracticeAllowed());
        copy.tagChapter(source.getChapter());
        Quiz saved = quizRepository.save(copy);
        for (QuizItem item : itemRepository.findByQuizIdOrderByPosition(id)) {
            QuizItem fresh = new QuizItem(saved, item.getPosition(), item.getType(),
                    item.getStatement(), item.getPoints(), item.getSolution());
            fresh.tagChapter(item.getChapter());
            fresh.placeInSection(item.getSection());
            String imageCopy = imageService.copyOf(item.getImageKey()); // each question owns its own file
            if (imageCopy != null) {
                fresh.attachImage(imageCopy);
            } else {
                fresh.requireImage(item.isImageRequired());
            }
            QuizItem itemCopy = itemRepository.save(fresh);
            for (QuizOption option : optionRepository.findByItemIdOrderByPosition(item.getId())) {
                optionRepository.save(new QuizOption(itemCopy, option.getPosition(), option.getText(), option.isCorrect()));
            }
            for (QuizItemHint hint : hintRepository.findByItemIdOrderByPosition(item.getId())) {
                hintRepository.save(new QuizItemHint(itemCopy, hint.getPosition(), hint.getText()));
            }
        }
        return saved;
    }

    /**
     * T2: a DRAFT quiz made of exercises from the content bank, so the teacher does not type them twice. It is for the
     * chapter's class and tagged with the chapter (mastery map). Each exercise becomes an OPEN question (exercises have
     * no answer options; the student uploads a photo) worth {@code points}, with the exercise's solution as the barem,
     * in the chapter's order. The result is a copy: editing the quiz later does not touch the bank, and vice versa.
     */
    @Transactional
    public Quiz createFromExercises(Long chapterId, String title, List<Long> exerciseIds, int points) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new QuizNotFoundException("Chapter", chapterId));
        Set<Long> picked = new HashSet<>(exerciseIds);
        List<Exercise> exercises = exerciseRepository.findByChapterIdOrderById(chapterId).stream()
                .filter(e -> picked.contains(e.getId()))
                .toList();
        if (exercises.size() != picked.size()) {
            throw new InvalidQuizException("Alege doar exerciții din capitolul „" + chapter.getTitle() + "”.");
        }
        Quiz quiz = new Quiz(title, null);
        // Loaded (not the lazy proxy) so the summary returned after the transaction can still read its name.
        quiz.assignToClass(findClass(chapter.getBook().getSchoolClass().getId()));
        quiz.tagChapter(chapter);
        Quiz saved = quizRepository.save(quiz);
        int position = 0; // 0-based, like the builder and the text import
        for (Exercise exercise : exercises) {
            itemRepository.save(new QuizItem(saved, position++, QuizItemType.OPEN,
                    exercise.getStatement(), points, exercise.getSolution()));
        }
        return saved;
    }

    /** "Teza (copie)", cut so it still fits the 200 characters a title may have. */
    private static String copyTitle(String title) {
        String suffix = " (copie)";
        int room = 200 - suffix.length();
        return (title.length() > room ? title.substring(0, room) : title) + suffix;
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
            imageService.deleteAfterCommit(item.getImageKey());
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
        requireNotTaken(quiz, "nu mai poți adăuga subiecte");
        requireValidHints(request.hints()); // before anything is written
        QuizItem fresh = new QuizItem(
                quiz, request.position(), request.type(), request.statement(),
                request.points(), request.solution());
        fresh.tagChapter(chapterFitting(request.chapterId(), quiz.getSchoolClass()));
        fresh.placeInSection(request.section());
        fresh.requireImage(Boolean.TRUE.equals(request.imageRequired()));
        QuizItem item = itemRepository.save(fresh);
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
        // The chapter tag is bookkeeping for the mastery map, not part of the grade: it may change even once taken.
        item.tagChapter(chapterFitting(request.chapterId(), item.getQuiz().getSchoolClass()));
        item.placeInSection(request.section()); // layout only, like the chapter tag: allowed even once taken
        boolean taken = quizRepository.countAttempts(item.getQuiz().getId()) > 0;
        List<QuizOption> options;
        if (taken) {
            // Students' answers point at these options and their grades at these points: only texts may change.
            options = correctTextsInPlace(item, request);
            item.update(request.position(), request.statement(), item.getPoints(), request.solution());
            itemRepository.save(item);
        } else {
            item.update(request.position(), request.statement(), request.points(), request.solution());
            itemRepository.save(item);
            // Replace options wholesale for a single-choice item.
            optionRepository.deleteByItemId(itemId);
            options = saveOptionsIfSingleChoice(item, request);
        }
        hintRepository.deleteByItemId(itemId); // hints are replaced wholesale too
        List<QuizItemHint> hints = saveHints(item, request.hints());
        dropScoreCaches();
        return ItemDto.from(item, options, hints);
    }

    @Transactional
    public void deleteItem(Long itemId) {
        QuizItem item = getItem(itemId);
        Quiz quiz = item.getQuiz();
        requireNotTaken(quiz, "nu mai poți șterge subiecte");
        if (quiz.getStatus() == QuizStatus.PUBLISHED && itemRepository.countByQuizId(quiz.getId()) <= 1) {
            throw new InvalidQuizException("Acesta e ultimul subiect al unui quiz publicat: apasă întâi „Depublică”, "
                    + "altfel elevii ar vedea un quiz gol.");
        }
        optionRepository.deleteByItemId(itemId);
        hintRepository.deleteByItemId(itemId);
        imageService.deleteAfterCommit(item.getImageKey());
        itemRepository.delete(item);
        dropScoreCaches();
    }

    /**
     * Once students have attempts, the quiz's items and points are frozen: the max score is computed from the current
     * items, so adding or removing one would silently re-grade every past attempt (and answers reference the items).
     */
    private void requireNotTaken(Quiz quiz, String refused) {
        long attempts = quizRepository.countAttempts(quiz.getId());
        if (attempts > 0) {
            throw new QuizInUseException("Quiz-ul „" + quiz.getTitle() + "” a fost deja dat de elevi ("
                    + RoCount.of(attempts, "încercare", "încercări") + "): " + refused
                    + ", s-ar schimba punctajul maxim și notele lor. Apasă „Copiază” pe rândul quiz-ului și modifică copia.");
        }
    }

    /** On a taken item: same points, same options in the same order with the same right one — only the texts change. */
    private List<QuizOption> correctTextsInPlace(QuizItem item, ItemRequest request) {
        String refusal = "Quiz-ul „" + item.getQuiz().getTitle() + "” a fost deja dat de elevi: la acest subiect poți "
                + "corecta doar textele (enunț, variante, rezolvare, indicii) — punctajul, numărul de variante și "
                + "varianta corectă nu se mai pot schimba.";
        if (request.points() != item.getPoints()) {
            throw new QuizInUseException(refusal);
        }
        if (item.getType() != QuizItemType.SINGLE_CHOICE) {
            return List.of();
        }
        List<QuizOption> existing = optionRepository.findByItemIdOrderByPosition(item.getId());
        List<OptionRequest> requested = request.options() == null ? List.of()
                : request.options().stream().sorted(java.util.Comparator.comparingInt(OptionRequest::position)).toList();
        if (requested.size() != existing.size()) {
            throw new QuizInUseException(refusal);
        }
        for (int i = 0; i < existing.size(); i++) {
            if (existing.get(i).isCorrect() != requested.get(i).correct()) {
                throw new QuizInUseException(refusal);
            }
        }
        for (int i = 0; i < existing.size(); i++) {
            existing.get(i).changeText(requested.get(i).text());
            optionRepository.save(existing.get(i));
        }
        return existing;
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
