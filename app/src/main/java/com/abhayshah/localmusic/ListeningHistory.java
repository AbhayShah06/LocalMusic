package com.abhayshah.localmusic;

import android.content.Context;
import android.net.Uri;
import androidx.media3.common.MediaItem;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Small on-device listening profile used for history and personal mixes. */
final class ListeningHistory {
    private static final String PREFS = "listening_history", KEY = "tracks";

    static synchronized void record(Context context, MediaItem item) {
        if (item == null || item.mediaId == null || item.mediaId.isEmpty()) return;
        try {
            JSONArray old = new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"));
            int plays = 1; JSONArray next = new JSONArray();
            for (int i = 0; i < old.length(); i++) {
                JSONObject value = old.getJSONObject(i);
                if (Track.originalId(item.mediaId).equals(Track.originalId(value.optString("id")))) plays += value.optInt("plays", 1);
                else if (next.length() < 99) next.put(value);
            }
            JSONObject value = new JSONObject().put("id", Track.originalId(item.mediaId)).put("plays", plays).put("last", System.currentTimeMillis())
                    .put("title", text(item.mediaMetadata.title)).put("artist", text(item.mediaMetadata.artist))
                    .put("album", text(item.mediaMetadata.albumTitle)).put("artwork", item.mediaMetadata.artworkUri == null ? "" : item.mediaMetadata.artworkUri.toString())
                    .put("uri", item.localConfiguration == null ? "" : item.localConfiguration.uri.toString());
            JSONArray saved = new JSONArray().put(value); for (int i = 0; i < next.length(); i++) saved.put(next.getJSONObject(i));
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, saved.toString()).apply();
        } catch (Exception ignored) { }
    }

    static List<Track> recent(Context context, int limit) { return read(context, limit, false); }
    static List<Track> dailyMix(Context context, int limit) { return read(context, limit, true); }

    private static List<Track> read(Context context, int limit, boolean mix) {
        List<Entry> entries = new ArrayList<>();
        try {
            JSONArray saved = new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"));
            for (int i = 0; i < saved.length(); i++) { JSONObject value = saved.getJSONObject(i); Track track = track(value); if (track != null) entries.add(new Entry(track, Math.max(1, value.optInt("plays", 1)))); }
        } catch (Exception ignored) { }
        List<Track> result = new ArrayList<>();
        if (!mix) { for (Entry entry : entries) { if (result.size() >= limit) break; result.add(entry.track); } return result; }
        Random random = new Random(System.currentTimeMillis() / 86_400_000L);
        while (!entries.isEmpty() && result.size() < limit) {
            int total = 0; for (Entry entry : entries) total += Math.max(1, (int)Math.sqrt(entry.plays));
            int pick = random.nextInt(Math.max(1, total)), selected = 0;
            for (int i = 0; i < entries.size(); i++) { pick -= Math.max(1, (int)Math.sqrt(entries.get(i).plays)); if (pick < 0) { selected = i; break; } }
            result.add(entries.remove(selected).track);
        }
        return result;
    }

    private static Track track(JSONObject value) {
        String id = value.optString("id"), uri = value.optString("uri"), artwork = value.optString("artwork"); if (id.isEmpty()) return null;
        return new Track(id, uri.isEmpty() ? Uri.EMPTY : Uri.parse(uri), value.optString("title"), value.optString("artist"), value.optString("album"), artwork.isEmpty() ? null : Uri.parse(artwork));
    }
    private static final class Entry { final Track track; final int plays; Entry(Track track, int plays) { this.track = track; this.plays = plays; } }
    private static String text(CharSequence value) { return value == null ? "" : value.toString(); }
    private ListeningHistory() { }
}
