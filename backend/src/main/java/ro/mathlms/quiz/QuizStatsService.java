package ro.mathlms.quiz;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.cache.CacheNames;
import ro.mathlms.quiz.QuizStatsDtos.BucketDto;
import ro.mathlms.quiz.QuizStatsDtos.ItemStatDto;
import ro.mathlms.quiz.QuizStatsDtos.NumericStatDto;
import ro.mathlms.quiz.QuizStatsDtos.OptionStatDto;
import ro.mathlms.quiz.QuizStatsDtos.QuizStatsDto;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Teacher analytics for one quiz, computed over the attempt that counts for each student (their highest grade), so a
 * student who retook it counts once: the average, the distribution by grade groups and, per item, how often it was
 * answered right (single choice), who chose which option, whether the marked answer looks wrong, and the average points.
 * A few queries for the whole quiz — never one per attempt.
 */
@Service
public class QuizStatsService {

    /** The groups a teacher reads a class by: the grades of the app, not percents of the points. */
    private static final String[] BUCKET_LABELS = {"sub 5", "5 – 6,99", "7 – 8,99", "9 – 10"};

    /** How many different numbers a question lists: the usual answers, not every typo. */
    private static final int NUMBERS_SHOWN = 5;

    private final QuizRepository quizRepository;
    private final QuizItemRepository itemRepository;
    private final QuizAttemptRepository attemptRepository;
    private final ItemResponseRepository responseRepository;
    private final QuizOptionRepository optionRepository;

    public QuizStatsService(QuizRepository quizRepository, QuizItemRepository itemRepository,
                            QuizAttemptRepository attemptRepository, ItemResponseRepository responseRepository,
                            QuizOptionRepository optionRepository) {
        this.optionRepository = optionRepository;
        this.quizRepository = quizRepository;
        this.itemRepository = itemRepository;
        this.attemptRepository = attemptRepository;
        this.responseRepository = responseRepository;
    }

    /** Cached per quiz id; evicted when an attempt of the quiz is graded or its items change. */
    @Cacheable(cacheNames = CacheNames.QUIZ_STATS, key = "#quizId")
    @Transactional(readOnly = true)
    public QuizStatsDto getStats(Long quizId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new QuizNotFoundException("Quiz", quizId));
        List<QuizItem> items = itemRepository.findByQuizIdOrderByPosition(quizId);
        int maxScore = items.stream().mapToInt(QuizItem::getPoints).sum();
        List<QuizAttemptRepository.CountedAttemptRow> counted = attemptRepository.findCountedAttempts(quizId);
        List<Integer> scores = counted.stream().map(QuizAttemptRepository.CountedAttemptRow::getScore).toList();
        int graded = scores.size();

        Double averageScore = graded == 0 ? null : scores.stream().mapToInt(Integer::intValue).average().orElse(0);
        Integer averagePercent = averageScore == null || maxScore == 0
                ? null : (int) Math.round(averageScore * 100 / maxScore);

        List<Long> attemptIds = counted.stream().map(QuizAttemptRepository.CountedAttemptRow::getAttemptId).toList();
        Map<Long, ItemStat> byItem = attemptIds.isEmpty() ? Map.of()
                : responseRepository.findItemStatsByAttemptIds(attemptIds).stream()
                        .collect(Collectors.toMap(ItemStat::itemId, Function.identity()));
        Map<Long, List<OptionChoice>> choices = attemptIds.isEmpty() ? Map.of()
                : responseRepository.findChoicesByAttemptIds(attemptIds).stream()
                        .collect(Collectors.groupingBy(OptionChoice::itemId));
        Map<Long, List<NumericChoice>> numbers = attemptIds.isEmpty() ? Map.of()
                : responseRepository.findNumbersByAttemptIds(attemptIds).stream()
                        .collect(Collectors.groupingBy(NumericChoice::itemId));
        Map<Long, List<QuizOption>> options = items.isEmpty() ? Map.of()
                : optionRepository.findByItemIdInOrderByItemIdAscPositionAsc(items.stream().map(QuizItem::getId).toList())
                        .stream().collect(Collectors.groupingBy(o -> o.getItem().getId()));
        List<ItemStatDto> itemStats = new ArrayList<>();
        for (QuizItem item : items) {
            ItemStat stat = byItem.get(item.getId());
            long correct = stat == null ? 0 : stat.correct();
            long totalPoints = stat == null || stat.totalPoints() == null ? 0 : stat.totalPoints();
            Double correctRate = graded == 0 || item.getType() == QuizItemType.OPEN
                    ? null : (double) correct / graded;
            Double averagePoints = graded == 0 ? null : (double) totalPoints / graded;
            List<QuizOption> itemOptions = options.getOrDefault(item.getId(), List.of());
            List<OptionChoice> itemChoices = choices.getOrDefault(item.getId(), List.of());
            itemStats.add(new ItemStatDto(item.getId(), item.getPosition(), item.getType(), item.getStatement(),
                    item.getPoints(), correctRate, averagePoints, item.getSection(), optionStats(itemOptions, itemChoices),
                    SuspectAnswer.find(itemOptions, itemChoices, counted, item.getPoints()).orElse(null),
                    item.getNumericAnswer(), numberStats(numbers.getOrDefault(item.getId(), List.of()))));
        }
        return new QuizStatsDto(quiz.getId(), quiz.getTitle(), graded, maxScore, averageScore, averagePercent,
                distribution(scores, maxScore), itemStats);
    }

    /** The numbers typed, the same value counted once however it was written, most typed first. */
    private static List<NumericStatDto> numberStats(List<NumericChoice> numbers) {
        Map<String, List<NumericChoice>> byValue = numbers.stream().collect(Collectors.groupingBy(
                n -> NumericAnswer.parse(n.value()).map(NumericAnswer::key).orElse(n.value()),
                java.util.LinkedHashMap::new, Collectors.toList()));
        return byValue.values().stream()
                .sorted(Comparator.comparingInt((List<NumericChoice> same) -> same.size()).reversed())
                .limit(NUMBERS_SHOWN)
                .map(same -> new NumericStatDto(same.get(0).value(), same.size(), Boolean.TRUE.equals(same.get(0).correct())))
                .toList();
    }

    private static List<OptionStatDto> optionStats(List<QuizOption> options, List<OptionChoice> choices) {
        Map<Long, Long> chosen = choices.stream().collect(Collectors.groupingBy(OptionChoice::optionId, Collectors.counting()));
        return options.stream()
                .map(o -> new OptionStatDto(o.getId(), QuizAttemptService.letterOf(options, o), o.getText(), o.isCorrect(),
                        chosen.getOrDefault(o.getId(), 0L).intValue(), o.getMistake()))
                .toList();
    }

    /** The 1–10 grade as the app shows it, to two decimals: a 6,996 reads 7 on screen and belongs with the 7s. */
    private static double grade(int score, int maxScore) {
        return maxScore == 0 ? 1 : Math.round((1 + 9.0 * score / maxScore) * 100) / 100.0;
    }

    private static int bucketOf(double grade) {
        if (grade < 5) {
            return 0;
        }
        if (grade < 7) {
            return 1;
        }
        return grade < 9 ? 2 : 3;
    }

    private static List<BucketDto> distribution(List<Integer> scores, int maxScore) {
        int[] counts = new int[BUCKET_LABELS.length];
        for (int score : scores) {
            counts[bucketOf(grade(score, maxScore))]++;
        }
        List<BucketDto> buckets = new ArrayList<>();
        for (int i = 0; i < BUCKET_LABELS.length; i++) {
            buckets.add(new BucketDto(BUCKET_LABELS[i], counts[i]));
        }
        return buckets;
    }
}
