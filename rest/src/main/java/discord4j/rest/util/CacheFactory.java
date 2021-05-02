package discord4j.rest.util;

import com.github.benmanes.caffeine.cache.Cache;
import discord4j.rest.request.BucketKey;
import discord4j.rest.request.DefaultRouter;

/**
 * Provides caches for various parts of the library
 */
public interface CacheFactory {

    /**
     * Provide a cache for request buckets in {@link DefaultRouter}
     * @param <K> the key {@link BucketKey}
     * @param <V> the value RequestStream
     * @return the cache for request buckets
     */
    <K, V> Cache<K, V> createRequestStreamCache();

}
