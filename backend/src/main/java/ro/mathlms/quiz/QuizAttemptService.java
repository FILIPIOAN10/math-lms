package ro.mathlms.quiz;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ro.mathlms.cache.AfterCommitCacheEvictor;
import ro.mathlms.cache.CacheNames;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.quiz.AdminAttemptDtos.AdminAttemptDetailDto;
import ro.mathlms.quiz.AdminAttemptDtos.AdminAttemptSummaryDto;
import ro.mathlms.quiz.AdminAttemptDtos.AdminItemReviewDto;
import ro.mathlms.quiz.StudentQuizDtos.AnswerFeedbackDto;
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultDto;
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultViewDto;
import ro.mathlms.quiz.StudentQuizDtos.ItemResultDto;
import ro.mathlms.quiz.StudentQuizDtos.MyAttemptDto;
import ro.mathlms.quiz.StudentQuizDtos.ProgressPointDto;
import ro.mathlms.quiz.StudentQuizDtos.SavedAnswerDto;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;
import ro.mathlms.quiz.StudentQuizDtos.StudentItemDto;
import ro.mathlms.quiz.StudentQuizDtos.StudentQuizDto;
import ro.mathlms.storage.FileService;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The student side of quizzes: browse published quizzes, start (or resume) an attempt, record
 * single-choice answers, and submit. On submit the SINGLE_CHOICE items are graded automatically
 * on the server; OPEN items are left for a teacher (Faza Q7). The correct answer is never sent to
 * the client before grading — see {@link StudentQuizDtos}.
 */
@Service
public class QuizAttemptService {

    private final QuizRepository quizRepository;
    private final QuizItemRepository itemRepository;
    private final QuizOptionRepository optionRepository;
    private final QuizAttemptRepository attemptRepository;
    private final ItemResponseRepository responseRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AfterCommitCacheEvictor cacheEvictor;
    private final ResultNotifier resultNotifier;
    private final FileService fileService;
    private final Clock clock;
    private final String quizPhotosDir;

    /**
     * How long after the deadline a request is still honoured - covers a browser that fires its last save or its
     * auto-submit a moment late (latency, a slow upload). Anything later is rejected; the server's clock decides.
     */
    static final Duration SUBMIT_GRACE = Duration.ofSeconds(30);

    public QuizAttemptService(QuizRepository quizRepository, QuizItemRepository itemRepository,
                              QuizOptionRepository optionRepository, QuizAttemptRepository attemptRepository,
                              ItemResponseRepository responseRepository, UserRepository userRepository,
                              EnrollmentRepository enrollmentRepository, AfterCommitCacheEvictor cacheEvictor,
                              ResultNotifier resultNotifier, FileService fileService, Clock clock,
                              @Value("${app.storage.quiz-photos-dir}") String quizPhotosDir) {
        this.quizRepository = quizRepository;
        this.itemRepository = itemRepository;
        this.optionRepository = optionRepository;
        this.attemptRepository = attemptRepository;
        this.responseRepository = responseRepository;
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.cacheEvictor = cacheEvictor;
        this.resultNotifier = resultNotifier;
        this.fileService = fileService;
        this.clock = clock;
        this.quizPhotosDir = quizPhotosDir;
    }

    /**
     * Published quizzes this student may take: the ones for every student plus the ones assigned
     * to a class they are enrolled in (a student with no classes sees only the first group).
     */
    @Transactional(readOnly = true)
    public List<Quiz> listPublished(String studentEmail) {
        User student = requireUser(studentEmail);
        List<Long> classIds = enrollmentRepository.findClassIdsByStudentId(student.getId());
        if (classIds.isEmpty()) {
            return quizRepository.findByStatusAndSchoolClassIsNullOrderByTitle(QuizStatus.PUBLISHED);
        }
        return quizRepository.findVisibleToClasses(QuizStatus.PUBLISHED, classIds);
    }

    /** A quiz is open to everyone unless assigned to a class — then only to that class's students. */
    private boolean isVisibleTo(Quiz quiz, User student) {
        return quiz.getSchoolClass() == null
                || enrollmentRepository.existsByStudentIdAndSchoolClassId(student.getId(), quiz.getSchoolClass().getId());
    }

    /**
     * Starts a fresh attempt at a published quiz, or resumes the student's existing in-progress one,
     * and returns the answer-hidden quiz to fill in.
     */
    @Transactional
    public StartedAttemptDto startAttempt(Long quizId, String studentEmail) {
        return startAttempt(quizId, studentEmail, AttemptMode.TEST);
    }

    /**
     * {@link AttemptMode#PRACTICE} needs the quiz to allow it (the teacher's opt-in) and never has a deadline; a
     * student may have one open practice next to one open test, but resumes the one of the mode asked for.
     */
    @Transactional
    public StartedAttemptDto startAttempt(Long quizId, String studentEmail, AttemptMode mode) {
        User student = requireUser(studentEmail);
        Quiz quiz = quizRepository.findById(quizId)
                .filter(q -> q.getStatus() == QuizStatus.PUBLISHED)
                // 404 (not 403) for another class's quiz: a guessed id must not confirm it exists
                .filter(q -> isVisibleTo(q, student))
                .orElseThrow(() -> new QuizNotFoundException("Quiz", quizId));
        if (mode == AttemptMode.PRACTICE && !quiz.isPracticeAllowed()) {
            throw new InvalidQuizException("Acest quiz nu permite modul practică");
        }
        Optional<QuizAttempt> open = attemptRepository
                .findByQuizIdAndStudentIdAndStatusAndMode(quizId, student.getId(), QuizAttemptStatus.IN_PROGRESS, mode);
        if (open.isPresent() && open.get().isOverdue(Instant.now(clock), SUBMIT_GRACE)) {
            // Time ran out before the expiry job got to it: hand it in now (its saved answers count), then start fresh.
            autoSubmitIfOverdue(open.get().getId());
            open = Optional.empty();
        }
        QuizAttempt attempt = open.orElseGet(() -> attemptRepository.save(new QuizAttempt(quiz, student, Instant.now(clock), mode)));
        List<SavedAnswerDto> answers = responseRepository.findByAttemptId(attempt.getId()).stream()
                .map(response -> attempt.getMode() == AttemptMode.PRACTICE
                        ? SavedAnswerDto.from(response, feedbackFor(response.getItem(), response))
                        : SavedAnswerDto.from(response))
                .toList();
        return new StartedAttemptDto(attempt.getId(), attempt.getStatus(), studentQuiz(quiz), answers,
                attempt.getDeadlineAt(), Instant.now(clock), attempt.getMode());
    }

    /** The student's own attempts, newest first — the "Încercările mele" list. */
    @Transactional(readOnly = true)
    public List<MyAttemptDto> listMyAttempts(String studentEmail) {
        User student = requireUser(studentEmail);
        return attemptRepository.findByStudentIdOrderByStartedAtDesc(student.getId()).stream()
                .map(MyAttemptDto::from)
                .toList();
    }

    /**
     * The student's graded attempts over time, oldest first, each with its percent of the quiz's maximum
     * score. The maximums come from one aggregate query for all the quizzes involved.
     */
    @Cacheable(cacheNames = CacheNames.PROGRESS, key = "#studentEmail")
    @Transactional(readOnly = true)
    public List<ProgressPointDto> getProgress(String studentEmail) {
        User student = requireUser(studentEmail);
        List<QuizAttempt> graded = attemptRepository
                .findByStudentIdAndStatusAndModeOrderBySubmittedAtAsc(student.getId(), QuizAttemptStatus.GRADED, AttemptMode.TEST);
        if (graded.isEmpty()) {
            return List.of();
        }
        List<Long> quizIds = graded.stream().map(a -> a.getQuiz().getId()).distinct().toList();
        Map<Long, Long> maxByQuiz = itemRepository.sumPointsByQuiz(quizIds).stream()
                .collect(Collectors.toMap(QuizMaxScore::quizId, QuizMaxScore::maxScore));
        return graded.stream().map(attempt -> {
            int max = maxByQuiz.getOrDefault(attempt.getQuiz().getId(), 0L).intValue();
            int score = attempt.getScore() == null ? 0 : attempt.getScore();
            int percent = max == 0 ? 0 : Math.round(score * 100f / max);
            return new ProgressPointDto(attempt.getId(), attempt.getQuiz().getId(), attempt.getQuiz().getTitle(),
                    attempt.getSubmittedAt(), score, max, percent);
        }).collect(Collectors.toCollection(ArrayList::new)); // a plain list: it is what the cache stores
    }

    /**
     * Records (or replaces) the student's choice for one SINGLE_CHOICE item. In a PRACTICE the answer is marked on
     * the spot and the feedback returned; in a graded TEST nothing is revealed (empty) and nothing is graded until submit.
     */
    @Transactional
    public Optional<AnswerFeedbackDto> saveResponse(Long attemptId, Long itemId, Long optionId, String studentEmail) {
        QuizAttempt attempt = requireOwnedInProgress(attemptId, studentEmail);
        requireTimeLeft(attempt);
        QuizItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new QuizNotFoundException("QuizItem", itemId));
        if (!item.getQuiz().getId().equals(attempt.getQuiz().getId())) {
            throw new InvalidQuizException("Item does not belong to this quiz");
        }
        if (item.getType() != QuizItemType.SINGLE_CHOICE) {
            throw new InvalidQuizException("Only single-choice items are answered this way");
        }
        QuizOption option = optionRepository.findById(optionId)
                .orElseThrow(() -> new QuizNotFoundException("QuizOption", optionId));
        if (!option.getItem().getId().equals(itemId)) {
            throw new InvalidQuizException("Option does not belong to this item");
        }
        ItemResponse response = responseRepository.findByAttemptIdAndItemId(attemptId, itemId)
                .orElseGet(() -> new ItemResponse(attempt, item));
        response.answerSingleChoice(option);
        if (attempt.getMode() == AttemptMode.PRACTICE) {
            response.gradeAuto(option.isCorrect(), option.isCorrect() ? item.getPoints() : 0);
        }
        responseRepository.save(response);
        return attempt.getMode() == AttemptMode.PRACTICE
                ? Optional.of(feedbackFor(item, response))
                : Optional.empty();
    }

    /**
     * Stores the uploaded rezolvare photo for one OPEN item and points the response at it. Replacing
     * an earlier photo deletes the old file. The correct/points stay null — a teacher grades it later. In a PRACTICE
     * the barem comes back to compare with; in a graded TEST nothing does.
     */
    @Transactional
    public Optional<AnswerFeedbackDto> uploadOpenPhoto(Long attemptId, Long itemId, MultipartFile file, String studentEmail) {
        QuizAttempt attempt = requireOwnedInProgress(attemptId, studentEmail);
        requireTimeLeft(attempt);
        QuizItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new QuizNotFoundException("QuizItem", itemId));
        if (!item.getQuiz().getId().equals(attempt.getQuiz().getId())) {
            throw new InvalidQuizException("Item does not belong to this quiz");
        }
        if (item.getType() != QuizItemType.OPEN) {
            throw new InvalidQuizException("Only open items take a photo");
        }
        requireImage(file);

        ItemResponse response = responseRepository.findByAttemptIdAndItemId(attemptId, itemId)
                .orElseGet(() -> new ItemResponse(attempt, item));
        String previous = response.getImageKey();
        String stored;
        try {
            stored = fileService.uploadImage(quizPhotosDir, file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store the photo", e);
        }
        response.answerOpen(stored);
        responseRepository.save(response);

        if (previous != null) {
            try {
                fileService.deleteImage(quizPhotosDir, previous);
            } catch (IOException ignored) {
                // The new photo is saved; a leftover old file is not worth failing the request.
            }
        }
        return attempt.getMode() == AttemptMode.PRACTICE
                ? Optional.of(feedbackFor(item, response))
                : Optional.empty();
    }

    /**
     * Hands the attempt in. Every SINGLE_CHOICE response is auto-graded (correct earns the item's
     * points, anything else zero). If the quiz has no OPEN items the attempt is fully graded and its
     * final score set; otherwise it stays SUBMITTED until a teacher scores the open items.
     */
    @Transactional
    public AttemptResultDto submit(Long attemptId, String studentEmail) {
        // Not time-checked on purpose: after the deadline the answers can no longer change, so handing in what was
        // saved in time is exactly right (and it is what the browser does when the countdown reaches zero).
        QuizAttempt attempt = requireOwnedInProgress(attemptId, studentEmail);
        return finishSubmission(attempt);
    }

    /** Ids of in-progress attempts whose time (plus grace) has run out - the expiry job's work list. */
    @Transactional(readOnly = true)
    public List<Long> findOverdueAttemptIds() {
        return attemptRepository.findOverdueIds(Instant.now(clock).minus(SUBMIT_GRACE));
    }

    /**
     * Hands in one overdue attempt on the student's behalf. Re-checks under the row lock, so a student who submitted
     * a moment earlier (or a second app instance running the same job) makes this a harmless no-op.
     */
    @Transactional
    public void autoSubmitIfOverdue(Long attemptId) {
        attemptRepository.findByIdForUpdate(attemptId)
                .filter(a -> a.getStatus() == QuizAttemptStatus.IN_PROGRESS)
                .filter(a -> a.isOverdue(Instant.now(clock), SUBMIT_GRACE))
                .ifPresent(attempt -> {
                    finishSubmission(attempt);
                    attemptRepository.saveAndFlush(attempt); // flushed now: a fresh attempt may be inserted right after
                });
    }

    /** Grades the SINGLE_CHOICE items and hands the attempt in; shared by the student's submit and the auto-submit. */
    private AttemptResultDto finishSubmission(QuizAttempt attempt) {
        Long attemptId = attempt.getId();
        List<QuizItem> items = itemRepository.findByQuizIdOrderByPosition(attempt.getQuiz().getId());
        Map<Long, ItemResponse> byItem = responseRepository.findByAttemptId(attemptId).stream()
                .collect(Collectors.toMap(r -> r.getItem().getId(), Function.identity()));

        int autoScore = 0;
        int autoMaxScore = 0;
        boolean hasOpenItems = false;
        for (QuizItem item : items) {
            if (item.getType() != QuizItemType.SINGLE_CHOICE) {
                hasOpenItems = true;
                continue;
            }
            autoMaxScore += item.getPoints();
            ItemResponse response = byItem.get(item.getId());
            boolean correct = response != null && response.getSelectedOption() != null
                    && response.getSelectedOption().isCorrect();
            int awarded = correct ? item.getPoints() : 0;
            if (response != null) {
                response.gradeAuto(correct, awarded);
                responseRepository.save(response);
            }
            autoScore += awarded;
        }

        attempt.submit();
        if (attempt.getMode() == AttemptMode.PRACTICE) {
            attempt.completePractice(); // never marked: no score, no cache to drop, nobody to email
        } else if (!hasOpenItems) {
            attempt.markGraded(autoScore);
            evictGradingCaches(attempt);
        }
        attemptRepository.save(attempt);
        return new AttemptResultDto(attempt.getId(), attempt.getStatus(),
                autoScore, autoMaxScore, attempt.getScore());
    }

    /**
     * The student's own graded attempt (Q8). Available once submitted; the correct option and the
     * barem — withheld before submit — are revealed here. OPEN items show their teacher-awarded points
     * (null while still pending). Only the owner may view it.
     */
    @Transactional(readOnly = true)
    public AttemptResultViewDto getResult(Long attemptId, String studentEmail) {
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new QuizNotFoundException("QuizAttempt", attemptId));
        if (!attempt.getStudent().getEmail().equals(studentEmail)) {
            throw new QuizAccessException("This attempt belongs to another student");
        }
        if (attempt.getStatus() == QuizAttemptStatus.IN_PROGRESS) {
            throw new InvalidQuizException("Submit the attempt to see the result");
        }

        List<QuizItem> items = itemRepository.findByQuizIdOrderByPosition(attempt.getQuiz().getId());
        Map<Long, ItemResponse> byItem = responseRepository.findByAttemptId(attemptId).stream()
                .collect(Collectors.toMap(r -> r.getItem().getId(), Function.identity()));

        int maxScore = 0;
        List<ItemResultDto> itemResults = new ArrayList<>();
        for (QuizItem item : items) {
            maxScore += item.getPoints();
            ItemResponse response = byItem.get(item.getId());
            String selectedText = null;
            String correctText = null;
            boolean photoUploaded = false;
            if (item.getType() == QuizItemType.SINGLE_CHOICE) {
                List<QuizOption> options = optionRepository.findByItemIdOrderByPosition(item.getId());
                correctText = options.stream().filter(QuizOption::isCorrect)
                        .map(QuizOption::getText).findFirst().orElse(null);
                if (response != null && response.getSelectedOption() != null) {
                    selectedText = response.getSelectedOption().getText();
                }
            } else {
                photoUploaded = response != null && response.getImageKey() != null;
            }
            itemResults.add(new ItemResultDto(
                    item.getPosition(), item.getType(), item.getStatement(), item.getPoints(),
                    response == null ? null : response.getAwardedPoints(),
                    response == null ? null : response.getCorrect(),
                    selectedText, correctText, item.getSolution(), photoUploaded));
        }
        return new AttemptResultViewDto(attempt.getId(), attempt.getQuiz().getTitle(),
                attempt.getStatus(), attempt.getScore(), maxScore, itemResults, attempt.getMode());
    }

    private StudentQuizDto studentQuiz(Quiz quiz) {
        List<StudentItemDto> items = itemRepository.findByQuizIdOrderByPosition(quiz.getId()).stream()
                .map(item -> StudentItemDto.from(item, optionRepository.findByItemIdOrderByPosition(item.getId())))
                .toList();
        return StudentQuizDto.of(quiz, items);
    }

    private QuizAttempt requireOwnedInProgress(Long attemptId, String studentEmail) {
        QuizAttempt attempt = attemptRepository.findByIdForUpdate(attemptId)
                .orElseThrow(() -> new QuizNotFoundException("QuizAttempt", attemptId));
        if (!attempt.getStudent().getEmail().equals(studentEmail)) {
            throw new QuizAccessException("This attempt belongs to another student");
        }
        if (attempt.getStatus() != QuizAttemptStatus.IN_PROGRESS) {
            throw new InvalidQuizException("This attempt is no longer in progress");
        }
        return attempt;
    }

    /** What a PRACTICE answer reveals: the verdict (SINGLE_CHOICE), the right option's id and the barem. */
    private AnswerFeedbackDto feedbackFor(QuizItem item, ItemResponse response) {
        if (item.getType() != QuizItemType.SINGLE_CHOICE) {
            return new AnswerFeedbackDto(null, null, item.getSolution());
        }
        Long correctOptionId = optionRepository.findByItemIdOrderByPosition(item.getId()).stream()
                .filter(QuizOption::isCorrect)
                .map(QuizOption::getId)
                .findFirst().orElse(null);
        return new AnswerFeedbackDto(response.getCorrect(), correctOptionId, item.getSolution());
    }

    private void requireTimeLeft(QuizAttempt attempt) {
        if (attempt.isOverdue(Instant.now(clock), SUBMIT_GRACE)) {
            throw new AttemptExpiredException();
        }
    }

    private static void requireImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidQuizException("A photo file is required");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new InvalidQuizException("Only image files are accepted");
        }
    }

    private User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new QuizAccessException("Unknown user " + email));
    }
    /** Fetch pentru poza uploadată, folosit de admin/profesor. */
    public Resource getOpenPhotoResource(Long attemptId, Long itemId) {
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new QuizNotFoundException("QuizAttempt", attemptId));

        ItemResponse response = responseRepository.findByAttemptIdAndItemId(attemptId, itemId)
                .orElseThrow(() -> new InvalidQuizException("Nu există răspuns pentru acest subiect"));

        if (response.getImageKey() == null) {
            throw new InvalidQuizException("Nu a fost încărcată nicio poză pentru acest răspuns");
        }

        try {
            return fileService.loadImage(quizPhotosDir, response.getImageKey());
        } catch (IOException e) {
            throw new UncheckedIOException("Nu am putut citi poza", e);
        }
    }

    /** Profesorul acordă punctaj manual pentru un subiect OPEN. */
    @Transactional
    public void gradeOpenResponse(Long attemptId, Long itemId, int points) {
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new QuizNotFoundException("QuizAttempt", attemptId));

        if (attempt.getStatus() != QuizAttemptStatus.SUBMITTED) {
            throw new InvalidQuizException("Attempt-ul trebuie să fie SUBMITTED pentru corectură");
        }

        QuizItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new QuizNotFoundException("QuizItem", itemId));
        if (item.getType() != QuizItemType.OPEN) {
            throw new InvalidQuizException("Only open items are graded manually");
        }
        if (!item.getQuiz().getId().equals(attempt.getQuiz().getId())) {
            throw new InvalidQuizException("Item does not belong to this quiz");
        }
        if (points > item.getPoints()) {
            throw new InvalidQuizException(
                    "At most " + item.getPoints() + " points can be awarded for this item");
        }

        // Dacă elevul nu a răspuns nimic, creăm un răspuns gol ca să reținem punctajul (ex: 0)
        ItemResponse response = responseRepository.findByAttemptIdAndItemId(attemptId, itemId)
                .orElseGet(() -> new ItemResponse(attempt, item));

        response.gradeManual(points);
        responseRepository.save(response);
    }

    /** Profesorul închide corectura, validând că toate subiectele OPEN au fost notate. */
    @Transactional
    public void finalizeGrading(Long attemptId) {
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new QuizNotFoundException("QuizAttempt", attemptId));

        if (attempt.getStatus() != QuizAttemptStatus.SUBMITTED) {
            throw new InvalidQuizException("Attempt-ul trebuie să fie SUBMITTED pentru finalizare");
        }

        List<QuizItem> items = itemRepository.findByQuizIdOrderByPosition(attempt.getQuiz().getId());
        Map<Long, ItemResponse> responses = responseRepository.findByAttemptId(attemptId).stream()
                .collect(Collectors.toMap(r -> r.getItem().getId(), Function.identity()));

        int totalScore = 0;
        for (QuizItem item : items) {
            ItemResponse response = responses.get(item.getId());

            if (item.getType() == QuizItemType.OPEN) {
                if (response == null || response.getAwardedPoints() == null) {
                    throw new InvalidQuizException("Subiectul " + item.getId() + " nu a fost corectat!");
                }
            }

            if (response != null && response.getAwardedPoints() != null) {
                totalScore += response.getAwardedPoints();
            }
        }

        attempt.markGraded(totalScore);
        attemptRepository.save(attempt);
        evictGradingCaches(attempt);
    }

    /**
     * Everything that follows an attempt becoming GRADED, inside the grading transaction: the cached progress
     * and statistics are dropped (after the commit) and the "result is ready" emails are queued in the outbox
     * (committed atomically with the grade).
     */
    private void evictGradingCaches(QuizAttempt attempt) {
        cacheEvictor.evict(CacheNames.PROGRESS, attempt.getStudent().getEmail());
        cacheEvictor.evict(CacheNames.QUIZ_STATS, attempt.getQuiz().getId());
        resultNotifier.resultGraded(attempt);
    }

    /** The teacher's grading queue (Q10): attempts in one status, oldest submission first. */
    @Transactional(readOnly = true)
    public List<AdminAttemptSummaryDto> listForGrading(QuizAttemptStatus status) {
        return attemptRepository.findByStatusForGrading(status).stream()
                .map(AdminAttemptSummaryDto::from)
                .toList();
    }

    /**
     * One submitted attempt as the teacher reviews it: every item with the student's answer, the
     * correct option, the barem and the points so far. An in-progress attempt is not reviewable yet.
     */
    @Transactional(readOnly = true)
    public AdminAttemptDetailDto getAttemptForGrading(Long attemptId) {
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new QuizNotFoundException("QuizAttempt", attemptId));
        if (attempt.getStatus() == QuizAttemptStatus.IN_PROGRESS) {
            throw new InvalidQuizException("The student has not submitted this attempt yet");
        }

        List<QuizItem> items = itemRepository.findByQuizIdOrderByPosition(attempt.getQuiz().getId());
        Map<Long, ItemResponse> byItem = responseRepository.findByAttemptId(attemptId).stream()
                .collect(Collectors.toMap(r -> r.getItem().getId(), Function.identity()));

        int maxScore = 0;
        List<AdminItemReviewDto> reviews = new ArrayList<>();
        for (QuizItem item : items) {
            maxScore += item.getPoints();
            ItemResponse response = byItem.get(item.getId());
            String selectedText = null;
            String correctText = null;
            if (item.getType() == QuizItemType.SINGLE_CHOICE) {
                correctText = optionRepository.findByItemIdOrderByPosition(item.getId()).stream()
                        .filter(QuizOption::isCorrect)
                        .map(QuizOption::getText)
                        .findFirst().orElse(null);
                if (response != null && response.getSelectedOption() != null) {
                    selectedText = response.getSelectedOption().getText();
                }
            }
            reviews.add(new AdminItemReviewDto(
                    item.getId(), item.getPosition(), item.getType(), item.getStatement(), item.getPoints(),
                    item.getSolution(), selectedText, correctText,
                    response == null ? null : response.getCorrect(),
                    response == null ? null : response.getAwardedPoints(),
                    response != null && response.getImageKey() != null));
        }
        return new AdminAttemptDetailDto(attempt.getId(), attempt.getQuiz().getTitle(),
                attempt.getStudent().getFullName(), attempt.getStatus(), attempt.getSubmittedAt(),
                attempt.getScore(), maxScore, reviews);
    }
}
