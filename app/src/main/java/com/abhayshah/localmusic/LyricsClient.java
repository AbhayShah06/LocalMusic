package com.abhayshah.localmusic;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Fetches lyrics on demand and keeps successful results on this phone. */
final class LyricsClient {
    static String get(Context context, Track track) {
        File directory = new File(context.getCacheDir(), "lyrics"), cached = new File(directory, digest(track.stableId) + ".txt");
        String local = read(cached); if (!local.isEmpty()) return local;
        HttpURLConnection connection = null;
        try {
            String query = "track_name=" + encode(track.title) + "&artist_name=" + encode(track.artist.replaceAll("(?i)\\s*-\\s*topic$", "").trim());
            connection = (HttpURLConnection) new URL("https://lrclib.net/api/search?" + query).openConnection();
            connection.setConnectTimeout(8_000); connection.setReadTimeout(12_000); connection.setRequestProperty("User-Agent", "LocalMusic/0.8.2 personal Android player");
            if (connection.getResponseCode() / 100 != 2) return "";
            String body; try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; for (int n; (n = input.read(buffer)) > 0; ) output.write(buffer, 0, n); body = output.toString(StandardCharsets.UTF_8.name());
            }
            JSONArray results = new JSONArray(body); String best = "";
            for (int i = 0; i < Math.min(8, results.length()); i++) { JSONObject item = results.getJSONObject(i); String plain = item.optString("plainLyrics", "").trim(); if (!plain.isEmpty()) { best = plain; break; } }
            if (!best.isEmpty()) { if (!directory.exists()) directory.mkdirs(); try (FileOutputStream output = new FileOutputStream(cached)) { output.write(best.getBytes(StandardCharsets.UTF_8)); } }
            return best;
        } catch (Exception ignored) { return ""; }
        finally { if (connection != null) connection.disconnect(); }
    }
    private static String encode(String value) throws Exception { return URLEncoder.encode(value, StandardCharsets.UTF_8.name()); }
    private static String read(File file) { if (!file.isFile()) return ""; try (FileInputStream input = new FileInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream()) { byte[] buffer = new byte[8192]; for (int n; (n = input.read(buffer)) > 0; ) output.write(buffer, 0, n); return output.toString(StandardCharsets.UTF_8.name()); } catch (Exception ignored) { return ""; } }
    private static String digest(String value) { try { byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder out = new StringBuilder(); for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", b)); return out.toString(); } catch (Exception ignored) { return Integer.toHexString(value.hashCode()); } }
    static void clear(Context context) { File[] files = new File(context.getCacheDir(), "lyrics").listFiles(); if (files != null) for (File file : files) file.delete(); }
    private LyricsClient() { }
}
