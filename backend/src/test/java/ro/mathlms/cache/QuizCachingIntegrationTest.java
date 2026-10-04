package ro.mathlms.cache;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.quiz.ItemRequest;
import ro.mathlms.quiz.Quiz;
import ro.mathlms.quiz.QuizAdminService;
import ro.mathlms.quiz.QuizAttemptService;
import ro.mathlms.quiz.QuizItem;
import ro.mathlms.quiz.QuizItemRepository;
import ro.mathlms.quiz.QuizItemType;
import ro.mathlms.quiz.QuizOption;
import ro.mathlms.quiz.QuizOptionRepository;
import ro.mathlms.quiz.QuizRepository;
import ro.mathlms.quiz.QuizStatsService;
import ro.mathlms.quiz.StudentQuizDtos.StartedAttemptDto;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 5.5 on real Redis + Postgres: the aggregates are cached, and the cache is evicted AFTER the
 * transaction that changes them commits — otherwise a student would keep seeing a stale chart.
 * Not @Transactional on purpose: every service call commits, like in production.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class QuizCachingIntegrationTest {

    @Autowired private CacheManager cacheManager;
    @Autowired private QuizStatsService statsService;
    @Autowired private QuizAttemptService attemptService;
    @Autowired private QuizAdminService adminService;
    @Autowired private UserRepository userRepository;
    @Autowired private QuizRepository quizRepository;
    @Autowired private QuizItemRepository itemRepository;
    @Autowired private QuizOptionRepository optionRepository;

    private Long cached(String cacheName, Object key) {
        var wrapper = cacheManager.getCache(cacheName).get(key);
        return wrapper == null ? null : 1L;
    }

    @Test
    void statsAndProgressAreCachedThenEvictedWhenAnAttemptGetsGraded() {
        String stamp = String.valueOf(System.nanoTime());
        String email = "cache." + stamp + "@scoala.ro";
        userRepository.save(new User(email, "Elev Cache", Role.STUDENT));
        Quiz quiz = new Quiz("Cache " + stamp, null);
        quiz.publish();
        quiz = quizRepository.save(quiz);
        QuizItem item = itemRepository.save(new QuizItem(quiz, 1, QuizItemType.SINGLE_CHOICE, "s", 5, null));
        QuizOption right = optionRepository.save(new QuizOption(item, 0, "A", true));
        optionRepository.save(new QuizOption(item, 1, "B", false));

        // first read computes + stores; nothing graded yet
        assertThat(statsService.getStats(quiz.getId()).gradedAttempts()).isZero();
        assertThat(attemptService.getProgress(email)).isEmpty();
        assertThat(cached(CacheNames.QUIZ_STATS, quiz.getId())).isNotNull();
        assertThat(cached(CacheNames.PROGRESS, email)).isNotNull();

        // the student hands in a quiz that is auto-graded entirely -> GRADED in the submit transaction
        StartedAttemptDto started = attemptService.startAttempt(quiz.getId(), email);
        attemptService.saveResponse(started.attemptId(), item.getId(), right.getId(), email);
        attemptService.submit(started.attemptId(), email);

        // evicted after the commit, so the next read sees the new grade (a stale cache would still say 0)
        assertThat(cached(CacheNames.QUIZ_STATS, quiz.getId())).isNull();
        assertThat(cached(CacheNames.PROGRESS, email)).isNull();
        assertThat(statsService.getStats(quiz.getId()).gradedAttempts()).isEqualTo(1);
        assertThat(attemptService.getProgress(email)).singleElement()
                .satisfies(point -> assertThat(point.percent()).isEqualTo(100));

        // editing the quiz changes its max score: both caches are dropped as a whole
        assertThat(cached(CacheNames.QUIZ_STATS, quiz.getId())).isNotNull();
        adminService.addItem(quiz.getId(), new ItemRequest(QuizItemType.OPEN, 2, "deschis", 5, null, null));
        assertThat(cached(CacheNames.QUIZ_STATS, quiz.getId())).isNull();
        assertThat(cached(CacheNames.PROGRESS, email)).isNull();
    }
}
