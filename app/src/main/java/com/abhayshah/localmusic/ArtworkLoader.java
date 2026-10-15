package com.abhayshah.localmusic;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ArtworkLoader {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(24);
    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void into(ImageView view, Uri uri, int fallback) {
        String key = uri == null ? "" : uri.toString(); view.setTag(key); view.setImageResource(fallback);
        if (key.isEmpty()) return;
        if ("content".equals(uri.getScheme())) { view.setImageURI(uri); if (view.getDrawable() == null) view.setImageResource(fallback); return; }
        Bitmap cached = CACHE.get(key); if (cached != null) { view.setImageBitmap(cached); return; }
        IO.execute(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(key).openConnection();
                connection.setConnectTimeout(8_000); connection.setReadTimeout(10_000);
                Bitmap bitmap = BitmapFactory.decodeStream(connection.getInputStream()); connection.disconnect();
                if (bitmap != null) { CACHE.put(key, bitmap); MAIN.post(() -> { if (key.equals(view.getTag())) view.setImageBitmap(bitmap); }); }
            } catch (Exception ignored) { }
        });
    }

    private ArtworkLoader() { }
}
