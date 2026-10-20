package ro.mathlms.ingest;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEvent;
import ro.mathlms.audit.AuditLog;
import ro.mathlms.content.ChapterRepository;
import ro.mathlms.content.RoCount;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.ingest.AiImportDtos.AiImportDto;
import ro.mathlms.ingest.AiImportDtos.AiImportSettingsDto;
import ro.mathlms.ingest.AiImportDtos.CreateQuizFromImportRequest;
import ro.mathlms.ingest.PageExtractor.ExtractionResult;
import ro.mathlms.ingest.PageExtractor.PageImage;
import ro.mathlms.quiz.InvalidQuizException;
import ro.mathlms.quiz.ItemRequest;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAdminService;
import ro.mathlms.quiz.QuizNotFoundException;
import ro.mathlms.storage.FileService;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Faza 3: a quiz from photographed textbook pages. The teacher uploads 1-5 pages (stored, QUEUED); the worker reads
 * them with the configured {@link PageExtractor} (DONE with questions, or FAILED with a reason); the teacher reviews
 * the questions, marks the right options and makes a DRAFT quiz, after which the page files are deleted.
 *
 * <p>Reading takes tens of seconds and calls an outside service, so it never runs inside a transaction (the lesson of
 * the e-commerce Stripe audit): a short transaction claims the row, the call runs with none open, a new one saves the
 * result. Only textbook pages are sent (decision 2026-10-12) - never student work or personal data.
 */
@Service
public class AiImportService {

    private static final Logger log = LoggerFactory.getLogger(AiImportService.class);

    static final ZoneId ZONE = ZoneId.of("Europe/Bucharest");
    /** The browser shrinks pages before upload (a readable page is well under 1 MB); this bounds a direct call. */
    static final long MAX_PAGE_BYTES = 4L * 1024 * 1024;
    static final int MAX_ATTEMPTS = 3;
    /** A read that has run this long is not running any more (the app restarted mid-read). */
    static final Duration STUCK_AFTER = Duration.ofMinutes(10);
    private static final Set<String> TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private static final TypeReference<List<StoredPage>> PAGES = new TypeReference<>() { };
    private static final TypeReference<List<ExtractedItem>> ITEMS = new TypeReference<>() { };

    static final String INTERRUPTED = "Citirea a fost întreruptă (serverul a repornit). Încearcă din nou.";
    static final String UNREADABLE = "Nu am putut citi paginile. Încearcă din nou peste câteva minute.";
    static final String NO_PROVIDER = "Citirea din poze nu e configurată pe server.";
    static final String NOTHING_FOUND = "Nu am găsit niciun exercițiu în poze. Fotografiază pagina mai de aproape, drept și cu lumină bună.";

    /** A stored page file and the type it was uploaded as (sent to the provider as is). */
    record StoredPage(String key, String type) {
    }

    private final AiImportRepository repository;
    private final UserRepository userRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ChapterRepository chapterRepository;
    private final QuizAdminService quizAdminService;
    private final FileService fileService;
    private final AuditLog auditLog;
    private final List<PageExtractor> extractors;
    private final AiImportProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final ChapterSuggestionService suggestions;

    public AiImportService(AiImportRepository repository, UserRepository userRepository,
                           SchoolClassRepository schoolClassRepository, ChapterRepository chapterRepository,
                           QuizAdminService quizAdminService, FileService fileService, AuditLog auditLog,
                           List<PageExtractor> extractors, AiImportProperties properties, ObjectMapper objectMapper,
                           Clock clock, PlatformTransactionManager transactionManager,
                           ChapterSuggestionService suggestions) {
        this.suggestions = suggestions;
        this.repository = repository;
        this.userRepository = userRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.chapterRepository = chapterRepository;
        this.quizAdminService = quizAdminService;
        this.fileService = fileService;
        this.auditLog = auditLog;
        this.extractors = extractors;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // --- teacher ---

    @Transactional(readOnly = true)
    public AiImportSettingsDto settings(String email) {
        if (!properties.enabled()) {
            return new AiImportSettingsDto(false, 0, 0, 0);
        }
        return new AiImportSettingsDto(true, properties.maxPages(), properties.dailyPageLimit(),
                repository.pagesSince(teacher(email).getId(), startOfToday()));
    }

    /** Stores the pages and queues them for reading; the worker picks them up within seconds. */
    @Transactional
    public AiImportDto create(String email, List<MultipartFile> files, Long schoolClassId, Long chapterId) {
        requireEnabled();
        User teacher = teacher(email);
        List<MultipartFile> pages = files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (pages.isEmpty()) {
            throw new InvalidQuizException("Alege cel puțin o poză cu o pagină din manual.");
        }
        if (pages.size() > properties.maxPages()) {
            throw new InvalidQuizException("Poți trimite cel mult " + properties.maxPages() + " pagini odată.");
        }
        for (MultipartFile page : pages) {
            if (page.getContentType() == null || !TYPES.contains(page.getContentType())) {
                throw new InvalidQuizException("Pozele trebuie să fie JPG, PNG sau WEBP.");
            }
            if (page.getSize() > MAX_PAGE_BYTES) {
                throw new InvalidQuizException("O poză e prea mare (maxim 4 MB).");
            }
        }
        long used = repository.pagesSince(teacher.getId(), startOfToday());
        if (used + pages.size() > properties.dailyPageLimit()) {
            long left = Math.max(0, properties.dailyPageLimit() - used);
            String limit = RoCount.of(properties.dailyPageLimit(), "pagină", "pagini");
            throw new InvalidQuizException(left == 0
                    ? "Ai atins limita de azi (" + limit + "). Mâine poți trimite din nou."
                    : "Azi mai poți trimite doar " + RoCount.of(left, "pagină", "pagini") + " (limita e " + limit + " pe zi).");
        }
        if (schoolClassId != null && !schoolClassRepository.existsById(schoolClassId)) {
            throw new InvalidQuizException("Clasa aleasă nu mai există.");
        }
        if (chapterId != null && !chapterRepository.existsById(chapterId)) {
            throw new InvalidQuizException("Capitolul ales nu mai există.");
        }
        List<StoredPage> stored = new ArrayList<>();
        for (MultipartFile page : pages) {
            try {
                String key = fileService.uploadImage(properties.pagesDir(), page);
                stored.add(new StoredPage(key, page.getContentType()));
                whenRolledBack(key);
            } catch (IOException e) {
                throw new UncheckedIOException("Nu am putut salva poza", e);
            }
        }
        AiImport saved = repository.save(new AiImport(teacher.getId(), schoolClassId, chapterId, stored.size(),
                write(stored), clock.instant()));
        return dto(saved, false);
    }

    @Transactional(readOnly = true)
    public List<AiImportDto> list(String email) {
        requireEnabled();
        return repository.findTop20ByTeacherIdOrderByIdDesc(teacher(email).getId()).stream()
                .map(i -> dto(i, false)).toList();
    }

    @Transactional(readOnly = true)
    public AiImportDto get(String email, Long id) {
        requireEnabled();
        return dto(own(email, id), true);
    }

    /** Page {@code number} (1-based), for the review screen. Only the teacher who uploaded it. */
    @Transactional(readOnly = true)
    public Resource page(String email, Long id, int number) {
        requireEnabled();
        List<StoredPage> pages = pages(own(email, id));
        if (number < 1 || number > pages.size()) {
            throw new QuizNotFoundException("AiImport page", (long) number);
        }
        try {
            return fileService.loadImage(properties.pagesDir(), pages.get(number - 1).key());
        } catch (IOException e) {
            throw new QuizNotFoundException("AiImport page", (long) number);
        }
    }

    /** "Încearcă din nou" on a failed import whose pages are still kept. */
    @Transactional
    public AiImportDto retry(String email, Long id) {
        requireEnabled();
        AiImport ai = own(email, id);
        if (ai.getStatus() != AiImportStatus.FAILED) {
            throw new AiImportConflictException("Doar o citire eșuată poate fi reluată.");
        }
        if (!ai.hasPages()) {
            throw new AiImportConflictException("Pozele nu mai sunt păstrate. Încarcă paginile din nou.");
        }
        if (ai.getAttempts() >= MAX_ATTEMPTS) {
            throw new AiImportConflictException("Am încercat deja de " + MAX_ATTEMPTS
                    + " ori. Fă poze mai clare și încarcă-le din nou.");
        }
        ai.requeue();
        return dto(repository.save(ai), false);
    }

    /** Throws the import away, page files included. A read already under way finishes into nothing. */
    @Transactional
    public void discard(String email, Long id) {
        requireEnabled();
        AiImport ai = own(email, id);
        List<StoredPage> pages = pages(ai);
        repository.delete(ai);
        deleteAfterCommit(pages);
    }

    /**
     * The reviewed questions become a DRAFT quiz, all or nothing (the quiz builder's own rules apply: a grilă needs
     * exactly one correct option). The page files are then deleted - the quiz is what is kept.
     */
    @Transactional
    public Long createQuiz(String email, Long id, CreateQuizFromImportRequest request) {
        requireEnabled();
        AiImport ai = own(email, id);
        if (ai.getQuizId() != null) {
            throw new AiImportConflictException("Testul din aceste poze a fost deja creat.");
        }
        if (ai.getStatus() != AiImportStatus.DONE) {
            throw new AiImportConflictException("Pozele nu au fost citite încă.");
        }
        Quiz quiz = quizAdminService.createQuiz(request.title(), null, request.schoolClassId(), null, false,
                request.chapterId());
        List<ItemRequest> items = new ArrayList<>();
        int position = 0;
        for (ItemRequest item : request.items()) {
            items.add(new ItemRequest(item.type(), position++, item.statement(),
                    item.points(), item.solution(), item.options(), item.hints(), item.chapterId(), item.section(),
                    item.imageRequired(), item.parts(), item.enChapterId()));
        }
        quizAdminService.addItems(quiz.getId(), items);
        List<StoredPage> pages = pages(ai);
        ai.linkQuiz(quiz.getId());
        ai.forgetPages();
        repository.save(ai);
        deleteAfterCommit(pages);
        auditLog.record(AuditEvent.of(AuditAction.QUIZ_FROM_PHOTOS, quiz.getId(), quiz.getTitle())
                .details(RoCount.of(request.items().size(), "întrebare", "întrebări") + " din "
                        + RoCount.of(ai.getPageCount(), "pagină", "pagini")));
        return quiz.getId();
    }

    // --- worker ---

    /**
     * Reads the oldest waiting import, if any; {@code false} when the queue is empty. Three steps, no transaction
     * around the provider call: claim (QUEUED -> RUNNING), read, save (DONE | FAILED).
     */
    public boolean processNext() {
        Optional<Claimed> claimed = tx.execute(status -> repository.claimNext().map(ai -> {
            ai.start(clock.instant());
            repository.save(ai);
            return new Claimed(ai.getId(), pages(ai));
        }));
        if (claimed == null || claimed.isEmpty()) {
            return false;
        }
        long id = claimed.get().id();
        Outcome outcome = read(id, claimed.get().pages());
        tx.executeWithoutResult(status -> repository.findById(id).ifPresent(ai -> {
            if (ai.getStatus() != AiImportStatus.RUNNING) {
                return; // discarded and re-made meanwhile, or recovered as stuck: not ours to finish
            }
            if (outcome.error() != null) {
                ai.fail(outcome.error(), clock.instant());
            } else {
                ExtractionResult result = outcome.result();
                ai.succeed(write(outcome.items()), provider().map(PageExtractor::provider).orElse(null),
                        result.model(), result.inputTokens(), result.outputTokens(), clock.instant());
            }
            repository.save(ai);
        }));
        return true;
    }

    private record Claimed(long id, List<StoredPage> pages) {
    }

    private record Outcome(ExtractionResult result, List<ExtractedItem> items, String error) {
        static Outcome failed(String error) {
            return new Outcome(null, null, error);
        }
    }

    /** Runs with no transaction open; every failure becomes a reason the teacher can read. */
    private Outcome read(long id, List<StoredPage> stored) {
        Optional<PageExtractor> extractor = provider();
        if (extractor.isEmpty()) {
            log.error("AI import {}: no PageExtractor named '{}'", id, properties.provider());
            return Outcome.failed(NO_PROVIDER);
        }
        try {
            List<PageImage> pages = new ArrayList<>();
            for (StoredPage page : stored) {
                pages.add(new PageImage(fileService.loadImage(properties.pagesDir(), page.key()).getContentAsByteArray(),
                        page.type()));
            }
            ExtractionResult result = extractor.get().extract(pages);
            List<ExtractedItem> items = ExtractedItems.normalize(result.items(), pages.size());
            if (items.isEmpty()) {
                return Outcome.failed(NOTHING_FOUND);
            }
            items = suggestions.annotate(items); // the EN chapter of each question, for the review (best effort)
            return new Outcome(result, items, null);
        } catch (ExtractionException e) {
            log.warn("AI import {} failed: {}", id, e.getMessage(), e);
            return Outcome.failed(e.getMessage());
        } catch (Exception e) {
            log.error("AI import {} failed", id, e);
            return Outcome.failed(UNREADABLE);
        }
    }

    /** Reads that never finished (the app stopped mid-read) fail, so the teacher can try again. */
    @Transactional
    public int recoverStuck() {
        List<AiImport> stuck = repository.findByStatusAndStartedAtBefore(AiImportStatus.RUNNING,
                clock.instant().minus(STUCK_AFTER));
        stuck.forEach(ai -> ai.fail(INTERRUPTED, clock.instant()));
        repository.saveAll(stuck);
        return stuck.size();
    }

    /** No textbook photo is kept longer than {@code keepPagesDays}: the questions stay, the files go. */
    @Transactional
    public int forgetExpiredPages() {
        List<AiImport> expired = repository.withPagesCreatedBefore(
                clock.instant().minus(Duration.ofDays(properties.keepPagesDays())));
        for (AiImport ai : expired) {
            deleteAfterCommit(pages(ai));
            ai.forgetPages();
        }
        repository.saveAll(expired);
        return expired.size();
    }

    // --- helpers ---

    private Optional<PageExtractor> provider() {
        return extractors.stream().filter(e -> e.provider().equals(properties.provider())).findFirst();
    }

    /** Switched off, the feature does not exist: 404 like any unknown address. */
    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private User teacher(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new QuizNotFoundException("User", null));
    }

    /** Another teacher's import gets the same 404 as one that does not exist. */
    private AiImport own(String email, Long id) {
        return repository.findByIdAndTeacherId(id, teacher(email).getId())
                .orElseThrow(() -> new QuizNotFoundException("AiImport", id));
    }

    private Instant startOfToday() {
        return LocalDate.now(clock.withZone(ZONE)).atStartOfDay(ZONE).toInstant();
    }

    private AiImportDto dto(AiImport ai, boolean withItems) {
        List<ExtractedItem> items = ai.getItemsJson() == null ? null : parse(ai.getItemsJson(), ITEMS);
        return new AiImportDto(ai.getId(), ai.getStatus(), ai.getPageCount(), ai.hasPages(),
                items == null ? null : items.size(), ai.getError(), ai.getSchoolClassId(), ai.getChapterId(),
                ai.getQuizId(), ai.getCreatedAt(), ai.getFinishedAt(), withItems ? items : null);
    }

    private List<StoredPage> pages(AiImport ai) {
        return parse(ai.getPageKeys(), PAGES);
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot write AI import JSON", e);
        }
    }

    private <T> T parse(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot read AI import JSON", e);
        }
    }

    private void deleteAfterCommit(List<StoredPage> pages) {
        if (pages.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            pages.forEach(p -> delete(p.key()));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                pages.forEach(p -> delete(p.key()));
            }
        });
    }

    /** A page stored for an upload that then failed is referenced by nothing: remove it again. */
    private void whenRolledBack(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    delete(key);
                }
            }
        });
    }

    private void delete(String key) {
        try {
            fileService.deleteImage(properties.pagesDir(), key);
        } catch (IOException e) {
            log.warn("Could not delete AI import page {}", key, e);
        }
    }
}
