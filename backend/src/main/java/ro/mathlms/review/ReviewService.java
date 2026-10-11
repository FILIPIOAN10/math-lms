package ro.mathlms.review;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.content.ContentNotFoundException;
import ro.mathlms.content.Enrollment;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.quiz.AttemptMode;
import ro.mathlms.quiz.ItemResponse;
import ro.mathlms.quiz.ItemResponseRepository;
import ro.mathlms.quiz.QuizAttempt;
import ro.mathlms.quiz.QuizAttemptRepository;
import ro.mathlms.quiz.QuizAttemptStatus;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.quiz.QuizOption;
import ro.mathlms.quiz.QuizOptionRepository;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.quiz.QuizStatus;
import ro.mathlms.review.LeitnerEngine.Card;
import ro.mathlms.review.LeitnerEngine.Event;
import ro.mathlms.review.ReviewDtos.ReviewOptionDto;
import ro.mathlms.review.ReviewDtos.ReviewQuestionDto;
import ro.mathlms.review.ReviewDtos.ReviewResultDto;
import ro.mathlms.review.ReviewDtos.ReviewStatusDto;
import ro.mathlms.review.ReviewDtos.ReviewTodayDto;
import ro.mathlms.review.ReviewDtos.StudentReviewStatusDto;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * E4 spaced repetition - the daily review. Rules, decided with the teacher:
 * <ul>
 *   <li>the pool is every MULTIPLE-CHOICE question the student answered in a practice or in a handed-in test (its
 *       answers are on their result page anyway) - never one of a test still being written, never an open item;</li>
 *   <li>Leitner boxes of 1, 3, 7, 14 and 30 days ({@link LeitnerEngine}); the reviews themselves move the boxes too;</li>
 *   <li>at most {@value #DAILY_LIMIT} a day, the longest-waiting first; a quiz the teacher unpublished leaves the queue.</li>
 * </ul>
 * Nothing about the queue is stored: each question's box and due date are replayed from the answers, so they can never
 * disagree with them. Only the review answers themselves are written ({@code review_answers}, V22).
 */
@Service
public class ReviewService {

    public static final int DAILY_LIMIT = 10;
    /** The school's day: a review at 00:30 in Bucharest belongs to that day, not to the previous UTC day. */
    public static final ZoneId ZONE = ZoneId.of("Europe/Bucharest");

    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final QuizRepository quizRepository;
    private final QuizAttemptRepository attemptRepository;
    private final QuizItemRepository itemRepository;
    private final QuizOptionRepository optionRepository;
    private final ItemResponseRepository responseRepository;
    private final ReviewAnswerRepository reviewAnswerRepository;
    private final Clock clock;

    public ReviewService(UserRepository userRepository, EnrollmentRepository enrollmentRepository,
                         QuizRepository quizRepository, QuizAttemptRepository attemptRepository,
                         QuizItemRepository itemRepository, QuizOptionRepository optionRepository,
                         ItemResponseRepository responseRepository, ReviewAnswerRepository reviewAnswerRepository,
                         Clock clock) {
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.quizRepository = quizRepository;
        this.attemptRepository = attemptRepository;
        this.itemRepository = itemRepository;
        this.optionRepository = optionRepository;
        this.responseRepository = responseRepository;
        this.reviewAnswerRepository = reviewAnswerRepository;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- the student

    @Transactional(readOnly = true)
    public ReviewTodayDto today(String studentEmail) {
        User student = requireStudent(studentEmail);
        State state = stateOf(List.of(student.getId())).get(student.getId());
        List<ReviewQuestionDto> questions = state.queue().stream()
                .map(itemId -> question(state.items().get(itemId), state.cards().get(itemId).box()))
                .toList();
        return new ReviewTodayDto(DAILY_LIMIT, state.doneToday(), questions.size(), state.streak(),
                state.cards().size(), state.mastered(), questions);
    }

    /** Answers one question of today's queue; anything else (not due, over the limit, not a card) is a 409. */
    @Transactional
    public ReviewResultDto answer(String studentEmail, Long itemId, Long optionId) {
        User student = requireStudent(studentEmail);
        State state = stateOf(List.of(student.getId())).get(student.getId());
        if (!state.queue().contains(itemId)) {
            throw new InvalidReviewException("Întrebarea nu e de recapitulat azi.");
        }
        QuizItem item = state.items().get(itemId);
        QuizOption chosen = optionRepository.findById(optionId)
                .filter(o -> o.getItem().getId().equals(itemId))
                .orElseThrow(() -> new InvalidReviewException("Varianta nu aparține acestei întrebări."));
        Instant now = Instant.now(clock);
        reviewAnswerRepository.save(new ReviewAnswer(student, item, chosen, chosen.isCorrect(), now));

        Card before = state.cards().get(itemId);
        Card after = LeitnerEngine.next(before, chosen.isCorrect(), today());
        Long correctOptionId = optionRepository.findByItemIdOrderByPosition(itemId).stream()
                .filter(QuizOption::isCorrect).map(QuizOption::getId).findFirst().orElse(null);
        return new ReviewResultDto(chosen.isCorrect(), correctOptionId, item.getSolution(), before.box(), after.box(),
                after.dueOn());
    }

    // ---------------------------------------------------------------- parent and teacher

    /** One student's review status (the parent's view goes through ParentService, which checks the child is theirs). */
    @Transactional(readOnly = true)
    public ReviewStatusDto status(Long studentId) {
        State s = stateOf(List.of(studentId)).get(studentId);
        return new ReviewStatusDto(s.doneToday(), s.queue().size(), s.streak(), s.cards().size(), s.mastered(),
                s.lastReviewOn());
    }

    /** Every student of a class, alphabetical: who keeps up with their reviews. */
    @Transactional(readOnly = true)
    public List<StudentReviewStatusDto> forClass(Long schoolClassId) {
        List<User> students = enrollmentRepository.findBySchoolClassIdFetchStudent(schoolClassId).stream()
                .map(Enrollment::getStudent)
                .filter(s -> !s.isErased())
                .toList();
        if (students.isEmpty()) {
            return List.of();
        }
        Map<Long, State> states = stateOf(students.stream().map(User::getId).toList());
        return students.stream().map(student -> {
            State s = states.get(student.getId());
            return new StudentReviewStatusDto(student.getId(), student.getFullName(), s.doneToday(), s.queue().size(),
                    s.streak(), s.cards().size(), s.mastered(), s.lastReviewOn());
        }).toList();
    }

    // ---------------------------------------------------------------- the replay

    /** One student's review state today. {@code queue} = item ids to review today, in order, within the limit. */
    private record State(Map<Long, Card> cards, Map<Long, QuizItem> items, List<Long> queue, int doneToday,
                         int streak, int mastered, LocalDate lastReviewOn) {
    }

    private Map<Long, State> stateOf(List<Long> studentIds) {
        LocalDate today = today();
        Map<Long, List<Event>> events = new HashMap<>();
        Map<Long, QuizItem> items = new HashMap<>();
        Map<Long, List<LocalDate>> reviewDays = new HashMap<>();
        studentIds.forEach(id -> {
            events.put(id, new ArrayList<>());
            reviewDays.put(id, new ArrayList<>());
        });

        // 1. Multiple-choice answers from practices (any state) and handed-in tests.
        List<QuizAttempt> attempts = attemptRepository.findByStudentIdsFetchQuiz(studentIds).stream()
                .filter(a -> a.getMode() == AttemptMode.PRACTICE || a.getStatus() != QuizAttemptStatus.IN_PROGRESS)
                .toList();
        if (!attempts.isEmpty()) {
            Map<Long, QuizItem> quizItems = itemRepository
                    .findByQuizIdIn(attempts.stream().map(a -> a.getQuiz().getId()).collect(Collectors.toSet())).stream()
                    .filter(i -> i.getType() == QuizItemType.SINGLE_CHOICE)
                    .collect(Collectors.toMap(QuizItem::getId, Function.identity()));
            items.putAll(quizItems);
            Map<Long, QuizAttempt> byId = attempts.stream().collect(Collectors.toMap(QuizAttempt::getId, Function.identity()));
            for (ItemResponse r : responseRepository.findByAttemptIdIn(byId.keySet())) {
                QuizAttempt attempt = byId.get(r.getAttempt().getId());
                if (!quizItems.containsKey(r.getItem().getId()) || r.getSelectedOption() == null || r.getCorrect() == null) {
                    continue; // an open item, a hint-only row, or a test answer not marked yet
                }
                Instant at = attempt.getMode() == AttemptMode.TEST && attempt.getSubmittedAt() != null
                        ? attempt.getSubmittedAt() : attempt.getStartedAt();
                events.get(attempt.getStudent().getId()).add(new Event(r.getItem().getId(), r.getCorrect(), at));
            }
        }

        // 2. The reviews themselves.
        for (ReviewAnswer answer : reviewAnswerRepository.findByStudentIdIn(studentIds)) {
            Long studentId = answer.getStudent().getId();
            items.putIfAbsent(answer.getItem().getId(), answer.getItem());
            events.get(studentId).add(new Event(answer.getItem().getId(), answer.isCorrect(), answer.getAnsweredAt()));
            reviewDays.get(studentId).add(answer.getAnsweredAt().atZone(ZONE).toLocalDate());
        }

        Set<Long> unpublishedQuizzes = unpublishedQuizIds(items.values());
        Map<Long, State> states = new HashMap<>();
        for (Long studentId : studentIds) {
            Map<Long, Card> cards = LeitnerEngine.replay(events.get(studentId), ZONE);
            List<LocalDate> days = reviewDays.get(studentId);
            int doneToday = (int) days.stream().filter(today::equals).count();
            List<Long> queue = cards.entrySet().stream()
                    .filter(e -> e.getValue().isDue(today))
                    .filter(e -> items.containsKey(e.getKey())
                            && !unpublishedQuizzes.contains(items.get(e.getKey()).getQuiz().getId()))
                    .sorted(Comparator.comparing((Map.Entry<Long, Card> e) -> e.getValue().dueOn())
                            .thenComparing(e -> e.getValue().box())
                            .thenComparing(Map.Entry::getKey))
                    .limit(Math.max(0, DAILY_LIMIT - doneToday))
                    .map(Map.Entry::getKey)
                    .toList();
            int mastered = (int) cards.values().stream().filter(c -> c.box() == LeitnerEngine.LAST_BOX).count();
            LocalDate last = days.stream().max(LocalDate::compareTo).orElse(null);
            states.put(studentId, new State(cards, items, queue, doneToday, streak(days, today), mastered, last));
        }
        return states;
    }

    private Set<Long> unpublishedQuizIds(java.util.Collection<QuizItem> items) {
        Set<Long> quizIds = items.stream().map(i -> i.getQuiz().getId()).collect(Collectors.toSet());
        if (quizIds.isEmpty()) {
            return Set.of();
        }
        return quizRepository.findAllById(quizIds).stream()
                .filter(q -> q.getStatus() != QuizStatus.PUBLISHED)
                .map(q -> q.getId())
                .collect(Collectors.toSet());
    }

    /** Consecutive days with a review, counting back from today - or from yesterday while today is still open. */
    static int streak(List<LocalDate> reviewDays, LocalDate today) {
        TreeSet<LocalDate> days = new TreeSet<>(reviewDays);
        LocalDate day = days.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        while (days.contains(day)) {
            streak++;
            day = day.minusDays(1);
        }
        return streak;
    }

    private ReviewQuestionDto question(QuizItem item, int box) {
        List<ReviewOptionDto> options = optionRepository.findByItemIdOrderByPosition(item.getId()).stream()
                .map(o -> new ReviewOptionDto(o.getId(), o.getPosition(), o.getText()))
                .toList();
        return new ReviewQuestionDto(item.getId(), item.getQuiz().getTitle(), item.getStatement(), options, box);
    }

    private LocalDate today() {
        return Instant.now(clock).atZone(ZONE).toLocalDate();
    }

    private User requireStudent(String email) {
        return userRepository.findByEmail(email)
                .filter(u -> u.getRole() == Role.STUDENT)
                .orElseThrow(() -> new ContentNotFoundException("Nu există un elev cu acest cont."));
    }
}
