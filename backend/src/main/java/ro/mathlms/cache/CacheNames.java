package ro.mathlms.cache;

/** Names of the Redis caches (see {@link CacheConfig}). */
public final class CacheNames {

    /** Teacher statistics of one quiz, keyed by quiz id. Evicted when an attempt of it is graded or its items change. */
    public static final String QUIZ_STATS = "quizStats";

    /** A student's progress chart data, keyed by the student's email. Evicted when one of their attempts is graded. */
    public static final String PROGRESS = "progress";

    private CacheNames() {
    }
}
