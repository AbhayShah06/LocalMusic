package com.abhayshah.localmusic;

import android.content.Context;

import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;

/** Process-wide playback cache. Replayed and recently queued songs reuse downloaded audio. */
@androidx.media3.common.util.UnstableApi
final class PlaybackCache {
    private static final long MAX_BYTES = 512L * 1024L * 1024L;
    private static SimpleCache cache;

    static synchronized DataSource.Factory dataSourceFactory(Context context, DataSource.Factory upstream) {
        if (cache == null) {
            Context app = context.getApplicationContext();
            cache = new SimpleCache(new java.io.File(app.getCacheDir(), "playback"),
                    new LeastRecentlyUsedCacheEvictor(MAX_BYTES), new StandaloneDatabaseProvider(app));
        }
        return new CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(upstream)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }
    static synchronized long bytes() { return cache == null ? 0 : cache.getCacheSpace(); }
    static synchronized void clear() {
        if (cache == null) return;
        for (String key : new java.util.HashSet<>(cache.getKeys())) try { cache.removeResource(key); } catch (Exception ignored) { }
    }

    private PlaybackCache() { }
}
