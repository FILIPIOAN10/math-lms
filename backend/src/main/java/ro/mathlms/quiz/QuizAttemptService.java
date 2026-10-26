package ro.mathlms.quiz;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import ro.mathlms.audit.AuditAction;
import ro.mathlms.audit.AuditEvent;
import ro.mathlms.audit.AuditLog;
import ro.mathlms.audit.AuditText;
import ro.mathlms.cache.AfterCommitCacheEvictor;
import ro.mathlms.cache.CacheNames;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.RoCount;
import ro.mathlms.quiz.AdminAttemptDtos.AdminAttemptDetailDto;
import ro.mathlms.quiz.AdminAttemptDtos.AdminAttemptSummaryDto;
import ro.mathlms.quiz.AdminAttemptDtos.AdminItemReviewDto;
import ro.mathlms.quiz.StudentQuizDtos.AnswerFeedbackDto;
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultDto;
import ro.mathlms.quiz.StudentQuizDtos.AttemptResultViewDto;
import ro.mathlms.quiz.StudentQuizDtos.HintDto;
import ro.mathlms.quiz.StudentQuizDtos.ItemResultDto;
import ro.mathlms.quiz.StudentQuizDtos.MyAttemptDto;
import ro.mathlms.quiz.StudentQuizDtos.PhotosDto;
import ro.mathlms.quiz.StudentQuizDtos.ProgressPointDto;
import ro.mathlms.quiz.StudentQuizDtos.QuizPreviewDto;
import ro.mathlms.quiz.StudentQuizDtos.RevealedHintsDto;
import ro.mathlms.quiz.StudentQuizDtos.SavedAnswerDto;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;
import ro.mathlms.quiz.StudentQuizDtos.StudentItemDto;
import ro.mathlms.quiz.StudentQuizDtos.StudentQuizDto;
import ro.mathlms.storage.FileService;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
    private final QuizItemHintRepository hintRepository;
    private final QuizAttemptRepository attemptRepository;
    private final ItemResponseRepository responseRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AfterCommitCacheEvictor cacheEvictor;
    private final ResultNotifier resultNotifier;
    private final FileService fileService;
    private final AuditLog auditLog;
    private final Clock clock;
    private final String quizPhotosDir;
    private final List<SubmissionListener> submissionListeners;

    /**
     * How long after the deadline a request is still honoured - covers a browser that fires its last save or its
     * auto-submit a moment late (latency, a slow upload). Anything later is rejected; the server's clock decides.
     */
    static final Duration SUBMIT_GRACE = Duration.ofSeconds(30);

    private final QuizItemPartRepository partRepository;
    private final EnChapterLinks enChapterLinks;

    public QuizAttemptService(QuizRepository quizRepository, QuizItemRepository itemRepository,
                              QuizOptionRepository optionRepository, QuizItemHintRepository hintRepository,
                              QuizAttemptRepository attemptRepository,
                              ItemResponseRepository responseRepository, UserRepository userRepository,
                              EnrollmentRepository enrollmentRepository, AfterCommitCacheEvictor cacheEvictor,
                              ResultNotifier resultNotifier, FileService fileService, AuditLog auditLog, Clock clock,
                              @Value("${app.storage.quiz-photos-dir}") String quizPhotosDir,
                              QuizItemPartRepository partRepository, EnChapterLinks enChapterLinks) {
        this(quizRepository, itemRepository, optionRepository, hintRepository, attemptRepository, responseRepository,
                userRepository, enrollmentRepository, cacheEvictor, resultNotifier, fileService, auditLog, clock,
                quizPhotosDir, partRepository, enChapterLinks, List.of());
    }

    /** ...told on every hand-in ({@link SubmissionListener}: the AI grading's queue). */
    @Autowired
    public QuizAttemptService(QuizRepository quizRepository, QuizItemRepository itemRepository,
                              QuizOptionRepository optionRepository, QuizItemHintRepository hintRepository,
                              QuizAttemptRepository attemptRepository,
                              ItemResponseRepository responseRepository, UserRepository userRepository,
                              EnrollmentRepository enrollmentRepository, AfterCommitCacheEvictor cacheEvictor,
                              ResultNotifier resultNotifier, FileService fileService, AuditLog auditLog, Clock clock,
                              @Value("${app.storage.quiz-photos-dir}") String quizPhotosDir,
                              QuizItemPartRepository partRepository, EnChapterLinks enChapterLinks,
                              List<SubmissionListener> submissionListeners) {
        this.submissionListeners = List.copyOf(submissionListeners);
        this.enChapterLinks = enChapterLinks;
        this.partRepository = partRepository;
        this.auditLog = auditLog;
        this.quizRepository = quizRepository;
        this.itemRepository = itemRepository;
        this.optionRepository = optionRepository;
        this.hintRepository = hintRepository;
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
     * The "before you start" summary of a quiz the student may take: item count, total points, time limit. The same
     * rules as starting it apply - a draft, or another class's quiz, is a 404 (a guessed id must not confirm it exists).
     */
    @Transactional(readOnly = true)
    public QuizPreviewDto getPreview(Long quizId, String studentEmail) {
        Quiz quiz = requireTakeable(quizId, requireUser(studentEmail));
        int maxScore = itemRepository.sumPointsByQuiz(List.of(quizId)).stream()
                .findFirst().map(m -> m.maxScore().intValue()).orElse(0);
        return new QuizPreviewDto(quiz.getId(), quiz.getTitle(), quiz.getDescription(), quiz.getTimeLimitMinutes(),
                quiz.isPracticeAllowed(), (int) itemRepository.countByQuizId(quizId), maxScore);
    }

    private Quiz requireTakeable(Long quizId, User student) {
        return quizRepository.findById(quizId)
                .filter(q -> q.getStatus() == QuizStatus.PUBLISHED)
                .filter(q -> isVisibleTo(q, student))
                .orElseThrow(() -> new QuizNotFoundException("Quiz", quizId));
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
        // 404 (not 403) for another class's quiz: a guessed id must not confirm it exists
        Quiz quiz = requireTakeable(quizId, student);
        if (mode == AttemptMode.PRACTICE && !quiz.isPracticeAllowed()) {
            throw new InvalidQuizException("Acest test nu permite modul practică");
        }
        Optional<QuizAttempt> open = attemptRepository
                .findByQuizIdAndStudentIdAndStatusAndMode(quizId, student.getId(), QuizAttemptStatus.IN_PROGRESS, mode);
        if (open.isPresent() && open.get().isOverdue(Instant.now(clock), SUBMIT_GRACE)) {
            // Time ran out before the expiry job got to it: hand it in now (its saved answers count), then start fresh.
            autoSubmitIfOverdue(open.get().getId());
            open = Optional.empty();
        }
        QuizAttempt attempt = open.orElseGet(() -> attemptRepository.save(new QuizAttempt(quiz, student, Instant.now(clock), mode)));
        List<ItemResponse> responses = responseRepository.findByAttemptId(attempt.getId());
        // A row that exists only because the student asked for a hint is not an answer: it must not come back as one
        // (its "feedback" would hand over the solution of a question they have not tried).
        List<SavedAnswerDto> answers = responses.stream()
                .filter(response -> response.getSelectedOption() != null || response.hasPhotos())
                .map(response -> attempt.getMode() == AttemptMode.PRACTICE
                        ? SavedAnswerDto.from(response, feedbackFor(response.getItem(), response))
                        : SavedAnswerDto.from(response))
                .toList();
        List<RevealedHintsDto> revealedHints = attempt.getMode() == AttemptMode.PRACTICE
                ? revealedHints(responses)
                : List.of();
        return new StartedAttemptDto(attempt.getId(), attempt.getStatus(), studentQuiz(quiz, attempt.getMode()), answers,
                attempt.getDeadlineAt(), Instant.now(clock), attempt.getMode(), revealedHints);
    }

    /** The student's own attempts, newest first — the "Încercările mele" list. */
    @Transactional(readOnly = true)
    public List<MyAttemptDto> listMyAttempts(String studentEmail) {
        User student = requireUser(studentEmail);
        List<QuizAttempt> attempts = attemptRepository.findByStudentIdOrderByStartedAtDesc(student.getId());
        Map<Long, Integer> maxScores = maxScores(attempts);
        return attempts.stream()
                .map(a -> MyAttemptDto.from(a, maxScores.getOrDefault(a.getQuiz().getId(), 0)))
                .toList();
    }

    /** Each quiz's total points for a list of attempts, in ONE aggregate query (a quiz without items is 0). */
    private Map<Long, Integer> maxScores(List<QuizAttempt> attempts) {
        if (attempts.isEmpty()) {
            return Map.of();
        }
        Set<Long> quizIds = attempts.stream().map(a -> a.getQuiz().getId()).collect(Collectors.toSet());
        return itemRepository.sumPointsByQuiz(quizIds).stream()
                .collect(Collectors.toMap(QuizMaxScore::quizId, m -> m.maxScore().intValue()));
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
        return saveResponse(attemptId, itemId, optionId, null, null, studentEmail);
    }

    /**
     * ...from a browser that queues unsent answers ({@code clientId} + its counter {@code clientSeq}): an older retry
     * arriving after a newer answer is ignored, and still answered with success, so the browser stops retrying it.
     */
    @Transactional
    public Optional<AnswerFeedbackDto> saveResponse(Long attemptId, Long itemId, Long optionId, java.util.UUID clientId,
                                                    Long clientSeq, String studentEmail) {
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
        boolean applied = response.answerSingleChoice(option, clientId, clientSeq);
        if (applied && attempt.getMode() == AttemptMode.PRACTICE) {
            response.gradeAuto(option.isCorrect(), option.isCorrect() ? item.getPoints() : 0);
        }
        if (applied) {
            responseRepository.save(response);
        }
        return attempt.getMode() == AttemptMode.PRACTICE
                ? Optional.of(feedbackFor(item, response))
                : Optional.empty();
    }

    /**
     * S2: adds one page of the rezolvare to an OPEN item's answer (at most {@link ItemResponse#MAX_PHOTOS}). The
     * correct/points stay null — a teacher grades it later. In a PRACTICE the barem comes back to compare with; in a
     * graded TEST only the photo ids do. A failed transaction removes the file it just stored.
     */
    @Transactional
    public PhotosDto uploadOpenPhoto(Long attemptId, Long itemId, MultipartFile file, String studentEmail) {
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
        if (response.getPhotos().size() >= ItemResponse.MAX_PHOTOS) {
            throw new InvalidQuizException("Ai deja " + ItemResponse.MAX_PHOTOS
                    + " poze la această întrebare. Șterge una ca să adaugi alta.");
        }
        String stored;
        try {
            stored = fileService.uploadImage(quizPhotosDir, file);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store the photo", e);
        }
        deleteFileIfRolledBack(stored);
        response.addPhoto(stored, Instant.now(clock));
        responseRepository.saveAndFlush(response); // the new photo's id is part of the answer

        return new PhotosDto(response.photoIds(),
                attempt.getMode() == AttemptMode.PRACTICE ? feedbackFor(item, response) : null);
    }

    /**
     * S2: takes one page off the answer while the attempt is still open (the same deadline as adding one). Another
     * student's attempt, or a photo of another answer, is the same 404 as one that does not exist. The file goes once
     * the deletion is committed.
     */
    @Transactional
    public PhotosDto deleteOpenPhoto(Long attemptId, Long itemId, Long photoId, String studentEmail) {
        QuizAttempt attempt = requireOwnedInProgress(attemptId, studentEmail);
        requireTimeLeft(attempt);
        ItemResponse response = responseRepository.findByAttemptIdAndItemId(attemptId, itemId)
                .orElseThrow(() -> new QuizNotFoundException("ResponsePhoto", photoId));
        String key = response.removePhoto(photoId)
                .orElseThrow(() -> new QuizNotFoundException("ResponsePhoto", photoId));
        responseRepository.save(response);
        deleteFileAfterCommit(key);
        return new PhotosDto(response.photoIds(), null);
    }

    /** A file stored for a request whose transaction then fails is referenced by nothing: remove it again. */
    private void deleteFileIfRolledBack(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    deleteQuietly(key);
                }
            }
        });
    }

    /** Deletes a removed photo's file only once the removal is committed (a rollback keeps the row - and its file). */
    private void deleteFileAfterCommit(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(key);
            }
        });
    }

    private void deleteQuietly(String key) {
        try {
            fileService.deleteImage(quizPhotosDir, key);
        } catch (IOException ignored) {
            // A leftover file is not worth failing the request for.
        }
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
        if (attempt.getMode() == AttemptMode.TEST) {
            submissionListeners.forEach(listener -> listener.submitted(attempt, items, byItem));
        }
        return new AttemptResultDto(attempt.getId(), attempt.getStatus(),
                autoScore, autoMaxScore, attempt.getScore());
    }

    /**
     * The student's own graded attempt (Q8). Available once submitted; the correct option and the
     * barem — withheld before submit — are revealed here. OPEN items show their teacher-awarded points
     * (null while still pending). Only the owner may view it: anyone else's attempt is the same 404 as one that does
     * not exist (a guessed id must not confirm that the attempt is there).
     */
    @Transactional(readOnly = true)
    public AttemptResultViewDto getResult(Long attemptId, String studentEmail) {
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .filter(a -> a.getStudent().getEmail().equals(studentEmail))
                .orElseThrow(() -> new QuizNotFoundException("QuizAttempt", attemptId));
        if (attempt.getStatus() == QuizAttemptStatus.IN_PROGRESS) {
            throw new InvalidQuizException("Submit the attempt to see the result");
        }

        List<QuizItem> items = itemRepository.findByQuizIdOrderByPosition(attempt.getQuiz().getId());
        Map<Long, ItemResponse> byItem = responseRepository.findByAttemptId(attemptId).stream()
                .collect(Collectors.toMap(r -> r.getItem().getId(), Function.identity()));

        Map<Long, Integer> hintCounts = attempt.getMode() == AttemptMode.PRACTICE
                ? hintCounts(attempt.getQuiz().getId()) : Map.of();
        Map<Long, List<QuizItemPart>> partsByItem = partsOf(items);
        EnChapterLinks.Resolved links = enChapterLinks.resolve(items);
        int maxScore = 0;
        List<ItemResultDto> itemResults = new ArrayList<>();
        for (QuizItem item : items) {
            maxScore += item.getPoints();
            ItemResponse response = byItem.get(item.getId());
            String selectedText = null;
            String correctText = null;
            String selectedLetter = null;
            String correctLetter = null;
            boolean photoUploaded = false;
            if (item.getType() == QuizItemType.SINGLE_CHOICE) {
                List<QuizOption> options = optionRepository.findByItemIdOrderByPosition(item.getId());
                QuizOption correctOption = options.stream().filter(QuizOption::isCorrect).findFirst().orElse(null);
                if (correctOption != null) {
                    correctText = correctOption.getText();
                    correctLetter = letterOf(options, correctOption);
                }
                if (response != null && response.getSelectedOption() != null) {
                    selectedText = response.getSelectedOption().getText();
                    selectedLetter = letterOf(options, response.getSelectedOption());
                }
            } else {
                photoUploaded = response != null && response.hasPhotos();
            }
            itemResults.add(new ItemResultDto(
                    item.getId(), item.getPosition(), item.getType(), item.getStatement(), item.getPoints(),
                    response == null ? null : response.getAwardedPoints(),
                    response == null ? null : response.getCorrect(),
                    selectedText, correctText, item.getSolution(), photoUploaded,
                    response == null ? 0 : response.getHintsUsed(),
                    hintCounts.getOrDefault(item.getId(), 0),
                    response == null ? null : response.getTeacherComment(),
                    item.getSection(),
                    item.getImageKey(),
                    response == null ? List.of() : response.photoIds(),
                    PartDtos.GradedPartDto.of(partsByItem.getOrDefault(item.getId(), List.of()), response, links),
                    selectedLetter, correctLetter, links.linkOf(item)));
        }
        return new AttemptResultViewDto(attempt.getId(), attempt.getQuiz().getTitle(),
                attempt.getStatus(), attempt.getScore(), maxScore, itemResults, attempt.getMode(),
                attempt.getTeacherComment(), attempt.getQuiz().getId(),
                attempt.getQuiz().isPracticeAllowed() && attempt.getQuiz().getStatus() == QuizStatus.PUBLISHED);
    }

    /**
     * S3: the student opened their result, so it is no longer "Nou". Only their own attempt (anyone else's is the same
     * 404 as one that does not exist); a result still waiting for the teacher stays new for when it is graded.
     */
    @Transactional
    public void markResultSeen(Long attemptId, String studentEmail) {
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .filter(a -> a.getStudent().getEmail().equals(studentEmail))
                .orElseThrow(() -> new QuizNotFoundException("QuizAttempt", attemptId));
        attempt.markResultSeen(Instant.now(clock));
    }

    /** The quiz as the student sees it; hint counts only exist in a practice - a graded test hides even that. */
    private StudentQuizDto studentQuiz(Quiz quiz, AttemptMode mode) {
        Map<Long, Integer> hintCounts = mode == AttemptMode.PRACTICE ? hintCounts(quiz.getId()) : Map.of();
        List<QuizItem> quizItems = itemRepository.findByQuizIdOrderByPosition(quiz.getId());
        Map<Long, List<QuizItemPart>> parts = partsOf(quizItems);
        List<StudentItemDto> items = quizItems.stream()
                .map(item -> StudentItemDto.from(item, optionRepository.findByItemIdOrderByPosition(item.getId()),
                        hintCounts.getOrDefault(item.getId(), 0), parts.getOrDefault(item.getId(), List.of())))
                .toList();
        return StudentQuizDto.of(quiz, items);
    }

    private QuizAttempt requireOwnedInProgress(Long attemptId, String studentEmail) {
        // Another student's attempt is the same 404 as one that does not exist: a guessed id confirms nothing.
        QuizAttempt attempt = attemptRepository.findByIdForUpdate(attemptId)
                .filter(a -> a.getStudent().getEmail().equals(studentEmail))
                .orElseThrow(() -> new QuizNotFoundException("QuizAttempt", attemptId));
        if (attempt.getStatus() != QuizAttemptStatus.IN_PROGRESS) {
            throw new InvalidQuizException("This attempt is no longer in progress");
        }
        return attempt;
    }

    /**
     * Reveals hint {@code number} (1-based) of an item to a student PRACTISING. Hints come one at a time and in order
     * (asking again for one already shown just returns it), cost nothing, and the furthest one reached is recorded on the
     * student's response. A graded test never gives hints.
     */
    @Transactional
    public HintDto revealHint(Long attemptId, Long itemId, int number, String studentEmail) {
        QuizAttempt attempt = requireOwnedInProgress(attemptId, studentEmail);
        if (attempt.getMode() != AttemptMode.PRACTICE) {
            throw new InvalidQuizException("Indiciile sunt disponibile doar în modul practică");
        }
        QuizItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new QuizNotFoundException("QuizItem", itemId));
        if (!item.getQuiz().getId().equals(attempt.getQuiz().getId())) {
            throw new InvalidQuizException("Item does not belong to this quiz");
        }
        List<QuizItemHint> hints = hintRepository.findByItemIdOrderByPosition(itemId);
        if (number < 1 || number > hints.size()) {
            throw new InvalidQuizException("Nu există acest indiciu");
        }
        ItemResponse response = responseRepository.findByAttemptIdAndItemId(attemptId, itemId)
                .orElseGet(() -> new ItemResponse(attempt, item));
        if (number > response.getHintsUsed() + 1) {
            throw new InvalidQuizException("Indiciile se dezvăluie pe rând");
        }
        response.revealHint(number);
        responseRepository.save(response);
        return new HintDto(number, hints.get(number - 1).getText(), hints.size());
    }

    private Map<Long, Integer> hintCounts(Long quizId) {
        return hintRepository.countsByQuizId(quizId).stream()
                .collect(Collectors.toMap(HintCount::itemId, count -> count.total().intValue()));
    }

    /** The hints a practice has already revealed, per item, so a reloaded page shows them again. */
    private List<RevealedHintsDto> revealedHints(List<ItemResponse> responses) {
        return responses.stream()
                .filter(response -> response.getHintsUsed() > 0)
                .map(response -> new RevealedHintsDto(response.getItem().getId(),
                        hintRepository.findByItemIdOrderByPosition(response.getItem().getId()).stream()
                                .limit(response.getHintsUsed())
                                .map(QuizItemHint::getText)
                                .toList()))
                .toList();
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
    /**
     * A student's own uploaded photo, in any state of the attempt (to see it while working and on the result).
     * Somebody else's attempt is a 404 - the same answer as an id that does not exist.
     */
    @Transactional(readOnly = true)
    public Resource getOwnPhoto(Long attemptId, Long itemId, Long photoId, String studentEmail) {
        attemptRepository.findById(attemptId)
                .filter(a -> a.getStudent().getEmail().equals(studentEmail))
                .orElseThrow(() -> new QuizNotFoundException("QuizAttempt", attemptId));
        return getOpenPhotoResource(attemptId, itemId, photoId);
    }

    /** One page of an answer, for the teacher (and, through {@link #getOwnPhoto}, its student). Unknown = 404. */
    @Transactional(readOnly = true)
    public Resource getOpenPhotoResource(Long attemptId, Long itemId, Long photoId) {
        String key = responseRepository.findByAttemptIdAndItemId(attemptId, itemId)
                .flatMap(response -> response.photoKey(photoId))
                .orElseThrow(() -> new QuizNotFoundException("ResponsePhoto", photoId));
        try {
            return fileService.loadImage(quizPhotosDir, key);
        } catch (IOException e) {
            throw new UncheckedIOException("Nu am putut citi poza", e);
        }
    }

    /** Profesorul acordă punctaj manual pentru un subiect OPEN. */
    @Transactional
    public void gradeOpenResponse(Long attemptId, Long itemId, int points, String comment) {
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
        if (!partRepository.findByItemIdOrderByPosition(itemId).isEmpty()) {
            throw new InvalidQuizException("Întrebarea are cerințe a), b): punctajul se dă pe fiecare cerință.");
        }

        // Dacă elevul nu a răspuns nimic, creăm un răspuns gol ca să reținem punctajul (ex: 0)
        ItemResponse response = responseRepository.findByAttemptIdAndItemId(attemptId, itemId)
                .orElseGet(() -> new ItemResponse(attempt, item));

        Integer pointsBefore = response.getAwardedPoints();
        String commentBefore = response.getTeacherComment();
        response.gradeManual(points, comment);
        responseRepository.save(response);

        String commentChange = commentChange(commentBefore, response.getTeacherComment());
        if (Objects.equals(pointsBefore, points) && commentChange == null) {
            return; // saved again unchanged ("Finalizează" re-sends every score): nothing new to journal
        }
        String score = pointsBefore == null || pointsBefore == points
                ? points + " p" : pointsBefore + " → " + points + " p";
        String number = QuestionNumbers.of(item, itemRepository.findByQuizIdOrderByPosition(item.getQuiz().getId()));
        auditLog.record(AuditEvent.of(AuditAction.ANSWER_GRADED, attemptId,
                        AuditText.question(attempt.getQuiz().getTitle(), number))
                .about(attempt.getStudent())
                .details(score + " din " + item.getPoints() + (commentChange == null ? "" : ", " + commentChange)));
    }

    /**
     * Grades a problem with requirements a), b)... one requirement at a time (some may still be missing: a null takes a
     * requirement's points away). The answer's points are the sum once every requirement has some.
     */
    @Transactional
    public void gradeOpenResponseParts(Long attemptId, Long itemId, Map<Long, Integer> partPoints, String comment) {
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new QuizNotFoundException("QuizAttempt", attemptId));
        if (attempt.getStatus() != QuizAttemptStatus.SUBMITTED) {
            throw new InvalidQuizException("Attempt-ul trebuie să fie SUBMITTED pentru corectură");
        }
        QuizItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new QuizNotFoundException("QuizItem", itemId));
        if (item.getType() != QuizItemType.OPEN || !item.getQuiz().getId().equals(attempt.getQuiz().getId())) {
            throw new InvalidQuizException("Item does not belong to this quiz");
        }
        List<QuizItemPart> parts = partRepository.findByItemIdOrderByPosition(itemId);
        if (parts.isEmpty()) {
            throw new InvalidQuizException("Această întrebare nu are cerințe a), b): dă un singur punctaj.");
        }
        for (Map.Entry<Long, Integer> grade : partPoints.entrySet()) {
            QuizItemPart part = parts.stream().filter(p -> p.getId().equals(grade.getKey())).findFirst()
                    .orElseThrow(() -> new InvalidQuizException("Cerința nu ține de această întrebare."));
            if (grade.getValue() != null && grade.getValue() > part.getPoints()) {
                throw new InvalidQuizException("La cerința " + part.label() + ") se pot da cel mult "
                        + RoCount.of(part.getPoints(), "punct", "puncte") + ".");
            }
        }
        ItemResponse response = responseRepository.findByAttemptIdAndItemId(attemptId, itemId)
                .orElseGet(() -> new ItemResponse(attempt, item));
        Map<Long, Integer> before = new java.util.HashMap<>(response.getPartPoints());
        String commentBefore = response.getTeacherComment();
        response.gradeParts(parts, partPoints, comment);
        responseRepository.save(response);

        String commentChange = commentChange(commentBefore, response.getTeacherComment());
        if (before.equals(response.getPartPoints()) && commentChange == null) {
            return; // saved again unchanged (autosave): nothing new to journal
        }
        String perPart = parts.stream()
                .filter(p -> response.getPartPoints().containsKey(p.getId()))
                .map(p -> p.label() + ") " + response.getPartPoints().get(p.getId()) + "/" + p.getPoints())
                .collect(Collectors.joining(", "));
        String number = QuestionNumbers.of(item, itemRepository.findByQuizIdOrderByPosition(item.getQuiz().getId()));
        auditLog.record(AuditEvent.of(AuditAction.ANSWER_GRADED, attemptId,
                        AuditText.question(attempt.getQuiz().getTitle(), number))
                .about(attempt.getStudent())
                .details((perPart.isEmpty() ? "fără punctaj" : perPart) + " p"
                        + (response.getAwardedPoints() == null ? "" : " = " + response.getAwardedPoints() + " din " + item.getPoints())
                        + (commentChange == null ? "" : ", " + commentChange)));
    }

    /** The requirements a), b)... of each item of a quiz, by item id (most items have none). */
    private Map<Long, List<QuizItemPart>> partsOf(List<QuizItem> items) {
        if (items.isEmpty()) {
            return Map.of();
        }
        return partRepository.findByItemIdInOrderByItemIdAscPositionAsc(items.stream().map(QuizItem::getId).toList())
                .stream().collect(Collectors.groupingBy(p -> p.getItem().getId()));
    }

    /** "comentariu adăugat / modificat / șters", or null when the comment stayed the same. */
    private static String commentChange(String before, String after) {
        if (Objects.equals(before, after)) {
            return null;
        }
        return "comentariu " + (after == null ? "șters" : before == null ? "adăugat" : "modificat");
    }

    /**
     * The teacher's comment on a whole paper - while grading it, or later on one that is already graded (an
     * all-grilă test is graded the moment it is handed in). Not before it is handed in, never on a practice.
     */
    @Transactional
    public void commentOnAttempt(Long attemptId, String comment) {
        QuizAttempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new QuizNotFoundException("QuizAttempt", attemptId));
        if (attempt.getMode() != AttemptMode.TEST || attempt.getStatus() == QuizAttemptStatus.IN_PROGRESS) {
            throw new InvalidQuizException("Poți comenta doar o lucrare predată");
        }
        String before = attempt.getTeacherComment();
        attempt.commentOverall(comment);
        attemptRepository.save(attempt);
        String change = commentChange(before, attempt.getTeacherComment());
        if (change != null) {
            auditLog.record(AuditEvent.of(AuditAction.PAPER_COMMENTED, attemptId, attempt.getQuiz().getTitle())
                    .about(attempt.getStudent()).details(change));
        }
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
        int maxScore = 0;
        for (QuizItem item : items) {
            maxScore += item.getPoints();
            ItemResponse response = responses.get(item.getId());

            if (item.getType() == QuizItemType.OPEN) {
                if (response == null || response.getAwardedPoints() == null) {
                    throw new InvalidQuizException("Întrebarea " + item.getId() + " nu a fost corectată!");
                }
            }

            if (response != null && response.getAwardedPoints() != null) {
                totalScore += response.getAwardedPoints();
            }
        }

        attempt.markGraded(totalScore);
        attemptRepository.save(attempt);
        evictGradingCaches(attempt);
        auditLog.record(AuditEvent.of(AuditAction.PAPER_GRADED, attemptId, attempt.getQuiz().getTitle())
                .about(attempt.getStudent()).details(totalScore + " p din " + maxScore));
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

    /** The most graded papers one page holds (the browser asks for the next one with "Mai vechi"). */
    public static final int GRADED_PAGE = 50;

    /** One page of graded papers, newest first; {@code teacherId} null = every test. See {@code findGradedPage}. */
    @Transactional(readOnly = true)
    public List<AdminAttemptSummaryDto> listGradedPage(Long teacherId, Long beforeId, int limit) {
        int size = Math.clamp(limit, 1, 200);
        List<QuizAttempt> attempts = attemptRepository.findGradedPage(teacherId, beforeId,
                Limit.of(size));
        Map<Long, Integer> maxScores = maxScores(attempts);
        return attempts.stream()
                .map(a -> AdminAttemptSummaryDto.from(a, maxScores.getOrDefault(a.getQuiz().getId(), 0)))
                .toList();
    }

    /** The teacher's grading queue (Q10): attempts in one status, oldest submission first. */
    @Transactional(readOnly = true)
    public List<AdminAttemptSummaryDto> listForGrading(QuizAttemptStatus status) {
        List<QuizAttempt> attempts = attemptRepository.findByStatusForGrading(status);
        Map<Long, Integer> maxScores = maxScores(attempts);
        return attempts.stream()
                .map(a -> AdminAttemptSummaryDto.from(a, maxScores.getOrDefault(a.getQuiz().getId(), 0)))
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

        Map<Long, List<QuizItemPart>> partsByItem = partsOf(items);
        int maxScore = 0;
        List<AdminItemReviewDto> reviews = new ArrayList<>();
        for (QuizItem item : items) {
            maxScore += item.getPoints();
            ItemResponse response = byItem.get(item.getId());
            String selectedText = null;
            String correctText = null;
            String selectedLetter = null;
            String correctLetter = null;
            if (item.getType() == QuizItemType.SINGLE_CHOICE) {
                List<QuizOption> options = optionRepository.findByItemIdOrderByPosition(item.getId());
                QuizOption correctOption = options.stream().filter(QuizOption::isCorrect).findFirst().orElse(null);
                if (correctOption != null) {
                    correctText = correctOption.getText();
                    correctLetter = letterOf(options, correctOption);
                }
                if (response != null && response.getSelectedOption() != null) {
                    selectedText = response.getSelectedOption().getText();
                    selectedLetter = letterOf(options, response.getSelectedOption());
                }
            }
            reviews.add(new AdminItemReviewDto(
                    item.getId(), item.getPosition(), item.getType(), item.getStatement(), item.getPoints(),
                    item.getSolution(), selectedText, correctText,
                    response == null ? null : response.getCorrect(),
                    response == null ? null : response.getAwardedPoints(),
                    response != null && response.hasPhotos(),
                    response == null ? null : response.getTeacherComment(),
                    item.getSection(),
                    item.getImageKey(),
                    response == null ? List.of() : response.photoIds(),
                    PartDtos.GradedPartDto.of(partsByItem.getOrDefault(item.getId(), List.of()), response),
                    selectedLetter, correctLetter));
        }
        return new AdminAttemptDetailDto(attempt.getId(), attempt.getQuiz().getTitle(),
                attempt.getStudent().getFullName(), attempt.getStatus(), attempt.getSubmittedAt(),
                attempt.getScore(), maxScore, reviews, attempt.getTeacherComment());
    }

    /**
     * The option's letter as printed on a paper test - a), b), c)... by its place among the item's options (ordered
     * by position), so "răspunsul b" means the same thing on screen, on paper and in the barem. Null if not found.
     */
    static String letterOf(List<QuizOption> options, QuizOption option) {
        for (int i = 0; i < options.size(); i++) {
            if (Objects.equals(options.get(i).getId(), option.getId())) {
                return String.valueOf((char) ('a' + i));
            }
        }
        return null;
    }
}
