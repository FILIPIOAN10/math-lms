package ro.mathlms.mastery;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.content.Chapter;
import ro.mathlms.content.ChapterRepository;
import ro.mathlms.content.ContentNotFoundException;
import ro.mathlms.content.Enrollment;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.NaturalOrder;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.mastery.MasteryCalculator.Answer;
import ro.mathlms.mastery.MasteryCalculator.Tally;
import ro.mathlms.mastery.MasteryDtos.CellDto;
import ro.mathlms.mastery.MasteryDtos.ChapterMasteryDto;
import ro.mathlms.mastery.MasteryDtos.ChapterRefDto;
import ro.mathlms.mastery.MasteryDtos.ClassMasteryDto;
import ro.mathlms.mastery.MasteryDtos.StudentRowDto;
import ro.mathlms.quiz.AttemptMode;
import ro.mathlms.quiz.ItemResponse;
import ro.mathlms.quiz.ItemResponseRepository;
import ro.mathlms.quiz.QuizAttempt;
import ro.mathlms.quiz.QuizAttemptRepository;
import ro.mathlms.quiz.QuizAttemptStatus;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.review.ReviewAnswer;
import ro.mathlms.review.ReviewAnswerRepository;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The mastery map (E6): how well each student does in each chapter, from the questions tagged with it (the question's
 * own chapter, else its quiz's). Rules, decided with the teacher:
 * <ul>
 *   <li>graded tests AND practice count; for each question only the student's <b>latest</b> scored answer;</li>
 *   <li>a handed-in test counts every question - an unanswered one is a 0 (as on the result page); an open answer
 *       still waiting for the teacher does not count yet; a test still being written does not count at all;</li>
 *   <li>a practice counts each answered multiple-choice question at once (practice open answers are never scored);</li>
 *   <li>a daily-review answer (E4) counts like a practice answer.</li>
 * </ul>
 * Everything is loaded in four queries (attempts, items, answers, chapters) and computed by {@link MasteryCalculator}.
 */
@Service
public class MasteryService {

    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final ChapterRepository chapterRepository;
    private final QuizAttemptRepository attemptRepository;
    private final QuizItemRepository itemRepository;
    private final ItemResponseRepository responseRepository;
    private final ReviewAnswerRepository reviewAnswerRepository;

    public MasteryService(UserRepository userRepository, EnrollmentRepository enrollmentRepository,
                          SchoolClassRepository schoolClassRepository, ChapterRepository chapterRepository,
                          QuizAttemptRepository attemptRepository, QuizItemRepository itemRepository,
                          ItemResponseRepository responseRepository, ReviewAnswerRepository reviewAnswerRepository) {
        this.reviewAnswerRepository = reviewAnswerRepository;
        this.userRepository = userRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.chapterRepository = chapterRepository;
        this.attemptRepository = attemptRepository;
        this.itemRepository = itemRepository;
        this.responseRepository = responseRepository;
    }

    private static final Comparator<Chapter> CHAPTER_ORDER = Comparator
            .comparing((Chapter c) -> c.getBook().getSchoolClass().getName(), NaturalOrder.BY_NAME)
            .thenComparing(c -> c.getBook().getTitle(), NaturalOrder.BY_NAME)
            .thenComparing(Chapter::getTitle, NaturalOrder.BY_NAME);

    /**
     * One student's map: every chapter of their classes (a chapter not practised yet has no percent), plus any other
     * chapter they have answers in (a quiz for every student may be tagged with another class's chapter).
     */
    @Transactional(readOnly = true)
    public List<ChapterMasteryDto> forStudent(String studentEmail) {
        User student = userRepository.findByEmail(studentEmail).orElse(null);
        if (student == null || student.getRole() != Role.STUDENT) {
            return List.of();
        }
        Map<Long, Tally> tallies = MasteryCalculator.latestPerQuestion(answersOf(List.of(student.getId())))
                .getOrDefault(student.getId(), Map.of());

        Set<Long> chapterIds = new LinkedHashSet<>(tallies.keySet());
        List<Long> classIds = enrollmentRepository.findClassIdsByStudentId(student.getId());
        List<Chapter> chapters = new ArrayList<>(classIds.isEmpty() ? List.of() : chapterRepository.findByClassIdsFetched(classIds));
        chapters.forEach(c -> chapterIds.remove(c.getId()));
        if (!chapterIds.isEmpty()) {
            chapters.addAll(chapterRepository.findByIdsFetched(chapterIds));
        }
        return chapters.stream()
                .sorted(CHAPTER_ORDER)
                .map(c -> {
                    Tally t = tallies.getOrDefault(c.getId(), new Tally(0, 0, 0));
                    return new ChapterMasteryDto(c.getId(), c.getTitle(), c.getBook().getTitle(),
                            c.getBook().getSchoolClass().getName(), t.earned(), t.possible(), t.percent(), t.questions());
                })
                .toList();
    }

    /** The teacher's grid: the class's students (alphabetical) × the class's chapters (book, then chapter order). */
    @Transactional(readOnly = true)
    public ClassMasteryDto forClass(Long schoolClassId) {
        if (!schoolClassRepository.existsById(schoolClassId)) {
            throw new ContentNotFoundException("SchoolClass", schoolClassId);
        }
        List<Chapter> chapters = chapterRepository.findByClassIdsFetched(List.of(schoolClassId)).stream()
                .sorted(CHAPTER_ORDER).toList();
        List<User> students = enrollmentRepository.findBySchoolClassIdFetchStudent(schoolClassId).stream()
                .map(Enrollment::getStudent)
                .filter(s -> !s.isErased())
                .toList();
        Map<Long, Map<Long, Tally>> tallies = MasteryCalculator.latestPerQuestion(
                answersOf(students.stream().map(User::getId).toList()));

        List<StudentRowDto> rows = students.stream().map(s -> {
            Map<Long, Tally> mine = tallies.getOrDefault(s.getId(), Map.of());
            List<CellDto> cells = chapters.stream().map(c -> {
                Tally t = mine.getOrDefault(c.getId(), new Tally(0, 0, 0));
                return new CellDto(c.getId(), t.percent(), t.questions());
            }).toList();
            return new StudentRowDto(s.getId(), s.getFullName(), cells);
        }).toList();
        return new ClassMasteryDto(
                chapters.stream().map(c -> new ChapterRefDto(c.getId(), c.getTitle(), c.getBook().getTitle())).toList(),
                rows);
    }

    /** The scored answers of these students, one per (attempt, question), with the chapter each one counts toward. */
    private List<Answer> answersOf(List<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        // A test still being written does not count; a practice counts answer by answer.
        List<QuizAttempt> attempts = attemptRepository.findByStudentIdsFetchQuiz(studentIds).stream()
                .filter(a -> a.getMode() == AttemptMode.PRACTICE || a.getStatus() != QuizAttemptStatus.IN_PROGRESS)
                .toList();
        List<Answer> answers = new ArrayList<>(reviewAnswersOf(studentIds));
        if (attempts.isEmpty()) {
            return answers;
        }
        Map<Long, List<QuizItem>> itemsByQuiz = itemRepository
                .findByQuizIdIn(attempts.stream().map(a -> a.getQuiz().getId()).collect(Collectors.toSet())).stream()
                .collect(Collectors.groupingBy(i -> i.getQuiz().getId()));
        Map<Long, Map<Long, ItemResponse>> responses = responseRepository
                .findByAttemptIdIn(attempts.stream().map(QuizAttempt::getId).toList()).stream()
                .collect(Collectors.groupingBy(r -> r.getAttempt().getId(),
                        Collectors.toMap(r -> r.getItem().getId(), Function.identity())));

        for (QuizAttempt attempt : attempts) {
            Instant at = attempt.getSubmittedAt() != null ? attempt.getSubmittedAt() : attempt.getStartedAt();
            Long quizChapter = attempt.getQuiz().getChapter() == null ? null : attempt.getQuiz().getChapter().getId();
            Map<Long, ItemResponse> mine = responses.getOrDefault(attempt.getId(), Map.of());
            for (QuizItem item : itemsByQuiz.getOrDefault(attempt.getQuiz().getId(), List.of())) {
                Long chapter = item.getChapter() != null ? item.getChapter().getId() : quizChapter;
                if (chapter == null) {
                    continue;
                }
                Integer earned = earned(attempt, item, mine.get(item.getId()));
                if (earned != null) {
                    answers.add(new Answer(attempt.getStudent().getId(), item.getId(), chapter, item.getPoints(), earned, at));
                }
            }
        }
        return answers;
    }

    /** The daily-review answers, each worth the question's points when right. */
    private List<Answer> reviewAnswersOf(List<Long> studentIds) {
        List<Answer> answers = new ArrayList<>();
        for (ReviewAnswer r : reviewAnswerRepository.findByStudentIdIn(studentIds)) {
            QuizItem item = r.getItem();
            Long chapter = item.getChapter() != null ? item.getChapter().getId()
                    : item.getQuiz().getChapter() == null ? null : item.getQuiz().getChapter().getId();
            answers.add(new Answer(r.getStudent().getId(), item.getId(), chapter, item.getPoints(),
                    r.isCorrect() ? item.getPoints() : 0, r.getAnsweredAt()));
        }
        return answers;
    }

    /** Points earned on one question of one attempt, or {@code null} when it does not count (yet). */
    private static Integer earned(QuizAttempt attempt, QuizItem item, ItemResponse response) {
        Integer awarded = response == null ? null : response.getAwardedPoints();
        if (attempt.getMode() == AttemptMode.PRACTICE) {
            return awarded; // answered multiple-choice only; a hint-only row or an open answer has none
        }
        if (awarded != null) {
            return awarded;
        }
        if (item.getType() == QuizItemType.SINGLE_CHOICE) {
            return 0; // left unanswered in a handed-in test
        }
        // An open answer without points: waiting for the teacher, unless the paper is already graded (then a 0).
        return attempt.getStatus() == QuizAttemptStatus.GRADED ? 0 : null;
    }
}
