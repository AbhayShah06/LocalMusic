package com.abhayshah.localmusic;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ArtworkLoader {
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(16 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return Math.max(1, value.getByteCount() / 1024); }
    };
    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static void into(ImageView view, Uri uri, int fallback) {
        String key = uri == null ? "" : uri.toString(); view.setTag(key); view.setImageResource(fallback);
        if (key.isEmpty()) return;
        if ("content".equals(uri.getScheme())) { view.setImageURI(uri); if (view.getDrawable() == null) view.setImageResource(fallback); return; }
        Bitmap cached = CACHE.get(key); if (cached != null) { view.setImageBitmap(cached); return; }
        File directory = new File(view.getContext().getCacheDir(), "artwork");
        File file = new File(directory, digest(key) + ".img");
        IO.execute(() -> {
            Bitmap bitmap = decode(file);
            if (bitmap == null && download(key, directory, file)) bitmap = decode(file);
            if (bitmap != null) {
                CACHE.put(key, bitmap); Bitmap ready = bitmap;
                MAIN.post(() -> { if (key.equals(view.getTag())) view.setImageBitmap(ready); });
            }
        });
    }

    private static boolean download(String url, File directory, File destination) {
        if (!directory.exists() && !directory.mkdirs()) return false;
        File temporary = new File(directory, destination.getName() + ".part"); HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection(); connection.setConnectTimeout(8_000); connection.setReadTimeout(10_000);
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[16_384]; for (int read; (read = input.read(buffer)) >= 0; ) output.write(buffer, 0, read);
            }
            if (destination.exists()) destination.delete(); boolean saved = temporary.renameTo(destination); if (saved) trim(directory, 128L * 1024L * 1024L); return saved;
        } catch (Exception ignored) { temporary.delete(); return false; }
        finally { if (connection != null) connection.disconnect(); }
    }

    private static Bitmap decode(File file) {
        if (!file.isFile()) return null; file.setLastModified(System.currentTimeMillis());
        BitmapFactory.Options bounds = new BitmapFactory.Options(); bounds.inJustDecodeBounds = true; BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
        int sample = 1; while (bounds.outWidth / sample > 768 || bounds.outHeight / sample > 768) sample *= 2;
        BitmapFactory.Options options = new BitmapFactory.Options(); options.inSampleSize = sample; return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private static String digest(String value) {
        try { byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); StringBuilder out = new StringBuilder(); for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", b)); return out.toString(); }
        catch (Exception ignored) { return Integer.toHexString(value.hashCode()); }
    }
    private static void trim(File directory, long maxBytes) {
        File[] files = directory.listFiles(file -> file.isFile() && !file.getName().endsWith(".part")); if (files == null) return;
        long total = 0; for (File file : files) total += file.length();
        java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));
        for (File file : files) { if (total <= maxBytes) break; long size = file.length(); if (file.delete()) total -= size; }
    }
    private ArtworkLoader() { }
}
