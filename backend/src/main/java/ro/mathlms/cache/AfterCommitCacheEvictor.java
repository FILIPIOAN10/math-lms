package ro.mathlms.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Evicts cache entries only AFTER the surrounding transaction commits. Evicting earlier is wrong both
 * ways: on a rollback the entry was dropped for nothing, and — worse — a concurrent reader can
 * re-populate the cache from the not-yet-committed old data between the eviction and the commit, so
 * the stale value outlives the change. Outside a transaction it evicts at once. A cache failure is
 * logged, never thrown: the business operation already succeeded and the TTL bounds the staleness.
 */
@Component
public class AfterCommitCacheEvictor {

    private static final Logger log = LoggerFactory.getLogger(AfterCommitCacheEvictor.class);

    private final CacheManager cacheManager;

    public AfterCommitCacheEvictor(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evict(String cacheName, Object key) {
        runAfterCommit(() -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
            }
        });
    }

    public void evictAll(String cacheName) {
        runAfterCommit(() -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        });
    }

    private void runAfterCommit(Runnable eviction) {
        Runnable safe = () -> {
            try {
                eviction.run();
            } catch (RuntimeException e) {
                log.warn("Cache eviction failed, entries expire by TTL: {}", e.toString());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safe.run();
                }
            });
        } else {
            safe.run();
        }
    }
}
