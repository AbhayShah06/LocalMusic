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
        ListeningStats.record(context,item);
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
            for (int i = 0; i < saved.length(); i++) { JSONObject value = saved.getJSONObject(i); Track track = track(value); if (track != null) entries.add(new Entry(track, Math.max(1, value.optInt("plays", 1)),value.optLong("last"))); }
        } catch (Exception ignored) { }
        List<Track> result = new ArrayList<>();
        if (!mix) { for (Entry entry : entries) { if (result.size() >= limit) break; result.add(entry.track); } return result; }
        long now=System.currentTimeMillis();TasteProfile taste=TasteProfile.load(context);entries.removeIf(entry->now-entry.last>90L*86_400_000L||taste.suppresses(entry.track)||BlacklistedStore.contains(context,entry.track)||AvoidedStore.contains(context,entry.track));
        Random random = new Random(System.currentTimeMillis() / 86_400_000L);
        while (!entries.isEmpty() && result.size() < limit) {
            int total = 0; for (Entry entry : entries) total += weight(entry,now);
            int pick = random.nextInt(Math.max(1, total)), selected = 0;
            for (int i = 0; i < entries.size(); i++) { pick -= weight(entries.get(i),now); if (pick < 0) { selected = i; break; } }
            result.add(entries.remove(selected).track);
        }
        return result;
    }

    private static Track track(JSONObject value) {
        String id = value.optString("id"), uri = value.optString("uri"), artwork = value.optString("artwork"); if (id.isEmpty()) return null;
        return new Track(id, uri.isEmpty() ? Uri.EMPTY : Uri.parse(uri), value.optString("title"), value.optString("artist"), value.optString("album"), artwork.isEmpty() ? null : Uri.parse(artwork));
    }
    private static int weight(Entry entry,long now){return Math.max(1,(int)Math.round(Math.sqrt(entry.plays)*8*Math.exp(-Math.max(0,now-entry.last)/(21d*86_400_000d))));}
    private static final class Entry { final Track track; final int plays;final long last; Entry(Track track, int plays,long last) { this.track = track; this.plays = plays;this.last=last; } }
    private static String text(CharSequence value) { return value == null ? "" : value.toString(); }
    private ListeningHistory() { }
}
