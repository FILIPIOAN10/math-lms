package ro.mathlms.mastery;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.content.ContentNotFoundException;
import ro.mathlms.content.Enrollment;
import ro.mathlms.content.EnrollmentRepository;
import ro.mathlms.content.SchoolClassRepository;
import ro.mathlms.enchapter.EnArea;
import ro.mathlms.enchapter.EnChapter;
import ro.mathlms.enchapter.EnChapterRepository;
import ro.mathlms.enchapter.EnChapterService;
import ro.mathlms.mastery.MasteryCalculator.Answer;
import ro.mathlms.mastery.MasteryCalculator.Tally;
import ro.mathlms.mastery.MasteryDtos.CellDto;
import ro.mathlms.mastery.MasteryDtos.StudentRowDto;
import ro.mathlms.quiz.AttemptMode;
import ro.mathlms.quiz.EnChapterLinks;
import ro.mathlms.quiz.ItemResponse;
import ro.mathlms.quiz.ItemResponseRepository;
import ro.mathlms.quiz.QuizAttempt;
import ro.mathlms.quiz.QuizAttemptRepository;
import ro.mathlms.quiz.QuizAttemptStatus;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemPart;
import ro.mathlms.quiz.QuizItemPartRepository;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.review.ReviewAnswer;
import ro.mathlms.review.ReviewAnswerRepository;
import ro.mathlms.user.User;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The map of EN chapters (stage 4): how well a student does in each EN chapter page, from every question that points
 * at it (the teacher's choice, else the default for its place on the paper) - and, for a problem with requirements
 * a), b)..., from each requirement on its own, since they often test different chapters. Same rules as the class
 * chapters' map ({@link MasteryService}): the latest scored answer to each question counts; a handed-in test counts every
 * question (unanswered = 0; an open answer still waiting for the teacher does not count yet); practice and the daily
 * review count each answered grilă.
 */
@Service
public class EnMasteryService {

    /** One EN chapter for one student. {@code percent} null = nothing scored there yet; {@code ready} = its page has content. */
    public record EnChapterMasteryDto(Long chapterId, String code, String title, EnArea area, int earned, int possible,
                                      Integer percent, int questions, boolean ready) {
    }

    /** A column of the teacher's grid. */
    public record EnChapterRefDto(Long id, String code, String title, EnArea area) {
    }

    /** The class grid: students × the EN chapters the class has met, in list order. */
    public record EnClassMasteryDto(List<EnChapterRefDto> chapters, List<StudentRowDto> students) {
    }

    private final EnrollmentRepository enrollmentRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final EnChapterRepository chapterRepository;
    private final EnChapterService chapterService;
    private final EnChapterLinks links;
    private final QuizAttemptRepository attemptRepository;
    private final QuizItemRepository itemRepository;
    private final QuizItemPartRepository partRepository;
    private final ItemResponseRepository responseRepository;
    private final ReviewAnswerRepository reviewAnswerRepository;

    public EnMasteryService(EnrollmentRepository enrollmentRepository, SchoolClassRepository schoolClassRepository,
                            EnChapterRepository chapterRepository, EnChapterService chapterService, EnChapterLinks links,
                            QuizAttemptRepository attemptRepository, QuizItemRepository itemRepository,
                            QuizItemPartRepository partRepository, ItemResponseRepository responseRepository,
                            ReviewAnswerRepository reviewAnswerRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.chapterRepository = chapterRepository;
        this.chapterService = chapterService;
        this.links = links;
        this.attemptRepository = attemptRepository;
        this.itemRepository = itemRepository;
        this.partRepository = partRepository;
        this.responseRepository = responseRepository;
        this.reviewAnswerRepository = reviewAnswerRepository;
    }

    /** Every EN chapter, in list order, with this student's level (null where they have not met it yet). */
    @Transactional(readOnly = true)
    public List<EnChapterMasteryDto> forStudent(User student) {
        Map<Long, Tally> tallies = MasteryCalculator.latestPerQuestion(answersOf(List.of(student.getId())))
                .getOrDefault(student.getId(), Map.of());
        Set<Long> ready = chapterService.readyIds();
        return chapterRepository.findAllByOrderByPositionAscIdAsc().stream()
                .map(c -> {
                    Tally t = tallies.getOrDefault(c.getId(), new Tally(0, 0, 0));
                    return new EnChapterMasteryDto(c.getId(), c.getCode(), c.getTitle(), c.getArea(), t.earned(),
                            t.possible(), t.percent(), t.questions(), ready.contains(c.getId()));
                })
                .toList();
    }

    /** The teacher's grid: the class's students (alphabetical) × the EN chapters any of them has met. */
    @Transactional(readOnly = true)
    public EnClassMasteryDto forClass(Long schoolClassId) {
        if (!schoolClassRepository.existsById(schoolClassId)) {
            throw new ContentNotFoundException("SchoolClass", schoolClassId);
        }
        List<User> students = enrollmentRepository.findBySchoolClassIdFetchStudent(schoolClassId).stream()
                .map(Enrollment::getStudent)
                .filter(s -> !s.isErased())
                .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        Map<Long, Map<Long, Tally>> tallies = MasteryCalculator.latestPerQuestion(
                answersOf(students.stream().map(User::getId).toList()));
        Set<Long> met = tallies.values().stream().flatMap(m -> m.keySet().stream()).collect(Collectors.toSet());
        List<EnChapter> columns = chapterRepository.findAllByOrderByPositionAscIdAsc().stream()
                .filter(c -> met.contains(c.getId())).toList();
        List<StudentRowDto> rows = students.stream().map(s -> {
            Map<Long, Tally> mine = tallies.getOrDefault(s.getId(), Map.of());
            return new StudentRowDto(s.getId(), s.getFullName(), columns.stream().map(c -> {
                Tally t = mine.getOrDefault(c.getId(), new Tally(0, 0, 0));
                return new CellDto(c.getId(), t.percent(), t.questions());
            }).toList());
        }).toList();
        return new EnClassMasteryDto(
                columns.stream().map(c -> new EnChapterRefDto(c.getId(), c.getCode(), c.getTitle(), c.getArea())).toList(),
                rows);
    }

    /**
     * The scored answers of these students, keyed by EN chapter. A requirement is its own "question" here: it is keyed
     * by its negated id, so it never collides with a question's id in {@link MasteryCalculator}.
     */
    private List<Answer> answersOf(List<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return List.of();
        }
        List<QuizAttempt> attempts = attemptRepository.findByStudentIdsFetchQuiz(studentIds).stream()
                .filter(a -> a.getMode() == AttemptMode.PRACTICE || a.getStatus() != QuizAttemptStatus.IN_PROGRESS)
                .toList();
        List<ReviewAnswer> reviews = reviewAnswerRepository.findByStudentIdIn(studentIds);
        Set<Long> quizIds = attempts.stream().map(a -> a.getQuiz().getId()).collect(Collectors.toSet());
        reviews.forEach(r -> quizIds.add(r.getItem().getQuiz().getId()));
        if (quizIds.isEmpty()) {
            return List.of();
        }

        // Each quiz's questions in paper order (the default links follow it), its requirements, and their links.
        Map<Long, List<QuizItem>> itemsByQuiz = itemRepository.findByQuizIdIn(quizIds).stream()
                .sorted(Comparator.comparingInt(QuizItem::getPosition).thenComparing(QuizItem::getId))
                .collect(Collectors.groupingBy(i -> i.getQuiz().getId()));
        Map<Long, List<QuizItemPart>> partsByItem = partRepository.findByItemIdInOrderByItemIdAscPositionAsc(
                        itemsByQuiz.values().stream().flatMap(List::stream).map(QuizItem::getId).toList()).stream()
                .collect(Collectors.groupingBy(p -> p.getItem().getId()));
        Map<Long, EnChapterLinks.Resolved> linksByQuiz = new HashMap<>();
        itemsByQuiz.forEach((quizId, items) -> linksByQuiz.put(quizId, links.resolve(items)));

        Map<Long, Map<Long, ItemResponse>> responses = attempts.isEmpty() ? Map.of() : responseRepository
                .findByAttemptIdIn(attempts.stream().map(QuizAttempt::getId).toList()).stream()
                .collect(Collectors.groupingBy(r -> r.getAttempt().getId(),
                        Collectors.toMap(r -> r.getItem().getId(), Function.identity())));

        List<Answer> answers = new ArrayList<>();
        for (QuizAttempt attempt : attempts) {
            Long quizId = attempt.getQuiz().getId();
            EnChapterLinks.Resolved quizLinks = linksByQuiz.get(quizId);
            Instant at = attempt.getSubmittedAt() != null ? attempt.getSubmittedAt() : attempt.getStartedAt();
            Map<Long, ItemResponse> mine = responses.getOrDefault(attempt.getId(), Map.of());
            Long studentId = attempt.getStudent().getId();
            for (QuizItem item : itemsByQuiz.getOrDefault(quizId, List.of())) {
                ItemResponse response = mine.get(item.getId());
                List<QuizItemPart> parts = partsByItem.getOrDefault(item.getId(), List.of());
                if (parts.isEmpty()) {
                    EnChapter chapter = quizLinks.itemChapter(item);
                    Integer earned = MasteryService.earned(attempt, item, response);
                    if (chapter != null && earned != null) {
                        answers.add(new Answer(studentId, item.getId(), chapter.getId(), item.getPoints(), earned, at));
                    }
                    continue;
                }
                for (QuizItemPart part : parts) {
                    EnChapter chapter = quizLinks.partChapter(part);
                    Integer earned = partEarned(attempt, response, part);
                    if (chapter != null && earned != null) {
                        answers.add(new Answer(studentId, -part.getId(), chapter.getId(), part.getPoints(), earned, at));
                    }
                }
            }
        }
        // The daily review asks grile again: each answer is worth the question's points when right.
        for (ReviewAnswer review : reviews) {
            QuizItem item = review.getItem();
            EnChapterLinks.Resolved quizLinks = linksByQuiz.get(item.getQuiz().getId());
            EnChapter chapter = quizLinks == null ? null : quizLinks.itemChapter(item);
            if (chapter != null) {
                answers.add(new Answer(review.getStudent().getId(), item.getId(), chapter.getId(), item.getPoints(),
                        review.isCorrect() ? item.getPoints() : 0, review.getAnsweredAt()));
            }
        }
        return answers;
    }

    /**
     * Points on one requirement, or null when it does not count (yet): practice never scores open answers; in a test
     * a requirement without points counts as 0 only once the paper is graded.
     */
    private static Integer partEarned(QuizAttempt attempt, ItemResponse response, QuizItemPart part) {
        if (attempt.getMode() == AttemptMode.PRACTICE) {
            return null;
        }
        Integer given = response == null ? null : response.getPartPoints().get(part.getId());
        if (given != null) {
            return given;
        }
        return attempt.getStatus() == QuizAttemptStatus.GRADED ? 0 : null;
    }
}
