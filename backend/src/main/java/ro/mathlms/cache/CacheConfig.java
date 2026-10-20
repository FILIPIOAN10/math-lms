package ro.mathlms.cache;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import ro.mathlms.quiz.QuizStatsDtos.QuizStatsDto;
import ro.mathlms.quiz.StudentQuizDtos.ProgressPointDto;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Redis caching of the two expensive aggregates (quiz stats, student progress). Each cache has its own
 * typed JSON serializer (no class names in the payload, so a refactor never breaks stored entries) and a
 * short TTL as a safety net on top of the explicit, after-commit eviction ({@link AfterCommitCacheEvictor}).
 * Redis being down is never fatal: a failed read or write is logged and the value is simply recomputed.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);
    private static final Duration TTL = Duration.ofMinutes(10);

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Jackson 3 has java.time built in; Jackson 2 defaults keep the stored JSON exactly as before the Boot 4 upgrade.
        JsonMapper mapper = JsonMapper.builderWithJackson2Defaults()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        JavaType progressList = mapper.getTypeFactory().constructCollectionType(List.class, ProgressPointDto.class);

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(TTL)
                .disableCachingNullValues()
                .prefixCacheNameWith("mathlms:");

        // Spring Data Redis 4 may run cache writes asynchronously on Lettuce; the after-commit eviction relies on
        // the entry being gone when commit returns, so writes stay synchronous as they were before Boot 4.
        RedisCacheWriter writer = RedisCacheWriter.create(connectionFactory, config -> config.immediateWrites());

        return RedisCacheManager.builder(writer)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(Map.of(
                        CacheNames.QUIZ_STATS, base.serializeValuesWith(SerializationPair.fromSerializer(
                                new JacksonJsonRedisSerializer<>(mapper, QuizStatsDto.class))),
                        CacheNames.PROGRESS, base.serializeValuesWith(SerializationPair.fromSerializer(
                                new JacksonJsonRedisSerializer<>(mapper, progressList)))))
                .build();
    }

    /** Fail open: cache trouble must degrade to "no cache", never to a 500. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache get failed ({}): {}", cache.getName(), e.toString());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache put failed ({}): {}", cache.getName(), e.toString());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache evict failed ({}): {}", cache.getName(), e.toString());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache clear failed ({}): {}", cache.getName(), e.toString());
            }
        };
    }
}
