package ro.mathlms.quiz;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEvent;
import ro.mathlms.audit.AuditLog;
import ro.mathlms.audit.AuditText;
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
import ro.mathlms.teaching.TeacherScope;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Admin authoring of quizzes: quiz CRUD, publish, and item/option management. Quiz-level changes are always journalled;
 * question-level ones only once students can see the quiz (published, or already taken) - drafting a test question by
 * question, or importing 30 at once, is not something anyone will need to account for later.
 */
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
    private final AuditLog auditLog;
    private final QuizItemPartRepository partRepository;
    private final EnChapterLinks enChapterLinks;
    private final UnfinishedAttempts unfinishedAttempts;
    private final TeacherScope scope;

    public QuizAdminService(QuizRepository quizRepository, QuizItemRepository itemRepository,
                            QuizOptionRepository optionRepository, QuizItemHintRepository hintRepository,
                            SchoolClassRepository schoolClassRepository, ChapterRepository chapterRepository,
                            ExerciseRepository exerciseRepository, AfterCommitCacheEvictor cacheEvictor,
                            QuestionImageService imageService, AuditLog auditLog, QuizItemPartRepository partRepository,
                            EnChapterLinks enChapterLinks, UnfinishedAttempts unfinishedAttempts,
                            TeacherScope scope) {
        this.enChapterLinks = enChapterLinks;
        this.unfinishedAttempts = unfinishedAttempts;
        this.scope = scope;
        this.partRepository = partRepository;
        this.imageService = imageService;
        this.auditLog = auditLog;
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

    /** The list rows' numbers (see {@link QuizRepository#listSummaries()}), for every quiz. */
    public List<QuizListSummaryDto> listSummaries() {
        return quizRepository.listSummaries().stream().map(QuizListSummaryDto::from).toList();
    }

    public Quiz getQuiz(Long id) {
        return quizRepository.findById(id)
                .orElseThrow(() -> new QuizNotFoundException("Quiz", id));
    }

    /** One query per kind (options, hints, requirements) for the whole quiz, not three per question. */
    @Transactional(readOnly = true)
    public QuizDetailDto getQuizDetail(Long id) {
        Quiz quiz = getQuiz(id);
        List<QuizItem> quizItems = itemRepository.findByQuizIdOrderByPosition(id);
        EnChapterLinks.Resolved links = linksOf(quizItems);
        List<Long> itemIds = quizItems.stream().map(QuizItem::getId).toList();
        Map<Long, List<QuizOption>> options = itemIds.isEmpty() ? Map.of()
                : optionRepository.findByItemIdInOrderByItemIdAscPositionAsc(itemIds).stream()
                        .collect(Collectors.groupingBy(o -> o.getItem().getId()));
        Map<Long, List<QuizItemHint>> hints = itemIds.isEmpty() ? Map.of()
                : hintRepository.findByItemIdInOrderByItemIdAscPositionAsc(itemIds).stream()
                        .collect(Collectors.groupingBy(h -> h.getItem().getId()));
        Map<Long, List<QuizItemPart>> parts = itemIds.isEmpty() ? Map.of()
                : partRepository.findByItemIdInOrderByItemIdAscPositionAsc(itemIds).stream()
                        .collect(Collectors.groupingBy(p -> p.getItem().getId()));
        List<ItemDto> items = quizItems.stream()
                .map(item -> ItemDto.from(item, options.getOrDefault(item.getId(), List.of()),
                        hints.getOrDefault(item.getId(), List.of()),
                        parts.getOrDefault(item.getId(), List.of()), links))
                .toList();
        return QuizDetailDto.of(quiz, items, quizRepository.countAttempts(id) > 0);
    }

    /**
     * The shuffle switches (V65). Any time, even on a test students took: it decides only the attempts started from now
     * on (each attempt keeps the order it began with), and grading never depends on the order.
     */
    @Transactional
    public Quiz setShuffle(Long id, boolean items, boolean options) {
        Quiz quiz = getQuiz(id);
        quiz.shuffle(items, options);
        return quizRepository.save(quiz);
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
        return createQuiz(title, description, schoolClassId, timeLimitMinutes, practiceAllowed, chapterId, null);
    }

    /** ...and how many times a student may hand it in ({@code null} = no limit). */
    @Transactional
    public Quiz createQuiz(String title, String description, Long schoolClassId, Integer timeLimitMinutes,
                           boolean practiceAllowed, Long chapterId, Integer maxAttempts) {
        Quiz quiz = new Quiz(title, description);
        SchoolClass target = findClass(schoolClassId);
        quiz.assignToClass(target);
        quiz.tagChapter(chapterFitting(chapterId, target));
        quiz.changeTimeLimit(timeLimitMinutes);
        quiz.allowPractice(practiceAllowed);
        quiz.limitAttempts(maxAttempts);
        quiz.ownedBy(scope.currentUserId());
        Quiz saved = quizRepository.save(quiz);
        auditLog.record(AuditEvent.of(AuditAction.QUIZ_CREATED, saved.getId(), saved.getTitle())
                .details(audience(saved)));
        return saved;
    }

    /** "pentru Clasa a 9-a" / "pentru toți elevii". */
    private static String audience(Quiz quiz) {
        return quiz.getSchoolClass() == null ? "pentru toți elevii" : "pentru " + quiz.getSchoolClass().getName();
    }

    /** Students see it (published) or saw it (has attempts): from then on, its questions' edits are journalled. */
    private boolean seenByStudents(Quiz quiz) {
        return quiz.getStatus() == QuizStatus.PUBLISHED || quizRepository.countAttempts(quiz.getId()) > 0;
    }

    private void recordQuestion(AuditAction action, QuizItem item, String number, String details) {
        auditLog.record(AuditEvent.of(action, item.getQuiz().getId(), AuditText.question(item.getQuiz().getTitle(), number))
                .details(details));
    }

    @Transactional
    public Quiz updateQuiz(Long id, String title, String description, Long schoolClassId, Integer timeLimitMinutes,
                           boolean practiceAllowed) {
        return updateQuiz(id, title, description, schoolClassId, timeLimitMinutes, practiceAllowed, null);
    }

    @Transactional
    public Quiz updateQuiz(Long id, String title, String description, Long schoolClassId, Integer timeLimitMinutes,
                           boolean practiceAllowed, Long chapterId) {
        return updateQuiz(id, title, description, schoolClassId, timeLimitMinutes, practiceAllowed, chapterId,
                getQuiz(id).getMaxAttempts());
    }

    @Transactional
    public Quiz updateQuiz(Long id, String title, String description, Long schoolClassId, Integer timeLimitMinutes,
                           boolean practiceAllowed, Long chapterId, Integer maxAttempts) {
        Quiz quiz = getQuiz(id);
        SchoolClass target = findClass(schoolClassId);
        Chapter chapter = chapterFitting(chapterId, target);
        if (target != null) {
            List<String> stranded = quizRepository.findAssignedClassNamesOtherThan(id, schoolClassId);
            if (!stranded.isEmpty()) {
                throw new QuizInUseException("Testul „" + quiz.getTitle() + "” e temă pentru " + String.join(", ", stranded)
                        + ": dacă îl rezervi pentru " + target.getName() + ", elevii de acolo nu-l mai pot deschide. "
                        + "Șterge întâi tema din pagina Teme sau lasă testul la „Toți elevii”.");
            }
        }
        String oldTitle = quiz.getTitle();
        AuditText.Changes changes = new AuditText.Changes()
                .addIfDifferent(quiz.getDescription(), description, "descriere")
                .addIfDifferent(classId(quiz.getSchoolClass()), schoolClassId, "elevi: " + audience(target))
                .addIfDifferent(quiz.getTimeLimitMinutes(), timeLimitMinutes,
                        "timp " + minutes(quiz.getTimeLimitMinutes()) + " → " + minutes(timeLimitMinutes))
                .addIfDifferent(quiz.getMaxAttempts(), maxAttempts,
                        "încercări " + attempts(quiz.getMaxAttempts()) + " → " + attempts(maxAttempts))
                .addIf(quiz.isPracticeAllowed() != practiceAllowed, practiceAllowed ? "exersare permisă" : "exersare oprită")
                .addIfDifferent(quiz.getChapter() == null ? null : quiz.getChapter().getId(), chapterId, "capitol");
        quiz.update(title, description);
        quiz.assignToClass(target);
        quiz.changeTimeLimit(timeLimitMinutes); // attempts already running keep their own deadline
        quiz.allowPractice(practiceAllowed);    // practice already in progress simply finishes; no new one can start
        quiz.tagChapter(chapter);               // only bookkeeping for the mastery map: allowed even once taken
        quiz.limitAttempts(maxAttempts);        // decides only the next start: what was handed in stays handed in
        Quiz saved = quizRepository.save(quiz);
        String renamed = AuditText.renamedFrom(oldTitle, quiz.getTitle());
        String details = renamed == null ? changes.joined()
                : changes.isEmpty() ? renamed : renamed + "; " + changes.joined();
        auditLog.record(AuditEvent.of(AuditAction.QUIZ_UPDATED, id, quiz.getTitle()).details(details));
        return saved;
    }

    private static String audience(SchoolClass target) {
        return target == null ? "toți elevii" : target.getName();
    }

    private static Long classId(SchoolClass schoolClass) {
        return schoolClass == null ? null : schoolClass.getId();
    }

    private static String attempts(Integer limit) {
        return limit == null ? "nelimitate" : String.valueOf(limit);
    }

    private static String minutes(Integer limit) {
        return limit == null ? "fără limită" : limit + " min";
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
            throw new InvalidQuizException("Testul e pentru " + quizClass.getName() + ", dar capitolul „"
                    + chapter.getTitle() + "” e din " + chapterClass.getName() + ".");
        }
        return chapter;
    }

    /**
     * The class a test is for. A TEACHER must pick one of their own classes: a test "for every student" would reach the
     * students of every other teacher, so only an ADMIN may leave it empty.
     */
    private SchoolClass findClass(Long schoolClassId) {
        if (schoolClassId == null) {
            if (scope.restricted()) {
                throw new InvalidQuizException("Alege clasa testului: un test pentru toți elevii îl poate face doar un administrator.");
            }
            return null;
        }
        scope.requireClass(schoolClassId);
        return schoolClassRepository.findById(schoolClassId)
                .orElseThrow(() -> new QuizNotFoundException("SchoolClass", schoolClassId));
    }

    /** What the teacher is told before unpublishing: counted at that moment, never remembered from the list. */
    public QuizDtos.QuizActivityDto getActivity(Long id) {
        getQuiz(id);
        return new QuizDtos.QuizActivityDto(quizRepository.countStudentsWriting(id), quizRepository.countHomeworkClasses(id));
    }

    @Transactional
    public Quiz setPublished(Long id, boolean published) {
        Quiz quiz = getQuiz(id);
        QuizStatus before = quiz.getStatus();
        if (published) {
            if (itemRepository.countByQuizId(id) == 0) {
                throw new InvalidQuizException("Adaugă cel puțin o întrebare înainte să publici testul.");
            }
            requireEveryGrilaAnswered(quiz);
            quiz.publish();
        } else {
            quiz.unpublish();
        }
        Quiz saved = quizRepository.save(quiz);
        if (quiz.getStatus() != before) {
            auditLog.record(AuditEvent.of(published ? AuditAction.QUIZ_PUBLISHED : AuditAction.QUIZ_UNPUBLISHED,
                    id, quiz.getTitle()));
        }
        return saved;
    }

    /** Students would be marked against nothing: a grilă without its right answer keeps the test from being published. */
    private void requireEveryGrilaAnswered(Quiz quiz) {
        List<QuizItem> items = itemRepository.findByQuizIdOrderByPosition(quiz.getId());
        Map<Long, String> numbers = QuestionNumbers.of(items);
        List<String> unanswered = items.stream()
                .filter(i -> i.getType() == QuizItemType.SINGLE_CHOICE)
                .filter(i -> optionRepository.findByItemIdOrderByPosition(i.getId()).stream().noneMatch(QuizOption::isCorrect))
                .map(i -> numbers.get(i.getId()))
                .toList();
        if (!unanswered.isEmpty()) {
            throw new InvalidQuizException((unanswered.size() == 1 ? "Grila " + unanswered.get(0) + " nu are"
                    : "Grilele " + String.join(", ", unanswered) + " nu au") + " răspunsul corect. Pune baremul "
                    + "(„Barem din poze”) sau bifează varianta corectă, apoi publică.");
        }
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
        copy.limitAttempts(source.getMaxAttempts());
        copy.shuffle(source.isShuffleItems(), source.isShuffleOptions());
        copy.tagChapter(source.getChapter());
        copy.ownedBy(scope.currentUserId()); // the copy belongs to whoever made it
        Quiz saved = quizRepository.save(copy);
        for (QuizItem item : itemRepository.findByQuizIdOrderByPosition(id)) {
            copyItem(item, saved, item.getPosition());
        }
        auditLog.record(AuditEvent.of(AuditAction.QUIZ_COPIED, saved.getId(), saved.getTitle())
                .details("copie după „" + source.getTitle() + "”"));
        return saved;
    }

    /** A question with everything it holds, as a new one in {@code target}. */
    private QuizItem copyItem(QuizItem item, Quiz target, int position) {
        QuizItem fresh = new QuizItem(target, position, item.getType(),
                item.getStatement(), item.getPoints(), item.getSolution());
        fresh.tagChapter(item.getChapter());
        fresh.pointToEnChapter(item.getEnChapter());
        fresh.placeInSection(item.getSection());
        if (item.getType() == QuizItemType.NUMERIC) {
            fresh.expectNumber(item.getNumericAnswer());
        }
        String imageCopy = imageService.copyOf(item.getImageKey()); // each question owns its own file
        if (imageCopy != null) {
            fresh.attachImage(imageCopy);
        } else {
            fresh.requireImage(item.isImageRequired());
        }
        QuizItem itemCopy = itemRepository.save(fresh);
        for (QuizOption option : optionRepository.findByItemIdOrderByPosition(item.getId())) {
            QuizOption optionCopy = new QuizOption(itemCopy, option.getPosition(), option.getText(), option.isCorrect());
            optionCopy.explainMistake(option.getMistake());
            optionRepository.save(optionCopy);
        }
        for (QuizItemHint hint : hintRepository.findByItemIdOrderByPosition(item.getId())) {
            hintRepository.save(new QuizItemHint(itemCopy, hint.getPosition(), hint.getText()));
        }
        for (QuizItemPart part : partRepository.findByItemIdOrderByPosition(item.getId())) {
            QuizItemPart partCopy = new QuizItemPart(itemCopy, part.getPosition(), part.getStatement(), part.getPoints(),
                    part.getSolution());
            partCopy.pointToEnChapter(part.getEnChapter());
            partRepository.save(partCopy);
        }
        return itemCopy;
    }

    /**
     * Moves a question one place up or down inside its section: past a heading it would land under another subject. Not
     * once students took the test, since the numbers on their papers would no longer match.
     */
    @Transactional
    public void moveItem(Long itemId, boolean up) {
        QuizItem item = getItem(itemId);
        Quiz quiz = item.getQuiz();
        if (quizRepository.countAttempts(quiz.getId()) > 0) {
            throw new QuizInUseException("Testul „" + quiz.getTitle() + "” a fost deja dat de elevi: ordinea întrebărilor "
                    + "nu se mai poate schimba, numerele din lucrările lor n-ar mai fi aceleași. "
                    + "Apasă „Copiază” pe rândul testului și modifică copia.");
        }
        List<QuizItem> items = new java.util.ArrayList<>(itemRepository.findByQuizIdOrderByPosition(quiz.getId()));
        int from = items.indexOf(item);
        int to = up ? from - 1 : from + 1;
        if (to < 0 || to >= items.size() || !java.util.Objects.equals(items.get(to).getSection(), item.getSection())) {
            throw new InvalidQuizException("Întrebarea e deja la capătul secțiunii ei.");
        }
        java.util.Collections.swap(items, from, to);
        renumber(items);
        dropScoreCaches();
        if (seenByStudents(quiz)) {
            recordQuestion(AuditAction.QUESTION_UPDATED, item, QuestionNumbers.of(item, items),
                    up ? "mutată mai sus" : "mutată mai jos");
        }
    }

    /** A copy of a question, right under it: a variant is usually written next to the original. */
    @Transactional
    public void duplicateItem(Long itemId) {
        QuizItem item = getItem(itemId);
        Quiz quiz = item.getQuiz();
        requireNotTaken(quiz, "nu mai poți adăuga întrebări");
        List<QuizItem> items = new java.util.ArrayList<>(itemRepository.findByQuizIdOrderByPosition(quiz.getId()));
        int at = items.indexOf(item) + 1;
        QuizItem copy = copyItem(item, quiz, at);
        items.add(at, copy);
        renumber(items);
        dropScoreCaches();
        if (seenByStudents(quiz)) {
            recordQuestion(AuditAction.QUESTION_ADDED, copy, QuestionNumbers.of(copy, items), copy.getPoints() + " p, copie");
        }
    }

    /** Positions 0, 1, 2... in this order: older tests may have gaps or two questions on the same place. */
    private static void renumber(List<QuizItem> items) {
        for (int i = 0; i < items.size(); i++) {
            items.get(i).moveTo(i);
        }
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
        quiz.limitAttempts(Quiz.DEFAULT_ATTEMPTS);
        quiz.ownedBy(scope.currentUserId());
        Quiz saved = quizRepository.save(quiz);
        int position = 0; // 0-based, like the builder and the text import
        for (Exercise exercise : exercises) {
            itemRepository.save(new QuizItem(saved, position++, QuizItemType.OPEN,
                    exercise.getStatement(), points, exercise.getSolution()));
        }
        auditLog.record(AuditEvent.of(AuditAction.QUIZ_CREATED, saved.getId(), saved.getTitle())
                .details("din capitolul „" + chapter.getTitle() + "”, "
                        + RoCount.of(exercises.size(), "exercițiu", "exerciții")));
        return saved;
    }

    /** "Teza (copie)", cut so it still fits the 200 characters a title may have. */
    private static String copyTitle(String title) {
        String suffix = " (copie)";
        int room = 200 - suffix.length();
        return (title.length() > room ? title.substring(0, room) : title) + suffix;
    }

    /**
     * Deletes a test and its questions. A test with handed-in attempts stays: those are grades (and the mastery maps).
     * Attempts only started go with it, but not while the test is published - a student may be writing right now;
     * once it is unpublished they cannot finish them, so "Depublică, apoi Șterge" always works.
     */
    @Transactional
    public void deleteQuiz(Long id) {
        Quiz quiz = getQuiz(id);
        boolean published = quiz.getStatus() == QuizStatus.PUBLISHED;
        long handedIn = quizRepository.countHandedInAttempts(id);
        if (handedIn > 0) {
            throw new QuizInUseException("Testul „" + quiz.getTitle() + "” nu poate fi șters: elevii au predat deja "
                    + RoCount.of(handedIn, "încercare", "încercări") + ", cu răspunsuri și note care s-ar pierde. "
                    + (published ? "Apasă „Depublică” ca să nu mai apară la elevi."
                                 : "E deja ciornă, deci elevii nu-l mai văd; rămâne doar pentru notele lor."));
        }
        long started = quizRepository.countAttempts(id); // none handed in: every one is still in progress
        if (started > 0 && published) {
            throw new QuizInUseException("Testul „" + quiz.getTitle() + "” nu poate fi șters acum: elevii îl lucrează ("
                    + RoCount.of(started, "încercare începută și nepredată", "încercări începute și nepredate") + "). "
                    + "Apasă întâi „Depublică”, apoi „Șterge” - încercările neterminate se șterg odată cu el.");
        }
        int discarded = unfinishedAttempts.discard(id);
        for (QuizItem item : itemRepository.findByQuizIdOrderByPosition(id)) {
            optionRepository.deleteByItemId(item.getId());
            hintRepository.deleteByItemId(item.getId());
            imageService.deleteAfterCommit(item.getImageKey());
        }
        itemRepository.deleteAll(itemRepository.findByQuizIdOrderByPosition(id));
        quizRepository.delete(quiz);
        dropScoreCaches();
        AuditEvent deleted = AuditEvent.of(AuditAction.QUIZ_DELETED, id, quiz.getTitle());
        auditLog.record(discarded == 0 ? deleted
                : deleted.details("cu " + RoCount.of(discarded, "încercare neterminată", "încercări neterminate")));
    }

    // --- item-level ---

    public QuizItem getItem(Long itemId) {
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new QuizNotFoundException("QuizItem", itemId));
    }

    @Transactional
    public ItemDto addItem(Long quizId, ItemRequest request) {
        Quiz quiz = getQuiz(quizId);
        requireNotTaken(quiz, "nu mai poți adăuga întrebări");
        Added added = insertItem(quiz, request);
        dropScoreCaches();
        List<QuizItem> quizItems = itemRepository.findByQuizIdOrderByPosition(quizId);
        if (seenByStudents(quiz)) {
            recordQuestion(AuditAction.QUESTION_ADDED, added.item(), QuestionNumbers.of(added.item(), quizItems),
                    added.item().getPoints() + " p");
        }
        return ItemDto.from(added.item(), added.options(), added.hints(), added.parts(), enChapterLinks.resolve(quizItems));
    }

    /**
     * Fills a new test with its questions at once (the AI import): nothing is sent back, so the links are worked out
     * once at the end, not per question. An EN chapter chosen for a question that is just the default for its place is
     * dropped back to automatic - the AI proposes it, and pinned it would no longer follow the automatic links.
     */
    @Transactional
    public void addItems(Long quizId, List<ItemRequest> requests) {
        Quiz quiz = getQuiz(quizId);
        requireNotTaken(quiz, "nu mai poți adăuga întrebări");
        List<QuizItemPart> parts = new java.util.ArrayList<>();
        for (ItemRequest request : requests) {
            parts.addAll(insertItem(quiz, request).parts());
        }
        dropScoreCaches();
        releaseDefaultEnChapters(itemRepository.findByQuizIdOrderByPosition(quizId), parts);
    }

    private record Added(QuizItem item, List<QuizOption> options, List<QuizItemHint> hints, List<QuizItemPart> parts) {
    }

    /** One question with its options, hints and requirements, checked before anything is written. */
    private Added insertItem(Quiz quiz, ItemRequest request) {
        requireValidHints(request.hints()); // before anything is written
        requireValidParts(request);
        requireNumber(request);
        QuizItem fresh = new QuizItem(
                quiz, request.position(), request.type(), request.statement(),
                pointsOf(request), request.solution());
        fresh.tagChapter(chapterFitting(request.chapterId(), quiz.getSchoolClass()));
        fresh.pointToEnChapter(enChapter(request.enChapterId()));
        fresh.placeInSection(request.section());
        fresh.requireImage(Boolean.TRUE.equals(request.imageRequired()));
        if (request.type() == QuizItemType.NUMERIC) {
            fresh.expectNumber(request.numericAnswer());
        }
        QuizItem item = itemRepository.save(fresh);
        return new Added(item, saveOptionsIfSingleChoice(item, request), saveHints(item, request.hints()),
                saveParts(item, request.parts()));
    }

    /**
     * Back to automatic wherever the chosen chapter is the one automatic would give anyway. The requirements are
     * checked against their problem's link, which does not move: a problem is only released when it points at its
     * default already.
     */
    private void releaseDefaultEnChapters(List<QuizItem> quizItems, List<QuizItemPart> parts) {
        EnChapterLinks.Resolved links = enChapterLinks.resolve(quizItems);
        for (QuizItemPart part : parts) {
            if (part.getEnChapter() != null
                    && part.getEnChapter().getId().equals(EnChapterLinks.idOf(links.partDefault(part)))) {
                part.pointToEnChapter(null);
            }
        }
        for (QuizItem item : quizItems) {
            if (item.getEnChapter() != null
                    && item.getEnChapter().getId().equals(EnChapterLinks.idOf(links.itemDefault(item)))) {
                item.pointToEnChapter(null);
            }
        }
    }

    @Transactional
    public ItemDto updateItem(Long itemId, ItemRequest request) {
        QuizItem item = getItem(itemId);
        if (item.getType() != request.type()) {
            // Only on a draft nobody took: no answer points at its options yet, and options, requirements and the right
            // answer are replaced wholesale below anyway. The statement, points and hints stay as sent.
            if (item.getQuiz().getStatus() != QuizStatus.DRAFT
                    || quizRepository.countAttempts(item.getQuiz().getId()) > 0) {
                throw new InvalidQuizException(
                        "Tipul întrebării se poate schimba doar cât testul e ciornă și nu l-a dat încă niciun elev.");
            }
            item.changeType(request.type());
        }
        requireValidHints(request.hints());
        requireValidParts(request);
        requireNumber(request);
        boolean journalled = seenByStudents(item.getQuiz());
        ItemSnapshot before = journalled ? snapshot(item) : null;
        // The chapter tag is bookkeeping for the mastery map, not part of the grade: it may change even once taken.
        item.tagChapter(chapterFitting(request.chapterId(), item.getQuiz().getSchoolClass()));
        item.pointToEnChapter(enChapter(request.enChapterId())); // where to revise: allowed even once taken
        item.placeInSection(request.section()); // layout only, like the chapter tag: allowed even once taken
        boolean taken = quizRepository.countAttempts(item.getQuiz().getId()) > 0;
        List<QuizOption> options;
        List<QuizItemPart> parts;
        if (taken) {
            // Students' answers point at these options and their grades at these points: only texts may change.
            options = correctTextsInPlace(item, request);
            parts = correctPartTextsInPlace(item, request);
            if (item.getType() == QuizItemType.NUMERIC) {
                if (!NumericAnswer.same(item.getNumericAnswer(), request.numericAnswer())) {
                    throw new QuizInUseException("Testul „" + item.getQuiz().getTitle() + "” a fost deja dat de elevi: "
                            + "răspunsul corect se schimbă din Statistici, ca să se recalculeze notele.");
                }
                item.expectNumber(request.numericAnswer()); // the same value, written another way
            }
            item.update(request.position(), request.statement(), item.getPoints(), request.solution());
            itemRepository.save(item);
        } else {
            item.update(request.position(), request.statement(), pointsOf(request), request.solution());
            if (item.getType() == QuizItemType.NUMERIC) {
                item.expectNumber(request.numericAnswer());
            }
            itemRepository.save(item);
            // Replace options and requirements wholesale.
            optionRepository.deleteByItemId(itemId);
            options = saveOptionsIfSingleChoice(item, request);
            partRepository.deleteByItemId(itemId);
            parts = saveParts(item, request.parts());
        }
        hintRepository.deleteByItemId(itemId); // hints are replaced wholesale too
        List<QuizItemHint> hints = saveHints(item, request.hints());
        dropScoreCaches();
        if (journalled) {
            String changes = before.changesTo(snapshot(item, options, hints, parts));
            if (changes != null) { // re-saved unchanged (e.g. only moved): not worth a row
                recordQuestion(AuditAction.QUESTION_UPDATED, item, before.number(), changes);
            }
        }
        return ItemDto.from(item, options, hints, parts,
                linksOf(itemRepository.findByQuizIdOrderByPosition(item.getQuiz().getId())));
    }

    /**
     * Saves the barem the teacher kept from the AI's reading of the official barem, all questions in one transaction:
     * the right option of a grilă, the barem of an open question and of its requirements. Once students took the test
     * the right option can no longer change (their grile were marked against it) - the texts still can. Each changed
     * question of a test students see is journalled like an edit. A question or requirement of another test is not
     * found.
     */
    @Transactional
    public void applyBarem(Long quizId, List<BaremApply.ItemBarem> entries) {
        Quiz quiz = getQuiz(quizId);
        boolean taken = quizRepository.countAttempts(quizId) > 0;
        boolean journalled = seenByStudents(quiz);
        List<QuizItem> ordered = itemRepository.findByQuizIdOrderByPosition(quizId);
        Map<Long, QuizItem> items = ordered.stream().collect(Collectors.toMap(QuizItem::getId, Function.identity()));
        for (BaremApply.ItemBarem entry : entries) {
            QuizItem item = items.get(entry.itemId());
            if (item == null) {
                throw new QuizNotFoundException("QuizItem", entry.itemId());
            }
            ItemSnapshot before = journalled ? snapshot(item) : null;
            List<QuizOption> options = optionRepository.findByItemIdOrderByPosition(item.getId());
            if (entry.correctOptionId() != null) {
                if (item.getType() != QuizItemType.SINGLE_CHOICE
                        || options.stream().noneMatch(o -> o.getId().equals(entry.correctOptionId()))) {
                    throw new InvalidQuizException("Varianta aleasă nu e a acestei întrebări.");
                }
                boolean changes = options.stream().anyMatch(o -> o.isCorrect() != o.getId().equals(entry.correctOptionId()));
                if (changes && taken) {
                    throw new QuizInUseException("Testul „" + quiz.getTitle() + "” a fost deja dat de elevi: varianta "
                            + "corectă a întrebării " + QuestionNumbers.of(item, ordered)
                            + " nu se mai poate schimba de aici: o schimbi din Statistici, ca să se recalculeze notele. "
                            + "Baremul scris se poate salva în continuare.");
                }
                options.forEach(o -> o.markCorrect(o.getId().equals(entry.correctOptionId())));
                optionRepository.saveAll(options);
            }
            if (entry.barem() != null) {
                String barem = entry.barem().isBlank() ? null : entry.barem().strip();
                item.update(item.getPosition(), item.getStatement(), item.getPoints(), barem);
                itemRepository.save(item);
            }
            List<QuizItemPart> parts = partRepository.findByItemIdOrderByPosition(item.getId());
            for (BaremApply.PartBarem partBarem : entry.parts() == null ? List.<BaremApply.PartBarem>of() : entry.parts()) {
                QuizItemPart part = parts.stream().filter(p -> p.getId().equals(partBarem.partId())).findFirst()
                        .orElseThrow(() -> new QuizNotFoundException("QuizItemPart", partBarem.partId()));
                if (partBarem.barem() != null) {
                    part.update(part.getStatement(), partBarem.barem().isBlank() ? null : partBarem.barem().strip());
                    partRepository.save(part);
                }
            }
            if (journalled) {
                String changes = before.changesTo(snapshot(item, options,
                        hintRepository.findByItemIdOrderByPosition(item.getId()), parts));
                if (changes != null) {
                    recordQuestion(AuditAction.QUESTION_UPDATED, item, before.number(), changes + " (din barem)");
                }
            }
        }
        dropScoreCaches();
    }

    /**
     * Sets the EN chapter of several questions (and requirements) of one test at once - what the teacher kept of the
     * AI's proposals. Allowed even once the test was taken: it only says where to revise. One journal row says how many
     * changed. A question or requirement of another test is not found.
     */
    @Transactional
    public void assignEnChapters(Long quizId, List<EnChapterAssignments.ItemAssignment> assignments) {
        Quiz quiz = getQuiz(quizId);
        Map<Long, QuizItem> items = itemRepository.findByQuizIdOrderByPosition(quizId).stream()
                .collect(java.util.stream.Collectors.toMap(QuizItem::getId, java.util.function.Function.identity()));
        int changed = 0;
        for (EnChapterAssignments.ItemAssignment assignment : assignments) {
            QuizItem item = items.get(assignment.itemId());
            if (item == null) {
                throw new QuizNotFoundException("QuizItem", assignment.itemId());
            }
            if (!java.util.Objects.equals(EnChapterLinks.idOf(item.getEnChapter()), assignment.enChapterId())) {
                item.pointToEnChapter(enChapter(assignment.enChapterId()));
                changed++;
            }
            if (assignment.parts() == null || assignment.parts().isEmpty()) {
                continue;
            }
            Map<Long, QuizItemPart> parts = partRepository.findByItemIdOrderByPosition(item.getId()).stream()
                    .collect(java.util.stream.Collectors.toMap(QuizItemPart::getId, java.util.function.Function.identity()));
            for (EnChapterAssignments.PartAssignment partAssignment : assignment.parts()) {
                QuizItemPart part = parts.get(partAssignment.partId());
                if (part == null) {
                    throw new QuizNotFoundException("QuizItemPart", partAssignment.partId());
                }
                if (!java.util.Objects.equals(EnChapterLinks.idOf(part.getEnChapter()), partAssignment.enChapterId())) {
                    part.pointToEnChapter(enChapter(partAssignment.enChapterId()));
                    changed++;
                }
            }
        }
        if (changed > 0) {
            auditLog.record(AuditEvent.of(AuditAction.QUIZ_UPDATED, quizId, quiz.getTitle())
                    .details("capitol EN la " + ro.mathlms.content.RoCount.of(changed, "întrebare sau cerință",
                            "întrebări sau cerințe")));
        }
    }

    private EnChapterLinks.Resolved linksOf(List<QuizItem> quizItems) {
        return enChapterLinks.resolve(quizItems);
    }

    private ro.mathlms.enchapter.EnChapter enChapter(Long enChapterId) {
        return enChapterLinks.chosen(enChapterId);
    }

    /** What a question looked like, to word what an edit changed ("punctaj 3 → 5, răspuns corect"). */
    private record ItemSnapshot(String number, String statement, int points, String solution, List<String> options,
                                int correctOption, List<String> hints, String section, Long chapterId,
                                List<String> parts, Long enChapterId, List<String> mistakes, String numericAnswer) {

        String changesTo(ItemSnapshot after) {
            return new AuditText.Changes()
                    .addIfDifferent(statement, after.statement, "enunț")
                    .addIf(points != after.points, "punctaj " + points + " → " + after.points)
                    .addIfDifferent(solution, after.solution, "barem")
                    .addIf(options.size() != after.options.size(), "număr de variante")
                    .addIf(options.size() == after.options.size() && !options.equals(after.options), "texte variante")
                    .addIf(correctOption != after.correctOption, "răspuns corect")
                    .addIfDifferent(hints, after.hints, "indicii")
                    .addIfDifferent(section, after.section, "secțiune")
                    .addIfDifferent(chapterId, after.chapterId, "capitol")
                    .addIfDifferent(parts, after.parts, "cerințe")
                    .addIfDifferent(enChapterId, after.enChapterId, "capitol EN")
                    .addIfDifferent(mistakes, after.mistakes, "explicații greșeli")
                    .addIfDifferent(numericAnswer, after.numericAnswer, "răspuns corect")
                    .joined();
        }
    }

    private ItemSnapshot snapshot(QuizItem item) {
        return snapshot(item, optionRepository.findByItemIdOrderByPosition(item.getId()),
                hintRepository.findByItemIdOrderByPosition(item.getId()),
                partRepository.findByItemIdOrderByPosition(item.getId()));
    }

    private ItemSnapshot snapshot(QuizItem item, List<QuizOption> options, List<QuizItemHint> hints,
                                  List<QuizItemPart> parts) {
        List<QuizOption> ordered = options.stream()
                .sorted(java.util.Comparator.comparingInt(QuizOption::getPosition)).toList();
        int correct = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).isCorrect()) {
                correct = i;
            }
        }
        return new ItemSnapshot(QuestionNumbers.of(item, itemRepository.findByQuizIdOrderByPosition(item.getQuiz().getId())),
                item.getStatement(), item.getPoints(), item.getSolution(),
                ordered.stream().map(QuizOption::getText).toList(), correct,
                hints.stream().map(QuizItemHint::getText).toList(), item.getSection(),
                item.getChapter() == null ? null : item.getChapter().getId(),
                parts.stream().map(p -> p.label() + ") " + p.getPoints() + "p " + p.getStatement() + " | " + p.getSolution()
                        + " | " + (p.getEnChapter() == null ? "" : p.getEnChapter().getCode())).toList(),
                item.getEnChapter() == null ? null : item.getEnChapter().getId(),
                ordered.stream().map(o -> String.valueOf(o.getMistake())).toList(), item.getNumericAnswer());
    }

    /** The question an option belongs to: 404 for an option that does not exist. */
    public Long itemIdOfOption(Long optionId) {
        return option(optionId).getItem().getId();
    }

    private QuizOption option(Long optionId) {
        return optionRepository.findById(optionId)
                .orElseThrow(() -> new QuizNotFoundException("QuizOption", optionId));
    }

    /**
     * Says what mistake leads to a wrong option; blank takes it away. Only words for the student who chose it, so it may
     * change on a test already taken, and it reaches papers already handed in.
     */
    @Transactional
    public void explainMistake(Long optionId, String mistake) {
        QuizOption option = option(optionId);
        if (option.isCorrect()) {
            throw new InvalidQuizException("Varianta corectă nu are o greșeală de explicat.");
        }
        QuizItem item = option.getItem();
        String before = option.getMistake();
        option.explainMistake(mistake);
        optionRepository.save(option);
        cacheEvictor.evict(CacheNames.QUIZ_STATS, item.getQuiz().getId());
        if (!java.util.Objects.equals(before, option.getMistake()) && seenByStudents(item.getQuiz())) {
            List<QuizItem> items = itemRepository.findByQuizIdOrderByPosition(item.getQuiz().getId());
            String letter = QuizAttemptService.letterOf(optionRepository.findByItemIdOrderByPosition(item.getId()), option);
            recordQuestion(AuditAction.QUESTION_UPDATED, item, QuestionNumbers.of(item, items),
                    "explicație la varianta " + letter + ")");
        }
    }

    @Transactional
    public void deleteItem(Long itemId) {
        QuizItem item = getItem(itemId);
        Quiz quiz = item.getQuiz();
        requireNotTaken(quiz, "nu mai poți șterge întrebări");
        if (quiz.getStatus() == QuizStatus.PUBLISHED && itemRepository.countByQuizId(quiz.getId()) <= 1) {
            throw new InvalidQuizException("Aceasta e ultima întrebare a unui test publicat: apasă întâi „Depublică”, "
                    + "altfel elevii ar vedea un test gol.");
        }
        if (seenByStudents(quiz)) {
            recordQuestion(AuditAction.QUESTION_DELETED, item,
                    QuestionNumbers.of(item, itemRepository.findByQuizIdOrderByPosition(quiz.getId())),
                    AuditText.snippet(item.getStatement(), 80));
        }
        optionRepository.deleteByItemId(itemId);
        hintRepository.deleteByItemId(itemId);
        partRepository.deleteByItemId(itemId);
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
            throw new QuizInUseException("Testul „" + quiz.getTitle() + "” a fost deja dat de elevi ("
                    + RoCount.of(attempts, "încercare", "încercări") + "): " + refused
                    + ", s-ar schimba punctajul maxim și notele lor. Apasă „Copiază” pe rândul testului și modifică copia.");
        }
    }

    /** On a taken item: same points, same options in the same order with the same right one — only the texts change. */
    private List<QuizOption> correctTextsInPlace(QuizItem item, ItemRequest request) {
        String refusal = "Testul „" + item.getQuiz().getTitle() + "” a fost deja dat de elevi: la această întrebare poți "
                + "corecta doar textele (enunț, variante, rezolvare, indicii) — punctajul, numărul de variante și "
                + "varianta corectă nu se mai pot schimba aici. Răspunsul corect se schimbă din Statistici.";
        if (pointsOf(request) != item.getPoints()) {
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
            existing.get(i).explainMistake(requested.get(i).mistake());
            optionRepository.save(existing.get(i));
        }
        return existing;
    }

    /** An item's points: given, or - for a problem with requirements a), b)... - their sum. */
    private static int pointsOf(ItemRequest request) {
        return request.parts() == null || request.parts().isEmpty()
                ? request.points()
                : request.parts().stream().mapToInt(PartDtos.PartRequest::points).sum();
    }

    private static void requireNumber(ItemRequest request) {
        if (request.type() == QuizItemType.NUMERIC && NumericAnswer.parse(request.numericAnswer()).isEmpty()) {
            throw new InvalidQuizException("Scrie răspunsul corect ca număr: 12, 0,5, 1/2 sau 0,(3).");
        }
    }

    private static void requireValidParts(ItemRequest request) {
        List<PartDtos.PartRequest> parts = request.parts();
        if (parts == null || parts.isEmpty()) {
            return;
        }
        if (request.type() != QuizItemType.OPEN) {
            throw new InvalidQuizException("Doar o întrebare cu rezolvare poate avea cerințe a), b).");
        }
        if (parts.size() > QuizItemPart.MAX_PARTS) {
            throw new InvalidQuizException("O întrebare are cel mult " + QuizItemPart.MAX_PARTS + " cerințe.");
        }
        if (parts.stream().anyMatch(p -> p.statement() == null || p.statement().isBlank())) {
            throw new InvalidQuizException("Fiecare cerință are nevoie de un enunț.");
        }
    }

    /** Persists an open problem's requirements in order (a, b, c...). Null/empty = none. */
    private List<QuizItemPart> saveParts(QuizItem item, List<PartDtos.PartRequest> parts) {
        if (parts == null || parts.isEmpty()) {
            return List.of();
        }
        List<QuizItemPart> saved = new java.util.ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            PartDtos.PartRequest part = parts.get(i);
            QuizItemPart fresh = new QuizItemPart(item, i, part.statement(), part.points(), part.solution());
            fresh.pointToEnChapter(enChapter(part.enChapterId()));
            saved.add(partRepository.save(fresh));
        }
        return saved;
    }

    /** On a taken problem: the same requirements with the same points - only their texts and barem change. */
    private List<QuizItemPart> correctPartTextsInPlace(QuizItem item, ItemRequest request) {
        List<QuizItemPart> existing = partRepository.findByItemIdOrderByPosition(item.getId());
        List<PartDtos.PartRequest> requested = request.parts() == null ? List.of() : request.parts();
        boolean same = existing.size() == requested.size();
        for (int i = 0; same && i < existing.size(); i++) {
            same = existing.get(i).getPoints() == requested.get(i).points();
        }
        if (!same) {
            throw new QuizInUseException("Testul „" + item.getQuiz().getTitle() + "” a fost deja dat de elevi: la "
                    + "această întrebare poți corecta doar textele cerințelor și baremul — numărul lor și punctajele nu se "
                    + "mai pot schimba.");
        }
        for (int i = 0; i < existing.size(); i++) {
            existing.get(i).update(requested.get(i).statement(), requested.get(i).solution());
            existing.get(i).pointToEnChapter(enChapter(requested.get(i).enChapterId())); // where to revise: allowed
            partRepository.save(existing.get(i));
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
     * For a single-choice item, validates and persists its options (at least two, at most one correct: a grilă read
     * from photos may wait for its barem to say which - publishing needs it, see {@link #setPublished}). For an open
     * item, options are ignored and none are stored.
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
        if (correct > 1) {
            throw new InvalidQuizException("O grilă are un singur răspuns corect.");
        }
        return options.stream()
                .map(o -> {
                    QuizOption option = new QuizOption(item, o.position(), o.text(), o.correct());
                    option.explainMistake(o.mistake());
                    return optionRepository.save(option);
                })
                .toList();
    }
}
