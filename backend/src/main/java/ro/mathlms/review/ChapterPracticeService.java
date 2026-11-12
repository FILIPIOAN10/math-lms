package ro.mathlms.review;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.content.ContentNotFoundException;
import ro.mathlms.enchapter.EnChapter;
import ro.mathlms.enchapter.EnChapterNotFoundException;
import ro.mathlms.enchapter.EnChapterRepository;
import ro.mathlms.quiz.AnswerKeys;
import ro.mathlms.quiz.AttemptMode;
import ro.mathlms.quiz.EnChapterLinks;
import ro.mathlms.quiz.ItemResponse;
import ro.mathlms.quiz.ItemResponseRepository;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAttempt;
import ro.mathlms.quiz.QuizAttemptRepository;
import ro.mathlms.quiz.QuizAttemptService;
import ro.mathlms.quiz.QuizAttemptStatus;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.quiz.QuizOption;
import ro.mathlms.quiz.QuizItemHintRepository;
import ro.mathlms.quiz.QuizOptionRepository;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.review.ReviewDtos.ChapterPracticeDto;
import ro.mathlms.review.ReviewDtos.ChapterPracticeResultDto;
import ro.mathlms.review.ReviewDtos.ReviewQuestionDto;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Practise one EN chapter from its page (Khan-style "practice this unit"): up to {@value #SIZE} grile linked to the
 * chapter (or to one of its sub-chapters), the ones the student got wrong last time first, then the ones they never
 * answered, then the rest. Each answer is checked at once, with the solution.
 * <p>
 * The pool is only questions whose answers the student may already see: a test they handed in and cannot hand in again
 * (its result page shows them, see {@link AnswerKeys}) or a test the teacher opened for practice. So it never gives away
 * a test still to be written - nor one with a retake left. Nothing is
 * stored: the answers do not move the daily review's boxes nor the grades, they only tell the student how they stand.
 */
@Service
public class ChapterPracticeService {

    public static final int SIZE = 5;

    private final UserRepository userRepository;
    private final EnChapterRepository chapterRepository;
    private final QuizAttemptService quizAttemptService;
    private final QuizAttemptRepository attemptRepository;
    private final QuizItemRepository itemRepository;
    private final QuizOptionRepository optionRepository;
    private final PracticeQuestions practiceQuestions;
    private final ItemResponseRepository responseRepository;
    private final ReviewAnswerRepository reviewAnswerRepository;
    private final EnChapterLinks enChapterLinks;
    private final AnswerKeys answerKeys;

    public ChapterPracticeService(UserRepository userRepository, EnChapterRepository chapterRepository,
                                  QuizAttemptService quizAttemptService, QuizAttemptRepository attemptRepository,
                                  QuizItemRepository itemRepository, QuizOptionRepository optionRepository,
                                  ItemResponseRepository responseRepository, ReviewAnswerRepository reviewAnswerRepository,
                                  EnChapterLinks enChapterLinks, QuizRepository quizRepository,
                                  QuizItemHintRepository hintRepository) {
        this.practiceQuestions = new PracticeQuestions(optionRepository, hintRepository);
        this.userRepository = userRepository;
        this.chapterRepository = chapterRepository;
        this.quizAttemptService = quizAttemptService;
        this.attemptRepository = attemptRepository;
        this.itemRepository = itemRepository;
        this.optionRepository = optionRepository;
        this.responseRepository = responseRepository;
        this.reviewAnswerRepository = reviewAnswerRepository;
        this.enChapterLinks = enChapterLinks;
        this.answerKeys = new AnswerKeys(quizRepository);
    }

    @Transactional(readOnly = true)
    public ChapterPracticeDto practice(String studentEmail, Long chapterId) {
        User student = requireStudent(studentEmail);
        EnChapter chapter = requireChapter(chapterId);
        List<QuizAttempt> attempts = attemptRepository.findByStudentIdOrderByStartedAtDesc(student.getId());
        List<QuizItem> pool = pool(student, chapter, attempts, null);
        Map<Long, Boolean> lastVerdict = lastVerdicts(student, pool, attempts);
        List<ReviewQuestionDto> questions = practiceQuestions.of(pool.stream()
                .sorted(Comparator.comparingInt((QuizItem i) -> rank(lastVerdict.get(i.getId())))
                        .thenComparing(i -> i.getQuiz().getId())
                        .thenComparingInt(QuizItem::getPosition))
                .limit(SIZE)
                .toList(), item -> 0);
        return new ChapterPracticeDto(chapter.getId(), chapter.getCode(), chapter.getTitle(), pool.size(), questions);
    }

    /** Checks one answer; a question that is not in this chapter's practice for this student is a 409. */
    @Transactional(readOnly = true)
    public ChapterPracticeResultDto answer(String studentEmail, Long chapterId, Long itemId, Long optionId) {
        User student = requireStudent(studentEmail);
        EnChapter chapter = requireChapter(chapterId);
        List<QuizAttempt> attempts = attemptRepository.findByStudentIdOrderByStartedAtDesc(student.getId());
        // Only the question's own test is checked, not the chapter's whole pool rebuilt for every answer.
        Long quizId = itemRepository.findById(itemId).map(i -> i.getQuiz().getId()).orElse(null);
        QuizItem item = (quizId == null ? List.<QuizItem>of() : pool(student, chapter, attempts, quizId)).stream()
                .filter(i -> i.getId().equals(itemId)).findFirst()
                .orElseThrow(() -> new InvalidReviewException("Întrebarea nu face parte din exersarea acestui capitol."));
        List<QuizOption> options = optionRepository.findByItemIdOrderByPosition(itemId);
        QuizOption chosen = options.stream().filter(o -> o.getId().equals(optionId)).findFirst()
                .orElseThrow(() -> new InvalidReviewException("Varianta nu aparține acestei întrebări."));
        Long correctOptionId = options.stream().filter(QuizOption::isCorrect).map(QuizOption::getId).findFirst().orElse(null);
        return new ChapterPracticeResultDto(chosen.isCorrect(), correctOptionId, item.getSolution(),
                chosen.isCorrect() ? null : chosen.getMistake());
    }

    /** Wrong last time first (0), never answered (1), right last time (2). */
    private static int rank(Boolean lastCorrect) {
        return lastCorrect == null ? 1 : lastCorrect ? 2 : 0;
    }

    /**
     * The grile of the chapter the student may practise: from published tests they can see, which allow practice or
     * which they handed in and cannot hand in again - never one with a test of theirs still running on it.
     * {@code onlyQuizId}: just that test's grile (checking one answer), null = every test.
     */
    private List<QuizItem> pool(User student, EnChapter chapter, List<QuizAttempt> attempts, Long onlyQuizId) {
        Set<Long> handedIn = attempts.stream()
                .filter(a -> a.getMode() == AttemptMode.TEST && a.getStatus() != QuizAttemptStatus.IN_PROGRESS)
                .map(a -> a.getQuiz().getId()).collect(Collectors.toSet());
        Set<Long> running = attempts.stream()
                .filter(a -> a.getMode() == AttemptMode.TEST && a.getStatus() == QuizAttemptStatus.IN_PROGRESS)
                .map(a -> a.getQuiz().getId()).collect(Collectors.toSet());
        Set<Long> keyHidden = answerKeys.hiddenQuizzes(attempts).getOrDefault(student.getId(), Set.of());
        Set<Long> quizIds = quizAttemptService.listPublished(student.getEmail()).stream()
                .filter(q -> onlyQuizId == null || onlyQuizId.equals(q.getId()))
                .filter(q -> !running.contains(q.getId()))
                .filter(q -> q.isPracticeAllowed() || (handedIn.contains(q.getId()) && !keyHidden.contains(q.getId())))
                .map(Quiz::getId)
                .collect(Collectors.toSet());
        if (quizIds.isEmpty()) {
            return List.of();
        }
        Map<Long, List<QuizItem>> byQuiz = itemRepository.findByQuizIdIn(quizIds).stream()
                .collect(Collectors.groupingBy(i -> i.getQuiz().getId()));
        EnChapterLinks.Snapshot links = enChapterLinks.snapshot();
        List<QuizItem> pool = new ArrayList<>();
        for (List<QuizItem> items : byQuiz.values()) {
            List<QuizItem> ordered = items.stream().sorted(Comparator.comparingInt(QuizItem::getPosition)).toList();
            EnChapterLinks.Resolved resolved = links.resolve(ordered); // the numbering needs every question of the quiz
            for (QuizItem item : ordered) {
                if (item.getType() != QuizItemType.SINGLE_CHOICE) continue; // checked at once only when it is a grilă
                EnChapter linked = resolved.itemChapter(item);
                if (linked != null && (linked.getId().equals(chapter.getId())
                        || Objects.equals(linked.getParentId(), chapter.getId()))) {
                    pool.add(item);
                }
            }
        }
        return pool;
    }

    /** Whether the student's latest answer to each question (test, practice or daily review) was right. */
    private Map<Long, Boolean> lastVerdicts(User student, List<QuizItem> pool, List<QuizAttempt> attempts) {
        Set<Long> itemIds = pool.stream().map(QuizItem::getId).collect(Collectors.toSet());
        Map<Long, Instant> when = new HashMap<>();
        Map<Long, Boolean> verdict = new HashMap<>();
        if (itemIds.isEmpty()) {
            return verdict;
        }
        Map<Long, QuizAttempt> byId = attempts.stream().collect(Collectors.toMap(QuizAttempt::getId, a -> a));
        if (!byId.isEmpty()) {
            for (ItemResponse r : responseRepository.findByAttemptIdIn(byId.keySet())) {
                if (!itemIds.contains(r.getItem().getId()) || r.getCorrect() == null) continue;
                QuizAttempt a = byId.get(r.getAttempt().getId());
                keepLatest(when, verdict, r.getItem().getId(), a.getStartedAt(), r.getCorrect());
            }
        }
        for (ReviewAnswer answer : reviewAnswerRepository.findByStudentIdIn(List.of(student.getId()))) {
            if (itemIds.contains(answer.getItem().getId())) {
                keepLatest(when, verdict, answer.getItem().getId(), answer.getAnsweredAt(), answer.isCorrect());
            }
        }
        return verdict;
    }

    private static void keepLatest(Map<Long, Instant> when, Map<Long, Boolean> verdict, Long itemId, Instant at,
                                   boolean correct) {
        Instant seen = when.get(itemId);
        if (seen == null || at.isAfter(seen)) {
            when.put(itemId, at);
            verdict.put(itemId, correct);
        }
    }

    private EnChapter requireChapter(Long chapterId) {
        return chapterRepository.findById(chapterId)
                .orElseThrow(() -> new EnChapterNotFoundException("EnChapter", chapterId));
    }

    private User requireStudent(String email) {
        return userRepository.findByEmail(email)
                .filter(u -> u.getRole() == Role.STUDENT)
                .orElseThrow(() -> new ContentNotFoundException("Nu există un elev cu acest cont."));
    }
}
