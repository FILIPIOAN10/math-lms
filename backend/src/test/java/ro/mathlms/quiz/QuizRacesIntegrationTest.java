package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.support.TransactionTemplate;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Races on real Postgres. "+1 încercare" (audit 2026-11-10 low): two teachers clicking at the same moment both count -
 * the read-then-save it replaced lost one of them, or failed on the unique (quiz, student) - and the cap of
 * {@link AttemptGrant#MAX_EXTRA} still holds. And a question edit's lock on its test (audit 2026-11-05 L3).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class QuizRacesIntegrationTest {

    @Autowired private QuizRepository quizRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AttemptGrantRepository grantRepository;
    @Autowired private TransactionTemplate tx;

    @Test
    void twoGrantsAtTheSameMomentBothCountAndTheCapHolds() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        Long quizId = quizRepository.save(new Quiz("Fracții " + stamp, null)).getId();
        Long ana = userRepository.save(new User("ana." + stamp + "@scoala.ro", "Ana", Role.STUDENT)).getId();
        Instant now = Instant.now();

        CyclicBarrier together = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> clicks = List.of(
                    pool.submit(() -> tx.execute(s -> {
                        await(together);
                        return grantRepository.addOne(quizId, ana, now, AttemptGrant.MAX_EXTRA);
                    })),
                    pool.submit(() -> tx.execute(s -> {
                        await(together);
                        return grantRepository.addOne(quizId, ana, now, AttemptGrant.MAX_EXTRA);
                    })));
            for (Future<Integer> click : clicks) {
                assertThat(click.get(30, TimeUnit.SECONDS)).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(grantRepository.findExtra(quizId, ana)).contains(2);

        for (int i = 2; i < AttemptGrant.MAX_EXTRA; i++) {
            Integer granted = tx.execute(s -> grantRepository.addOne(quizId, ana, now, AttemptGrant.MAX_EXTRA));
            assertThat(granted).isEqualTo(1);
        }
        assertThat(grantRepository.findExtra(quizId, ana)).contains(AttemptGrant.MAX_EXTRA);
        Integer pastTheCap = tx.execute(s -> grantRepository.addOne(quizId, ana, now, AttemptGrant.MAX_EXTRA));
        assertThat(pastTheCap).isZero();
        assertThat(grantRepository.findExtra(quizId, ana)).contains(AttemptGrant.MAX_EXTRA);
    }

    @Autowired private QuizAttemptRepository attemptRepository;

    /**
     * Audit 2026-11-05 L3: while a teacher's edit holds the test's row ({@link QuizRepository#lockForEdit}), a student
     * starting it waits, so "nobody took it, replace the options" cannot interleave with a new attempt.
     */
    @Test
    void aStudentStartingATestWaitsForAnEditThatLockedIt() throws Exception {
        String stamp = String.valueOf(System.nanoTime());
        Long quizId = quizRepository.save(new Quiz("Ecuații " + stamp, null)).getId();
        Long bob = userRepository.save(new User("bob." + stamp + "@scoala.ro", "Bob", Role.STUDENT)).getId();
        java.util.concurrent.CountDownLatch locked = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> teacher = pool.submit(() -> tx.executeWithoutResult(s -> {
                assertThat(quizRepository.lockForEdit(quizId)).contains(quizId);
                locked.countDown();
                try {
                    release.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertThat(locked.await(30, TimeUnit.SECONDS)).isTrue();
            Future<Long> student = pool.submit(() -> tx.execute(s -> attemptRepository.save(new QuizAttempt(
                    quizRepository.findById(quizId).orElseThrow(), userRepository.findById(bob).orElseThrow(),
                    Instant.now(), AttemptMode.TEST)).getId()));

            Thread.sleep(1500);
            assertThat(student.isDone()).as("the attempt must wait for the edit's lock").isFalse();
            release.countDown();
            teacher.get(30, TimeUnit.SECONDS);
            assertThat(student.get(30, TimeUnit.SECONDS)).isNotNull();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
