package discord4j.rest.util;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

public class DefaultCacheFactory implements CacheFactory {

	public <K, V> Cache<K, V> createRequestStreamCache() {
	    return Caffeine.newBuilder()
            .maximumSize(100_000)
            .build();
	}
}
