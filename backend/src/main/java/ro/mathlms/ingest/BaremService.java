package ro.mathlms.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import ro.mathlms.aigrading.AiGradingProperties;
import ro.mathlms.content.RoCount;
import ro.mathlms.ingest.AiImportService.StoredPage;
import ro.mathlms.ingest.BaremDtos.AiGradingSettingsDto;
import ro.mathlms.ingest.BaremDtos.BaremProposalDto;
import ro.mathlms.ingest.BaremDtos.BaremReadDto;
import ro.mathlms.ingest.BaremDtos.ItemProposal;
import ro.mathlms.ingest.BaremDtos.PartProposal;
import ro.mathlms.ingest.BaremReader.Entry;
import ro.mathlms.ingest.BaremReader.OutlineItem;
import ro.mathlms.ingest.BaremReader.OutlinePart;
import ro.mathlms.ingest.BaremReader.PartBarem;
import ro.mathlms.ingest.PageExtractor.PageImage;
import ro.mathlms.quiz.InvalidQuizException;
import ro.mathlms.quiz.QuestionNumbers;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemPart;
import ro.mathlms.quiz.QuizItemPartRepository;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.quiz.QuizNotFoundException;
import ro.mathlms.quiz.QuizOption;
import ro.mathlms.quiz.QuizOptionRepository;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.storage.FileService;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * "Barem din poze": the teacher photographs the official barem of a test, the AI reads it in the background and maps it
 * onto the test's questions, the teacher reviews and applies it ({@code QuizAdminService#applyBarem}). Like the AI
 * import, no transaction is open around the provider call: claim, read, save. Only the barem photos and the test's own
 * questions are sent; the photos are deleted once read.
 */
@Service
public class BaremService {

    private static final Logger log = LoggerFactory.getLogger(BaremService.class);

    static final String NO_PROVIDER = "Citirea baremului nu e configurată pe server.";
    static final String UNREADABLE = "Nu am putut citi baremul. Încearcă din nou peste câteva minute.";
    static final String NO_QUESTIONS = "Testul nu are încă întrebări: adaugă-le, apoi încarcă baremul.";
    private static final TypeReference<List<StoredPage>> PAGES = new TypeReference<>() { };
    private static final TypeReference<List<Entry>> ENTRIES = new TypeReference<>() { };
    private static final Set<String> TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final BaremReadRepository repository;
    private final UserRepository userRepository;
    private final QuizRepository quizRepository;
    private final QuizItemRepository itemRepository;
    private final QuizOptionRepository optionRepository;
    private final QuizItemPartRepository partRepository;
    private final FileService fileService;
    private final List<BaremReader> readers;
    private final AiGradingProperties properties;
    private final AiImportProperties importProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate tx;

    public BaremService(BaremReadRepository repository, UserRepository userRepository, QuizRepository quizRepository,
                        QuizItemRepository itemRepository, QuizOptionRepository optionRepository,
                        QuizItemPartRepository partRepository, FileService fileService, List<BaremReader> readers,
                        AiGradingProperties properties, AiImportProperties importProperties, ObjectMapper objectMapper,
                        Clock clock, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.quizRepository = quizRepository;
        this.itemRepository = itemRepository;
        this.optionRepository = optionRepository;
        this.partRepository = partRepository;
        this.fileService = fileService;
        this.readers = readers;
        this.properties = properties;
        this.importProperties = importProperties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.tx = new TransactionTemplate(transactionManager);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // --- teacher ---

    @Transactional(readOnly = true)
    public AiGradingSettingsDto settings(String email) {
        if (!properties.enabled()) {
            return new AiGradingSettingsDto(false, 0, 0, 0);
        }
        return new AiGradingSettingsDto(true, properties.baremMaxPages(), properties.baremDailyPageLimit(),
                repository.pagesSince(teacher(email).getId(), startOfToday()));
    }

    /** Stores the barem photos and queues them; the worker reads them within seconds. */
    @Transactional
    public BaremReadDto create(String email, Long quizId, List<MultipartFile> files) {
        requireEnabled();
        if (!quizRepository.existsById(quizId)) {
            throw new QuizNotFoundException("Quiz", quizId);
        }
        if (itemRepository.findByQuizIdOrderByPosition(quizId).isEmpty()) {
            throw new InvalidQuizException(NO_QUESTIONS);
        }
        User teacher = teacher(email);
        List<MultipartFile> pages = files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (pages.isEmpty()) {
            throw new InvalidQuizException("Alege cel puțin o poză cu baremul.");
        }
        if (pages.size() > properties.baremMaxPages()) {
            throw new InvalidQuizException("Poți trimite cel mult " + properties.baremMaxPages() + " poze cu baremul odată.");
        }
        for (MultipartFile page : pages) {
            if (page.getContentType() == null || !TYPES.contains(page.getContentType())) {
                throw new InvalidQuizException("Pozele trebuie să fie JPG, PNG sau WEBP.");
            }
            if (page.getSize() > AiImportService.MAX_PAGE_BYTES) {
                throw new InvalidQuizException("O poză e prea mare (maxim 4 MB).");
            }
        }
        long used = repository.pagesSince(teacher.getId(), startOfToday());
        if (used + pages.size() > properties.baremDailyPageLimit()) {
            long left = Math.max(0, properties.baremDailyPageLimit() - used);
            String limit = RoCount.of(properties.baremDailyPageLimit(), "poză", "poze");
            throw new InvalidQuizException(left == 0
                    ? "Ai atins limita de azi pentru barem (" + limit + "). Mâine poți trimite din nou."
                    : "Azi mai poți trimite doar " + RoCount.of(left, "poză", "poze") + " cu baremul (limita e " + limit
                    + " pe zi).");
        }
        List<StoredPage> stored = new ArrayList<>();
        for (MultipartFile page : pages) {
            try {
                String key = fileService.uploadImage(importProperties.pagesDir(), page);
                stored.add(new StoredPage(key, page.getContentType()));
                whenRolledBack(key);
            } catch (IOException e) {
                throw new UncheckedIOException("Nu am putut salva poza", e);
            }
        }
        BaremRead saved = repository.save(new BaremRead(quizId, teacher.getId(), stored.size(), write(stored),
                clock.instant()));
        return dto(saved);
    }

    /** One of the teacher's own readings of this test; another teacher's is the same 404 as a missing one. */
    @Transactional(readOnly = true)
    public BaremReadDto get(String email, Long quizId, Long id) {
        requireEnabled();
        return dto(repository.findByIdAndQuizIdAndTeacherId(id, quizId, teacher(email).getId())
                .orElseThrow(() -> new QuizNotFoundException("BaremRead", id)));
    }

    // --- worker ---

    /** Reads the oldest waiting barem, if any; {@code false} when the queue is empty. */
    public boolean processNext() {
        Optional<Claimed> claimed = tx.execute(status -> repository.claimNext().map(read -> {
            read.start(clock.instant());
            repository.save(read);
            return new Claimed(read.getId(), parse(read.getPageKeys(), PAGES), outline(read.getQuizId()));
        }));
        if (claimed == null || claimed.isEmpty()) {
            return false;
        }
        long id = claimed.get().id();
        Outcome outcome = read(id, claimed.get());
        tx.executeWithoutResult(status -> repository.findById(id).ifPresent(read -> {
            if (read.getStatus() != AiImportStatus.RUNNING) {
                return; // recovered as stuck meanwhile: not ours to finish
            }
            if (outcome.error() != null) {
                read.fail(outcome.error(), clock.instant());
            } else {
                read.succeed(write(outcome.result().entries()), outcome.result().model(),
                        outcome.result().inputTokens(), outcome.result().outputTokens(), clock.instant());
            }
            read.forgetPages();
            repository.save(read);
            deleteAfterCommit(claimed.get().pages());
        }));
        return true;
    }

    /** Readings that never finished (the app stopped mid-read) fail, so the teacher can send the photos again. */
    @Transactional
    public int recoverStuck() {
        List<BaremRead> stuck = repository.findByStatusAndStartedAtBefore(AiImportStatus.RUNNING,
                clock.instant().minus(AiImportService.STUCK_AFTER));
        for (BaremRead read : stuck) {
            read.fail(AiImportService.INTERRUPTED, clock.instant());
            deleteAfterCommit(parse(read.getPageKeys(), PAGES));
            read.forgetPages();
        }
        repository.saveAll(stuck);
        return stuck.size();
    }

    private record Claimed(long id, List<StoredPage> pages, List<OutlineItem> questions) {
    }

    private record Outcome(BaremReader.Result result, String error) {
    }

    /** Runs with no transaction open; every failure becomes a reason the teacher can read. */
    private Outcome read(long id, Claimed claimed) {
        Optional<BaremReader> reader = readers.stream().filter(r -> r.provider().equals(properties.provider())).findFirst();
        if (reader.isEmpty()) {
            log.error("Barem read {}: no BaremReader named '{}'", id, properties.provider());
            return new Outcome(null, NO_PROVIDER);
        }
        try {
            List<PageImage> pages = new ArrayList<>();
            for (StoredPage page : claimed.pages()) {
                pages.add(new PageImage(fileService.loadImage(importProperties.pagesDir(), page.key()).getContentAsByteArray(),
                        page.type()));
            }
            BaremReader.Result result = reader.get().read(pages, claimed.questions());
            if (result.entries().isEmpty()) {
                return new Outcome(null, BaremReading.NOT_A_BAREM);
            }
            return new Outcome(result, null);
        } catch (ExtractionException e) {
            log.warn("Barem read {} failed: {}", id, e.getMessage(), e);
            return new Outcome(null, e.getMessage());
        } catch (Exception e) {
            log.error("Barem read {} failed", id, e);
            return new Outcome(null, UNREADABLE);
        }
    }

    // --- the test's questions, and the reading mapped onto them ---

    /** The test's questions as the reader gets them, numbered as on screen. */
    private List<OutlineItem> outline(Long quizId) {
        List<QuizItem> items = itemRepository.findByQuizIdOrderByPosition(quizId);
        Map<Long, String> numbers = QuestionNumbers.of(items);
        List<OutlineItem> outline = new ArrayList<>();
        for (QuizItem item : items) {
            List<String> options = item.getType() == QuizItemType.SINGLE_CHOICE
                    ? optionRepository.findByItemIdOrderByPosition(item.getId()).stream().map(QuizOption::getText).toList()
                    : List.of();
            List<OutlinePart> parts = partRepository.findByItemIdOrderByPosition(item.getId()).stream()
                    .map(p -> new OutlinePart(p.label(), p.getStatement(), p.getPoints())).toList();
            outline.add(new OutlineItem(numbers.get(item.getId()), item.getType(), item.getStatement(), options,
                    item.getPoints(), parts));
        }
        return outline;
    }

    private BaremReadDto dto(BaremRead read) {
        BaremProposalDto proposal = read.getStatus() == AiImportStatus.DONE && read.getResultJson() != null
                ? proposal(read.getQuizId(), parse(read.getResultJson(), ENTRIES)) : null;
        return new BaremReadDto(read.getId(), read.getStatus(), read.getError(), proposal);
    }

    /**
     * What the AI read, on the test as it is now (the teacher may have edited it meanwhile): a question by its number,
     * a grilă's letter by the option's place, a requirement by its letter. A number the test does not have is listed
     * apart; a letter the question does not have becomes a warning.
     */
    BaremProposalDto proposal(Long quizId, List<Entry> entries) {
        List<QuizItem> items = itemRepository.findByQuizIdOrderByPosition(quizId);
        Map<Long, String> numbers = QuestionNumbers.of(items);
        Set<String> unmatched = new LinkedHashSet<>();
        List<ItemProposal> proposals = new ArrayList<>();
        for (QuizItem item : items) {
            String label = numbers.get(item.getId());
            Entry entry = entries.stream().filter(e -> sameLabel(e.label(), label)).findFirst().orElse(null);
            if (entry == null) {
                continue;
            }
            List<String> warnings = new ArrayList<>(entry.warnings() == null ? List.of() : entry.warnings());
            String currentLetter = null;
            String proposedLetter = null;
            Long proposedOptionId = null;
            if (item.getType() == QuizItemType.SINGLE_CHOICE) {
                List<QuizOption> options = optionRepository.findByItemIdOrderByPosition(item.getId());
                for (int i = 0; i < options.size(); i++) {
                    if (options.get(i).isCorrect()) {
                        currentLetter = BaremReading.letter(i);
                    }
                }
                if (entry.correctLetter() != null) {
                    int index = entry.correctLetter().charAt(0) - 'a';
                    if (index >= 0 && index < options.size()) {
                        proposedLetter = entry.correctLetter();
                        proposedOptionId = options.get(index).getId();
                    } else {
                        warnings.add("Baremul dă varianta " + entry.correctLetter() + "), dar întrebarea are doar "
                                + RoCount.of(options.size(), "variantă", "variante") + ".");
                    }
                }
            }
            List<PartProposal> parts = new ArrayList<>();
            for (QuizItemPart part : partRepository.findByItemIdOrderByPosition(item.getId())) {
                String barem = (entry.parts() == null ? List.<PartBarem>of() : entry.parts()).stream()
                        .filter(p -> p.label().equalsIgnoreCase(part.label())).map(PartBarem::barem).findFirst().orElse(null);
                parts.add(new PartProposal(part.getId(), part.label(), part.getPoints(), part.getStatement(),
                        part.getSolution(), barem));
            }
            proposals.add(new ItemProposal(item.getId(), label, item.getType(), item.getStatement(), item.getPoints(),
                    currentLetter, proposedLetter, proposedOptionId, item.getSolution(), entry.barem(), parts, warnings));
        }
        for (Entry entry : entries) {
            if (numbers.values().stream().noneMatch(label -> sameLabel(entry.label(), label))) {
                unmatched.add(entry.label());
            }
        }
        return new BaremProposalDto(proposals, List.copyOf(unmatched), quizRepository.countAttempts(quizId) > 0);
    }

    /** "III.2" = "iii.2" = "III. 2": the AI may space or case the number differently. */
    private static boolean sameLabel(String a, String b) {
        return a != null && b != null
                && a.replaceAll("\\s", "").toUpperCase(Locale.ROOT).equals(b.replaceAll("\\s", "").toUpperCase(Locale.ROOT));
    }

    // --- helpers ---

    /** Switched off, the feature does not exist: 404 like any unknown address. */
    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private User teacher(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> new QuizNotFoundException("User", null));
    }

    private Instant startOfToday() {
        return LocalDate.now(clock.withZone(AiImportService.ZONE)).atStartOfDay(AiImportService.ZONE).toInstant();
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot write barem JSON", e);
        }
    }

    private <T> T parse(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException e) {
            throw new IllegalStateException("Cannot read barem JSON", e);
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

    /** A photo saved for a reading that was then rolled back is deleted with it. */
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
            fileService.deleteImage(importProperties.pagesDir(), key);
        } catch (IOException e) {
            log.warn("Could not delete barem photo {}", key, e);
        }
    }
}
