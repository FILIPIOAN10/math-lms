package ro.mathlms.quiz;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.quiz.QuizStatsDtos.BucketDto;
import ro.mathlms.quiz.QuizStatsDtos.ItemStatDto;
import ro.mathlms.quiz.QuizStatsDtos.QuizStatsDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Teacher analytics for one quiz, computed over its GRADED attempts only: the average, a five-bucket
 * score distribution and, per item, how often it was answered right (single choice) and the average
 * points. Two aggregate queries for the whole quiz — never one per attempt.
 */
@Service
public class QuizStatsService {

    private static final String[] BUCKET_LABELS = {"0–20%", "20–40%", "40–60%", "60–80%", "80–100%"};

    private final QuizRepository quizRepository;
    private final QuizItemRepository itemRepository;
    private final QuizAttemptRepository attemptRepository;
    private final ItemResponseRepository responseRepository;

    public QuizStatsService(QuizRepository quizRepository, QuizItemRepository itemRepository,
                            QuizAttemptRepository attemptRepository, ItemResponseRepository responseRepository) {
        this.quizRepository = quizRepository;
        this.itemRepository = itemRepository;
        this.attemptRepository = attemptRepository;
        this.responseRepository = responseRepository;
    }

    @Transactional(readOnly = true)
    public QuizStatsDto getStats(Long quizId) {
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new QuizNotFoundException("Quiz", quizId));
        List<QuizItem> items = itemRepository.findByQuizIdOrderByPosition(quizId);
        int maxScore = items.stream().mapToInt(QuizItem::getPoints).sum();
        List<Integer> scores = attemptRepository.findGradedScoresByQuizId(quizId);
        int graded = scores.size();

        Double averageScore = graded == 0 ? null : scores.stream().mapToInt(Integer::intValue).average().orElse(0);
        Integer averagePercent = averageScore == null || maxScore == 0
                ? null : (int) Math.round(averageScore * 100 / maxScore);

        Map<Long, ItemStat> byItem = responseRepository.findItemStatsByQuizId(quizId).stream()
                .collect(Collectors.toMap(ItemStat::itemId, Function.identity()));
        List<ItemStatDto> itemStats = new ArrayList<>();
        for (QuizItem item : items) {
            ItemStat stat = byItem.get(item.getId());
            long correct = stat == null ? 0 : stat.correct();
            long totalPoints = stat == null || stat.totalPoints() == null ? 0 : stat.totalPoints();
            Double correctRate = graded == 0 || item.getType() != QuizItemType.SINGLE_CHOICE
                    ? null : (double) correct / graded;
            Double averagePoints = graded == 0 ? null : (double) totalPoints / graded;
            itemStats.add(new ItemStatDto(item.getId(), item.getPosition(), item.getType(), item.getStatement(),
                    item.getPoints(), correctRate, averagePoints));
        }
        return new QuizStatsDto(quiz.getId(), quiz.getTitle(), graded, maxScore, averageScore, averagePercent,
                distribution(scores, maxScore), itemStats);
    }

    private static List<BucketDto> distribution(List<Integer> scores, int maxScore) {
        int[] counts = new int[BUCKET_LABELS.length];
        for (int score : scores) {
            int percent = maxScore == 0 ? 0 : score * 100 / maxScore;
            counts[Math.min(percent / 20, BUCKET_LABELS.length - 1)]++;
        }
        List<BucketDto> buckets = new ArrayList<>();
        for (int i = 0; i < BUCKET_LABELS.length; i++) {
            buckets.add(new BucketDto(BUCKET_LABELS[i], counts[i]));
        }
        return buckets;
    }
}
