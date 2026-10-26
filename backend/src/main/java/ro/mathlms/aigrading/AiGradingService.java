package ro.mathlms.aigrading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import ro.mathlms.aigrading.AnswerGrader.Proposal;
import ro.mathlms.aigrading.AnswerGrader.Task;
import ro.mathlms.aigrading.AnswerGrader.TaskPart;
import ro.mathlms.ingest.ExtractionException;
import ro.mathlms.ingest.PageExtractor.PageImage;
import ro.mathlms.quiz.InvalidQuizException;
import ro.mathlms.quiz.ItemResponse;
import ro.mathlms.quiz.ItemResponseRepository;
import ro.mathlms.quiz.QuizAttempt;
import ro.mathlms.quiz.QuizAttemptRepository;
import ro.mathlms.quiz.QuizAttemptStatus;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemPart;
import ro.mathlms.quiz.QuizItemPartRepository;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.quiz.QuizNotFoundException;
import ro.mathlms.quiz.SubmissionListener;
import ro.mathlms.storage.FileService;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The AI's grade proposals for Subiectul III (user's decisions 2026-10-26: Gemini's strongest model; the AI proposes,
 * the teacher confirms). Every open answer with photos and a barem is queued when the paper is handed in, in the same
 * transaction; a worker asks the AI one answer at a time with no transaction open (claim, grade, save), within a daily
 * limit for the whole app. The proposal is shown next to the photo on the grading screen - it never becomes a grade
 * by itself. Only the photos, the statement, the figure and the barem are sent: no name, no account.
 */
@Service
public class AiGradingService implements SubmissionListener {

    private static final Logger log = LoggerFactory.getLogger(AiGradingService.class);

    static final ZoneId ZONE = ZoneId.of("Europe/Bucharest");
    /** A call that has run this long is not running any more (the app restarted mid-call). */
    static final Duration STUCK_AFTER = Duration.ofMinutes(10);
    private static final TypeReference<List<AiPartProposalDto>> PARTS = new TypeReference<>() { };

    static final String NO_PROVIDER = "Propunerile AI nu sunt configurate pe server.";
    static final String UNREADABLE = "AI-ul nu a putut propune un punctaj. Cere din nou peste câteva minute.";
    static final String INTERRUPTED = "Propunerea a fost întreruptă (serverul a repornit). Cere din nou.";
    static final String NO_PHOTOS = "Elevul nu a trimis nicio poză la această întrebare: nu e nimic de propus.";
    static final String NO_BAREM = "Întrebarea nu are barem. Adaugă-l (sau „Barem din poze”) ca AI-ul să poată propune punctajul.";

    /** One requirement of the claimed answer: its id (to store the proposal against) and what the AI is told. */
    private record ClaimedPart(Long id, TaskPart task) {
    }

    /** Everything the call needs, read in the claim's transaction; the files are loaded after it. */
    private record Claimed(long id, String statement, int points, String barem, List<ClaimedPart> parts,
                           String figureKey, List<String> photoKeys) {
    }

    private final AiGradeProposalRepository repository;
    private final QuizAttemptRepository attemptRepository;
    private final QuizItemRepository itemRepository;
    private final QuizItemPartRepository partRepository;
    private final ItemResponseRepository responseRepository;
    private final FileService fileService;
    private final List<AnswerGrader> graders;
    private final AiGradingProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final String photosDir;
    private final String questionImagesDir;

    public AiGradingService(AiGradeProposalRepository repository, QuizAttemptRepository attemptRepository,
                            QuizItemRepository itemRepository, QuizItemPartRepository partRepository,
                            ItemResponseRepository responseRepository, FileService fileService,
                            List<AnswerGrader> graders, AiGradingProperties properties, ObjectMapper objectMapper,
                            Clock clock, PlatformTransactionManager transactionManager,
                            @Value("${app.storage.quiz-photos-dir}") String photosDir,
                            @Value("${app.storage.question-images-dir}") String questionImagesDir) {
        this.repository = repository;
        this.attemptRepository = attemptRepository;
        this.itemRepository = itemRepository;
        this.partRepository = partRepository;
        this.responseRepository = responseRepository;
        this.fileService = fileService;
        this.graders = graders;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.photosDir = photosDir;
        this.questionImagesDir = questionImagesDir;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // --- the hand-in ---

    /** Queues a proposal for every open answer with photos and a barem (inside the hand-in transaction). */
    @Override
    public void submitted(QuizAttempt attempt, List<QuizItem> items, Map<Long, ItemResponse> responsesByItem) {
        if (!properties.enabled()) {
            return;
        }
        for (QuizItem item : items) {
            ItemResponse response = responsesByItem.get(item.getId());
            if (item.getType() == QuizItemType.OPEN && response != null && response.hasPhotos() && hasBarem(item)
                    && repository.findByAttemptIdAndItemId(attempt.getId(), item.getId()).isEmpty()) {
                repository.save(new AiGradeProposal(attempt.getId(), item.getId(), clock.instant()));
            }
        }
    }

    private boolean hasBarem(QuizItem item) {
        return notBlank(item.getSolution())
                || partRepository.findByItemIdOrderByPosition(item.getId()).stream().anyMatch(p -> notBlank(p.getSolution()));
    }

    // --- the teacher ---

    /** The proposals of one paper (none while the feature is off: the grading screen just shows nothing). */
    @Transactional(readOnly = true)
    public List<AiProposalDto> proposals(Long attemptId) {
        if (!properties.enabled()) {
            return List.of();
        }
        return repository.findByAttemptIdOrderByItemId(attemptId).stream().map(this::dto).toList();
    }

    /**
     * "Cere propunere AI" / "Cere din nou": queues one answer (again). For a paper handed in before the feature was on,
     * a barem added later, or a proposal that failed. Only while the paper waits for grading.
     */
    @Transactional
    public AiProposalDto request(Long attemptId, Long itemId) {
        requireEnabled();
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new QuizNotFoundException("QuizAttempt", attemptId));
        if (attempt.getStatus() != QuizAttemptStatus.SUBMITTED) {
            throw new InvalidQuizException("Lucrarea nu mai așteaptă corectura.");
        }
        QuizItem item = itemRepository.findById(itemId)
                .filter(i -> i.getQuiz().getId().equals(attempt.getQuiz().getId()) && i.getType() == QuizItemType.OPEN)
                .orElseThrow(() -> new QuizNotFoundException("QuizItem", itemId));
        ItemResponse response = responseRepository.findByAttemptIdAndItemId(attemptId, itemId).orElse(null);
        if (response == null || !response.hasPhotos()) {
            throw new InvalidQuizException(NO_PHOTOS);
        }
        if (!hasBarem(item)) {
            throw new InvalidQuizException(NO_BAREM);
        }
        AiGradeProposal proposal = repository.findByAttemptIdAndItemId(attemptId, itemId)
                .orElseGet(() -> new AiGradeProposal(attemptId, itemId, clock.instant()));
        proposal.requeue();
        return dto(repository.save(proposal));
    }

    // --- worker ---

    /**
     * Proposes for the oldest waiting answer, if any and if today's limit allows; {@code false} when there is nothing to
     * do now. No transaction is open around the AI call.
     */
    public boolean processNext() {
        if (repository.countByStartedAtGreaterThanEqual(startOfToday()) >= properties.dailyAnswerLimit()) {
            return false; // the rest waits for tomorrow, still QUEUED
        }
        Optional<Claimed> claimed = tx.execute(status -> repository.claimNext().map(p -> {
            p.start(clock.instant());
            repository.save(p);
            return claim(p);
        }));
        if (claimed == null || claimed.isEmpty()) {
            return false;
        }
        long id = claimed.get().id();
        Outcome outcome = grade(id, claimed.get());
        tx.executeWithoutResult(status -> repository.findById(id).ifPresent(p -> {
            if (p.getStatus() != AiProposalStatus.RUNNING) {
                return; // recovered as stuck, or asked again, meanwhile: not ours to finish
            }
            if (outcome.error() != null) {
                p.fail(outcome.error(), clock.instant());
            } else {
                Proposal result = outcome.proposal();
                List<AiPartProposalDto> parts = new ArrayList<>();
                for (ClaimedPart part : claimed.get().parts()) {
                    result.parts().stream().filter(pp -> pp.label().equals(part.task().label())).findFirst()
                            .ifPresent(pp -> parts.add(new AiPartProposalDto(part.id(), pp.label(), pp.points(), pp.reason())));
                }
                p.succeed(result.points(), parts.isEmpty() ? null : write(parts), result.reason(), result.confidence(),
                        result.model(), result.inputTokens(), result.outputTokens(), clock.instant());
            }
            repository.save(p);
        }));
        return true;
    }

    /** What the AI is told about this answer, read while the row is claimed. */
    private Claimed claim(AiGradeProposal p) {
        QuizItem item = itemRepository.findById(p.getItemId()).orElseThrow();
        List<ClaimedPart> parts = partRepository.findByItemIdOrderByPosition(item.getId()).stream()
                .map((QuizItemPart part) -> new ClaimedPart(part.getId(),
                        new TaskPart(part.label(), part.getStatement(), part.getPoints(), part.getSolution())))
                .toList();
        List<String> photos = responseRepository.findByAttemptIdAndItemId(p.getAttemptId(), p.getItemId())
                .map(ItemResponse::photoKeys).orElse(List.of());
        return new Claimed(p.getId(), item.getStatement(), item.getPoints(), item.getSolution(), parts,
                item.getImageKey(), photos);
    }

    private record Outcome(Proposal proposal, String error) {
    }

    /** Runs with no transaction open; every failure becomes a reason the teacher can read. */
    private Outcome grade(long id, Claimed claimed) {
        Optional<AnswerGrader> grader = graders.stream().filter(g -> g.provider().equals(properties.provider())).findFirst();
        if (grader.isEmpty()) {
            log.error("AI proposal {}: no AnswerGrader named '{}'", id, properties.provider());
            return new Outcome(null, NO_PROVIDER);
        }
        try {
            if (claimed.photoKeys().isEmpty()) {
                return new Outcome(null, NO_PHOTOS);
            }
            List<PageImage> photos = new ArrayList<>();
            for (String key : claimed.photoKeys()) {
                photos.add(image(photosDir, key));
            }
            PageImage figure = claimed.figureKey() == null ? null : image(questionImagesDir, claimed.figureKey());
            Task task = new Task(claimed.statement(), claimed.points(), claimed.barem(),
                    claimed.parts().stream().map(ClaimedPart::task).toList(), figure, photos);
            return new Outcome(grader.get().grade(task), null);
        } catch (ExtractionException e) {
            log.warn("AI proposal {} failed: {}", id, e.getMessage(), e);
            return new Outcome(null, e.getMessage());
        } catch (Exception e) {
            log.error("AI proposal {} failed", id, e);
            return new Outcome(null, UNREADABLE);
        }
    }

    private PageImage image(String dir, String key) throws java.io.IOException {
        String type = MediaTypeFactory.getMediaType(key).map(MediaType::toString).orElse("image/jpeg");
        return new PageImage(fileService.loadImage(dir, key).getContentAsByteArray(), type);
    }

    /** Calls that never finished (the app stopped mid-call) fail, so the teacher can ask again. */
    @Transactional
    public int recoverStuck() {
        List<AiGradeProposal> stuck = repository.findByStatusAndStartedAtBefore(AiProposalStatus.RUNNING,
                clock.instant().minus(STUCK_AFTER));
        stuck.forEach(p -> p.fail(INTERRUPTED, clock.instant()));
        repository.saveAll(stuck);
        return stuck.size();
    }

    // --- helpers ---

    private AiProposalDto dto(AiGradeProposal p) {
        List<AiPartProposalDto> parts = p.getPartsJson() == null ? List.of() : parse(p.getPartsJson());
        return new AiProposalDto(p.getItemId(), p.getStatus(), p.getPoints(), parts, p.getReason(), p.getConfidence(),
                p.getError());
    }

    /** Switched off, the feature does not exist: 404 like any unknown address. */
    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private Instant startOfToday() {
        return LocalDate.now(clock.withZone(ZONE)).atStartOfDay(ZONE).toInstant();
    }

    private static boolean notBlank(String text) {
        return text != null && !text.isBlank();
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot write AI proposal JSON", e);
        }
    }

    private List<AiPartProposalDto> parse(String json) {
        try {
            return objectMapper.readValue(json, PARTS);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot read AI proposal JSON", e);
        }
    }
}
