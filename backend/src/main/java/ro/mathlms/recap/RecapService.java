package ro.mathlms.recap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ro.mathlms.assignment.Assignment;
import ro.mathlms.assignment.AssignmentService;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEvent;
import ro.mathlms.audit.AuditLog;
import ro.mathlms.content.RoCount;
import ro.mathlms.enchapter.EnChapter;
import ro.mathlms.enchapter.EnChapterRepository;
import ro.mathlms.quiz.EnChapterDetector;
import ro.mathlms.quiz.ItemRequest;
import ro.mathlms.quiz.PartDtos.PartRequest;
import ro.mathlms.quiz.QuestionImageService;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAdminService;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.recap.RecapDtos.CatchUpDto;
import ro.mathlms.recap.RecapDtos.CatchUpRequest;
import ro.mathlms.recap.RecapDtos.ChapterCountDto;
import ro.mathlms.recap.RecapDtos.CreateQuizRequest;
import ro.mathlms.recap.RecapDtos.PartEdit;
import ro.mathlms.recap.RecapDtos.RecapExerciseDto;
import ro.mathlms.recap.RecapDtos.UpdateRequest;
import ro.mathlms.storage.FileService;
import ro.mathlms.teaching.TeacherScope;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The teacher's side of „Teme recapitulative”: read and correct the imported exercises, approve them, put approved ones
 * in a draft test (then published and given as homework the usual way), or give catch-up homework on one EN chapter to
 * the students weak there (V59) in one step. Nothing that is not approved ever goes in a test.
 */
@Service
public class RecapService {

    /** Points of one requirement, and of an exercise without requirements, in a test made here (editable after). */
    static final int POINTS_PER_PART = 5;
    static final int POINTS_WHOLE = 10;
    static final String DESCRIPTION = "Din culegerea „Teme recapitulative”.";

    private final RecapExerciseRepository repository;
    private final EnChapterRepository enChapterRepository;
    private final QuizAdminService quizAdminService;
    private final QuizItemRepository itemRepository;
    private final QuestionImageService questionImages;
    private final AssignmentService assignmentService;
    private final FileService fileService;
    private final TeacherScope scope;
    private final AuditLog auditLog;
    private final Clock clock;
    private final String imagesDir;

    public RecapService(RecapExerciseRepository repository, EnChapterRepository enChapterRepository,
                        QuizAdminService quizAdminService, QuizItemRepository itemRepository,
                        QuestionImageService questionImages, AssignmentService assignmentService,
                        FileService fileService, TeacherScope scope, AuditLog auditLog, Clock clock,
                        @Value("${app.storage.question-images-dir}") String imagesDir) {
        this.repository = repository;
        this.enChapterRepository = enChapterRepository;
        this.quizAdminService = quizAdminService;
        this.itemRepository = itemRepository;
        this.questionImages = questionImages;
        this.assignmentService = assignmentService;
        this.fileService = fileService;
        this.scope = scope;
        this.auditLog = auditLog;
        this.clock = clock;
        this.imagesDir = imagesDir;
    }

    // --- reading ---

    /**
     * The collection in book order, filtered: review state, EN chapter, tema, "AI-checked only" and words of the
     * statement or the key ({@code q}). A few hundred rows - filtered here, not in SQL.
     */
    @Transactional(readOnly = true)
    public List<RecapExerciseDto> list(RecapReview review, Long enChapterId, Integer tema, Boolean verified, String q) {
        String words = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
        return repository.findAllByOrderByTemaAscNumberAscIdAsc().stream()
                .filter(e -> review == null || e.getReview() == review)
                .filter(e -> enChapterId == null || (e.getEnChapter() != null && e.getEnChapter().getId().equals(enChapterId)))
                .filter(e -> tema == null || e.getTema() == tema)
                .filter(e -> verified == null || "AGREE".equals(e.getAiStatus()) == verified)
                .filter(e -> words.isEmpty() || e.getSourceKey().toLowerCase(Locale.ROOT).contains(words)
                        || e.fullText().toLowerCase(Locale.ROOT).contains(words))
                .map(RecapExerciseDto::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public RecapExerciseDto get(Long id) {
        return RecapExerciseDto.of(find(id));
    }

    /** Approved exercises per EN chapter. */
    @Transactional(readOnly = true)
    public List<ChapterCountDto> approvedByChapter() {
        Map<EnChapter, Long> counts = repository.findAllByOrderByTemaAscNumberAscIdAsc().stream()
                .filter(e -> e.getReview() == RecapReview.APPROVED && !e.isFigureMissing() && e.getEnChapter() != null)
                .collect(Collectors.groupingBy(RecapExercise::getEnChapter, LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<EnChapter, Long> c) -> c.getKey().getPosition()))
                .map(c -> new ChapterCountDto(c.getKey().getId(), c.getKey().getCode(), c.getValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Resource image(Long id) {
        RecapExercise exercise = find(id);
        if (exercise.getImageKey() == null) {
            throw new RecapNotFoundException(id);
        }
        try {
            return fileService.loadImage(imagesDir, exercise.getImageKey());
        } catch (IOException e) {
            throw new RecapNotFoundException(id);
        }
    }

    // --- the teacher's review ---

    @Transactional
    public RecapExerciseDto update(Long id, UpdateRequest request) {
        RecapExercise exercise = find(id);
        List<RecapPart> old = exercise.getParts();
        List<PartEdit> edits = request.parts() == null ? List.of() : request.parts();
        List<RecapPart> parts = new ArrayList<>();
        for (int i = 0; i < edits.size(); i++) {
            PartEdit p = edits.get(i);
            parts.add(new RecapPart(p.statement(), p.solution(), p.answer(), checkedNumber(p.result()),
                    i < old.size() && old.get(i).isVerified()));
        }
        exercise.write(request.statement(), request.solution(), request.answer(), checkedNumber(request.result()), parts);
        if (request.enChapterId() != null) {
            exercise.pointToEnChapter(enChapterRepository.findById(request.enChapterId())
                    .orElseThrow(() -> new InvalidRecapException("Capitolul ales nu există.")));
        } else {
            String code = EnChapterDetector.detect(exercise.fullText());
            exercise.pointToEnChapter(code == null ? null : enChapterRepository.findByCode(code).orElse(null));
        }
        return RecapExerciseDto.of(repository.save(exercise));
    }

    @Transactional
    public RecapExerciseDto review(Long id, RecapReview review) {
        RecapExercise exercise = find(id);
        if (review == RecapReview.APPROVED && exercise.isFigureMissing()) {
            throw new RecapConflictException("Exercițiul are un desen în carte care lipsește aici: adaugă imaginea "
                    + "sau confirmă că se poate fără ea.");
        }
        exercise.review(review, scope.currentUserId(), clock.instant());
        return RecapExerciseDto.of(repository.save(exercise));
    }

    @Transactional
    public RecapExerciseDto waiveFigure(Long id) {
        RecapExercise exercise = find(id);
        exercise.waiveFigure();
        return RecapExerciseDto.of(repository.save(exercise));
    }

    @Transactional
    public RecapExerciseDto attachImage(Long id, MultipartFile file) {
        RecapExercise exercise = find(id);
        if (file == null || file.isEmpty() || file.getContentType() == null
                || !Set.of("image/png", "image/jpeg", "image/webp").contains(file.getContentType())) {
            throw new InvalidRecapException("Imaginea trebuie să fie PNG, JPG sau WEBP.");
        }
        if (file.getSize() > RecapImportService.MAX_IMAGE_BYTES) {
            throw new InvalidRecapException("Imaginea e prea mare (maxim 4 MB).");
        }
        String stored;
        try {
            stored = fileService.uploadImage(imagesDir, file);
        } catch (IOException e) {
            throw new UncheckedIOException("Nu am putut salva imaginea", e);
        }
        String old = exercise.showImage(stored);
        questionImages.deleteAfterCommit(old);
        return RecapExerciseDto.of(repository.save(exercise));
    }

    // --- into tests ---

    /** A DRAFT test of the chosen approved exercises, in the order chosen; the teacher publishes and assigns it. */
    @Transactional
    public Long createQuiz(CreateQuizRequest request) {
        if (request.schoolClassId() != null) {
            scope.requireClass(request.schoolClassId());
        }
        List<RecapExercise> chosen = approved(request.exerciseIds());
        Quiz quiz = build(request.title().strip(), request.schoolClassId(), chosen);
        return quiz.getId();
    }

    /**
     * Catch-up homework from the EN mastery map: {@code count} approved exercises of the chapter - those not yet in a
     * test of this class first - become a test for this class, published, and given to the chosen students only.
     * One transaction: a refused homework (a student not in the class, a past deadline) leaves no test behind.
     */
    @Transactional
    public CatchUpDto catchUp(CatchUpRequest request) {
        scope.requireClass(request.schoolClassId());
        EnChapter chapter = enChapterRepository.findById(request.enChapterId())
                .orElseThrow(() -> new InvalidRecapException("Capitolul ales nu există."));
        Set<Long> classQuizzes = repository.findQuizIdsOfClass(request.schoolClassId());
        List<RecapExercise> pool = repository.findByEnChapterIdAndReviewOrderByTemaAscNumberAscIdAsc(
                        chapter.getId(), RecapReview.APPROVED).stream()
                .filter(e -> !e.isFigureMissing()) // a drawing still missing: not for a student yet
                .toList();
        if (pool.isEmpty()) {
            throw new RecapConflictException("Niciun exercițiu aprobat (cu desenul lui, dacă are) din culegere la „"
                    + chapter.getTitle() + "”. Aprobă câteva în pagina Culegere.");
        }
        List<RecapExercise> chosen = pool.stream()
                .sorted(Comparator.comparing((RecapExercise e) -> e.getQuizIds().stream().anyMatch(classQuizzes::contains)))
                .limit(request.count())
                .toList();
        String title = request.title() == null || request.title().isBlank()
                ? "Recuperare: " + chapter.getTitle() : request.title().strip();
        Quiz quiz = build(title.length() > 200 ? title.substring(0, 200) : title, request.schoolClassId(), chosen);
        quizAdminService.setPublished(quiz.getId(), true);
        Assignment homework = assignmentService.createForStudents(quiz.getId(), request.schoolClassId(),
                request.studentIds(), request.dueAt());
        return new CatchUpDto(quiz.getId(), homework.getId(), chosen.size());
    }

    private Quiz build(String title, Long schoolClassId, List<RecapExercise> chosen) {
        Quiz quiz = quizAdminService.createQuiz(title, DESCRIPTION, schoolClassId, null, false, null,
                Quiz.DEFAULT_ATTEMPTS);
        List<ItemRequest> items = new ArrayList<>();
        for (int i = 0; i < chosen.size(); i++) {
            items.add(item(chosen.get(i), i));
        }
        quizAdminService.addItems(quiz.getId(), items);
        List<QuizItem> created = itemRepository.findByQuizIdOrderByPosition(quiz.getId());
        for (int i = 0; i < chosen.size(); i++) {
            RecapExercise exercise = chosen.get(i);
            String copy = questionImages.copyOf(exercise.getImageKey());
            if (copy != null) {
                QuizItem item = created.get(i);
                item.attachImage(copy);
                itemRepository.save(item);
            }
            exercise.usedIn(quiz.getId());
            repository.save(exercise);
        }
        auditLog.record(AuditEvent.of(AuditAction.QUIZ_FROM_RECAP, quiz.getId(), quiz.getTitle())
                .details(RoCount.of(chosen.size(), "exercițiu", "exerciții") + " din culegere: "
                        + chosen.stream().map(RecapExercise::getSourceKey).collect(Collectors.joining(", "))));
        return quiz;
    }

    /** An open problem: the statement, the requirements with their barem and answer, the typed result when checked. */
    private static ItemRequest item(RecapExercise e, int position) {
        Long chapter = e.getEnChapter() == null ? null : e.getEnChapter().getId();
        List<PartRequest> parts = e.getParts().stream()
                .map(p -> new PartRequest(p.getStatement(), POINTS_PER_PART, barem(p.getSolution(), p.getAnswer()),
                        null, p.getResult()))
                .toList();
        String solution = parts.isEmpty() ? barem(e.getSolution(), e.getAnswer()) : e.getSolution();
        return new ItemRequest(QuizItemType.OPEN, position, e.getStatement(), parts.isEmpty() ? POINTS_WHOLE : 0,
                solution, null, null, null, null, null, parts.isEmpty() ? null : parts, chapter);
    }

    private static String barem(String steps, String answer) {
        String text = steps == null ? "" : steps;
        if (answer != null) {
            text = text.isEmpty() ? "Răspuns: " + answer : text + "\nRăspuns: " + answer;
        }
        return text.isEmpty() ? null : text;
    }

    private List<RecapExercise> approved(List<Long> ids) {
        Map<Long, RecapExercise> found = repository.findAllById(ids).stream()
                .collect(Collectors.toMap(RecapExercise::getId, Function.identity()));
        List<RecapExercise> out = new ArrayList<>();
        for (Long id : ids.stream().distinct().toList()) {
            RecapExercise exercise = found.get(id);
            if (exercise == null) {
                throw new RecapNotFoundException(id);
            }
            if (exercise.getReview() != RecapReview.APPROVED) {
                throw new RecapConflictException("Exercițiul " + exercise.getSourceKey()
                        + " nu este aprobat: doar exercițiile aprobate intră într-un test.");
            }
            if (exercise.isFigureMissing()) {
                throw new RecapConflictException("Exercițiului " + exercise.getSourceKey() + " îi lipsește desenul din "
                        + "carte: adaugă imaginea sau confirmă că merge fără ea, apoi fă testul.");
            }
            out.add(exercise);
        }
        return out;
    }

    private static String checkedNumber(String result) {
        if (result == null || result.isBlank()) {
            return null;
        }
        String number = RecapImportService.number(result);
        if (number == null) {
            throw new InvalidRecapException("Rezultatul „" + result + "” nu este un număr (ex.: 12, -3/4, 0,5).");
        }
        return number;
    }

    private RecapExercise find(Long id) {
        return repository.findById(id).orElseThrow(() -> new RecapNotFoundException(id));
    }
}
