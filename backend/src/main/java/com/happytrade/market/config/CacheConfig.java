package com.happytrade.market.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Two caches with different TTLs, so a single {@code spring.cache.caffeine.spec} property will not
 * do. {@link SimpleCacheManager} holding two independently configured caches expresses that.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CHART_CACHE = "marketChart";
    public static final String TICKER_CACHE = "marketTicker";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(
                // 15s: several browser tabs polling at once must not multiply upstream calls.
                new CaffeineCache(CHART_CACHE, Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(15))
                        .maximumSize(200)
                        .build()),
                // 3s: below the frontend's 5s poll, so a refresh nearly always sees fresh data.
                new CaffeineCache(TICKER_CACHE, Caffeine.newBuilder()
                        .expireAfterWrite(Duration.ofSeconds(3))
                        .maximumSize(50)
                        .build())
        ));
        return manager;
    }
}
