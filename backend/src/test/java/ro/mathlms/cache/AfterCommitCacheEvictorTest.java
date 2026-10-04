package ro.mathlms.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AfterCommitCacheEvictorTest {

    private final CacheManager cacheManager = mock(CacheManager.class);
    private final Cache cache = mock(Cache.class);
    private final AfterCommitCacheEvictor evictor = new AfterCommitCacheEvictor(cacheManager);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private void commit() {
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
    }

    @Test
    void evictsAtOnceWhenThereIsNoTransaction() {
        when(cacheManager.getCache("progress")).thenReturn(cache);

        evictor.evict("progress", "ana@scoala.ro");

        verify(cache).evict("ana@scoala.ro");
    }

    @Test
    void insideATransactionTheEvictionWaitsForTheCommit() {
        when(cacheManager.getCache("progress")).thenReturn(cache);
        TransactionSynchronizationManager.initSynchronization();

        evictor.evict("progress", "ana@scoala.ro");
        verify(cache, never()).evict("ana@scoala.ro"); // not before the commit

        commit();
        verify(cache).evict("ana@scoala.ro");
    }

    @Test
    void aRolledBackTransactionNeverEvicts() {
        when(cacheManager.getCache("progress")).thenReturn(cache);
        TransactionSynchronizationManager.initSynchronization();

        evictor.evict("progress", "ana@scoala.ro");
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK); // no afterCommit
        }

        verify(cache, never()).evict("ana@scoala.ro");
    }

    @Test
    void evictAllClearsTheWholeCacheAfterTheCommit() {
        when(cacheManager.getCache("quizStats")).thenReturn(cache);
        TransactionSynchronizationManager.initSynchronization();

        evictor.evictAll("quizStats");
        verify(cache, never()).clear();

        commit();
        verify(cache).clear();
    }

    @Test
    void anUnknownCacheIsIgnored() {
        when(cacheManager.getCache("nope")).thenReturn(null);

        evictor.evict("nope", 1L); // must not throw
    }

    @Test
    void aFailingCacheNeverBreaksTheBusinessOperation() {
        when(cacheManager.getCache("progress")).thenReturn(cache);
        doThrow(new IllegalStateException("redis down")).when(cache).evict("ana@scoala.ro");

        evictor.evict("progress", "ana@scoala.ro"); // logged, not thrown
    }
}
