package com.abhayshah.localmusic;

import android.content.Context;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/** Metadata index for user-pinned offline audio. Files live only in LocalMusic's private storage. */
final class DownloadStore {
    private static final String PREFS = "offline_downloads", KEY = "tracks";
    static File directory(Context context) { File value = new File(context.getFilesDir(), "downloads"); if (!value.exists()) value.mkdirs(); return value; }
    static File destination(Context context, String stableId) { return new File(directory(context), digest(stableId) + ".audio"); }

    static synchronized void put(Context context, Track track, File file) {
        try {
            JSONArray old = values(context), next = new JSONArray();
            for (int i = 0; i < old.length(); i++) if (!track.stableId.equals(old.getJSONObject(i).optString("id"))) next.put(old.getJSONObject(i));
            next.put(new JSONObject().put("id", track.stableId).put("file", file.getAbsolutePath()).put("title", track.title)
                    .put("artist", track.artist).put("album", track.album).put("artwork", track.artworkUri == null ? "" : track.artworkUri.toString()));
            save(context, next);
        } catch (Exception ignored) { }
    }

    static synchronized Track offline(Context context, Track source) {
        for (Track track : all(context)) if (track.stableId.equals(source.stableId)) return track; return null;
    }
    static synchronized boolean contains(Context context, String stableId) {
        try { JSONArray values = values(context); for (int i = 0; i < values.length(); i++) if (stableId.equals(values.getJSONObject(i).optString("id")) && new File(values.getJSONObject(i).optString("file")).isFile()) return true; }
        catch (Exception ignored) { } return false;
    }
    static synchronized List<Track> all(Context context) {
        List<Track> tracks = new ArrayList<>();
        try { JSONArray values = values(context); for (int i = values.length() - 1; i >= 0; i--) { JSONObject value = values.getJSONObject(i); File file = new File(value.optString("file")); if (!file.isFile()) continue; String artwork = value.optString("artwork"); tracks.add(new Track(value.optString("id"), Uri.fromFile(file), value.optString("title"), value.optString("artist"), value.optString("album"), artwork.isEmpty() ? null : Uri.parse(artwork))); } }
        catch (Exception ignored) { } return tracks;
    }
    static synchronized boolean remove(Context context, String stableId) {
        boolean removed = false; JSONArray next = new JSONArray();
        try { JSONArray values = values(context); for (int i = 0; i < values.length(); i++) { JSONObject value = values.getJSONObject(i); if (stableId.equals(value.optString("id"))) { removed = new File(value.optString("file")).delete() || removed; } else next.put(value); } save(context, next); }
        catch (Exception ignored) { } return removed;
    }
    static synchronized long bytes(Context context) { long total = 0; for (File file : directory(context).listFiles() == null ? new File[0] : directory(context).listFiles()) if (file.isFile() && !file.getName().endsWith(".part")) total += file.length(); return total; }
    private static JSONArray values(Context context) { try { return new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]")); } catch (Exception ignored) { return new JSONArray(); } }
    private static void save(Context context, JSONArray values) { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, values.toString()).apply(); }
    private static String digest(String value) { try { byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); StringBuilder out = new StringBuilder(); for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", b)); return out.toString(); } catch (Exception ignored) { return Integer.toHexString(value.hashCode()); } }
    private DownloadStore() { }
}
