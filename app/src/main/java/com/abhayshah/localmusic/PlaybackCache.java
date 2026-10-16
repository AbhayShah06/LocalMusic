package com.abhayshah.localmusic;

import android.content.Context;

import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.datasource.cache.CacheWriter;
import androidx.media3.datasource.DataSpec;

/** Process-wide playback cache. Replayed and recently queued songs reuse downloaded audio. */
@androidx.media3.common.util.UnstableApi
final class PlaybackCache {
    private static final long MAX_BYTES = 256L * 1024L * 1024L;
    private static SimpleCache cache;
    private static CacheDataSource.Factory factory;

    static synchronized DataSource.Factory dataSourceFactory(Context context, DataSource.Factory upstream) {
        if (cache == null) {
            Context app = context.getApplicationContext();
            cache = new SimpleCache(new java.io.File(app.getCacheDir(), "playback"),
                    new LeastRecentlyUsedCacheEvictor(MAX_BYTES), new StandaloneDatabaseProvider(app));
        }
        factory = new CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(upstream)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
        return factory;
    }
    /** Downloads a resolved remote item into the same bounded cache used by playback. */
    static void preload(androidx.media3.common.MediaItem item) throws java.io.IOException {
        CacheDataSource.Factory current;
        synchronized (PlaybackCache.class) { current = factory; }
        if (current == null || item.localConfiguration == null) return;
        String scheme = item.localConfiguration.uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return;
        DataSpec spec = new DataSpec.Builder().setUri(item.localConfiguration.uri).setKey(item.mediaId).build();
        new CacheWriter(current.createDataSource(), spec, null, null).cache();
    }
    static synchronized long bytes() { return cache == null ? 0 : cache.getCacheSpace(); }
    static synchronized void clear() {
        if (cache == null) return;
        for (String key : new java.util.HashSet<>(cache.getKeys())) try { cache.removeResource(key); } catch (Exception ignored) { }
    }

    private PlaybackCache() { }
}
