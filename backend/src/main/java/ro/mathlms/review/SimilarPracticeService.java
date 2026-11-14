package ro.mathlms.review;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.content.ContentNotFoundException;
import ro.mathlms.enchapter.EnChapterExample;
import ro.mathlms.enchapter.EnChapterExampleRepository;
import ro.mathlms.enchapter.LessonLinks;
import ro.mathlms.quiz.QuizAttempt;
import ro.mathlms.quiz.QuizAttemptRepository;
import ro.mathlms.quiz.QuizAttemptService;
import ro.mathlms.quiz.QuizAttemptStatus;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemHintRepository;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.quiz.QuizNotFoundException;
import ro.mathlms.quiz.QuizOption;
import ro.mathlms.quiz.QuizOptionRepository;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.review.ReviewDtos.ChapterPracticeResultDto;
import ro.mathlms.review.ReviewDtos.SimilarEntryDto;
import ro.mathlms.review.ReviewDtos.SimilarExampleDto;
import ro.mathlms.review.ReviewDtos.SimilarPracticeDto;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.sql.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * „Dă-mi una asemănătoare": after a mistake, the {@value #SIZE} grile and worked examples nearest by meaning to the
 * question, from the embeddings in {@code similar_index} (V71, written by the tutor service every night). Plain search:
 * nothing is generated, so nothing can be made up, and nothing the student does reaches a model.
 *
 * <p>Only grile the student may already see ({@link PracticePool}: never a test still to be written), never the
 * question itself, nor a copy of it in another test (closer than {@code app.similar.min-distance}), nor anything farther
 * than {@code app.similar.max-distance}. Worked examples are theory, open to every student. Answers are checked at once
 * and stored nowhere, like a chapter's practice.
 */
@Service
public class SimilarPracticeService {

    static final int SIZE = 3;

    private record Hit(String kind, long id, double distance) {
    }

    private final UserRepository userRepository;
    private final QuizAttemptRepository attemptRepository;
    private final QuizItemRepository itemRepository;
    private final QuizOptionRepository optionRepository;
    private final EnChapterExampleRepository exampleRepository;
    private final LessonLinks lessonLinks;
    private final JdbcTemplate jdbc;
    private final PracticePool practicePool;
    private final PracticeQuestions practiceQuestions;
    private final double minDistance;
    private final double maxDistance;

    public SimilarPracticeService(UserRepository userRepository, QuizAttemptRepository attemptRepository,
                                  QuizItemRepository itemRepository, QuizOptionRepository optionRepository,
                                  QuizItemHintRepository hintRepository, EnChapterExampleRepository exampleRepository,
                                  LessonLinks lessonLinks, JdbcTemplate jdbc, QuizAttemptService quizAttemptService,
                                  QuizRepository quizRepository,
                                  @Value("${app.similar.min-distance:0.02}") double minDistance,
                                  @Value("${app.similar.max-distance:0.5}") double maxDistance) {
        this.userRepository = userRepository;
        this.attemptRepository = attemptRepository;
        this.itemRepository = itemRepository;
        this.optionRepository = optionRepository;
        this.exampleRepository = exampleRepository;
        this.lessonLinks = lessonLinks;
        this.jdbc = jdbc;
        this.practicePool = new PracticePool(quizAttemptService, quizRepository);
        this.practiceQuestions = new PracticeQuestions(optionRepository, hintRepository);
        this.minDistance = minDistance;
        this.maxDistance = maxDistance;
    }

    /** The nearest ones to a question the student answered (handed in or practised); 404 for any other question. */
    @Transactional(readOnly = true)
    public SimilarPracticeDto similar(String studentEmail, Long itemId) {
        User student = requireStudent(studentEmail);
        List<QuizAttempt> attempts = attemptRepository.findByStudentIdOrderByStartedAtDesc(student.getId());
        QuizItem source = itemRepository.findById(itemId)
                .filter(item -> attempts.stream().anyMatch(a -> a.getQuiz().getId().equals(item.getQuiz().getId())
                        && a.getStatus() != QuizAttemptStatus.IN_PROGRESS))
                .orElseThrow(() -> new QuizNotFoundException("QuizItem", itemId));
        Boolean indexed = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM similar_index WHERE item_id = ?)",
                Boolean.class, source.getId());
        if (!Boolean.TRUE.equals(indexed)) {
            return new SimilarPracticeDto(false, List.of());
        }
        Set<Long> quizIds = practicePool.quizIds(student, attempts, null);
        List<Long> pool = quizIds.isEmpty() ? List.of() : itemRepository.findByQuizIdIn(quizIds).stream()
                .filter(item -> item.getType() == QuizItemType.SINGLE_CHOICE && !item.getId().equals(source.getId()))
                .map(QuizItem::getId).toList();
        List<Hit> hits = nearest(source.getId(), pool);

        Map<Long, QuizItem> items = itemRepository.findAllById(hits.stream().filter(h -> h.kind().equals("QUESTION"))
                .map(Hit::id).toList()).stream().collect(Collectors.toMap(QuizItem::getId, Function.identity()));
        Map<Long, ReviewDtos.ReviewQuestionDto> cards = practiceQuestions.of(new ArrayList<>(items.values()), i -> 0)
                .stream().collect(Collectors.toMap(ReviewDtos.ReviewQuestionDto::itemId, Function.identity()));
        Map<Long, EnChapterExample> examples = exampleRepository.findAllById(hits.stream()
                        .filter(h -> h.kind().equals("EXAMPLE")).map(Hit::id).toList()).stream()
                .collect(Collectors.toMap(EnChapterExample::getId, Function.identity()));
        List<SimilarEntryDto> entries = new ArrayList<>();
        for (Hit hit : hits) {
            if (hit.kind().equals("QUESTION") && cards.containsKey(hit.id())) {
                entries.add(new SimilarEntryDto("QUESTION", cards.get(hit.id()), null));
            } else if (hit.kind().equals("EXAMPLE") && examples.containsKey(hit.id())) {
                EnChapterExample e = examples.get(hit.id());
                entries.add(new SimilarEntryDto("EXAMPLE", null, new SimilarExampleDto(e.getId(), e.getChapter().getId(),
                        e.getStatement(), e.getSolution(), e.getImageKey(),
                        lessonLinks.ofId(e.getChapter().getId(), e.getChapter().getBookPage()).orElse(null))));
            }
        }
        return new SimilarPracticeDto(true, entries);
    }

    /** Checks one answer to a grilă the student may practise; any other question is a 409. Nothing is stored. */
    @Transactional(readOnly = true)
    public ChapterPracticeResultDto answer(String studentEmail, Long itemId, Long optionId) {
        User student = requireStudent(studentEmail);
        QuizItem item = itemRepository.findById(itemId)
                .filter(i -> i.getType() == QuizItemType.SINGLE_CHOICE)
                .filter(i -> practicePool.quizIds(student, attemptRepository.findByStudentIdOrderByStartedAtDesc(
                        student.getId()), i.getQuiz().getId()).contains(i.getQuiz().getId()))
                .orElseThrow(() -> new InvalidReviewException("Întrebarea nu se poate exersa încă."));
        List<QuizOption> options = optionRepository.findByItemIdOrderByPosition(item.getId());
        QuizOption chosen = options.stream().filter(o -> o.getId().equals(optionId)).findFirst()
                .orElseThrow(() -> new InvalidReviewException("Varianta nu aparține acestei întrebări."));
        Long correctOptionId = options.stream().filter(QuizOption::isCorrect).map(QuizOption::getId).findFirst().orElse(null);
        return new ChapterPracticeResultDto(chosen.isCorrect(), correctOptionId, item.getSolution(),
                chosen.isCorrect() ? null : chosen.getMistake());
    }

    /** The nearest rows to the source's embedding, same model only: grile of {@code pool} and every example. */
    private List<Hit> nearest(Long sourceItemId, List<Long> pool) {
        return jdbc.query(connection -> {
            var statement = connection.prepareStatement("""
                    WITH src AS (SELECT embedding, embedded_with FROM similar_index WHERE item_id = ?)
                    SELECT s.item_id, s.example_id, s.embedding <=> src.embedding AS distance
                    FROM similar_index s, src
                    WHERE s.embedded_with = src.embedded_with
                      AND (s.example_id IS NOT NULL OR s.item_id = ANY(?))
                      AND s.embedding <=> src.embedding >= ? AND s.embedding <=> src.embedding <= ?
                    ORDER BY distance LIMIT ?""");
            Array ids = connection.createArrayOf("bigint", pool.toArray());
            statement.setLong(1, sourceItemId);
            statement.setArray(2, ids);
            statement.setDouble(3, minDistance);
            statement.setDouble(4, maxDistance);
            statement.setInt(5, SIZE);
            return statement;
        }, (rs, n) -> {
            long itemId = rs.getLong("item_id");
            boolean isItem = !rs.wasNull();
            return isItem ? new Hit("QUESTION", itemId, rs.getDouble("distance"))
                    : new Hit("EXAMPLE", rs.getLong("example_id"), rs.getDouble("distance"));
        });
    }

    private User requireStudent(String email) {
        return userRepository.findByEmail(email)
                .filter(u -> u.getRole() == Role.STUDENT)
                .orElseThrow(() -> new ContentNotFoundException("Nu există un elev cu acest cont."));
    }
}
